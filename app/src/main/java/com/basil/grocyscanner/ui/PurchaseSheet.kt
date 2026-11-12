package com.basil.grocyscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.basil.grocyscanner.ui.theme.DarkerHeaderPurple
import com.basil.grocyscanner.ui.theme.DeepPurple
import com.basil.grocyscanner.ui.theme.ErrorRed
import com.basil.grocyscanner.ui.theme.SuccessGreen
import com.basil.grocyscanner.viewmodel.ListStatus
import com.basil.grocyscanner.viewmodel.PurchaseSheet
import kotlinx.coroutines.delay

private const val SHEET_VISIBLE_MILLIS = 10_000L
private const val UNDONE_VISIBLE_MILLIS = 3_000L
// Longest the sheet waits for the background job before giving up on it
private const val SIBLINGS_WAIT_CAP_MILLIS = 30_000L

private fun formatAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()

// Pop-up over the shopping list confirming a scan-to-purchase, with the other
// items already in stock under the same parent and an Undo.
@Composable
fun PurchaseSheetCard(
    sheet: PurchaseSheet,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Closes itself so the next aisle needs no taps. The timer restarts for
    // each new purchase and after an Undo; it holds while an Undo is running.
    // While the "already at home" list is still loading, the normal countdown
    // waits for it (up to a cap), then starts fresh once it arrives.
    LaunchedEffect(sheet.id, sheet.isUndone, sheet.isUndoing, sheet.siblingsPending) {
        if (sheet.isUndoing) return@LaunchedEffect
        delay(
            when {
                sheet.isUndone -> UNDONE_VISIBLE_MILLIS
                sheet.siblingsPending -> SIBLINGS_WAIT_CAP_MILLIS
                else -> SHEET_VISIBLE_MILLIS
            }
        )
        onDismiss()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkerHeaderPurple),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when {
                    sheet.isUndone -> Icons.AutoMirrored.Filled.Undo to Color.LightGray
                    sheet.isDoNotBuy -> Icons.Filled.Block to ErrorRed
                    else -> Icons.Filled.CheckCircle to SuccessGreen
                }
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sheet.product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (sheet.isUndone) "Purchase undone · stock ${formatAmount(sheet.newStock)}"
                               else "+${formatAmount(sheet.amount)} · stock now ${formatAmount(sheet.newStock)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color.LightGray)
                }
            }

            if (!sheet.isUndone) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (sheet.listStatus) {
                        ListStatus.CHECKED_OFF -> "✓ Checked off your list"
                        ListStatus.REDUCED -> "${formatAmount(sheet.remainingOnList)} more still on your list"
                        ListStatus.NOT_ON_LIST -> "Not on your list"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sheet.listStatus == ListStatus.NOT_ON_LIST) Color.LightGray else SuccessGreen
                )
                if (sheet.isNewProduct) {
                    Text("New product added to Grocy", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                }
                if (sheet.isDoNotBuy) {
                    Text(
                        "Marked Do Not Buy",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )
                }
            }

            SiblingSection(sheet)

            if (!sheet.isUndone && sheet.canUndo) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onUndo,
                    enabled = !sheet.isUndoing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepPurple)
                ) {
                    if (sheet.isUndoing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = DeepPurple)
                    } else {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Undo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SiblingSection(sheet: PurchaseSheet) {
    val parentName = sheet.parentName
    if (parentName == null && !sheet.siblingsPending) return

    Spacer(modifier = Modifier.height(10.dp))
    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
    Spacer(modifier = Modifier.height(8.dp))

    if (parentName == null) {
        // The background job is still working out what this is a kind of
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.LightGray)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Checking for similar items at home…", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        }
        return
    }

    if (sheet.siblings.isEmpty()) {
        Text("No other $parentName at home", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
        return
    }

    Text(
        text = "Already at home in $parentName",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    Spacer(modifier = Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // A long family shouldn't push the Undo button off screen
        val shown = sheet.siblings.take(5)
        shown.forEach { sibling ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = sibling.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "×${formatAmount(sibling.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        if (sheet.siblings.size > shown.size) {
            Text(
                "and ${sheet.siblings.size - shown.size} more",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
        }
    }
}
