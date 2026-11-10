# Combined Android validation

The instrumentation sources exercise the current Campus Home and authored episode Activities with test-only dependencies. They also exercise the two account progress SQLite stores in UUID-prefixed test databases.

| Android test class | Prepared coverage |
| --- | --- |
| `com.example.firststep.game.ui.authored.CombinedAuthoredJourneyTest` | Home entry, synthetic arrival and heading/SHAKE, intro Skip, away re-entry, 15-minute boundary, failed quiz and retry, first passing result, explicit send/readback, and owner switch during a delayed response |
| `com.example.firststep.game.sync.AuthoredSQLitePersistenceTest` | Committed pass and timestamp, transaction rollback, store close/reopen, recovery into the outbox, acknowledgement persistence and UID isolation |
| `com.example.firststep.game.ui.authored.AuthoredEpisodeRouteTest` | Retained episode routes, including a remote-only pass projection that leaves authored progress absent |
| `com.example.firststep.game.sync.FirestoreSyncBackendEmulatorTest` | Opt-in Android SDK read/merge with synthetic Google identity, second SDK readback and rules denial against the local candidate |

`AccountHomeTestRuntime.install(...)` and `AuthoredEpisodeActivity.installDebugRuntime(...)` bind the two screens to one synthetic owner, clock and in-memory authored/sync stores. The explicit sync calls use the real coordinator with fake transport. The journey starts at Campus Home. The test drives controls and injects camera readiness, arrival, heading and acceleration. These inputs simulate the flow and do not provide sensor or field evidence.

The targeted Android test sources compile and package with `gradlew.bat :app:compileDebugAndroidTestJavaWithJavac :app:assembleDebugAndroidTest`. Run the selected classes after design integration on a separately assigned disposable Android emulator. The journey and SQLite tests require no Firebase emulator or account. The Android SDK case uses the opt-in harness to start local Auth and Firestore and seed the demo project. [SDK runner instructions](../../../firebase/progress-sync/verification/README.md) · [Verification handoff](../../harness/handoffs/Validation.md)
