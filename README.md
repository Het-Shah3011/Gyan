# GYAN - Student Life OS (Android)

A local-first all-in-one student app: timetable, attendance, tasks/exams with
reminders, money tracker, subscriptions, warranties, scholarships, and a file
inbox with subject/category organization.

## Tech
- Kotlin + Jetpack Compose (Material 3)
- Room database (fully local, no server, no Google Sheets)
- AlarmManager notifications (reminders re-scheduled after reboot)
- minSdk 31 (Android 12+), targetSdk 34

## Setup
1. Extract this zip anywhere.
2. Open Android Studio -> Open -> select the `Gyan` folder.
3. Let Gradle sync (first sync downloads dependencies, needs internet).
4. Run on a device/emulator with Android 12 or higher.

> If Gradle sync complains about a missing wrapper jar: Android Studio will
> offer to use its bundled Gradle - accept it, or run
> `gradle wrapper` once with a local Gradle 8.9+ installation.

## Features
- **Today screen**: today's classes, upcoming deadlines, attendance status,
  today's spending, renewals due soon.
- **Study**: Timetable, Attendance ("how many lectures can I bunk" calculator),
  Tasks and Exams with reminder notifications.
- **Money**: income/expense log, monthly budget per category.
- **Track**: subscriptions, product warranties, scholarships.
- **Files**: Share any PDF/doc from WhatsApp/Drive/downloads into the GYAN
  inbox, then organize into Subject -> Category folders. Opens with any viewer.
- **Backup**: one-tap export/import of ALL data as a single JSON file
  (save it to Drive/USB - this replaces cloud sync for now).
- Notifications: grant the permission when asked (Android 13+).

## No Google Sheets
Campus boards (buy/sell, rides, hackathon teams) are intentionally NOT in this
build. All your data stays on-device in the Room database
(`/data/data/com.gyan.app/databases/gyan.db`), with JSON export as backup.
