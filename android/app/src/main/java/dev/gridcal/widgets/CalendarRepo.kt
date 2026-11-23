package dev.gridcal.widgets

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

data class CalInfo(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
    val visible: Boolean,
    val isHoliday: Boolean,
)

data class Ev(
    val eventId: Long,
    val title: String,
    val location: String,
    val allDay: Boolean,
    /** First day the event appears on. */
    val startDate: LocalDate,
    /** Last day the event appears on (inclusive). */
    val endDate: LocalDate,
    /** Start time for timed events, in the widget's time zone. */
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val beginMs: Long,
    val endMs: Long,
    val color: Int,
    val calendarId: Long,
    val holiday: Boolean,
    /** False for holiday-calendar events read only to color their dates. */
    val display: Boolean = true,
) {
    val multiDay get() = endDate > startDate
}

object CalendarRepo {

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun zoneFor(cfg: WidgetConfig): ZoneId =
        cfg[S.TIME_ZONE]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

    fun calendars(ctx: Context): List<CalInfo> {
        if (!hasPermission(ctx)) return emptyList()
        val out = ArrayList<CalInfo>()
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
        )
        runCatching {
            ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: "Calendar"
                    val owner = c.getString(5) ?: ""
                    out += CalInfo(
                        id = c.getLong(0),
                        name = name,
                        account = c.getString(2) ?: "",
                        color = c.getInt(3),
                        visible = c.getInt(4) == 1,
                        isHoliday = owner.contains("#holiday@") || name.contains("holiday", ignoreCase = true),
                    )
                }
            }
        }
        return out.sortedWith(compareBy({ it.account }, { it.name.lowercase() }))
    }

    /** Events overlapping [from, to] (inclusive dates), already filtered by the widget's settings. */
    fun events(ctx: Context, cfg: WidgetConfig, from: LocalDate, to: LocalDate): List<Ev> {
        if (!hasPermission(ctx)) return emptyList()
        val zone = zoneFor(cfg)
        val cals = calendars(ctx).associateBy { it.id }
        val chosen: Set<Long> = cfg[S.CALENDARS]?.toSet() ?: cals.values.filter { it.visible }.map { it.id }.toSet()

        // Widen by a day each side so all-day events (stored in UTC) are not cut off.
        val startMs = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, startMs)
            ContentUris.appendId(it, endMs)
        }.build()
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )
        val list = ArrayList<Ev>()
        runCatching {
            ctx.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val calId = c.getLong(7)
                    val cal = cals[calId]
                    val holiday = cal?.isHoliday == true
                    // Holiday calendars are always read so their dates can be colored, even if not displayed.
                    if (calId !in chosen && !holiday) continue
                    if (cfg[S.HIDE_DECLINED] && c.getInt(8) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                    val display = calId in chosen && !(holiday && cfg[S.FILTER_HOLIDAY_CALS])
                    val allDay = c.getInt(3) == 1
                    val b = c.getLong(4)
                    val e = maxOf(c.getLong(5), b)
                    val ev = if (allDay) {
                        val sd = Instant.ofEpochMilli(b).atZone(ZoneOffset.UTC).toLocalDate()
                        val ed = Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
                        Ev(c.getLong(0), c.getString(1) ?: "(No title)", c.getString(2) ?: "", true,
                            sd, maxOf(sd, ed), null, null, b, e, c.getInt(6), calId, holiday, display)
                    } else {
                        val s = Instant.ofEpochMilli(b).atZone(zone).toLocalDateTime()
                        val en = Instant.ofEpochMilli(e).atZone(zone).toLocalDateTime()
                        // An event ending exactly at midnight does not appear on the next day.
                        val lastDay = if (en > s && en.toLocalTime() == java.time.LocalTime.MIDNIGHT) en.toLocalDate().minusDays(1) else en.toLocalDate()
                        Ev(c.getLong(0), c.getString(1) ?: "(No title)", c.getString(2) ?: "", false,
                            s.toLocalDate(), maxOf(s.toLocalDate(), lastDay), s, en, b, e, c.getInt(6), calId, holiday, display)
                    }
                    if (ev.endDate < from || ev.startDate > to) continue
                    if (cfg[S.BUSINESS_HOURS] && !ev.allDay && !inBusinessHours(ev, cfg)) continue
                    list += ev
                }
            }
        }
        var result: List<Ev> = list
        if (cfg[S.FILTER_DUPLICATES]) {
            result = result.distinctBy { Triple(it.title.trim().lowercase(), it.beginMs, it.endMs) }
        }
        return result
    }

    private fun inBusinessHours(ev: Ev, cfg: WidgetConfig): Boolean {
        val s = ev.start ?: return true
        val e = ev.end ?: return true
        val open = s.toLocalDate().atTime(cfg[S.BUSINESS_START].coerceIn(0, 23), 0)
        val close = s.toLocalDate().atTime(cfg[S.BUSINESS_END].coerceIn(1, 24).let { if (it == 24) 23 else it }, if (cfg[S.BUSINESS_END] >= 24) 59 else 0)
        return e > open && s < close
    }

    /** Sample events used for previews when calendar access hasn't been granted. */
    fun sampleEvents(today: LocalDate): List<Ev> {
        fun ev(id: Long, title: String, day: Long, hour: Int?, color: Long, len: Long = 0): Ev {
            val d = today.plusDays(day)
            val s = hour?.let { d.atTime(it, 0) }
            return Ev(id, title, "", hour == null, d, d.plusDays(len), s, s?.plusHours(1), 0, 0, color.toInt(), 0, false)
        }
        return listOf(
            ev(1, "Team standup", 0, 9, 0xFF7986CB),
            ev(2, "Lunch with Sam", 0, 12, 0xFFE67C73),
            ev(3, "Assignment due", 1, null, 0xFF3F51B5),
            ev(4, "Networking event", 2, 18, 0xFFE67C73),
            ev(5, "Conference", 4, null, 0xFF039BE5, 2),
            ev(6, "Physio", 4, 15, 0xFFE67C73),
            ev(7, "Club meeting", 4, 19, 0xFF8E24AA),
            ev(8, "House meeting", 6, 20, 0xFFE67C73),
            ev(9, "Reflection due", 7, null, 0xFF7986CB),
            ev(10, "Midterm", 9, 10, 0xFF8E24AA),
            ev(11, "Tutoring", 12, 16, 0xFFF4511E),
            ev(12, "Doctor", 16, 11, 0xFF0B8043),
            ev(13, "Presentation", 14, 13, 0xFFE67C73),
            ev(14, "Final exam", 22, 9, 0xFF7986CB),
            ev(15, "Web dev meeting", 18, 18, 0xFF8E24AA),
        )
    }
}
