#!/usr/bin/env bash
set -euo pipefail
cd /opt/lynxad
registry=lynxad-prod-acr-registry-vpc.cn-beijing.cr.aliyuncs.com/lynxad-prod
backup=app.compose.yml.pre-engine-console-20261016
test -f engine-console.env
test "$(stat -c '%a' engine-console.env)" = 600
test -f app.compose.engine-console.yml
grep -q 'lynxad-app-web:20261016-account-nav' app.compose.yml
docker pull "$registry/lynxad-app-web:20261016-engine-console"
docker pull "$registry/lynxad-app-web:20261016-engine-entry"
cp -p app.compose.yml "$backup"
rollback() {
  cp -p "$backup" app.compose.yml
  docker compose -f app.compose.yml up -d --no-deps app-web
  echo 'ROLLBACK_FINISHED'
}
trap rollback ERR
cp app.compose.engine-console.yml app.compose.yml
docker compose -f app.compose.yml config --quiet
docker compose -f app.compose.yml up -d --no-deps engine-console
console_id=$(docker compose -f app.compose.yml ps -q engine-console)
console_status=''
for attempt in $(seq 1 60); do
  console_status=$(docker inspect --format '{{.State.Health.Status}}' "$console_id")
  if [ "$console_status" = healthy ]; then break; fi
  sleep 2
done
test "$console_status" = healthy
docker compose -f app.compose.yml up -d --no-deps app-web
ready=''
for attempt in $(seq 1 30); do
  if curl -fsS http://127.0.0.1/ -o /dev/null 2>/dev/null && curl -fsS -H 'Host: api.lynxad.cn' http://127.0.0.1/login 2>/dev/null | grep -q '管理员登录'; then
    ready=yes; break
  fi
  sleep 2
done
test "$ready" = yes
test "$(curl -sS -o /dev/null -w '%{http_code}' -H 'Host: api.lynxad.cn' http://127.0.0.1/)" = 303
for path in api/v1/agents docs openapi.json metrics; do
  test "$(curl -sS -o /dev/null -w '%{http_code}' -H 'Host: api.lynxad.cn' "http://127.0.0.1/$path")" = 401
done
curl -fsS https://api.lynxad.cn/health -o /dev/null
api_id=$(docker compose -f app.compose.yml ps -q app-api)
test "$(docker inspect --format '{{.State.Health.Status}}' "$api_id")" = healthy
trap - ERR
echo 'DEPLOY_OK: main site ready, console login ready, anonymous engine access denied, private engine reachable'
docker compose -f app.compose.yml ps
