package com.basil.grocyscanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TakeoutDining
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// --- BRANDING COLORS ---
val DeepPurple = Color(0xFF5D3A50)
val DarkerHeaderPurple = Color(0xFF422939)
val SuccessGreen = Color(0xFFA5D6A7)
val ErrorRed = Color(0xFFEF9A9A)

// --- DATA MODELS ---
data class ProductResponse(val product: ProductDetails, val stock_amount: Double?)
data class ProductDetails(val id: Int, val name: String, val category_id: Int?, val default_best_before_days: Int, val picture_file_name: String? = null)
data class AddStockRequest(val amount: Int, val price: Double? = null, val best_before_date: String? = null, val transaction_type: String = "purchase")
data class ConsumeStockRequest(val amount: Int, val transaction_type: String = "consume", val spoiled: Boolean = false)
data class OpenStockRequest(val amount: Int = 1)
data class StockEntry(val id: Int, val amount: Double, val best_before_date: String?)
data class GrocyLocation(val id: Int, val name: String)
data class GrocyProductGroup(val id: Int, val name: String)

// --- RETROFIT INTERFACE ---
interface GrocyApi {
    @GET("stock/products/by-barcode/{barcode}")
    suspend fun getProductByBarcode(@Path("barcode") barcode: String): ProductResponse

    @GET("stock/products/{productId}")
    suspend fun getProductById(@Path("productId") productId: Int): ProductResponse

    @GET("stock/products/{productId}/entries")
    suspend fun getStockEntries(@Path("productId") productId: Int): List<StockEntry>

    @POST("stock/products/{productId}/add")
    suspend fun addStock(@Path("productId") productId: Int, @Body request: AddStockRequest)

    @POST("stock/products/{productId}/consume")
    suspend fun consumeStock(@Path("productId") productId: Int, @Body request: ConsumeStockRequest)

    @POST("stock/products/{productId}/open")
    suspend fun openStock(@Path("productId") productId: Int, @Body request: OpenStockRequest)

    @GET("stock/products/{productId}/printlabel")
    suspend fun printLabel(@Path("productId") productId: Int)

    @GET("stock/barcodes/external-lookup/{barcode}")
    suspend fun externalBarcodeLookup(@Path("barcode") barcode: String, @Query("add") add: Boolean = true): Any

    @GET("objects/locations")
    suspend fun getLocations(): List<GrocyLocation>

    @GET("objects/product_groups")
    suspend fun getProductGroups(): List<GrocyProductGroup>

    @PUT("objects/products/{productId}")
    suspend fun updateProduct(@Path("productId") productId: Int, @Body productData: @JvmSuppressWildcards Map<String, Any>)
}

// --- VIEWMODEL ---
enum class AppMode { PURCHASE, CONSUME, INVENTORY }

class ScannerViewModel(private val api: GrocyApi, private val geminiApiKey: String?) : ViewModel() {
    private val categoriesRequiringDate = listOf(3, 5, 8)
    private var cachedLocations = listOf<GrocyLocation>()
    private var cachedGroups = listOf<GrocyProductGroup>()

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
            _state.value = AppState.Loading("Identifying barcode...")
            delay(150)

            var isNewlyAdded = false
            var currentProduct: ProductDetails?

