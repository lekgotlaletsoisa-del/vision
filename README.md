# Vision Lab — Phase 2: contour experiments

Vision Lab is a Java laboratory for studying OpenCV edge detection, contour quality, hierarchy and image geometry. Capture a single camera frame in the page or upload an existing image, then view edges and contours directly over the photograph. Optional measurements remain available below the image. It uses traditional OpenCV in Java throughout. It does not classify objects, use machine learning, continuously process camera frames or control sewing hardware.

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

1. Select **Take photo** to open the in-page camera, then tap the **Capture & process** shutter. That frame is immediately used and processed with the current settings; there is no photo acceptance or retry confirmation. Closing the camera without taking a photo preserves existing results. The browser may request camera access the first time. To use an existing image, expand **Or upload an image** and choose a JPEG or PNG; select **Process image** when ready. The API validates actual content rather than trusting the extension or MIME type.
2. Adjust **Canny low/high**. Expand **More controls** for the existing **minimum area** (default 100 px²) and **blur kernel** (default 5). Raw Canny remains the default contour source.
3. Select **Process image** to see **blue raw Canny edges** and **green retained contours**. The six existing overlay checkboxes—**Edges**, **Contours**, **Boxes**, **Centroids**, **IDs** and **Long axes**—start checked and are enabled after each successful processing request. Toggle them independently: boxes are amber, centroids magenta and long axes orange. These presentation changes need no processing request. **Original only** clears every overlay; the photograph remains available at all times. Select one contour in the dropdown to isolate its annotations; raw blue edges still show the entire Canny result. The optional **Approximation** checkbox adds a purple Douglas–Peucker polygon. **Open image** opens a self-contained SVG of the current photograph/mask and selected annotations. On phones the page scrolls to the result after processing.
4. Expand **Measurements** for geometry and shape descriptors. Expand **4 corners** for rotated rectangle vertices. IDs match the labels and are assigned from largest to smallest area (with top/left tie breaks), separately for each processing request. The toolbar shows **retained / detected** counts; detected means all contours in the active extraction mask under the selected retrieval mode before geometry filters. Selection isolates the measurement row as well as the overlays. Selection resets after each successful request so an old ID cannot silently refer to a different trace.
5. Expand **Contour filters** to set optional maximum area, minimum/maximum aspect ratio, minimum circularity, minimum solidity, or border exclusion. Blank area/ratio fields disable their bounds; circularity/solidity minima of zero disable those filters. Border exclusion is **off** by default. All bounds are inclusive and use original-resolution measurements. Filters do not modify raw edges or the extraction mask. Adjust one criterion at a time; there is no automatic background rejection preset.
6. Expand **Boundary experiment** to select **Canny after closing only**, **Experimental: edge closing + opening** or **Experimental: brightness regions**, then reprocess. Closing and opening use odd kernels from 1–15 source pixels; 1 disables that operation. Select **Ellipse** (existing default) or **Rectangle**. Closing defaults to 3 and opening to 1. The Canny stage previews are computed in every mode, but raw Canny measurements remain untouched. Choose **Canny after closing only** to measure stage 3 or **Experimental: edge closing + opening** to measure stage 4. Brightness foreground applies only to the brightness-region experiment. Use **Active extraction mask** to inspect the actual binary input to contour extraction, without annotations.
7. Use **Compare raw Canny** to reprocess the same image with the same blur, thresholds and filters but without the experiment. It does not change the chosen experiment in the form: **Reprocess image** returns to that selection. This remeasures raw Canny; inspect the result label to see which source produced the displayed measurements. For a mask-only comparison, the stage selector switches instantly between the original photograph, raw Canny, closing, opening and final filtered geometry without another request. The button is disabled before a photo/result is available. Changed controls show a reprocess reminder; failed requests leave the previous successful result visibly identified.
8. Expand **Contour tracing** to compare `RETR_EXTERNAL` / `RETR_TREE`, `CHAIN_APPROX_SIMPLE` / `CHAIN_APPROX_NONE`, and approximation epsilon (0–10% of closed perimeter). Reprocess to change these calculations. The measurements include rotated-box area, contour-to-box fit, nesting links, stored point count and approximation geometry. Existing filters always use the source trace, independently of the approximation.

