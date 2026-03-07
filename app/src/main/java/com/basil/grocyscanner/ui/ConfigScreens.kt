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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.basil.grocyscanner.R
import com.basil.grocyscanner.ui.theme.DarkerHeaderPurple
import com.basil.grocyscanner.ui.theme.DeepPurple
import com.basil.grocyscanner.ui.theme.ErrorRed
import com.basil.grocyscanner.ui.theme.SuccessGreen


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
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit
) {
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
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(painter = painterResource(id = R.drawable.basil_logo), contentDescription = "Basil Logo", modifier = Modifier.size(96.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Basil", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Version 1.1.0-AI", style = MaterialTheme.typography.titleMedium, color = Color.LightGray)

            Spacer(modifier = Modifier.height(32.dp))

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

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = DeepPurple),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Logout / Disconnect", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Developed with ❤️ in Massachusetts\nby Justin Sabourin",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}