            try {
                val response = api.getProductByBarcode(barcode)
                currentProduct = response.product
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
                    isNewlyAdded = true
                } catch (_: Exception) {
                    _state.value = AppState.Error("Unable to identify product.\nAdd manually in Grocy.")
                    return@launch
                }
            } catch (e: Exception) {
                _state.value = AppState.Error("Network Error: ${e.message}")
                return@launch
            }

            currentProduct.let { product ->
                if (isNewlyAdded && generativeModel != null) {
                    _state.value = AppState.Loading("Analyzing product...")
                    enrichProductWithGemini(product.id, product.name)
                }
                processFoundProduct(product)
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

    private suspend fun processFoundProduct(product: ProductDetails) {
        when (_currentMode.value) {
            AppMode.INVENTORY -> {
                try {
                    _state.value = AppState.Loading("Fetching inventory data...")
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
                var estimatedPrice: Double? = null

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

                if (product.category_id in categoriesRequiringDate || product.default_best_before_days > 0) {
                    _state.value = AppState.NeedsDate(product, estimatedPrice)
                } else {
                    confirmAction(product.id, 1, null, estimatedPrice)
                }
            }
        }
    }

    fun confirmAction(productId: Int, amount: Int, expireDate: String?, price: Double?) {
        viewModelScope.launch {
            _state.value = AppState.Loading(if (_currentMode.value == AppMode.PURCHASE) "Adding to stock..." else "Consuming stock...")
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
                    _state.value = AppState.Error("No stock found to consume!")
                } else {
                    _state.value = AppState.Error("Action failed: HTTP ${e.code()}")
                }
            } catch (e: Exception) {
                _state.value = AppState.Error("Action failed: ${e.message}")
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

// --- MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    private var viewModel by mutableStateOf<ScannerViewModel?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = "#422939".toColorInt()

        val sharedPrefs = getSharedPreferences("GrocyPrefs", MODE_PRIVATE)
        val savedUrl = sharedPrefs.getString("API_URL", null)
        val savedToken = sharedPrefs.getString("API_TOKEN", null)
        val geminiKey = sharedPrefs.getString("GEMINI_KEY", null)

        if (!savedUrl.isNullOrBlank() && !savedToken.isNullOrBlank()) {
            val aiKeyToUse = if (sharedPrefs.getBoolean("AI_ENABLED", false)) geminiKey else null
            initializeApi(savedUrl, savedToken, aiKeyToUse)
        }

        setContent {
            MaterialTheme {
                val currentVm = viewModel
                var aiEnabledState by remember { mutableStateOf(sharedPrefs.getBoolean("AI_ENABLED", false)) }

                var currentScreen by remember {
                    mutableStateOf(
                        if (savedUrl.isNullOrBlank()) "unconfigured"
                        else if (!sharedPrefs.contains("AI_ENABLED")) "ai_setup"
                        else "scanner"
                    )
                }

                Surface(modifier = Modifier.fillMaxSize(), color = DeepPurple, contentColor = Color.White) {

                    DisposableEffect(currentScreen) {
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                val scannedText = intent?.getStringExtra("barcode_data")?.trim()
                                if (scannedText.isNullOrBlank()) return

                                when (currentScreen) {
                                    "unconfigured" -> {
                                        if (scannedText.contains("|")) {
                                            val parts = scannedText.split("|")
                                            if (parts.size >= 2) {
                                                sharedPrefs.edit {
                                                    putString(
                                                        "API_URL",
                                                        parts[0]
                                                    ).putString("API_TOKEN", parts[1])
                                                }
                                                currentScreen = "ai_setup"
                                            }
                                        }
                                    }
                                    "ai_setup" -> {
                                        sharedPrefs.edit { putString("GEMINI_KEY", scannedText) }
                                        aiEnabledState = true
                                        initializeApi(
                                            sharedPrefs.getString("API_URL", "")!!,
                                            sharedPrefs.getString("API_TOKEN", "")!!,
                                            scannedText
                                        )
                                        currentScreen = "scanner"
                                    }
                                    "scanner" -> {
                                        viewModel?.onBarcodeScanned(scannedText)
                                    }
                                }
                            }
                        }
                        registerReceiver(receiver, IntentFilter("com.basil.grocyscanner.SCAN"), RECEIVER_EXPORTED)
                        onDispose { unregisterReceiver(receiver) }
                    }

                    when (currentScreen) {
                        "unconfigured" -> UnconfiguredScreen()
                        "ai_setup" -> AiSetupScreen(
                            onEnableAi = {
                                sharedPrefs.edit { putBoolean("AI_ENABLED", true) }
                                aiEnabledState = true
                            },
                            onSkip = {
                                sharedPrefs.edit { putBoolean("AI_ENABLED", false) }
                                aiEnabledState = false
                                initializeApi(sharedPrefs.getString("API_URL", "")!!, sharedPrefs.getString("API_TOKEN", "")!!, null)
                                currentScreen = "scanner"
                            }
                        )
                        "scanner" -> currentVm?.let {
                            GrocyScannerApp(it, onNavigateToSettings = { currentScreen = "settings" })
                        }
                        "settings" -> SettingsScreen(
                            isAiEnabled = aiEnabledState,
                            onToggleAi = { enabled ->
                                aiEnabledState = enabled
                                sharedPrefs.edit { putBoolean("AI_ENABLED", enabled) }

                                if (enabled && sharedPrefs.getString("GEMINI_KEY", null).isNullOrBlank()) {
                                    currentScreen = "ai_setup"
                                } else {
                                    initializeApi(
                                        sharedPrefs.getString("API_URL", "")!!,
                                        sharedPrefs.getString("API_TOKEN", "")!!,
                                        if (enabled) sharedPrefs.getString("GEMINI_KEY", null) else null
                                    )
                                }
                            },
                            onLogout = {
                                sharedPrefs.edit { clear() }
                                viewModel = null
                                aiEnabledState = false
                                currentScreen = "unconfigured"
                            },
                            onNavigateBack = { currentScreen = "scanner" }
                        )
                    }
                }
            }
        }
    }

    private fun initializeApi(url: String, token: String, geminiKey: String?) {
        var safeUrl = if (url.endsWith("/")) url else "$url/"
        if (!safeUrl.endsWith("api/")) safeUrl += "api/"

        val logging = HttpLoggingInterceptor { Log.d("GrocyNetwork", it) }
        logging.level = HttpLoggingInterceptor.Level.BODY

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("GROCY-API-KEY", token)
                    .header("Accept", "application/json")

                if (original.body != null) {
                    val oldBody = original.body
                    val newBody = object : RequestBody() {
                        override fun contentType() = "application/json".toMediaTypeOrNull()
                        override fun writeTo(sink: BufferedSink) { oldBody?.writeTo(sink) }
                    }
                    requestBuilder.method(original.method, newBody)
                }
                chain.proceed(requestBuilder.build())
            }.addInterceptor(logging).build()

        val retrofit = Retrofit.Builder().baseUrl(safeUrl).client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()).build()

        viewModel = ScannerViewModel(retrofit.create(GrocyApi::class.java), geminiKey)
    }
}

