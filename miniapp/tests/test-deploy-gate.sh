#!/usr/bin/env bash
# Smoke test for miniapp/deploy.sh — 배포 전 산출물 검증 게이트.
#
# Invariants this guards (each a distinct real failure — 전부 T-150에서 실제로 터진 것):
#   1) 정상 경로는 통과하고 배포까지 간다(게이트가 과하게 조여 배포를 못 하면 아무도 안 쓴다).
#   2) 이번 릴리스 기능 마커가 번들에 없으면 **배포 전에** 죽는다 — T-150의 본체.
#      빌드가 완료 로그 없이 조용히 끝나도 exit 0이면 체인이 계속돼 옛 dist가 그대로 배포됐다.
#      마커는 **이전 번들과 구별되는 것**이어야 한다(env 값은 직전 번들에도 있어 신·구를 못 가른다).
#   3) `localhost:8080`이 남은 번들은 배포되지 않는다(T-148 — env 미주입 빌드).
#   4) .ait 안의 js가 방금 빌드한 dist의 js와 바이트가 다르면 죽는다(패키징 단계 스테일).
#   5) index.html이 참조하는 js가 실존하지 않으면 죽는다(반쪽 dist).
#
# npm/npx 호출은 PATH 앞의 스텁으로 전부 우회한다(deploy/tests/test-render-env.sh 와 같은 방식).
# 한글 마커 비교는 grep(C 로케일 무성실패)이 아니라 bash 패턴/파이썬으로 한다.

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
S="$REPO/miniapp/deploy.sh"
FAILED=0

STUB="$(mktemp -d)"
WORK="$(mktemp -d)"
trap 'rm -rf "$STUB" "$WORK"' EXIT

# ── 스텁: npm run build — 실 vite 대신 dist/를 만든다. 내용은 MODE가 정한다. ──
cat > "$STUB/npm" <<'STUBEOF'
#!/usr/bin/env bash
set -e
mkdir -p dist/assets
JS=dist/assets/index-STUBHASH.js
case "${MODE:-ok}" in
  # T-150 본체 재현: 빌드가 완료 로그 없이 조용히 끝나는데 exit는 0 — 산출물을 하나도 안 만든다.
  silent-noop) echo 'vite v6.0.0 building for production...'; exit 0 ;;
  no-marker) printf '%s\n' 'var API="https://booktimer.app";' > "$JS" ;;
  # localhost 케이스는 **다른 검사는 전부 통과**해야 한다 — 안 그러면 booktimer.app 부재로 먼저 죽어
  # "localhost를 잡았다"가 아니라 "다른 이유로 죽었다"를 재는 공허한 테스트가 된다.
  localhost) printf '%s\n' 'var API="http://localhost:8080";/*booktimer.app 토스로 시작하기 borderRadius:28*/' > "$JS" ;;
  *)         printf '%s\n' 'var API="https://booktimer.app";/*토스로 시작하기 borderRadius:28*/' > "$JS" ;;
esac
if [ "${MODE:-ok}" = missing-js ]; then rm -f "$JS"; fi
cat > dist/index.html <<'HTMLEOF'
<!doctype html><html lang="ko"><head>
<script type="module" crossorigin src="/assets/index-STUBHASH.js"></script>
</head><body><div id="root"></div></body></html>
HTMLEOF
STUBEOF
chmod +x "$STUB/npm"

# ── 스텁: npx ait build / npx ait deploy ──
# build 는 dist/ 를 sources/ 접두로 zip 한다(실 .ait 레이아웃 실측). STALE_AIT 면 js만 옛 내용으로.
cat > "$STUB/npx" <<'STUBEOF'
#!/usr/bin/env bash
set -e
case "${2:-}" in
  build)
    python - <<'PYEOF'
import os, zipfile
stale = os.environ.get('STALE_AIT')
with zipfile.ZipFile('booktimer.ait', 'w') as z:
    for root, _, files in os.walk('dist'):
        for f in files:
            p = os.path.join(root, f)
            arc = 'sources/' + os.path.relpath(p, 'dist').replace(os.sep, '/')
            if stale and arc.endswith('.js'):
                z.writestr(arc, b'STALE-OLD-BUNDLE')
            else:
                z.write(p, arc)
PYEOF
    ;;
  deploy)
    : > "$DEPLOY_MARK"
    echo "Uploading bundle..."
    echo "deploymentId: 019ff48e-stub"
    ;;
  *) echo "STUB-NPX unexpected: $*" >&2; exit 9 ;;
esac
STUBEOF
chmod +x "$STUB/npx"

