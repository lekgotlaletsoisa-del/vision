#!/usr/bin/env bash
# Persistently select Vision's auth snippet without changing any other application.
# Usage: ./deploy/set-auth.sh status|enable|disable
set -euo pipefail
mode=${1:-status}
snippets=/etc/nginx/snippets
link="$snippets/vision-auth.conf"
case "$mode" in
    status)
        target=$(readlink "$link") || { echo 'Vision auth snippets have not been installed.' >&2; exit 1; }
        case "$target" in
            "$snippets/vision-auth-enabled.conf") echo enabled ;;
            "$snippets/vision-auth-disabled.conf") echo disabled ;;
            *) echo 'Unrecognized Vision auth snippet; configuration left unchanged.' >&2; exit 1 ;;
        esac
        exit 0 ;;
    enable) target="$snippets/vision-auth-enabled.conf" ;;
    disable) target="$snippets/vision-auth-disabled.conf" ;;
    *) echo "Usage: $0 status|enable|disable" >&2; exit 2 ;;
esac
previous=$(readlink "$link") || { echo 'Run deploy/deploy.sh to install auth snippets first.' >&2; exit 1; }
case "$previous" in
    "$snippets/vision-auth-enabled.conf"|"$snippets/vision-auth-disabled.conf") ;;
    *) echo 'Unrecognized Vision auth snippet; configuration left unchanged.' >&2; exit 1 ;;
esac
sudo test -f "$target"
# Atomic link replacement; restore the previous link if validation or reload fails.
staged="$link.next"
sudo ln -sfn "$target" "$staged"
sudo mv -Tf "$staged" "$link"
if ! sudo nginx -t || ! sudo systemctl reload nginx; then
    sudo ln -sfn "$previous" "$staged"
    sudo mv -Tf "$staged" "$link"
    sudo nginx -t && sudo systemctl reload nginx
    echo 'Auth change failed; restored the previous Vision auth mode.' >&2
    exit 1
fi
"$(dirname "$0")/check-https.sh" "$mode"
printf 'Vision authentication %sd. This choice persists across deployments until changed manually.\n' "$mode"
