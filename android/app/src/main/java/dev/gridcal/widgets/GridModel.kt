package dev.gridcal.widgets

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.ceil

/** Which days the widget shows and how the header describes them. */
data class GridRange(
    val start: LocalDate,
    val weeks: Int,
    val primaryMonth: YearMonth,
    val title: String,
    val columns: List<DayOfWeek>,
) {
    val end: LocalDate get() = start.plusDays(weeks * 7L - 1)
    fun date(row: Int, col: Int): LocalDate {
        val dow = columns[col]
        val weekStart = start.plusWeeks(row.toLong())
        return weekStart.plusDays(((dow.value - start.dayOfWeek.value + 7) % 7).toLong())
    }
}

object GridModel {
    fun firstDay(cfg: WidgetConfig): DayOfWeek = when (val v = cfg[S.FIRST_DAY]) {
        "locale" -> WeekFields.of(Locale.getDefault()).firstDayOfWeek
        else -> DayOfWeek.valueOf(v)
    }

    fun range(cfg: WidgetConfig, today: LocalDate, offset: Int): GridRange {
        val fd = firstDay(cfg)
        val columns = (0..6).map { fd.plus(it.toLong()) }.filter {
            (it != DayOfWeek.SATURDAY || cfg[S.SHOW_SATURDAY]) && (it != DayOfWeek.SUNDAY || cfg[S.SHOW_SUNDAY])
        }.ifEmpty { listOf(fd) }
        val view = cfg[S.VIEW]
        return if (view == "month") {
            val ym = YearMonth.from(today).plusMonths(offset.toLong())
            val start = ym.atDay(1).with(TemporalAdjusters.previousOrSame(fd))
            val weeks = if (cfg[S.DISPLAYED_WEEKS] == "6") 6
            else ceil((ChronoUnit.DAYS.between(start, ym.atEndOfMonth()) + 1) / 7.0).toInt()
            val title = ym.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
            GridRange(start, weeks, ym, title, columns)
        } else {
            val n = view.toInt()
            val anchor = today.plusWeeks(offset.toLong() * n)
            val start = anchor.with(TemporalAdjusters.previousOrSame(fd))
            val end = start.plusDays(n * 7L - 1)
            GridRange(start, n, YearMonth.from(anchor), rangeTitle(start, end, today), columns)
        }
    }

    private fun rangeTitle(a: LocalDate, b: LocalDate, today: LocalDate): String {
        val loc = Locale.getDefault()
        val short = DateTimeFormatter.ofPattern("MMM d", loc)
        val withYear = DateTimeFormatter.ofPattern("MMM d, yyyy", loc)
        // Keep it short: only show the year (once, at the end) when the range isn't entirely in this year.
        val endFmt = if (a.year == today.year && b.year == today.year) short else withYear
        return if (a == b) a.format(endFmt) else "${a.format(short)} – ${b.format(endFmt)}"
    }

    fun dowLabel(cfg: WidgetConfig, dow: DayOfWeek): String {
        val loc = Locale.getDefault()
        return when (cfg[S.DOW_FORMAT]) {
            "short" -> dow.getDisplayName(TextStyle.SHORT, loc)
            "narrow" -> dow.getDisplayName(TextStyle.NARROW, loc)
            else -> dow.getDisplayName(TextStyle.SHORT, loc).uppercase(loc)
        }
    }

    fun formatTime(cfg: WidgetConfig, t: LocalDateTime): String =
        if (cfg[S.USE_24H]) "%d:%02d".format(t.hour, t.minute)
        else {
            val h = (t.hour + 11) % 12 + 1
            val ap = if (t.hour < 12) "am" else "pm"
            if (t.minute == 0) "$h$ap" else "$h:%02d$ap".format(t.minute)
        }
}

/** All colors after applying the theme and any custom overrides. */
data class Palette(
    val bg: Int, val fg: Int, val dim: Int, val line: Int,
    val header: Int, val headerText: Int,
    val dayLabel: Int, val dayLabelBg: Int,
    val weekNum: Int, val weekNumBg: Int,
    val primaryBg: Int, val secondaryBg: Int,
    val weekday: Int, val saturday: Int, val sunday: Int, val holiday: Int,
    val allDayText: Int, val timedTextFilled: Int, val timedTextPlain: Int,
    val today: Int, val alpha: Float,
) {
    companion object {
        fun isDark(ctx: Context, cfg: WidgetConfig) = when (cfg[S.THEME]) {
            "light" -> false
            "dark", "black" -> true
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        fun resolve(ctx: Context, cfg: WidgetConfig): Palette {
            val black = cfg[S.THEME] == "black"
            val dark = isDark(ctx, cfg)
            val bg = cfg[S.C_BACKGROUND] ?: when { black -> 0xFF000000.toInt(); dark -> 0xFF1C1D23.toInt(); else -> Color.WHITE }
            val fg = when { black -> 0xFFF2F2F2.toInt(); dark -> 0xFFE4E4EA.toInt(); else -> 0xFF1F1F23.toInt() }
            val dim = (fg and 0x00FFFFFF) or 0x8C000000.toInt()
            val line = cfg[S.C_LINE] ?: when { black -> 0xFF2B2B2B.toInt(); dark -> 0xFF383A42.toInt(); else -> 0xFFDADCE0.toInt() }
            val today = cfg[S.C_TODAY] ?: if (dark) 0xFFA8C7FA.toInt() else 0xFF0B57D0.toInt()
            val alpha = (100 - cfg[S.TRANSPARENCY].coerceIn(0, 100)) / 100f
            return Palette(
                bg = bg, fg = fg, dim = dim, line = line,
                header = cfg[S.C_HEADER] ?: Color.TRANSPARENT,
                headerText = cfg[S.C_HEADER_TEXT] ?: fg,
                dayLabel = cfg[S.C_DAY_LABEL] ?: fg,
                dayLabelBg = cfg[S.C_DAY_LABEL_BG] ?: Color.TRANSPARENT,
                weekNum = cfg[S.C_WEEK_NUM] ?: dim,
                weekNumBg = cfg[S.C_WEEK_NUM_BG] ?: Color.TRANSPARENT,
                primaryBg = cfg[S.C_PRIMARY_MONTH_BG] ?: Color.TRANSPARENT,
                secondaryBg = cfg[S.C_SECONDARY_MONTH_BG] ?: when { black -> 0xFF0E0E0E.toInt(); dark -> 0xFF24262D.toInt(); else -> 0xFFF3F4F6.toInt() },
                weekday = cfg[S.C_DATE] ?: cfg[S.C_WEEKDAY] ?: fg,
                saturday = cfg[S.C_SATURDAY] ?: 0xFF8E9299.toInt(),
                sunday = cfg[S.C_SUNDAY] ?: 0xFFF2483B.toInt(),
                holiday = cfg[S.C_HOLIDAY] ?: 0xFFF2483B.toInt(),
                allDayText = cfg[S.C_ALLDAY_TEXT] ?: Color.WHITE,
                timedTextFilled = cfg[S.C_TIMED_TEXT] ?: Color.WHITE,
                timedTextPlain = cfg[S.C_TIMED_TEXT] ?: fg,
                today = today,
                alpha = alpha,
            )
        }
    }
}

fun luminance(c: Int): Double {
    fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
    return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
}

fun contrastOn(c: Int): Int = if (luminance(c) > 0.5) 0xFF1F1F23.toInt() else Color.WHITE

fun withAlpha(c: Int, a: Float): Int = Color.argb((Color.alpha(c) * a).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
