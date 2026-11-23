package dev.gridcal.widgets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A choice option: stored value + label shown in the UI. */
data class Opt(val value: String, val label: String)

sealed class Key<T>(val id: String, val default: T)
class BoolKey(id: String, default: Boolean) : Key<Boolean>(id, default)
class IntKey(id: String, default: Int) : Key<Int>(id, default)
class ChoiceKey(id: String, default: String, val options: List<Opt>) : Key<String>(id, default)
/** Nullable color: null means "use the theme's default". */
class ColorKey(id: String) : Key<Int?>(id, null)
class StringKey(id: String) : Key<String?>(id, null)
class LongListKey(id: String) : Key<List<Long>?>(id, null)

/** Every per-widget setting lives here. */
object S {
    // ---- General
    val VIEW = ChoiceKey("view", "4", listOf(
        Opt("month", "Month"), Opt("1", "1 week"), Opt("2", "2 weeks"), Opt("3", "3 weeks"),
        Opt("4", "4 weeks"), Opt("5", "5 weeks"), Opt("6", "6 weeks"),
    ))
    val DISPLAYED_WEEKS = ChoiceKey("displayedWeeks", "auto", listOf(
        Opt("auto", "Default"), Opt("6", "Always 6 weeks"),
    ))
    val EVENT_DURATION = ChoiceKey("eventDuration", "60", listOf(
        Opt("15", "15 minutes"), Opt("30", "30 minutes"), Opt("45", "45 minutes"),
        Opt("60", "1 hour"), Opt("90", "1.5 hours"), Opt("120", "2 hours"),
    ))
    val FIRST_DAY = ChoiceKey("firstDay", "locale", listOf(
        Opt("locale", "Locale default"), Opt("SUNDAY", "Sunday"), Opt("MONDAY", "Monday"), Opt("SATURDAY", "Saturday"),
    ))
    val DAY_LABEL_ALIGN = ChoiceKey("dayLabelAlign", "center", listOf(
        Opt("left", "Left"), Opt("center", "Center"), Opt("right", "Right"),
    ))
    val DOW_FORMAT = ChoiceKey("dowFormat", "upper", listOf(
        Opt("upper", "Default (SUN)"), Opt("short", "Short (Sun)"), Opt("narrow", "Narrow (S)"),
    ))
    val DATE_ALIGN = ChoiceKey("dateAlign", "auto", listOf(
        Opt("auto", "Locale (Auto)"), Opt("left", "Left"), Opt("center", "Center"), Opt("right", "Right"),
    ))
    val SHOW_START_TIME = BoolKey("showStartTime", false)
    val START_TIME_FIRST = BoolKey("startTimeFirst", true)
    val USE_24H = BoolKey("use24h", false)
    val SHOW_LOCATION = BoolKey("showLocation", false)
    val SHOW_WEEK_NUMBER = BoolKey("showWeekNumber", false)
    val SHOW_SATURDAY = BoolKey("showSaturday", true)
    val SHOW_SUNDAY = BoolKey("showSunday", true)
    val BIG_HEADER = BoolKey("bigHeader", true)
    val SHOW_TODAY_ICON = BoolKey("showTodayIcon", true)
    val SHOW_SETTINGS_ICON = BoolKey("showSettingsIcon", true)
    val HIDE_DECLINED = BoolKey("hideDeclined", false)
    val FILTER_DUPLICATES = BoolKey("filterDuplicates", true)
    val FILTER_HOLIDAY_CALS = BoolKey("filterHolidayCals", false)
    val CALENDARS = LongListKey("calendars")             // null = all visible calendars

