# GYAN - Student Life OS (Android)

A local-first all-in-one student app: timetable, attendance, tasks/exams with
reminders, money tracker, subscriptions, warranties, scholarships, and a file
inbox with subject/category organization.

## Tech
- Kotlin + Jetpack Compose (Material 3)
- Room database for personal data (local-first)
- AlarmManager notifications (reminders re-scheduled after reboot)
- Dark-first Material 3 UI with no third-party advertising SDKs
- minSdk 31 (Android 12+), compile/target SDK 36
- Store variants: Play `com.gyan.app`; Samsung `com.gyan.app.samsung`

## Setup
1. Extract this zip anywhere.
2. Open Android Studio -> Open -> select the `Gyan` folder.
3. Let Gradle sync (first sync downloads dependencies, needs internet).
4. Run on a device/emulator with Android 12 or higher.
5. For signed GitHub releases and in-app update checks, follow [GITHUB_RELEASES.md](GITHUB_RELEASES.md).

> If Gradle sync complains about a missing wrapper jar: Android Studio will
> offer to use its bundled Gradle - accept it, or run
> `gradle wrapper` once with a local Gradle 8.9+ installation.

## Features
- **Today screen**: today's classes, upcoming deadlines, attendance status,
  today's spending, renewals due soon.
- **Study**: weekly timetable with editable repeating/one-off classes and labs,
  class-topic notes, attendance guidance, and editable Tasks/Exams with reminders.
- **Money**: income/expense log, monthly budget per category.
- **Track**: subscriptions, product warranties, scholarships.
- **Files**: Share any PDF/doc from WhatsApp/Drive/downloads into the GYAN
  inbox, then organize into Subject -> Category folders. Opens with any viewer.
- **Contact us**: GYAN-branded contact cards appear at the bottom of key screens
  and in the attendance list. Add the Google Forms responder URL in
  `app/src/main/java/com/gyan/app/ui/ContactUsCard.kt`.
- **Backup**: export/import all data as a `.het` file. Choose timetable-only
  import to share a schedule while keeping personal attendance, files, tasks,
  and money.
- **Notifications**: attendance-aware class reminders 10 minutes before class,
  clearer task/exam nudges, and skip-impact estimates when class counts are known.
- **Attendance setup**: enter a teacher-reported percentage and, optionally,
  how many classes had already happened. Without the count, GYAN labels the
  percentage as reported and does not invent a number of safe absences.
- Notifications: grant the permission when asked (Android 13+).

## No Google Sheets
All timetable, attendance, task, finance and file-organizer records stay in the
on-device Room database (`/data/data/<application-id>/databases/gyan.db`). The
update checker reads GitHub's public Releases Atom feed; checking, downloading,
and installing are user controlled. It needs no API key or GYAN server.
