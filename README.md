# Motion Studio (Android)

Native Android rewrite of **Motion Studio** built with **Kotlin**, **Jetpack Compose**, **Material 3**, and **Android Canvas / MediaCodec**.

## Features

- **Home & Project Hub**: Create new projects across 4 canvas presets (16:9 HD, 9:16 Reel, 1:1 Square, 16:9 FHD) and 24/30/60 fps, launch 3 live-rendered demo projects (*The Lost Kingdom*, *Sakura Wind & Cloth*, *Midnight Lyric Stage*), browse/search locally persisted projects, and import `.motionx` archives.
- **Composition Editor**: Interactive 2D stage with multi-plane depth parallax, drag/scale/rotate selection gizmos, spatial motion-path editing, pinch-to-zoom/pan, safe guides, Layers panel (parenting with cycle detection, pre-compose, nested compositions, reorder/lock/solo/visibility), 4-tab Inspector (`Layer`, `Presets`, `Cam`, `World`), and multi-track Composition & Audio Timeline with frame-accurate keyframe scrubbing and undo/redo history.
- **Procedural Motion Engine & Presets**: 37 built-in presets across hair, clothing (16-strip mesh deformation), character, camera, environment, lyrics, and FX, plus custom preset saving and non-destructive baking to keyframes.
- **Lyrics Studio**: Import `.lrc` (standard & enhanced karaoke), `.srt`, or plain text; configure 10 lyric animation styles, BPM beat pulse, glow/highlight colors, and cue start/end timings.
- **Movie Sequencer**: Sequence compositions into a multi-act movie timeline with Hard Cut, Cross Dissolve, and Fade via Black transitions and live playback preview.
- **Asset Library & Character Studio**: Zero-permission image/sprite-sheet import via Android Photo Picker and SAF audio import, non-destructive asset replacement, sprite-sheet grid slicing, clip FPS/loop configuration, and interactive frame picking.
- **Export & `.motionx` Archives**: Deterministic PNG frame snapshots, native Android H.264 `.mp4` video rendering via `MediaCodec` + `MediaMuxer`, and portable wire-compatible `.motionx` JSON project export/import via SAF.
- **MCP Automation & Client**: Built-in 10-tool Local Studio MCP Engine (`get_project_summary`, `create_composition`, `add_layer`, `set_keyframe`, `apply_motion_preset`, `update_environment`, `set_lyrics_cues`, `evaluate_frame`, etc.) plus a real Streamable HTTP JSON-RPC 2.0 client for external MCP servers.

## Build Requirements

- Android Studio / Gradle 9.3.1 (Kotlin DSL), AGP 9.1.1, Kotlin 2.2.10, JDK 21, Android SDK 36 (`minSdk = 26`).
