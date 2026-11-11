package com.basil.grocyscanner.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.basil.grocyscanner.ui.theme.ErrorRed
import com.basil.grocyscanner.ui.theme.SuccessGreen
import com.basil.grocyscanner.viewmodel.ScannerViewModel

// Takes the mode buttons' place while shopping: what the last scan is doing,
// its error if it failed, or just "Shopping Mode". The X leaves shopping mode.
@Composable
fun ShoppingStatusBar(state: ScannerViewModel.AppState.ShoppingList, onExit: () -> Unit) {
    Row(
        // Same height as the mode-button row, so the list doesn't jump
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val error = state.scanError
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            when {
                state.isProcessing -> {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = SuccessGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    StatusText("Purchasing…", Color.White)
                }
                error != null -> {
                    Icon(imageVector = Icons.Filled.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    StatusText(error, ErrorRed)
                }
                else -> {
                    Icon(imageVector = Icons.Filled.ShoppingCart, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    StatusText("Shopping Mode", Color.White)
                }
            }
        }
        IconButton(onClick = onExit) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Leave Shopping Mode", tint = Color.LightGray)
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
