# Local Android progress SDK verification

This runner uses the existing candidate rules with a separate Auth and Firestore emulator pair. It fixes the project to `demo-firststep-progress-sdk`. Host listeners bind to loopback. Android uses the emulator's host loopback alias `10.0.2.2`. The existing profile and progress-rules emulator configurations remain separate.

| Service | Host port |
| --- | --- |
| Auth | 19383 |
| Firestore | 18383 |
| Firestore websocket | 19283 |
| Emulator hub | 14683 |
| Emulator logging | 14783 |

1. From the repository root, install the pinned tools with `npm.cmd ci --prefix firebase --ignore-scripts --offline --no-audit --no-fund` if the npm cache is prepared. The launcher requires the pinned Firestore emulator JAR in the local Firebase cache. Use Android Studio JBR and run `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest`.
2. Start the assigned disposable Android Studio AVD with a name matching `FirstStep_ProgressSdk_<suffix>`. Confirm the AVD name and serial before installing the two APKs from `app/build/outputs/apk/debug/` and `app/build/outputs/apk/androidTest/debug/`. Use the same checkout for both APKs. Leave Android user 0 active and the installed app idle.
3. In PowerShell 7, run `.\firebase\progress-sync\verification\run-local.ps1 -Serial <disposable-emulator-serial>`. The launcher checks the AVD and ports, copies the candidate rules into an ignored run directory, and starts its local Auth and Firestore pair. It uses an allowlisted child environment and isolated CLI configuration directory. The child process seeds two synthetic Google identities, their private profiles and one trusted registration, then executes the selected Android test. Fixture creation uses emulator administrative access. Android SDK requests use the emulator-issued Google identities and the candidate rules.
4. Check the runner exit status and `firebase/progress-sync/verification/.local/<run>/emulator.log`. The runner requires the selected test's start and pass reports, a successful runner exit and no skip. The Firebase CLI shuts down the emulator processes it started, discarding the in-memory fixture data. The Android test signs out its named SDK clients and deletes their app instances. On an ADB timeout, the Android test may still be running. Stop the disposable AVD before retrying. Ignored evidence files remain available for inspection.

The test reads an empty server record, writes intro and pass milestones, preserves the first pass during a later merge and reads the result through a separate memory-cache SDK instance. It checks denial of pass removal and a cross-owner read, then checks the local guard after revocation. The test is opt-in through the runner argument. A broader instrumentation run without that argument reports this case as skipped. The login fixture uses Firebase's documented mock Google credential flow. It exercises emulator provider claims and Android transport. Live Google sign-in and actual cross-device sync require separate verification. [Firebase Auth emulator guide](https://firebase.google.com/docs/emulator-suite/connect_auth#non-interactive-testing)
