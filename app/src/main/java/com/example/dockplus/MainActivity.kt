package com.example.dockplus

import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.google.gson.Gson

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DockPlusScreen(
                        onSaveClick = { selectedApps ->
                            handleSaveClick(selectedApps)
                        }
                    )
                }
            }
        }
    }

    private fun handleSaveClick(selectedApps: Set<AppInfo>) {
        if (selectedApps.isNotEmpty() && selectedApps.size <= 5) {
            val success = saveSelectedApps(this, selectedApps.toList())
            if (success) {
                Toast.makeText(this, "Selected apps saved successfully", Toast.LENGTH_SHORT).show()
                stopOverlayService()
                checkOverlayPermission()
            } else {
                Toast.makeText(this, "Failed to save selected apps", Toast.LENGTH_SHORT).show()
            }
        } else if (selectedApps.isEmpty()) {
            Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopOverlayService() {
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        } else {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        startService(Intent(this, OverlayService::class.java))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockPlusScreen(onSaveClick: (Set<AppInfo>) -> Unit) {
    val context = LocalContext.current
    val installedApps = remember { getInstalledApps(context) }

    val savedAppsPackageNames = remember { getSavedSelectedApps(context) }
    var selectedApps by remember {
        mutableStateOf(
            installedApps.filter { it.packageName in savedAppsPackageNames }.toSet()
        )
    }

    var showLimitReached by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Dock+",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.titleLarge

                        )
                    }
                }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Selected ${selectedApps.size}/5 apps",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 16.dp)
                ) {
                    items(installedApps) { app ->
                        AppCardItem(
                            app = app,
                            isSelected = selectedApps.contains(app),
                            onSelectionChanged = { isSelected ->
                                if (isSelected && selectedApps.size < 5) {
                                    selectedApps = selectedApps + app
                                } else if (!isSelected) {
                                    selectedApps = selectedApps - app
                                } else {
                                    showLimitReached = true
                                }
                            }
                        )
                    }
                }

                Button(
                    onClick = { onSaveClick(selectedApps) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    enabled = selectedApps.isNotEmpty()
                ) {
                    Text("Save")
                }
            }

            if (showLimitReached) {
                Snackbar(
                    modifier = Modifier.padding(8.dp),
                    action = {
                        TextButton(onClick = { showLimitReached = false }) {
                            Text("OK")
                        }
                    },
                    content = { Text("You can select a maximum of 5 apps") }
                )
            }
        }
    )
}

@Composable
fun AppCardItem(
    app: AppInfo,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionChanged(it) }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Image(
                bitmap = app.icon.toBitmap().asImageBitmap(),
                contentDescription = "App icon",
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}



data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable
)

fun getInstalledApps(context: android.content.Context): List<AppInfo> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null)
    intent.addCategory(Intent.CATEGORY_LAUNCHER)

    return packageManager.queryIntentActivities(intent, 0).map { resolveInfo: ResolveInfo ->
        AppInfo(
            resolveInfo.loadLabel(packageManager).toString(),
            resolveInfo.activityInfo.packageName,
            resolveInfo.loadIcon(packageManager)
        )
    }.sortedBy { it.name }
}

fun saveSelectedApps(context: android.content.Context, selectedApps: List<AppInfo>): Boolean {
    return try {
        val sharedPreferences = context.getSharedPreferences("DockPlusPrefs", ComponentActivity.MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(selectedApps.map { it.packageName })
        sharedPreferences.edit().putString("selected_apps", json).apply()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun getSavedSelectedApps(context: android.content.Context): Set<String> {
    val sharedPreferences = context.getSharedPreferences("DockPlusPrefs", ComponentActivity.MODE_PRIVATE)
    val json = sharedPreferences.getString("selected_apps", "[]")
    val gson = Gson()
    return gson.fromJson(json, Array<String>::class.java)?.toSet() ?: emptySet()
}
