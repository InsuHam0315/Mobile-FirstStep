# Account Home to department entry — 2026-11-10

Confirmed direct user clarification: profile saving succeeded and Home appeared; the user cannot enter exploration. This is missing navigation, not a reproduced Firebase save failure.

## Ownership and baseline

Original H07 chat local01a1b03e-8027-7ad3-b54e-53c538832975 owns this bounded repair in C:/Users/InsuHam/FirstStep-worktrees/app-entry, branch app-entry, baseline228a088bdec3c8b1c34f8240c8b7f9a7c5868275. Management assembled mainca2ca2e, H10C55ba443 and firebasea686a26 in this isolated candidate. Nothing is yet merged into main or fully accepted by this assembly. Manifest resolution retained both identity initialization/launcher and guide/narration/TTS entries; task conflict retained current management records.

## Contract

Add an understandable Home action opening existing GuideActivity with an explicit Intent and no identity/profile/progress/storage extras. Recheck current COMPLETE session at click; incomplete or stale account state cannot launch through this action. Avoid duplicate launches and preserve Back to authenticated Home. The guide exposes the existing clearly labeled debug DEMO episode. It is local demonstration progress, not cloud/field progress; never auto-import or promote it.

Owner writes only IdentityActivity.java, game_identity_home.xml, game_identity_strings.xml, focused tests and handoffs/APP_ENTRY.md. Any additional production path needs coordination. Concurrent campus-content owner builds richer identity-independent app navigation, and must not edit these identity paths. Keep existing launcher entries during this bounded repair.

## Verification and delivery

Short configured build and affected state/navigation checks precede a data-preserving update on retained QA5572. Owner may observe Home -> guide -> clearly labeled demo -> Back, preserving account data. No automated account choice, credentials, profile save, logout, reset, or use of5564/5580. Retain previous APK, exact source/hash and installed readback. Broad whole-app validation follows functional/design integration under IMPLEMENTATION_FIRST.md. No Firebase rule deployment or service changes.

Korean commits use [app-entry vN]. Management owns publication/main integration and a single branch journal. Original firebase and content worktrees and raw evidence remain preserved.
