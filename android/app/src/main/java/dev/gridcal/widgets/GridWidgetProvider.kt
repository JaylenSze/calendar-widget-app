package dev.gridcal.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDateTime
import kotlin.concurrent.thread
import kotlin.math.sqrt

class GridWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        background { ids.forEach { Widgets.update(ctx, mgr, it) }; Widgets.scheduleAll(ctx) }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        background { Widgets.update(ctx, mgr, id) }
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach { Store.delete(ctx, it) }
    }

    override fun onEnabled(ctx: Context) = Widgets.scheduleAll(ctx)

    override fun onDisabled(ctx: Context) {
        ctx.getSystemService(JobScheduler::class.java)?.cancel(Widgets.JOB_ID)
        ctx.getSystemService(AlarmManager::class.java)?.cancel(Widgets.midnightIntent(ctx))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Widgets.ACTION_NAV -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
                val delta = intent.getIntExtra(Widgets.EXTRA_DELTA, 0)
                Store.setOffset(ctx, id, if (delta == 0) 0 else Store.offset(ctx, id) + delta)
                background { Widgets.update(ctx, AppWidgetManager.getInstance(ctx), id) }
            }
            Widgets.ACTION_MIDNIGHT -> background {
                val mgr = AppWidgetManager.getInstance(ctx)
                for (id in Widgets.ids(ctx)) if (Store.config(ctx, id)[S.AUTO_ADVANCE]) Store.setOffset(ctx, id, 0)
                Widgets.updateAll(ctx)
                Widgets.scheduleAll(ctx)
            }
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED,
            Widgets.ACTION_REFRESH -> background { Widgets.updateAll(ctx); Widgets.scheduleAll(ctx) }
            else -> super.onReceive(ctx, intent)
        }
    }

    private fun background(block: () -> Unit) {
        val pending = goAsync()
        thread {
            try { block() } finally { pending.finish() }
        }
    }
}

/** Re-renders all widgets whenever the calendar database changes. */
class CalendarChangeJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread {
            Widgets.updateAll(applicationContext)
            Widgets.scheduleCalendarJob(applicationContext)
            jobFinished(params, false)
        }
        return true
    }
    override fun onStopJob(params: JobParameters) = false
}

object Widgets {
    const val ACTION_NAV = "dev.gridcal.widgets.NAV"
    const val ACTION_MIDNIGHT = "dev.gridcal.widgets.MIDNIGHT"
    const val ACTION_REFRESH = "dev.gridcal.widgets.REFRESH"
    const val EXTRA_DELTA = "delta"
    const val EXTRA_DATE = "date"
    const val EXTRA_MODE = "mode"
    const val JOB_ID = 4242

    private const val HEADER_DP = 44f
    private const val LABELS_DP = 22f

