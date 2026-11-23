# Gridcal — calendar widget for Android

A home-screen widget that shows several weeks (or a whole month) of the events already on your phone's calendars — Google, Samsung, Outlook, anything synced to Android. Every part of the look is customizable from a settings screen with a live preview.

## Features

**Widget**
- 1–6 week view or month view, starting on the week containing today
- Colored event blocks; titles wrap when there's room and shrink to one line on busy days, with a `+N` count for events that don't fit
- Multi-day events drawn as bars across days
- Header with date range, settings, jump-to-today, previous/next
- Tap a day: list that day's events, add an event, or open your calendar app (configurable for empty vs. busy days)
- Updates automatically when your calendar changes and at midnight

**Settings** (General / Appearance / Advanced, with live preview)
- View, first day of week, day label and date alignment, day-of-week format
- Start time before/after title, 24-hour time, location, week numbers, hide Saturday/Sunday
- Hide declined events, filter duplicates, filter holiday calendars, choose which calendars to show
- Theme (system / light / dark / black), transparency, font sizes, word wrapping
- ~18 individual colors (background, header, labels, weekends, holidays, lines, today highlight…)
- Today highlight style, dim past events, fade adjacent months, rounded blocks, timed vs. all-day styles
- Time zone, alternate calendar (Chinese lunar, Hebrew, Islamic, Persian, Indian), business-hours filter
- Calendar priorities and event sort order

## Project layout

```
android/   Kotlin Android app (the widget)
web/       Earlier browser prototype of calendar widgets (HTML/CSS/JS, no build step)
```

Key Android files in `android/app/src/main/java/dev/gridcal/widgets/`:

| File | What it does |
|---|---|
| `Settings.kt` | Every widget setting (key + default) and per-widget storage |
| `CalendarRepo.kt` | Reads calendars and event instances from Android's calendar provider |
| `GridModel.kt` | Which dates are shown, header title, theme palette |
| `GridRenderer.kt` | Draws the week rows and event blocks into a bitmap |
| `GridWidgetProvider.kt` | Builds the widget views, tap targets, navigation, refresh scheduling |
| `ConfigActivity.kt` | Settings screen (Jetpack Compose) with live preview |
| `DayActivity.kt` | What happens when you tap a day |
| `MainActivity.kt` | Launcher screen: permission, add widget, open settings |

The weekday labels and header are real views; the grid itself is drawn as a bitmap so event blocks can wrap and span days, with an invisible grid of tap targets laid over it.

## Build

Requirements: JDK 17+ and the Android SDK (API 36).

```sh
cd android
./gradlew assembleDebug
```

The APK lands in `android/app/build/outputs/apk/debug/app-debug.apk`. Install with:

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

CI builds the debug APK on every push; download it from the workflow run's artifacts.

## Web prototype

```sh
python -m http.server 5173 --directory web
```

Then open http://localhost:5173.
