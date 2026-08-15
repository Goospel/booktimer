#!/usr/bin/env bash
# Caddyfile의 forwarded 헤더 신뢰 경계 회귀 테스트.
#
# 지키는 불변식: **클라이언트가 심은 forwarded 계열 헤더는 앱에 도달하지 않는다.**
#
# 왜 중요한가 — 앱은 ForwardedHeaderFilter(WebConfig)를 HIGHEST_PRECEDENCE로 등록해서
# request.getRemoteAddr() / 스킴 / 호스트를 이 헤더들에서 다시 읽는다. 그 값이 공격자 것이면:
#   · LoginAttemptFilter의 IP 잠금(MAX_ATTEMPTS=5)이 요청마다 새 키를 잡아 **무력화**된다.
#     앞단에 WAF·rate limit이 없어 이게 온라인 비밀번호 추측의 유일한 통제다.
#   · OAuth redirect_uri와 세션 쿠키 secure 판정이 공격자가 지정한 host/proto로 만들어진다.
#
# ⚠️ Spring은 `Forwarded`를 `X-Forwarded-*`보다 **우선**한다. 그런데 Caddy는 `Forwarded`를
#    스스로 세팅하지 않으면서 들어온 값은 그대로 통과시킨다 — 즉 이 헤더는 항상 공격자 입력이다.
#    (실측 2026-08-15, caddy v2.11.4: 스푸핑한 X-Forwarded-For는 Caddy가 리셋했지만
#     `Forwarded: for=203.0.113.9;host=evil.example;proto=https`는 그대로 업스트림에 도달했다.)
#
# X-Forwarded-For 단언도 함께 둔다 — 그건 지금 Caddy 기본 동작(trusted_proxies 미설정 시 리셋)에
# 기대고 있고, compose.prod.yaml이 `caddy:2-alpine`이라는 **떠다니는 태그**를 쓰기 때문이다.
# 기본값이 바뀌거나 누가 trusted_proxies를 켜면 여기서 잡힌다.
#
# 실행: 레포 루트에서 `bash deploy/tests/test-caddy-forwarded.sh` (Docker 필요)

CADDYFILE="deploy/caddy/Caddyfile"
COMPOSE="deploy/compose.prod.yaml"
IMG="caddy:2-alpine"
NET="bt-fwdtest-net"
UP="bt-fwdtest-upstream"
PROXY="bt-fwdtest-proxy"
SPOOF_IP="203.0.113.9"
SPOOF_HOST="evil.example"
FAILED=0

cleanup() {
    docker rm -f "$UP" "$PROXY" >/dev/null 2>&1
    docker network rm "$NET" >/dev/null 2>&1
}
trap cleanup EXIT

assert_not() {   # $1=label $2=haystack $3=needle
    if printf '%s' "$2" | grep -qF -- "$3"; then
        echo "FAIL: $1 — 스푸핑 값 '$3'이 업스트림에 도달했다"; FAILED=1
    else
        echo "PASS: $1"
    fi
}
assert_has() {   # $1=label $2=haystack $3=needle
    if printf '%s' "$2" | grep -qF -- "$3"; then echo "PASS: $1"; else echo "FAIL: $1 — '$3' 없음"; FAILED=1; fi
}

[ -f "$CADDYFILE" ] || { echo "FAIL: $CADDYFILE 없음 — 레포 루트에서 실행할 것"; exit 1; }

# 운영 Caddyfile의 header_up 지시자를 **그대로** 뽑아 테스트 프록시에 심는다.
# (패턴이 ASCII라 Git Bash grep의 멀티바이트 무성실패에 걸리지 않는다 — 한글 주석은 매칭 대상이 아님.)
HEADER_UP="$(grep -E '^[[:space:]]*header_up[[:space:]]' "$CADDYFILE")"
echo "운영 Caddyfile에서 뽑은 header_up 지시자:"
printf '%s\n' "${HEADER_UP:-  (없음)}"
echo

cleanup
docker network create "$NET" >/dev/null || { echo "FAIL: docker network 생성 실패"; exit 1; }

# 업스트림 = 앱 대역. 받은 forwarded 계열 헤더를 그대로 되돌려준다.
UP_CFG='{
	auto_https off
}
:80 {
	respond "XFF=[{http.request.header.X-Forwarded-For}] FWD=[{http.request.header.Forwarded}] XFH=[{http.request.header.X-Forwarded-Host}] XFP=[{http.request.header.X-Forwarded-Proto}]" 200
}'