Camera capture uses [`getUserMedia`](https://www.w3.org/TR/mediacapture-streams/) with a preferred rear-facing camera and `audio: false`. A local, muted inline video preview is displayed in a light dialog; only the frame selected by the shutter is sent to Java. The native camera application/file-capture route is no longer used, so its mandatory OK/retry screen is avoided. Browser permission is still required and cannot be bypassed. If permission is denied, camera access is unsupported, or the camera is busy/unavailable, an explanatory error is shown and upload remains available.

The camera requests a 2560 × 1920 frame where supported, with maximum width/height constraints of 3840 × 2880. The actual negotiated frame dimensions can differ. The shutter copies that frame, without filters or resizing, into a temporary canvas and requests JPEG encoding at quality 0.92. This is image acquisition/transport only: grayscale, blur, Canny, morphology, segmentation, contours and geometry run exclusively in Java. Java generates preview images and contour paths; JavaScript places the supplied geometry in SVG without calculating new measurements or boundaries. Camera measurements use the captured video-frame pixels, which can differ from the resolution of the phone's native still-photo sensor output. The JPEG/PNG bytes use the same multipart endpoint and existing size/pixel limits. No video or audio stream is sent to the server.

Camera tracks are stopped immediately after capture, when the dialog closes or is cancelled, when the page is hidden/left, and when a pending permission request resolves after cancellation. The shutter remains disabled until a usable frame is ready and prevents duplicate captures while encoding. Captured frames are automatically submitted once. Unsupported browsers use the existing upload option rather than falling back to a native capture flow that would restore the confirmation screen.

### Keeping the latest photo and analysis

The latest accepted photo, last successful analysis, processing controls and display options are saved in **this browser's IndexedDB**, under this site's origin. Refreshing, closing and reopening the page restores them without uploading again or recomputing measurements. A single snapshot is replaced; there is no photo history or automatic expiry. The original encoded photo remains available for reprocessing. Camera cancellation, invalid replacement images and processing failures preserve the last successful analysis.

Saved sessions from before all six overlays were enabled by default receive those defaults once. Subsequent manual checkbox changes are remembered when reopening the page; a successful new processing request enables all overlays again. The optional border-exclusion filter keeps its existing default of off.

For Phase 2 results, reopening restores the selected processing stage, contour ID, approximation overlay and tracing/kernel settings without reprocessing. Older saved results keep their existing final-analysis restoration behavior; reprocess once to generate the new stage masks and diagnostics. Individual overlay choices are retained; if all six original overlays were off, they are enabled again on restoration. Successful processing selects **Final filtered contours + geometry** and resets selection to all retained contours. The selected-contour summary exposes area, rotated-box fit, centroid, angle and trace nesting without horizontal table scrolling.

A valid new photo replaces the saved source immediately. Until processing succeeds, **Original / current photograph** shows that new photo; **Final filtered contours + geometry** and **Extraction mask** continue to show the previous analysis, and a note identifies its measurements as belonging to the previous photo. A successful result replaces the stored analysis and clears that note. Restoring state never starts the camera or automatically runs analysis. Changing controls saves the chosen settings; measurements still describe the settings echoed in their last result until reprocessing.

`session-store.js` stores the original Blob and the JSON response atomically as one record in database `vision-lab`, store `session`, key `latest`. `vision.js` serializes writes, validates restored photos against the existing upload/pixel budgets and prevents a late restore from overwriting a newly selected photo. Blob URLs are regenerated after reload; they are not persisted. If storage is blocked or full, processing still works and the retention message reports that changes remain in the current page only; a failed write leaves the previous saved snapshot intact.

Retention belongs to the browser profile and origin, not a server account, and is not shared between visitors or devices. Clearing site data, browser eviction or closing a private-browsing session can remove it. The server still does not store uploaded photographs or generated previews permanently. To remove the retained snapshot, clear site data for `test.letsoisa.co.za` in browser settings. See [IndexedDB's storage model](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API/Using_IndexedDB).

Detection and geometry run at original resolution. A separate Java renderer creates bounded previews, with thick blue Canny marks for visibility. Blue includes edges removed by geometry filters and always represents **raw Canny**, even in experimental modes. Green contours represent the active mode's retained boundaries. SVG uses the returned preview dimensions and the same contained image viewport, with source pixel centres mapped into preview pixels. Rotated vertices, centroids and axis endpoints come directly from Java. Selected-contour paths are simplified in Java within 0.75 preview pixels for display only; area, perimeter, hulls and filters use the unsimplified source contours. Display thickness and simplification are never measurement inputs. The four existing JPEG API previews, including baked contour labels, remain available for compatibility. No detected/retained contours is a valid result. The browser verifies result-image decoding before replacing the source preview.

## Pipeline and controls

Processing lives in `VisionProcessingService`, separate from MVC controllers:

1. Reject empty/oversized uploads. Java ImageIO inspects JPEG/PNG format and dimensions in memory **before native decoding**; it does not perform image processing or save a file.
2. Decode in OpenCV to an 8-bit BGR image at the original dimensions. `IMREAD_IGNORE_ORIENTATION` deliberately ignores EXIF rotation; every returned preview shares the same orientation and coordinate mapping. A browser's initial source preview may honor metadata rotation until the server result replaces it.
3. `Imgproc.cvtColor(..., COLOR_BGR2GRAY)` converts BGR to grayscale.
4. `Imgproc.GaussianBlur` smooths local intensity noise using an odd square kernel, with sigma inferred by OpenCV. A kernel of 1 has no smoothing effect.
5. `Imgproc.Canny` detects intensity boundaries with a 3-pixel Sobel aperture and `L2gradient=true` (Euclidean gradient magnitude). The resulting mask has white edges and a black background.
6. Generate raw, closed and optionally opened Canny masks. Enabled operations create a separate output; size 1 shares its unchanged input without a full-resolution copy. Default `CANNY` extracts from raw edges; `CANNY_CLOSED` selects closing only; `MORPHOLOGY` selects closing followed by opening. `SEGMENTATION` separately thresholds blurred grayscale using Otsu (bright or dark foreground), then applies the selected morphology. Its active mask is separate from the Canny comparison stages. Raw edges are preserved in every mode.
7. `Imgproc.findContours` uses the selected retrieval and chain modes on the active mask. Defaults remain `RETR_EXTERNAL` and `CHAIN_APPROX_SIMPLE`. The current OpenCV version preserves the source mask, so no defensive image clone is required. Capture the full hierarchy before sorting/filtering. Connectivity in an edge mask does not establish that it encloses a physical object.
8. Measure source-resolution geometry for discovered contours, then apply every enabled inclusive filter. Existing minimum area defaults to 100 px²; zero allows degenerate contours. Detected count is recorded before filtering; retained count is after filtering. Sort retained results by decreasing area with top/left tie breaks and assign IDs.
9. `PreviewRenderer` creates the four existing JPEG photograph previews (original, raw edges, retained contours/IDs and combined), binary PNG previews of the active extraction mask and each Canny comparison stage, and display-only SVG contour paths. Separate `approxPolyDP(..., closed=true)` diagnostics use a source-coordinate epsilon equal to the selected percentage of closed perimeter; these never replace source measurements or filters. Longest preview side defaults to 1600 pixels, without upscaling. Area interpolation plus a nonzero threshold preserves visible contributions from thin source masks during reduction; the edge photograph additionally thickens marks for display. The extraction-mask preview is not dilated, although reduced pixels can combine source contributions. Source masks, measurements and filters remain unchanged.
10. Return immutable Java records as JSON, with bounded previews, source/preview dimensions, detected/retained counts, raw edge and active-mask pixel counts, actual parameters and duration. JPEG quality defaults to 88. No native matrices escape the service.

Canny's **high threshold** selects strong gradient seeds. Edges between the **low and high thresholds** survive only when connected to strong edges through hysteresis. Values below the low threshold are discarded. Lower thresholds are more sensitive to faint edges and texture; higher ones can lose genuine low-contrast boundaries. These are gradient thresholds, not brightness cutoffs. The UI intentionally limits them to `0 ≤ low < high ≤ 255` for this phase; Canny gradients themselves are not restricted to an 8-bit intensity range.

Increasing the blur kernel suppresses noise and fine texture, but shifts/removes detail. Increasing minimum area filters small polygon boundaries after edge detection; it does not change the edge image. Use high contrast, a plain background and a reasonably overhead photograph as an initial experiment.

### Interpreting boundary experiments

Closing (dilation then erosion) can reconnect short gaps; a larger kernel can also join neighboring objects, grout and shadows. Opening (erosion then dilation) can remove isolated noise from filled masks but often erases one-pixel Canny edges altogether. Neither operation guarantees a better boundary. Kernel sizes are source pixels, so the same kernel has different relative effects on differently sized photographs. Keep raw Canny as the baseline. Compare closing 1, 3 and 5 with opening 1, then inspect each mask before increasing sizes. Size 1 means no operation, not a weaker operation. `Imgproc.getStructuringElement()` supplies a rectangular or elliptical footprint; `Imgproc.morphologyEx(..., MORPH_CLOSE)` performs closing. Opening explicitly calls `Imgproc.erode()` then `Imgproc.dilate()` with the same footprint and one iteration, matching `MORPH_OPEN`. All use OpenCV's default anchor and morphology border policy.

Canny detects intensity changes, not complete object regions. Closed polygon measurements of a broken edge trace can be misleading. The separate brightness-region experiment provides an explainable **region segmentation** alternative when foreground/background brightness separates clearly. Otsu uses one global grayscale threshold: differently colored objects with similar brightness, lighting gradients, texture and grout may still fragment or merge. Choosing the wrong foreground polarity may identify the floor as one border-touching region. Keep border exclusion optional and inspect that failure rather than hiding it automatically. None of these modes identifies shoes, small objects or floor tiles semantically.

### Structuring elements, tracing and approximation

A rectangular kernel includes every pixel in its square footprint, including the corners. It often bridges horizontal/vertical gaps readily. An ellipse omits corner pixels and has a rounded footprint; small digital ellipses are only rough approximations of circles. Neither is universally preferable: kernel orientation, gap width, noise thickness and grout spacing all matter. The synthetic fixtures show both shapes merging separate objects under excessive closing, and different corner retention under opening.

`RETR_EXTERNAL` returns outermost traces and is useful for a baseline with fewer duplicate outlines. `RETR_TREE` returns nested boundaries with parent/child/sibling relationships. Canny often creates outlines on both sides of a bright region, so TREE can increase the count without finding more physical objects. Each hierarchy link uses the original **zero-based extraction index**, with -1 for absent links. These indices differ from the area-sorted **one-based result ID**. Links can refer to traces removed by filters. Odd nesting depth is labelled a hole trace in this mask; the area shown for each trace remains its independent polygon area and does not subtract children.

`CHAIN_APPROX_SIMPLE` compresses straight horizontal, vertical and diagonal runs. `CHAIN_APPROX_NONE` retains each boundary step and can use much more memory. For the same mask these storage choices should preserve geometric measurements; neither closes a missing physical boundary. The dashboard reports stored point count to make the difference visible.

`approxPolyDP()` creates a diagnostic polygon. A larger epsilon generally removes more vertices, but can alter area, perimeter, corners and narrow details. Purple approximation paths and their own area/perimeter/vertex count can be compared with the green trace and amber rotated box. Epsilon 0 retains the zero-tolerance polygon, which may still eliminate redundant collinear points. Filtering and primary measurements always use the original returned trace. A closed polygon convention in these APIs is not a test of physical boundary completeness: an open edge may be followed out and back, producing near-zero polygon area. No automatic “complete object” label is assigned.

### Suggested shoe-and-floor experiment

Keep the same retained photograph, thresholds, blur and filters while changing one parameter at a time:

1. Process raw Canny with closing 1/opening 1 and inspect the original, raw mask and final geometry. Select a suspect contour, disable **Edges** if necessary to isolate it, and compare its green trace with the amber box.
2. Set closing 3/opening 1, reprocess, and switch between raw and closed masks. Try ellipse and rectangle separately. Select **Canny after closing only** to measure changes in continuity and box fit. A lower contour count alone is not evidence of improvement.
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
| `spring.servlet.multipart.max-file-size` | 10MB | Servlet limit for each uploaded file. |
| `spring.servlet.multipart.max-request-size` | 11MB | Servlet request limit including multipart overhead. |
| `spring.servlet.multipart.file-size-threshold` | 10MB | Keeps accepted uploads up to the normal file limit in memory. |
| `server.tomcat.max-part-count` | 20 | Bounded parser budget for the image plus eighteen optional processing fields (19 parts). |
| `server.address` / `server.port` | 127.0.0.1 / 8090 | Local interface / port. |

If increasing upload limits, keep the servlet limit, request limit, memory threshold and `vision.max-upload-bytes` consistent. Rejected large requests may be handled using servlet temporary storage; such files are request-scoped and cleaned by the container. The server has no permanent image-storage feature. Java ImageIO uses a memory stream, and OpenCV encodes/decodes in memory. Responses use `Cache-Control: no-store`; server-side source bytes/results live only for the request. The browser explicitly retains one snapshot in IndexedDB as described above; HTTP cache policy does not prevent application-managed storage. Matrices, encoded buffers and contour matrices are explicitly released using `MatScope`, including failure paths. A semaphore bounds concurrent native processing; matrices are never shared between requests.

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
| `kernelShape` | No | `ELLIPSE` (default) or `RECTANGLE`, shared by closing/opening |
| `retrievalMode` | No | `EXTERNAL` (default) or `TREE` |
| `chainMode` | No | `SIMPLE` (default) or `NONE` |
| `approximationPercent` | No | Finite 0–10, default 1; epsilon as percentage of closed trace perimeter |

```bash
curl --fail-with-body http://127.0.0.1:8090/api/vision/process \
  -F image=@/path/to/paper.jpg \
  -F cannyLow=50 -F cannyHigh=150 \
  -F minContourArea=100 -F blurKernel=5
```

Success remains HTTP 200 with `width`, `height`, `previewWidth`, `previewHeight`, `contourCount`, `detectedContourCount`, `edgePixelCount`, `processingMillis`, `parameters`, `originalImage`, `edgesImage`, `contoursImage`, `overlayImage`, and `contours`. The four images remain `data:image/jpeg;base64,...` strings at preview dimensions: original photograph, blue raw Canny edges, green retained contours/IDs, and combined. Existing request fields and measurement fields keep their names and meanings. Additive shape fields follow the geometry table above; `parameters.filters`, `parameters.boundary` and `parameters.tracing` echo the actual extra controls.

Additional response fields:

- `boundaryPixelCount`: source-resolution nonzero count in the active extraction mask, before filtering. `edgePixelCount` remains raw Canny, independent of the selected experiment.
- `boundaryMaskImage`: `data:image/png;base64,...` binary active-mask preview at preview dimensions. Do not use reduced mask pixels to recalculate source counts.
- `stages`: three `{id, label, image, pixelCount}` records, IDs `raw`, `closed`, `opened`. Images are bounded binary PNGs without display dilation. Counts use original resolution. These are always Canny stages, including when the active source is segmentation.
- `contourSource`: `raw`, `closed`, `opened` or `segmentation`, identifying the exact input supplying the current contour measurements. Selecting a stage in the browser changes the view, not this extraction source.
- `contourDiagnostics`: keyed by retained `id`; includes `sourceIndex`, `nextIndex`, `previousIndex`, `firstChildIndex`, `parentIndex`, `depth`, `hole`, `pointCount`, `approximationVertices`, `approximationEpsilon`, `approximationArea`, `approximationPerimeter`, `approximationPath`, `rotatedRectangleArea` and `rotatedExtent`. Box area is long side × short side; fit is contour area / box area, or null for a zero-area box. Low fit can expose a loose box but is not a recognition score.
- `contourDrawings`: `{id, path}` records with display-only SVG paths generated in Java. Path coordinates remain in original pixels. These paths are simplified for rendering and must not be used for measurement or filtering.

All geometry remains in the coordinate space described by `width`/`height`. For preview pixel-centre mapping use `(x + 0.5) * previewWidth / width - 0.5` and the corresponding Y formula. The SVG viewport uses pixel-edge coordinates, so its group scales source points after a half-pixel translation; it shares the photograph's exact preview dimensions and letterboxing. No geometry is recalculated in JavaScript. `processingMillis` includes header checks, decoding, geometry and preview encoding, excluding multipart transfer and JSON/network delivery.

For example, compare with a conservative morphology experiment using the same request and add `-F processingMode=MORPHOLOGY -F closeKernel=3 -F openKernel=1`. To test filled bright regions instead, use `-F processingMode=SEGMENTATION -F foreground=BRIGHT`. Optional filters affect every mode; omit them for the baseline.

Expected errors return `{"status":"error","message":"..."}`: 400 for invalid images, missing parts or invalid controls; 413 for byte/pixel limits; 415 for non-multipart requests; 422 for contour complexity; 429 when processing slots are occupied. Unexpected failures are logged server-side and return a generic 500 message. Image bytes and filenames are never logged by application code.

## Architecture and tests

- `config`: validated settings and eager native loading.
- `controller`: Thymeleaf dashboard, multipart API, readiness and HTTP error mapping.
- `service`: `VisionProcessingService` orchestrates header validation, budgets and processing; `ContourBoundaries` generates masks; `ContourGeometry` calculates measurements; `PreviewRenderer` creates bounded images and display paths; `MatScope` owns native resources.
- `model`: immutable parameters, geometry and result records; no native or MVC types.
- `templates/index.html`, `static/css/vision.css`, `static/js/vision.js`: responsive light dashboard, in-page single-frame camera acquisition and fetch-based updates. JavaScript handles presentation, camera lifecycle, frame encoding, input validation and sending the selected image; edge/contour processing remains in Java. A single validated selected file is used for both camera and upload flows, and replaces the multipart `image` part explicitly so reprocessing cannot accidentally send an earlier upload.

The automated tests generate fixture images directly in Java/OpenCV. They cover analytic rectangles, sampled circles, rotated/narrow objects, concave shapes, all image borders, point/line degeneracy, PNG/JPEG input, empty/invalid input, blank images, multiple objects, area/aspect/circularity/solidity/border filters, inclusive bounds and invalid combinations. A synthetic scene includes a border-spanning straight background line and separate compact/narrow foreground objects: assertions verify configured criteria, including that convex background regions can pass solidity filtering. No test assumes all grout can be rejected.

Morphology tests compare different closing/opening kernels, explicitly demonstrate merging and thin-edge loss, verify raw-mask independence, exercise Otsu polarity, and check source mask/count consistency. API tests cover legacy fields/defaults plus additive controls and invalid enums/numbers. A real embedded-server HTTP test submits all nineteen multipart parts, verifying the parser limit that MockMvc bypasses. Existing byte/pixel/contour budgets and slot-release tests remain. Overlay assertions allow JPEG colour tolerance and preserve photographic background. A 12-megapixel textured JPEG fixture verifies bounded 1600-pixel previews, unchanged geometry, a preview payload under 8 MB and visible edges/contours on a phone-sized view. Additional tests compare display paths at different preview resolutions and verify Java-calculated axis endpoints.

```bash
./mvnw test
./mvnw clean verify javadoc:javadoc
```

Surefire reports are in `target/surefire-reports`; generated API documentation is under `target/reports/apidocs`. Javadoc is configured to fail on warnings. No downloaded test photographs or external services are needed. API and service tests verify the real bundled native library, rather than mocking OpenCV.

The optional frontend regression checks use Node.js 20+ built-in modules only (no npm packages or application/runtime dependency):

```bash
node src/test/javascript/camera.test.cjs
node src/test/javascript/retention.test.cjs
node src/test/javascript/experiments.test.cjs
```

These checks simulate browser APIs and verify automatic camera submission, duplicate-click protection, reprocessing, cancellation, late permission release, permission denial, HTTPS requirements, background cleanup and frame readiness. Preview checks cover legacy-response compatibility, source-coordinate scaling, independent annotations, selected contours, original/mask views, detected/retained counts, shape columns, SVG export creation and raw-mode comparison. They are separate from Maven and do not access a physical camera. Actual device permission prompts and video appearance still require a browser/device check.

Retention checks cover reopening the page without reprocessing, saved controls/overlays, cancelled captures, invalid replacements, new-photo/previous-analysis labeling, failed processing, successful replacement, storage failures and late-restore races. Browser integration checks additionally exercise real IndexedDB and reopen a persistent Chrome profile; unit checks use a simulated storage adapter.

Phase 2 verification on Ubuntu/Java 21: the existing **41/41** tests passed before changes, and the extended **50/50** Java tests pass with no failures or skipped tests. The new fixtures cover a complete rectangle, broken outline, nearby independent rectangles, rectangle beside a long line, several noisy shapes and a rotated elongated rectangle. They demonstrate intended gap repair, unintended merging, loose rotated boxes, kernel-shape differences, thin-edge loss, stage/source/count consistency, hierarchy after filtering, SIMPLE/NONE measurement equivalence, approximation diagnostics and numeric validation. All three JavaScript regression suites pass. Real headless Chrome checks exercise a 4000 × 3000 synthetic image, cached stage switching without uploads, hierarchy/tracing controls, isolated measurements, decodable SVG exports, real IndexedDB restoration and desktop/mobile layouts without page errors. Maven packaging and warning-checked Javadoc are required by the deployment workflow. No real shoe photograph is included in these tests.

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

`deploy/vision.service` runs `/opt/vision/vision.jar` using the system Java 21 executable `/usr/bin/java`, as the dedicated unprivileged `vision-app` user. The service starts on boot, restarts on failure, and has a private temporary directory with a read-only system/home view. Systemd owns its writable state/cache directories, `/var/lib/vision` and `/var/cache/vision`, with mode 0700. Native libraries extract to `/var/cache/vision/javacpp`; no user home or source checkout is required at runtime. The Java heap limit is 768 MiB; OpenCV native allocations are additional memory. The service creates no permanent image storage.

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

### Operations and verification

```bash
sudo systemctl status vision.service --no-pager
sudo journalctl -u vision.service -n 50 --no-pager
curl --fail http://127.0.0.1:8090/api/vision/health
sudo nginx -t
curl --head https://test.letsoisa.co.za/
```

The internal readiness response must contain `status=ready` and the OpenCV version. Use `./deploy/set-auth.sh status` to determine the intended public response: HTTP 200 when disabled, or HTTP 302 to `https://test.letsoisa.co.za/login/…` when enabled. `/login/` retains the existing login route. Validate a sample image through the dashboard after changes affecting uploads or processing. Nginx logs are `/var/log/nginx/test.letsoisa.co.za.access.log` and `.error.log`. Port 8090 remains bound to loopback; the Vision-specific Nginx snippet controls whether Authelia is required on the public hostname.

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
- Photograph previews are bounded, lossy JPEG images; extraction masks are bounded binary PNGs. The SVG open-image export embeds that bounded image and the currently selected annotations, rather than full-resolution photo data. Use structured measurements for source-resolution geometry. Thickened strokes and simplified display paths are visual aids. Base64 data URLs add transfer overhead. The latest photo/analysis survives reloads through browser storage, subject to its availability and eviction policy; it is not synchronized across devices.
- Automated synthetic fixtures test known geometry and failure modes; they do not establish accuracy for every real photograph of a sponge, circular object or pen on a tiled floor. The described test photograph has not been supplied as a fixture.

See the [OpenCV Java Imgproc API](https://docs.opencv.org/4.x/javadoc/org/opencv/imgproc/Imgproc.html), [shape descriptors](https://docs.opencv.org/4.x/d3/dc0/group__imgproc__shape.html), [Canny explanation](https://docs.opencv.org/4.x/da/d22/tutorial_py_canny.html), [morphological operations](https://docs.opencv.org/4.x/d9/d61/tutorial_py_morphological_ops.html), [Bytedeco native packaging](https://github.com/bytedeco/javacpp-presets) and [Bootstrap documentation](https://getbootstrap.com/docs/5.3/). Tutorial language does not imply a Python dependency; this application uses Java throughout.
