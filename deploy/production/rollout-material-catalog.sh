#!/usr/bin/env bash
set -euo pipefail

cd /opt/lynxad
registry=lynxad-prod-acr-registry-vpc.cn-beijing.cr.aliyuncs.com/lynxad-prod
release=20261016-material-catalog
backup=app.compose.yml.pre-${release}

test -f app.compose.yml
test -f production.env
grep -q 'lynxad-app-api:20261015-sms-live' app.compose.yml
grep -q 'lynxad-app-web:20261015-favicon' app.compose.yml

# Download both images before changing either running container.
docker pull "$registry/lynxad-app-api:$release"
docker pull "$registry/lynxad-app-web:$release"
cp -p app.compose.yml "$backup"

rollback() {
  echo 'ROLLBACK_START'
  cp -p "$backup" app.compose.yml
  docker compose -f app.compose.yml up -d --no-deps app-api app-web
  echo 'ROLLBACK_FINISHED'
}
trap rollback ERR

sed -i 's/lynxad-app-api:20261015-sms-live/lynxad-app-api:20261016-material-catalog/' app.compose.yml
sed -i 's/lynxad-app-web:20261015-favicon/lynxad-app-web:20261016-material-catalog/' app.compose.yml
docker compose -f app.compose.yml up -d --no-deps app-api

api_id=$(docker compose -f app.compose.yml ps -q app-api)
test -n "$api_id"
status=''
for attempt in $(seq 1 60); do
  status=$(docker inspect --format '{{.State.Health.Status}}' "$api_id")
  if [ "$status" = healthy ]; then break; fi
  sleep 5
done
test "$status" = healthy

docker compose -f app.compose.yml up -d --no-deps app-web
web_status=''
for attempt in $(seq 1 30); do
  if curl -fsS -o /dev/null http://127.0.0.1/ 2>/dev/null; then
    web_status=ready
    break
  fi
  sleep 2
done
test "$web_status" = ready
trap - ERR
echo 'DEPLOY_OK'
docker compose -f app.compose.yml ps