run() {  # $1=MODE ; $2.. = deploy.sh 인자 — echoes "<exit>\n<output>"
    local mode="$1"; shift
    local stale=""
    if [ "$mode" = stale-ait ]; then stale=1; fi
    rm -rf "$WORK"; mkdir -p "$WORK/miniapp" "$WORK/.claude/.secrets"
    cp "$S" "$WORK/miniapp/deploy.sh"
    echo 'stub-api-key' > "$WORK/.claude/.secrets/ait-api-key.txt"
    if [ "$mode" = silent-noop ]; then
        # 직전 릴리스의 dist가 그대로 남아 있는 상태 — **마커까지 들어 있다**(그 문구가 이번에 처음
        # 들어간 게 아니라면 벌어지는 일). 클린 빌드가 없으면 이 옛 번들이 모든 검사를 통과해 배포된다.
        mkdir -p "$WORK/miniapp/dist/assets"
        printf '%s\n' 'var API="https://booktimer.app";/*토스로 시작하기 borderRadius:28*/' \
            > "$WORK/miniapp/dist/assets/index-OLDHASH.js"
        printf '%s\n' '<script type="module" src="/assets/index-OLDHASH.js"></script>' \
            > "$WORK/miniapp/dist/index.html"
    fi
    local out rc
    out="$(cd "$WORK/miniapp" && MODE="$mode" STALE_AIT="$stale" DEPLOY_MARK="$WORK/deployed" \
            PATH="$STUB:$PATH" bash ./deploy.sh "$@" 2>&1)"; rc=$?
    printf '%s\n' "$rc"
    printf '%s' "$out"
}

assert_exit() {  # $1=label $2=got $3=want
    if [ "$2" = "$3" ]; then echo "PASS: $1 (exit $2)"; else echo "FAIL: $1 exit=$2 want=$3"; FAILED=1; fi
}
assert_has() {   # $1=label $2=haystack $3=needle — bash 패턴 매칭(한글 안전, grep 무성실패 회피)
    if [[ "$2" == *"$3"* ]]; then echo "PASS: $1"; else echo "FAIL: $1 — missing '$3'"; FAILED=1; fi
}
assert_lacks() { # $1=label $2=haystack $3=needle
    if [[ "$2" == *"$3"* ]]; then echo "FAIL: $1 — '$3' 가 들어 있다"; FAILED=1; else echo "PASS: $1"; fi
}
assert_deployed() {    # $1=label
    if [ -e "$WORK/deployed" ]; then echo "PASS: $1"; else echo "FAIL: $1 — 배포가 실행되지 않았다"; FAILED=1; fi
}
assert_not_deployed() {  # $1=label
    if [ -e "$WORK/deployed" ]; then echo "FAIL: $1 — 검증에 걸렸는데 배포가 나갔다"; FAILED=1; else echo "PASS: $1"; fi
}

MARKER='토스로 시작하기'

# ── Case 1: 정상 — 마커가 든 번들은 검증을 통과해 배포까지 간다 ──
r="$(run ok --expect "$MARKER")"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "정상 경로" "$rc" "0"
assert_deployed "  배포까지 실행된다"
assert_has "  deploymentId를 마지막에 알린다" "$out" "019ff48e-stub"

# ── Case 2: 이번 릴리스 마커가 번들에 없다 → 배포 전 차단 (T-150 본체) ──
r="$(run no-marker --expect "$MARKER")"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "마커 부재 → 차단" "$rc" "1"
assert_not_deployed "  배포 전에 멈춘다"
assert_has "  무엇이 없는지 마커를 이름으로 알린다" "$out" "$MARKER"

# ── Case 3: localhost 잔존(env 미주입 빌드, T-148) → 차단 ──
r="$(run localhost --expect "$MARKER")"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "localhost 잔존 → 차단" "$rc" "1"
assert_not_deployed "  배포 전에 멈춘다"
assert_has "  무엇이 남았는지 알린다" "$out" "localhost:8080"

# ── Case 4: .ait 안의 js ≠ dist의 js(패키징 스테일) → 차단 ──
r="$(run stale-ait --expect "$MARKER")"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit ".ait 스테일 → 차단" "$rc" "1"
assert_not_deployed "  배포 전에 멈춘다"
assert_has "  .ait와 dist가 다름을 알린다" "$out" "booktimer.ait"

# ── Case 5: index.html이 참조하는 js가 없다(반쪽 dist) → 차단 ──
r="$(run missing-js --expect "$MARKER")"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "참조 js 부재 → 차단" "$rc" "1"
assert_not_deployed "  배포 전에 멈춘다"
assert_has "  없는 파일명을 알린다" "$out" "index-STUBHASH.js"
# 가드가 없으면 파이썬이 FileNotFoundError 트레이스백을 뱉으며 죽는다 — 그것도 exit 1이라 종료코드만으론
# 구별되지 않는다. "무엇이 어긋났는지 한 줄"이 게이트의 계약이므로 트레이스백 부재로 계측한다.
assert_lacks "  한 줄 진단으로 죽는다(트레이스백 아님)" "$out" "Traceback"

# ── Case 6: 빌드가 조용히 아무것도 안 했는데 옛 dist가 남아 있다 → 클린 빌드가 막는다 (T-150 본체) ──
# 옛 번들에도 마커가 들어 있으므로 --expect만으론 못 잡는다 — `rm -rf dist` 가 유일한 방어선이다.
r="$(run silent-noop --expect "$MARKER")"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "빌드 무성 미완료 + 옛 dist 잔존 → 차단" "$rc" "1"
assert_not_deployed "  스테일 번들이 배포되지 않는다"
assert_has "  산출물이 없음을 알린다" "$out" "dist/index.html"

echo
if [ "$FAILED" = 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$FAILED"
