package com.basil.grocyscanner.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TakeoutDining
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.basil.grocyscanner.R
import com.basil.grocyscanner.ui.theme.DarkerHeaderPurple
import com.basil.grocyscanner.ui.theme.DeepPurple
import com.basil.grocyscanner.ui.theme.SuccessGreen
import com.basil.grocyscanner.viewmodel.AppMode
import com.basil.grocyscanner.viewmodel.ScannerViewModel

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
                        Text("Basil", fontWeight = FontWeight.Bold)
                        Image(
                            painter = painterResource(id = R.drawable.basil_logo),
                            contentDescription = "Basil Logo",
                            modifier = Modifier.size(36.dp).padding(start = 8.dp, bottom = 2.dp)
                        )
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
                    ModeButton("Purchase", currentMode == AppMode.PURCHASE) { viewModel.setMode(
                        AppMode.PURCHASE) }
                    ModeButton("Consume", currentMode == AppMode.CONSUME) { viewModel.setMode(
                        AppMode.CONSUME) }
                    ModeButton("Inventory", currentMode == AppMode.INVENTORY) { viewModel.setMode(
                        AppMode.INVENTORY) }
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

                                drawLine(bracketColor, Offset(0f, 0f), Offset(cornerLengthPx, 0f), strokeWidthPx)
                                drawLine(bracketColor, Offset(0f, 0f), Offset(0f, cornerLengthPx), strokeWidthPx)
                                drawLine(bracketColor, Offset(w, 0f), Offset(w - cornerLengthPx, 0f), strokeWidthPx)
                                drawLine(bracketColor, Offset(w, 0f), Offset(w, cornerLengthPx), strokeWidthPx)
                                drawLine(bracketColor, Offset(0f, h), Offset(cornerLengthPx, h), strokeWidthPx)
                                drawLine(bracketColor, Offset(0f, h), Offset(0f, h - cornerLengthPx), strokeWidthPx)
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
                            modifier = Modifier.size(80.dp).padding(bottom = 16.dp)
                        )
                    } else if (isLookupAction) {
                        Image(
                            painter = painterResource(id = R.drawable.openfoodfacts_logo),
                            contentDescription = "Open Food Facts Lookup",
                            modifier = Modifier.size(80.dp).padding(bottom = 16.dp)
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 6.dp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(msg, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = Color.White)
                }
                is ScannerViewModel.AppState.NeedsDate -> {
                    ExpirationDatePrompt(
                        product = currentState.product,
                        estimatedPrice = currentState.estimatedPrice,
                        onSubmit = { date ->
                            viewModel.confirmAction(
                                currentState.product.id,
                                1,
                                date,
                                currentState.estimatedPrice
                            )
                        },
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
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = DeepPurple),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(imageVector = Icons.Filled.Print, contentDescription = "Print Label", modifier = Modifier.size(32.dp))
                            }
                            FilledIconButton(
                                onClick = { viewModel.performQuickAction(currentState.productId, "open") },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = DeepPurple),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(imageVector = Icons.Filled.TakeoutDining, contentDescription = "Mark Opened", modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    if (currentState.productId != null && currentState.currentStock <= 0.0) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            FilledIconButton(
                                onClick = { /* Future: api.addToShoppingList(productId) */ },
                                enabled = false,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.3f),
                                    contentColor = Color.LightGray,
                                    disabledContainerColor = Color.White.copy(alpha = 0.1f),
                                    disabledContentColor = Color.Gray
                                ),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(imageVector = Icons.Default.AddShoppingCart, contentDescription = "Add to Shopping List", modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    AutoResetCountdown(
                        durationMillis = 4000L,
                        message = "Ready to Scan...",
                        onTimeout = {
                            viewModel.resetState()
                        }
                    )
                }
                is ScannerViewModel.AppState.InventoryResult -> {
                    InventoryResultView(currentState = currentState)
                }
                is ScannerViewModel.AppState.Error -> {
                    Text(
                        text = currentState.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (currentMode == AppMode.CONSUME) {
                        AutoResetCountdown(
                            durationMillis = 4000L,
                            message = "Auto-clearing...",
                            onTimeout = { viewModel.resetState() }
                        )
                    } else {
                        Button(onClick = { viewModel.resetState() }) {
                            Text("Clear Error")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutoResetCountdown(
    durationMillis: Long,
    message: String,
    onTimeout: () -> Unit
) {
    var progress by remember { mutableStateOf(1f) }

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < durationMillis) {
            val elapsed = System.currentTimeMillis() - startTime
            progress = 1f - (elapsed.toFloat() / durationMillis)
            kotlinx.coroutines.delay(16)
        }

        onTimeout()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                color = Color.White,
                strokeWidth = 4.dp
            )
            // Calculate seconds remaining
            Text(
                text = ((durationMillis * progress) / 1000).toInt().plus(1).toString(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(message)
    }
}