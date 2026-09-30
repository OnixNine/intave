# Exporting PTR highlights

`render-ptr-highlights.cjs` exports the existing trace data from `build/reports/ptr-branching` using the current viewer template in `PtrBranchingVisualizationTest.java`. It reuses the viewer's Three.js scene, block geometry, player model, simulation selection, and recorded data. It captures the view without titles or debug tables, and adds texture aliases and a diagnostic branch-tree animation with input labels.

Every tick shows translucent hitboxes and parent-to-child paths for all recorded candidates, including diagnostic branches in every layer. Coincident endpoints and edges share geometry to avoid stacking identical surfaces. The opaque player remains the focused possibility. Walking uses Minecraft's damped stride accumulator, shoulder pivots, opposite arm/leg swing, and idle arm sway; swimming uses its stroke cycle. Limb state is reconstructed from recorded displacement because PTR files do not store client render animation state.

Branch labels come from each candidate's recorded configuration: movement keys, sprint, and jump. Multiple modifiers are separated by spaces. Small layers label every candidate; dense layers label the highlighted path, with all candidates still drawn. Labels have no background boxes, so branch lines remain visible behind the text. Tick headings identify the search depth. The tree fills the available height below the headings, with padding for the edge labels. The selected path is marked in green, without an endpoint ring or bottom caption block.

The gallery, scene, and branch panel use `#0A0A0A`; the GIF encoder preserves that exact background color in its palette. Each clip's `focusCamera` selects an unobstructed viewing direction. The camera smoothly orbits to that angle before the branch tour, holds it through the focused section, and returns as the replay resumes. The player uses normal terrain depth throughout. Clip ten holds its focused angle for the entire loop.

During movement, the camera follows a smoothed curve through the recorded positions, with a small trailing offset and a look-ahead target. It gently turns with the route and moves closer during the shot. These offsets fade out for the branch close-up so all alternatives stay in frame. The camera is sampled directly from the replay timeline, making its motion independent of capture order and frame rate. `headingFollow`, `followArcDegrees`, and `orbitSeconds` tune the shot; `replaySeconds` optionally sets its playback length.

The input reports are produced by `PtrBranchingVisualizationTest`. Generate those reports using the project-configured JDK when new replay data is needed:

```powershell
.\gradlew.bat test --tests de.jpx3.intave.check.movement.physics.recording.PtrBranchingVisualizationTest
```

Do not change recordings or physics tolerances to prepare a highlight. Reports may be partial or contain mismatches; check the source and selected range before presenting them.

## Requirements

- Node.js with `playwright` available through normal module resolution or `NODE_PATH`. The exporter can also find the bundled Codex workspace runtime when present.
- JDK 17 or later to read the canonical Java text blocks. Set `PTR_JAVA`, `JAVA_HOME`, or put `java` on `PATH`.
- Chrome, or Playwright's installed Chromium. Set `PTR_CHROME` to use a specific browser executable.
- FFmpeg on `PATH`, or an executable path supplied through `FFMPEG`.
- The public Three.js and Minecraft texture assets used by the existing viewer. Downloads are cached in `build/ptr-highlights/asset-cache` for subsequent runs.

## Commands

Run from the repository root:

```powershell
# Validate report ranges, tour plans, and source recordings without a browser.
node docs/tools/render-ptr-highlights.cjs --check

# Run tool regression tests.
node --test docs/tools/ptr-highlights.test.cjs

# Capture movement and branch-tour previews for all clips.
node docs/tools/render-ptr-highlights.cjs --preview

# Refresh existing interactive reports with the current viewer, preserving trace data.
node docs/tools/ptr-viewer-template.cjs

# Render all GIFs, static posters, and source metadata.
node docs/tools/render-ptr-highlights.cjs

# Render a single clip after adjusting its framing or range.
node docs/tools/render-ptr-highlights.cjs --only=10-branch-search

# Re-encode already captured frames and rebuild the gallery without a browser.
node docs/tools/render-ptr-highlights.cjs --encode-only

# Rebuild the gallery without rendering or encoding.
node docs/tools/render-ptr-highlights.cjs --gallery-only
```

Disabled entries are excluded from the default export. The cherry grove clip is disabled because its source PTR is unavailable; restore that recording before enabling it. `--only` explicitly selects even a disabled clip and will report missing inputs.

Edit titles, source reports, tick ranges, and camera offsets in `ptr-highlights.json`. Every clip includes a branch tree beside the replay. It pauses at a sample with distinct movement alternatives and walks through up to seven representative trajectories, finishing on the exact selected candidate. Set `tourTick` to choose the paused sample explicitly. Every diagnostic candidate remains visible in the tree and the translucent 3D overlays; the tour samples distinct trajectories when the tree is large.

`ptr-branch-tour.cjs` chooses the sample and tour order from the recorded tree. `ptr-branch-tour-view.js` animates the highlighted tree edges and a matching amber hitbox along each candidate's actual ancestor path. The selected candidate turns green. Each edge uses the stored cumulative positions relative to the tick origin, and the camera fits the alternatives without scaling their displacement. Movement clips resume their sequence after the tour; clip ten stays on its three-layer snapshot. The full tour plan and renderer hash are included in `manifest.json`.

The gallery uses `ptr-gallery.html` and displays only the animations. Click a view to pause or resume it. Reduced-motion preferences start the views on their static posters. Titles and descriptions remain available as accessibility labels and in the Markdown gallery.

Frames and intermediate HTML live in `build/ptr-highlights`. Final GIFs, PNG posters, the playback gallery, and `manifest.json` live in `docs/assets/ptr-highlights`. Frames are captured at 960 × 600 pixels and 20 frames per second; GIFs are compressed to 768 × 480 pixels, 12.5 frames per second, and a 96-color palette with an infinite loop. The renderer checks for JavaScript errors and failed remote assets; FFmpeg failures stop the export.

The source hashes and report modification times in the manifest document the existing replay evidence used for each export. Reusing a report does not rerun or certify the current physics implementation.
