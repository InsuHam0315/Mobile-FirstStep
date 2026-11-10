# Campus Home account progress

Campus Home displays the local NORMAL record for the registered software-department target. It shows unstarted, waiting, ready, completed and load-failure states. When registration is closed, the card says the episode is unavailable and the existing authored entry opens its unavailable screen. The map, facility directory and guide remain available.

## Reading and re-entry

- `AccountHomeReader` checks the production registry before initializing account services or opening gameplay or sync stores. After eligibility, it captures the current confirmed account scope with `AccountProgressAccess.capture(dataset, target)`.
- The reader uses `AuthoredProgressRepository` for the local gameplay record and the local sync outbox's confirmed milestone for a separate server summary. Home does not fetch from the server or copy a confirmed milestone into local quiz state.
- `AccountHomeController` loads on a worker and discards obsolete lifecycle responses. It guards account publication and the authored CTA with the captured owner. On return it reloads, and while resumed it projects the countdown from the persisted intro timestamp and the domain wait duration. The authored host remains responsible for enforcing eligibility and resuming the correct stage.
- `FacilityRepository` supplies existing device-local favorite IDs and curated facility names. Home displays names that still exist in the catalog and directs facility browsing through the existing directory. It does not add map pins or synchronize favorites.

The production registry remains empty. The software guide and demo do not grant a NORMAL intro, quiz or stamp.

## Reminder caller contract

Create an `AuthoredReminder` with `AuthoredReminderAndroid.create(context, registry, owner)`. The authored host calls `schedule(scope, committedProgress)` after the local intro is durable, including a replay of that committed intro when permission is granted later. A stamped snapshot invalidates its pending ticket. Schedule outcomes are informational. Local save, waiting and quiz re-entry proceed regardless of notification permission or Android scheduling failure.

The nonexported receiver schedules an inexact device alarm with no exact-alarm permission. The reminder entry contains an opaque token without account extras. In the authored Activity, check `isReminderEntry(intent)` before opening the episode. For marked entries, use `requireCurrentEntry(intent)` and require the returned scope to match the current host project, UID, session generation, dataset, mode and target before reading progress. Reject invalid entries instead of treating them as ordinary authored entry. The authored-flow owner supplies this entry guard, permission interaction and post-commit scheduling call.

Tickets are process-local and bounded. If Android has stopped the app process before delivery, the receiver drops the alarm even if Android restarts the process to invoke it. Reboot and an inexact delivery window can also prevent timely notification. Campus Home remains the durable way to find the quiz once it becomes available.

## Synthetic verification seam

The debug/instrumentation process can install `AccountHomeTestRuntime.install(source, registered, clock)`, which returns an `AutoCloseable` lease. Close the Activity before releasing the lease. There is no Intent selector and release builds reject installation. An integration fixture can share its clock, progress source and owner guard with the authored Activity fixture by returning `AccountHomeController.Reading.progress(local, confirmed, ownerGuard, favorites, favoritesAvailable)`.

Focused JVM tests exercise countdown, return refresh, stale-owner and obsolete-screen fencing, fixture rejection, favorites filtering, separate server projection and reminder policy. The Android test source exercises the real Home renderer with synthetic data. Android delivery, production SQLite runtime, and live account/server behavior are part of the later combined verification.
