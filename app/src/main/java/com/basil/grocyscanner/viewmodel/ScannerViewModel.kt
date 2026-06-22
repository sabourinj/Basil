package com.basil.grocyscanner.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.basil.grocyscanner.data.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class AppMode { PURCHASE, CONSUME, INVENTORY, BATCH_MOVE }

class ScannerViewModel(private val api: GrocyApi, private val geminiApiKey: String?) : ViewModel() {
    private val categoriesRequiringDate = listOf(3, 5, 8)
    private var cachedGroups = listOf<GrocyProductGroup>()
    private var isProcessingAction = false

    private var batchMoveLocationId: Int? = null
    private var batchMoveLocationName: String? = null

    private val _locations = MutableStateFlow<List<GrocyLocation>>(emptyList())
    val locations: StateFlow<List<GrocyLocation>> = _locations

    sealed class AppState {
        object Idle : AppState()
        data class Loading(
            val message: String = "Communicating with Grocy...",
            val showGemini: Boolean = false,
            val showOff: Boolean = false,
            val showGrocy: Boolean = false
        ) : AppState()
        data class NeedsDate(val product: ProductDetails, val estimatedPrice: Double? = null, val amount: Double = 1.0, val autoPrint: Boolean = false) : AppState()
        data class Success(
            val message: String,
            val stockMessage: String = "",
            val product: ProductDetails? = null,
            val currentStock: Double = 0.0,
            val stockId: String? = null,
            val addedAmount: Double = 1.0,
            val isPrinting: Boolean = false,
            val isOpened: Boolean = false,
            val lastScannedBarcode: String? = null,
            val lastPrice: Double? = null,
            val lastExpireDate: String? = null
        ) : AppState()
        data class Error(val error: String) : AppState()
        data class InventoryResult(
            val product: ProductDetails,
            val entries: List<StockEntry>,
            val isOpened: Boolean = false,
            val canOpen: Boolean = false
        ) : AppState()
        data class LinkingChild(val product: ProductDetails, val parentBarcode: String) : AppState()
        data class ChildQuantityPrompt(val product: ProductDetails, val parentBarcode: String, val childBarcode: String) : AppState()
        data class BatchMoveActive(val locationName: String) : AppState()
    }

    private val _state = MutableStateFlow<AppState>(AppState.Idle)
    val state: StateFlow<AppState> = _state

    private val _currentMode = MutableStateFlow(AppMode.PURCHASE)
    val currentMode: StateFlow<AppMode> = _currentMode

    private var lastScanTime = 0L
    private var lastScannedBarcode = ""

