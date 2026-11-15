package com.basil.grocyscanner.ui

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TakeoutDining
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.basil.grocyscanner.ui.theme.DarkerHeaderPurple
import com.basil.grocyscanner.ui.theme.DeepPurple
import com.basil.grocyscanner.ui.theme.ErrorRed
import com.basil.grocyscanner.viewmodel.ScannerViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale


@Composable
fun ColumnScope.InventoryResultView(currentState: ScannerViewModel.AppState.InventoryResult, viewModel: ScannerViewModel) {
    val context = LocalContext.current
    var showGrocycodeDialog by remember { mutableStateOf(false) }
    var showLabelPicker by remember { mutableStateOf(false) }
    val labelChoices = remember(currentState.labelEntries) { labelChoices(currentState.labelEntries) }
    var showOpenPicker by remember { mutableStateOf(false) }
    val openChoices = remember(currentState.labelEntries) { openChoices(currentState.labelEntries) }

    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    Text(
        text = currentState.product.name,
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Hero Row: Image and Actions side-by-side
    Row(
        modifier = Modifier.fillMaxWidth(0.9f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val sharedPrefs = com.basil.grocyscanner.data.SecurePrefs.get(context)
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
                    .size(110.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(20.dp))
        }

        // 2x2 Grid of action buttons (full 56.dp industrial size)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledIconButton(
                    onClick = {
                        // No question to ask with one batch (or none): print it
                        when (labelChoices.size) {
                            0 -> viewModel.printInventoryLabel(currentState.product.id, null)
                            1 -> viewModel.printInventoryLabel(currentState.product.id, labelChoices.first().entryId)
                            else -> showLabelPicker = true
                        }
                    },
                    enabled = currentState.canPrint,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (currentState.isPrinting) Color.White.copy(alpha = blinkAlpha) else Color.White,
                        contentColor = DeepPurple,
                        disabledContainerColor = Color.White.copy(alpha = 0.2f),
                        disabledContentColor = DeepPurple.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Print, contentDescription = "Print Label", modifier = Modifier.size(28.dp))
                }

                FilledIconButton(
                    onClick = {
                        // Only ask when the sealed batches have different due dates
                        if (openChoices.size > 1) showOpenPicker = true
                        else viewModel.openInventoryEntry(currentState.product.id, null)
                    },
                    enabled = currentState.canOpen,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (currentState.isOpened) Color.White.copy(alpha = blinkAlpha) else Color.White,
                        contentColor = DeepPurple,
                        disabledContainerColor = Color.White.copy(alpha = 0.2f),
                        disabledContentColor = DeepPurple.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(imageVector = Icons.Filled.TakeoutDining, contentDescription = "Open Next", modifier = Modifier.size(28.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledIconButton(
                    onClick = { viewModel.performQuickAction(currentState.product.id, "shopping_list") },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (currentState.isAddingToList) Color.White.copy(alpha = blinkAlpha) else Color.White,
                        contentColor = DeepPurple
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(imageVector = Icons.Filled.AddShoppingCart, contentDescription = "Add to List", modifier = Modifier.size(28.dp))
                }

                FilledIconButton(
                    onClick = { showGrocycodeDialog = true },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = DeepPurple
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(imageVector = Icons.Filled.QrCode, contentDescription = "Show Grocycode", modifier = Modifier.size(28.dp))
                }
            }
        }
    }

    if (showGrocycodeDialog) {
        Dialog(onDismissRequest = { showGrocycodeDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkerHeaderPurple),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Grocycode",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val grocycodeText = "grcy:p:${currentState.product.id}"
                    val qrcodeBitmap: Bitmap? = remember(currentState.product.id) {
                        generateQrCodeBitmap(grocycodeText, 400)
                    }

                    if (qrcodeBitmap != null) {
                        Image(
                            bitmap = qrcodeBitmap.asImageBitmap(),
                            contentDescription = "Grocycode QR Code",
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            filterQuality = FilterQuality.None
                        )
                    } else {
                        Text("Failed to generate QR Code", color = ErrorRed)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showGrocycodeDialog = false },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = DeepPurple
                        )
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showLabelPicker) {
        StockEntryPickerDialog(
            title = "Print which label?",
            choices = labelChoices,
            productOption = "Product label (no dates)",
            onPick = { choice ->
                showLabelPicker = false
                viewModel.printInventoryLabel(currentState.product.id, choice?.entryId)
            },
            onDismiss = { showLabelPicker = false }
        )
    }

    if (showOpenPicker) {
        StockEntryPickerDialog(
            title = "Open which one?",
            choices = openChoices,
            productOption = null,
            onPick = { choice ->
                showOpenPicker = false
                viewModel.openInventoryEntry(currentState.product.id, choice?.stockId)
            },
            onDismiss = { showOpenPicker = false }
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    if (currentState.entries.isEmpty()) {
        Text("No stock found!", color = ErrorRed, style = MaterialTheme.typography.titleMedium)
        currentState.parentStock?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(parentStockLabel(it), color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkerHeaderPurple),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Qty", color = Color.LightGray, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f))
                    Text("Expires", color = Color.LightGray, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                }
                HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(currentState.entries) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val amountStr = if (entry.amount % 1.0 == 0.0) entry.amount.toInt().toString() else entry.amount.toString()
                            Text(amountStr, color = if (entry.open == 1) Color.Gray else Color.White, modifier = Modifier.weight(0.3f))

                            val dateStr = remember(entry.best_before_date) {
                                if (entry.best_before_date == "2999-12-31" || entry.best_before_date.isNullOrEmpty()) {
                                    "Never"
                                } else {
                                    try {
                                        val expireDate = LocalDate.parse(entry.best_before_date)
                                        val today = LocalDate.now()
                                        val daysBetween = ChronoUnit.DAYS.between(today, expireDate)

                                        when {
                                            daysBetween == 0L -> "Today"
                                            daysBetween == 1L -> "Tomorrow"
                                            daysBetween in 2..6 -> expireDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                                            daysBetween < 0 -> {
                                                val daysPast = -daysBetween
                                                "Expired $daysPast day${if (daysPast != 1L) "s" else ""} ago"
                                            }
                                            else -> entry.best_before_date
                                        }
                                    } catch (e: Exception) {
                                        entry.best_before_date
                                    }
                                }
                            }

                            val dateColor = if (dateStr.startsWith("Expired")) ErrorRed else if (entry.open == 1) Color.Gray else Color.White
                            Text(text = dateStr, color = dateColor, modifier = Modifier.weight(0.7f))
                        }
                    }
                }

                currentState.parentStock?.let {
                    HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    Text(parentStockLabel(it), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// One row in the label picker: stock entries whose labels would print the
// same (same purchased, opened and due dates) are folded together.
private data class LabelChoice(
    val entryId: Int,
    val stockId: String?,
    val amount: Double,
    val isOpen: Boolean,
    val purchased: String?,
    val opened: String?,
    val due: String?
)

private fun labelChoices(entries: List<com.basil.grocyscanner.data.StockEntry>): List<LabelChoice> =
    entries
        .groupBy { listOf(it.open == 1, day(it.purchased_date), day(it.opened_date), day(it.best_before_date)) }
        .map { (_, group) -> group.toChoice() }

// The open picker: sealed entries by due date, which is what opening one
// rather than another changes. Within a due date, the one bought first goes,
// as Grocy itself would pick.
private fun openChoices(entries: List<com.basil.grocyscanner.data.StockEntry>): List<LabelChoice> =
    entries
        .filter { it.open == 0 }
        .sortedWith(compareBy({ day(it.best_before_date) ?: "2999-12-31" }, { day(it.purchased_date) ?: "" }))
        .groupBy { day(it.best_before_date) }
        .map { (_, group) -> group.toChoice() }

private fun List<com.basil.grocyscanner.data.StockEntry>.toChoice(): LabelChoice {
    val first = first()
    return LabelChoice(
        entryId = first.id,
        stockId = first.stock_id,
        amount = sumOf { it.amount },
        isOpen = first.open == 1,
        purchased = day(first.purchased_date),
        opened = day(first.opened_date),
        due = day(first.best_before_date)?.takeIf { it != "2999-12-31" }
    )
}

private fun day(value: String?): String? = value?.take(10)?.takeIf { it.isNotBlank() }

private fun shortDate(value: String): String = try {
    val date = LocalDate.parse(value)
    "${date.monthValue}/${date.dayOfMonth}"
} catch (e: Exception) {
    value
}

@Composable
// productOption: a last row below the entries that picks none of them (null)
private fun StockEntryPickerDialog(
    title: String,
    choices: List<LabelChoice>,
    productOption: String?,
    onPick: (LabelChoice?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        // Same frame as the Gemini model picker
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(16.dp),
            color = DeepPurple,
            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(choices) { choice ->
                        val first = if (choice.isOpen && choice.opened != null) {
                            "Opened ${shortDate(choice.opened)}"
                        } else {
                            choice.purchased?.let { "Purchased ${shortDate(it)}" } ?: "Sealed"
                        }
                        val due = choice.due?.let { "Due ${shortDate(it)}" } ?: "No due date"
                        val amountStr = if (choice.amount % 1.0 == 0.0) choice.amount.toInt().toString() else choice.amount.toString()
                        Button(
                            onClick = { onPick(choice) },
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = DeepPurple
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$first · $due", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("×$amountStr")
                        }
                    }
                }

                if (productOption != null) {
                    HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
                    Button(
                        onClick = { onPick(null) },
                        shape = RoundedCornerShape(10.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(productOption)
                    }
                }
            }
        }
    }
}

private fun generateQrCodeBitmap(contents: String, size: Int = 400): Bitmap? {
    try {
        val writer = QRCodeWriter()
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = writer.encode(contents, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bmp
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
