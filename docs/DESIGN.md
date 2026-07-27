# GalleryVision — Rebuild & Expansion Plan

> Synthesized via adversarial multi-agent planning (hyperplan): 5 hostile members
> (skeptic, architect, logician, maverick, fact-checker) drafted independently,
> cross-critiqued, and the lead synthesized this plan resolving conflicts C1-C8.
> Status: AWAITING USER APPROVAL before implementation.
> License: AGPL-3.0-only, (C) 2026 Luke Simmons <luke5083@live.com>.

---

## 1. Executive Summary

GalleryVision today is an **OCR demo harness** (a `MainActivity` that runs Paddle-Lite OCR
on four bundled images). This plan rebuilds it into a **complete, fast, fully on-device,
FOSS Android gallery app** with OCR, face recognition, object detection, tagging, notes,
powerful search, and libmpv video playback — Kotlin + Material 3, near-100% tested,
emulator-first, published with hardened CI.

### Pivotal decisions (resolved from adversarial conflicts)
- **Inference engine (C1):** an engine-neutral `:inference` abstraction. **Primary = ONNX
  Runtime Mobile (MIT)** via Paddle2ONNX — proven op coverage, XNNPACK for fast CPU on all
  devices (wide acceleration **without deprecated NNAPI**). **NCNN + Vulkan** is a
  **spike-gated GPU accelerator** behind the same interface (adopted only if PP-OCRv5
  converts cleanly and shows measurable speedup on Pixel-class hardware). Paddle-Lite is
  dropped (upstream effectively abandoned; last commit 2025-05-22; forces NNAPI).
- **No NNAPI (R2):** the current hardcoded `use_nnadapter=1` / `android_nnapi` path is removed.
- **Deny/allow (C5):** fully **non-destructive** — all raw detections persisted; visibility is
  a query-time filter; un-denying requires no re-scan.
- **Search (C4):** immutable `SearchSpec` AST → parameterized `QueryCompiler` (injection-safe);
  FTS5 for text/OCR/notes, indexed comparisons for dates/path, `REGEXP` function + candidate
  post-filter for regex; XOR = `(A OR B) AND NOT (A AND B)`.
- **Scanner (C6):** MediaStore `ContentObserver` + WorkManager generation-safe incremental
  scan. Raw-filesystem inotify rejected (scoped storage).
- **Coverage (C2):** tiered Kover targets (business logic + query compiler 100%, overall ~90%);
  literal 100% treated as vanity; native C++ via GTest, UI via screenshot tests.

---

## 2. Canonical Requirements (authoritative R1–R12)

| ID | Requirement | Binary acceptance criterion |
|----|-------------|-----------------------------|
| R1 | On-device PP-OCRv5 OCR; positioned overlays; select/copy all-or-part; manual edit; custom dictionary; word deny-list | Boxes drawn at correct coords; long-press region copies text; edit persists; dictionary add persists; denied word absent |
| R2 | No NNAPI; wide hardware acceleration | grep: zero NNAPI/NNAdapter; inference via XNNPACK/Vulkan confirmed in logs on emulator + Pixel 9 XL |
| R3 | Fast cold start, initial index, add-image, refresh | Cold start < 1s to grid; single add reflected < 2s; 10k-lib refresh delta < 5s (benchmark) |
| R4 | Images+videos; per-folder deny + allow-list-only mode; filename+location indexed; nested notes on files & folders | Folder deny hides media; allow-list shows only allowed; folder note inherits to children; filename/location searchable |
| R5 | Face detect + name + Contacts integration; face deny-list | Faces boxed; name persists; contact link read/write; denied face hidden |
| R6 | Tagging + object detection; object/tag deny-lists; all detections positioned + editable | Objects boxed+labeled; tag CRUD persists; drag edits box; denied object/tag hidden |
| R7 | libmpv video; gestures (scrub/volume/brightness/zoom/double-tap); GIF/APNG as video; common + custom mpv config | Plays via libmpv; each gesture verified; GIF/APNG loop as video; mpv.conf override applied |
| R8 | Search path/4-dates/text/tag/object/face/note; wildcard+regex each; AND/OR/XOR/NOT; sort all | Compiler test suite: each field wildcard+regex; each boolean op; combined expr; each sort key asc/desc |
| R9 | Kotlin, latest stack, Material 3, excellent UI | Kotlin-only; M3; screenshot tests approved; catalog at latest-stable |
| R10 | Near-100% coverage; emulator-first then Pixel 9 XL | Kover meets tiered thresholds; suite green on emulator CI; device smoke documented |
| R11 | AGPL-3.0-only, (C) Luke Simmons; on-device; F-Droid-compatible | LICENSE AGPL-3.0-only; SPDX headers; no network except explicit model download; F-Droid-clean |
| R12 | Commit often; publish Skyluker4/GalleryVision; dependabot(all, 7d); super-linter; test+build workflows; tight pins | Repo pushed; CI green; dependabot covers gradle+actions(+docker); SHA-pinned; catalog pinned |

