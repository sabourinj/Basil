package com.basil.grocyscanner.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.basil.grocyscanner.data.AddStockRequest
import com.basil.grocyscanner.data.ConsumeStockRequest
import com.basil.grocyscanner.data.GrocyApi
import com.basil.grocyscanner.data.GrocyLocation
import com.basil.grocyscanner.data.GrocyProductGroup
import com.basil.grocyscanner.data.OpenStockRequest
import com.basil.grocyscanner.data.ProductDetails
import com.basil.grocyscanner.data.StockEntry
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

enum class AppMode { PURCHASE, CONSUME, INVENTORY }

class ScannerViewModel(private val api: GrocyApi, private val geminiApiKey: String?) : ViewModel() {
    private val categoriesRequiringDate = listOf(3, 5, 8)
    private var cachedLocations = listOf<GrocyLocation>()
    private var cachedGroups = listOf<GrocyProductGroup>()
    private var isProcessingAction = false

    sealed class AppState {
        object Idle : AppState()
        data class Loading(val message: String = "Communicating with Grocy...") : AppState()
        data class NeedsDate(val product: ProductDetails, val estimatedPrice: Double? = null) : AppState()
        data class Success(val message: String, val stockMessage: String = "", val productId: Int? = null, val currentStock: Double = 0.0) : AppState()
        data class Error(val error: String) : AppState()
        data class InventoryResult(val product: ProductDetails, val entries: List<StockEntry>) : AppState()
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
                cachedLocations = api.getLocations()
                cachedGroups = api.getProductGroups()
            } catch (e: Exception) {
                Log.e("BasilDebug", "Failed to cache locations/groups: ${e.message}")
            }
        }
    }

    fun setMode(mode: AppMode) {
        _currentMode.value = mode
        resetState()
    }

    fun onBarcodeScanned(barcode: String) {
        if (_state.value is AppState.Loading) {
            Log.d("BasilDebug", "Scan ignored: App is busy.")
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
            _state.value = AppState.Loading("Identifying product...")
            delay(150)

            var isNewlyAdded = false
            var currentProduct: ProductDetails? = null
            var knownPrice: Double? = null

            try {
                val response = api.getProductByBarcode(barcode)
                currentProduct = response.product
                knownPrice = response.last_price ?: response.product.default_price
            } catch (_: HttpException) {
                if (_currentMode.value == AppMode.INVENTORY) {
                    _state.value = AppState.Error("Product not found.")
                    return@launch
                }

                _state.value = AppState.Loading("Looking up product...")
                try {
                    api.externalBarcodeLookup(barcode, true)
                } catch (_: Exception) {
                    Log.w("BasilDebug", "External lookup timeout/error.")
                }

                try {
                    val newResponse = api.getProductByBarcode(barcode)
                    currentProduct = newResponse.product
                    knownPrice = newResponse.last_price ?: newResponse.product.default_price
                    isNewlyAdded = true
                } catch (_: Exception) {
                    _state.value = AppState.Error("Unable to identify product.\nAdd manually in Grocy.")
                    return@launch
                }
            } catch (e: Exception) {
                _state.value = AppState.Error("Network Error: ${e.message}")
                return@launch
            }

            currentProduct?.let { product ->
                if (isNewlyAdded && generativeModel != null) {
                    _state.value = AppState.Loading("Analyzing product...")
                    enrichProductWithGemini(product.id, product.name)

                    try {
                        _state.value = AppState.Loading("Finalizing...")
                        val updatedResponse = api.getProductByBarcode(barcode)
                        processFoundProduct(updatedResponse.product, knownPrice)
                    } catch (e: Exception) {
                        processFoundProduct(product, knownPrice)
                    }

                } else {
                    processFoundProduct(product, knownPrice)
                }
            }
        }
    }

    private suspend fun enrichProductWithGemini(productId: Int, name: String) {
        try {
            val locationList = cachedLocations.joinToString { "${it.id}: ${it.name}" }
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

    private suspend fun processFoundProduct(product: ProductDetails, knownPrice: Double? = null) {
        when (_currentMode.value) {
            AppMode.INVENTORY -> {
                try {
                    _state.value = AppState.Loading("Checking inventory data...")
                    val rawEntries = api.getStockEntries(product.id)
                    val groupedEntries = rawEntries
                        .groupBy { if (it.best_before_date.isNullOrBlank()) "2999-12-31" else it.best_before_date }
                        .map { mapEntry ->
                            StockEntry(
                                id = mapEntry.value.first().id,
                                amount = mapEntry.value.sumOf { it.amount },
                                best_before_date = mapEntry.key
                            )
                        }
                        .sortedBy { it.best_before_date }
                    _state.value = AppState.InventoryResult(product, groupedEntries)
                } catch (_: Exception) {
                    _state.value = AppState.Error("Failed to fetch stock entries.")
                }
            }
            AppMode.CONSUME -> confirmAction(product.id, 1, null, null)
            AppMode.PURCHASE -> {
                var estimatedPrice: Double? = knownPrice

                if (estimatedPrice == null || estimatedPrice <= 0.0) {
                    if (generativeModel != null) {
                        _state.value = AppState.Loading("Estimating price...")
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

                when (strategy) {
                    "user_entry" -> {
                        _state.value = AppState.NeedsDate(product, estimatedPrice)
                    }
                    "ai_estimate" -> {
                        val daysToAdd = if (shelfLife > 0) shelfLife.toLong() else 7L
                        val autoCalculatedDate = LocalDate.now().plusDays(daysToAdd).format(
                            DateTimeFormatter.ISO_LOCAL_DATE)
                        confirmAction(product.id, 1, autoCalculatedDate, estimatedPrice)
                    }
                    "not_required", "" -> {
                        confirmAction(product.id, 1, null, estimatedPrice)
                    }
                }
            }
        }
    }

    fun confirmAction(productId: Int, amount: Int, expireDate: String?, price: Double?) {
        if (isProcessingAction) return
        isProcessingAction = true

        viewModelScope.launch {
            _state.value = AppState.Loading(if (_currentMode.value == AppMode.PURCHASE) "Adding stock..." else "Consuming stock...")
            try {
                if (_currentMode.value == AppMode.PURCHASE) {
                    api.addStock(productId, AddStockRequest(amount, price, expireDate))
                } else {
                    api.consumeStock(productId, ConsumeStockRequest(amount))
                }

                val updatedData = api.getProductById(productId)
                val remaining = updatedData.stock_amount ?: 0.0
                val stockStr = if (remaining % 1.0 == 0.0) remaining.toInt().toString() else remaining.toString()

                _state.value = AppState.Success(
                    message = "Success!",
                    stockMessage = "${if (_currentMode.value == AppMode.PURCHASE) "New stock level" else "Remaining stock"}: $stockStr",
                    productId = productId,
                    currentStock = remaining
                )

                delay(6000)
                if (_state.value is AppState.Success) resetState()

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

    fun performQuickAction(productId: Int, action: String) {
        viewModelScope.launch {
            try {
                when(action) {
                    "open" -> api.openStock(productId, OpenStockRequest())
                    "print" -> api.printLabel(productId)
                }
                resetState()
            } catch (e: Exception) {
                _state.value = AppState.Error("Quick action failed: ${e.message}")
            }
        }
    }

    fun resetState() { _state.value = AppState.Idle }
}