    // ---- Appearance
    val THEME = ChoiceKey("theme", "system", listOf(
        Opt("system", "Default (Light & Dark)"), Opt("light", "Light"), Opt("dark", "Dark"), Opt("black", "Black"),
    ))
    val EVENT_STYLE = ChoiceKey("eventStyle", "filled", listOf(
        Opt("filled", "Filled"), Opt("tinted", "Tinted"), Opt("bar", "Side bar"), Opt("text", "Colored text"),
    ))
    val TRANSPARENCY = IntKey("transparency", 0)          // 0..100
    val DATE_FONT = IntKey("dateFont", 13)                // sp
    val EVENT_FONT = IntKey("eventFont", 9)               // sp
    val WORD_WRAP = ChoiceKey("wordWrap", "auto", listOf(
        Opt("auto", "Auto adjust (Recommended)"), Opt("wrap", "Always wrap"), Opt("single", "Single line"),
    ))
    val C_BACKGROUND = ColorKey("cBackground")
    val C_DATE = ColorKey("cDate")
    val C_HEADER = ColorKey("cHeader")
    val C_HEADER_TEXT = ColorKey("cHeaderText")
    val C_DAY_LABEL = ColorKey("cDayLabel")
    val C_DAY_LABEL_BG = ColorKey("cDayLabelBg")
    val C_WEEK_NUM = ColorKey("cWeekNum")
    val C_WEEK_NUM_BG = ColorKey("cWeekNumBg")
    val C_PRIMARY_MONTH_BG = ColorKey("cPrimaryMonthBg")
    val C_SECONDARY_MONTH_BG = ColorKey("cSecondaryMonthBg")
    val C_WEEKDAY = ColorKey("cWeekday")
    val C_SATURDAY = ColorKey("cSaturday")
    val C_SUNDAY = ColorKey("cSunday")
    val C_HOLIDAY = ColorKey("cHoliday")
    val C_ALLDAY_TEXT = ColorKey("cAllDayText")
    val C_TIMED_TEXT = ColorKey("cTimedText")
    val C_LINE = ColorKey("cLine")
    val C_TODAY = ColorKey("cToday")
    val SHOW_VERTICAL_LINE = BoolKey("verticalLine", false)
    val TODAY_STYLE = ChoiceKey("todayStyle", "filledCircle", listOf(
        Opt("filledCircle", "Filled circle"), Opt("outlineCircle", "Outline circle"),
        Opt("filledSquare", "Filled square"), Opt("cell", "Cell background"), Opt("text", "Text color only"),
    ))
    val SYSTEM_FONT_SCALE = BoolKey("systemFontScale", true)
    val DIM_PAST = BoolKey("dimPast", true)
    val FADE_ADJACENT = BoolKey("fadeAdjacent", true)
    val AUTO_CONTRAST = BoolKey("autoContrast", true)
    val ROUNDED = BoolKey("rounded", true)
    val TIMED_AS_ALLDAY = BoolKey("timedAsAllDay", true)
    val TIMED_TEXT_MATCHES = BoolKey("timedTextMatches", true)
    val HL_TODAY_DOW = BoolKey("hlTodayDow", true)
    val HL_SATURDAY = BoolKey("hlSaturday", false)
    val HL_SUNDAY = BoolKey("hlSunday", true)
    val HL_HOLIDAY = BoolKey("hlHoliday", true)
    val HL_MULTIWEEK = BoolKey("hlMultiWeek", false)

    // ---- Advanced
    val TIME_ZONE = StringKey("timeZone")                 // null = device default
    val AUTO_ADVANCE = BoolKey("autoAdvance", true)
    val ALT_CALENDAR = ChoiceKey("altCalendar", "none", listOf(
        Opt("none", "None"), Opt("chinese", "Chinese lunar"), Opt("hebrew", "Hebrew"),
        Opt("islamic", "Islamic (Hijri)"), Opt("persian", "Persian"), Opt("indian", "Indian national"),
    ))
    val ALT_EVERY_DAY = BoolKey("altEveryDay", false)
    val BUSINESS_HOURS = BoolKey("businessHours", false)
    val BUSINESS_START = IntKey("businessStart", 9)
    val BUSINESS_END = IntKey("businessEnd", 17)
    val TAP_EMPTY = ChoiceKey("tapEmpty", "add", listOf(
        Opt("add", "Add a new event"), Opt("list", "Show the day's events"), Opt("open", "Open calendar app"), Opt("none", "Do nothing"),
    ))
    val TAP_EVENTS = ChoiceKey("tapEvents", "list", listOf(
        Opt("list", "Default (show the day's events)"), Opt("open", "Open calendar app"), Opt("add", "Add a new event"), Opt("none", "Do nothing"),
    ))
    val SIZE_CALC = ChoiceKey("sizeCalc", "portrait", listOf(
        Opt("portrait", "Default (portrait)"), Opt("landscape", "Landscape"), Opt("largest", "Largest"), Opt("smallest", "Smallest"),
    ))
    val GROUP_BY_CALENDAR = BoolKey("groupByCalendar", false)
    val CALENDAR_PRIORITY = LongListKey("calendarPriority")
    val SORT_ORDER = ChoiceKey("sortOrder", "time", listOf(
        Opt("time", "Start time"), Opt("title", "Title (A–Z)"), Opt("duration", "Longest first"),
    ))
    val ALLDAY_FIRST = BoolKey("allDayFirst", true)

