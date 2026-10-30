# Vision Lab — Phase 1

Vision Lab is a Java laboratory for studying OpenCV edge detection, external contours and image geometry. Capture a single camera frame in the page or upload an existing image, then view edges and contours directly over the photograph. Optional measurements remain available below the image. This phase does not classify objects, use machine learning, continuously process camera frames or control sewing hardware.

## Private repository

Source control is hosted at **https://github.com/lekgotlaletsoisa-del/vision** with **private** visibility. Keep the repository private; access is managed through the owner's GitHub account. The repository contains source, tests, static assets, Maven configuration and the deployment templates for this host. Build output, local environment files, private keys and logs are excluded by `.gitignore`. Production authentication databases, secrets, certificates and captured images are not part of source control.

The main branch is `main`. Before committing changes, update the affected README/API documentation, run the Maven verification and Javadoc commands below, and run the camera regression checks for frontend changes. Commit and push only when requested. After pushing an approved change, use `./deploy/deploy.sh` to update the configured service and verify readiness through the established deployment workflow.

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

The deployed site is **https://test.letsoisa.co.za/**, protected by the host's existing Authelia sign-in. For local development, open **http://localhost:8090/**. The default bind address is `127.0.0.1` and the default port is `8090`, avoiding the other applications on this host. Stop the deployed service before running a local development instance on that same port, or choose a different port. For development, `./mvnw spring-boot:run` is also supported. To select another port:

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
2. Adjust **Canny low/high**. Expand **More controls** for **minimum area** and **blur kernel**.
3. Select **Process image** to see **blue edges** and **green retained contours/IDs** over the source photograph in one large preview. Both overlays are enabled after each successful processing request. Toggle **Edges** or **Contours** independently; disable both to see the source preview. Switching overlays needs no new processing request. **Open image** opens the currently selected preview. On phones the page scrolls to the result after processing.
4. Expand **Measurements** when geometry is needed. Expand **4 corners** for each rotated rectangle's vertices. IDs match the contour labels and are assigned from largest to smallest area (with top/left tie breaks). Measurements and advanced controls start collapsed; explanatory paragraphs and metric cards are omitted to keep attention on the image.
5. Change one control and select **Reprocess image**. The status indicates when controls have changed since the displayed result. A failed reprocess leaves the previous successful result visibly identified.

