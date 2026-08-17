#!/usr/bin/env bash
# 운영 헬스 감시기(deploy/health-probe.sh) 테스트.
#
# 이 스크립트가 지키는 계약은 두 줄이다:
#   ① 서비스가 살아 있으면 조용하다(exit 0). ② 죽었으면 반드시 시끄럽다(exit 1 → Actions 실패 → 메일).
#
# ⚠️ 감시기에서 **오탐(false alarm)이 가장 위험한 실패**다 — 멀쩡한데 메일이 오면 사람은
#    두 번째 메일부터 안 읽고, 그 순간 감시는 없는 것과 같아진다. 그래서 「한 번 실패했는데
#    다음 시도에 성공하면 조용하다」를 계약으로 못 박는다(네트워크 blip은 장애가 아니다).
#
# ⚠️ 「200이면 OK」로 판정하지 않는다. 도메인이 남의 곳을 가리키거나 앞단이 파킹/에러 페이지를
#    200으로 돌려주면 **HTTP 코드만 보는 감시는 장애를 통과시킨다**. 이 레포가 배포 게이트에서
#    이미 배운 것과 같은 원칙이다 — 성공은 exit code가 아니라 **응답 내용**으로 판정한다(T-150).
#
# curl을 PATH 스텁으로 갈아 시나리오를 주입한다(네트워크·운영 서버에 닿지 않는다 — 오프라인에서도 돈다).
# 실행: 레포 루트에서 `bash deploy/tests/test-health-probe.sh`

PROBE="deploy/health-probe.sh"
FAILED=0
STUB_DIR=""

cleanup() { [ -n "$STUB_DIR" ] && rm -rf "$STUB_DIR"; }
trap cleanup EXIT

[ -f "$PROBE" ] || { echo "FAIL: $PROBE 없음 — 레포 루트에서 실행할 것"; exit 1; }

# curl 스텁을 심는다. SCENARIO에 따라 「본문 + 개행 + HTTP 코드」를 내거나 실패한다.
# 호출 횟수는 COUNT_FILE에 한 줄씩 쌓아 「몇 번 시도했는가」를 계측한다.
make_stub() {
    STUB_DIR=$(mktemp -d)
    cat > "$STUB_DIR/curl" <<'STUB'
#!/usr/bin/env bash
echo "call" >> "$COUNT_FILE"
n=$(wc -l < "$COUNT_FILE" | tr -d ' ')
case "$SCENARIO" in
    up)        printf '{"status":"UP","groups":["liveness","readiness"]}\n200\n' ;;
    down)      printf '{"status":"DOWN"}\n503\n' ;;
    unreach)   exit 7 ;;                      # curl: 연결 불가(빈 stdout)
    notours)   printf '<html>Welcome to nginx</html>\n200\n' ;;
    blip)      if [ "$n" -eq 1 ]; then exit 7; else printf '{"status":"UP"}\n200\n'; fi ;;
    *)         echo "스텁: 알 수 없는 SCENARIO=$SCENARIO" >&2; exit 99 ;;
esac
STUB
    chmod +x "$STUB_DIR/curl"
}

# $1=label $2=SCENARIO $3=기대 exit code
run_probe() {
    COUNT_FILE="$STUB_DIR/count"; : > "$COUNT_FILE"
    OUT=$(PATH="$STUB_DIR:$PATH" COUNT_FILE="$COUNT_FILE" SCENARIO="$2" \
          PROBE_DELAY=0 PROBE_TRIES=3 BASE_URL="https://example.test" \
          bash "$PROBE" 2>&1)
    ACTUAL=$?
    CALLS=$(wc -l < "$COUNT_FILE" | tr -d ' ')
    if [ "$ACTUAL" = "$3" ]; then
        echo "PASS: $1 (exit=$ACTUAL, curl 호출 ${CALLS}회)"
    else
        echo "FAIL: $1 — exit 기대 $3, 실제 $ACTUAL"
        printf '%s\n' "$OUT" | sed 's/^/      /'
        FAILED=1
    fi
}

make_stub

# ── ① 성공 경로 — 살아 있으면 조용하다
run_probe "살아 있으면 exit 0" up 0

# ── ② 앱이 죽음(503 DOWN) — 반드시 시끄럽다
run_probe "헬스가 DOWN이면 exit 1" down 1

# ── ③ 서버·DNS 자체가 안 붙음(curl 실패) — 가장 흔한 진짜 장애
run_probe "연결 자체가 안 되면 exit 1" unreach 1

# ── ④ 200인데 우리 앱이 아님 — HTTP 코드만 보는 감시가 놓치는 자리
run_probe "200이어도 본문이 우리 헬스가 아니면 exit 1" notours 1

# ── ⑤ 오탐 방지 — 첫 시도 실패 후 회복되면 조용하다(blip은 장애가 아니다)
run_probe "일시 실패 뒤 회복되면 exit 0" blip 0

# ── ⑥ 재시도가 유한하다 — 죽어 있을 때 정확히 PROBE_TRIES번만 두드린다
COUNT_FILE="$STUB_DIR/count"; : > "$COUNT_FILE"
PATH="$STUB_DIR:$PATH" COUNT_FILE="$COUNT_FILE" SCENARIO=down \
    PROBE_DELAY=0 PROBE_TRIES=3 BASE_URL="https://example.test" \
    bash "$PROBE" >/dev/null 2>&1
CALLS=$(wc -l < "$COUNT_FILE" | tr -d ' ')
if [ "$CALLS" = "3" ]; then
    echo "PASS: PROBE_TRIES=3이면 정확히 3번만 시도한다"
else
    echo "FAIL: 시도 횟수 — 기대 3, 실제 $CALLS"
    FAILED=1
fi

# ── ⑦ 실패 메시지에 진단 재료가 있다 — 메일만 오고 원인이 없으면 열어봐야 한다
COUNT_FILE="$STUB_DIR/count"; : > "$COUNT_FILE"
OUT=$(PATH="$STUB_DIR:$PATH" COUNT_FILE="$COUNT_FILE" SCENARIO=down \
      PROBE_DELAY=0 PROBE_TRIES=1 BASE_URL="https://example.test" \
      bash "$PROBE" 2>&1)
if printf '%s' "$OUT" | grep -qF '503' && printf '%s' "$OUT" | grep -qF 'DOWN'; then
    echo "PASS: 실패 출력에 HTTP 코드와 응답 본문이 담긴다"
else
    echo "FAIL: 실패 출력에 진단 재료(코드·본문)가 없다"
    printf '%s\n' "$OUT" | sed 's/^/      /'
    FAILED=1
fi

echo
if [ "$FAILED" = "0" ]; then echo "✅ 전부 통과"; else echo "❌ 실패 있음"; fi
exit $FAILED
