# Firebase identity and first profile

H07 owns Google sign-in, the required first profile, account Home and account actions. The implementation is on main. Live acceptance and comprehensive combined verification remain pending.

## Interfaces and callers

- [IdentityViewModel](../../../app/src/main/java/com/example/firststep/game/ui/identity/IdentityViewModel.java) builds `SessionController(runtime.auth(), runtime.profiles())`. [IdentityActivity](../../../app/src/main/java/com/example/firststep/game/ui/identity/IdentityActivity.java) binds it on resume, hides personal views on pause, and dispatches `signIn(false)`, `signIn(true)` for reselection, `edit(name, studentNumber, department)`, `save()`, `retry()` and `logout()`. Read the current `SessionState`; only `COMPLETE` opens authenticated Home.
- [AuthPort](../../../app/src/main/java/com/example/firststep/game/auth/AuthPort.java) supplies `currentUid()`, `observe(observer)`, `signIn(reselect, callback)` and `signOut(callback)`. Calls and callbacks are serialized. [ProfilePort](../../../app/src/main/java/com/example/firststep/game/auth/ProfilePort.java) exposes `load(uid, callback)` with source and failure, and `save(uid, profile, callback)`. A save acknowledgement is followed by server confirmation. Cached or pending data cannot complete onboarding.
- [FirebaseIdentityRuntime](../../../app/src/main/java/com/example/firststep/game/composition/identity/FirebaseIdentityRuntime.java) selects the configured named Firebase app or the unconfigured state. [GoogleCredentialSource](../../../app/src/main/java/com/example/firststep/game/composition/identity/GoogleCredentialSource.java) uses the explicit Credential Manager button flow. Provider clear results return through the main handler with a bounded deadline. [FirebaseAuthPort](../../../app/src/main/java/com/example/firststep/game/composition/identity/FirebaseAuthPort.java) waits for any in-flight Firebase sign-in before completing logout.

## Data and module seam

[FirestoreProfilePort](../../../app/src/main/java/com/example/firststep/game/composition/identity/FirestoreProfilePort.java) uses private `users/{uid}` records with [owner rules](../../../firebase/firestore.rules). Name, student number and department are trimmed bounded strings. Student number retains leading zeros. Current UID and request guards discard stale profile callbacks. Real configuration stays in ignored `app/google-services.json`. Missing configuration exposes the unavailable state.

The app-entry binding opens the department guide with an explicit intent after rechecking `COMPLETE`. It sends no UID, profile or storage identifiers. The guide selects its own packaged content, and the software DEMO uses its own local database. Progress sync is a separate implementation slice.

Configured and clean-unconfigured checks, local profile/rules tests, returning server-confirmed Home and the signed-in guide route have evidence. The user's first save was reported and persisted profile readback was observed. The API 37 QA regression verified account-free Android provider clear on the main Looper. Repaired authenticated logout, live incomplete-profile recovery and combined validation remain open. [H07 handoff](../../harness/handoffs/H07.md) · [App-entry handoff](../../harness/handoffs/APP_ENTRY.md)
