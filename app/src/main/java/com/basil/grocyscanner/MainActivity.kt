package com.basil.grocyscanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// --- BRANDING COLORS ---
val DeepPurple = Color(0xFF5D3A50)
val DarkerHeaderPurple = Color(0xFF422939)
val SuccessGreen = Color(0xFFA5D6A7)
val ErrorRed = Color(0xFFEF9A9A)

// --- DATA MODELS ---
data class ProductResponse(
    val product: ProductDetails,
    val stock_amount: Double?
)

data class ProductDetails(
    val id: Int,
    val name: String,
    val category_id: Int?,
    val default_best_before_days: Int
)

data class AddStockRequest(
    val amount: Int,
    val best_before_date: String? = null,
    val transaction_type: String = "purchase"
)

data class ConsumeStockRequest(
    val amount: Int,
    val transaction_type: String = "consume",
    val spoiled: Boolean = false
)

data class StockEntry(
    val id: Int,
    val amount: Double,
    val best_before_date: String?
)

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

    @GET("stock/barcodes/external-lookup/{barcode}")
    suspend fun externalBarcodeLookup(
        @Path("barcode") barcode: String,
        @Query("add") add: Boolean = true
    ): Any
}

// --- VIEWMODEL ---
enum class AppMode { PURCHASE, CONSUME, INVENTORY }

class ScannerViewModel(private val api: GrocyApi) : ViewModel() {
    private val categoriesRequiringDate = listOf(3, 5, 8)

    sealed class AppState {
        object Idle : AppState()
        data class Loading(val message: String = "Communicating with Grocy...") : AppState()
        data class NeedsDate(val product: ProductDetails) : AppState()
        data class Success(val message: String, val stockMessage: String = "") : AppState()
        data class Error(val error: String) : AppState()
        data class InventoryResult(val product: ProductDetails, val entries: List<StockEntry>) : AppState()
    }

    private val _state = MutableStateFlow<AppState>(AppState.Idle)
    val state: StateFlow<AppState> = _state

    private val _currentMode = MutableStateFlow(AppMode.PURCHASE)
    val currentMode: StateFlow<AppMode> = _currentMode

    fun setMode(mode: AppMode) {
        _currentMode.value = mode
        resetState()
    }

    fun onBarcodeScanned(barcode: String) {
        if (_state.value is AppState.Loading) {
            Log.d("GrocyDebug", "Scan ignored: App is already processing an intent.")
            return
        }

        viewModelScope.launch {
            _state.value = AppState.Loading()
            delay(200)

            try {
                val response = api.getProductByBarcode(barcode)
                processFoundProduct(response.product)
            } catch (e: HttpException) {
                if (_currentMode.value == AppMode.INVENTORY) {
                    _state.value = AppState.Error("Product not found in database.")
                    return@launch
                }

                _state.value = AppState.Loading("Looking up new product info...")
                try {
                    api.externalBarcodeLookup(barcode, true)
                } catch (ex: Exception) {
                    Log.w("GrocyDebug", "External lookup timeout/error.")
                }
                try {
                    val newResponse = api.getProductByBarcode(barcode)
                    processFoundProduct(newResponse.product)
                } catch (ex: Exception) {
                    _state.value = AppState.Error("Unable to identify new product.\n\nAdd manually in Grocy.")
                }
            } catch (e: Exception) {
                _state.value = AppState.Error("Network Error: ${e.message}")
            }
        }
    }

