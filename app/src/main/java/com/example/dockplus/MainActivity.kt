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
                checkOverlayPermission()
            } else {
                Toast.makeText(this, "Failed to save selected apps", Toast.LENGTH_SHORT).show()
            }
        } else if (selectedApps.isEmpty()) {
            Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show()
        }
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

@Composable
fun DockPlusScreen(onSaveClick: (Set<AppInfo>) -> Unit) {
    val context = LocalContext.current
    val installedApps = remember { getInstalledApps(context) }
    var selectedApps by remember { mutableStateOf(setOf<AppInfo>()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Selected ${selectedApps.size}/5 apps",
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(installedApps) { app ->
                AppItem(
                    app = app,
                    isSelected = selectedApps.contains(app),
                    onSelectionChanged = { isSelected ->
                        if (isSelected && selectedApps.size < 5) {
                            selectedApps = selectedApps + app
                        } else if (!isSelected) {
                            selectedApps = selectedApps - app
                        } else {
                            Toast.makeText(context, "You can select a maximum of 5 apps", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
        Button(
            onClick = { onSaveClick(selectedApps) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Save")
        }
    }
}

@Composable
fun AppItem(
    app: AppInfo,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
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
        Text(text = app.name)
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