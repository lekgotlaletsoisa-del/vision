#!/usr/bin/env bash
# Certbot deploy hook: apply renewed test.letsoisa.co.za certificates safely.
set -euo pipefail
if [[ "${RENEWED_LINEAGE:-}" == /etc/letsencrypt/live/test.letsoisa.co.za ]]; then
    /usr/sbin/nginx -t
    /usr/bin/systemctl reload nginx
fi
