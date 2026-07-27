<!-- SPDX-License-Identifier: AGPL-3.0-only -->
<!-- Copyright (C) 2026 Luke Simmons <luke5083@live.com> -->

# GalleryVision

A fast, fully **on-device**, free and open-source (FOSS) Android gallery that sees your
media: on-device OCR, face recognition, object detection, tagging, notes, and powerful
search — plus a gesture-driven video player. No cloud, no tracking, no network required
for core features.

> **Status:** under active rebuild. The full design is captured in
> [`docs/DESIGN.md`](docs/DESIGN.md).

## Features (target)

- **OCR** — on-device text recognition (PaddleOCR PP-OCRv5, 106 languages) with text drawn
  at its real position; select and copy all or part; manually edit recognized text; custom
  dictionary; per-word deny list.
- **Faces** — on-device face detection and recognition; name faces; link to Contacts; face
  deny list.
- **Objects & tags** — on-device object detection and image tagging; object/tag deny lists.
  Every detection (text, face, object) has an editable position.
- **Video** — libmpv playback with gestures (drag to scrub, volume, brightness, pinch to
  zoom, double-tap to play/pause); GIF/APNG played as video; custom `mpv.conf`.
- **Organize** — per-folder visibility (deny list or allow-list-only mode); nested notes on
  files and folders; filename and location indexing.
- **Search** — file path, dates (created / modified / added / taken), text, tags, objects,
  faces, and notes; wildcards and regular expressions on every field; arbitrary boolean
  combinations (AND / OR / XOR / NOT); sort on any field.
- **Fast** — quick cold start, indexing, adding images, and refresh.

## Privacy & licensing

- **100% on-device.** Inference runs locally. The only optional network access is
  downloading additional OCR language packs (hash-verified, from documented FOSS sources).
- **License:** [GNU AGPL-3.0-only](LICENSE). Copyright (C) 2026 Luke Simmons
  &lt;luke5083@live.com&gt;.
- **FOSS / F-Droid friendly.** No proprietary dependencies or non-free models are bundled.
  Third-party components and model licenses are enumerated in [`docs/DESIGN.md`](docs/DESIGN.md)
  and will be listed in a `NOTICE` file.

## Tech

Kotlin, Jetpack Compose (Material 3), Room, WorkManager, Hilt, Coil, ONNX Runtime Mobile /
NCNN (Vulkan) for inference, MediaPipe for face detection, libmpv for video.

## Build

Requirements: Android Studio (latest), Android SDK (API 36), NDK r27 LTS, CMake, JDK 17+.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest   # requires a running emulator or device
```

## Contributing

Because GalleryVision is AGPL-3.0-only, contributions are accepted under the same license.
