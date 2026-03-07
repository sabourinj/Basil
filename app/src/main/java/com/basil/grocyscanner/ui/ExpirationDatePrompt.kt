package com.basil.grocyscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.basil.grocyscanner.data.ProductDetails
import com.basil.grocyscanner.ui.theme.DeepPurple
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpirationDatePrompt(
    product: ProductDetails,
    estimatedPrice: Double?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    val defaultMillis = remember {
        if (product.default_best_before_days > 0) {
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, product.default_best_before_days) }.timeInMillis
        } else {
            System.currentTimeMillis()
        }
    }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = defaultMillis)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Set Expiration for:", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                Text(product.name, style = MaterialTheme.typography.titleSmall, color = DeepPurple, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)

                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = currentDensity.density * 0.7f
                    )
                ) {
                    DatePicker(
                        state = datePickerState,
                        title = null,
                        headline = null,
                        showModeToggle = false,
                        modifier = Modifier.weight(1f, fill = false),
                        colors = DatePickerDefaults.colors(
                            selectedDayContainerColor = DeepPurple,
                            selectedDayContentColor = Color.White,
                            todayContentColor = DeepPurple,
                            todayDateBorderColor = DeepPurple
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val finalDate = datePickerState.selectedDateMillis?.let { sdf.format(Date(it)) } ?: ""
                            onSubmit(finalDate)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepPurple, contentColor = Color.White)
                    ) {
                        Text("Save to Stock", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}