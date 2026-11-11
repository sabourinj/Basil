package com.basil.grocyscanner.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.basil.grocyscanner.data.*
import com.basil.grocyscanner.work.EnrichmentRequest
import com.basil.grocyscanner.work.EnrichmentScheduler
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class AppMode { PURCHASE, CONSUME, INVENTORY, BATCH_MOVE }

// Audible outcomes. Plain success is deliberately absent: the screen and the
// haptic already confirm it, and an extra chirp on every good scan trains you
// to tune the sounds out (the same call basil-capture makes).
enum class ScanSound { ERROR, SERVER_ERROR, PRINTED, UNDONE }

// What a shopping-mode purchase did to the shopping list
enum class ListStatus { CHECKED_OFF, REDUCED, NOT_ON_LIST }

data class PurchaseSheet(
    // Distinguishes one purchase's sheet from the next, even for the same product
    val id: Long,
    val product: ProductDetails,
    val amount: Double,
    val newStock: Double,
    val listStatus: ListStatus,
    val remainingOnList: Double = 0.0,
    val isDoNotBuy: Boolean = false,
    val isNewProduct: Boolean = false,
    val parentName: String? = null,
    val siblings: List<SiblingStock> = emptyList(),
    // The background job is still working out the parent
    val siblingsPending: Boolean = false,
    val canUndo: Boolean = false,
    val isUndoing: Boolean = false,
    val isUndone: Boolean = false
)

