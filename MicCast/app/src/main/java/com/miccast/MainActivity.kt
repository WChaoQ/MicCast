package com.miccast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.miccast.ui.screens.MainScreen
import com.miccast.ui.theme.MicCastTheme
import com.miccast.viewmodel.MicCastViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MicCastViewModel by viewModels()

    // ─────────────────────────────────────────────────────────────────────────
    //  Permission launcher
    // ─────────────────────────────────────────────────────────────────────────

    private var permissionsGranted by mutableStateOf(false)
    private var showRationale by mutableStateOf(false)

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // RECORD_AUDIO is the only hard requirement
        permissionsGranted = results[Manifest.permission.RECORD_AUDIO] == true
        if (!permissionsGranted) {
            showRationale = true
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            MicCastTheme(darkTheme = isSystemInDarkTheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (permissionsGranted) {
                        MainScreen(viewModel = viewModel)
                    }

                    // Rationale dialog when permission was denied
                    if (showRationale) {
                        AlertDialog(
                            onDismissRequest = { showRationale = false },
                            title = { Text("需要麦克风权限") },
                            text = { Text("MicCast 需要麦克风权限才能将音频传输到您的电脑。请在系统设置中授予权限。") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showRationale = false
                                    permissionLauncher.launch(requiredPermissions)
                                }) { Text("重试") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRationale = false; finish() }) {
                                    Text("退出")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}