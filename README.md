# Vision Lab — edges and contours

Vision Lab is a Spring Boot application for viewing traditional OpenCV edges and contours over a photograph. The light dashboard contains only **Take photo**, **Contours** and **Edges**. Capture processes one frame automatically; uncheck both boxes to see the original photograph. Processing runs entirely in Java. The existing experimental API and read-only analysis reports remain available for technical inspection, without adding controls to the dashboard. No object recognition, neural networks or Python are used.

## Repository

Source control is publicly available at **https://github.com/lekgotlaletsoisa-del/vision**. The repository contains source, tests, static assets, Maven configuration and the deployment templates for this host. Build output, local environment files, private keys and logs are excluded by `.gitignore`. Production authentication databases, secrets, certificates and captured images are not part of source control.

The main branch is `main`. Before committing changes, update the affected README/API documentation, run the Maven verification and Javadoc commands below, and run the JavaScript regression checks for frontend changes. The owner's standing instruction for this project is to commit and push every successful change without requesting routine confirmation. After pushing, use `./deploy/deploy.sh` to update the configured service and verify readiness through the established deployment workflow. Project-specific agent instructions are recorded in `AGENTS.md`.

## Requirements and pinned stack

- Java **21** (JDK required to build and generate Javadoc).
- Ubuntu on **x86-64** by default; compatible Ubuntu ARM64 builds can be created with `-Dnative.platform=linux-arm64`.
- The checked-in Maven wrapper selects Maven **3.10.0**. A compatible installed Maven can also build the project.
- Spring Boot **4.1.2**, Spring MVC and Thymeleaf; the original `za.co.vision:vision:0.0.1-SNAPSHOT` coordinates and JAR packaging are preserved.
- Bytedeco OpenCV **4.14.0-1.5.14**, JavaCPP **1.5.14**, OpenBLAS **0.3.34-1.5.14**, with matching platform classifiers pinned in `pom.xml`.
- Bootstrap **5.3.8** CSS is included locally with its upstream MIT license. No CDN, Node build, Python runtime, external image service or system OpenCV installation is required.

The first Maven build needs internet access for dependency downloads. Subsequent application startup uses the native libraries bundled in the executable JAR. A current browser with JavaScript and `createImageBitmap` is required for the dashboard. The in-page camera also needs HTTPS (or localhost), camera permission, `getUserMedia`, native HTML dialogs and canvas frame encoding.

The app icon combines a teal camera, blue lens and contour/focus marks on a light mint tile. The editable vector source is `src/main/resources/static/favicon.svg`; the matching `favicon.ico` contains 16/32/48/64/256-pixel versions, with a 32-pixel PNG fallback and 180-pixel Apple touch icon alongside it. The dashboard declares each asset explicitly and uses the same vector in its header. The root `/favicon.ico` also handles browsers' automatic icon requests. Icon URLs include a version query to refresh cached artwork when it changes; the files are bundled in the executable JAR.

## Build and run on Ubuntu

From the project directory:

```bash
java -version
sudo apt-get install libgtk2.0-0t64 libgl1
./mvnw clean verify
./mvnw javadoc:javadoc
java -jar target/vision-0.0.1-SNAPSHOT.jar
```

The deployed site is **https://test.letsoisa.co.za/**. Vision's Authelia gate is currently disabled at the owner's request until manually enabled again; other hosted applications retain their own authentication. For local development, open **http://localhost:8090/**. The default bind address is `127.0.0.1` and the default port is `8090`, avoiding the other applications on this host. Stop the deployed service before running a local development instance on that same port, or choose a different port. For development, `./mvnw spring-boot:run` is also supported. To select another port:

```bash
java -jar target/vision-0.0.1-SNAPSHOT.jar --server.port=8092
```

The application loads OpenCV eagerly. Check readiness and the loaded native version with:

```bash
curl --fail http://127.0.0.1:8090/api/vision/health
```

A successful response contains `{"status":"ready","opencvVersion":"4.14.0"}` (JSON property order is not significant). Failure to load native libraries aborts startup; a web server reporting ready therefore has successfully loaded OpenCV. Stop the foreground process with Ctrl+C.

### Native loading and supported platforms

On Ubuntu 24.04/26.04, install the standard runtime packages `libgtk2.0-0t64` and `libgl1` as shown above (Ubuntu 22.04 uses `libgtk2.0-0` instead). The bundled official Java binding also loads OpenCV HighGUI, so GTK 2 is required even though Vision Lab does not create desktop windows. No desktop session or display server is required. These are operating-system prerequisites, not manually copied OpenCV libraries. This application was verified on Ubuntu 26.04 x86-64.

