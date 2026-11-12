package com.basil.grocyscanner.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.basil.grocyscanner.ui.theme.DeepPurple
import com.basil.grocyscanner.ui.theme.SuccessGreen
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

/**
 * Whether this device scans with its camera instead of a hardware scanner.
 * Asked on first run, and again after Logout / Disconnect in Settings. Installs from before camera
 * support have no answer saved, and keep using the hardware scanner.
 */
object CameraScanning {
    const val PREF_KEY = "CAMERA_SCAN"

    fun hasCamera(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}

// An open viewfinder that hasn't read anything turns itself off after this long
private const val VIEWFINDER_TIMEOUT_MS = 30_000L

/**
 * Returns a function that runs [onGranted] once camera access is allowed,
 * asking for it first if needed.
 */
@Composable
fun rememberCameraPermission(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            currentOnGranted()
        } else {
            val activity = context.findActivity()
            val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
            if (canAskAgain) {
                Toast.makeText(context, "Basil needs the camera to scan barcodes", Toast.LENGTH_LONG).show()
            } else {
                // Refused for good: the only way back is the app's system settings
                Toast.makeText(context, "Allow camera access in Basil's settings to scan", Toast.LENGTH_LONG).show()
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    return remember {
        {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                currentOnGranted()
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

/**
 * A "Tap to Scan" area that becomes the live camera in place. [idle] draws the
 * area while the camera is off and is handed the function that turns it on.
 * The first code read goes to [onScan] and the camera turns off again.
 */
@Composable
fun InlineCameraScan(
    onScan: (String) -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    idle: @Composable (start: () -> Unit) -> Unit
) {
    var active by remember { mutableStateOf(false) }
    val start = rememberCameraPermission { active = true }

    if (active) {
        LaunchedEffect(Unit) {
            delay(VIEWFINDER_TIMEOUT_MS)
            active = false
        }
        Box(
            modifier = modifier
                .size(width = width, height = height)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black)
                .scanCornerBrackets(width)
        ) {
            CameraViewfinder(
                onResult = {
                    active = false
                    onScan(it)
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = { active = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Stop camera", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    } else {
        Box(modifier = modifier) { idle(start) }
    }
}

/**
 * Returns a function that opens a small pop-up viewfinder, for scan buttons
 * that have no "Tap to Scan" area beside them. The first code read goes to
 * [onScan] and the pop-up closes.
 */
@Composable
fun rememberCameraScanner(onScan: (String) -> Unit): () -> Unit {
    var showScanner by remember { mutableStateOf(false) }
    val currentOnScan by rememberUpdatedState(onScan)

    if (showScanner) {
        CameraScanCard(
            onResult = { code ->
                showScanner = false
                currentOnScan(code)
            },
            onDismiss = { showScanner = false }
        )
    }

    return rememberCameraPermission { showScanner = true }
}

/** Floating camera button for the scanner screen. */
@Composable
fun CameraScanFab(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick, containerColor = SuccessGreen, contentColor = DeepPurple) {
        Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = "Scan with camera")
    }
}

/** Compact camera button, for the shopping status bar. */
@Composable
fun CameraScanIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = SuccessGreen, contentColor = DeepPurple)
    ) {
        Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = "Scan with camera")
    }
}

/**
 * The corner brackets of the "Ready to Scan" box, drawn over the content so
 * they stay visible on top of a live camera feed.
 */
fun Modifier.scanCornerBrackets(boxWidth: Dp, color: Color = Color.White.copy(alpha = 0.7f)): Modifier =
    drawWithContent {
        drawContent()
        val strokeWidthPx = 4.dp.toPx()
        val cornerLengthPx = (boxWidth.toPx() * 0.15f).coerceAtLeast(20.dp.toPx())
        val w = size.width
        val h = size.height
        drawLine(color, Offset(0f, 0f), Offset(cornerLengthPx, 0f), strokeWidthPx)
        drawLine(color, Offset(0f, 0f), Offset(0f, cornerLengthPx), strokeWidthPx)
        drawLine(color, Offset(w, 0f), Offset(w - cornerLengthPx, 0f), strokeWidthPx)
        drawLine(color, Offset(w, 0f), Offset(w, cornerLengthPx), strokeWidthPx)
        drawLine(color, Offset(0f, h), Offset(cornerLengthPx, h), strokeWidthPx)
        drawLine(color, Offset(0f, h), Offset(0f, h - cornerLengthPx), strokeWidthPx)
        drawLine(color, Offset(w, h), Offset(w - cornerLengthPx, h), strokeWidthPx)
        drawLine(color, Offset(w, h), Offset(w, h - cornerLengthPx), strokeWidthPx)
    }

/** Pop-up card holding a viewfinder the size of the "Tap to Scan" box, over a dimmed screen. */
@Composable
private fun CameraScanCard(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) {
            delay(VIEWFINDER_TIMEOUT_MS)
            onDismiss()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(DeepPurple, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.55f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black)
                    .scanCornerBrackets(280.dp)
            ) {
                CameraViewfinder(onResult = onResult, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Point the camera at a barcode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.LightGray) }
        }
    }
}