Camera capture uses [`getUserMedia`](https://www.w3.org/TR/mediacapture-streams/) with a preferred rear-facing camera and `audio: false`. A local, muted inline video preview is displayed in a light dialog; only the frame selected by the shutter is sent to Java. The native camera application/file-capture route is no longer used, so its mandatory OK/retry screen is avoided. Browser permission is still required and cannot be bypassed. If permission is denied, camera access is unsupported, or the camera is busy/unavailable, an explanatory error is shown and upload remains available.

The camera requests a 2560 × 1920 frame where supported, with maximum width/height constraints of 3840 × 2880. The actual negotiated frame dimensions can differ. The shutter copies that frame, without filters or resizing, into a temporary canvas and requests JPEG encoding at quality 0.92. This is image acquisition/transport only: grayscale, blur, Canny, contours, geometry and overlay rendering continue to run exclusively in Java. Camera measurements use the captured video-frame pixels, which can differ from the resolution of the phone's native still-photo sensor output. The JPEG/PNG bytes use the same multipart endpoint and existing size/pixel limits. No video or audio stream is sent to the server.

Camera tracks are stopped immediately after capture, when the dialog closes or is cancelled, when the page is hidden/left, and when a pending permission request resolves after cancellation. The shutter remains disabled until a usable frame is ready and prevents duplicate captures while encoding. Captured frames are automatically submitted once, remain in page memory for reprocessing, and are replaced by a new capture or upload. The app has no permanent image-storage feature. Unsupported browsers use the existing upload option rather than falling back to a native capture flow that would restore the confirmation screen.

Detection and geometry run in Java/OpenCV at the original resolution. A separate Java renderer draws display overlays on bounded previews, using thicker blue edges and bright green contours with dark outlines so they remain visible on phones. Blue marks every Canny edge, including edges belonging to contours removed by area filtering. Green retained contours take precedence where they overlap blue edges. Contour IDs are green; rotated boxes and centre markers are not drawn. No detected/retained contours is a successful result, with advice to adjust settings. The status distinguishes no edges from no contours retained by area filtering. The browser checks that the combined result image can decode before replacing the source preview, and reports an error if it cannot.

## Pipeline and controls

Processing lives in `VisionProcessingService`, separate from MVC controllers:

1. Reject empty/oversized uploads. Java ImageIO inspects JPEG/PNG format and dimensions in memory **before native decoding**; it does not perform image processing or save a file.
2. Decode in OpenCV to an 8-bit BGR image at the original dimensions. `IMREAD_IGNORE_ORIENTATION` deliberately ignores EXIF rotation; every returned preview shares the same orientation and coordinate mapping. A browser's initial source preview may honor metadata rotation until the server result replaces it.
3. `Imgproc.cvtColor(..., COLOR_BGR2GRAY)` converts BGR to grayscale.
4. `Imgproc.GaussianBlur` smooths local intensity noise using an odd square kernel, with sigma inferred by OpenCV. A kernel of 1 has no smoothing effect.
5. `Imgproc.Canny` detects intensity boundaries with a 3-pixel Sobel aperture and `L2gradient=true` (Euclidean gradient magnitude). The resulting mask has white edges and a black background.
6. `Imgproc.findContours` uses `RETR_EXTERNAL` and `CHAIN_APPROX_SIMPLE` on a copy of the edge mask. This retrieves outermost boundaries and compresses collinear segments. It omits holes and nested outlines and avoids measuring inner/outer Canny outlines as separate objects.
7. Calculate polygon areas with `contourArea`, then retain contours whose area is **greater than or equal to** the minimum. A value of zero permits degenerate contours.
8. Measure retained geometry at the original resolution, then create four JPEG previews: source photograph, blue edges over the photograph, green contours/IDs over the photograph, and both overlays together. `PreviewRenderer` limits the longest preview side to 1600 pixels by default without upscaling smaller inputs. It reduces the photograph and Canny mask using area interpolation, keeps nonzero mask contributions, thickens the displayed edges, and scales copies of the retained contour points for drawing. Only visual marks are thickened; original masks, area filtering and measurements are unchanged. The binary Canny mask remains an internal intermediate.
9. Return immutable Java records as JSON, including JPEG preview data URLs, original and preview dimensions, counts, edge-pixel count, actual parameters and processing duration. JPEG quality defaults to 88. This avoids returning multiple full-size PNG photographs that can total around 100 MB for a phone image. No native matrices escape the service.

Canny's **high threshold** selects strong gradient seeds. Edges between the **low and high thresholds** survive only when connected to strong edges through hysteresis. Values below the low threshold are discarded. Lower thresholds are more sensitive to faint edges and texture; higher ones can lose genuine low-contrast boundaries. These are gradient thresholds, not brightness cutoffs. The UI intentionally limits them to `0 ≤ low < high ≤ 255` for this phase; Canny gradients themselves are not restricted to an 8-bit intensity range.

Increasing the blur kernel suppresses noise and fine texture, but shifts/removes detail. Increasing minimum area filters small polygon boundaries after edge detection; it does not change the edge image. Use high contrast, a plain background and a reasonably overhead photograph as an initial experiment.

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
| `server.tomcat.max-part-count` | 8 | Bounds multipart part count. |
| `server.address` / `server.port` | 127.0.0.1 / 8090 | Local interface / port. |

If increasing upload limits, keep the servlet limit, request limit, memory threshold and `vision.max-upload-bytes` consistent. Rejected large requests may be handled using servlet temporary storage; such files are request-scoped and cleaned by the container. The application has no permanent image-storage feature. Java ImageIO uses a memory stream, and OpenCV encodes/decodes in memory. Responses use `Cache-Control: no-store`; source bytes/results live only for the request and current browser page. Matrices, encoded buffers and contour matrices are explicitly released using `MatScope`, including failure paths. A semaphore bounds concurrent native processing; matrices are never shared between requests.

A large photograph can consume much more memory than its compressed upload: BGR copies, grayscale intermediates, contours, base64 strings and browser images all add overhead. Pixel/concurrency limits reduce load but are not a strict process memory cap. The contour-count guard runs after `findContours`, so it cannot prevent allocations made during contour discovery. Use smaller input images or lower budgets for constrained hardware.

## HTTP API

`POST /api/vision/process` accepts `multipart/form-data`:

| Field | Required | Type |
| --- | --- | --- |
| `image` | Yes | JPEG or PNG binary part |
| `cannyLow` | No | Finite number, default from configuration |
| `cannyHigh` | No | Finite number, default from configuration |
| `minContourArea` | No | Finite number in px², default from configuration |
| `blurKernel` | No | Odd integer, default from configuration |

```bash
curl --fail-with-body http://127.0.0.1:8090/api/vision/process \
  -F image=@/path/to/paper.jpg \
  -F cannyLow=50 -F cannyHigh=150 \
  -F minContourArea=100 -F blurKernel=5
```

Success is HTTP 200 with `width`, `height`, `previewWidth`, `previewHeight`, `contourCount`, `detectedContourCount`, `edgePixelCount`, `processingMillis`, `parameters`, `originalImage`, `edgesImage`, `contoursImage`, `overlayImage`, and `contours`. Images are `data:image/jpeg;base64,...` strings at the preview dimensions: `originalImage` is the normalized source preview, `edgesImage` contains blue Canny edges over the photograph, `contoursImage` contains green contours/IDs, and `overlayImage` combines both. This replaces the earlier full-resolution PNG response format. All geometry remains in the original image coordinate space described by `width`/`height`. For display-coordinate mapping use `(x + 0.5) * previewWidth / width - 0.5` and the corresponding Y formula. Contour geometry follows the table above. `processingMillis` measures server processing including header checks/decoding/encoding, excluding multipart transfer and JSON/network delivery.

Expected errors return `{"status":"error","message":"..."}`: 400 for invalid images, missing parts or invalid controls; 413 for byte/pixel limits; 415 for non-multipart requests; 422 for contour complexity; 429 when processing slots are occupied. Unexpected failures are logged server-side and return a generic 500 message. Image bytes and filenames are never logged by application code.

## Architecture and tests

- `config`: validated settings and eager native loading.
- `controller`: Thymeleaf dashboard, multipart API, readiness and HTTP error mapping.
- `service`: header validation, request-local resource ownership, OpenCV pipeline and geometry extraction.
- `model`: immutable parameters, geometry and result records; no native or MVC types.
- `templates/index.html`, `static/css/vision.css`, `static/js/vision.js`: responsive light dashboard, in-page single-frame camera acquisition and fetch-based updates. JavaScript handles presentation, camera lifecycle, frame encoding, input validation and sending the selected image; edge/contour processing remains in Java. A single validated selected file is used for both camera and upload flows, and replaces the multipart `image` part explicitly so reprocessing cannot accidentally send an earlier upload.

The automated tests generate fixture images directly in Java/OpenCV. They cover analytic rectangle geometry, raster/Canny tolerances, rotated/tall rectangles, line/point degeneracy, PNG/JPEG input, image dimensions, invalid/empty/unsupported input, blank images, multiple objects, small-area filtering, threshold sensitivity, resource budgets and slot release, numeric validation, MVC defaults/overrides, error JSON, dashboard rendering and readiness. Overlay assertions allow JPEG colour tolerance and verify blue/green marks, preserved photographic background, and area-filter effects. A 12-megapixel textured JPEG fixture verifies bounded 1600-pixel previews, unchanged source-coordinate geometry, a combined preview payload under 8 MB, and visible blue/green marks when fitted to a 360-pixel-wide phone view.

```bash
./mvnw clean verify javadoc:javadoc
```

Surefire reports are in `target/surefire-reports`; generated API documentation is under `target/reports/apidocs`. Javadoc is configured to fail on warnings. No downloaded test photographs or external services are needed. API and service tests verify the real bundled native library, rather than mocking OpenCV.

The optional frontend regression checks use Node.js 20+ built-in modules only (no npm packages or application/runtime dependency):

```bash
node src/test/javascript/camera.test.cjs
```

These checks simulate browser camera APIs and verify automatic submission after the shutter, duplicate-click protection, reprocessing the same frame, cancellation, releasing a late permission response, permission denial, HTTPS requirements, background cleanup and frame readiness. They are separate from the Maven tests and do not access a physical camera. Actual device permission prompts and video appearance still require a browser/device check.

## Deployment to test.letsoisa.co.za

The configured deployment target is this Ubuntu host, using **Nginx → 127.0.0.1:8090 → vision.service**. Public access is **https://test.letsoisa.co.za/**. Nginx redirects HTTP to HTTPS, serves ACME challenges from `/var/www/letsencrypt`, and uses the existing domain certificate at `/etc/letsencrypt/live/test.letsoisa.co.za/`. Certbot's existing renewal job is retained; `deploy/renew-certificate.sh` is installed as its domain-specific deploy hook to validate/reload Nginx after renewal.

`deploy/nginx.conf` follows the other hosted applications' Authelia integration: `/login/` proxies to the existing authentication container and all application routes, including API/readiness, require sign-in. The hostname has a normal `one_factor` application rule and a domain-specific session cookie in Authelia. No user accounts or existing application policies are changed. Nginx permits an 11 MiB multipart request, while the application retains its 10 MiB image limit. Request buffering is disabled, and upstream processing timeouts are 120 seconds. Forwarded host/protocol/address headers are supplied by Nginx and handled by Spring only on the localhost-bound service.

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

The script runs Maven tests/build and warning-checked Javadoc, verifies the domain certificate is present, creates the service account if needed, installs the executable JAR atomically, checks that its bytes match the build artifact, installs/enables/restarts `vision.service`, and waits for successful application readiness. It then installs the hostname's Nginx configuration, runs `nginx -t`, reloads Nginx, installs the certificate-renewal hook and verifies a valid HTTPS response that redirects an unauthenticated visitor to the domain's sign-in page. It leaves a previously deployed JAR at `/opt/vision/vision.jar.previous`. No commit or push is performed.

The first deployment must stop any manually launched Vision process using port 8090 before starting the service. Future deployments use the service workflow directly. Service readiness failures stop deployment before activating the Nginx configuration; inspect logs and restore the previous JAR if appropriate. A failed Nginx configuration test prevents reloading; the running workers continue using their previous configuration. Correct the staged file before a later reload.

### Operations and verification

```bash
sudo systemctl status vision.service --no-pager
sudo journalctl -u vision.service -n 50 --no-pager
curl --fail http://127.0.0.1:8090/api/vision/health
sudo nginx -t
curl --head https://test.letsoisa.co.za/
```

The internal readiness response must contain `status=ready` and the OpenCV version. The unauthenticated public request should return HTTP 302 to `https://test.letsoisa.co.za/login/…`; `/login/` should return the login page. After signing in, the dashboard and multipart API are available through the same hostname. Validate a sample image through the dashboard after changes affecting uploads or processing. Nginx logs are `/var/log/nginx/test.letsoisa.co.za.access.log` and `.error.log`. The raw port 8090 remains bound to loopback; application authentication is enforced by Nginx/Authelia.

To roll back an application deployment when a previous JAR exists:

```bash
sudo cp /opt/vision/vision.jar.previous /opt/vision/vision.jar.next
sudo mv /opt/vision/vision.jar.next /opt/vision/vision.jar
sudo systemctl restart vision.service
curl --fail http://127.0.0.1:8090/api/vision/health
```

Deploy configuration changes can require restoring the matching service/Nginx files from the prior project version as well. Validate Nginx before reloading, and run `systemctl daemon-reload` before restarting when the unit changes.

## Known limitations and references

- No recognition: every retained external boundary is a contour, including shadows, folds or background detail.
- Textured fabric often creates many small edges. Area filtering cannot repair a broken outline or remove all internal texture.
- Holes, nested contours, hierarchy, line fitting, polygon simplification and automatic sheet selection are outside this phase.
- No calibration: perspective, camera angle and lens distortion affect pixel geometry; physical lengths/areas require later calibration.
- EXIF orientation is intentionally ignored in processed results. Transparent PNG is decoded to BGR, so transparency is not preserved. Animated/multipage images are not supported as sequences.
- Native image decoders may tolerate some truncated images; header and decoded dimensions plus decode success are checked, but this is not an exhaustive file-integrity validator.
- Display images are bounded, lossy JPEG previews; they are not full-resolution exports. Use the structured measurements for original-resolution geometry. Thickened display strokes are visual aids and must not be used to remeasure contour area. Base64 data URLs add transfer overhead, though bounded previews greatly reduce cost compared with full-resolution PNG copies. Uploaded images and results are lost when the page is closed/reloaded.

See the [OpenCV Java Imgproc API](https://docs.opencv.org/4.x/javadoc/org/opencv/imgproc/Imgproc.html), [Canny tutorial](https://docs.opencv.org/4.x/da/d22/tutorial_py_canny.html) for the algorithm explanation, [Bytedeco native packaging](https://github.com/bytedeco/javacpp-presets) and [Bootstrap documentation](https://getbootstrap.com/docs/5.3/). Tutorial language does not imply a Python dependency; this application uses Java throughout.