`OpenCvRuntime` calls `Loader.load(opencv_java.class)`, the loading method prescribed by [Bytedeco's OpenCV documentation](https://github.com/bytedeco/javacpp-presets/tree/1.5.14/opencv). This enables the official `org.opencv.core` and `org.opencv.imgproc` Java APIs. JavaCPP extracts the matching bundled JNI/OpenCV/OpenBLAS libraries into its native cache (normally `~/.javacpp/cache`) and loads them automatically, including from a Spring Boot executable JAR. The running account needs a writable cache directory on a filesystem from which native libraries can be loaded. A cache override can be placed before `-jar`:

```bash
java -Dorg.bytedeco.javacpp.cachedir=/var/cache/vision/javacpp \
  -jar target/vision-0.0.1-SNAPSHOT.jar
```

Create that directory with ownership for the service account before using it. Do not manually copy `.so` files or set `java.library.path`. Only CPU Linux libraries for the selected architecture are included, rather than downloading every platform or GPU dependencies. To build on/for Ubuntu ARM64, use:

```bash
./mvnw -Dnative.platform=linux-arm64 clean verify
```

Tests execute natives, so run the build on the matching architecture. Other operating systems/architectures have not been verified. Native libraries implement OpenCV internally; all application orchestration, decoding, edge/contour processing and geometry calls are written in Java.

## Dashboard workflow

1. Select **Take photo**, allow camera access if requested, then tap the shutter. The frame is processed automatically; closing the camera without capture preserves the existing photograph.
2. Use **Contours** to show or hide green retained contour traces and **Edges** to show or hide blue raw Canny edges. Both start checked. These switches are independent and change the display immediately without another upload or processing request. Uncheck both to view only the photograph.
3. Take another photo to replace the result. A new capture enables ENHANCED processing with configured baseline settings (Canny 50/150, Gaussian blur 5, minimum contour area 100 px² and EXTERNAL/SIMPLE tracing). Java compares bounded Canny trials, preserves useful long traces and conditionally repairs tiny gaps. There are no hidden settings from a previous session. Your two display choices remain selected across captures and refreshes.

The dashboard has no upload, threshold sliders, filter controls, stage selectors, contour IDs, bounding boxes, centroids, axes, approximation controls, measurements, export or sharing buttons. The photograph stays visible as the background. A brief status appears during processing or on error; the normal view has no extra status text. A failed request, invalid capture or undecodable result leaves the previous successful photograph and its contours together.

The camera needs HTTPS (or localhost), browser permission and a camera-capable browser. It uses `navigator.mediaDevices.getUserMedia` with rear-facing preference and audio disabled. A muted inline preview opens in a light dialog. Only the shutter-selected frame is sent; no audio or video stream is uploaded. If unavailable, denied or busy, the page explains the problem so it can be retried.

The preferred frame is 2560 × 1920, bounded by 3840 × 2880; negotiated dimensions can differ. A temporary canvas copies the frame without resizing or filtering and encodes JPEG at quality 0.92. Browser decoding checks the configured byte/pixel limits before upload; the Java API independently validates content and dimensions. Tracks stop on capture, cancellation, backgrounding, page exit or a late permission grant after cancellation. Duplicate captures and captures before frame readiness are prevented. Camera-frame resolution can differ from the phone's native still-photo resolution.

### Automatic restoration and server-readable results

Every successful processing request atomically saves the accepted original and exact Java/OpenCV result on the server before returning HTTP 200. The dashboard also keeps one successful photograph/result pair and its two checkbox choices in IndexedDB. Both previews must decode before the displayed pair is replaced. Existing STANDARD photos upgrade in the background on reopening the dashboard, and successful enhanced snapshots are reused on later visits. Failed replacements preserve the previous local snapshot.

Opening a fresh browser automatically restores the latest completed server result and its matching original. Already-enhanced results load without rerunning OpenCV. An older STANDARD result is displayed immediately, then upgraded once using its saved baseline parameters and ENHANCED processing; the improved result is saved as a new immutable version. Failed upgrades keep the previous matching photograph/result. Subsequent visits prefer that browser's successful local snapshot and checkbox choices; there is no live synchronization or manual load button. Clearing browser site data makes the next visit restore the server's latest version. The server's latest alias is shared across site visitors; local sessions are independent.

For ChatGPT or other technical readers, open **[the latest read-only analysis](https://test.letsoisa.co.za/results/latest)** or **[compact measurements](https://test.letsoisa.co.za/api/vision/results/latest/measurements)** directly. These routes remain available independently of the minimal dashboard. The server-rendered report includes ordinary image URLs, settings, stages, contour selection and geometric diagnostics, requiring neither JavaScript nor browser storage. Use its resolved UUID URL to discuss one particular experiment. Checkbox choices affect only the dashboard; the diagnostic report uses its own documented geometry defaults. No AI service is called by the application.

Older successful browser-only snapshots publish once using their saved analysis parameters with ENHANCED processing, never unsent form edits. Existing archived UUID reports remain unchanged; only the dashboard upgrades its working copy. Their former stage, selection and geometry preferences are discarded; the original Edges/Contours choices are migrated. Pending older photographs are processed automatically with current defaults and never paired with their predecessor's measurements. A failed migration or detection upgrade preserves a matching successful local analysis for a later retry on reopening. Snapshots predating display paths must be processed once to obtain label-free contour paths. The original browser/profile must open this version before a local-only photograph can be migrated.

`session-store.js` stores the Blob and result atomically in origin-local IndexedDB database `vision-lab`, store `session`, key `latest`. Snapshot envelope version 1 remains readable; display version 2 stores only `edges` and `contours`. Writes are serialized, Blob URLs are regenerated and revoked, and operation revisions prevent late restoration from replacing a new capture. `server-results.js` resolves the latest UUID before requesting its original so concurrent saves cannot mix photographs. Local storage failure does not prevent processing or server saves.

The server retains the latest **20 successful analyses** by default, with UUID, UTC save time, original bytes and result JSON. Versions survive deployments and service restarts; pruned links return 404. Publication uses a directory rename, so failed or incomplete saves never replace latest. Result JSON is bounded at 32 MiB in addition to the 10 MiB image limit; default retention can use approximately 840 MiB plus staging files. Pruning failures are logged and retried after later saves.

Production storage is `/var/lib/vision/results`, owned by `vision-app` and preserved during deployment. Local development uses `${user.home}/.local/share/vision/results`. Override with `VISION_RESULTS_DIRECTORY` or `vision.results.directory`; `vision.results.retained` accepts 1–100. Back up state if photographs need longer preservation. Tests use temporary stores. Never commit captured images/results.

Saved photographs follow the site's authentication mode: while Vision is public, anyone with site access can read them. Enabling the existing Authelia gate protects the same routes; UUID links do not bypass authentication. Clearing browser data does not delete server copies. Stop the service during manual state-directory maintenance to avoid racing a save.

Java produces bounded JPEG photograph previews and display-only paths simplified within 0.75 preview pixels. The dashboard decodes only original/edges previews and draws the supplied contour paths over the same contained image viewport; it never uses the JPEGs with baked IDs or calculates contours in JavaScript. Source pixel centres are mapped to preview coordinates. Display thickness/simplification never affect source measurements. No retained contours is a valid result; edges and the photograph remain viewable.

## Pipeline and API parameters

The following processing options remain available through the multipart API and saved diagnostic reports. They are intentionally absent from the main dashboard.

Processing lives in `VisionProcessingService`, separate from MVC controllers:

1. Reject empty/oversized uploads. Java ImageIO inspects JPEG/PNG format and dimensions in memory **before native decoding**; it does not perform image processing or save a file.
2. Decode in OpenCV to an 8-bit BGR image at the original dimensions. `IMREAD_IGNORE_ORIENTATION` deliberately ignores EXIF rotation; every returned preview and display path shares the same orientation and coordinate mapping.
3. `Imgproc.cvtColor(..., COLOR_BGR2GRAY)` converts BGR to grayscale.
4. `Imgproc.GaussianBlur` smooths local intensity noise using an odd square kernel, with sigma inferred by OpenCV. A kernel of 1 has no smoothing effect.
5. `Imgproc.Canny` detects intensity boundaries with a 3-pixel Sobel aperture and `L2gradient=true` (Euclidean gradient magnitude). The resulting mask has white edges and a black background. STANDARD uses the exact requested thresholds; ENHANCED compares the bounded trials described below.
6. Generate raw, closed and optionally opened Canny masks. Enabled operations create a separate output; size 1 shares its unchanged input without a full-resolution copy. Default `CANNY` extracts from raw edges; `CANNY_CLOSED` selects closing only; `MORPHOLOGY` selects closing followed by opening. `SEGMENTATION` separately thresholds blurred grayscale using Otsu (bright or dark foreground), then applies the selected morphology. Its active mask is separate from the Canny comparison stages. Raw edges are preserved in every mode. ENHANCED may select the existing closed stage for contours only when its automatic repair guards pass; the returned `contourSource` identifies the actual mask.
7. `Imgproc.findContours` uses the selected retrieval and chain modes on the active mask. Defaults remain `RETR_EXTERNAL` and `CHAIN_APPROX_SIMPLE`. The current OpenCV version preserves the source mask, so no defensive image clone is required. Capture the full hierarchy before sorting/filtering. Connectivity in an edge mask does not establish that it encloses a physical object.
8. Measure source-resolution geometry for discovered contours, then apply every enabled inclusive filter. Existing minimum area defaults to 100 px²; zero allows degenerate contours. ENHANCED additionally admits sustained long traces below that area, while preserving every explicit maximum-area, shape and border filter. Detected count is recorded before filtering; retained count is after filtering. Sort retained results by decreasing area with top/left tie breaks and assign IDs.
9. `PreviewRenderer` creates the four existing JPEG photograph previews (original, raw edges, retained contours/IDs and combined), binary PNG previews of the active extraction mask and each Canny comparison stage, and display-only SVG contour paths. Separate `approxPolyDP(..., closed=true)` diagnostics use a source-coordinate epsilon equal to the selected percentage of closed perimeter; these never replace source measurements or filters. Longest preview side defaults to 1600 pixels, without upscaling. Area interpolation plus a nonzero threshold preserves visible contributions from thin source masks during reduction; the edge photograph additionally thickens marks for display. The extraction-mask preview is not dilated, although reduced pixels can combine source contributions. Source masks, measurements and filters remain unchanged.
10. Return immutable Java records as JSON, with bounded previews, source/preview dimensions, detected/retained counts, raw edge and active-mask pixel counts, actual parameters and duration. JPEG quality defaults to 88. No native matrices escape the service. After successful native processing, the controller atomically saves the accepted original and result via `AnalysisStore`; the response includes its stable URL in `Location`. A persistence failure returns an error and preserves the previous saved result.

Canny's **high threshold** selects strong gradient seeds. Edges between the **low and high thresholds** survive only when connected to strong edges through hysteresis. Values below the low threshold are discarded. Lower thresholds are more sensitive to faint edges and texture; higher ones can lose genuine low-contrast boundaries. These are gradient thresholds, not brightness cutoffs. The API intentionally limits them to `0 ≤ low < high ≤ 255` for this phase; Canny gradients themselves are not restricted to an 8-bit intensity range.

Increasing the blur kernel suppresses noise and fine texture, but shifts/removes detail. Increasing minimum area filters small polygon boundaries after edge detection; it does not change the edge image. Use high contrast, a plain background and a reasonably overhead photograph as an initial experiment.

### Automatic edge and contour refinement

The minimal dashboard sends `detectionMode=ENHANCED`. Direct API calls that omit it, explicit STANDARD calls and legacy Java constructors preserve exact threshold/minimum-area behavior. Old JSON archives without this field deserialize as STANDARD. This keeps technical experiments reproducible while allowing the camera workflow to improve without extra controls.

For ENHANCED, the minimum sustained-trace perimeter is `max(40, 0.04 × longest image side)` pixels. Its raster bounding-box diagonal must also be at least half that length. A contour qualifies if its polygon area meets the existing minimum **or** both trace tests pass. Explicit maximum area, aspect ratio, circularity, solidity and image-border filters remain mandatory. This retains long incomplete or cropped boundaries without admitting short isolated specks or a high-perimeter scribble confined to a tiny region. OpenCV perimeter can follow a thin edge out and back; neither length nor enclosed polygon area proves object completeness. The span check makes the default partially scale with image resolution, although it is not physical calibration.

Canny is evaluated at the requested baseline and at threshold multipliers **0.7, 0.5 and 1.4**, with the upper threshold capped at 255. Every trial uses the same Gaussian-smoothed grayscale photograph, Sobel aperture and L2 gradient convention. Each mask is scored by sustained trace perimeter minus twice short-trace perimeter. An alternative must improve that score by more than 20 pixels or 5% of its magnitude, whichever is larger. Earlier trials win ties. Guards require:

- Existing contour/point budgets and at most 10% edge-pixel density.
- At least 95% pixel overlap with baseline edges and at least 98% of baseline sustained perimeter.
- Total edge pixels no greater than the larger of 1.8× baseline or baseline plus 0.5% of image pixels.
- Short-trace perimeter no greater than the largest of 40 pixels, 1.5× baseline short-trace perimeter or 25% of the candidate sustained perimeter.

If no alternative qualifies, baseline Canny is retained. A high score is a continuity/noise heuristic, not proof that the boundary is useful or belongs to one object. Long grout lines can score well; small useful details can resemble noise. Blank images keep the baseline. At most four Canny passes run inside the existing processing semaphore. Temporary candidate/summary matrices are released before final geometry, with no label image or full-photo copy required. Copies of an edge mask occur only when a trial becomes the best candidate.

Small-gap repair reuses the existing morphology stage; no duplicate morphology implementation is added. For the default CANNY source, ENHANCED tests a **3 × 3 closing kernel**, with opening disabled. New enhanced API requests default to a rectangular kernel; explicitly supplied ellipse kernels remain supported. Automatic repair is refused for disabled/larger kernels, enabled opening or an explicitly selected alternative contour source. The raw and closed external-trace counts must match and added foreground pixels must be positive and at most `max(8, 8% of raw edge pixels)`. This can seal a small break within one connected outline while refusing the tested mergers between separate rectangles or a rectangle and a nearby line. Count/pixel guards are deliberately conservative and do not guarantee that every accidental connection is detected. Blue edges always show selected **unclosed Canny**; green contours may use the guarded closed mask, identified in saved diagnostics. Raw, closed and opened masks remain separately available.

### Interpreting boundary experiments

Closing (dilation then erosion) can reconnect short gaps; a larger kernel can also join neighboring objects, grout and shadows. Opening (erosion then dilation) can remove isolated noise from filled masks but often erases one-pixel Canny edges altogether. Neither operation guarantees a better boundary. Kernel sizes are source pixels, so the same kernel has different relative effects on differently sized photographs. Keep raw Canny as the baseline. Compare closing 1, 3 and 5 with opening 1, then inspect each mask before increasing sizes. Size 1 means no operation, not a weaker operation. `Imgproc.getStructuringElement()` supplies a rectangular or elliptical footprint; `Imgproc.morphologyEx(..., MORPH_CLOSE)` performs closing. Opening explicitly calls `Imgproc.erode()` then `Imgproc.dilate()` with the same footprint and one iteration, matching `MORPH_OPEN`. All use OpenCV's default anchor and morphology border policy.

Canny detects intensity changes, not complete object regions. Closed polygon measurements of a broken edge trace can be misleading. The separate brightness-region experiment provides an explainable **region segmentation** alternative when foreground/background brightness separates clearly. Otsu uses one global grayscale threshold: differently colored objects with similar brightness, lighting gradients, texture and grout may still fragment or merge. Choosing the wrong foreground polarity may identify the floor as one border-touching region. Keep border exclusion optional and inspect that failure rather than hiding it automatically. None of these modes identifies shoes, small objects or floor tiles semantically.

### Structuring elements, tracing and approximation

A rectangular kernel includes every pixel in its square footprint, including the corners. It often bridges horizontal/vertical gaps readily. An ellipse omits corner pixels and has a rounded footprint; small digital ellipses are only rough approximations of circles. Neither is universally preferable: kernel orientation, gap width, noise thickness and grout spacing all matter. The synthetic fixtures show both shapes merging separate objects under excessive closing, and different corner retention under opening.

`RETR_EXTERNAL` returns outermost traces and is useful for a baseline with fewer duplicate outlines. `RETR_TREE` returns nested boundaries with parent/child/sibling relationships. Canny often creates outlines on both sides of a bright region, so TREE can increase the count without finding more physical objects. Each hierarchy link uses the original **zero-based extraction index**, with -1 for absent links. These indices differ from the area-sorted **one-based result ID**. Links can refer to traces removed by filters. Odd nesting depth is labelled a hole trace in this mask; the area shown for each trace remains its independent polygon area and does not subtract children.

`CHAIN_APPROX_SIMPLE` compresses straight horizontal, vertical and diagonal runs. `CHAIN_APPROX_NONE` retains each boundary step and can use much more memory. For the same mask these storage choices should preserve geometric measurements; neither closes a missing physical boundary. The saved diagnostic report exposes stored point count to make the difference visible.

`approxPolyDP()` creates a diagnostic polygon. A larger epsilon generally removes more vertices, but can alter area, perimeter, corners and narrow details. The API provides approximation paths and their own area/perimeter/vertex count for comparison with the source trace and rotated box. Epsilon 0 retains the zero-tolerance polygon, which may still eliminate redundant collinear points. Filtering and primary measurements always use the original returned trace. A closed polygon convention in these APIs is not a test of physical boundary completeness: an open edge may be followed out and back, producing near-zero polygon area. No automatic “complete object” label is assigned.

### Suggested shoe-and-floor experiment

For technical experimentation, submit the same original photograph to the multipart API with different parameters, then inspect each immutable saved report. These experiments use the API, not the simplified dashboard. Keep thresholds, blur and filters fixed while changing one parameter at a time:

1. Process raw Canny with closing 1/opening 1 and inspect the original, raw mask and final geometry. Select a suspect contour, disable **Edges** if necessary to isolate it, and compare its green trace with the amber box.
2. Set closing 3/opening 1, reprocess, and switch between raw and closed masks. Try ellipse and rectangle separately. Use `processingMode=CANNY_CLOSED` to measure changes in continuity and box fit. A lower contour count alone is not evidence of improvement.
3. Increase closing to 5, 9 or 15 and inspect whether grout or neighboring items become connected. Record contour area, rotated-box area, fit, solidity and aspect ratio. Several disconnected traces can still belong to one shoe; a single merged trace can include multiple unrelated things.
4. Try opening 3 and inspect stage 4 before trusting it. Thin edges may disappear; opening is usually easier to interpret on filled brightness regions.
5. Compare TREE with EXTERNAL, inspect hierarchy and stored points, then compare approximation 0.1%, 1% and 2%. Keep fit/circularity/solidity filters permissive initially so failure cases remain visible.

These experiments measure contour behavior. Perspective correction, camera calibration, illumination normalization and directional line suppression are possible later traditional-OpenCV investigations; they are not automatically enabled here. The original shoe photograph is not bundled with this repository, so synthetic results do not establish its real-scene accuracy.

## Geometry conventions

All lengths/coordinates are **pixels**, area is **pixels squared**, and angles are **degrees**. These are image measurements, not millimetres or physical dimensions. No camera calibration, lens correction or perspective rectification is applied.

| JSON measurement | Meaning |
| --- | --- |
| `id` | One-based label matching the overlay; IDs are local to each request. |
| `area` | Absolute polygon area from `Imgproc.contourArea`; not the count of filled image pixels. |
| `perimeter` | Closed curve length from `Imgproc.arcLength(..., true)`. |
| `boundingRectangle` | `Imgproc.boundingRect`: integer `x`, `y`, `width`, `height`. Inclusive raster endpoints mean a polygon spanning x=20 through x=140 has raster width 121. |
| `centre` | Area centroid `(m10/m00, m01/m00)` from `Imgproc.moments`. If `abs(m00) < 1e-9`, use the rotated-rectangle centre and set `centroidFallback=true`. |
| `rotatedRectangle.centre` | Centre of `Imgproc.minAreaRect`, which can differ from a non-rectangular contour's centroid. |
| `rotatedRectangle.width/height` | Geometric side lengths normalized to `width ≥ height`; these differ from inclusive raster bounding sizes. |
| `rotatedRectangle.orientationDegrees` | Direction of the normalized width/long axis, **clockwise from positive X** in image coordinates, modulo 180, in `[0,180)`. When OpenCV returns width shorter than height, swap the sides and add 90° before normalization. |
| `rotatedRectangle.vertices` | Four original OpenCV rectangle corners, in cyclic order; no fixed starting corner is promised. |
| `aspectRatio` | Normalized rotated rectangle long side / short side; `null` for zero short side. Point/line contours pass when aspect bounds are disabled and fail when either bound is enabled. |
| `circularity` | `4 × π × area / perimeter²`; 0 when perimeter is 0. A well-sampled circle approaches 1. Polygon/raster approximation affects the result; the formula is returned without clamping. |
| `solidity` | Polygon area / convex hull area from `Imgproc.convexHull`; 0 for zero hull area. Convex grout-like regions can have solidity 1. |
| `extent` | Polygon area / axis-aligned raster bounding rectangle area; 0 for a zero rectangle area. Inclusive raster widths mean even a perfect rectangle is slightly below 1. |
| `touchesImageBoundary` | True when the contour's raster bounds reach x=0, y=0, x=width−1 or y=height−1. No tolerance or inferred physical-object contact is added; Canny may omit a boundary at the image edge. |
| `orientationAxis.start/end` | Java-calculated source-coordinate endpoints through the rotated rectangle centre, separated by its long-side length; `null` for a point. The centroid marker independently uses `centre`. |

The image origin is its **top-left pixel**, X increases to the right and Y increases downward. A horizontal long axis has angle 0°, a vertical one 90°. Equivalent directions 180° apart are the same axis. A square has no unique long side; its reported side orientation is ambiguous and may change with pixel noise. A line has height zero and a defined axis; a single point has no orientation (`null`). Rotated corners may be fractional or extend slightly beyond image boundaries. A zero-area contour uses the safe centroid fallback. Polygon measurements on broken/open edges can be misleading because contour measurement closes the curve; the application does not infer whether the shape is paper or a towel.

## Configuration and resource limits

Defaults live in `src/main/resources/application.properties`. Spring Boot command-line arguments or environment variables can override them, for example `--vision.min-contour-area=250` or `VISION_BLUR_KERNEL=7`. Invalid startup defaults or nonpositive budgets stop application startup.

| Property | Default | Purpose / validation |
| --- | --- | --- |
| `vision.canny-low` | 50 | Default lower threshold; finite and nonnegative. |
| `vision.canny-high` | 150 | Default upper threshold; finite, at most 255, greater than low. |
| `vision.min-contour-area` | 100 | Default minimum polygon area; finite, from 0 to 16,000,000 px². |
| `vision.blur-kernel` | 5 | Default Gaussian side length; odd integer from 1 to 31. |
| `vision.max-upload-bytes` | 10485760 | Independent service byte limit (10 MiB). |
| `vision.max-pixels` | 16000000 | Maximum decoded width × height; detection and geometry are not downscaled. |
| `vision.max-contours` | 2000 | Maximum discovered contours **before filtering**; refusal avoids excessive geometry/response work. |
| `vision.max-concurrent-processing` | 2 | Native pipeline slots per JVM; excess requests receive 429 immediately. |
| `vision.preview-max-dimension` | 1600 | Longest displayed preview side; valid values 320–2048. Smaller inputs are not enlarged. |
| `vision.preview-jpeg-quality` | 88 | JPEG display quality; valid values 50–95. Does not affect detection or geometry. |
| `vision.results.directory` | `${user.home}/.local/share/vision/results` | Durable result directory; `VISION_RESULTS_DIRECTORY` selects `/var/lib/vision/results` in production. |
| `vision.results.retained` | 20 | Completed server analyses retained, valid integers 1–100. Oldest completed versions are pruned after publication. |
| `spring.servlet.multipart.max-file-size` | 10MB | Servlet limit for each uploaded file. |
| `spring.servlet.multipart.max-request-size` | 11MB | Servlet request limit including multipart overhead. |
| `spring.servlet.multipart.file-size-threshold` | 10MB | Keeps accepted uploads up to the normal file limit in memory. |
| `server.tomcat.max-part-count` | 20 | Bounded parser budget for the image plus nineteen optional processing fields (20 parts). |
| `server.address` / `server.port` | 127.0.0.1 / 8090 | Local interface / port. |

If increasing upload limits, keep the servlet limit, request limit, memory threshold and `vision.max-upload-bytes` consistent. Rejected large requests may be handled using servlet temporary storage; such files are request-scoped and cleaned by the container. Java ImageIO uses a memory stream, and OpenCV encodes/decodes in memory. Successful analyses and original encoded uploads are then persisted by `AnalysisStore` using the retention/directory limits above. Responses use `Cache-Control: no-store`; HTTP cache policy does not prevent the explicit server store or the browser's IndexedDB snapshot. Matrices, encoded buffers and contour matrices are explicitly released using `MatScope`, including failure paths. A semaphore bounds concurrent native processing; matrices are never shared between requests.

A large photograph can consume much more memory than its compressed upload: BGR copies, grayscale intermediates, contours, base64 strings and browser images all add overhead. Pixel/concurrency limits reduce load but are not a strict process memory cap. The contour-count guard runs after `findContours`, so it cannot prevent allocations made during contour discovery. Dense chains have a 1,000,000-source-point budget checked after discovery but before Java point-array conversion. Diagnostic approximations additionally have a separate 100,000-vertex budget. These limits refuse excessive requests with 422; they cannot prevent allocations already made by `findContours`. Display paths additionally have a total 100,000 simplified-vertex budget per response (422 if exceeded); increase blur, filter small contours or crop to reduce complexity. All temporary morphology kernels, convex hulls and path-simplification matrices use `MatScope`. Use smaller input images or lower budgets for constrained hardware.

## HTTP API

`POST /api/vision/process` accepts `multipart/form-data`:

| Field | Required | Type |
| --- | --- | --- |
| `image` | Yes | JPEG or PNG binary part |
| `cannyLow` | No | Finite number, default from configuration |
| `cannyHigh` | No | Finite number, default from configuration |
| `minContourArea` | No | Finite number in px², default from configuration |
| `blurKernel` | No | Odd integer, default from configuration |
| `maxContourArea` | No | Finite 0–16,000,000 px², at least minimum area; omitted disables |
| `minAspectRatio` / `maxAspectRatio` | No | Finite long/short ratios ≥1, ordered if both supplied; omitted disables each bound |
| `minCircularity` / `minSolidity` | No | Finite minima in [0,1], default 0 |
| `excludeBoundary` | No | Boolean, default false |
| `processingMode` | No | `CANNY` (default), `CANNY_CLOSED`, `MORPHOLOGY` or `SEGMENTATION` |
| `closeKernel` / `openKernel` | No | Odd integers 1–15, default 3 / 1; validated in every mode; comparison stages always computed, selected source determines measurement input |
| `foreground` | No | `BRIGHT` (default) or `DARK`, used only by segmentation |
| `kernelShape` | No | `ELLIPSE` (STANDARD default) or `RECTANGLE` (ENHANCED default), shared by closing/opening |
| `retrievalMode` | No | `EXTERNAL` (default) or `TREE` |
| `chainMode` | No | `SIMPLE` (default) or `NONE` |
| `approximationPercent` | No | Finite 0–10, default 1; epsilon as percentage of closed trace perimeter |
| `detectionMode` | No | `STANDARD` (API default, exact legacy behavior) or `ENHANCED` (automatic dashboard profile) |

```bash
curl --fail-with-body http://127.0.0.1:8090/api/vision/process \
  -F image=@/path/to/paper.jpg \
  -F cannyLow=50 -F cannyHigh=150 \
  -F minContourArea=100 -F blurKernel=5
```

Success remains HTTP 200 with `Location: /results/{uuid}` identifying the durably saved version, plus `width`, `height`, `previewWidth`, `previewHeight`, `contourCount`, `detectedContourCount`, `edgePixelCount`, `processingMillis`, `parameters`, `originalImage`, `edgesImage`, `contoursImage`, `overlayImage`, and `contours`. The four images remain `data:image/jpeg;base64,...` strings at preview dimensions: original photograph, blue raw Canny edges, green retained contours/IDs, and combined. Existing request fields and measurement fields keep their names and meanings. Additive shape fields follow the geometry table above; `parameters.filters`, `parameters.boundary`, `parameters.tracing` and `parameters.detectionMode` echo the requested controls. Canny fields remain the requested baseline; `edgeDiagnostics.cannyLow/cannyHigh` identify the actual selected thresholds.

Additional response fields:

- `edgeDiagnostics`: selected Canny thresholds, alternative trace-length/span minima, whether automatic closing was applied, the repair decision, and threshold trials with edge counts, sustained/short trace lengths and eligibility. These are deterministic heuristics, not confidence or accuracy scores. This field is null in older archives, which remain readable. The compact measurements endpoint and saved diagnostic report expose these decisions.
- `boundaryPixelCount`: source-resolution nonzero count in the active extraction mask, before filtering. `edgePixelCount` remains raw Canny, independent of the selected experiment.
- `boundaryMaskImage`: `data:image/png;base64,...` binary active-mask preview at preview dimensions. Do not use reduced mask pixels to recalculate source counts.
- `stages`: three `{id, label, image, pixelCount}` records, IDs `raw`, `closed`, `opened`. Images are bounded binary PNGs without display dilation. Counts use original resolution. These are always Canny stages, including when the active source is segmentation.
- `contourSource`: `raw`, `closed`, `opened` or `segmentation`, identifying the exact input supplying the current contour measurements. Selecting a stage in the saved diagnostic report changes the view, not this extraction source.
- `contourDiagnostics`: keyed by retained `id`; includes `sourceIndex`, `nextIndex`, `previousIndex`, `firstChildIndex`, `parentIndex`, `depth`, `hole`, `pointCount`, `approximationVertices`, `approximationEpsilon`, `approximationArea`, `approximationPerimeter`, `approximationPath`, `rotatedRectangleArea` and `rotatedExtent`. Box area is long side × short side; fit is contour area / box area, or null for a zero-area box. Low fit can expose a loose box but is not a recognition score.
- `contourDrawings`: `{id, path}` records with display-only SVG paths generated in Java. Path coordinates remain in original pixels. These paths are simplified for rendering and must not be used for measurement or filtering.

All geometry remains in the coordinate space described by `width`/`height`. For preview pixel-centre mapping use `(x + 0.5) * previewWidth / width - 0.5` and the corresponding Y formula. The SVG viewport uses pixel-edge coordinates, so its group scales source points after a half-pixel translation; it shares the photograph's exact preview dimensions and letterboxing. No geometry is recalculated in JavaScript. `processingMillis` includes header checks, decoding, threshold trials, repair guards, geometry and preview encoding, excluding multipart transfer and JSON/network delivery.

For example, compare with a conservative morphology experiment using the same request and add `-F processingMode=MORPHOLOGY -F closeKernel=3 -F openKernel=1`. To test filled bright regions instead, use `-F processingMode=SEGMENTATION -F foreground=BRIGHT`. Optional filters affect every mode; omit them for the baseline.

Expected errors return `{"status":"error","message":"..."}`: 400 for invalid images, missing parts or invalid controls; 413 for byte/pixel limits; 415 for non-multipart requests; 422 for contour complexity; 429 when processing slots are occupied. Missing static resources and unknown unmapped paths return 404 JSON rather than a processing failure. Unexpected failures are logged server-side and return a generic 500 message. Image bytes and filenames are never logged by application code.

### Read-only saved-result routes

| Route | Representation |
| --- | --- |
| `GET /results/latest` | Server-rendered latest successful result; 404 explanatory HTML when empty. |
| `GET /results/{uuid}` | Immutable saved-result page; optional `view=photo/source/raw/closed/opened/mask` and `contour=<retained-id>`. |
| `GET /api/vision/results/latest` or `/api/vision/results/{uuid}` | Metadata `id`, `savedAt`, `photoType` and complete `result`, used for dashboard restoration. |
| `GET /api/vision/results/{id}/measurements` | Compact metadata, dimensions, parameters, contours, hierarchy/shape diagnostics and ordinary image URLs; `id` accepts `latest` or a UUID. |
| `GET /results/{id}/images/{kind}` | JPEG/PNG preview: `original`, `edges`, `contours`, `overlay`, `mask`, `raw`, `closed` or `opened`. |
| `GET /results/{id}/original` | Exact validated uploaded JPEG/PNG bytes for restoration/reprocessing. |

All readers send `Cache-Control: no-store` and never rerun OpenCV. Unknown/pruned IDs and unknown images return 404. UUID validation confines lookup to the configured store. The latest alias is resolved before generating image links or loading the original, so image/result pairs remain consistent across concurrent saves. No image bytes or uploaded filenames are logged by application code.

## Architecture and tests

- `config`: validated settings and eager native loading.
- `controller`: Thymeleaf dashboard, multipart API, readiness and HTTP error mapping.
- `service`: `VisionProcessingService` orchestrates header validation, budgets and processing; `ContourBoundaries` generates masks; `ContourGeometry` calculates measurements; `PreviewRenderer` creates bounded images and display paths; `MatScope` owns native resources. `AnalysisStore` separately persists successful original/result pairs and manages immutable reads and rolling retention.
- `model`: immutable parameters, geometry and result records; no native or MVC types.
- `templates/index.html`, `static/css/vision.css`, `static/js/vision.js`, `static/js/server-results.js` and `templates/saved-result.html`: responsive light dashboard, in-page single-frame camera acquisition and fetch-based updates. JavaScript handles presentation, camera lifecycle, frame encoding, input validation and sending the selected image; edge/contour processing remains in Java. The camera sends the captured `image` and `detectionMode=ENHANCED` for automatic refinement of the configured baseline. Upgrade/publication of a legacy successful snapshot additionally sends that analysis's saved baseline parameters. The dashboard renders only source contour paths, without IDs or geometric annotations.

The automated tests generate fixture images directly in Java/OpenCV. They cover analytic rectangles, sampled circles, rotated/narrow objects, concave shapes, all image borders, point/line degeneracy, PNG/JPEG input, empty/invalid input, blank images, multiple objects, area/aspect/circularity/solidity/border filters, inclusive bounds and invalid combinations. A synthetic scene includes a border-spanning straight background line and separate compact/narrow foreground objects: assertions verify configured criteria, including that convex background regions can pass solidity filtering. No test assumes all grout can be rejected.

Morphology tests compare different closing/opening kernels, explicitly demonstrate merging and thin-edge loss, verify raw-mask independence, exercise Otsu polarity, and check source mask/count consistency. API tests cover legacy fields/defaults plus additive controls and invalid enums/numbers. A real embedded-server HTTP test submits all twenty multipart parts, verifying the parser limit that MockMvc bypasses. Existing byte/pixel/contour budgets and slot-release tests remain. Overlay assertions allow JPEG colour tolerance and preserve photographic background. A 12-megapixel textured JPEG fixture verifies bounded 1600-pixel previews, unchanged geometry, a preview payload under 8 MB and visible edges/contours on a phone-sized view. Additional tests compare display paths at different preview resolutions and verify Java-calculated axis endpoints.

```bash
./mvnw test
./mvnw clean verify javadoc:javadoc
```

Surefire reports are in `target/surefire-reports`; generated API documentation is under `target/reports/apidocs`. Javadoc is configured to fail on warnings. No downloaded test photographs or external services are needed. API and service tests verify the real bundled native library, rather than mocking OpenCV.

The optional frontend regression checks use Node.js 20+ built-in modules only (no npm packages or application/runtime dependency):

```bash
node src/test/javascript/camera.test.cjs
node src/test/javascript/retention.test.cjs
node src/test/javascript/overlays.test.cjs
node src/test/javascript/server-results.test.cjs
```

These checks simulate browser APIs without a physical camera. Camera coverage includes automatic single submission, server defaults, duplicate protection, cancellation, late permission release, permission denial, HTTPS, background cleanup and frame readiness. Overlay coverage checks all four checkbox combinations, source-to-preview mapping, path-only rendering, no extra image copies or processing requests, legacy preferences and empty contour results.

Retention and server-reader checks cover restoration without reprocessing, both-off persistence, cancelled captures, failed/invalid/undecodable replacements, unavailable storage, immutable IDs, one-time legacy publication and STANDARD-to-ENHANCED upgrades using saved baseline parameters, failed upgrades preserving prior results, pending-photo separation and late restore/decode races. The Java suite covers real native processing and durable storage; the dashboard contract verifies exactly two checkboxes and the absence of the removed controls. Browser verification must additionally check real IndexedDB, a fresh browser, camera acquisition and responsive desktop/mobile layout. Synthetic tests demonstrate morphology failure modes; they do not establish accuracy for every photograph.

Edge/contour refinement verification: **69 Java tests** and **all four JavaScript suites** pass, along with Maven packaging and warning-checked Javadoc. Native fixtures additionally cover cropped zero-area boundaries, weak rectangles, noisy threshold refusal, guarded gap repair, unintended joins, excessive repair pixels, complexity-slot release, rotated geometry and old archive compatibility. Headless Chrome checks use a simulated camera device with real JPEG capture and Java/OpenCV processing in an isolated result store. They verify exactly three main controls, all four overlay combinations without additional requests, label-free paths, IndexedDB restoration, failed replacement preservation, fresh-browser server restoration and desktop/mobile layout without page errors. Synthetic checks use isolated state and never replace production latest. Physical phone-camera permissions and negotiated sensor resolution remain device-dependent.

In isolated comparisons using the exact saved originals, the panel photograph retained 21 traces instead of 1 and recovered much of the previously missing top edge; the floor photograph retained 69 instead of 13. Both selected 35/105 instead of baseline 50/150. Automatic closing was refused for both because external trace counts changed. These counts include background detail and grout; increases are not object-detection accuracy measurements. Processing in these individual runs was approximately 0.27–0.39 seconds for ENHANCED versus 0.21 seconds for STANDARD, excluding upload/network transfer; these are observations on this host, not latency guarantees. Neither private photograph is committed or used as an automated test fixture.

## Deployment to test.letsoisa.co.za

The deployed application is available at **https://test.letsoisa.co.za/**. The established workflow below builds and installs the application, restarts the service and verifies readiness and HTTPS access according to the selected authentication mode. Service units, TLS, upstream binding and the shared Authelia service are retained. The Vision-specific authentication choice persists across application deployments and certificate reloads.

The configured deployment target is this Ubuntu host, using **Nginx → 127.0.0.1:8090 → vision.service**. Public access is **https://test.letsoisa.co.za/**. Nginx redirects HTTP to HTTPS, serves ACME challenges from `/var/www/letsencrypt`, and uses the existing domain certificate at `/etc/letsencrypt/live/test.letsoisa.co.za/`. Certbot's existing renewal job is retained; `deploy/renew-certificate.sh` is installed as its domain-specific deploy hook to validate/reload Nginx after renewal.

`deploy/nginx.conf` retains the existing Authelia integration and `/login/` route, but its application location includes `/etc/nginx/snippets/vision-auth.conf`. This root-owned symlink selects either `vision-auth-enabled.conf` (the existing auth request and login redirect) or `vision-auth-disabled.conf` (`auth_request off`). With authentication disabled, the dashboard, API and readiness are publicly reachable over HTTPS. With it enabled, all application routes require sign-in. The hostname's existing `one_factor` rule and cookie remain available for re-enabling; no shared Authelia configuration, user accounts or other applications' policies are modified. Nginx permits an 11 MiB multipart request, while the application retains its 10 MiB image limit. Request buffering is disabled, and upstream processing timeouts are 120 seconds. Forwarded headers are handled by Spring only on the localhost-bound service.

### Manually changing Vision authentication

From the repository directory:

```bash
./deploy/set-auth.sh status
./deploy/set-auth.sh enable   # Require Authelia again.
./deploy/set-auth.sh disable  # Keep Vision public until manually enabled.
```

The helper uses sudo for the root-owned snippet link and Nginx reload. It changes only Vision, validates Nginx before reload and restores the previous link if validation or reload fails. Its HTTPS check requires a login redirect when enabled, or HTTP 200 plus public readiness when disabled. There is no timer that re-enables authentication. Deployment installs both snippet templates and preserves the selected symlink; a newly provisioned host defaults to enabled. Inspect `status` after changing the mode. The helper requires the snippets installed by the normal deployment workflow. This uses Nginx's documented [`auth_request off`](https://nginx.org/en/docs/http/ngx_http_auth_request_module.html) switch; the Authelia service itself keeps running.

`deploy/vision.service` runs `/opt/vision/vision.jar` using the system Java 21 executable `/usr/bin/java`, as the dedicated unprivileged `vision-app` user. The service starts on boot, restarts on failure, and has a private temporary directory with a read-only system/home view. Systemd owns its writable state/cache directories, `/var/lib/vision` and `/var/cache/vision`, with mode 0700. Native libraries extract to `/var/cache/vision/javacpp`; no user home or source checkout is required at runtime. The Java heap limit is 768 MiB; OpenCV native allocations are additional memory. Successful original photographs and their analysis JSON are durably stored in `/var/lib/vision/results`, selected by the unit's `VISION_RESULTS_DIRECTORY` environment variable. Deployments replace the JAR and preserve this state.

### Initial host provisioning

The DNS record and valid TLS certificate must already exist on a new target. Install Java 21, the GTK/OpenGL runtime packages above, Nginx, Certbot and the existing Authelia integration before enabling this configuration. On this host, Authelia runs in the `authelia` Docker container with configuration under `/root/authelia/config`. The following one-time helper adds only the new hostname to the existing normal-app rule and cookie list, preserving all other configuration text and backing up the original with its permissions:

```bash
sudo perl deploy/configure-auth.pl
sudo docker exec authelia authelia validate-config --config /config/configuration.yml
# Restart only after validation succeeds.
sudo docker restart authelia
```

The helper is specific to this host's current configuration layout and refuses to write when its expected insertion points are absent. Re-running it makes no changes when both entries already exist. It does not modify the authentication user database. If validation fails, restore the printed `.before-vision-*` backup before restarting Authelia. A container restart briefly affects sign-in for the other hosted applications.

### Routine deployment

From the project directory, run:

```bash
./deploy/deploy.sh
```

The script runs Maven tests/build and warning-checked Javadoc, verifies the domain certificate is present, creates the service account if needed, installs the executable JAR atomically, checks that its bytes match the build artifact, installs/enables/restarts `vision.service`, and waits for successful application readiness. It installs the authentication snippets while preserving the selected mode, installs the hostname's Nginx configuration, runs `nginx -t`, reloads Nginx, and installs the certificate-renewal hook. `check-https.sh` then verifies the selected mode: a sign-in redirect when enabled, or HTTP 200 and public readiness when disabled. It leaves a previously deployed JAR at `/opt/vision/vision.jar.previous`. No commit or push is performed.

The first deployment must stop any manually launched Vision process using port 8090 before starting the service. Future deployments use the service workflow directly. Service readiness failures stop deployment before activating the Nginx configuration; inspect logs and restore the previous JAR if appropriate. A failed Nginx configuration test prevents reloading; the running workers continue using their previous configuration. Correct the staged file before a later reload.

### Crawler access and browsing-tool diagnosis

`GET /robots.txt` and `HEAD /robots.txt` return HTTP 200 with a plain-text policy allowing public routes. This policy follows the existing public-site configuration; it does not bypass Nginx authentication when Authelia is enabled. Saved photographs and results remain accessible to anyone with site access, as described above. A crawler policy is advisory, not authorization.

A missing-resource exception previously passed through the generic processing-error handler, causing `/robots.txt` and other nonexistent resources to return HTTP 500. The corrected handler returns 404 for genuinely missing resources, while the explicit robots file returns 200. This matters because [RFC 9309 section 2.3.1.4](https://www.rfc-editor.org/rfc/rfc9309.html#section-2.3.1.4) requires crawlers to assume complete disallow when robots retrieval has a server/network error. The robots error is a plausible contributor to browsing failures; a tool's generic access error alone cannot establish its exact cause. Cached crawler failures may also outlive a server correction.

To diagnose an inaccessible site without guessing at proxy changes, compare direct HTTPS with the browser tool using a uniquely labelled query, then correlate timestamps in the Vision access/error logs. Verify public DNS with an independent resolver, TLS certificate/chain validation, TLS 1.2/1.3, effective authentication mode, and host firewall rules. If mobile data works and direct HTTPS succeeds, do not infer a general outage or remove working access controls. Requests absent from the access log may fail before HTTP, including upstream routing, TLS or client-side fetch policy. A direct request carrying a crawler User-Agent can check application filtering but does not reproduce the crawler's network path.

Regression coverage checks robots GET/HEAD and MIME type, plus 404 responses for missing pages, images and API paths. These routing checks remain in the current 69-test Java suite; authentication selection is unchanged.

### Operations and verification

```bash
sudo systemctl status vision.service --no-pager
sudo journalctl -u vision.service -n 50 --no-pager
curl --fail http://127.0.0.1:8090/api/vision/health
sudo nginx -t
curl --head https://test.letsoisa.co.za/
```

The internal readiness response must contain `status=ready` and the OpenCV version. Use `./deploy/set-auth.sh status` to determine the intended public response: HTTP 200 when disabled, or HTTP 302 to `https://test.letsoisa.co.za/login/…` when enabled. `/login/` retains the existing login route. Validate camera capture in a browser after changes affecting acquisition or processing; use an isolated result store for synthetic checks so production latest is preserved. Nginx logs are `/var/log/nginx/test.letsoisa.co.za.access.log` and `.error.log`. Port 8090 remains bound to loopback; the Vision-specific Nginx snippet controls whether Authelia is required on the public hostname.

To roll back an application deployment when a previous JAR exists:

```bash
sudo cp /opt/vision/vision.jar.previous /opt/vision/vision.jar.next
sudo mv /opt/vision/vision.jar.next /opt/vision/vision.jar
sudo systemctl restart vision.service
curl --fail http://127.0.0.1:8090/api/vision/health
```

Deploy configuration changes can require restoring the matching service/Nginx files from the prior project version as well. Validate Nginx before reloading, and run `systemctl daemon-reload` before restarting when the unit changes.

## Known limitations and references

- No recognition: every retained boundary is a contour, including shadows, folds or background detail.
- Textured fabric and tiled floors create many edges. Geometry filters select measured contours; they cannot reliably separate background lines from a pen or recover an occluded physical boundary. Excluding high aspect ratios can also remove genuine narrow objects.
- Canny traces may be incomplete, self-touching or open in the physical scene. OpenCV calculates a closed polygon area/perimeter for each returned trace; the reported geometry is not a claim of a complete object region. Morphology can merge objects and grout or erase useful boundaries. Global brightness segmentation is an optional experiment with its own lighting/contrast limitations.
- TREE exposes holes and nesting without assigning object identities or combining independent traces into physical objects. Fitted background-line removal and automatic object/sheet selection remain outside this phase. Diagnostic approximation and display simplification do not change measured source contours.
- No calibration: perspective, camera angle and lens distortion affect pixel geometry; physical lengths/areas require later calibration.
- EXIF orientation is intentionally ignored in processed results. Transparent PNG is decoded to BGR, so transparency is not preserved. Animated/multipage images are not supported as sequences.
- Native image decoders may tolerate some truncated images; header and decoded dimensions plus decode success are checked, but this is not an exhaustive file-integrity validator.
- Photograph previews are bounded, lossy JPEG images; extraction masks are bounded binary PNGs. The main dashboard shows a bounded preview, with no full-resolution export control; retrieve exact original bytes from the saved-result endpoint when needed. Use structured measurements for source-resolution geometry. Thickened strokes and simplified display paths are visual aids. Base64 data URLs add transfer overhead. Successful photo/analysis pairs also survive browser storage loss through server retention. Local checkbox choices are independent across browsers; a fresh browser automatically restores the latest completed server result. The latest alias is shared across all site visitors and can change after another processing request. Use a UUID link for a particular experiment; older links expire when rolling retention prunes them.
- Automated synthetic fixtures test known geometry and failure modes; they do not establish accuracy for every real photograph of a sponge, circular object or pen on a tiled floor. The described test photograph has not been supplied as a fixture.

See the [OpenCV Java Imgproc API](https://docs.opencv.org/4.x/javadoc/org/opencv/imgproc/Imgproc.html), [shape descriptors](https://docs.opencv.org/4.x/d3/dc0/group__imgproc__shape.html), [Canny explanation](https://docs.opencv.org/4.x/da/d22/tutorial_py_canny.html), [morphological operations](https://docs.opencv.org/4.x/d9/d61/tutorial_py_morphological_ops.html), [Bytedeco native packaging](https://github.com/bytedeco/javacpp-presets) and [Bootstrap documentation](https://getbootstrap.com/docs/5.3/). Tutorial language does not imply a Python dependency; this application uses Java throughout.
