# Local progress

H02 owns introduction completion, eligibility, quiz attempts, scoring, rewards and the five-destination ending rule. Hosts supply the selected content, repository, clock and attempt factory.

## Interfaces and callers

- [GameContent](../../../app/src/main/java/com/example/firststep/game/domain/GameContent.java) holds a version and one to five buildings. Each building has exactly three questions. Stable question and option IDs determine the answer, including after shuffle.
- [ProgressService](../../../app/src/main/java/com/example/firststep/game/domain/ProgressService.java) exposes `read(mode, id)`, `intro(mode, id, instantDemo)`, `eligible(progress)`, `start(mode, id)`, `submit(mode, id, attemptId, contentVersion, answers)` and `endingAvailable(mode)`. `answers` maps question ID to option ID. A replay retains the original introduction time. NORMAL eligibility starts after 15 minutes. DEMO may request instant eligibility after explicit introduction completion.
- `start` returns the stored active attempt or creates a shuffled snapshot. A complete submission with at least two correct answers passes and awards one stamp. A failed submission creates a fresh retry attempt. Repeating an identical submission returns the prior result. A conflicting repeat fails. Ending requires five distinct stamped buildings in the selected catalog and mode.

For example, [HomeHost](../../../app/src/main/java/com/example/firststep/game/composition/home/HomeHost.java) reads the selected target on its worker and calls `service.eligible(progress)` before delivering the home state. [IntroQuizController](../../../app/src/main/java/com/example/firststep/game/presentation/introquiz/IntroQuizController.java) supplies the active attempt ID and content version when submitting.

## Storage and evidence

[ProgressRepository](../../../app/src/main/java/com/example/firststep/game/domain/ProgressRepository.java) is blocking. `atomicUpdate(mode, buildingId, transition)` applies the transition once inside a transaction. [RoomProgressRepository](../../../app/src/main/java/com/example/firststep/game/data/local/RoomProgressRepository.java) stores strict JSON rows keyed by mode and building ID. Its default database is `firststep-game-progress.db`. Registered runs use the separate database chosen by [integration](../integration/). Select the repository before calling the service. Changing content version alone does not create a new storage namespace.

H02 unit and Room checks covered eligibility, retry, duplicate submission, restart and malformed records. The H10C demo exercised retry and stamp readback in its own database. Account sync and combined validation have separate evidence gates. [H02 review](../../harness/reviews/H02-management.md) · [H10C handoff](../../harness/handoffs/H10C.md)
