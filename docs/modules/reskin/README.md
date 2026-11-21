# Current-screen visual integration

The reference is teammate branch commit `0c22f2e4548439919bca8a8ae2a4ac728f814db9`. The reskin uses the original `home.png` bytes as `drawable-nodpi/game_reskin_artwork.png`, Git blob `4bf6545d58356073c1f7d904c58d005b12ada0e9`. The same decorative campus desk illustration appears on sign-in and Campus Home. It is separated from interactive content and excluded from accessibility announcements. Landscape uses shorter crops. The art is decorative, with no position, route or survey meaning.

## Visual contract

- `res/values/game_tokens.xml` and `game_reskin_tokens.xml` hold shared warm ivory, charcoal, rose and sage roles. `GameActionButton`, `GameSecondaryButton` and the compact `GameMapButton` are the XML controls. Dynamic buttons use `GameVisuals.action(Button, boolean primary)` without replacing their view, state or callbacks.
- Signed-out launch uses `game_identity_welcome.xml`. The existing artwork fills the window behind a dark scrim, with the title and stacked account and guest buttons at the bottom. `IdentityActivity` opens its existing branded Google panel from the account button and returns to welcome on Back when signed out. Returning sessions enter the profile or account Home flow. The guest button opens `GuideActivity` for bundled guidance and transient practice. Debug builds also offer the guide's existing local DEMO, with its separate record scope.
- The Google sign-in panel, profile form and confirmed account Home use paper cards and responsive text. Campus Home groups favorites and authored progress separately while preserving map, directory, guide and quiz actions.
- Guide, narration, DEMO discovery/quiz and authored arrival/introduction/quiz use the same readable cards, state colors and action hierarchy. The live discovery preview and camera overlay layout are kept as-is.
- Browse map uses compact controls and retains its existing canvas, attribution and touch regions. Directory search, filters, result and detail styling keep the existing catalog identity, favorites and two-way map context.
- Keep labels, source citations, disclosures, ownership gates and the normal/DEMO distinction when modifying these visuals. Legacy launchers and shared activity navigation are outside this change.

The original, superseded palette check measured 13.28:1 ink on ivory, 11.67:1 ink on amber, 6.79:1 muted on ivory and 8.58:1 white on plum. These ratios apply to flat color pairs. Runtime typography, composition and touch behavior still require the combined device check.

## Verification boundary

APK assembly and Android test source compilation passed. Existing layout IDs, text attributes, visibility, input attributes and live-region attributes were retained in all 11 touched layouts. No test source, account, game, network, map geometry or device changes belong to this branch. Screenshots, portrait and large-font landscape, contrast on-device and interaction evidence remain with management validation.

The welcome follow-up adapts identity and visual instrumentation for the new presentation step. The unconfigured build, lint and 233 JVM tests passed. The user-preview APK also built with the existing local Firebase configuration. Emulator outcomes and installation belong to the management handoff.

## Design v1

The welcome screen uses a larger left-aligned title and retains the campus desk artwork and both login routes. Campus Home and account Home use taller portrait artwork. Shared ivory surfaces, rose primary controls, sage secondary controls and rounded paper cards carry through directory, map, guide and game screens. Map headings and quiz grouping were adjusted in XML. View IDs, account gates, gameplay rules and storage are unchanged.

Verified with the bundled Android Studio JDK using testDebugUnitTest, assembleDebug and lintDebug. A separate Pixel 4 API 35 emulator rendered the welcome, guest guide, demo home and discovery screens. Google sign-in was not exercised in the unconfigured build.

## Design v4 review repairs

The software booklet retains its Previous control through the last page. Narration shows the preview disclosure and per-sentence provenance with full source details while keeping the current artwork and navigation. The fallback smoke path now checks and clicks the no-camera action after simulated arrival. The guide and narration tests accommodate their fixed footer controls with visibility checks and Espresso clicks. AGP is restored to 9.4.1. The 233 JVM tests, APK assembly, lint and instrumentation compilation passed. Management owns portrait and landscape runtime verification. [Repair evidence](../../harness/handoffs/Design.md)