---

## 3. Architecture (R9, D7)

Multi-module, Clean Architecture, unidirectional data flow, Hilt constructor DI, Compose UDF
(`StateFlow` + Paging 3).

```
:app                composition root; @HiltAndroidApp; MainActivity; NavHost; binds data impls
:core:model         pure Kotlin: MediaItem, Detection(Text/Face/Object), Note, SearchSpec, Box
:core:common        Result, dispatchers, geometry (Matrix transform pipeline)
:core:ui            M3 theme, shared Compose components, overlay canvas
:core:database      Room DB, entities, DAOs, FTS5 tables, migrations
:core:datastore     proto/prefs DataStore: settings, deny/allow lists, mpv config, dictionary
:core:testing       fixtures, fakes, Room/coroutine test rules
:data:mediastore    MediaStore ContentObserver + queries -> MediaItem
:data:index         scan/index WorkManager pipeline; detection persistence; generation guard
:domain             use cases: library, search, index, deny/allow, notes, faces, tagging
:inference          engine-neutral OCR/face/object API; :inference:ort (ONNX RT); :inference:ncnn (spike)
:feature:library    grid + folders UI
:feature:viewer     image viewer + OCR/face/object overlays + edit
:feature:video      libmpv player + gestures
:feature:search     query builder UI + results
:feature:faces      face naming + contacts sync
:feature:settings   settings, mpv config editor, model/language pack management
```

Dependency rule: `feature:* -> domain -> core:model/common`; `data:* -> domain` (impl) bound in
`:app`; `:inference` depended on by `:data:index`; no feature depends on another feature.

---

## 4. Inference Engine (C1, D1) & On-device Models (D2)

- **`:inference` API** (engine-neutral): `TextRecognizer.detect(bitmap): List<TextRegion>` (with
  4-vertex `dt_polys`), `FaceDetector`, `ObjectDetector`. Implementations selected at DI time.
- **`:inference:ort` (default, MIT):** ONNX Runtime Mobile 1.28. Models produced by Paddle2ONNX
  from PP-OCRv5 mobile det + per-language rec packs (`inference.json` + `inference.pdiparams`,
  opset pinned). Acceleration: XNNPACK EP (CPU SIMD, all devices) + optional GPU EP. **No NNAPI EP.**
- **`:inference:ncnn` (M0 spike-gated, BSD-3):** NCNN + Vulkan GPU path. Adopted as the accelerated
  backend **iff** PP-OCRv5 DB/CRNN-CTC convert (`onnx2ncnn`) with output parity and measurable
  speedup vs ORT+XNNPACK on emulator + Pixel 9 XL. Otherwise ORT remains sole engine.
- **Models (D2):** bundle the minimal Latin/English PP-OCRv5 mobile det+rec pack; other language
  packs (korean, cyrillic, arabic, devanagari, japanese, ...) downloaded on demand from documented
  FOSS sources via a **versioned manifest with SHA-256 + license metadata**; stored in app files;
  inference always on-device. INT8 quantization evaluated in the M0 spike for speed (R3).
  **OCR fallback/edges:** Tesseract4Android (Apache-2.0) is an optional lightweight fallback for clean
  printed documents; an optional on-device VLM (Qwen2.5-VL-class GGUF via llama.cpp) may be added later
  as a "heavy" handwriting mode only if measured handwriting CER exceeds ~15-20% on the target mix.
