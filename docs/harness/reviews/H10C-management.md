# H10C management record

## Scope review — 2026-11-10

No implementation candidate or runtime acceptance yet. Main baseline before planning: 58c8a3087ed43851169716dab030ec830eb85392. Existing content owner/chat is opening accepted H10B on its retained demo device before any H10C dispatch.

Implementation PM performed a bounded read-only seam audit: accepted H06 navigation and intro/quiz/progress can be reused, but context/registry, stable run keys, navigation persistence and Room opens must discriminate the new dataset. Existing H10/H10B content supplies approved factual narration/questions. No unresolved product choice was identified for a strictly DEMO-only local episode.

Independent verification PM identified three scope improvements: user ownership of 5580 after handoff, debug/release rejection beyond hiding the UI, and synthetic rather than real-account isolation evidence. Management incorporated all three and the PM re-read the current scope with PASS. This accepts the scope for dispatch, not code or app behavior. H07 live gates and H07B dependency remain unchanged.

Next: finish the accepted-demo opening-only handoff, fast-forward the clean idle campus-content worktree to the management scope commit, dispatch H10C exactly once, and record actual start. Do not test on the handed-off demo device while the user views it.
