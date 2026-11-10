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
