# Latest teammate design — deferred semantic integration

Read-only audit2026-11-10 by management verification PM. Current main1cfb237 versus fetched origin/codex/midpoint0c22f2e4548439919bca8a8ae2a4ac728f814db9, merge base9468d24. Not an implementation dispatch or merge approval for unrelated gameplay changes. Functional/account delivery wiring comes first.

The teammate branch primarily changes legacy activities. A blind merge would not style the new identity, Campus Home and software DEMO screens, and can replace their navigation with old account behavior. Final design integration must port the visuals into actual current Java/XML consumers.

| Changed area | Preserve current behavior / intended visual reuse |
| --- | --- |
| Manifest / WelcomeActivity | Preserve identity and retained legacy launcher declarations, named Firebase graph and server-confirmed first-profile routing. Reuse Welcome visual treatment without legacy AuthSession/AuthActivity substitution. |
| MainActivity / activity_main | Reuse palette, rounded cards and spacing. Preserve existing routes and facility/device-lab entry; legacy completion counters are not account progress. |
| RouteMapActivity / geographic map layout | Retain browseIntent, browse-only empty mission set, attribution, pan/follow and simulated-location disclosures. Do not enable unapproved five-target missions through a decorative 5 SPOTS label or building picker. |
| BuildingDiscoveryActivity / missions.json | Index-based legacy launches, hard-coded narration, accelerometer behavior and removal of candidate labels are functional/content changes, not automatic design adoption. Preserve sourced nonce-bound H10C and account/local data separation. Do not delete teammate source/history. |
| Assets / version catalog | home.png is referenced; home2.png is byte-identical and bg_home.png unused. Inspect visual asset before port, avoid duplicate assets. Preserve current Firebase and dependency configuration. |

Current target layouts: game_identity_activity.xml, game_identity_home.xml, game_campus_home_activity.xml, then guide/DEMO surfaces as applicable. Preserve IDs, click handlers, state gates, source text and local DEMO notices. Do not infer campus photographs are real/surveyed from artwork.

After functional wiring and visual port, combined scenarios include confirmed Home -> Campus Home -> guide -> DEMO, browse-only map, facilities, Back, portrait and large-font landscape. Prior source/build evidence does not cover these final rendered screens. The protected human devices remain unavailable for automatic validation unless explicitly handed back.

Management re-fetched on2026-11-10: origin/codex/midpoint remains0c22f2e. Visually inspected its exact home.png: warm dark illustrated desk/window, campus-themed photos/map/book, amber illumination. Treat it as decorative artwork, not a verified campus photograph, arrival map or official survey. Port the actual teammate treatment into current screens only after required functional callers are ready; retain readable contrast, Google branding, current account gates and distinct real/synthetic labels.