PROXY_CFG="{
	auto_https off
}
:80 {
	reverse_proxy ${UP}:80 {
${HEADER_UP}
	}
}"

BOOT='printf "%s" "$CFG" > /etc/caddy/Caddyfile && caddy run --config /etc/caddy/Caddyfile --adapter caddyfile'
docker run -d --name "$UP"    --network "$NET" -e CFG="$UP_CFG"    "$IMG" sh -c "$BOOT" >/dev/null
docker run -d --name "$PROXY" --network "$NET" -e CFG="$PROXY_CFG" "$IMG" sh -c "$BOOT" >/dev/null

# 기동 대기 — 고정 sleep 대신 실제 응답을 폴링한다.
OUT=""
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
    OUT="$(docker run --rm --network "$NET" "$IMG" sh -c \
        "wget -q -O - \
           --header='X-Forwarded-For: ${SPOOF_IP}' \
           --header='Forwarded: for=${SPOOF_IP};host=${SPOOF_HOST};proto=https' \
           --header='X-Forwarded-Host: ${SPOOF_HOST}' \
           --header='X-Forwarded-Proto: https' \
           http://${PROXY}/ 2>/dev/null")"
    printf '%s' "$OUT" | grep -q 'XFF=' && break
    OUT=""
done

if [ -z "$OUT" ]; then
    echo "FAIL: 프록시가 응답하지 않음 — Caddyfile 문법 오류일 수 있다"
    docker logs "$PROXY" 2>&1 | tail -10
    exit 1
fi

echo "업스트림이 실제로 받은 헤더:"
echo "  $OUT"
echo

# ① 이번 수정의 핵심. Spring이 X-Forwarded-* 보다 먼저 읽는 헤더라 통과하면 나머지 방어가 무의미하다.
assert_not "Forwarded 헤더가 앱에 도달하지 않는다" "$OUT" "for=${SPOOF_IP}"
# ② Caddy 기본 동작(trusted_proxies 미설정 → XFF 리셋) 회귀 가드. 떠다니는 caddy:2 태그 때문에 필요하다.
assert_not "X-Forwarded-For에 스푸핑 IP가 없다"   "$OUT" "XFF=[${SPOOF_IP}"
# ③ 호스트 스푸핑 = OAuth redirect_uri 조작 경로.
assert_not "X-Forwarded-Host가 스푸핑되지 않는다"  "$OUT" "XFH=[${SPOOF_HOST}]"
# ④ proto 스푸핑. 이 테스트는 평문 http로 도는데 https를 심었으니, https로 보이면 통과한 것이다.
assert_not "X-Forwarded-Proto가 스푸핑되지 않는다" "$OUT" "XFP=[https]"
# ⑤ 방어가 헤더를 통째로 지워버린 게 아니라 **진짜 값으로 대체**했는지 — 이게 없으면
#    "reverse_proxy 자체가 깨져서 다 빈 값"인 상태가 위 단언들을 전부 통과한다.
assert_has "X-Forwarded-For에 실제 클라이언트 IP가 실린다" "$OUT" "XFF=[172."

# ⑥ 이 경계는 **운영 Caddy에 실제로 도달할 때만** 방어다. 파일 하나를 bind mount 하면 리눅스가
#    기동 시점 inode에 고정하는데 배포의 `aws s3 sync`는 rename으로 교체해서, 컨테이너가 옛 설정을
#    영원히 보고 `caddy reload`마저 성공해 버린다(T-167 — 실제로 이 함정을 밟아 첫 수정이 무효였다).
#    디렉터리 마운트여야 한다. 위 5단언이 전부 통과해도 여기가 깨지면 운영엔 무반영이다.
if grep -qE '^\s*-\s*\./caddy:/etc/caddy(:ro)?\s*$' "$COMPOSE"; then
    echo "PASS: compose가 Caddyfile을 디렉터리로 마운트한다(파일 마운트면 배포가 무반영)"
else
    echo "FAIL: compose의 caddy 마운트가 디렉터리(./caddy:/etc/caddy)가 아니다 — T-167 재발"; FAILED=1
fi

echo
if [ "$FAILED" = 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$FAILED"
