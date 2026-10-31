#!/usr/bin/env bash
# Check the effective unauthenticated HTTPS response after asynchronous Nginx reload.
set -euo pipefail
mode=${1:?Expected enable or disable}
case "$mode" in enable|disable) ;; *) echo 'Expected enable or disable.' >&2; exit 2 ;; esac
for attempt in {1..10}; do
    if headers=$(curl --fail --silent --show-error --max-time 10 \
            --resolve test.letsoisa.co.za:443:127.0.0.1 \
            --dump-header - --output /dev/null https://test.letsoisa.co.za/); then
        if [[ "$mode" == enable ]] && grep -q '^HTTP/.* 302' <<< "$headers" \
            && grep -qi '^location: https://test.letsoisa.co.za/login' <<< "$headers"; then
            exit 0
        fi
        if [[ "$mode" == disable ]] && grep -q '^HTTP/.* 200' <<< "$headers"; then
            curl --fail --silent --show-error --max-time 10 \
                --resolve test.letsoisa.co.za:443:127.0.0.1 \
                https://test.letsoisa.co.za/api/vision/health | grep -q '"status":"ready"' && exit 0
        fi
    fi
    sleep 1
done
echo "Vision HTTPS check failed for auth mode $mode; inspect Nginx and service health." >&2
exit 1