    private fun processFoundProduct(product: ProductDetails) {
        when (_currentMode.value) {
            AppMode.INVENTORY -> {
                viewModelScope.launch {
                    try {
                        _state.value = AppState.Loading("Fetching inventory data...")
                        val rawEntries = api.getStockEntries(product.id)

                        val groupedEntries = rawEntries
                            .groupBy {
                                if (it.best_before_date.isNullOrBlank()) "2999-12-31" else it.best_before_date
                            }
                            .map { mapEntry ->
                                StockEntry(
                                    id = mapEntry.value.first().id,
                                    amount = mapEntry.value.sumOf { it.amount },
                                    best_before_date = mapEntry.key
                                )
                            }
                            .sortedBy { it.best_before_date }

                        _state.value = AppState.InventoryResult(product, groupedEntries)
                    } catch (e: Exception) {
                        Log.e("GrocyDebug", "Inventory grouping error: ${e.message}", e)
                        _state.value = AppState.Error("Failed to fetch or group stock entries.")
                    }
                }
            }
            AppMode.CONSUME -> confirmAction(product.id, 1, null)
            AppMode.PURCHASE -> {
                if (product.category_id in categoriesRequiringDate || product.default_best_before_days > 0) {
                    _state.value = AppState.NeedsDate(product)
                } else {
                    confirmAction(product.id, 1, null)
                }
            }
        }
    }

