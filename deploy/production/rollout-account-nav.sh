#!/usr/bin/env bash
set -euo pipefail

cd /opt/lynxad
registry=lynxad-prod-acr-registry-vpc.cn-beijing.cr.aliyuncs.com/lynxad-prod
release=20261016-account-nav
backup=app.compose.yml.pre-${release}

test -f app.compose.yml
grep -q 'lynxad-app-web:20261016-material-catalog' app.compose.yml
docker pull "$registry/lynxad-app-web:$release"
cp -p app.compose.yml "$backup"

rollback() {
  cp -p "$backup" app.compose.yml
  docker compose -f app.compose.yml up -d --no-deps app-web
  echo 'ROLLBACK_FINISHED'
}
trap rollback ERR

sed -i 's/lynxad-app-web:20261016-material-catalog/lynxad-app-web:20261016-account-nav/' app.compose.yml
docker compose -f app.compose.yml up -d --no-deps app-web

ready=''
for attempt in $(seq 1 30); do
  if curl -fsS -o /dev/null http://127.0.0.1/ 2>/dev/null; then
    ready=yes
    break
  fi
  sleep 2
done
test "$ready" = yes
asset_hash=$(curl -fsS http://127.0.0.1/assets/AppLayout-B_1IFBGt.js | sha256sum | cut -d ' ' -f 1)
test "$asset_hash" = 0e33deed932a61705c7a20c18bc79b05b206c6045f0438d8553e24baa2135866
api_id=$(docker compose -f app.compose.yml ps -q app-api)
test "$(docker inspect --format '{{.State.Health.Status}}' "$api_id")" = healthy
trap - ERR
echo 'DEPLOY_OK'
docker compose -f app.compose.yml ps