- **Faces (D3, F4):** MediaPipe **BlazeFace full-range** (detection, Apache-2.0, converted to ONNX
  via tf2onnx) + OpenCV Zoo **SFace** (recognition, permissive, 128-d embeddings). **Amends the earlier
  YuNet choice:** the official OpenCV Zoo YuNet export has a dead objectness head, and with cls-only
  scoring it hallucinated faces on texture — on the probe corpus receipts scored *higher* than the one
  real portrait (junk to 0.93 vs real 0.86) at every size (2-80% of frame), so no score/size/aspect/
  landmark/embedding-norm filter could separate junk from real (all measured). BlazeFace full-range
  separates them cleanly (real face 0.63-0.75, texture < 0.15). Detection runs through the verified
  24x24/stride-8/4-anchor SSD decode; partial boxes are dropped by size-aware containment.
  dlib ResNet (Boost) is the permissive C++ alternative.
  **InsightFace ArcFace "buffalo" packs excluded** (non-commercial research only). Embeddings computed
  and stored on-device only, clustered, user-named, optional `ContactsContract` link. Biometric-consent
  UX (see §10).
- **Objects (D4, F5):** **YOLOX** (Apache-2.0, Megvii; first-class ncnn/ONNX export) primary, or
  **MediaPipe EfficientDet-Lite** (Apache-2.0 code+weights) for the smallest footprint; COCO-80 via
  `:inference`. PicoDet-S/NanoDet-Plus remain acceptable Apache-2.0 fallbacks. **Ultralytics YOLO
  excluded** (AGPL-3.0 code+weights); **YOLO-NAS excluded** (restrictive/NC weights).
- **Tagging (image classification):** **MobileNetV3 / EfficientNet-Lite** (Apache-2.0 code+weights,
  ONNX/TFLite, few-ms) for fixed-label tags; optional **OpenCLIP / SigLIP 2** (Apache-2.0 weights)
  image-encoder for open-vocabulary tags (verify per-checkpoint LAION terms).

---

## 5. Data Model & Search (C4, C5, D6, D8, R8)

### Room schema (source of truth = MediaStore; Room = queryable read model)
- `media(id, source_uri UNIQUE, path, folder_id, type, date_taken, date_added, date_modified,
  date_created, lat, lon, width, height, duration, scan_generation)`
- `folder(id, path, parent_id)` — nested tree
- `detection(id, media_id, kind{TEXT,FACE,OBJECT}, box(x,y,w,h + poly for text), label, confidence,
  value_text, embedding?, edited BOOLEAN, source{AUTO,MANUAL})`
- `tag(id, name)`, `media_tag(media_id, tag_id, box?)`
- `face_cluster(id, name?, contact_lookup_key?)`, `detection.cluster_id`
- `note(id, target_kind{MEDIA,FOLDER}, target_id, body, parent_note_id)` — nestable
- `denylist(kind{WORD,OBJECT,TAG,FACE}, value)`, `folder_policy(folder_id, mode{DENY,ALLOW})`,
  `settings(allow_list_only BOOLEAN)`, `dictionary(word)`
- **FTS5** external-content tables mirroring `detection.value_text`, `note.body`, `media.path`
  (tokenizer chosen per M0 spike: `unicode61` + `trigram` fallback for CJK/substring).

### Search algebra (formal, logician-arbitrated)
- Grammar: `expr := or ; or := xor ('OR' xor)* ; xor := and ('XOR' and)* ;
  and := not ('AND' not)* ; not := 'NOT'? atom ; atom := '(' expr ')' | fieldPredicate`