    fun confirmAction(productId: Int, amount: Int, expireDate: String?) {
        viewModelScope.launch {
            _state.value = AppState.Loading(if (_currentMode.value == AppMode.PURCHASE) "Adding to stock..." else "Consuming stock...")
            try {
                if (_currentMode.value == AppMode.PURCHASE) {
                    api.addStock(productId, AddStockRequest(amount, expireDate))
                } else {
                    api.consumeStock(productId, ConsumeStockRequest(amount))
                }

                val updatedData = api.getProductById(productId)
                val remaining = updatedData.stock_amount ?: 0.0
                val stockStr = if (remaining % 1.0 == 0.0) remaining.toInt().toString() else remaining.toString()

                _state.value = AppState.Success(
                    message = "Success!",
                    stockMessage = "Remaining Stock: $stockStr"
                )

                delay(2000)
                if (_state.value is AppState.Success) {
                    resetState()
                }
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

    fun resetState() { _state.value = AppState.Idle }
}

// --- MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    private var viewModel by mutableStateOf<ScannerViewModel?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.parseColor("#422939")

        val sharedPrefs = getSharedPreferences("GrocyPrefs", MODE_PRIVATE)
        val savedUrl = sharedPrefs.getString("API_URL", null)
        val savedToken = sharedPrefs.getString("API_TOKEN", null)

        if (!savedUrl.isNullOrBlank() && !savedToken.isNullOrBlank()) {
            initializeApi(savedUrl, savedToken)
        }

        setContent {
            MaterialTheme {
                val currentVm = viewModel
                // Create a simple state to track the current screen
                var currentScreen by remember { mutableStateOf("scanner") }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepPurple,
                    contentColor = Color.White
                ) {
                    DisposableEffect(Unit) {
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                val scannedText = intent?.getStringExtra("barcode_data")?.trim()
                                if (scannedText.isNullOrBlank()) return

                                if (scannedText.contains("|")) {
                                    val parts = scannedText.split("|")
                                    if (parts.size >= 2) {
                                        val url = parts[0]
                                        val token = parts[1]
                                        sharedPrefs.edit().putString("API_URL", url).putString("API_TOKEN", token).apply()
                                        initializeApi(url, token)
                                    }
                                } else {
                                    viewModel?.onBarcodeScanned(scannedText)
                                }
                            }
                        }
                        registerReceiver(receiver, IntentFilter("com.basil.grocyscanner.SCAN"),
                            RECEIVER_EXPORTED
                        )
                        onDispose { unregisterReceiver(receiver) }
                    }

                    if (currentVm == null) {
                        UnconfiguredScreen()
                    } else {
                        // Switch between screens based on the state
                        when (currentScreen) {
                            "scanner" -> {
                                GrocyScannerApp(
                                    viewModel = currentVm,
                                    onNavigateToSettings = { currentScreen = "settings" }
                                )
                            }
                            "settings" -> {
                                SettingsScreen(
                                    onNavigateBack = { currentScreen = "scanner" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initializeApi(url: String, token: String) {
        var safeUrl = if (url.endsWith("/")) url else "$url/"
        if (!safeUrl.endsWith("api/")) safeUrl += "api/"

        val logging = HttpLoggingInterceptor { Log.d("GrocyNetwork", it) }
        logging.level = HttpLoggingInterceptor.Level.BODY

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
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

        viewModel = ScannerViewModel(retrofit.create(GrocyApi::class.java))
    }
}

// --- UI SCREENS ---

@Composable
fun UnconfiguredScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.basil_logo),
            contentDescription = "Basil Logo",
            modifier = Modifier
                .size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Configuration Required", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text("1. Generate an API Key in Grocy.\n2. Click the Show QR Code button.\n3. Scan the QR code displayed.", textAlign = TextAlign.Left, color = Color.LightGray)
    }
}

@Composable
fun RowScope.ModeButton(title: String, isSelected: Boolean, onClick: () -> Unit) {
    if (isSelected) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepPurple),
            contentPadding = PaddingValues(0.dp)
        ) { Text(title, fontWeight = FontWeight.Bold, maxLines = 1) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.weight(1f),
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    LaunchedEffect(state) {
        when (state) {
            is ScannerViewModel.AppState.Success, is ScannerViewModel.AppState.InventoryResult -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
            }
            is ScannerViewModel.AppState.NeedsDate -> {
                val pattern = longArrayOf(0, 50, 100, 50, 100, 50)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
            is ScannerViewModel.AppState.Error -> {
                val pattern = longArrayOf(0, 150, 100, 150)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Basil", fontWeight = FontWeight.Bold)
                        Image(
                            painter = painterResource(id = R.drawable.basil_logo),
                            contentDescription = "Basil Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .padding(start = 6.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkerHeaderPurple,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(color = DarkerHeaderPurple) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton("Purchase", currentMode == AppMode.PURCHASE) { viewModel.setMode(AppMode.PURCHASE) }
                    ModeButton("Consume", currentMode == AppMode.CONSUME) { viewModel.setMode(AppMode.CONSUME) }
                    ModeButton("Inventory", currentMode == AppMode.INVENTORY) { viewModel.setMode(AppMode.INVENTORY) }
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val currentState = state) {
                is ScannerViewModel.AppState.Idle -> {
                    Text("Ready to Scan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                is ScannerViewModel.AppState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 6.dp, color = Color.White)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(currentState.message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = Color.White)
                }
                is ScannerViewModel.AppState.NeedsDate -> {
                    ExpirationDatePrompt(
                        product = currentState.product,
                        onSubmit = { date -> viewModel.confirmAction(currentState.product.id, 1, date) },
                        onCancel = { viewModel.resetState() }
                    )
                }
                is ScannerViewModel.AppState.Success -> {
                    Text(currentState.message, style = MaterialTheme.typography.headlineMedium, color = SuccessGreen, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)

                    if (currentState.stockMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = currentState.stockMessage,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is ScannerViewModel.AppState.InventoryResult -> {
                    Text(currentState.product.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

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
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.resetState() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepPurple)
                    ) { Text("Scan Next", fontWeight = FontWeight.Bold) }
                }
                is ScannerViewModel.AppState.Error -> {
                    Text(currentState.error, style = MaterialTheme.typography.titleMedium, color = ErrorRed, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.resetState() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepPurple)
                    ) { Text("Clear Error") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpirationDatePrompt(product: ProductDetails, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    val defaultMillis = remember {
        if (product.default_best_before_days > 0) {
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, product.default_best_before_days)
            }.timeInMillis
        } else {
            System.currentTimeMillis()
        }
    }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = defaultMillis)

    DatePickerDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            Button(onClick = {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val finalDate = datePickerState.selectedDateMillis?.let { sdf.format(Date(it)) } ?: ""
                onSubmit(finalDate)
            }) {
                Text("Save to Stock")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Set Expiration for:", style = MaterialTheme.typography.titleSmall)
            Text(product.name, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
        }
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkerHeaderPurple,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = DeepPurple
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.basil_logo),
                contentDescription = "Basil Logo",
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Basil",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.titleMedium,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Created by Justin Sabourin",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}