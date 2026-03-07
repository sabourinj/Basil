package com.basil.grocyscanner.ui

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.basil.grocyscanner.ui.theme.DarkerHeaderPurple
import com.basil.grocyscanner.ui.theme.ErrorRed
import com.basil.grocyscanner.viewmodel.ScannerViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale


@Composable
fun ColumnScope.InventoryResultView(currentState: ScannerViewModel.AppState.InventoryResult) {
    val context = LocalContext.current

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
                .fillMaxWidth(0.85f)
                .weight(1f, fill = false)
                .padding(vertical = 16.dp)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (currentState.entries.isEmpty()) {
        Text("No items currently in stock.", color = ErrorRed, style = MaterialTheme.typography.titleMedium)
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkerHeaderPurple),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Amount", color = Color.LightGray, fontWeight = FontWeight.Bold)
                    Text("Expires", color = Color.LightGray, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(currentState.entries) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val amountStr = if (entry.amount % 1.0 == 0.0) entry.amount.toInt().toString() else entry.amount.toString()
                            Text(amountStr, color = Color.White)

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

                            val dateColor = if (dateStr.startsWith("Expired")) ErrorRed else Color.White
                            Text(text = dateStr, color = dateColor)
                        }
                    }
                }
            }
        }
    }
}