package dev.gridcal.widgets

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GridcalTheme { Surface(Modifier.fillMaxSize()) { Home() } } }
    }
}

@Composable
private fun Home() {
    val ctx = LocalContext.current
    var permitted by remember { mutableStateOf(CalendarRepo.hasPermission(ctx)) }
    var ids by remember { mutableStateOf(Widgets.ids(ctx).toList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted = it
        if (it) Widgets.requestUpdate(ctx)
    }
    // Refresh the list when coming back (e.g. after adding a widget).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { ids = Widgets.ids(ctx).toList(); permitted = CalendarRepo.hasPermission(ctx) }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Gridcal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("A calendar widget for your home screen that shows weeks or a whole month of events from the calendars on your phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Calendar access", fontWeight = FontWeight.SemiBold)
                if (permitted) Text("Allowed ✓", color = MaterialTheme.colorScheme.primary)
                else {
                    Text("The widget reads events from your phone's calendars (Google, Samsung, Outlook…). Nothing leaves your phone.")
                    Button(onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) }) { Text("Allow calendar access") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Add the widget", fontWeight = FontWeight.SemiBold)
                Text("Long-press your home screen → Widgets → Gridcal. Or:")
                val mgr = AppWidgetManager.getInstance(ctx)
                if (mgr.isRequestPinAppWidgetSupported) {
                    Button(onClick = { mgr.requestPinAppWidget(ComponentName(ctx, GridWidgetProvider::class.java), null, null) }) {
                        Text("Add to home screen")
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Customize", fontWeight = FontWeight.SemiBold)
                if (ids.isEmpty()) Text("No widgets placed yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ids.forEachIndexed { i, id ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("Widget ${i + 1}", Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically))
                        OutlinedButton(onClick = {
                            ctx.startActivity(Intent(ctx, ConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        }) { Text("Settings") }
                    }
                }
                Text("Tip: the sliders icon on the widget also opens its settings.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
