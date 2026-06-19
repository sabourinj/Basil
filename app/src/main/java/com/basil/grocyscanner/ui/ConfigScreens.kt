package com.basil.grocyscanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoveDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.basil.grocyscanner.BuildConfig
import com.basil.grocyscanner.R
import com.basil.grocyscanner.data.GrocyLocation
import com.basil.grocyscanner.ui.theme.DarkerHeaderPurple
import com.basil.grocyscanner.ui.theme.DeepPurple
import com.basil.grocyscanner.ui.theme.ErrorRed
import com.basil.grocyscanner.ui.theme.SuccessGreen
import com.basil.grocyscanner.viewmodel.ScannerViewModel
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

@Composable
fun AiSetupScreen(onEnableAi: () -> Unit, onSkip: () -> Unit) {
    var step by remember { mutableIntStateOf(1) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (step == 1) {
            Image(
                painter = painterResource(id = R.drawable.gemini_logo),
                contentDescription = "Gemini Logo",
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text("Data Automation", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Basil uses Google Gemini to estimate expiration rules, predict prices, and organize new products into your defined categories.", textAlign = TextAlign.Center, color = Color.LightGray)
            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = {
                onEnableAi()
                step = 2
            }, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = DeepPurple)) {
                Text("Enable Gemini AI", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onSkip) { Text("Skip for now", color = Color.LightGray) }

        } else {
            Image(
                painter = painterResource(id = R.drawable.gemini_logo),
                contentDescription = "Gemini Logo",
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("API Key Required", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("1. Create a Gemini API Key.\n2. Generate a QR code for the key.\n3. Scan the QR Code.", textAlign = TextAlign.Left, color = Color.LightGray)
            Spacer(modifier = Modifier.height(48.dp))
            TextButton(onClick = onSkip) { Text("Skip for now", color = Color.LightGray) }
        }
    }
}

@Composable
fun UnconfiguredScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painter = painterResource(id = R.drawable.grocy_logo), contentDescription = "Grocy Logo", modifier = Modifier.size(120.dp))
        Text("API Key Required", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("1. Generate an API Key in Grocy.\n2. Click the Show QR Code button.\n3. Scan the QR code displayed.", textAlign = TextAlign.Left, color = Color.LightGray)
    }
}

@Composable
fun RowScope.ModeButton(title: String, isSelected: Boolean, onClick: () -> Unit) {
    if (isSelected) {
        Button(
            onClick = onClick, modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = DeepPurple),
            contentPadding = PaddingValues(0.dp)
        ) { Text(title, fontWeight = FontWeight.Bold, maxLines = 1) }
    } else {
        OutlinedButton(
            onClick = onClick, modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, Color.LightGray),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
            contentPadding = PaddingValues(0.dp)
        ) { Text(title, maxLines = 1) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isAiEnabled: Boolean,
    onToggleAi: (Boolean) -> Unit,
    resetDurationSeconds: Int,
    onResetDurationChange: (Int) -> Unit,
    viewModel: ScannerViewModel,
    onStartBatchMove: (Int, String) -> Unit,
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var showCreditsDialog by remember { mutableStateOf(false) }
    var showBatchModal by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val locations by viewModel.locations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkerHeaderPurple, titleContentColor = Color.White)
            )
        },
        containerColor = DeepPurple
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Image(painter = painterResource(id = R.drawable.basil_logo), contentDescription = "Basil Logo", modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Basil", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text(text = "v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = Color.LightGray)

            Spacer(modifier = Modifier.height(32.dp))

            // Settings Group 1: Behavior
            Text("App Behavior", style = MaterialTheme.typography.labelLarge, color = SuccessGreen, modifier = Modifier.fillMaxWidth(0.8f))
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Gemini AI", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Switch(
                    checked = isAiEnabled,
                    onCheckedChange = { onToggleAi(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = DeepPurple, checkedTrackColor = Color.LightGray)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auto-Reset Delay", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("${resetDurationSeconds}s", style = MaterialTheme.typography.titleMedium, color = SuccessGreen, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = resetDurationSeconds.toFloat(),
                    onValueChange = { onResetDurationChange(it.toInt()) },
                    valueRange = 2f..10f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = SuccessGreen,
                        activeTrackColor = SuccessGreen,
                        inactiveTrackColor = Color.Gray
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth(0.8f))
            Spacer(modifier = Modifier.height(24.dp))

            // Settings Group 2: Advanced Tools
            Text("Advanced Tools", style = MaterialTheme.typography.labelLarge, color = SuccessGreen, modifier = Modifier.fillMaxWidth(0.8f))
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showBatchModal = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepPurple),
                modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
            ) {
                Icon(imageVector = Icons.Default.MoveDown, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Batch Mode", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Settings Group 3: Account
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Logout / Disconnect", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Developed with ❤️ in Massachusetts\nby Justin Sabourin",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { showCreditsDialog = true }) { Text("Open Source Licenses", color = Color.Gray) }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showBatchModal) {
        BatchMoveModal(
            locations = locations,
            onConfirm = { locId, locName ->
                showBatchModal = false
                onStartBatchMove(locId, locName)
            },
            onDismiss = { showBatchModal = false }
        )
    }

    if (showCreditsDialog) {
        Dialog(
            onDismissRequest = { showCreditsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(0.8f),
                shape = RoundedCornerShape(16.dp),
                color = Color.LightGray
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Open Source Licenses",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.Black
                        )
                        IconButton(onClick = { showCreditsDialog = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Credits",
                                tint = Color.Black
                            )
                        }
                    }

                    MaterialTheme(
                        colorScheme = MaterialTheme.colorScheme,
                        typography = MaterialTheme.typography.copy(
                            titleLarge = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp), // Library Names
                            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), // Library Descriptions
                            bodySmall = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),   // Version Numbers
                            labelLarge = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp)  // License Badges
                        )
                    ) {
                        LibrariesContainer(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchMoveModal(
    locations: List<GrocyLocation>,
    onConfirm: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf<GrocyLocation?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DeepPurple,
            border = BorderStroke(2.dp, Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select Destination", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = selectedLocation?.name ?: "Select Location...",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        locations.forEach { location ->
                            DropdownMenuItem(
                                text = { Text(location.name) },
                                onClick = {
                                    selectedLocation = location
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { selectedLocation?.let { onConfirm(it.id, it.name) } },
                        enabled = selectedLocation != null,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = DeepPurple)
                    ) {
                        Text("Start Batch Mode", fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepPurple)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
