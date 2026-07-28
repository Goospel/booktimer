#!/usr/bin/env bash
# Smoke test for deploy-on-ec2.sh blue-green switching (BookTimer, ECS Fargate → EC2 이전).
#
# Invariants this guards (each a distinct real failure):
#   1) blue 실행중  -> green을 띄우고, 헬스 통과 후에야 blue를 제거한다.
#   2) green 실행중 -> blue를 띄운다. 양방향 대칭이 없으면 "두 번째 배포"부터 깨진다
#      (한쪽 방향만 구현하고 통과하는 흔한 버그 — 첫 배포만 검증하면 못 잡는다).
#   3) 아무것도 없음(콜드 스타트) -> blue를 띄우고, 존재하지 않는 상대를 정리하려다 죽지 않는다.
#   4) 헬스체크 실패 -> **새 컨테이너만 제거하고 옛 컨테이너는 살려둔 채** exit 1.
#      가장 중요한 실패경로: 여기서 옛 것을 먼저 내리면 배포 실패가 곧 서비스 전면 중단이 된다.
#   5) 헬스 실패 시 이미지 prune 같은 뒷정리로 넘어가지 않는다(성공 경로 오염 방지).
#
# Pure dry-run: DEPLOY_DRYRUN=1 + DEPLOY_FAKE_* 로 실제 docker 호출을 전부 우회한다.

S="deploy/deploy-on-ec2.sh"
FAILED=0

run() {  # $1=running services(공백구분, 빈문자열 가능)  $2=health(ok|fail) ; echoes "<exit>\n<output>"
    local out rc
    out="$(DEPLOY_DRYRUN=1 DEPLOY_FAKE_RUNNING="$1" DEPLOY_FAKE_HEALTH="$2" \
           HEALTH_RETRIES=3 HEALTH_SLEEP=0 bash "$S" 2>&1)"; rc=$?
    printf '%s\n' "$rc"
    printf '%s' "$out"
}

assert_exit() {  # $1=label $2=got $3=want
    if [ "$2" = "$3" ]; then echo "PASS: $1 (exit $2)"; else echo "FAIL: $1 exit=$2 want=$3"; FAILED=1; fi
}
assert_has() {   # $1=label $2=haystack $3=needle
    if printf '%s' "$2" | grep -qF -- "$3"; then echo "PASS: $1"; else echo "FAIL: $1 — missing '$3'"; FAILED=1; fi
}
assert_not() {   # $1=label $2=haystack $3=needle
    if printf '%s' "$2" | grep -qF -- "$3"; then echo "FAIL: $1 — unexpected '$3'"; FAILED=1; else echo "PASS: $1"; fi
}

# ── Case 1: blue 실행중 + 헬스 OK → green으로 전환 ──
r="$(run "app-blue" ok)"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "blue running + healthy" "$rc" "0"
assert_has  "  starts green"          "$out" "up -d app-green"
# 옛 컨테이너는 graceful(SIGTERM+유예)로 내린다. rm -sf(SIGKILL)로 즉시 죽이면 그 순간
# 처리 중이던 요청이 끊긴다 — 로컬 프로브로 실측한 회귀 지점이다.
assert_has  "  stops old blue gracefully" "$out" "stop -t 30 app-blue"
assert_not  "  does NOT kill old blue"    "$out" "rm -sf app-blue"
assert_not  "  does not remove green"     "$out" "rm -sf app-green"

# ── Case 2: green 실행중 + 헬스 OK → blue로 전환 (양방향 대칭) ──
r="$(run "app-green" ok)"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "green running + healthy" "$rc" "0"
assert_has  "  starts blue"           "$out" "up -d app-blue"
assert_has  "  stops old green"       "$out" "stop -t 30 app-green"

# ── Case 3: 콜드 스타트(실행중 없음) → blue 선택 ──
r="$(run "" ok)"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "cold start" "$rc" "0"
assert_has  "  starts blue" "$out" "up -d app-blue"

# ── Case 4: 헬스 실패 → 새 것만 버리고 옛 것 유지, 논제로 종료 ──
r="$(run "app-blue" fail)"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "health fails" "$rc" "1"
# 새 컨테이너는 트래픽을 받은 적이 없으므로 즉시(-sf) 버려도 안전하다.
assert_has  "  removes the new (failed) green"  "$out" "rm -sf app-green"
# 옛 컨테이너는 어떤 형태로도 건드리면 안 된다 — 그게 지금 서비스 중인 유일한 컨테이너다.
assert_not  "  KEEPS old blue (not stopped)"    "$out" "stop -t 30 app-blue"
assert_not  "  KEEPS old blue (not removed)"    "$out" "rm -f app-blue"
assert_not  "  no image prune on failure"       "$out" "image prune"

echo
if [ "$FAILED" = 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$FAILED"
