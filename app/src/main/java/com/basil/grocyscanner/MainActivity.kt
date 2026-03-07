package com.basil.grocyscanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import com.basil.grocyscanner.data.GrocyApi
import com.basil.grocyscanner.data.UserfieldCreateRequest
import com.basil.grocyscanner.ui.AiSetupScreen
import com.basil.grocyscanner.ui.GrocyScannerApp
import com.basil.grocyscanner.ui.SettingsScreen
import com.basil.grocyscanner.ui.UnconfiguredScreen
import com.basil.grocyscanner.ui.theme.DeepPurple
import com.basil.grocyscanner.viewmodel.ScannerViewModel
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

                var currentScreen by remember {
                    mutableStateOf(
                        if (savedUrl.isNullOrBlank()) "unconfigured"
                        else if (!sharedPrefs.contains("AI_ENABLED")) "ai_setup"
                        else "scanner"
                    )
                }

                Surface(modifier = Modifier.fillMaxSize(), color = DeepPurple, contentColor = Color.White) {

                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current

                    DisposableEffect(lifecycleOwner, currentScreen) {
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
                                                    putString("API_URL", parts[0])
                                                    putString("API_TOKEN", parts[1])
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

                        // Only listen when the app is actively on the screen
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

        val grocyApi = retrofit.create(GrocyApi::class.java)

        // --- THE FIX: Async Setup Block ---
        lifecycleScope.launch {
            try {
                runInitialSetup(grocyApi)
            } catch (e: Exception) {
                Log.e("BasilDebug", "Schema setup failed, but continuing: ${e.message}")
            }

            // Build the ViewModel after the database is ready
            viewModel = ScannerViewModel(grocyApi, geminiKey)
        }
    }

    private suspend fun runInitialSetup(api: GrocyApi) {
        val existingFields = api.getUserfields()

        val fieldExists = existingFields.any {
            it.name == "expiration_strategy" && it.entity == "product_groups"
        }

        if (!fieldExists) {
            Log.d("BasilDebug", "Injecting custom fields into Grocy...")
            api.createUserfield(UserfieldCreateRequest())
            Log.d("BasilDebug", "Successfully created expiration_strategy userfield.")
        } else {
            Log.d("BasilDebug", "Grocy database schema is already up to date.")
        }
    }
}