# Home progress handoff

## Checkpoint

Development base `77fd17a2dbbe48fa4f7538f5adebd7e2081afcd4` contains the then-current authored candidate for compile compatibility. This branch does not accept that candidate or fix its independent authored-flow issues. Integrate these scoped commits against the accepted main and campus work.

| Commit | Scope |
| --- | --- |
| `1a5f136daab0258226ada62538666a2c6be59cd2` | Reminder port, Android adapter/receiver, permissions declaration and policy tests |
| `e8328dc0e3ec8677b69edd0c93d087d0bbe8a190` | Campus Home consumer, owner-scoped reader, test lease, focused verification and module documentation |

The implementation PM owns Home and reviewed the Terra specialist's reminder adapter. [Module API and caller contract](../../modules/home-progress/README.md).

## Integration boundaries

- Campus Home reads only current, registered NORMAL gameplay and a separate locally confirmed server summary. It refreshes on return and guards account publication and navigation. The existing map, directory, guide, and device-local favorites stay available.
- The authored-flow owner supplies the post-commit intro/Skip and permission callback calls, stamp invalidation, and guarded reminder tap entry. The campus consumer must bind the validated ticket scope to its freshly captured authored host scope. The adapter checkpoint alone does not connect these paths.
- The production registry is closed. No authored dataset is activated, no coordinates are introduced, and no DEMO or legacy data is copied into account gameplay.
- Reminders can be dropped when the app process stops or the device reboots. The waiting card explains the limit. Durable quiz eligibility and re-entry remain available through Home.

## Verification

- `:app:assembleDebug` and `:app:compileDebugAndroidTestJavaWithJavac` passed.
- `:app:testDebugUnitTest` passed 14 focused tests across `AccountHomeControllerTest`, `AccountHomeReaderTest`, and `ReminderTicketsTest`. The final combined build executed 17 tasks and reused 35.
- `AccountHomeRuntimeTest` compiles against the actual Activity and the debug-only fixture lease. Device execution, Android alarm/notification delivery, SQLite runtime and live account/server behavior remain for combined verification.

## Facility bridge continuation

- Source checkpoint `09c4aaac04ba073c9f0c3e72b5138b3c1bf7b147` on base `fab2016cdd0f1e0eeea5aa2f6293a7c96e210b1a`. The implementation PM added real-map selection and tests. The Terra specialist added the directory detail/picker route. This is the transfer point after Home v3 if the other Home commits are already integrated.
- Existing directory details update the same local favorite IDs and open the real map with the facility selected. Map lookup can search, filter and open details through the same directory. Selection returns to the current map, cancellation keeps its context, and recreation reloads the ID. The browse map resolves catalog identity before display and explains unavailable coordinates. No facility pins, inferred authored destinations, mission data, map touch regions or attribution logic were changed.
- The first checkpoint build caught a Java forward reference in the result launcher initializer. Moving the browse state before the initializer fixed it. Final `:app:assembleDebug`, `:app:compileDebugAndroidTestJavaWithJavac`, and `:app:testDebugUnitTest --tests 'com.example.firststep.facility.*' --tests 'com.example.firststep.game.composition.home.account.AccountHomeReaderTest'` passed. The build executed 8 tasks and reused 44. Eleven focused JVM tests passed, with no failures, errors or skips.
- Three `FacilityMapBridgeTest` cases compiled for directory-to-map, map selection/cancel/recreation, and unknown ID. Device execution, large-text layout and physical map use remain for combined validation. Survey and any official facility-to-authored-target mapping remain activation inputs.
