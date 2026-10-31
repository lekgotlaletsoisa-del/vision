# Vision Lab project instructions

- Keep the main dashboard limited to Take photo and the Contours and Edges checkboxes. Processing is automatic after capture; do not reintroduce experimental controls or measurements without a user request. Preserve saved-result readers and the processing API for technical inspection.

- After completing a successful change in this project, always commit and push to its configured Git remote. The user has given standing authorization; routine confirmation is not required. This replaces the earlier instruction to commit and push only when explicitly requested.
- Before committing, review and update affected user documentation and Java API documentation, generate warning-checked Javadoc, and run the appropriate Maven and JavaScript checks.
- After pushing, deploy through `./deploy/deploy.sh` to the established target and verify `vision.service`, application readiness and HTTPS access. Report any push or deployment failure accurately.
- To inspect the current saved photograph and analysis, use `https://test.letsoisa.co.za/results/latest` and `/api/vision/results/latest/measurements`. These server-rendered resources do not require the user's browser storage. Prefer the result's UUID link when discussing a particular experiment; the shared latest alias can change after another processing request. An older browser-only photograph is published when that browser opens the updated dashboard.
