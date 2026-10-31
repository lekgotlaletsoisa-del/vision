#!/usr/bin/env bash
# Build, install, restart and verify Vision on this host's configured target.
# Prerequisites: Java 21 at /usr/bin/java, GTK 2/libGL, domain certificate and
# the test.letsoisa.co.za Authelia access rule/cookie described in README.md.
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B -ntp verify javadoc:javadoc
sudo test -s /etc/letsencrypt/live/test.letsoisa.co.za/fullchain.pem
sudo test -s /etc/letsencrypt/live/test.letsoisa.co.za/privkey.pem
if ! getent passwd vision-app > /dev/null; then
    sudo useradd --system --user-group --home-dir /var/lib/vision --shell /usr/sbin/nologin vision-app
fi
sudo install -d -m 0755 /opt/vision
if sudo test -f /opt/vision/vision.jar; then
    sudo cp -p /opt/vision/vision.jar /opt/vision/vision.jar.previous
fi
sudo install -m 0644 target/vision-0.0.1-SNAPSHOT.jar /opt/vision/vision.jar.next
sudo mv /opt/vision/vision.jar.next /opt/vision/vision.jar
sudo cmp --silent target/vision-0.0.1-SNAPSHOT.jar /opt/vision/vision.jar
sudo install -m 0644 deploy/vision.service /etc/systemd/system/vision.service
sudo systemctl daemon-reload
sudo systemctl enable vision.service
sudo systemctl restart vision.service
ready=false
for attempt in {1..40}; do
    if sudo systemctl is-active --quiet vision.service \
        && curl --fail --silent http://127.0.0.1:8090/api/vision/health | grep -q '"status":"ready"'; then
        ready=true
        break
    fi
    sleep 1
done
if [[ "$ready" != true ]]; then
    sudo journalctl -u vision.service -n 50 --no-pager
    echo 'Vision failed readiness. Inspect the log; previous JAR is retained for rollback.' >&2
    exit 1
fi
# Preserve the operator's auth choice on subsequent deployments. New hosts start protected.
sudo install -d -m 0755 /etc/nginx/snippets
sudo install -m 0644 deploy/auth-enabled.conf /etc/nginx/snippets/vision-auth-enabled.conf
sudo install -m 0644 deploy/auth-disabled.conf /etc/nginx/snippets/vision-auth-disabled.conf
if ! sudo test -e /etc/nginx/snippets/vision-auth.conf && ! sudo test -L /etc/nginx/snippets/vision-auth.conf; then
    sudo ln -s /etc/nginx/snippets/vision-auth-enabled.conf /etc/nginx/snippets/vision-auth.conf
fi
auth_mode=$(./deploy/set-auth.sh status)
sudo install -m 0644 deploy/nginx.conf /etc/nginx/sites-available/test.letsoisa.co.za
sudo ln -sfn /etc/nginx/sites-available/test.letsoisa.co.za /etc/nginx/sites-enabled/test.letsoisa.co.za
sudo nginx -t
sudo systemctl reload nginx
sudo install -m 0755 deploy/renew-certificate.sh /etc/letsencrypt/renewal-hooks/deploy/vision-nginx
sudo systemctl is-active vision.service
if [[ "$auth_mode" == enabled ]]; then ./deploy/check-https.sh enable; else ./deploy/check-https.sh disable; fi
printf 'Vision deployed and healthy: https://test.letsoisa.co.za/ (authentication %s)\n' "$auth_mode"
