package dev.gridcal.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GridcalTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Handles a tap on a day in the widget: add an event, open the calendar app, or list that day's events. */
class DayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        val date = runCatching { LocalDate.parse(intent.getStringExtra(Widgets.EXTRA_DATE)) }.getOrElse { LocalDate.now() }
        val cfg = Widgets.configFor(this, id)
        when (intent.getStringExtra(Widgets.EXTRA_MODE)) {
            "add" -> { CalendarIntents.addEvent(this, cfg, date); finish(); return }
            "open" -> { CalendarIntents.openDay(this, cfg, date); finish(); return }
            "list" -> Unit
            else -> { finish(); return }
        }
        val events = CalendarRepo.events(this, cfg, date, date).filter { it.display }
            .sortedWith(compareBy<Ev>({ if (it.allDay || it.multiDay) 0 else 1 }, { it.start ?: it.startDate.atStartOfDay() }))
        setContent {
            GridcalTheme {
                DaySheet(cfg, date, events, onDismiss = { finish() })
            }
        }
    }
}

object CalendarIntents {
    private fun zone(cfg: WidgetConfig): ZoneId = CalendarRepo.zoneFor(cfg)

    private fun launch(a: Activity, i: Intent) {
        try { a.startActivity(i) } catch (e: ActivityNotFoundException) {
            Toast.makeText(a, "No calendar app found", Toast.LENGTH_SHORT).show()
        }
    }

    fun addEvent(a: Activity, cfg: WidgetConfig, date: LocalDate) {
        val now = java.time.LocalDateTime.now(zone(cfg))
        val start = if (date == now.toLocalDate()) now.plusHours(1).withMinute(0).withSecond(0).withNano(0) else date.atTime(9, 0)
        val begin = start.atZone(zone(cfg)).toInstant().toEpochMilli()
        val minutes = cfg[S.EVENT_DURATION].toLong()
        launch(a, Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + minutes * 60_000))
    }

    fun openDay(a: Activity, cfg: WidgetConfig, date: LocalDate) {
        val ms = date.atTime(9, 0).atZone(zone(cfg)).toInstant().toEpochMilli()
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, ms) }.build()
        launch(a, Intent(Intent.ACTION_VIEW).setData(uri))
    }

    fun openEvent(a: Activity, ev: Ev) {
        launch(a, Intent(Intent.ACTION_VIEW)
            .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, ev.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ev.beginMs)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ev.endMs))
    }
}

@Composable
private fun DaySheet(cfg: WidgetConfig, date: LocalDate, events: List<Ev>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current as Activity
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.padding(24.dp).fillMaxWidth().clickable(enabled = false) {},
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                if (events.isEmpty()) {
                    Text("No events", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(events) { ev -> EventRow(cfg, ev, date) { CalendarIntents.openEvent(ctx, ev); ctx.finish() } }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { CalendarIntents.openDay(ctx, cfg, date); ctx.finish() }) { Text("Open calendar") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { CalendarIntents.addEvent(ctx, cfg, date); ctx.finish() }) { Text("+ New event") }
                }
            }
        }
    }
}

@Composable
private fun EventRow(cfg: WidgetConfig, ev: Ev, date: LocalDate, onClick: () -> Unit) {
    val time = when {
        ev.allDay -> "All day"
        ev.start != null && ev.end != null -> {
            val s = if (ev.start.toLocalDate() == date) GridModel.formatTime(cfg, ev.start) else "…"
            val e = if (ev.end.toLocalDate() == date) GridModel.formatTime(cfg, ev.end) else "…"
            "$s – $e"
        }
        else -> ""
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(36.dp).background(Color(ev.color or 0xFF000000.toInt()), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ev.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                listOf(time, ev.location).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
