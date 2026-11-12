package com.basil.grocyscanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.basil.grocyscanner.data.GrocyApi
import com.basil.grocyscanner.data.UserfieldCreateRequest
import com.basil.grocyscanner.ui.AiSetupScreen
import com.basil.grocyscanner.ui.CameraScanning
import com.basil.grocyscanner.ui.ScannerChoiceScreen
import com.basil.grocyscanner.ui.GrocyScannerApp
import com.basil.grocyscanner.ui.SettingsScreen
import com.basil.grocyscanner.ui.UnconfiguredScreen
import com.basil.grocyscanner.ui.theme.DeepPurple
import com.basil.grocyscanner.viewmodel.ScannerViewModel
import com.basil.grocyscanner.work.EnrichmentScheduler
import com.basil.grocyscanner.data.GeminiConfig
import com.basil.grocyscanner.data.GeminiModels
import com.basil.grocyscanner.data.SecurePrefs
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var viewModel by mutableStateOf<ScannerViewModel?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                "#422939".toColorInt()
            )
        )
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        super.onCreate(savedInstanceState)

        val sharedPrefs = com.basil.grocyscanner.data.SecurePrefs.get(this)
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
                var resetDurationSeconds by remember { mutableStateOf(sharedPrefs.getInt("RESET_DURATION", 4)) }
                // Scan sounds and vibration, both on unless turned off in Settings
                var soundEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("SOUND_ENABLED", true)) }
                var vibrationEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("VIBRATION_ENABLED", true)) }
                var geminiModelState by remember { mutableStateOf(GeminiConfig.model(sharedPrefs)) }

                val hasCamera = remember { CameraScanning.hasCamera(this) }
                // Asked on first run and again after logout. Installs from before camera
                // scanning have no answer saved and stay on the hardware scanner.
                var cameraScanEnabled by remember { mutableStateOf(hasCamera && sharedPrefs.getBoolean(CameraScanning.PREF_KEY, false)) }

                var currentScreen by remember {
                    mutableStateOf(
                        if (savedUrl.isNullOrBlank()) {
                            // First run: ask how this device scans, so the setup QR codes can be read
                            if (hasCamera && !sharedPrefs.contains(CameraScanning.PREF_KEY)) "scanner_choice" else "unconfigured"
                        }
                        else if (!sharedPrefs.contains("AI_ENABLED")) "ai_setup"
                        else "scanner"
                    )
                }

                fun chooseScanner(useCamera: Boolean) {
                    sharedPrefs.edit { putBoolean(CameraScanning.PREF_KEY, useCamera) }
                    cameraScanEnabled = useCamera
                    if (currentScreen == "scanner_choice") currentScreen = "unconfigured"
                }

                // Back to the scanner question, e.g. when the setup QR code won't scan this way
                fun backToScannerChoice() {
                    sharedPrefs.edit { remove(CameraScanning.PREF_KEY) }
                    cameraScanEnabled = false
                    currentScreen = "scanner_choice"
                }

                // Every scan lands here, from the hardware scanner's broadcast or the camera
                fun handleScan(scannedText: String, fromHardware: Boolean) {
                    when (currentScreen) {
                        "scanner_choice" -> {
                            // A hardware scan answers the question; if it was the Grocy QR code, use it too
                            if (fromHardware) {
                                chooseScanner(useCamera = false)
                                handleScan(scannedText, fromHardware)
                            }
                        }
                        "unconfigured" -> {
                            if (scannedText.contains("|")) {
                                val parts = scannedText.split("|")
                                if (parts.size >= 2) {
                                    sharedPrefs.edit {
                                        putString("API_URL", parts[0])
                                        putString("API_TOKEN", parts[1])
                                    }
                                    currentScreen = "ai_setup"
                                }
                            }
                        }
                        "ai_setup" -> {
                            sharedPrefs.edit {
                                putString("GEMINI_KEY", scannedText)
                                putBoolean("AI_ENABLED", true)
                            }
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

                Surface(modifier = Modifier.fillMaxSize(), color = DeepPurple, contentColor = Color.White) {

                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current

                    // Camera scans feed the same path as the hardware scanner
                    val onCameraScan: ((String) -> Unit)? =
                        if (cameraScanEnabled) { code -> handleScan(code, fromHardware = false) } else null

                    DisposableEffect(lifecycleOwner, currentScreen) {
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                val scannedText = intent?.getStringExtra("barcode_data")?.trim()
                                if (scannedText.isNullOrBlank()) return
                                handleScan(scannedText, fromHardware = true)
                            }
                        }

                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                context.registerReceiver(receiver, IntentFilter("com.basil.grocyscanner.SCAN"), RECEIVER_EXPORTED)
                            } else if (event == Lifecycle.Event.ON_PAUSE) {
                                context.unregisterReceiver(receiver)
                            }
                        }

                        lifecycleOwner.lifecycle.addObserver(observer)

                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            try { context.unregisterReceiver(receiver) } catch (e: IllegalArgumentException) { /* Ignored */ }
                        }
                    }

                    when (currentScreen) {
                        "scanner_choice" -> ScannerChoiceScreen(onChoose = { chooseScanner(it) })
                        "unconfigured" -> {
                            // Only devices with a camera were asked the question
                            val onBack: (() -> Unit)? = if (hasCamera) ({ backToScannerChoice() }) else null
                            BackHandler(enabled = onBack != null) { onBack?.invoke() }
                            UnconfiguredScreen(onCameraScan = onCameraScan, onBack = onBack)
                        }
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
                            },
                            onCameraScan = onCameraScan
                        )
                        "scanner" -> currentVm?.let {
                            GrocyScannerApp(
                                it,
                                resetDurationSeconds,
                                onNavigateToSettings = { currentScreen = "settings" },
                                onCameraScan = onCameraScan,
                                soundEnabled = soundEnabled,
                                vibrationEnabled = vibrationEnabled
                            )
                        }
                        "settings" -> {
                            // Back gesture does what the back arrow does
                            BackHandler { currentScreen = "scanner" }
                            SettingsScreen(
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
                                geminiModel = geminiModelState,
                                onGeminiModelChange = { model ->
                                    geminiModelState = model
                                    sharedPrefs.edit { putString(GeminiConfig.PREF_KEY, model) }
                                    // Rebuild so the scanner's own Gemini calls switch too
                                    initializeApi(
                                        sharedPrefs.getString("API_URL", "")!!,
                                        sharedPrefs.getString("API_TOKEN", "")!!,
                                        if (aiEnabledState) sharedPrefs.getString("GEMINI_KEY", null) else null
                                    )
                                },
                                loadGeminiModels = {
                                    val key = sharedPrefs.getString("GEMINI_KEY", null)
                                    if (key.isNullOrBlank()) emptyList() else GeminiModels.fetchFlashModels(key)
                                },
                                resetDurationSeconds = resetDurationSeconds,
                                onResetDurationChange = { duration ->
                                    resetDurationSeconds = duration
                                    sharedPrefs.edit { putInt("RESET_DURATION", duration) }
                                },
                                isSoundEnabled = soundEnabled,
                                onToggleSound = { enabled ->
                                    soundEnabled = enabled
                                    sharedPrefs.edit { putBoolean("SOUND_ENABLED", enabled) }
                                },
                                isVibrationEnabled = vibrationEnabled,
                                onToggleVibration = { enabled ->
                                    vibrationEnabled = enabled
                                    sharedPrefs.edit { putBoolean("VIBRATION_ENABLED", enabled) }
                                },
                                viewModel = currentVm!!,
                                onStartBatchMove = { locId, locName ->
                                    currentVm.startBatchMove(locId, locName)
                                    currentScreen = "scanner"
                                },
                                onLogout = {
                                    // Clears everything, the scanner choice included, and
                                    // starts over from the first-run question
                                    sharedPrefs.edit { clear() }
                                    // Queued lookups read the server at run time, so one left
                                    // waiting could write into the next Grocy connected
                                    WorkManager.getInstance(applicationContext).cancelAllWork()
                                    viewModel = null
                                    aiEnabledState = false
                                    resetDurationSeconds = 4
                                    soundEnabled = true
                                    vibrationEnabled = true
                                    geminiModelState = GeminiConfig.DEFAULT_MODEL
                                    cameraScanEnabled = false
                                    currentScreen = if (hasCamera) "scanner_choice" else "unconfigured"
                                },
                                onNavigateBack = { currentScreen = "scanner" }
                            )
                        }
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
                        override fun contentLength() = oldBody?.contentLength() ?: -1L
                        override fun writeTo(sink: BufferedSink) { oldBody?.writeTo(sink) }
                    }
                    requestBuilder.method(original.method, newBody)
                }
                chain.proceed(requestBuilder.build())
            }.addInterceptor(logging).build()

        val retrofit = Retrofit.Builder().baseUrl(safeUrl).client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()).build()

        val grocyApi = retrofit.create(GrocyApi::class.java)

        lifecycleScope.launch {
            try {
                runInitialSetup(grocyApi)
            } catch (e: Exception) {
                Log.e("BasilDebug", "Schema setup failed, but continuing: ${e.message}")
            }

            viewModel = ScannerViewModel(
                grocyApi,
                geminiKey,
                EnrichmentScheduler(applicationContext),
                GeminiConfig.model(SecurePrefs.get(this@MainActivity))
            )
        }
    }

    private suspend fun runInitialSetup(api: GrocyApi) {
        val existingFields = api.getUserfields()

        // 1. Expiration Strategy for Product Groups
        val expStrategyExists = existingFields.any {
            it.name == "expiration_strategy" && it.entity == "product_groups"
        }
        if (!expStrategyExists) {
            Log.d("BasilDebug", "Injecting expiration_strategy field...")
            api.createUserfield(UserfieldCreateRequest())
        }

        // 2. Auto Print Label for Barcodes
        val autoPrintExists = existingFields.any {
            it.name == "auto_print_label" && it.entity == "product_barcodes"
        }
        if (!autoPrintExists) {
            Log.d("BasilDebug", "Injecting auto_print_label field...")
            api.createUserfield(UserfieldCreateRequest(
                name = "auto_print_label",
                caption = "Auto Print Label",
                type = "checkbox",
                entity = "product_barcodes",
                config = null
            ))
        }

        // 3. Do Not Buy for Products
        val doNotBuyExists = existingFields.any {
            it.name == "do_not_buy" && it.entity == "products"
        }
        if (!doNotBuyExists) {
            Log.d("BasilDebug", "Injecting do_not_buy field...")
            api.createUserfield(UserfieldCreateRequest(
                name = "do_not_buy",
                caption = "Do Not Buy",
                type = "checkbox",
                entity = "products",
                config = null
            ))
        }

        // 4. Parent Product flag, set on parents Basil (or the nightly parent script) creates
        val parentProductExists = existingFields.any {
            it.name == "parent_product" && it.entity == "products"
        }
        if (!parentProductExists) {
            Log.d("BasilDebug", "Injecting parent_product field...")
            api.createUserfield(UserfieldCreateRequest(
                name = "parent_product",
                caption = "Parent Product",
                type = "checkbox",
                entity = "products",
                config = null
            ))
        }

        Log.d("BasilDebug", "Grocy database schema check complete.")
    }
}