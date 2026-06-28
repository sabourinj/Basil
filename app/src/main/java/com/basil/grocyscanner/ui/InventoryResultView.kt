package com.basil.grocyscanner.ui

import android.util.Base64
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.TakeoutDining
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(32.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FilledIconButton(
                onClick = { viewModel.performQuickAction(currentState.product.id, "open") },
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
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    if (currentState.entries.isEmpty()) {
        Text("No stock found!", color = ErrorRed, style = MaterialTheme.typography.titleMedium)
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
            }
        }
    }
}