- Precedence (tightest→loosest): `NOT > AND > XOR > OR`; parentheses override.
- `fieldPredicate := field ':' matcher`; `field ∈ {path,created,modified,added,taken,text,tag,
  object,face,note}`; `matcher ∈ {literal, "phrase", wildcard*, /regex/, range for dates}`.
- Compilation (immutable `SearchSpec` AST → `QueryCompiler`, all bind-parameterized):
  - text/note/OCR literal+phrase+prefix → FTS5 `MATCH`.
  - dates → indexed range comparisons; path literal/wildcard → escaped `LIKE`.
  - regex (any field) → SQLite `REGEXP` UDF (registered) over a candidate set narrowed by any
    non-regex conjuncts, else full post-filter in Kotlin.
  - `XOR(A,B)` → `(A OR B) AND NOT (A AND B)`; `NOT` → `NOT (...)`.
- Sorting: any of the 10 fields, ASC/DESC, stable tiebreak on `media.id`.

### Deny/allow (total function, non-destructive)
- All detections stored regardless of deny lists. Visibility computed at query time:
  `visible(media) = folderVisible(media.folder) AND NOT anyDeniedDetection(media)`.
- `folderVisible`: if `allow_list_only` → visible iff nearest ancestor with a policy is ALLOW;
  else visible unless nearest ancestor policy is DENY. Closest-ancestor rule wins; root default
  configurable. Un-denying edits the list only → immediate effect, **no re-scan**.

### Geometry (R6)
Single `Matrix` pipeline maps model-space boxes → EXIF-oriented → display-scaled → user zoom/pan;
inverse used when editing a box back to canonical image coords. Round-trip tolerance ≤ 1px asserted.

---

