# H03 discovery slice

Read AGENTS.md, tasks.json, UI_BOUNDARIES.md, CORE_GAME_SCOPE.md and PRODUCT_DECISIONS.md. Start only after H02's reviewed integration is recorded on main. Branch: discovery; versioned Korean commits [discovery v1], v2, etc. Implementation owns this slice; management owns acceptance/integration/Notion.

## Source and product boundary

User-supplied game design PDF, 2026-11-06, pages 2-4: camera preview with a 2D character overlay, direction alignment followed by a building-selected summon action. Digital-information building uses shake; the two library episodes use cover/light. Shake initial values: acceleration magnitude minus 9.8 >=12m/s2, five distinct pulses within three seconds. Cover: <=20% of entry baseline for1.5seconds; no light sensor uses a labeled long-press fallback. Success has vibration and a character approach/scale animation. These are configurable initial values, not field-calibrated results.

First real end-to-end target is the software department in the digital-information building. Do not invent office floors, coordinates, fairy names or campus facts. Use an explicitly labeled placeholder illustration and synthetic device-input harness for unconfirmed assets/data; changing artwork later must not change the rules. The module may validate both supported summon methods without pretending other departments' content is complete.

Deliver a real debug-accessible discovery surface and adapters; preserve existing launchers/legacy app behavior. This is a camera overlay, not ARCore tracking, plane detection or anchoring. No new authentication, profile, map/GPS arrival feature, TTS/quiz UI, cloud or optional photo capture. Arrival can be an explicit simulated input for this isolated slice and must not be labeled actual GPS arrival. Discovery success hands off a typed result for a later introduction; do not mark introduction complete, start H02's quiz timer or award stamps just for summoning.

## Technical contract and acceptance

- Keep rules in pure state/controller code with injected time and sensor ports. Views bind state/actions, host preview and render animations. Do not implement sensor thresholds in Views.
- Use CameraX preview with lifecycle ownership, explicit permission denial, absence, initialization failure and recoverable exit/retry/fallback. Permission granted does not prove a usable camera. Camera failure must not leave only a black preview and an unusable button.
- Direction alignment must handle wraparound, display rotation, unreliable/unavailable input and interrupted sessions. Choose documented configurable prototype tolerance/hysteresis and disclose that field calibration remains pending. Missing heading must have an explicit alternate path.
- Count distinct shake excursions with re-arm/debounce semantics; sustained high samples must not count as five separate shakes. Use monotonic session time for sensor windows. Cover baseline must be usable; zero/too-dark/missing/stale light samples must lead to recovery or fallback, not automatic success. Long press must cancel on release/cancel/pause and cannot leak progress across sessions.
- Pause/exit/recreation cancel camera/sensor sessions and invalidate stale callbacks. Resume with fresh observations; restored UI must not claim old sensor/arrival success. Completion and haptics fire once per successful session. Rapid repeated actions must not duplicate navigation/results.
- Keep normal sensor results and simulated/fallback paths distinct in state and visible Korean labels. An unsupported device must still have understandable controls and a way out.
- Use existing game XML tokens/string resources and replaceable artwork. Add a bounded debug entry point and only necessary composition/manifest/resource wiring. No wholesale legacy refactor.
- Add meaningful domain and device-lifecycle tests plus real APK screenshots for normal/simulated/fallback/denied states, large text and recreation. Emulator sensor input is simulated; actual preview on a virtual scene is not campus footage. Validate available paths and disclose unavailable hardware/field checks separately. Do not require a physical Android device to implement the recoverable flows.

Authorized supporting scope beyond the task's device/ui packages: new discovery-specific presentation/domain ports, discovery composition, game string/token resources, debug launcher manifest, scoped instrumentation tests and evidence scripts. Preserve H01 preview and H02 contracts. Escalate any change to durable game rules/storage or unrelated app/build behavior before editing it.

Run scripts/verify.ps1 using Android Studio JDK, focused instrumentation/device checks where appropriate, and independent review of the final patch. Retain evidence under .local/harness/H03 with commands, exact commit/hash, input mode, observed result and limits. Write handoffs/H03.md and active-task factual status; no main merge/push, Notion writes or H04 dispatch by implementation.
