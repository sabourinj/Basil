package com.basil.grocyscanner.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.MoveDown
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TakeoutDining
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.basil.grocyscanner.R
import com.basil.grocyscanner.ui.theme.DarkerHeaderPurple
import com.basil.grocyscanner.ui.theme.DeepPurple
import com.basil.grocyscanner.ui.theme.ErrorRed
import com.basil.grocyscanner.ui.theme.SuccessGreen
import com.basil.grocyscanner.viewmodel.AppMode
import com.basil.grocyscanner.viewmodel.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrocyScannerApp(viewModel: ScannerViewModel, resetDurationSeconds: Int, onNavigateToSettings: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val vibrator = remember {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    }

    LaunchedEffect(state) {
        when (val currentState = state) {
            is ScannerViewModel.AppState.Success -> {
                if (currentState.isPrinting || currentState.isOpened) {
                    val pattern = longArrayOf(0, 200, 100, 200)
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
            is ScannerViewModel.AppState.InventoryResult -> {
                if (currentState.isOpened) {
                    val pattern = longArrayOf(0, 200, 100, 200)
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
            is ScannerViewModel.AppState.NeedsDate, is ScannerViewModel.AppState.Error, is ScannerViewModel.AppState.LinkingChild -> {
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
                if (currentMode == AppMode.BATCH_MOVE) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setMode(AppMode.PURCHASE) 
                            },
                            modifier = Modifier.fillMaxWidth(0.8f),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = DeepPurple)
                        ) {
                            Text("End Batch Mode", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeButton("Purchase", currentMode == AppMode.PURCHASE) {
                            if (currentMode != AppMode.PURCHASE) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setMode(AppMode.PURCHASE)
                        }
                        ModeButton("Consume", currentMode == AppMode.CONSUME) {
                            if (currentMode != AppMode.CONSUME) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setMode(AppMode.CONSUME)
                        }
                        ModeButton("Inventory", currentMode == AppMode.INVENTORY) {
                            if (currentMode != AppMode.INVENTORY) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setMode(AppMode.INVENTORY)
                        }
                    }
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
                    ReadyToScanBox()
                }
                is ScannerViewModel.AppState.BatchMoveActive -> {
                    Icon(imageVector = Icons.Default.MoveDown, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Batch Relocating to:", style = MaterialTheme.typography.titleMedium, color = Color.LightGray)
                    Text(text = currentState.locationName, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    ReadyToScanBox(width = 220.dp, height = 130.dp, fontSize = 18.sp)
                }
                is ScannerViewModel.AppState.Loading -> {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(100.dp), strokeWidth = 2.dp, color = Color.White.copy(alpha = 0.5f))
                        if (currentState.showGemini) {
                            Image(painter = painterResource(id = R.drawable.gemini_logo), contentDescription = null, modifier = Modifier.size(48.dp))
                        } else if (currentState.showOff) {
                            Image(painter = painterResource(id = R.drawable.openfoodfacts_logo), contentDescription = null, modifier = Modifier.size(48.dp))
                        } else if (currentState.showGrocy) {
                            Image(painter = painterResource(id = R.drawable.grocy_g_logo), contentDescription = null, modifier = Modifier.size(48.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(currentState.message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = Color.White)
                }
                is ScannerViewModel.AppState.NeedsDate -> {
                    ExpirationDatePrompt(
                        product = currentState.product,
                        estimatedPrice = currentState.estimatedPrice,
                        onSubmit = { date ->
                            viewModel.confirmAction(currentState.product.id, currentState.amount, date, currentState.estimatedPrice, currentState.autoPrint)
                        },
                        onCancel = { viewModel.resetState() }
                    )
                }
                is ScannerViewModel.AppState.Success -> {
                    val infiniteTransition = rememberInfiniteTransition(label = "blink")
                    val blinkAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 0.2f,
                        animationSpec = infiniteRepeatable(animation = tween(400, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                        label = "blinkAlpha"
                    )

                    Text(currentState.message, style = MaterialTheme.typography.headlineMedium, color = SuccessGreen, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)

                    if (currentState.stockMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = currentState.stockMessage, style = MaterialTheme.typography.titleMedium, color = Color.LightGray, textAlign = TextAlign.Center)
                    }

                    if (currentState.product != null && currentState.currentStock > 0.0 && currentMode == AppMode.PURCHASE) {
                        val prodId = currentState.product.id
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            FilledIconButton(
                                onClick = { viewModel.performQuickAction(prodId, "print", currentState.stockId, currentState.addedAmount) },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (currentState.isPrinting) Color.White.copy(alpha = blinkAlpha) else Color.White,
                                    contentColor = DeepPurple
                                ),
                                modifier = Modifier.size(64.dp)
                            ) { Icon(imageVector = Icons.Filled.Print, contentDescription = "Print Label", modifier = Modifier.size(32.dp)) }
                            
                            FilledIconButton(
                                onClick = { viewModel.performQuickAction(prodId, "open", currentState.stockId) },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (currentState.isOpened) Color.White.copy(alpha = blinkAlpha) else Color.White,
                                    contentColor = DeepPurple
                                ),
                                modifier = Modifier.size(64.dp)
                            ) { Icon(imageVector = Icons.Filled.TakeoutDining, contentDescription = "Mark Opened", modifier = Modifier.size(32.dp)) }
                            
                            if (currentState.lastScannedBarcode != null) {
                                FilledIconButton(
                                    onClick = { viewModel.startLinkingChild(currentState.product, currentState.lastScannedBarcode) },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = DeepPurple),
                                    modifier = Modifier.size(64.dp)
                                ) { Icon(imageVector = Icons.Filled.AddLink, contentDescription = "Link Child", modifier = Modifier.size(32.dp)) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    AutoResetCountdown(
                        durationMillis = resetDurationSeconds * 1000L,
                        message = if (currentMode == AppMode.BATCH_MOVE) "Ready for Next..." else "Ready to Scan...",
                        resetTrigger = currentState,
                        onTimeout = { viewModel.resetState() }
                    )
                }
                is ScannerViewModel.AppState.InventoryResult -> {
                    InventoryResultView(currentState = currentState, viewModel = viewModel)
                }
                is ScannerViewModel.AppState.LinkingChild -> {
                    Text("Linking to: ${currentState.product.name}", style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Now scan the SINGLE ITEM barcode", style = MaterialTheme.typography.titleMedium, color = SuccessGreen, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(48.dp))
                    Button(
                        onClick = { viewModel.resetState() },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepPurple)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                }
                is ScannerViewModel.AppState.ChildQuantityPrompt -> {
                    var showCustomDialog by remember { mutableStateOf(false) }
                    var customValue by remember { mutableStateOf("") }

                    Text("How many items are in the box?", style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(32.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            QuantityButton("6") { viewModel.confirmChildLink(currentState.product.id, currentState.parentBarcode, currentState.childBarcode, 6.0) }
                            QuantityButton("12") { viewModel.confirmChildLink(currentState.product.id, currentState.parentBarcode, currentState.childBarcode, 12.0) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            QuantityButton("24") { viewModel.confirmChildLink(currentState.product.id, currentState.parentBarcode, currentState.childBarcode, 24.0) }
                            QuantityButton("Custom") { showCustomDialog = true }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { viewModel.resetState() },
                            modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepPurple)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (showCustomDialog) {
                        Dialog(onDismissRequest = { showCustomDialog = false }) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = DeepPurple,
                                border = BorderStroke(2.dp, Color.White)
                            ) {
                                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Enter Quantity", style = MaterialTheme.typography.titleLarge, color = Color.White)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    TextField(
                                        value = customValue,
                                        onValueChange = { if (it.all { char -> char.isDigit() }) customValue = it },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        TextButton(onClick = { showCustomDialog = false }) {
                                            Text("Cancel", color = Color.LightGray)
                                        }
                                        Button(
                                            onClick = {
                                                val qty = customValue.toDoubleOrNull() ?: 0.0
                                                if (qty > 0) {
                                                    viewModel.confirmChildLink(currentState.product.id, currentState.parentBarcode, currentState.childBarcode, qty)
                                                    showCustomDialog = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = DeepPurple)
                                        ) {
                                            Text("Confirm")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is ScannerViewModel.AppState.Error -> {
                    Text(text = currentState.error, color = ErrorRed, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    if (currentMode == AppMode.CONSUME || currentMode == AppMode.BATCH_MOVE) {
                        AutoResetCountdown(durationMillis = resetDurationSeconds * 1000L, message = "Auto-clearing...", resetTrigger = currentState, onTimeout = { viewModel.resetState() })
                    } else {
                        Button(onClick = { viewModel.resetState() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepPurple)) { Text("Clear Error") }
                    }
                }
            }
        }
    }
}

@Composable
fun QuantityButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(width = 120.dp, height = 80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepPurple),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text = label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AutoResetCountdown(durationMillis: Long, message: String, resetTrigger: Any? = Unit, onTimeout: () -> Unit) {
    var progress by remember(resetTrigger) { mutableStateOf(1f) }
    LaunchedEffect(durationMillis, resetTrigger) {
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
            CircularProgressIndicator(progress = { progress }, color = Color.White, strokeWidth = 4.dp)
            Text(text = ((durationMillis * progress) / 1000).toInt().plus(1).toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(message)
    }
}

@Composable
fun ReadyToScanBox(
    width: androidx.compose.ui.unit.Dp = 280.dp,
    height: androidx.compose.ui.unit.Dp = 180.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 24.sp
) {
    Box(
        modifier = Modifier
            .padding(24.dp)
            .size(width = width, height = height)
            .drawBehind {
                val strokeWidthPx = 4.dp.toPx()
                val cornerLengthPx = (width.toPx() * 0.15f).coerceAtLeast(20.dp.toPx())
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
        Text(text = "Ready to Scan", fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