    private val generativeModel by lazy {
        geminiApiKey?.let {
            GenerativeModel(
                modelName = "gemini-2.5-flash-lite",
                apiKey = it,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    temperature = 0.0f
                }
            )
        }
    }

    init {
        viewModelScope.launch {
            try {
                _locations.value = api.getLocations()
                cachedGroups = api.getProductGroups()
            } catch (e: Exception) {
                Log.e("BasilDebug", "Failed to cache locations/groups: ${e.message}")
            }
        }
    }

    fun getCachedLocations() = _locations.value

    fun setMode(mode: AppMode) {
        _currentMode.value = mode
        if (mode != AppMode.BATCH_MOVE) {
            batchMoveLocationId = null
            batchMoveLocationName = null
            resetState()
        }
    }

    fun startBatchMove(locationId: Int, locationName: String) {
        batchMoveLocationId = locationId
        batchMoveLocationName = locationName
        _currentMode.value = AppMode.BATCH_MOVE
        _state.value = AppState.BatchMoveActive(locationName)
    }

    fun onBarcodeScanned(barcode: String) {
        if (_state.value is AppState.Loading) {
            Log.d("BasilDebug", "Scan ignored: App is busy.")
            return
        }

        val currentState = _state.value
        if (currentState is AppState.LinkingChild) {
            _state.value = AppState.ChildQuantityPrompt(currentState.product, currentState.parentBarcode, barcode)
            return
        }

        val currentTime = System.currentTimeMillis()
        if (barcode == lastScannedBarcode && (currentTime - lastScanTime) < 1500) {
            Log.d("BasilDebug", "Scan ignored: Duplicate debounced.")
            return
        }

        lastScannedBarcode = barcode
        lastScanTime = currentTime

        viewModelScope.launch {
            if (_currentMode.value == AppMode.BATCH_MOVE) {
                processBatchMove(barcode)
            } else {
                identifyAndProcessBarcode(barcode)
            }
        }
    }

    private suspend fun processBatchMove(barcode: String) {
        val locationId = batchMoveLocationId ?: return
        val locationName = batchMoveLocationName ?: ""
        
        _state.value = AppState.Loading("Moving product to $locationName...", showGrocy = true)
        try {
            val response = api.getProductByBarcode(barcode)
            val product = response.product

            val bDetails = fetchBarcodeDetails(barcode)
            val moveAmount = response.barcode?.amount ?: bDetails?.amount ?: 1.0

            // 1. Update product's default location for future purchases
            api.updateProduct(product.id, mapOf("location_id" to locationId, "name" to product.name))

            // 2. Relocate stock - let Grocy pick the source automatically
            api.transferStock(
                product.id,
                TransferStockRequest(
                    amount = moveAmount,
                    location_id_to = locationId
                )
            )

            _state.value = AppState.Success(
                message = "Moved!",
                stockMessage = "${product.name} relocated to $locationName",
                product = product,
                lastScannedBarcode = barcode,
                addedAmount = moveAmount
            )
        } catch (e: Exception) {
            Log.e("BasilDebug", "Batch Move failed", e)
            _state.value = AppState.Error("Move failed: ${e.message}")
        }
    }

    private suspend fun identifyAndProcessBarcode(barcode: String, isRetry: Boolean = false) {
        _state.value = AppState.Loading("Identifying product...", showGrocy = true)
        if (!isRetry) delay(150)

        var isNewlyAdded = false
        var currentProduct: ProductDetails? = null
        var knownPrice: Double? = null
        var scanAmount = 1.0
        var barcodeId: Int? = null

        try {
            val response = api.getProductByBarcode(barcode)
            currentProduct = response.product
            knownPrice = response.last_price ?: response.product.default_price
            
            val bDetails = fetchBarcodeDetails(barcode)
            scanAmount = response.barcode?.amount ?: bDetails?.amount ?: 1.0
            barcodeId = response.barcode?.id ?: bDetails?.id
        } catch (e: HttpException) {
            if (_currentMode.value == AppMode.INVENTORY || _currentMode.value == AppMode.CONSUME) {
                _state.value = AppState.Error("Product not found.")
                return
            }

            _state.value = AppState.Loading("Looking up product...", showOff = true)
            try {
                api.externalBarcodeLookup(barcode, true)
            } catch (_: Exception) {
                Log.w("BasilDebug", "External lookup timeout/error.")
            }

            try {
                val newResponse = api.getProductByBarcode(barcode)
                currentProduct = newResponse.product
                knownPrice = newResponse.last_price ?: newResponse.product.default_price
                val bDetails = fetchBarcodeDetails(barcode)
                scanAmount = newResponse.barcode?.amount ?: bDetails?.amount ?: 1.0
                barcodeId = newResponse.barcode?.id ?: bDetails?.id
                isNewlyAdded = true
            } catch (_: Exception) {
                _state.value = AppState.Error("Unable to identify product.\nAdd manually in Grocy.")
                return
            }
        } catch (e: Exception) {
            _state.value = AppState.Error("Network Error: ${e.message}")
            return
        }

        currentProduct?.let { product ->
            if (isNewlyAdded && generativeModel != null) {
                _state.value = AppState.Loading("Analyzing product...", showGemini = true)
                enrichProductWithGemini(product.id, product.name)

                try {
                    _state.value = AppState.Loading("Creating new product...", showGrocy = true)
                    val updatedResponse = api.getProductByBarcode(barcode)
                    val bDetails = fetchBarcodeDetails(barcode)
                    processFoundProduct(updatedResponse.product, knownPrice, updatedResponse.barcode?.amount ?: bDetails?.amount ?: 1.0, updatedResponse.barcode?.id ?: bDetails?.id, barcode)
                } catch (e: Exception) {
                    processFoundProduct(product, knownPrice, scanAmount, barcodeId, barcode)
                }

            } else {
                processFoundProduct(product, knownPrice, scanAmount, barcodeId, barcode)
            }
        }
    }

    private suspend fun fetchBarcodeDetails(barcode: String): BarcodeDetails? {
        return try {
            val details = api.getBarcodeDetails("barcode=$barcode")
            details.firstOrNull()
        } catch (e: Exception) {
            Log.e("BasilDebug", "Failed to fetch barcode details: ${e.message}")
            null
        }
    }

    private suspend fun enrichProductWithGemini(productId: Int, name: String) {
        try {
            val locationList = _locations.value.joinToString { "${it.id}: ${it.name}" }
            val groupList = cachedGroups.joinToString { "${it.id}: ${it.name}" }

            val prompt = """
                Analyze the grocery product: "$name".
                Available Locations: [$locationList]
                Available Product Groups: [$groupList]
                
                Return a strict JSON object with these EXACT keys based on your best estimates:
                - "default_best_before_days" (int)
                - "default_best_before_days_after_open" (int)
                - "default_best_before_days_after_freezing" (int)
                - "default_best_before_days_after_thawing" (int)
                - "should_not_be_frozen" (int, 1 for yes, 0 for no)
                - "product_group_id" (int, choose best match from available groups or null if none fit)
                - "location_id" (int, choose best match from available locations or null if none fit)
            """.trimIndent()

            val response = generativeModel?.generateContent(prompt)
            response?.text?.let { jsonStr ->
                val json = JSONObject(jsonStr)
                val updateMap = mutableMapOf<String, Any>()

                updateMap["default_best_before_days"] = json.optInt("default_best_before_days", 0)
                updateMap["default_best_before_days_after_open"] = json.optInt("default_best_before_days_after_open", 0)
                updateMap["default_best_before_days_after_freezing"] = json.optInt("default_best_before_days_after_freezing", 0)
                updateMap["default_best_before_days_after_thawing"] = json.optInt("default_best_before_days_after_thawing", 0)
                updateMap["should_not_be_frozen"] = json.optInt("should_not_be_frozen", 0)

                if (json.has("product_group_id") && !json.isNull("product_group_id")) updateMap["product_group_id"] = json.getInt("product_group_id")
                if (json.has("location_id") && !json.isNull("location_id")) updateMap["location_id"] = json.getInt("location_id")

                api.updateProduct(productId, updateMap)
            }
        } catch (e: Exception) {
            Log.e("BasilDebug", "AI Enrichment Failed: ${e.message}")
        }
    }

    private suspend fun processFoundProduct(product: ProductDetails, knownPrice: Double? = null, amount: Double = 1.0, barcodeId: Int? = null, scannedBarcode: String? = null) {
        when (_currentMode.value) {
            AppMode.INVENTORY -> {
                try {
                    _state.value = AppState.Loading("Checking inventory...", showGrocy = true)
                    val rawEntries = api.getStockEntries(product.id)
                    val canOpen = rawEntries.any { it.open == 0 }
                    val groupedEntries = rawEntries
                        .groupBy { if (it.best_before_date.isNullOrBlank()) "2999-12-31" else it.best_before_date }
                        .map { mapEntry ->
                            StockEntry(
                                id = mapEntry.value.first().id,
                                amount = mapEntry.value.sumOf { it.amount },
                                best_before_date = mapEntry.key,
                                open = if (mapEntry.value.all { it.open == 1 }) 1 else 0,
                                location_id = mapEntry.value.first().location_id
                            )
                        }
                        .sortedBy { it.best_before_date }
                    _state.value = AppState.InventoryResult(product, groupedEntries, canOpen = canOpen)
                } catch (_: Exception) {
                    _state.value = AppState.Error("Failed to fetch stock entries.")
                }
            }
            AppMode.CONSUME -> confirmAction(product.id, amount, null, null)
            AppMode.PURCHASE -> {
                var estimatedPrice: Double? = knownPrice

                if (estimatedPrice == null || estimatedPrice <= 0.0) {
                    if (generativeModel != null) {
                        _state.value = AppState.Loading("Estimating price...", showGemini = true)
                        try {
                            val pricePrompt = "Estimate the current USD price of '${product.name}'. Return a JSON object with a single key 'price' containing a float value."
                            val response = generativeModel?.generateContent(pricePrompt)
                            response?.text?.let { jsonStr ->
                                estimatedPrice = JSONObject(jsonStr).optDouble("price", 0.0).takeIf { it > 0.0 }
                            }
                        } catch (e: Exception) {
                            Log.e("BasilDebug", "Price estimation failed: ${e.message}")
                        }
                    }
                }

                val productGroup = cachedGroups.find { it.id == product.product_group_id }
                val strategy = productGroup?.userfields?.expiration_strategy ?: "ai_estimate"
                val shelfLife = product.default_best_before_days

                var autoPrint = false
                if (barcodeId != null) {
                    try {
                        val uf = api.getBarcodeUserfields(barcodeId)
                        autoPrint = (uf["auto_print_label"]?.toString() ?: "0") == "1"
                    } catch (_: Exception) {}
                }

                when (strategy) {
                    "user_entry" -> {
                        _state.value = AppState.NeedsDate(product, estimatedPrice, amount, autoPrint)
                    }
                    "ai_estimate" -> {
                        val daysToAdd = if (shelfLife > 0) shelfLife.toLong() else 7L
                        val autoCalculatedDate = LocalDate.now().plusDays(daysToAdd).format(
                            DateTimeFormatter.ISO_LOCAL_DATE)
                        confirmAction(product.id, amount, autoCalculatedDate, estimatedPrice, autoPrint, scannedBarcode)
                    }
                    "not_required", "" -> {
                        confirmAction(product.id, amount, null, estimatedPrice, autoPrint, scannedBarcode)
                    }
                }
            }
            AppMode.BATCH_MOVE -> {
                // Handled directly in onBarcodeScanned/processBatchMove
            }
        }
    }

    fun confirmAction(productId: Int, amount: Double, expireDate: String?, price: Double?, autoPrint: Boolean = false, scannedBarcode: String? = null) {
        if (isProcessingAction) return
        isProcessingAction = true

        viewModelScope.launch {
            _state.value = AppState.Loading(if (_currentMode.value == AppMode.PURCHASE) "Adding stock..." else "Consuming stock...", showGrocy = true)
            var lastStockUuid: String? = null

            try {
                if (_currentMode.value == AppMode.PURCHASE) {
                    val result = api.addStock(productId, AddStockRequest(amount, price, expireDate))
                    lastStockUuid = result.firstOrNull()?.stock_id
                } else {
                    api.consumeStock(productId, ConsumeStockRequest(amount))
                }

                val updatedData = api.getProductById(productId)
                val remaining = updatedData.stock_amount ?: 0.0
                val stockStr = if (remaining % 1.0 == 0.0) remaining.toInt().toString() else remaining.toString()

                _state.value = AppState.Success(
                    message = "Success!",
                    stockMessage = "${if (_currentMode.value == AppMode.PURCHASE) "New stock level" else "Remaining stock"}: $stockStr",
                    product = updatedData.product,
                    currentStock = remaining,
                    stockId = lastStockUuid,
                    addedAmount = if (_currentMode.value == AppMode.PURCHASE) amount else 1.0,
                    lastScannedBarcode = scannedBarcode,
                    lastPrice = price,
                    lastExpireDate = expireDate
                )
                
                if (autoPrint && _currentMode.value == AppMode.PURCHASE) {
                    performQuickAction(productId, "print", lastStockUuid, amount)
                }

            } catch (e: HttpException) {
                if (_currentMode.value == AppMode.CONSUME && e.code() == 400) {
                    _state.value = AppState.Error("No stock found!")
                } else {
                    _state.value = AppState.Error("Action failed: HTTP ${e.code()}")
                }
            } catch (e: Exception) {
                _state.value = AppState.Error("Action failed: ${e.message}")
            } finally {
                isProcessingAction = false
            }

        }
    }

    fun performQuickAction(productId: Int, action: String, stockId: String? = null, amount: Double = 1.0) {
        viewModelScope.launch {
            try {
                when(action) {
                    "open" -> {
                        api.openStock(productId, OpenStockRequest(1))
                        val currentState = _state.value
                        if (currentState is AppState.Success) {
                            _state.value = currentState.copy(isOpened = true)
                        } else if (currentState is AppState.InventoryResult) {
                            // If opening from inventory, refresh the list
                            val rawEntries = api.getStockEntries(productId)
                            val canOpen = rawEntries.any { it.open == 0 }
                            val groupedEntries = rawEntries
                                .groupBy { if (it.best_before_date.isNullOrBlank()) "2999-12-31" else it.best_before_date }
                                .map { mapEntry ->
                                    StockEntry(
                                        id = mapEntry.value.first().id,
                                        amount = mapEntry.value.sumOf { it.amount },
                                        best_before_date = mapEntry.key,
                                        open = if (mapEntry.value.all { it.open == 1 }) 1 else 0,
                                        location_id = mapEntry.value.first().location_id
                                    )
                                }
                                .sortedBy { it.best_before_date }
                            _state.value = currentState.copy(entries = groupedEntries, isOpened = true, canOpen = canOpen)
                            delay(1000)
                            val finalState = _state.value
                            if (finalState is AppState.InventoryResult) {
                                _state.value = finalState.copy(isOpened = false)
                            }
                        }
                    }
                    "print" -> {
                        val printCount = if (amount > 0) Math.ceil(amount).toInt() else 1
                        val successState = _state.value as? AppState.Success
                        if (successState != null) {
                            _state.value = successState.copy(isPrinting = true)
                        }

                        repeat(printCount) {
                            try {
                                if (stockId != null) {
                                    api.printStockLabel(stockId)
                                } else {
                                    api.printLabel(productId)
                                }
                            } catch (e: Exception) {
                                Log.w("BasilDebug", "Specific label print failed, falling back to product label: ${e.message}")
                                api.printLabel(productId)
                            }
                            delay(100) // Small delay between repeat prints
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = AppState.Error("Quick action failed: ${e.message}")
            }
        }
    }

    fun startLinkingChild(product: ProductDetails, parentBarcode: String) {
        _state.value = AppState.LinkingChild(product, parentBarcode)
    }

    fun confirmChildLink(productId: Int, parentBarcode: String, childBarcode: String, quantity: Double) {
        val currentSuccess = _state.value as? AppState.Success
        val previousAddedAmount = currentSuccess?.addedAmount ?: 1.0
        val previousPrice = currentSuccess?.lastPrice
        val previousExpire = currentSuccess?.lastExpireDate

        viewModelScope.launch {
            _state.value = AppState.Loading("Linking barcodes...")
            try {
                // 1. Link child barcode
                val childDetails = fetchBarcodeDetails(childBarcode)
                if (childDetails == null) {
                    api.addBarcode(BarcodeDetails(barcode = childBarcode, product_id = productId, amount = 1.0))
                } else if (childDetails.product_id != productId) {
                    api.updateBarcode(childDetails.id!!, mapOf("product_id" to productId, "amount" to 1.0, "barcode" to childBarcode))
                }
                
                // 2. Update parent barcode
                val parentDetails = fetchBarcodeDetails(parentBarcode)
                if (parentDetails != null && parentDetails.id != null) {
                    api.updateBarcode(parentDetails.id, mapOf("amount" to quantity, "barcode" to parentBarcode, "product_id" to productId))
                }

                // 3. Stock Correction: Add the remaining items (Total - What we already added)
                val extraToAdd = quantity - previousAddedAmount
                if (extraToAdd > 0) {
                    api.addStock(productId, AddStockRequest(extraToAdd, previousPrice, previousExpire))
                }

                // 4. Final Success State
                val updatedData = api.getProductById(productId)
                val remaining = updatedData.stock_amount ?: 0.0
                val stockStr = if (remaining % 1.0 == 0.0) remaining.toInt().toString() else remaining.toString()

                _state.value = AppState.Success(
                    message = "Linking Successful!",
                    stockMessage = "Updated stock: $stockStr",
                    product = updatedData.product,
                    currentStock = remaining,
                    addedAmount = quantity,
                    lastScannedBarcode = parentBarcode
                )

            } catch (e: Exception) {
                Log.e("BasilDebug", "Linking failed", e)
                _state.value = AppState.Error("Linking failed: ${e.message}")
            }
        }
    }

    fun resetState() {
        val currentLocName = batchMoveLocationName
        if (_currentMode.value == AppMode.BATCH_MOVE && currentLocName != null) {
            _state.value = AppState.BatchMoveActive(currentLocName)
        } else {
            _state.value = AppState.Idle
        }
    }
}
