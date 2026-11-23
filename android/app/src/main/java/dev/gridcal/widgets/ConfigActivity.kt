package dev.gridcal.widgets

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import kotlin.concurrent.thread

class ConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        // If the user backs out while adding the widget, the launcher removes it.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val initial = Widgets.configFor(this, id)
        setContent {
            GridcalTheme {
                ConfigScreen(
                    widgetId = id,
                    initial = initial,
                    onClose = { finish() },
                    onSave = { cfg -> save(id, cfg) },
                )
            }
        }
    }

    private fun save(id: Int, cfg: WidgetConfig) {
        Store.saveConfig(this, id, cfg)
        Store.setLastUsed(this, cfg)
        Store.setOffset(this, id, 0)
        val app = applicationContext
        thread { Widgets.update(app, AppWidgetManager.getInstance(app), id); Widgets.scheduleAll(app) }
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}

private enum class Tab(val title: String) { General("General"), Appearance("Appearance"), Advanced("Advanced") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(widgetId: Int, initial: WidgetConfig, onClose: () -> Unit, onSave: (WidgetConfig) -> Unit) {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(initial) }
    var tab by rememberSaveable { mutableStateOf(Tab.General) }
    var permitted by remember { mutableStateOf(CalendarRepo.hasPermission(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    LaunchedEffect(Unit) { if (!permitted) permLauncher.launch(Manifest.permission.READ_CALENDAR) }
    var menu by remember { mutableStateOf(false) }
    val set: (WidgetConfig) -> Unit = { cfg = it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget settings") },
                navigationIcon = { TextButton(onClick = onClose) { Text("✕", style = MaterialTheme.typography.titleLarge) } },
                actions = {
                    Button(onClick = { onSave(cfg) }) { Text("Save") }
                    Box {
                        TextButton(onClick = { menu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Reset to defaults") }, onClick = { cfg = WidgetConfig(); menu = false })
                            val others = Widgets.ids(ctx).filter { it != widgetId && Store.hasConfig(ctx, it) }
                            others.forEachIndexed { i, other ->
                                DropdownMenuItem(text = { Text("Copy from widget ${i + 1}") }, onClick = { cfg = Store.config(ctx, other); menu = false })
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Preview(widgetId, cfg, permitted)
            if (!permitted) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Showing sample events. Allow calendar access to see yours.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { permLauncher.launch(Manifest.permission.READ_CALENDAR) }) { Text("Allow") }
                }
            }
            @Suppress("DEPRECATION")
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab.entries.forEach { t ->
                    Tab(
                        selected = tab == t, onClick = { tab = t }, text = { Text(t.title) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                when (tab) {
                    Tab.General -> GeneralTab(cfg, set)
                    Tab.Appearance -> AppearanceTab(cfg, set)
                    Tab.Advanced -> AdvancedTab(cfg, set)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun Preview(widgetId: Int, cfg: WidgetConfig, permitted: Boolean) {
    val ctx = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp - 24f
    val heightDp = 250f
    var views by remember { mutableStateOf<RemoteViews?>(null) }
    LaunchedEffect(cfg, permitted, widthDp) {
        views = withContext(Dispatchers.IO) { Widgets.build(ctx, widgetId, cfg, 0, widthDp, heightDp, interactive = false) }
    }
    Box(
        Modifier.fillMaxWidth().height(heightDp.dp + 24.dp).background(Color(0xFF101114)).padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        views?.let { rv ->
            AndroidView(
                factory = { FrameLayout(it) },
                update = { frame -> frame.removeAllViews(); frame.addView(rv.apply(frame.context, frame)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ------------------------------------------------------------------ tabs

@Composable
private fun GeneralTab(cfg: WidgetConfig, set: (WidgetConfig) -> Unit) {
    var calendars by remember { mutableStateOf(false) }
    ChoiceRow("View setting", S.VIEW, cfg, set)
    ChoiceRow("Displayed weeks", S.DISPLAYED_WEEKS, cfg, set, enabled = cfg[S.VIEW] == "month", hint = "Month view only")
    ChoiceRow("Event duration", S.EVENT_DURATION, cfg, set, hint = "Length of events you add from the widget")
    ChoiceRow("First day of the week", S.FIRST_DAY, cfg, set)
    Divider()
    ChoiceRow("Day label alignment", S.DAY_LABEL_ALIGN, cfg, set)
    ChoiceRow("Day of week format", S.DOW_FORMAT, cfg, set)
    Divider()
    ChoiceRow("Date alignment", S.DATE_ALIGN, cfg, set)
    Divider()
    SwitchRow("Show event start time", S.SHOW_START_TIME, cfg, set)
    SwitchRow("Show event start time before event title", S.START_TIME_FIRST, cfg, set, enabled = cfg[S.SHOW_START_TIME])
    SwitchRow("Use 24 hour format", S.USE_24H, cfg, set)
    Divider()
    SwitchRow("Show location", S.SHOW_LOCATION, cfg, set)
    Divider()
    SwitchRow("Show week number", S.SHOW_WEEK_NUMBER, cfg, set)
    Divider()
    SwitchRow("Display Saturday", S.SHOW_SATURDAY, cfg, set)
    SwitchRow("Display Sunday", S.SHOW_SUNDAY, cfg, set)
    Divider()
    SwitchRow("Use big header", S.BIG_HEADER, cfg, set)
    SwitchRow("Show today icon", S.SHOW_TODAY_ICON, cfg, set)
    SwitchRow("Show settings icon", S.SHOW_SETTINGS_ICON, cfg, set)
    Divider()
    SwitchRow("Hide declined events", S.HIDE_DECLINED, cfg, set)
    SwitchRow("Filter duplicate events", S.FILTER_DUPLICATES, cfg, set)
    SwitchRow("Filter holiday calendars", S.FILTER_HOLIDAY_CALS, cfg, set)
    ButtonRow("Calendars to display") { calendars = true }
    if (calendars) CalendarsDialog(cfg, set) { calendars = false }
}

@Composable
private fun AppearanceTab(cfg: WidgetConfig, set: (WidgetConfig) -> Unit) {
    val ctx = LocalContext.current
    val pal = remember(cfg) { Palette.resolve(ctx, cfg) }
    var displayStyle by remember { mutableStateOf(false) }
    ChoiceRow("Theme", S.THEME, cfg, set)
    ChoiceRow("Event color highlight option", S.EVENT_STYLE, cfg, set)
    ButtonRow("Event display style") { displayStyle = true }
    Divider()
    SliderRow("Transparency", S.TRANSPARENCY, 0..100, "%", cfg, set)
    Divider()
    SliderRow("Date font size", S.DATE_FONT, 8..24, "", cfg, set)
    SliderRow("Event font size", S.EVENT_FONT, 6..16, "", cfg, set)
    Divider()
    ChoiceRow("Word wrapping", S.WORD_WRAP, cfg, set)
    Divider()
    ColorRow("Background color", S.C_BACKGROUND, pal.bg, cfg, set)
    ColorRow("Date color", S.C_DATE, pal.weekday, cfg, set)
    Divider()
    ColorRow("Header color", S.C_HEADER, pal.header, cfg, set)
    ColorRow("Header text & icon color", S.C_HEADER_TEXT, pal.headerText, cfg, set)
    Divider()
    ColorRow("Day label color", S.C_DAY_LABEL, pal.dayLabel, cfg, set)
    ColorRow("Day label background color", S.C_DAY_LABEL_BG, pal.dayLabelBg, cfg, set)
    Divider()
    ColorRow("Week number color", S.C_WEEK_NUM, pal.weekNum, cfg, set)
    ColorRow("Week number background color", S.C_WEEK_NUM_BG, pal.weekNumBg, cfg, set)
    Divider()
    ColorRow("Primary month background color", S.C_PRIMARY_MONTH_BG, pal.primaryBg, cfg, set)
    ColorRow("Secondary month background color", S.C_SECONDARY_MONTH_BG, pal.secondaryBg, cfg, set)
    Divider()
    ColorRow("Weekday color", S.C_WEEKDAY, pal.weekday, cfg, set)
    ColorRow("Saturday color", S.C_SATURDAY, pal.saturday, cfg, set)
    ColorRow("Sunday color", S.C_SUNDAY, pal.sunday, cfg, set)
    ColorRow("Holiday color", S.C_HOLIDAY, pal.holiday, cfg, set)
    Divider()
    ColorRow("All-day event text color", S.C_ALLDAY_TEXT, pal.allDayText, cfg, set)
    ColorRow("Timed event text color", S.C_TIMED_TEXT, pal.timedTextFilled, cfg, set)
    Divider()
    ColorRow("Line color", S.C_LINE, pal.line, cfg, set)
    SwitchRow("Show vertical line", S.SHOW_VERTICAL_LINE, cfg, set)
    Divider()
    ChoiceRow("Today highlight option", S.TODAY_STYLE, cfg, set)
    ColorRow("Today highlight color", S.C_TODAY, pal.today, cfg, set)
    Divider()
    SwitchRow("Use system font size scaling", S.SYSTEM_FONT_SCALE, cfg, set)
    Divider()
    SwitchRow("Dim past events", S.DIM_PAST, cfg, set)
    SwitchRow("Fade adjacent months", S.FADE_ADJACENT, cfg, set)
    Divider()
    SwitchRow("Auto-adjust event text color against bright background", S.AUTO_CONTRAST, cfg, set)
    Divider()
    SwitchRow("Draw rounded rectangles", S.ROUNDED, cfg, set)
    SwitchRow("Draw timed events as all-day", S.TIMED_AS_ALLDAY, cfg, set)
    Divider()
    SwitchRow("Highlight today's day of the week", S.HL_TODAY_DOW, cfg, set)
    SwitchRow("Highlight Saturdays", S.HL_SATURDAY, cfg, set)
    SwitchRow("Highlight Sundays", S.HL_SUNDAY, cfg, set)
    SwitchRow("Highlight holidays", S.HL_HOLIDAY, cfg, set)
    Divider()
    SwitchRow("Highlight multi-week events", S.HL_MULTIWEEK, cfg, set)
    if (displayStyle) DisplayStyleDialog(cfg, set) { displayStyle = false }
}

@Composable
private fun AdvancedTab(cfg: WidgetConfig, set: (WidgetConfig) -> Unit) {
    var tz by remember { mutableStateOf(false) }
    var priorities by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(false) }
    ValueRow("Time zone", cfg[S.TIME_ZONE] ?: "Default") { tz = true }
    Divider()
    SwitchRow("Return to today at midnight", S.AUTO_ADVANCE, cfg, set)
    Divider()
    ChoiceRow("Alternate calendar", S.ALT_CALENDAR, cfg, set)
    SwitchRow("Show on every day", S.ALT_EVERY_DAY, cfg, set, enabled = cfg[S.ALT_CALENDAR] != "none")
    Divider()
    SwitchRow("Show business hours only (use time filtering)", S.BUSINESS_HOURS, cfg, set)
    if (cfg[S.BUSINESS_HOURS]) {
        SliderRow("Business hours start", S.BUSINESS_START, 0..23, ":00", cfg, set)
        SliderRow("Business hours end", S.BUSINESS_END, 1..24, ":00", cfg, set)
    }
    Divider()
    ChoiceRow("Tap action for empty day", S.TAP_EMPTY, cfg, set)
    ChoiceRow("Tap action for day w/ events", S.TAP_EVENTS, cfg, set)
    Divider()
    ChoiceRow("Widget size calculation", S.SIZE_CALC, cfg, set)
    Divider()
    SwitchRow("Group by calendar", S.GROUP_BY_CALENDAR, cfg, set)
    ButtonRow("Calendar priorities") { priorities = true }
    ButtonRow("Event sort order") { sort = true }
    if (tz) TimeZoneDialog(cfg, set) { tz = false }
    if (priorities) PrioritiesDialog(cfg, set) { priorities = false }
    if (sort) SortDialog(cfg, set) { sort = false }
}

// ------------------------------------------------------------------ rows

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun RowLabel(label: String, hint: String?, enabled: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ValueRow(label: String, value: String, enabled: Boolean = true, hint: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label, hint, enabled, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        Text("  ▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChoiceRow(label: String, key: ChoiceKey, cfg: WidgetConfig, set: (WidgetConfig) -> Unit, enabled: Boolean = true, hint: String? = null) {
    var open by remember { mutableStateOf(false) }
    Box {
        val current = key.options.firstOrNull { it.value == cfg[key] }?.label ?: cfg[key]
        ValueRow(label, current, enabled, hint) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.align(Alignment.TopEnd)) {
            key.options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.label, fontWeight = if (o.value == cfg[key]) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { set(cfg.with(key, o.value)); open = false },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, key: BoolKey, cfg: WidgetConfig, set: (WidgetConfig) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled) { set(cfg.with(key, !cfg[key])) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label, null, enabled, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = cfg[key], onCheckedChange = { set(cfg.with(key, it)) }, enabled = enabled)
    }
}

@Composable
private fun SliderRow(label: String, key: IntKey, range: IntRange, suffix: String, cfg: WidgetConfig, set: (WidgetConfig) -> Unit) {
    var local by remember(cfg[key]) { mutableFloatStateOf(cfg[key].toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("${local.toInt()}$suffix")
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { set(cfg.with(key, local.toInt())) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ColorRow(label: String, key: ColorKey, shown: Int, cfg: WidgetConfig, set: (WidgetConfig) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { open = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label, if (cfg[key] == null) null else "Custom", true, Modifier.weight(1f))
        Swatch(shown, 34.dp)
    }
    if (open) ColorDialog(label, cfg[key], shown, onDismiss = { open = false }) { set(cfg.with(key, it)); open = false }
}

@Composable
private fun Swatch(color: Int, size: androidx.compose.ui.unit.Dp, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val transparent = android.graphics.Color.alpha(color) == 0
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp))
            .background(if (transparent) Color.Transparent else Color(color))
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { if (transparent) Text("∅", color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ButtonRow(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(52.dp)) { Text(label) }
}

// ------------------------------------------------------------------ dialogs

private val PRESETS = listOf(
    0xFFFFFFFF, 0xFFE0E0E0, 0xFF9E9E9E, 0xFF616161, 0xFF303030, 0xFF1C1D23, 0xFF000000, 0x00000000,
    0xFFF2483B, 0xFFE67C73, 0xFFF4511E, 0xFFF6BF26, 0xFF33B679, 0xFF0B8043, 0xFF039BE5, 0xFF3F51B5,
    0xFF7986CB, 0xFF8E24AA, 0xFFD81B60, 0xFF795548, 0xFFA8C7FA, 0xFF0B57D0, 0xFFFFF4C7, 0xFFDFE8DC,
).map { it.toInt() }

@Composable
private fun ColorDialog(title: String, current: Int?, shown: Int, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    var picked by remember { mutableIntStateOf(current ?: shown) }
    var hex by remember { mutableStateOf("%08X".format(picked)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                PRESETS.chunked(8).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { c -> Swatch(c, 30.dp, selected = c == picked) { picked = c; hex = "%08X".format(c) } }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { v ->
                        hex = v.uppercase().filter { it.isDigit() || it in 'A'..'F' }.take(8)
                        val n = hex.toLongOrNull(16)
                        if (n != null && (hex.length == 6 || hex.length == 8)) picked = (if (hex.length == 6) n or 0xFF000000 else n).toInt()
                    },
                    label = { Text("Hex (AARRGGBB)") },
                    prefix = { Text("#") },
                    singleLine = true,
                    trailingIcon = { Swatch(picked, 24.dp) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(picked) }) { Text("OK") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }) { Text("Default") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun CalendarsDialog(cfg: WidgetConfig, set: (WidgetConfig) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cals = remember { CalendarRepo.calendars(ctx) }
    val chosen = remember { mutableStateListOf<Long>().apply { addAll(cfg[S.CALENDARS] ?: cals.filter { it.visible }.map { it.id }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calendars to display") },
        text = {
            if (cals.isEmpty()) Text("No calendars found. Make sure calendar access is allowed and a calendar account is set up on this phone.")
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                cals.groupBy { it.account }.forEach { (account, list) ->
                    item { Text(account.ifBlank { "Local" }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                    items(list) { cal ->
                        Row(Modifier.fillMaxWidth().clickable { if (cal.id in chosen) chosen.remove(cal.id) else chosen.add(cal.id) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = cal.id in chosen, onCheckedChange = { if (it) chosen.add(cal.id) else chosen.remove(cal.id) })
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(cal.color or 0xFF000000.toInt())))
                            Spacer(Modifier.width(8.dp))
                            Text(cal.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val visible = cals.filter { it.visible }.map { it.id }.toSet()
                set(cfg.with(S.CALENDARS, if (chosen.toSet() == visible) null else chosen.toList()))
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PrioritiesDialog(cfg: WidgetConfig, set: (WidgetConfig) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cals = remember { CalendarRepo.calendars(ctx).associateBy { it.id } }
    val order = remember {
        val saved = cfg[S.CALENDAR_PRIORITY].orEmpty().filter { it in cals }
        mutableStateListOf<Long>().apply { addAll(saved + cals.keys.filter { it !in saved }) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calendar priorities") },
        text = {
            Column {
                Text("Events from calendars higher in the list are drawn first.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (order.isEmpty()) Text("No calendars found.")
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    itemsIndexed(order.toList()) { i, id ->
                        val cal = cals[id] ?: return@itemsIndexed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(cal.color or 0xFF000000.toInt())))
                            Spacer(Modifier.width(8.dp))
                            Text(cal.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(enabled = i > 0, onClick = { order.removeAt(i); order.add(i - 1, id) }) { Text("↑") }
                            TextButton(enabled = i < order.size - 1, onClick = { order.removeAt(i); order.add(i + 1, id) }) { Text("↓") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { set(cfg.with(S.CALENDAR_PRIORITY, order.toList())); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SortDialog(cfg: WidgetConfig, set: (WidgetConfig) -> Unit, onDismiss: () -> Unit) {
    var order by remember { mutableStateOf(cfg[S.SORT_ORDER]) }
    var allDayFirst by remember { mutableStateOf(cfg[S.ALLDAY_FIRST]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Event sort order") },
        text = {
            Column {
                S.SORT_ORDER.options.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable { order = o.value }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = order == o.value, onClick = { order = o.value }); Text(o.label)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth().clickable { allDayFirst = !allDayFirst }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allDayFirst, onCheckedChange = { allDayFirst = it }); Text("All-day events first")
                }
            }
        },
        confirmButton = { TextButton(onClick = { set(cfg.with(S.SORT_ORDER, order).with(S.ALLDAY_FIRST, allDayFirst)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DisplayStyleDialog(cfg: WidgetConfig, set: (WidgetConfig) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Event display style") },
        text = {
            Column {
                Radio("Show timed events as filled blocks, like all-day events", cfg[S.TIMED_AS_ALLDAY]) { set(cfg.with(S.TIMED_AS_ALLDAY, true)) }
                Radio("Show timed events as plain text, different from all-day events", !cfg[S.TIMED_AS_ALLDAY]) { set(cfg.with(S.TIMED_AS_ALLDAY, false)) }
                if (!cfg[S.TIMED_AS_ALLDAY]) {
                    Column(Modifier.padding(start = 24.dp, top = 8.dp)) {
                        Radio("Color timed event text with the event's color", cfg[S.TIMED_TEXT_MATCHES]) { set(cfg.with(S.TIMED_TEXT_MATCHES, true)) }
                        Radio("Use the timed event text color from Appearance", !cfg[S.TIMED_TEXT_MATCHES]) { set(cfg.with(S.TIMED_TEXT_MATCHES, false)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Radio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun TimeZoneDialog(cfg: WidgetConfig, set: (WidgetConfig) -> Unit, onDismiss: () -> Unit) {
    val all = remember { ZoneId.getAvailableZoneIds().filter { '/' in it && !it.startsWith("Etc") }.sorted() }
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time zone") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    item {
                        Text("Default (${ZoneId.systemDefault().id})", Modifier.fillMaxWidth().clickable { set(cfg.with(S.TIME_ZONE, null)); onDismiss() }.padding(vertical = 12.dp), fontWeight = FontWeight.Medium)
                    }
                    items(all.filter { it.contains(q, ignoreCase = true) }) { z ->
                        Text(z.replace('_', ' '), Modifier.fillMaxWidth().clickable { set(cfg.with(S.TIME_ZONE, z)); onDismiss() }.padding(vertical = 10.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