    val ALL: List<Key<*>> = listOf(
        VIEW, DISPLAYED_WEEKS, EVENT_DURATION, FIRST_DAY, DAY_LABEL_ALIGN, DOW_FORMAT, DATE_ALIGN,
        SHOW_START_TIME, START_TIME_FIRST, USE_24H, SHOW_LOCATION, SHOW_WEEK_NUMBER, SHOW_SATURDAY,
        SHOW_SUNDAY, BIG_HEADER, SHOW_TODAY_ICON, SHOW_SETTINGS_ICON, HIDE_DECLINED, FILTER_DUPLICATES,
        FILTER_HOLIDAY_CALS, CALENDARS, THEME, EVENT_STYLE, TRANSPARENCY, DATE_FONT, EVENT_FONT, WORD_WRAP,
        C_BACKGROUND, C_DATE, C_HEADER, C_HEADER_TEXT, C_DAY_LABEL, C_DAY_LABEL_BG, C_WEEK_NUM, C_WEEK_NUM_BG,
        C_PRIMARY_MONTH_BG, C_SECONDARY_MONTH_BG, C_WEEKDAY, C_SATURDAY, C_SUNDAY, C_HOLIDAY, C_ALLDAY_TEXT,
        C_TIMED_TEXT, C_LINE, C_TODAY, SHOW_VERTICAL_LINE, TODAY_STYLE, SYSTEM_FONT_SCALE, DIM_PAST,
        FADE_ADJACENT, AUTO_CONTRAST, ROUNDED, TIMED_AS_ALLDAY, TIMED_TEXT_MATCHES, HL_TODAY_DOW, HL_SATURDAY,
        HL_SUNDAY, HL_HOLIDAY, HL_MULTIWEEK, TIME_ZONE, AUTO_ADVANCE, ALT_CALENDAR, ALT_EVERY_DAY,
        BUSINESS_HOURS, BUSINESS_START, BUSINESS_END, TAP_EMPTY, TAP_EVENTS, SIZE_CALC, GROUP_BY_CALENDAR,
        CALENDAR_PRIORITY, SORT_ORDER, ALLDAY_FIRST,
    )
}

/** Immutable snapshot of a widget's settings. Only non-default values are stored. */
class WidgetConfig(private val values: Map<String, Any?> = emptyMap()) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: Key<T>): T =
        if (values.containsKey(key.id)) values[key.id] as T else key.default

    fun <T> with(key: Key<T>, value: T): WidgetConfig {
        val m = values.toMutableMap()
        if (value == key.default) m.remove(key.id) else m[key.id] = value
        return WidgetConfig(m)
    }

    fun toJson(): String {
        val o = JSONObject()
        for ((k, v) in values) {
            o.put(k, when (v) {
                null -> JSONObject.NULL
                is List<*> -> JSONArray(v)
                else -> v
            })
        }
        return o.toString()
    }

    override fun equals(other: Any?) = other is WidgetConfig && other.values == values
    override fun hashCode() = values.hashCode()

    companion object {
        fun fromJson(json: String?): WidgetConfig {
            if (json.isNullOrBlank()) return WidgetConfig()
            return try {
                val o = JSONObject(json)
                val m = HashMap<String, Any?>()
                for (key in S.ALL) {
                    if (!o.has(key.id)) continue
                    val raw = o.get(key.id)
                    m[key.id] = when (key) {
                        is BoolKey -> raw as? Boolean ?: continue
                        is IntKey -> (raw as? Number)?.toInt() ?: continue
                        is ChoiceKey -> (raw as? String)?.takeIf { v -> key.options.any { it.value == v } } ?: continue
                        is ColorKey -> if (raw == JSONObject.NULL) null else (raw as? Number)?.toInt() ?: continue
                        is StringKey -> if (raw == JSONObject.NULL) null else raw as? String ?: continue
                        is LongListKey -> if (raw == JSONObject.NULL) null else (raw as? JSONArray)?.let { a ->
                            List(a.length()) { a.getLong(it) }
                        } ?: continue
                    }
                }
                WidgetConfig(m)
            } catch (e: Exception) {
                WidgetConfig()
            }
        }
    }
}

/** Per-widget storage: settings + navigation offset. */
object Store {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun config(ctx: Context, id: Int): WidgetConfig = WidgetConfig.fromJson(prefs(ctx).getString("cfg_$id", null))
    fun hasConfig(ctx: Context, id: Int) = prefs(ctx).contains("cfg_$id")
    fun saveConfig(ctx: Context, id: Int, cfg: WidgetConfig) {
        prefs(ctx).edit().putString("cfg_$id", cfg.toJson()).apply()
    }

    fun offset(ctx: Context, id: Int) = prefs(ctx).getInt("off_$id", 0)
    fun setOffset(ctx: Context, id: Int, v: Int) { prefs(ctx).edit().putInt("off_$id", v).apply() }

    fun delete(ctx: Context, id: Int) { prefs(ctx).edit().remove("cfg_$id").remove("off_$id").apply() }

    /** Settings to start a new widget from: the last one saved, so new widgets match your style. */
    fun lastUsed(ctx: Context): WidgetConfig = WidgetConfig.fromJson(prefs(ctx).getString("last", null))
    fun setLastUsed(ctx: Context, cfg: WidgetConfig) { prefs(ctx).edit().putString("last", cfg.toJson()).apply() }
}