// --- UI SCREENS ---

@Composable
fun AiSetupScreen(onEnableAi: () -> Unit, onSkip: () -> Unit) {
    var step by remember { mutableIntStateOf(1) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (step == 1) {
            Image(
                painter = painterResource(id = R.drawable.gemini_logo),
                contentDescription = "Gemini Logo",
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text("Enable AI Features?", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Basil can use Google Gemini to estimate expiration rules, predict prices, and organize new products into your categories.", textAlign = TextAlign.Center, color = Color.LightGray)
            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = {
                onEnableAi()
                step = 2
            }, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = DeepPurple)) {
                Text("Enable Gemini AI", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onSkip) { Text("Skip for now", color = Color.LightGray) }

        } else {
            Image(
                painter = painterResource(id = R.drawable.gemini_logo),
                contentDescription = "Gemini Logo",
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("Scan Gemini Key", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("1. Create an API Key\nin Google AI Studio.\n\n2. Generate a QR code\ncontaining only the key.\n\n3.Scan the QR Code now.", textAlign = TextAlign.Left, color = Color.LightGray)
        }
    }
}

@Composable
fun UnconfiguredScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painter = painterResource(id = R.drawable.basil_logo), contentDescription = "Basil Logo", modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Grocy Login Required", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text("1. Generate an API Key in Grocy.\n2. Click the Show QR Code button.\n3. Scan the QR code displayed.", textAlign = TextAlign.Left, color = Color.LightGray)
    }
}

@Composable
fun RowScope.ModeButton(title: String, isSelected: Boolean, onClick: () -> Unit) {
    if (isSelected) {
        Button(
            onClick = onClick, modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = DeepPurple),
            contentPadding = PaddingValues(0.dp)
        ) { Text(title, fontWeight = FontWeight.Bold, maxLines = 1) }
    } else {
        OutlinedButton(
            onClick = onClick, modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, Color.LightGray),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
            contentPadding = PaddingValues(0.dp)
        ) { Text(title, maxLines = 1) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrocyScannerApp(viewModel: ScannerViewModel, onNavigateToSettings: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val context = LocalContext.current

    val vibrator = remember {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    }

    LaunchedEffect(state) {
        when (state) {
            is ScannerViewModel.AppState.Success, is ScannerViewModel.AppState.InventoryResult -> {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            is ScannerViewModel.AppState.NeedsDate, is ScannerViewModel.AppState.Error -> {
                val pattern = longArrayOf(0, 150, 100, 150)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.basil_logo),
                            contentDescription = "Basil Logo",
                            modifier = Modifier.size(36.dp).padding(end = 12.dp)
                        )
                        Text("Basil", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) { Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkerHeaderPurple, titleContentColor = Color.White)
            )
        },
        bottomBar = {
            Surface(color = DarkerHeaderPurple) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton("Purchase", currentMode == AppMode.PURCHASE) { viewModel.setMode(AppMode.PURCHASE) }
                    ModeButton("Consume", currentMode == AppMode.CONSUME) { viewModel.setMode(AppMode.CONSUME) }
                    ModeButton("Inventory", currentMode == AppMode.INVENTORY) { viewModel.setMode(AppMode.INVENTORY) }
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(16.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val currentState = state) {
                is ScannerViewModel.AppState.Idle -> {
                    Box(
                        modifier = Modifier
                            .padding(24.dp)
                            .size(width = 280.dp, height = 180.dp)
                            .drawBehind {
                                val strokeWidthPx = 4.dp.toPx()
                                val cornerLengthPx = 40.dp.toPx()
                                val bracketColor = Color.White.copy(alpha = 0.7f)

                                val w = size.width
                                val h = size.height

                                // Top Left
                                drawLine(bracketColor, Offset(0f, 0f), Offset(cornerLengthPx, 0f), strokeWidthPx)
                                drawLine(bracketColor, Offset(0f, 0f), Offset(0f, cornerLengthPx), strokeWidthPx)

                                // Top Right
                                drawLine(bracketColor, Offset(w, 0f), Offset(w - cornerLengthPx, 0f), strokeWidthPx)
                                drawLine(bracketColor, Offset(w, 0f), Offset(w, cornerLengthPx), strokeWidthPx)

                                // Bottom Left
                                drawLine(bracketColor, Offset(0f, h), Offset(cornerLengthPx, h), strokeWidthPx)
                                drawLine(bracketColor, Offset(0f, h), Offset(0f, h - cornerLengthPx), strokeWidthPx)

                                // Bottom Right
                                drawLine(bracketColor, Offset(w, h), Offset(w - cornerLengthPx, h), strokeWidthPx)
                                drawLine(bracketColor, Offset(w, h), Offset(w, h - cornerLengthPx), strokeWidthPx)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ready to Scan",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
                is ScannerViewModel.AppState.Loading -> {
                    val msg = currentState.message
                    val isAiAction = msg.contains("Analyzing", ignoreCase = true) || msg.contains("Estimating", ignoreCase = true)
                    val isLookupAction = msg.contains("Looking up", ignoreCase = true)

                    if (isAiAction) {
                        Image(
                            painter = painterResource(id = R.drawable.gemini_logo),
                            contentDescription = "Gemini Processing",
                            modifier = Modifier
                                .size(80.dp)
                                .padding(bottom = 16.dp)
                        )
                    } else if (isLookupAction) {
                        Image(
                            painter = painterResource(id = R.drawable.openfoodfacts_logo),
                            contentDescription = "Open Food Facts Lookup",
                            modifier = Modifier
                                .size(80.dp)
                                .padding(bottom = 16.dp)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                }
                is ScannerViewModel.AppState.NeedsDate -> {
                    ExpirationDatePrompt(
                        product = currentState.product,
                        estimatedPrice = currentState.estimatedPrice,
                        onSubmit = { date -> viewModel.confirmAction(currentState.product.id, 1, date, currentState.estimatedPrice) },
                        onCancel = { viewModel.resetState() }
                    )
                }
                is ScannerViewModel.AppState.Success -> {
                    Text(currentState.message, style = MaterialTheme.typography.headlineMedium, color = SuccessGreen, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    if (currentState.stockMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = currentState.stockMessage, style = MaterialTheme.typography.titleMedium, color = Color.LightGray, textAlign = TextAlign.Center)
                    }
                    if (currentState.productId != null && currentState.currentStock > 0.0) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            FilledIconButton(
                                onClick = { viewModel.performQuickAction(currentState.productId, "print") },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White,
                                    contentColor = DeepPurple
                                ),
                                modifier = Modifier.size(64.dp) // Nice, large tap target for a PDA
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Print,
                                    contentDescription = "Print Label",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            FilledIconButton(
                                onClick = { viewModel.performQuickAction(currentState.productId, "open") },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White,
                                    contentColor = DeepPurple
                                ),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TakeoutDining,
                                    contentDescription = "Mark Opened",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                        }
                    }
                    // Only show this when product exists but stock is 0
                    if (currentState.productId != null && currentState.currentStock <= 0.0) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {

                            // Future "Add to Shopping List" Button
                            FilledIconButton(
                                onClick = { /* Future: api.addToShoppingList(productId) */ },
                                enabled = false, // This makes it look grayed out/disabled
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.3f), // Faded background
                                    contentColor = Color.LightGray,
                                    disabledContainerColor = Color.White.copy(alpha = 0.1f),
                                    disabledContentColor = Color.Gray
                                ),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddShoppingCart,
                                    contentDescription = "Add to Shopping List (Coming Soon)",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
                is ScannerViewModel.AppState.InventoryResult -> {

                    Text(
                        text = currentState.product.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val sharedPrefs = context.getSharedPreferences("GrocyPrefs", Context.MODE_PRIVATE)
                    val rawUrl = sharedPrefs.getString("API_URL", "") ?: ""
                    val apiToken = sharedPrefs.getString("API_TOKEN", "") ?: ""

                    var safeUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"
                    if (!safeUrl.endsWith("api/")) safeUrl += "api/"

                    val pictureName = currentState.product.picture_file_name
                    if (!pictureName.isNullOrEmpty()) {
                        val encodedName = Base64.encodeToString(pictureName.toByteArray(), Base64.NO_WRAP)
                        val imageUrl = "${safeUrl}files/productpictures/$encodedName"

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUrl)
                                .addHeader("GROCY-API-KEY", apiToken)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Product Image",
                            modifier = Modifier
                                .size(120.dp)
                                //.border(2.dp, Color.Red) // for positioning debugging!
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentState.entries.isEmpty()) {
                        Text("No items currently in stock.", color = ErrorRed, style = MaterialTheme.typography.titleMedium)
                    } else {
                        Card(colors = CardDefaults.cardColors(containerColor = DarkerHeaderPurple)) {
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Amount", color = Color.LightGray, fontWeight = FontWeight.Bold)
                                    Text("Expires", color = Color.LightGray, fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                                    items(currentState.entries) { entry ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            val amountStr = if (entry.amount % 1.0 == 0.0) entry.amount.toInt().toString() else entry.amount.toString()
                                            Text(amountStr, color = Color.White)
                                            val dateStr = if (entry.best_before_date == "2999-12-31" || entry.best_before_date.isNullOrEmpty()) "Never" else entry.best_before_date
                                            Text(dateStr, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is ScannerViewModel.AppState.Error -> {
                    Text(currentState.error, style = MaterialTheme.typography.titleMedium, color = ErrorRed, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.resetState() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepPurple)) { Text("Clear Error") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpirationDatePrompt(
    product: ProductDetails,
    estimatedPrice: Double?, // Kept this in so it doesn't error out!
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    val defaultMillis = remember {
        if (product.default_best_before_days > 0) {
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, product.default_best_before_days) }.timeInMillis
        } else {
            System.currentTimeMillis()
        }
    }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = defaultMillis)

    Dialog(
        onDismissRequest = onCancel,
        // This disables the default massive margins, giving the dialog room to breathe
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .wrapContentHeight() // Prevents the dialog from stretching off-screen
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Set Expiration for:", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                Text(product.name, style = MaterialTheme.typography.titleSmall, color = DeepPurple, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)

                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = currentDensity.density * 0.7f
                    )
                ) {
                    DatePicker(
                        state = datePickerState,
                        title = null,
                        headline = null,
                        showModeToggle = false,
                        modifier = Modifier.weight(1f, fill = false),
                        colors = DatePickerDefaults.colors(
                            selectedDayContainerColor = DeepPurple, // The solid circle
                            selectedDayContentColor = Color.White,  // The text inside the circle
                            todayContentColor = DeepPurple,         // The text for "Today" if unselected
                            todayDateBorderColor = DeepPurple       // The outline for "Today"
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val finalDate = datePickerState.selectedDateMillis?.let { sdf.format(Date(it)) } ?: ""
                            onSubmit(finalDate)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepPurple, contentColor = Color.White)
                    ) {
                        Text("Save to Stock", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isAiEnabled: Boolean,
    onToggleAi: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkerHeaderPurple, titleContentColor = Color.White)
            )
        },
        containerColor = DeepPurple
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(painter = painterResource(id = R.drawable.basil_logo), contentDescription = "Basil Logo", modifier = Modifier.size(96.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Basil", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Version 1.1.0-AI", style = MaterialTheme.typography.titleMedium, color = Color.LightGray)
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Gemini AI", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Switch(
                    checked = isAiEnabled,
                    onCheckedChange = { onToggleAi(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = DeepPurple, checkedTrackColor = Color.LightGray)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = DeepPurple),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Logout / Disconnect", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Created by Justin Sabourin", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}