// Retail codes, Grocy's own labels (Code 128 / Data Matrix / QR) and setup QR codes
private val SCAN_FORMATS = intArrayOf(
    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
    Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93, Barcode.FORMAT_ITF,
    Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX
)

private fun Barcode.is2D() = format == Barcode.FORMAT_QR_CODE || format == Barcode.FORMAT_DATA_MATRIX

/**
 * Live back-camera feed that reads one barcode and hands it to [onResult].
 * The camera runs only while this is on screen. A light toggle sits in the
 * corner when the camera has a flash.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraViewfinder(onResult: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // TextureView-backed, so the feed clips to the box's shape
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(SCAN_FORMATS[0], *SCAN_FORMATS.copyOfRange(1, SCAN_FORMATS.size)).build()
        )
        val delivered = AtomicBoolean(false)
        var lastCandidate: String? = null
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            // 1D barcodes need more pixels than ImageAnalysis's 640x480 default
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(executor) { proxy ->
            val mediaImage = proxy.image
            if (mediaImage == null || delivered.get()) {
                proxy.close()
                return@setAnalyzer
            }
            val rotation = proxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotation)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    // Several codes in view: take the one nearest the middle
                    val cx = image.width / 2f
                    val cy = image.height / 2f
                    val (w, h) = if (rotation % 180 == 0) cx to cy else cy to cx
                    val best = barcodes
                        .filter { !it.rawValue.isNullOrBlank() }
                        .minByOrNull { b -> b.boundingBox?.let { hypot(it.exactCenterX() - w, it.exactCenterY() - h) } ?: Float.MAX_VALUE }
                    if (best == null) {
                        lastCandidate = null
                        return@addOnSuccessListener
                    }
                    val value = best.rawValue!!.trim()
                    // QR/Data Matrix carry their own error correction; a 1D code must
                    // read the same in two frames in a row to rule out a misread.
                    val confirmed = best.is2D() || value == lastCandidate
                    lastCandidate = value
                    if (confirmed && delivered.compareAndSet(false, true)) {
                        mainExecutor.execute { currentOnResult(value) }
                    }
                }
                .addOnCompleteListener { proxy.close() }
        }

        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                provider = providerFuture.get().also {
                    camera = it.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't open the camera", Toast.LENGTH_SHORT).show()
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            delivered.set(true)
            provider?.unbind(preview, analysis)
            analysis.clearAnalyzer()
            scanner.close()
            executor.shutdown()
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            IconButton(
                onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(36.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                    contentDescription = if (torchOn) "Turn off light" else "Turn on light",
                    tint = if (torchOn) SuccessGreen else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** First-run question: which scanner this device will use. */
@Composable
fun ScannerChoiceScreen(onChoose: (useCamera: Boolean) -> Unit) {
    SetupScaffold {
        Text("How will you scan?", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Basil works with a scanner device's built-in barcode reader, or with this phone's camera. To change this later, use Logout / Disconnect in Settings.",
            textAlign = TextAlign.Center,
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.height(32.dp))
        ScannerChoiceCard(
            title = "Phone camera",
            icon = Icons.Filled.PhotoCamera,
            onClick = { onChoose(true) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        ScannerChoiceCard(
            title = "Built-in scanner",
            icon = Icons.Filled.QrCodeScanner,
            onClick = { onChoose(false) }
        )
    }
}

@Composable
private fun ScannerChoiceCard(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
