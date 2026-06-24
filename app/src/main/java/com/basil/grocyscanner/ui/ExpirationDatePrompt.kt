package com.basil.grocyscanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.basil.grocyscanner.data.ProductDetails
import com.basil.grocyscanner.ui.theme.DeepPurple
import com.basil.grocyscanner.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ExpirationDatePrompt(
    product: ProductDetails,
    estimatedPrice: Double?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var selectedCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            if (product.default_best_before_days > 0) {
                add(Calendar.DAY_OF_YEAR, product.default_best_before_days)
            } else {
                add(Calendar.DAY_OF_YEAR, 7)
            }
        })
    }

    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val monthSdf = SimpleDateFormat("MMMM", Locale.getDefault())
    val yearSdf = SimpleDateFormat("yyyy", Locale.getDefault())

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DeepPurple,
            border = BorderStroke(2.dp, Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Set Expiration",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Precise Date Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day
                    DatePartSelector(
                        label = "Day",
                        value = selectedCalendar.get(Calendar.DAY_OF_MONTH).toString(),
                        onIncrement = { selectedCalendar = (selectedCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) } },
                        onDecrement = { selectedCalendar = (selectedCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) } }
                    )
                    // Month
                    DatePartSelector(
                        label = "Month",
                        value = monthSdf.format(selectedCalendar.time).take(3),
                        onIncrement = { selectedCalendar = (selectedCalendar.clone() as Calendar).apply { add(Calendar.MONTH, 1) } },
                        onDecrement = { selectedCalendar = (selectedCalendar.clone() as Calendar).apply { add(Calendar.MONTH, -1) } }
                    )
                    // Year
                    DatePartSelector(
                        label = "Year",
                        value = yearSdf.format(selectedCalendar.time),
                        onIncrement = { selectedCalendar = (selectedCalendar.clone() as Calendar).apply { add(Calendar.YEAR, 1) } },
                        onDecrement = { selectedCalendar = (selectedCalendar.clone() as Calendar).apply { add(Calendar.YEAR, -1) } }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))

                // Quick Adjustment Grid (Smaller)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateAdjustButton("+1d", Modifier.weight(1f)) {
                            selectedCalendar = (selectedCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                        }
                        DateAdjustButton("+1w", Modifier.weight(1f)) {
                            selectedCalendar = (selectedCalendar.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, 1) }
                        }
                        DateAdjustButton("+1m", Modifier.weight(1f)) {
                            selectedCalendar = (selectedCalendar.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                        }
                        DateAdjustButton("+1y", Modifier.weight(1f)) {
                            selectedCalendar = (selectedCalendar.clone() as Calendar).apply { add(Calendar.YEAR, 1) }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepPurple)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onSubmit(sdf.format(selectedCalendar.time)) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = DeepPurple)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DatePartSelector(label: String, value: String, onIncrement: () -> Unit, onDecrement: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        IconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, tint = SuccessGreen)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onDecrement, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Remove, contentDescription = null, tint = Color.LightGray)
        }
    }
}

@Composable
fun DateAdjustButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.05f),
            contentColor = Color.White
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
