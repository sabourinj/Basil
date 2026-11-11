package com.basil.grocyscanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.basil.grocyscanner.data.GeminiConfig
import com.basil.grocyscanner.data.GeminiModelOption
import com.basil.grocyscanner.data.GeminiModels
import com.basil.grocyscanner.ui.theme.DeepPurple
import com.basil.grocyscanner.ui.theme.ErrorRed
import com.basil.grocyscanner.ui.theme.SuccessGreen

// Short name for the Settings row
fun modelLabel(id: String): String =
    if (id == GeminiConfig.LATEST_FLASH_ALIAS) GeminiModels.latestOption.label else id

@Composable
fun GeminiModelPicker(
    current: String,
    loadModels: suspend () -> List<GeminiModelOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var fetched by remember { mutableStateOf<List<GeminiModelOption>?>(null) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            fetched = loadModels()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            loadError = true
            fetched = emptyList()
        }
    }

    // The auto option first, then the fetched list. The default and the current
    // pick are always there, even if the fetch failed or no longer lists them.
    val options = remember(fetched, current) {
        val list = mutableListOf(GeminiModels.latestOption)
        fetched?.let { list += it }
        listOf(GeminiConfig.DEFAULT_MODEL, current).forEach { id ->
            if (list.none { it.id == id }) list += GeminiModelOption(id, id)
        }
        list.distinctBy { it.id }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DeepPurple,
            border = BorderStroke(2.dp, Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Gemini Model", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Used for every Gemini call in Basil. Set your parent script to the same model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option.id == current,
                                onClick = { onSelect(option.id) },
                                colors = RadioButtonDefaults.colors(selectedColor = SuccessGreen, unselectedColor = Color.LightGray)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(option.label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                                val detail = when (option.id) {
                                    GeminiConfig.LATEST_FLASH_ALIAS -> "Follows Google's newest Flash. Can change without notice."
                                    GeminiConfig.DEFAULT_MODEL -> "${option.id} · default"
                                    else -> option.id.takeIf { it != option.label }
                                }
                                detail?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                                }
                            }
                        }
                    }

                    if (fetched == null) {
                        Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.LightGray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading models…", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        }
                    } else if (loadError) {
                        Text(
                            "Couldn't load the model list. Check your connection or Gemini key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel", color = Color.White)
                }
            }
        }
    }
}