class ScannerViewModel(
    private val api: GrocyApi,
    private val geminiApiKey: String?,
    private val enrichment: EnrichmentScheduler? = null,
    private val geminiModelName: String = GeminiConfig.DEFAULT_MODEL
) : ViewModel() {
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
        data class ShoppingList(
            val items: List<ShoppingListItem>,
            val scanErrorTrigger: Long = 0L,
            // List row to scroll to and pulse (the one a scan just checked off)
            val newlyAddedProductId: Int? = null,
            val scanError: String? = null,
            // A scan is being purchased
            val isProcessing: Boolean = false,
            // Pop-up confirming the last purchase, shown over the list
            val sheet: PurchaseSheet? = null
        ) : AppState()
        data class NeedsDate(
            val product: ProductDetails,
            val estimatedPrice: Double? = null,
            val amount: Double = 1.0,
            val autoPrint: Boolean = false,
            val scanErrorTrigger: Long = 0L
        ) : AppState()
        data class Success(
            val message: String,
            val stockMessage: String = "",
            val product: ProductDetails? = null,
            val currentStock: Double = 0.0,
            val stockId: String? = null,
            val addedAmount: Double = 1.0,
            val isPrinting: Boolean = false,
            val isOpened: Boolean = false,
            val isAddingToList: Boolean = false,
            val isDoNotBuySet: Boolean = false,
            val lastScannedBarcode: String? = null,
            val lastPrice: Double? = null,
            val lastExpireDate: String? = null,
            val parentStock: ParentStock? = null,
            // Grocy transaction of a consume, offered for Undo while this screen is up.
            val transactionId: String? = null,
            val isUndone: Boolean = false
        ) : AppState()
        data class Error(val error: String) : AppState()
        data class InventoryResult(
            val product: ProductDetails,
            val entries: List<StockEntry>,
            val isOpened: Boolean = false,
            val isPrinting: Boolean = false,
            val isAddingToList: Boolean = false,
            val canOpen: Boolean = false,
            val canPrint: Boolean = false,
            val parentStock: ParentStock? = null
        ) : AppState()
        data class LinkingChild(val product: ProductDetails, val parentBarcode: String) : AppState()
        data class ChildQuantityPrompt(val product: ProductDetails, val parentBarcode: String, val childBarcode: String) : AppState()
        data class BatchMoveActive(val locationName: String) : AppState()
        data class DoNotBuyWarning(
            val product: ProductDetails,
            val estimatedPrice: Double?,
            val amount: Double,
            val autoPrint: Boolean,
            val scannedBarcode: String?
        ) : AppState()
    }

    private val _state = MutableStateFlow<AppState>(AppState.Idle)
    val state: StateFlow<AppState> = _state

    // One-shot sound cues for the screen to play. Events, not state: a sound
    // must play once per occurrence and never replay on recomposition.
    private val _sounds = MutableSharedFlow<ScanSound>(extraBufferCapacity = 4)
    val sounds: SharedFlow<ScanSound> = _sounds

    private fun playSound(sound: ScanSound) {
        _sounds.tryEmit(sound)
    }

    // Same split as basil-capture's GrocyError.server_side: "Grocy is
    // unreachable" (network failure, 5xx) is not the item's fault, so it gets
    // its own sound, distinct from "Grocy refused this item".
    private fun errorSoundFor(cause: Throwable?): ScanSound = when {
        cause is IOException -> ScanSound.SERVER_ERROR
        cause is HttpException && cause.code() >= 500 -> ScanSound.SERVER_ERROR
        else -> ScanSound.ERROR
    }

    private fun showError(message: String, cause: Throwable? = null) {
        _state.value = AppState.Error(message)
        playSound(errorSoundFor(cause))
    }

    private val _currentMode = MutableStateFlow(AppMode.PURCHASE)
    val currentMode: StateFlow<AppMode> = _currentMode

    private var lastScanTime = 0L
    private var lastScannedBarcode = ""

    companion object {
        const val INVALID_SCAN_MESSAGE = "Not a product code"

        // Scans matching any of these are rejected rather than sent to Grocy.
        // Catches URL QR codes, e.g. the "SmartLabel" codes next to UPCs.
        // Kept in step with basil-capture's DEFAULT_REJECT_PATTERNS.
        private val REJECT_PATTERNS = listOf(
            Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE),
            Regex("^www\\.", RegexOption.IGNORE_CASE)
        )

        fun isRejectedScan(barcode: String) = REJECT_PATTERNS.any { it.containsMatchIn(barcode) }
    }

    private val generativeModel by lazy {
        geminiApiKey?.let {
            GenerativeModel(
                modelName = geminiModelName,
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

    fun showShoppingList() {
        viewModelScope.launch {
            _state.value = AppState.Loading("Fetching shopping list...", showGrocy = true)
            fetchShoppingList()
        }
    }

    // Names, pictures and categories for the list rows, sorted for display
    private fun enrichListItems(items: List<ShoppingListItem>, allProducts: List<ProductDetails>): List<ShoppingListItem> {
        val productsById = allProducts.associateBy { it.id }
        return items.map { item ->
            val product = productsById[item.product_id]
            val group = cachedGroups.find { it.id == product?.product_group_id }

            item.copy(
                product_name = product?.name ?: item.product_name,
                product_picture_file_name = product?.picture_file_name ?: item.product_picture_file_name,
                category_name = group?.name ?: "Uncategorized"
            )
        }.sortedWith(compareBy({ it.category_name }, { if (isItemDone(it)) 1 else 0 }, { it.product_name ?: it.note }))
    }

    private fun isItemDone(item: ShoppingListItem): Boolean {
        val d = item.done
        return if (d is Boolean) d
        else if (d is String) d == "1"
        else (d as? Number)?.toInt() == 1
    }

    private suspend fun fetchShoppingList() {
        try {
            val items = enrichListItems(api.getShoppingList(), api.getProducts())
            // Keep an open purchase sheet across a refresh (e.g. a row tapped meanwhile)
            val sheet = (_state.value as? AppState.ShoppingList)?.sheet
            _state.value = AppState.ShoppingList(items, sheet = sheet)
        } catch (e: Exception) {
            Log.e("BasilDebug", "Failed to fetch shopping list", e)
            showError("Failed to fetch shopping list", e)
        }
    }

    fun markItemAsDone(item: ShoppingListItem) {
        viewModelScope.launch {
            try {
                val newDone = if (isItemDone(item)) 0 else 1
                api.updateShoppingListItem(item.id, mapOf("done" to newDone))
                fetchShoppingList() // Refresh
            } catch (e: Exception) {
                Log.e("BasilDebug", "Failed to update item", e)
            }
        }
    }

    // ---- Shopping mode: a scan on the list purchases the item ----

    // What Undo needs to put back for the purchase the open sheet shows
    private data class ShoppingPurchase(
        val sheetId: Long,
        val transactionId: String,
        val stockId: String?,
        // The list row as it was before the scan changed it
        val listItemBefore: ShoppingListItem?
    )

    private var lastShoppingPurchase: ShoppingPurchase? = null
    private val shoppingMutex = Mutex()

    private suspend fun purchaseFromShoppingList(barcode: String) {
        (_state.value as? AppState.ShoppingList)?.let { _state.value = it.copy(isProcessing = true) }
        try {
            // 1. Identify. A barcode Grocy doesn't know is created from
            // OpenFoodFacts right here; Gemini fills in the rest later.
            var isNew = false
            val response = try {
                api.getProductByBarcode(barcode)
            } catch (e: HttpException) {
                if (e.code() != 400) throw e
                try {
                    api.externalBarcodeLookup(barcode, true)
                } catch (ex: Exception) {
                    Log.w("BasilDebug", "External lookup failed: ${ex.message}")
                }
                isNew = true
                try {
                    api.getProductByBarcode(barcode)
                } catch (e2: HttpException) {
                    if (e2.code() == 400) {
                        flagShoppingScanError("Barcode not recognized", null)
                        return
                    }
                    throw e2
                }
            }
            val product = response.product
            val amount = response.barcode?.amount ?: fetchBarcodeDetails(barcode)?.amount ?: 1.0
            val doNotBuy = isDoNotBuy(product.id)

            // 2. Purchase with what's known now. No waiting on Gemini for a
            // price, and no date prompt: the background job fills both in.
            val knownPrice = (response.last_price ?: product.default_price)?.takeIf { it > 0.0 }
            val strategy = cachedGroups.find { it.id == product.product_group_id }?.userfields?.expiration_strategy ?: "ai_estimate"
            val tracksExpiry = strategy.isNotEmpty() && strategy != "not_required"
            val shelfLife = product.default_best_before_days
            val bestBefore = if (tracksExpiry && !isNew && shelfLife > 0) {
                LocalDate.now().plusDays(shelfLife.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            } else null
            val needsBestBefore = tracksExpiry && bestBefore == null

            val log = api.addStock(product.id, AddStockRequest(amount, knownPrice, bestBefore))
            val stockId = log.firstOrNull()?.stock_id
            val transactionId = log.firstNotNullOfOrNull { it.transaction_id }

            // 3. Check it off the list: the product's own row, else its parent's
            // (a list entry for "Ketchup" is covered by any ketchup).
            val listBefore = api.getShoppingList()
            val match = listBefore.firstOrNull { it.product_id == product.id && !isItemDone(it) }
                ?: product.parent_product_id?.let { parentId ->
                    listBefore.firstOrNull { it.product_id == parentId && !isItemDone(it) }
                }
            var listStatus = ListStatus.NOT_ON_LIST
            var remaining = 0.0
            if (match != null) {
                val left = match.amount - amount
                if (left > 0.0001) {
                    api.updateShoppingListItem(match.id, mapOf("amount" to left))
                    listStatus = ListStatus.REDUCED
                    remaining = left
                } else {
                    api.updateShoppingListItem(match.id, mapOf("done" to 1))
                    listStatus = ListStatus.CHECKED_OFF
                }
            }

            // 4. Hand the rest to the background job
            val needsPrice = knownPrice == null
            val needsParent = product.parent_product_id == null && (product.no_own_stock ?: 0) == 0
            var workId: UUID? = null
            if (enrichment != null && geminiApiKey != null && (isNew || needsPrice || needsBestBefore || needsParent)) {
                workId = enrichment.enqueue(EnrichmentRequest(product.id, isNew, needsPrice, needsBestBefore, stockId))
            }

            // 5. Show it
            val allProducts = api.getProducts()
            val items = enrichListItems(api.getShoppingList(), allProducts)
            val newStock = api.getProductById(product.id).stock_amount ?: 0.0
            val (parentName, siblings) = loadSiblings(product, allProducts)

            val sheetId = System.nanoTime()
            lastShoppingPurchase = transactionId?.let { ShoppingPurchase(sheetId, it, stockId, match) }
            val sheet = PurchaseSheet(
                id = sheetId,
                product = product,
                amount = amount,
                newStock = newStock,
                listStatus = listStatus,
                remainingOnList = remaining,
                isDoNotBuy = doNotBuy,
                isNewProduct = isNew,
                parentName = parentName,
                siblings = siblings,
                siblingsPending = workId != null && needsParent,
                canUndo = transactionId != null
            )
            _state.value = AppState.ShoppingList(items = items, newlyAddedProductId = match?.product_id, sheet = sheet)
            if (doNotBuy) playSound(ScanSound.ERROR)

            if (workId != null && needsParent) watchEnrichment(workId, sheetId, product.id)
        } catch (e: HttpException) {
            flagShoppingScanError("HTTP ${e.code()}: Purchase failed", e)
        } catch (e: Exception) {
            Log.e("BasilDebug", "Shopping purchase failed", e)
            flagShoppingScanError("Connection Error: Purchase failed", e)
        }
    }

    private fun flagShoppingScanError(message: String, cause: Throwable?) {
        val current = _state.value as? AppState.ShoppingList ?: return
        _state.value = current.copy(
            scanErrorTrigger = System.currentTimeMillis(),
            scanError = message,
            isProcessing = false,
            sheet = null
        )
        playSound(errorSoundFor(cause))
    }

    private suspend fun isDoNotBuy(productId: Int): Boolean = try {
        api.getProductUserfields(productId)["do_not_buy"]?.let {
            it == true || it == 1 || it == "1" || it.toString().lowercase() == "true"
        } ?: false
    } catch (e: Exception) {
        Log.e("BasilDebug", "Failed to check do_not_buy userfield: ${e.message}")
        false
    }

    // The scanned product's parent and every other product under it that's in
    // stock (the parent too, if it holds stock of its own).
    private suspend fun loadSiblings(product: ProductDetails, allProducts: List<ProductDetails>): Pair<String?, List<SiblingStock>> {
        val parentId = product.parent_product_id ?: return null to emptyList()
        val parent = allProducts.find { it.id == parentId }
        val stock = try {
            api.getCurrentStock().associate { it.product_id to it.amount }
        } catch (e: Exception) {
            Log.e("BasilDebug", "Failed to fetch current stock: ${e.message}")
            return parent?.name to emptyList()
        }
        val siblings = allProducts
            .filter { it.id != product.id && (it.parent_product_id == parentId || it.id == parentId) }
            .mapNotNull { p -> stock[p.id]?.takeIf { it > 0.0 }?.let { SiblingStock(p.name, it) } }
            .sortedByDescending { it.amount }
        return parent?.name to siblings
    }

    // Once the background job has found the parent, fill in the open sheet's siblings
    private fun watchEnrichment(workId: UUID, sheetId: Long, productId: Int) {
        val scheduler = enrichment ?: return
        viewModelScope.launch {
            try {
                scheduler.observe(workId).first { it == null || it.state.isFinished }
                if ((_state.value as? AppState.ShoppingList)?.sheet?.id != sheetId) return@launch
                val (product, allProducts) = coroutineScope {
                    val product = async { api.getProductById(productId).product }
                    val all = async { api.getProducts() }
                    product.await() to all.await()
                }
                val (parentName, siblings) = loadSiblings(product, allProducts)
                val current = _state.value as? AppState.ShoppingList ?: return@launch
                val sheet = current.sheet?.takeIf { it.id == sheetId } ?: return@launch
                _state.value = current.copy(sheet = sheet.copy(parentName = parentName, siblings = siblings, siblingsPending = false))
            } catch (e: Exception) {
                Log.w("BasilDebug", "Couldn't refresh siblings: ${e.message}")
                val current = _state.value as? AppState.ShoppingList ?: return@launch
                val sheet = current.sheet?.takeIf { it.id == sheetId } ?: return@launch
                _state.value = current.copy(sheet = sheet.copy(siblingsPending = false))
            }
        }
    }

    fun undoShoppingPurchase() {
        val state = _state.value as? AppState.ShoppingList ?: return
        val sheet = state.sheet ?: return
        val purchase = lastShoppingPurchase?.takeIf { it.sheetId == sheet.id } ?: return
        if (sheet.isUndoing || sheet.isUndone) return
        _state.value = state.copy(sheet = sheet.copy(isUndoing = true))

        viewModelScope.launch {
            shoppingMutex.withLock {
                try {
                    api.undoTransaction(purchase.transactionId)
                    purchase.stockId?.let { enrichment?.cancelStockEntryFix(it) }
                    purchase.listItemBefore?.let {
                        api.updateShoppingListItem(it.id, mapOf("amount" to it.amount, "done" to 0))
                    }
                    lastShoppingPurchase = null

                    val items = enrichListItems(api.getShoppingList(), api.getProducts())
                    val newStock = api.getProductById(sheet.product.id).stock_amount ?: 0.0
                    val current = _state.value as? AppState.ShoppingList
                    val currentSheet = current?.sheet?.takeIf { it.id == sheet.id } ?: sheet
                    _state.value = AppState.ShoppingList(
                        items = items,
                        sheet = currentSheet.copy(isUndoing = false, isUndone = true, newStock = newStock)
                    )
                    playSound(ScanSound.UNDONE)
                } catch (e: Exception) {
                    val message = if (e is HttpException) {
                        val body = e.response()?.errorBody()?.string().orEmpty()
                        if (e.code() == 400 && body.contains("undone", ignoreCase = true)) "Already undone" else "HTTP ${e.code()}: Undo failed"
                    } else "Connection Error: Undo failed"
                    val current = _state.value as? AppState.ShoppingList
                    if (current != null) {
                        _state.value = current.copy(
                            scanError = message,
                            scanErrorTrigger = System.currentTimeMillis(),
                            sheet = current.sheet?.takeIf { it.id == sheet.id }?.copy(isUndoing = false)
                        )
                    }
                    playSound(errorSoundFor(e))
                }
            }
        }
    }

    // Clears a shopping error once its timer runs out, unless a newer error replaced it
    fun clearShoppingScanError(trigger: Long) {
        val state = _state.value as? AppState.ShoppingList ?: return
        if (state.scanErrorTrigger == trigger && state.scanError != null) {
            _state.value = state.copy(scanError = null)
        }
    }

    fun dismissPurchaseSheet(sheetId: Long) {
        val state = _state.value as? AppState.ShoppingList ?: return
        if (state.sheet?.id == sheetId && state.sheet.isUndoing.not()) {
            _state.value = state.copy(sheet = null)
        }
    }

    fun onBarcodeScanned(barcode: String) {
        if (_state.value is AppState.Loading) {
            Log.d("BasilDebug", "Scan ignored: App is busy.")
            return
        }

        val currentState = _state.value
        if (currentState is AppState.NeedsDate) {
            _state.value = currentState.copy(scanErrorTrigger = System.currentTimeMillis())
            playSound(ScanSound.ERROR)
            return
        }
        if (currentState is AppState.ShoppingList) {
            if (isRejectedScan(barcode)) {
                // e.g. a URL QR code. Never sent to Grocy; flagged in the list instead.
                Log.d("BasilDebug", "Scan rejected: $barcode")
                _state.value = currentState.copy(scanErrorTrigger = System.currentTimeMillis(), scanError = INVALID_SCAN_MESSAGE)
                playSound(ScanSound.ERROR)
                return
            }
            val now = System.currentTimeMillis()
            if (barcode == lastScannedBarcode && (now - lastScanTime) < 1500) {
                Log.d("BasilDebug", "Scan ignored: Duplicate debounced.")
                return
            }
            lastScannedBarcode = barcode
            lastScanTime = now
            // Scans queue up rather than overlap: each one is a purchase.
            viewModelScope.launch {
                shoppingMutex.withLock { purchaseFromShoppingList(barcode) }
            }
            return
        }
        if (currentState is AppState.LinkingChild) {
            if (isRejectedScan(barcode)) {
                Log.d("BasilDebug", "Scan rejected: $barcode")
                showError(INVALID_SCAN_MESSAGE)
                return
            }
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

        if (isRejectedScan(barcode)) {
            // e.g. a URL QR code. Never sent to Grocy, but still shown as an
            // error so the scan doesn't look like it went unnoticed.
            Log.d("BasilDebug", "Scan rejected: $barcode")
            showError(INVALID_SCAN_MESSAGE)
            return
        }

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
        var currentProduct: ProductDetails? = null
        
        _state.value = AppState.Loading("Transferring to $locationName...", showGrocy = true)
        try {
            val response = api.getProductByBarcode(barcode)
            val product = response.product
            currentProduct = product

            // Determine how much the barcode is "worth" (e.g. 12 for a multipack)
            val bDetails = fetchBarcodeDetails(barcode)
            val barcodeQuantity = response.barcode?.amount ?: bDetails?.amount ?: 1.0

            // Find the actual current location of the stock that ISN'T the destination
            val stockEntries = api.getStockEntries(product.id)
            
            // Calculate total available to move (anything NOT at the destination)
            val availableOutsideDestination = stockEntries.filter { it.location_id != locationId }
            val totalAvailableToMove = availableOutsideDestination.sumOf { it.amount }

            if (totalAvailableToMove <= 0) {
                showError("No stock found outside of $locationName.")
                return
            }

            // Determine how much to move: the barcode amount, or whatever we have available
            val moveAmount = minOf(barcodeQuantity, totalAvailableToMove)

            // Pick the first source location that has stock
            val sourceEntry = availableOutsideDestination.firstOrNull { it.amount > 0 }
            val sourceLocationId = sourceEntry?.location_id ?: product.location_id

            if (sourceLocationId == null) {
                showError("Unable to determine source location.")
                return
            }

            // Relocate the stock
            api.transferStock(
                product.id,
                TransferStockRequest(
                    amount = moveAmount,
                    location_id_to = locationId,
                    location_id_from = sourceLocationId
                )
            )

            val sourceLocName = _locations.value.find { it.id == sourceLocationId }?.name ?: "Unknown"
            val amountStr = if (moveAmount % 1.0 == 0.0) moveAmount.toInt().toString() else moveAmount.toString()

            _state.value = AppState.Success(
                message = "Transferred!",
                stockMessage = "$amountStr units of ${product.name} transferred from $sourceLocName to $locationName",
                product = product,
                lastScannedBarcode = barcode,
                addedAmount = moveAmount
            )
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: ""
            Log.e("BasilDebug", "Transfer HTTP Error ${e.code()}: $errorBody")

            if (e.code() == 400) {
                showError("Product not found in Grocy.")
            } else if (e.code() == 405) {
                val detail = if (currentProduct?.is_parent == 1) " (Parent Product)" else ""
                showError("Transfer not allowed$detail. Check settings.")
            } else {
                showError("Transfer failed: HTTP ${e.code()}", e)
            }
        } catch (e: Exception) {
            Log.e("BasilDebug", "Transfer failed", e)
            showError("Connection Error: Check your network.", e)
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
                showError("Product not found in Grocy.")
                return
            }

            _state.value = AppState.Loading("Looking up externally...", showOff = true)
            var externalLookupError: String? = null
            try {
                api.externalBarcodeLookup(barcode, true)
            } catch (ex: Exception) {
                Log.w("BasilDebug", "External lookup failed: ${ex.message}")
                if (ex is java.io.IOException) {
                    externalLookupError = "Network error during external lookup."
                } else if (ex is HttpException) {
                    externalLookupError = "OpenFoodFacts lookup failed (HTTP ${ex.code()})."
                }
            }

            try {
                val newResponse = api.getProductByBarcode(barcode)
                currentProduct = newResponse.product
                knownPrice = newResponse.last_price ?: newResponse.product.default_price
                val bDetails = fetchBarcodeDetails(barcode)
                scanAmount = newResponse.barcode?.amount ?: bDetails?.amount ?: 1.0
                barcodeId = newResponse.barcode?.id ?: bDetails?.id
                isNewlyAdded = true
            } catch (e: HttpException) {
                if (e.code() == 400) {
                    val detail = if (externalLookupError != null) "\n($externalLookupError)" else ""
                    showError("Barcode not recognized.$detail")
                } else {
                    showError("HTTP ${e.code()}: Unable to identify product.", e)
                }
                return
            } catch (e: Exception) {
                showError("Connection error: ${e.message}", e)
                return
            }
        } catch (e: Exception) {
            showError("Connection Error: Check your network.", e)
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

    // Returns null when the product has no parent or its siblings have no stock
    private suspend fun fetchParentStock(product: ProductDetails?, ownAmount: Double): ParentStock? {
        val parentId = product?.parent_product_id ?: return null
        return try {
            val parent = api.getProductById(parentId)
            val familyTotal = parent.stock_amount_aggregated ?: parent.stock_amount ?: 0.0
            val others = familyTotal - ownAmount
            if (others > 0.0) ParentStock(parent.product.name, others) else null
        } catch (e: Exception) {
            Log.e("BasilDebug", "Failed to fetch parent stock: ${e.message}")
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
                    val canPrint = rawEntries.isNotEmpty()
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
                    val parentStock = fetchParentStock(product, rawEntries.sumOf { it.amount })
                    _state.value = AppState.InventoryResult(product, groupedEntries, canOpen = canOpen, canPrint = canPrint, parentStock = parentStock)
                } catch (e: Exception) {
                    showError("Failed to fetch stock entries.", e)
                }
            }
            AppMode.CONSUME -> confirmAction(product.id, amount, null, null)
            AppMode.PURCHASE -> {
                if (isDoNotBuy(product.id)) {
                    var autoPrint = false
                    if (barcodeId != null) {
                        try {
                            val uf = api.getBarcodeUserfields(barcodeId)
                            autoPrint = (uf["auto_print_label"]?.toString() ?: "0") == "1"
                        } catch (_: Exception) {}
                    }
                    _state.value = AppState.DoNotBuyWarning(
                        product = product,
                        estimatedPrice = knownPrice,
                        amount = amount,
                        autoPrint = autoPrint,
                        scannedBarcode = scannedBarcode
                    )
                    return
                }

                var autoPrint = false
                if (barcodeId != null) {
                    try {
                        val uf = api.getBarcodeUserfields(barcodeId)
                        autoPrint = (uf["auto_print_label"]?.toString() ?: "0") == "1"
                    } catch (_: Exception) {}
                }

                continuePurchaseProcessing(product, knownPrice, amount, autoPrint, scannedBarcode)
            }
            AppMode.BATCH_MOVE -> {
                // Handled directly in onBarcodeScanned/processBatchMove
            }
        }
    }

    fun proceedAfterDoNotBuyWarning(product: ProductDetails, estimatedPrice: Double?, amount: Double, autoPrint: Boolean, scannedBarcode: String?) {
        viewModelScope.launch {
            continuePurchaseProcessing(product, estimatedPrice, amount, autoPrint, scannedBarcode)
        }
    }

    private suspend fun continuePurchaseProcessing(product: ProductDetails, knownPrice: Double? = null, amount: Double = 1.0, autoPrint: Boolean = false, scannedBarcode: String? = null) {
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

        when (strategy) {
            "user_entry" -> {
                _state.value = AppState.NeedsDate(product, estimatedPrice, amount, autoPrint)
            }
            "ai_estimate" -> {
                val daysToAdd = shelfLife.toLong()
                val autoCalculatedDate = LocalDate.now().plusDays(daysToAdd).format(
                    DateTimeFormatter.ISO_LOCAL_DATE)
                confirmAction(product.id, amount, autoCalculatedDate, estimatedPrice, autoPrint, scannedBarcode)
            }
            "not_required", "" -> {
                confirmAction(product.id, amount, null, estimatedPrice, autoPrint, scannedBarcode)
            }
        }
    }

    fun confirmAction(productId: Int, amount: Double, expireDate: String?, price: Double?, autoPrint: Boolean = false, scannedBarcode: String? = null) {
        if (isProcessingAction) return
        isProcessingAction = true

        viewModelScope.launch {
            _state.value = AppState.Loading(if (_currentMode.value == AppMode.PURCHASE) "Adding stock..." else "Consuming stock...", showGrocy = true)
            var lastStockUuid: String? = null
            var consumeTransactionId: String? = null

            try {
                if (_currentMode.value == AppMode.PURCHASE) {
                    val result = api.addStock(productId, AddStockRequest(amount, price, expireDate))
                    lastStockUuid = result.firstOrNull()?.stock_id
                } else {
                    val result = api.consumeStock(productId, ConsumeStockRequest(amount))
                    // No id means no Undo button, rather than a button that can't work.
                    consumeTransactionId = result.firstNotNullOfOrNull { it.transaction_id }
                }

                val updatedData = api.getProductById(productId)
                val remaining = updatedData.stock_amount ?: 0.0
                val stockStr = if (remaining % 1.0 == 0.0) remaining.toInt().toString() else remaining.toString()
                val parentStock = fetchParentStock(updatedData.product, remaining)

                _state.value = AppState.Success(
                    message = "Success!",
                    stockMessage = "${if (_currentMode.value == AppMode.PURCHASE) "New stock level" else "Remaining stock"}: $stockStr",
                    product = updatedData.product,
                    currentStock = remaining,
                    stockId = lastStockUuid,
                    addedAmount = if (_currentMode.value == AppMode.PURCHASE) amount else 1.0,
                    lastScannedBarcode = scannedBarcode,
                    lastPrice = price,
                    lastExpireDate = expireDate,
                    parentStock = parentStock,
                    transactionId = consumeTransactionId
                )

                if (autoPrint && _currentMode.value == AppMode.PURCHASE) {
                    performQuickAction(productId, "print", lastStockUuid, amount, announce = true)
                }

            } catch (e: HttpException) {
                if (_currentMode.value == AppMode.CONSUME && e.code() == 400) {
                    showError("No stock found to consume!")
                } else {
                    showError("HTTP ${e.code()}: Action failed", e)
                }
            } catch (e: Exception) {
                showError("Connection Error: Action failed", e)
            } finally {
                isProcessingAction = false
            }

        }
    }

    fun undoConsume() {
        val success = _state.value as? AppState.Success ?: return
        val transactionId = success.transactionId ?: return
        val product = success.product ?: return
        if (isProcessingAction) return
        isProcessingAction = true

        viewModelScope.launch {
            _state.value = AppState.Loading("Undoing...", showGrocy = true)
            try {
                api.undoTransaction(transactionId)

                val updatedData = api.getProductById(product.id)
                val remaining = updatedData.stock_amount ?: 0.0
                val stockStr = if (remaining % 1.0 == 0.0) remaining.toInt().toString() else remaining.toString()

                _state.value = AppState.Success(
                    message = "Undone",
                    stockMessage = "Remaining stock: $stockStr",
                    product = updatedData.product,
                    currentStock = remaining,
                    parentStock = fetchParentStock(updatedData.product, remaining),
                    isUndone = true
                )
                playSound(ScanSound.UNDONE)
            } catch (e: HttpException) {
                val body = e.response()?.errorBody()?.string().orEmpty()
                showError(
                    if (e.code() == 400 && body.contains("undone", ignoreCase = true)) "Already undone"
                    else "HTTP ${e.code()}: Undo failed",
                    e
                )
            } catch (e: Exception) {
                showError("Connection Error: Undo failed", e)
            } finally {
                isProcessingAction = false
            }
        }
    }

    // announce: play the "printed" sound as printing starts. Used for the
    // purchase auto-print, which happens without the user tapping anything.
    fun performQuickAction(productId: Int, action: String, stockId: String? = null, amount: Double = 1.0, announce: Boolean = false) {
        viewModelScope.launch {
            try {
                when(action) {
                    "shopping_list" -> {
                        val currentState = _state.value
                        if (currentState is AppState.Success) {
                            _state.value = currentState.copy(isAddingToList = true)
                        } else if (currentState is AppState.InventoryResult) {
                            _state.value = currentState.copy(isAddingToList = true)
                        }
                        api.addToShoppingList(AddToShoppingListRequest(product_id = productId, amount = 1))
                    }
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
                        }
                    }
                    "print" -> {
                        val printCount = if (amount > 0) Math.ceil(amount).toInt() else 1
                        val successState = _state.value as? AppState.Success
                        if (successState != null) {
                            _state.value = successState.copy(isPrinting = true)
                        } else {
                            val inventoryState = _state.value as? AppState.InventoryResult
                            if (inventoryState != null) {
                                _state.value = inventoryState.copy(isPrinting = true)
                            }
                        }
                        // With the pulsing print button, not after the label
                        // requests return. A failed print still follows with
                        // the error sound.
                        if (announce) playSound(ScanSound.PRINTED)

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
                    "do_not_buy" -> {
                        // do_not_buy is a checkbox userfield (created in MainActivity), not a product column
                        api.updateProductUserfields(productId, mapOf("do_not_buy" to "1"))
                        val successState = _state.value as? AppState.Success
                        if (successState != null) {
                            _state.value = successState.copy(isDoNotBuySet = true)
                        }
                    }
                }
            } catch (e: Exception) {
                showError("Connection Error: Quick action failed", e)
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
                showError("Connection Error: Linking failed", e)
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
