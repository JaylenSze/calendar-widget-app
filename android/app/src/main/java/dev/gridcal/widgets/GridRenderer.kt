package dev.gridcal.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.icu.util.Calendar as IcuCalendar
import android.icu.util.ULocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the week rows of the calendar (dates + event chips) into a bitmap.
 * Header and weekday labels are real views in the widget layout; only the grid is drawn here.
 */
class GridRenderer(
    private val cfg: WidgetConfig,
    private val pal: Palette,
    private val range: GridRange,
    events: List<Ev>,
    private val now: LocalDateTime,
    private val density: Float,
    fontScale: Float,
) {
    private val today: LocalDate = now.toLocalDate()
    private val sp = density * (if (cfg[S.SYSTEM_FONT_SCALE]) fontScale else 1f)
    private fun dp(v: Float) = v * density

    private val holidays: Set<LocalDate> = buildSet {
        for (e in events) if (e.holiday) {
            var d = e.startDate
            while (d <= e.endDate) { add(d); d = d.plusDays(1) }
        }
    }
    private val shown = events.filter { it.display }
    private val priority = cfg[S.CALENDAR_PRIORITY].orEmpty()

    private val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = cfg[S.DATE_FONT] * sp; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
    private val evPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = cfg[S.EVENT_FONT] * sp }
    private val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = max(cfg[S.EVENT_FONT] * sp * 1.05f, dp(8f)) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint().apply { strokeWidth = max(1f, dp(0.7f)) }

    private val altCal: IcuCalendar? = when (val a = cfg[S.ALT_CALENDAR]) {
        "none" -> null
        else -> runCatching { IcuCalendar.getInstance(ULocale("@calendar=$a")) }.getOrNull()
    }

    fun render(width: Int, height: Int): Bitmap {
        val w = max(width, 1); val h = max(height, 1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wnW = if (cfg[S.SHOW_WEEK_NUMBER]) dp(22f) else 0f
        val cols = range.columns.size
        val cw = (w - wnW) / cols
        val rh = h.toFloat() / range.weeks
        linePaint.color = withAlpha(pal.line, max(pal.alpha, 0.35f))

        for (r in 0 until range.weeks) {
            val top = r * rh
            drawWeek(c, r, top, rh, wnW, cw)
        }
        // Grid lines on top so cell backgrounds don't cover them.
        for (r in 0 until range.weeks) c.drawLine(0f, r * rh, w.toFloat(), r * rh, linePaint)
        if (cfg[S.SHOW_VERTICAL_LINE]) for (i in 1 until cols) {
            val x = wnW + i * cw
            c.drawLine(x, 0f, x, h.toFloat(), linePaint)
        }
        if (wnW > 0) c.drawLine(wnW, 0f, wnW, h.toFloat(), linePaint)
        return bmp
    }

    private fun drawWeek(c: Canvas, row: Int, top: Float, rh: Float, wnW: Float, cw: Float) {
        val cols = range.columns.size
        val dates = List(cols) { range.date(row, it) }
        val bottom = top + rh - dp(1.5f)

        // ---- cell backgrounds
        for (i in 0 until cols) {
            val d = dates[i]
            val bg = if (YearMonth.from(d) == range.primaryMonth) pal.primaryBg else pal.secondaryBg
            val left = wnW + i * cw
            if (android.graphics.Color.alpha(bg) > 0) {
                fill.color = withAlpha(bg, pal.alpha); c.drawRect(left, top, left + cw, top + rh, fill)
            }
            if (d == today && cfg[S.TODAY_STYLE] == "cell") {
                fill.color = withAlpha(pal.today, 0.22f); c.drawRect(left, top, left + cw, top + rh, fill)
            }
        }

        // ---- week number column
        if (wnW > 0) {
            if (android.graphics.Color.alpha(pal.weekNumBg) > 0) {
                fill.color = withAlpha(pal.weekNumBg, pal.alpha); c.drawRect(0f, top, wnW, top + rh, fill)
            }
            val wn = range.start.plusWeeks(row.toLong()).plusDays(3).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString()
            smallPaint.color = pal.weekNum; smallPaint.typeface = Typeface.DEFAULT
            c.drawText(wn, (wnW - smallPaint.measureText(wn)) / 2, top + dp(3f) - smallPaint.ascent(), smallPaint)
        }

        val dateRowH = datePaint.fontSpacing + dp(5f)
        val evTop = top + dateRowH
        val cursor = FloatArray(cols) { evTop }
        val overflow = IntArray(cols)
        val padV = dp(1f); val padH = dp(2.5f); val gap = dp(1.5f)
        val lineH = evPaint.fontSpacing

        // ---- events this week, mapped to visible columns
        data class Placed(val ev: Ev, val first: Int, val last: Int)
        val weekStart = range.start.plusWeeks(row.toLong())
        val weekEnd = weekStart.plusDays(6)
        val placed = shown.filter { it.endDate >= weekStart && it.startDate <= weekEnd }.mapNotNull { ev ->
            val idx = dates.indices.filter { dates[it] >= ev.startDate && dates[it] <= ev.endDate }
            if (idx.isEmpty()) null else Placed(ev, idx.first(), idx.last())
        }
        val multi = placed.filter { it.ev.multiDay }
            .sortedWith(compareBy<Placed>({ it.ev.startDate }, { -(it.ev.endDate.toEpochDay() - it.ev.startDate.toEpochDay()) }, { it.ev.title }))
        for (p in multi) {
            val y = (p.first..p.last).maxOf { cursor[it] }
            val hgt = lineH + 2 * padV
            if (y + hgt > bottom) { for (i in p.first..p.last) overflow[i]++; continue }
            val left = wnW + p.first * cw + dp(1f)
            val right = wnW + (p.last + 1) * cw - dp(1f)
            val contL = p.ev.startDate < dates[p.first]
            val contR = p.ev.endDate > dates[p.last]
            drawChip(c, p.ev, left, y, right, y + hgt, label(p.ev, !contL), 1, contL, contR, padH, padV)
            for (i in p.first..p.last) cursor[i] = y + hgt + gap
        }

        val comparator = singleComparator()
        for (i in 0 until cols) {
            val singles = placed.filter { !it.ev.multiDay && it.ev.startDate == dates[i] }.map { it.ev }.sortedWith(comparator)
            if (singles.isEmpty()) continue
            val left = wnW + i * cw + dp(1f)
            val right = wnW + (i + 1) * cw - dp(1f)
            val textW = (right - left - 2 * padH - barInset()).toInt().coerceAtLeast(1)
            val mode = cfg[S.WORD_WRAP]
            val wrap = when (mode) {
                "single" -> false
                "wrap" -> true
                else -> { // auto: wrap only if everything still fits
                    val need = singles.sumOf { (wrappedLines(label(it, true), textW) * lineH + 2 * padV + gap).toDouble() }
                    need <= (bottom - cursor[i])
                }
            }
            for (ev in singles) {
                val avail = bottom - cursor[i]
                val fit = floor((avail - 2 * padV) / lineH).toInt()
                if (fit < 1) { overflow[i]++; continue }
                val text = label(ev, true)
                val lines = if (wrap) min(wrappedLines(text, textW), fit) else 1
                val hgt = lines * lineH + 2 * padV
                drawChip(c, ev, left, cursor[i], right, cursor[i] + hgt, text, lines, false, false, padH, padV)
                cursor[i] += hgt + gap
            }
        }

        // ---- date numbers (+ overflow counts, alternate calendar)
        for (i in 0 until cols) drawDate(c, dates[i], wnW + i * cw, top, cw, overflow[i])
    }

    private fun singleComparator(): Comparator<Ev> {
        var cmp: Comparator<Ev> = Comparator { _, _ -> 0 }
        if (cfg[S.ALLDAY_FIRST]) cmp = cmp.thenBy { if (it.allDay) 0 else 1 }
        if (cfg[S.GROUP_BY_CALENDAR]) cmp = cmp.thenBy { priority.indexOf(it.calendarId).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenBy { it.calendarId }
        cmp = when (cfg[S.SORT_ORDER]) {
            "title" -> cmp.thenBy { it.title.lowercase() }
            "duration" -> cmp.thenByDescending { it.endMs - it.beginMs }.thenBy { it.start }
            else -> cmp.thenBy { it.start ?: it.startDate.atStartOfDay() }.thenBy { it.title.lowercase() }
        }
        return cmp
    }

    private fun label(ev: Ev, firstSegment: Boolean): String {
        val sb = StringBuilder()
        val time = if (cfg[S.SHOW_START_TIME] && !ev.allDay && firstSegment && ev.start != null) GridModel.formatTime(cfg, ev.start) else null
        if (time != null && cfg[S.START_TIME_FIRST]) sb.append(time).append(' ')
        sb.append(ev.title)
        if (time != null && !cfg[S.START_TIME_FIRST]) sb.append(' ').append(time)
        if (cfg[S.SHOW_LOCATION] && ev.location.isNotBlank()) sb.append(" · ").append(ev.location)
        return sb.toString()
    }

    private fun barInset() = if (cfg[S.EVENT_STYLE] == "bar") dp(3f) else 0f

    private fun wrappedLines(text: String, width: Int): Int =
        StaticLayout.Builder.obtain(text, 0, text.length, evPaint, width).setIncludePad(false).build().lineCount

    private fun drawChip(
        c: Canvas, ev: Ev, l: Float, t: Float, r: Float, b: Float, text: String, lines: Int,
        contL: Boolean, contR: Boolean, padH: Float, padV: Float,
    ) {
        val past = cfg[S.DIM_PAST] && (ev.end ?: ev.endDate.plusDays(1).atStartOfDay()) <= now
        val a = if (past) 0.45f else 1f
        val plainTimed = !ev.allDay && !ev.multiDay && !cfg[S.TIMED_AS_ALLDAY]
        val style = if (plainTimed) "plain" else cfg[S.EVENT_STYLE]
        val radius = if (cfg[S.ROUNDED]) dp(3f) else 0f
        val rect = RectF(l, t, r, b)
        var textColor: Int
        var inset = 0f
        when (style) {
            "filled" -> {
                fill.color = withAlpha(ev.color or 0xFF000000.toInt(), a)
                roundRect(c, rect, radius, contL, contR)
                textColor = if (ev.allDay || ev.multiDay) pal.allDayText else pal.timedTextFilled
                if (cfg[S.AUTO_CONTRAST] && luminance(ev.color) > 0.55 && luminance(textColor) > 0.5) textColor = 0xFF1F1F23.toInt()
            }
            "tinted" -> {
                fill.color = withAlpha(ev.color or 0xFF000000.toInt(), 0.33f * a)
                roundRect(c, rect, radius, contL, contR)
                textColor = pal.fg
            }
            "bar" -> {
                fill.color = withAlpha(ev.color or 0xFF000000.toInt(), a)
                c.drawRect(l, t, l + dp(2.5f), b, fill)
                inset = dp(3f)
                textColor = pal.fg
            }
            "text" -> textColor = ev.color or 0xFF000000.toInt()
            else -> textColor = if (cfg[S.TIMED_TEXT_MATCHES]) ev.color or 0xFF000000.toInt() else pal.timedTextPlain
        }
        evPaint.color = withAlpha(textColor, a)
        evPaint.typeface = Typeface.DEFAULT

        if (cfg[S.HL_MULTIWEEK] && (contL || contR)) {
            fill.color = evPaint.color
            val s = dp(3f); val cy = (t + b) / 2
            if (contL) c.drawPath(Path().apply { moveTo(l + dp(1f), cy); lineTo(l + dp(1f) + s, cy - s); lineTo(l + dp(1f) + s, cy + s); close() }, fill)
            if (contR) c.drawPath(Path().apply { moveTo(r - dp(1f), cy); lineTo(r - dp(1f) - s, cy - s); lineTo(r - dp(1f) - s, cy + s); close() }, fill)
            if (contL) inset += s + dp(1f)
        }

        val x = l + padH + inset
        val width = (r - padH - x).toInt().coerceAtLeast(1)
        c.save()
        c.clipRect(l, t, r, b)
        if (lines <= 1) {
            c.drawText(text, x, t + padV - evPaint.ascent(), evPaint)
        } else {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, evPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
            c.translate(x, t + padV)
            layout.draw(c)
        }
        c.restore()
    }

    private fun roundRect(c: Canvas, rect: RectF, radius: Float, squareLeft: Boolean, squareRight: Boolean) {
        if (radius <= 0f) { c.drawRect(rect, fill); return }
        c.drawRoundRect(rect, radius, radius, fill)
        if (squareLeft) c.drawRect(rect.left, rect.top, rect.left + radius, rect.bottom, fill)
        if (squareRight) c.drawRect(rect.right - radius, rect.top, rect.right, rect.bottom, fill)
    }

    private fun drawDate(c: Canvas, d: LocalDate, left: Float, top: Float, cw: Float, more: Int) {
        val isToday = d == today
        val adjacent = YearMonth.from(d) != range.primaryMonth
        var color = when {
            cfg[S.HL_HOLIDAY] && d in holidays -> pal.holiday
            cfg[S.HL_SUNDAY] && d.dayOfWeek == DayOfWeek.SUNDAY -> pal.sunday
            cfg[S.HL_SATURDAY] && d.dayOfWeek == DayOfWeek.SATURDAY -> pal.saturday
            else -> pal.weekday
        }
        if (adjacent && cfg[S.FADE_ADJACENT]) color = withAlpha(color, 0.42f)
        val text = d.dayOfMonth.toString()
        datePaint.typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val tw = datePaint.measureText(text)
        val pad = dp(5f)
        val align = when (val a = cfg[S.DATE_ALIGN]) {
            "auto" -> if (TextUtils.getLayoutDirectionFromLocale(java.util.Locale.getDefault()) == android.view.View.LAYOUT_DIRECTION_RTL) "right" else "left"
            else -> a
        }
        val x = when (align) {
            "center" -> left + (cw - tw) / 2
            "right" -> left + cw - pad - tw
            else -> left + pad + dp(1f)
        }
        val baseline = top + dp(3f) - datePaint.ascent()
        if (isToday) {
            val cx = x + tw / 2
            val cy = baseline + (datePaint.ascent() + datePaint.descent()) / 2
            val rad = max(tw, datePaint.textSize) * 0.72f
            when (cfg[S.TODAY_STYLE]) {
                "filledCircle" -> { fill.color = pal.today; c.drawCircle(cx, cy, rad, fill); color = contrastOn(pal.today) }
                "filledSquare" -> { fill.color = pal.today; c.drawRoundRect(cx - rad, cy - rad, cx + rad, cy + rad, dp(4f), dp(4f), fill); color = contrastOn(pal.today) }
                "outlineCircle" -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.5f); this.color = pal.today }
                    c.drawCircle(cx, cy, rad, p); color = pal.today
                }
                else -> color = pal.today
            }
        }
        datePaint.color = color
        c.drawText(text, x, baseline, datePaint)

        // alternate calendar day
        altCal?.let { cal ->
            cal.timeInMillis = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val ad = cal.get(IcuCalendar.DAY_OF_MONTH)
            if (cfg[S.ALT_EVERY_DAY] || ad == 1 || ad == 15) {
                val s = if (ad == 1) "${cal.get(IcuCalendar.MONTH) + 1}/1" else ad.toString()
                smallPaint.color = withAlpha(pal.fg, 0.5f); smallPaint.typeface = Typeface.DEFAULT
                val sx = if (align == "right") x - dp(3f) - smallPaint.measureText(s) else x + tw + dp(4f)
                c.drawText(s, sx, baseline, smallPaint)
            }
        }

        if (more > 0) {
            val s = "+$more"
            smallPaint.color = pal.fg; smallPaint.typeface = Typeface.DEFAULT
            val sw = smallPaint.measureText(s)
            val sx = if (align == "right") left + pad else left + cw - pad - sw
            c.drawText(s, sx, baseline, smallPaint)
        }
    }
}
