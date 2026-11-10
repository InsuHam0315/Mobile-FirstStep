# Discovery

H03 provides simulated arrival, a CameraX preview with a fairy overlay, direction alignment and a summon interaction. It returns discovery provenance to the caller. The overlay is a camera preview effect and has no tracked AR anchors.

## Interfaces and callers

- [DiscoveryHost](../../../app/src/main/java/com/example/firststep/game/composition/discovery/DiscoveryHost.java) is created with `Context`, `LifecycleOwner`, `PreviewView`, a simulated-input flag and a `DiscoveryController.Listener`. `resume()` starts the current generation and returns its token. `pause()` stops the camera and sensor or test driver. `startCamera()` belongs to the Activity's permission path.
- [DiscoveryController](../../../app/src/main/java/com/example/firststep/game/presentation/discovery/DiscoveryController.java) renders a `DiscoveryState` and emits a typed [DiscoveryResult](../../../app/src/main/java/com/example/firststep/game/domain/discovery/DiscoveryResult.java). The Activity dispatches `arrival()`, `cameraFallback()`, `headingFallback()` and the selected summon input. Device callbacks carry the generation, for example `heading(generation, degrees, reliable)` and `acceleration(generation, magnitude)`. Stale generations are ignored.
- Results carry session ID, target ID, method, input source, camera/heading fallback flags and `arrivalSimulated`. SHAKE and its long-press fallback retain `digital_information_software`. The standalone LIGHT exercise uses `synthetic_cover_demo`. [DiscoveryActivity](../../../app/src/main/java/com/example/firststep/game/ui/discovery/DiscoveryActivity.java) hides LIGHT in the integrated route and returns the captured result only after explicit continuation.

## Integration and evidence

Call the debug Activity through [integration](../integration/) when an introduction should follow. That caller validates the route context and nonce before consuming the result. The discovery module does not write introduction times, quiz eligibility or rewards. Keep the input-source and simulated-arrival disclosures when replacing the layout.

H03 emulator checks covered permission/fallback, sensor-input handling and lifecycle. The H10C end-to-end demo used camera, heading and long-press fallbacks. Physical SHAKE, surveyed arrival and tracked AR are outside this evidence. [H03 review](../../harness/reviews/H03-management.md) · [H10C handoff](../../harness/handoffs/H10C.md)
