package com.example.dockplus

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
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
                    DockPlusScreen()
                }
            }
        }
    }
}

@Composable
fun DockPlusScreen() {
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
            onClick = {
                if (selectedApps.isNotEmpty() && selectedApps.size <= 5) {
                    val success = saveSelectedApps(context, selectedApps.toList())
                    if (success) {
                        Toast.makeText(context, "Selected apps saved successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to save selected apps", Toast.LENGTH_SHORT).show()
                    }
                } else if (selectedApps.isEmpty()) {
                    Toast.makeText(context, "Please select at least one app", Toast.LENGTH_SHORT).show()
                }
            },
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