## 6. Video (C-none, D5, R7)
- libmpv via `:feature:video`: **prebuilt AAR `dev.jdtech.mpv:libmpv:1.0.0`** (Maven Central, 4 ABIs,
  maintained; source: https://github.com/jarnedemeulemeester/libmpv-android). Amends the earlier
  "source-adapted, not an AAR" note: the AAR already provides the `MPVLib` JNI bridge +
  `libmpv.so`/ffmpeg shared libs, so no vendored Java/JNI is needed. Building our own .so from
  source (pinned fork commit, patched build flags) remains a publish-gate option (see §7).
- **License reality (research-corrected):** mpv core is **GPLv2+ by default** (LGPLv2.1+ only with
  `-Dgpl=false`) and the AAR's ffmpeg is built with `--enable-gpl,version3` → the bundled native
  stack is GPL, not LGPL. This is compatible with our AGPL-3.0-only app (GPL-2.0+ → GPL-3.0 →
  AGPL §13; same posture as VLC). Obligations met via `NOTICE` provenance (exact coordinates +
  build-script source). If a weaker-copyleft native stack is ever wanted, rebuild mpv with
  `-Dgpl=false` and ffmpeg without `--enable-gpl`/`--enable-nonfree`.
- Compose gesture layer (mpvKt reference, Apache-2.0): horizontal drag = seek/scrub, vertical
  left = brightness, vertical right = volume, pinch = zoom, double-tap = play/pause.
- GIF/APNG/animated routed to libmpv (ffmpeg gif/apng demuxers) and looped (`loop-file=inf`,
  `demuxer-lavf-o=ignore_loop=0`); static images use Coil. **APNG/animated-WebP are classified by
  content sniffing** (`AnimatedSniff`: `acTL` before first `IDAT`; `ANIM` RIFF chunk), because
  MediaStore reports them as image/png and image/webp.
- Common settings + user `mpv.conf` override via settings editor (`SettingsStore.mpvConfig`,
  parsed `key=value` lines applied as mpv options before init).
- **Supported media formats (R4/R7):**
  - *Still images* (Android `ImageDecoder` + Coil): JPEG, PNG, WebP (lossy/lossless + animated),
    HEIF/HEIC (API 28+), AVIF (API 31+ natively; API < 31 via bundled `libavif`, BSD-2), BMP, ICO,
    WBMP; **JPEG XL fully supported via a bundled `libjxl` decoder** (BSD-3, Coil native-decoder
    extension, e.g. jxl-coder); SVG via `coil-svg`; camera RAW (DNG and OEM raw) best-effort via
    `DngCreator`/device codecs.
  - *Animated* (routed to the video player per R7): GIF, APNG, animated WebP/AVIF.
  - *Video/audio* (libmpv/ffmpeg): MP4, MKV, WebM, MOV, AVI, 3GP, TS/MTS/M2TS, FLV, WMV, OGG and
    essentially every ffmpeg-supported container; codecs H.264/AVC, H.265/HEVC, VP8, VP9, AV1,
    MPEG-2/4, ProRes, Theora; audio AAC, MP3, FLAC, Opus, Vorbis, PCM.

---

## 7. Licensing (R11), CI/CD (R12), Versions (R9)
- **License:** `LICENSE` = AGPL-3.0-only; SPDX + copyright header in every file; `NOTICE`/`licenses/`
  for NCNN(BSD-3), ONNX RT(MIT), MediaPipe(Apache-2.0), Coil(Apache-2.0), **mpv/ffmpeg(GPLv2+/GPLv3
  per the libmpv-android build config — see §6)**, model licenses. F-Droid metadata; no non-free
  deps/models; document on-device (no server) so AGPL source-offer is met by the public repo.
- **CI (SHA-pinned, ubuntu-24.04):** `dependabot.yml` (gradle + github-actions + docker, daily,
  `cooldown.default-days: 7`); `lint.yml` (super-linter v8.6.0 `9e863354...`, checkout v6.0.2
  `de0fac2e...`, ktlint + clang-format, exclude submodules/generated); `test.yml` (unit+Kover, native
  GTest, instrumented via `reactivecircus/android-emulator-runner` API 36, coverage gate); `build.yml`
  (assemble APK+AAB, tag→Release + build-provenance attestation, dev pre-release on main).
- **Versions (version catalog, latest-stable at impl):** Kotlin 2.4.x, AGP latest supporting it,
  Gradle 9.x (distribution SHA-verified), Compose BOM latest-stable, Coil 3.5.x, Room 2.8.x,
  CameraX 1.6.x, Hilt/WorkManager/DataStore latest, NDK r27 LTS (27.3.13750724), CMake 3.31.x.
  M1 verifies Kotlin↔Compose-compiler↔AGP compatibility; no dynamic `+` versions.

---

## 8. Test Strategy (R10, C2)
- Kover tiers: domain/use-cases **100%**, query compiler **100%**, data/repos **95%**, viewmodels
  **90%**, UI **70%** (screenshot-tested via Roborazzi), overall **~90%**. Generated code excluded.
- Native C++ (JNI/pre/post-process) via **GTest**; JNI boundary via instrumented tests.
- Instrumented + screenshot on emulator (Pixel_API_37.1) in CI; final smoke on Pixel 9 XL.
- Honest ceiling: literal 100% is vanity; business logic + query compiler are the 100% floors.

---

## 9. Milestones

- **M0 — Spikes (gate all feature work; each binary pass/fail):**
  1. Emulator readiness (boot Pixel_API_37.1, API/ABI, smoke APK, URI grants).
  2. **OCR engine gate:** Paddle2ONNX PP-OCRv5 mobile det+rec → ONNX; `onnx.checker`; desktop parity;
     ORT Android inference on emulator+device; then attempt `onnx2ncnn`+Vulkan parity/speed → pick backend.
  3. Room FTS5 availability + tokenizer (bundled SQLite driver if needed).
  4. libmpv build/packaging + LGPL config; playback smoke.
  5. MediaPipe face task on API 36 + license-cleared embedding source.
- **M1 — Foundation:** modules, DI, Room schema+migrations, MediaStore scan→grid, version catalog,
  AGPL headers, LICENSE, CI (lint/test/build/dependabot). Ships a browsable gallery.
- **M2 — OCR:** PP-OCRv5 on chosen engine; positioned overlays; select/copy; manual edit; custom
  dictionary; word deny-list.
- **M3 — Search:** full data model; `SearchSpec` AST + `QueryCompiler`; all fields, boolean,
  wildcard/regex, sorting; FTS5 wiring.
- **M4 — Faces + Objects + Tags:** MediaPipe + embedding clustering + naming + contacts; object
  detector; tagging; deny-lists; editable positions.
- **M5 — Video:** libmpv player, gestures, GIF/APNG routing, mpv config.
- **M6 — Notes + folder policy + polish:** nested notes; per-folder deny/allow-only; M3 polish;
  screenshot tests; R3 perf benchmarks; coverage to target; Pixel 9 XL device run; publish.
- **Commit cadence:** small signed commits per passing spike / DAO+test / feature slice.

---

## 10. Open Items / Risks
- **R-eng:** ncnn conversion of PP-OCRv5 CTC/DB ops unproven → M0 gate; ORT is the guaranteed fallback.
- **R-mpv:** LGPL-clean libmpv/ffmpeg build is a real build-engineering + legal task (M0).
- **R-perf:** R3 targets require benchmarking; thumbnails precomputed, inference off-main-thread,
  Paging for large libraries.
- **R-legal (faces):** face templates are biometric identifiers. Implement explicit opt-in consent,
  on-device-only storage, retention limits, and deletion (BIPA 740 ILCS 14; GDPR special-category).
  Personal on-device FOSS use is low-risk, but ship consent UX + a DPIA-style privacy note.
- **Compliance manifest:** for EVERY model record BOTH the code license AND the weights license
  (per-checkpoint) in `NOTICE`/manifest. Treat "permissive code + non-commercial weights" as
  non-shippable (excludes Surya, InsightFace, YOLO-NAS, Ultralytics).

---

## 11. Verification & QA Scenarios (executable, tool-named, binary pass/fail)

Every gate below names the exact tool, concrete inputs, and a binary observable. Golden
fixtures live in `:core:testing` and are committed. No milestone/spike is "done" without its
scenarios PASS with captured evidence (RED→GREEN test output + real-surface artifact).

### M0 spike gates (must PASS before feature work)
- **S0.1 Emulator readiness** — `emulator -avd Pixel_API_37.1 -no-window -no-snapshot &`; PASS iff
  `adb wait-for-device` + `adb shell getprop sys.boot_completed` == `1` within 120s, `abilist`
  contains a supported ABI, and a hello APK installs (`adb install`) and reaches RESUMED
  (`adb shell dumpsys activity activities | grep RESUMED`).
- **S0.2 OCR engine gate** — (a) `paddle2onnx` on PP-OCRv5 mobile det+en-rec → `.onnx`;
  `python -c "import onnx,sys; onnx.checker.check_model('m.onnx')"` exit 0. (b) **Desktop parity
  (defined):** ORT-Python on the 5 committed fixtures (assets `det_0/90/180/270.jpg` + 1
  multilingual) → recognized text within **Levenshtein ≤ 2 per line** of golden JSON AND detected
  box count equal AND per-box **IoU ≥ 0.90**. (c) **Android ORT:** `connectedAndroidTest` runs the
  same 5 fixtures on emulator, same tolerances; `adb logcat` shows XNNPACK EP init and contains
  **zero** `nnapi`/`NnApi` lines. (d) **NCNN accel (optional):** `onnx2ncnn` exit 0; on-device
  parity within same tolerance; **"measurable speedup" = median rec-model latency over 20 runs on
  Pixel 9 XL is ≥ 20% below ORT+XNNPACK**. PASS→adopt NCNN; FAIL→ORT stays sole engine.
- **S0.3 FTS5 + tokenizer** — a `connectedAndroidTest` creates `CREATE VIRTUAL TABLE t USING fts5(...)`;
  if it throws, swap to bundled `requery/sqlite-android` and retry. PASS iff a `MATCH` returns the
  seeded row AND a mixed CJK+Latin string is substring-matchable (trigram tokenizer).
- **S0.4 libmpv/LGPL** — verify build config omits `--enable-gpl` (grep the build script / `mpv
  --version` flags); a `connectedAndroidTest` loads a 5s `sample.mp4` and a `sample.gif` via `MPVLib`.
  PASS iff `getPropertyDouble("time-pos") > 1.0` within 3s for both.
- **S0.5 Face model + license** — a `connectedAndroidTest` runs MediaPipe Face Detector on a bundled
  portrait returning ≥1 box; candidate embedding model returns a fixed-dim vector with cosine(same-face) >
  0.6 and cosine(diff-face) < 0.4 on committed crops; **AND** a permissive redistributable license
  URL is documented. Any part FAIL → face-recognition degrades to detection + manual naming.

### M1–M6 exit scenarios
- **M1 Foundation** — `./gradlew :app:assembleDebug testDebugUnitTest` exit 0; seed emulator
  (`adb push fixtures /sdcard/DCIM`, media scan) then `connectedDebugAndroidTest` asserts grid item
  count == seeded count; pushed branch shows lint/test/build workflows green.
- **M2 OCR** — `./gradlew :feature:viewer:connectedDebugAndroidTest` on the emulator: open a seeded
  image with known text → each overlay box corner within **≤ 1.0% of image width/height (normalized)**
  of golden coords; copy action places the exact expected string in `ClipboardManager` (assert
  `primaryClip`); manual edit persists across DB reload (re-read == edited value); a denied word
  returns **0** matching regions for that image.
- **M3 Search** — JVM parametrized suite on in-memory Room: for each of 10 fields ×
  {literal, wildcard, regex}, each boolean op, the XOR truth table, and each sort key asc/desc →
  compiled query result-id set == expected; an injection payload yields no SQL error and no extra
  rows. `./gradlew :domain:test :core:database:test` green.
- **M4 Faces/Objects/Tags** — `./gradlew :feature:faces:connectedDebugAndroidTest` and
  `:feature:library:connectedDebugAndroidTest` on the emulator: detect face → assign name → DB reload
  returns the same name (string equals); with `READ_CONTACTS` granted via `GrantPermissionRule`, link
  writes a non-null contact lookup key re-read from DB; object detector on the committed COCO fixture
  returns the expected top-1 label with confidence **≥ 0.50**; tag add/edit/delete each reflected in a
  follow-up query (exact id-set); a denied object/tag yields **0** visible detections of that value.
- **M5 Video** — `./gradlew :feature:video:connectedDebugAndroidTest` (Espresso) on the emulator:
  horizontal swipe increases `time-pos` by **≥ 5s**; vertical-right swipe changes `volume`; double-tap
  toggles `pause` (boolean flips); a `.gif` loops (`time-pos` wraps to < previous). Each asserted via
  MPV property reads.
- **M6 Notes/policy/polish** — `./gradlew :feature:library:connectedDebugAndroidTest` on the emulator:
  folder note inherits to child (query assert); folder deny hides its media; allow-only mode shows only
  allowed; **Macrobenchmark** (`./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`): cold start
  < 1s (`StartupTimingMetric`), 10k-item refresh delta < 5s; `./gradlew koverVerify` exit 0 (tiered
  thresholds); Roborazzi screenshot tests pass (`./gradlew verifyRoborazziDebug`); Pixel 9 XL smoke
  run captured.

### Standing gates (every commit)
`lsp`/compile clean on changed files; `./gradlew test` green; super-linter green; SHA-pinned
actions; no `nnapi` symbols; no non-free model/dep introduced.

## 12. Status: In Implementation (M0)
The referenced engineering report (2025-26 on-device FLOSS CV for Android) has been provided and
incorporated: it validates the ONNX-Runtime-first engine decision and resolves the face-stack question
(YuNet + SFace, both permissive). Defaults adopted (non-destructive, user-vetoable): ORT primary +
NCNN/Vulkan spike-gated; English/Latin bundled with other language packs on-demand; milestone order
M0→M6; YuNet+SFace faces; YOLOX/EfficientDet-Lite objects; MobileNetV3/EfficientNet-Lite tagging.
Implementation has begun at M0. The user may still adjust any default.
