package com.basil.grocyscanner.ui

import android.util.Base64
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.basil.grocyscanner.data.SecurePrefs
import com.basil.grocyscanner.data.ShoppingListItem
import com.basil.grocyscanner.ui.theme.DarkerHeaderPurple
import com.basil.grocyscanner.ui.theme.DeepPurple

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingListView(items: List<ShoppingListItem>, onToggleDone: (ShoppingListItem) -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = SecurePrefs.get(context)
    val rawUrl = sharedPrefs.getString("API_URL", "") ?: ""
    val apiToken = sharedPrefs.getString("API_TOKEN", "") ?: ""
    var safeUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"
    if (!safeUrl.endsWith("api/")) safeUrl += "api/"

    Column(modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Text("List is empty", color = Color.Gray, modifier = Modifier.padding(16.dp))
        } else {
            val groupedItems = remember(items) {
                items.groupBy { it.category_name ?: "Uncategorized" }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedItems.forEach { (category, categoryItems) ->
                    stickyHeader {
                        Surface(
                            color = DeepPurple,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                            )
                        }
                    }

                    items(categoryItems) { item ->
                        val isDone = remember(item.done) {
                            val d = item.done
                            if (d is Boolean) d
                            else if (d is String) d == "1"
                            else (d as? Number)?.toInt() == 1
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDone) Color.DarkGray.copy(alpha = 0.4f) else DarkerHeaderPurple
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onToggleDone(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val pictureName = item.product_picture_file_name
                                if (!pictureName.isNullOrEmpty()) {
                                    val encodedName = Base64.encodeToString(pictureName.toByteArray(), Base64.NO_WRAP)
                                    val imageUrl = "${safeUrl}files/productpictures/$encodedName"
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(imageUrl)
                                            .addHeader("GROCY-API-KEY", apiToken)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .then(if (isDone) Modifier.alpha(0.5f) else Modifier)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    val title = when {
                                        !item.product_name.isNullOrBlank() -> item.product_name
                                        !item.note.isNullOrBlank() -> item.note
                                        else -> "Unknown Item"
                                    }
                                    val subtitle = if (!item.product_name.isNullOrBlank() && !item.note.isNullOrBlank()) item.note else null

                                    Text(
                                        text = title,
                                        color = if (isDone) Color.Gray else Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (subtitle != null) {
                                        Text(
                                            text = subtitle,
                                            color = if (isDone) Color.Gray.copy(alpha = 0.7f) else Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Text(
                                    text = if (item.amount % 1.0 == 0.0) item.amount.toInt().toString() else item.amount.toString(),
                                    color = if (isDone) Color.Gray else Color.White,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
