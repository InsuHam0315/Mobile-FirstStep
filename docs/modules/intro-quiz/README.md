# Introduction and quiz

H04 presents captions, optional Korean TTS, waiting, quiz attempts and results. It delegates the timing, score and stamp to [local progress](../progress/). H10C supplies the approved seven captions and three questions through the registered software DEMO composition.

## Interfaces and callers

- [IntroQuizHost](../../../app/src/main/java/com/example/firststep/game/composition/introquiz/IntroQuizHost.java) accepts `(Context, String mode, boolean instant, String run, boolean fake, long now, IntegrationContext integration)`. `start(sentences, speechMode, speechStatus, observer, ready, failed)` opens the selected catalog/repository on its worker and delivers a ready [IntroQuizController](../../../app/src/main/java/com/example/firststep/game/presentation/introquiz/IntroQuizController.java) on the UI thread. A registered software DEMO supplies the captions and questions via `composition()`. Close the host at screen destruction.
- [IntroQuizActivity](../../../app/src/main/java/com/example/firststep/game/ui/intro/IntroQuizActivity.java) renders the controller snapshot and dispatches `replay()`, `next()`, `finish()`, `start()`, `choose(questionId, optionId)`, `submit()` and `retry()`. `finish()` is the explicit introduction action. The software DEMO exposes it after the seventh caption. Activity restoration binds draft answers to the stored attempt and content version.
- [IntroSpeech](../../../app/src/main/java/com/example/firststep/game/speech/IntroSpeech.java) implements `speak(sentence)`, `stop()` and `close()`. TTS status is informational. Pause stops speech and the foreground tick. Caption navigation works with a missing or failing voice.

## Integration and evidence

Open a registered route only with a validated context from [integration](../integration/). The standalone prototype reads the fixture catalog and uses the default local progress store or its explicit debug run database. Saved attempts and results are authoritative. Unsubmitted answers are screen state and lack a process-death guarantee. Notifications are best effort on the supported standalone route. Integration routes suppress them and use the service's eligibility state.

H04 checks covered caption fallback, explicit completion, retry/result restoration and denied or granted notification behavior. H10C exercised sourced captions and the software DEMO quiz with restart readback. Audible Korean voice quality, a real timed wait and combined app validation remain unverified by the demo run. [H04 review](../../harness/reviews/H04-management.md) · [H10C handoff](../../harness/handoffs/H10C.md)