    fun ids(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, GridWidgetProvider::class.java))

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        ids(ctx).forEach { update(ctx, mgr, it) }
    }

    fun requestUpdate(ctx: Context) {
        ctx.sendBroadcast(Intent(ctx, GridWidgetProvider::class.java).setAction(ACTION_REFRESH))
    }

    fun configFor(ctx: Context, id: Int): WidgetConfig =
        if (Store.hasConfig(ctx, id)) Store.config(ctx, id) else Store.lastUsed(ctx)

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val cfg = configFor(ctx, id)
        val (wDp, hDp) = sizeDp(cfg, mgr.getAppWidgetOptions(id))
        val views = build(ctx, id, cfg, Store.offset(ctx, id), wDp, hDp, interactive = true)
        runCatching { mgr.updateAppWidget(id, views) }
    }

    private fun sizeDp(cfg: WidgetConfig, o: Bundle): Pair<Float, Float> {
        val minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val (w, h) = when (cfg[S.SIZE_CALC]) {
            "landscape" -> maxW to minH
            "largest" -> maxW to maxH
            "smallest" -> minW to minH
            else -> minW to maxH
        }
        return (if (w > 0) w.toFloat() else 320f) to (if (h > 0) h.toFloat() else 360f)
    }

    /**
     * Builds the widget's views. Also used for the live preview in the settings screen
     * (with interactive = false so taps there do nothing).
     */
    fun build(
        ctx: Context, id: Int, cfg: WidgetConfig, offset: Int,
        widthDp: Float, heightDp: Float, interactive: Boolean,
    ): RemoteViews {
        val res = ctx.resources
        val density = res.displayMetrics.density
        val zone = CalendarRepo.zoneFor(cfg)
        val now = LocalDateTime.now(zone)
        val today = now.toLocalDate()
        val range = GridModel.range(cfg, today, offset)
        val pal = Palette.resolve(ctx, cfg)
        val permitted = CalendarRepo.hasPermission(ctx)
        val events = when {
            permitted -> CalendarRepo.events(ctx, cfg, range.start, range.end)
            !interactive -> CalendarRepo.sampleEvents(today)
            else -> emptyList()
        }
        val unit = if (cfg[S.SYSTEM_FONT_SCALE]) TypedValue.COMPLEX_UNIT_SP else TypedValue.COMPLEX_UNIT_DIP

        val v = RemoteViews(ctx.packageName, R.layout.widget_grid)

        // Background & header
        v.setInt(R.id.bg, "setColorFilter", pal.bg or 0xFF000000.toInt())
        v.setInt(R.id.bg, "setImageAlpha", (Color.alpha(pal.bg) * pal.alpha).toInt())
        v.setInt(R.id.header_bg, "setColorFilter", pal.header or 0xFF000000.toInt())
        v.setInt(R.id.header_bg, "setImageAlpha", (Color.alpha(pal.header) * pal.alpha).toInt())
        v.setTextViewText(R.id.title, range.title)
        v.setTextColor(R.id.title, pal.headerText)
        val long = range.title.length > 15
        v.setTextViewTextSize(R.id.title, unit, if (cfg[S.BIG_HEADER]) (if (long) 17f else 20f) else (if (long) 14f else 15f))
        for (icon in listOf(R.id.btn_settings, R.id.btn_today, R.id.btn_prev, R.id.btn_next)) {
            v.setInt(icon, "setColorFilter", pal.headerText)
            val p = if (cfg[S.BIG_HEADER]) 8 else 11
            v.setViewPadding(icon, dpi(p, density), dpi(p, density), dpi(p, density), dpi(p, density))
        }
        v.setViewVisibility(R.id.btn_settings, if (cfg[S.SHOW_SETTINGS_ICON]) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.btn_today, if (cfg[S.SHOW_TODAY_ICON]) View.VISIBLE else View.GONE)

        // Weekday labels
        val fd = GridModel.firstDay(cfg)
        val labelIds = intArrayOf(R.id.lbl0, R.id.lbl1, R.id.lbl2, R.id.lbl3, R.id.lbl4, R.id.lbl5, R.id.lbl6)
        val gravity = when (cfg[S.DAY_LABEL_ALIGN]) {
            "left" -> Gravity.START or Gravity.CENTER_VERTICAL
            "right" -> Gravity.END or Gravity.CENTER_VERTICAL
            else -> Gravity.CENTER
        }
        val todayVisible = today >= range.start && today <= range.end
        for (pos in 0..6) {
            val dow = fd.plus(pos.toLong())
            val lid = labelIds[pos]
            v.setViewVisibility(lid, if (dow in range.columns) View.VISIBLE else View.GONE)
            v.setTextViewText(lid, GridModel.dowLabel(cfg, dow))
            v.setTextColor(lid, if (cfg[S.HL_TODAY_DOW] && todayVisible && dow == today.dayOfWeek) pal.today else pal.dayLabel)
            v.setInt(lid, "setGravity", gravity)
            v.setTextViewTextSize(lid, unit, (cfg[S.DATE_FONT] - 1).coerceIn(8, 18).toFloat())
        }
        v.setInt(R.id.labels, "setBackgroundColor", withAlpha(pal.dayLabelBg, pal.alpha))
        v.setViewVisibility(R.id.lbl_wn, if (cfg[S.SHOW_WEEK_NUMBER]) View.VISIBLE else View.GONE)
        v.setInt(R.id.lbl_wn, "setBackgroundColor", withAlpha(pal.weekNumBg, pal.alpha))

        // Grid bitmap, sized to the area below header + labels; capped to stay under RemoteViews memory limits.
        var gw = widthDp * density
        var gh = (heightDp - HEADER_DP - LABELS_DP).coerceAtLeast(60f) * density
        val maxPx = 2_400_000f
        if (gw * gh > maxPx) { val s = sqrt(maxPx / (gw * gh)); gw *= s; gh *= s }
        val renderer = GridRenderer(cfg, pal, range, events, now, density * (gw / (widthDp * density)), res.configuration.fontScale)
        v.setImageViewBitmap(R.id.grid, renderer.render(gw.toInt(), gh.toInt()))

        v.setViewVisibility(R.id.message, if (!permitted && interactive) View.VISIBLE else View.GONE)
        v.setTextColor(R.id.message, pal.fg)

        // Tap targets: one per visible day.
        val rowIds = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5)
        val wnIds = intArrayOf(R.id.wn0, R.id.wn1, R.id.wn2, R.id.wn3, R.id.wn4, R.id.wn5)
        val cellIds = arrayOf(
            intArrayOf(R.id.c00, R.id.c01, R.id.c02, R.id.c03, R.id.c04, R.id.c05, R.id.c06),
            intArrayOf(R.id.c10, R.id.c11, R.id.c12, R.id.c13, R.id.c14, R.id.c15, R.id.c16),
            intArrayOf(R.id.c20, R.id.c21, R.id.c22, R.id.c23, R.id.c24, R.id.c25, R.id.c26),
            intArrayOf(R.id.c30, R.id.c31, R.id.c32, R.id.c33, R.id.c34, R.id.c35, R.id.c36),
            intArrayOf(R.id.c40, R.id.c41, R.id.c42, R.id.c43, R.id.c44, R.id.c45, R.id.c46),
            intArrayOf(R.id.c50, R.id.c51, R.id.c52, R.id.c53, R.id.c54, R.id.c55, R.id.c56),
        )
        val busy = HashSet<java.time.LocalDate>()
        events.filter { it.display }.forEach { e ->
            var d = e.startDate
            while (d <= e.endDate) { busy += d; d = d.plusDays(1) }
        }
        for (r in 0..5) {
            v.setViewVisibility(rowIds[r], if (r < range.weeks) View.VISIBLE else View.GONE)
            v.setViewVisibility(wnIds[r], if (cfg[S.SHOW_WEEK_NUMBER]) View.VISIBLE else View.GONE)
            for (pos in 0..6) {
                val dow = fd.plus(pos.toLong())
                val col = range.columns.indexOf(dow)
                val cell = cellIds[r][pos]
                v.setViewVisibility(cell, if (col >= 0) View.VISIBLE else View.GONE)
                if (!interactive || col < 0 || r >= range.weeks || !permitted) continue
                val date = range.date(r, col)
                val mode = if (date in busy) cfg[S.TAP_EVENTS] else cfg[S.TAP_EMPTY]
                if (mode != "none") v.setOnClickPendingIntent(cell, dayIntent(ctx, id, date.toString(), mode))
            }
        }

        if (interactive) {
            if (permitted) {
                v.setOnClickPendingIntent(R.id.title, dayIntent(ctx, id, range.start.toString(), "open"))
            } else {
                val pi = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                v.setOnClickPendingIntent(R.id.message, pi)
                v.setOnClickPendingIntent(R.id.title, pi)
            }
            v.setOnClickPendingIntent(R.id.btn_settings, PendingIntent.getActivity(
                ctx, id, Intent(ctx, ConfigActivity::class.java)
                    .setData(Uri.parse("gridcal://config/$id"))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            v.setOnClickPendingIntent(R.id.btn_today, navIntent(ctx, id, 0))
            v.setOnClickPendingIntent(R.id.btn_prev, navIntent(ctx, id, -1))
            v.setOnClickPendingIntent(R.id.btn_next, navIntent(ctx, id, 1))
        }
        return v
    }

    private fun dpi(v: Int, density: Float) = (v * density).toInt()

    private fun dayIntent(ctx: Context, id: Int, date: String, mode: String): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, DayActivity::class.java)
                .setData(Uri.parse("gridcal://day/$id/$date/$mode"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_DATE, date)
                .putExtra(EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun navIntent(ctx: Context, id: Int, delta: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, GridWidgetProvider::class.java)
                .setAction(ACTION_NAV)
                .setData(Uri.parse("gridcal://nav/$id/$delta"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_DELTA, delta),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    // ---- scheduling

    fun midnightIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, GridWidgetProvider::class.java).setAction(ACTION_MIDNIGHT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun scheduleAll(ctx: Context) {
        if (ids(ctx).isEmpty()) return
        val at = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 2_000
        ctx.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(AlarmManager.RTC, at, midnightIntent(ctx))
        scheduleCalendarJob(ctx)
    }

    fun scheduleCalendarJob(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, CalendarChangeJob::class.java))
            .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
            .setTriggerContentUpdateDelay(1_500)
            .setTriggerContentMaxDelay(10_000)
            .build()
        runCatching { js.schedule(job) }
    }
}
