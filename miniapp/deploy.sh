#!/usr/bin/env bash
# 미니앱 배포 표준 경로 — 클린 빌드 → 산출물 검증 → 배포.
#
# 왜 게이트인가 (T-150): `npm run build`가 완료 로그 없이 조용히 끝나도 exit 0이라 체인이 계속됐고,
# dist엔 옛 코드가 남은 채 스테일 번들이 심사에 올라갔다. 배포 전 검증이 env 값(booktimer.app 등)만
# 봤는데 그 값들은 **직전 번들에도 있어** 신·구를 구별하지 못했다.
# → ① 빌드 성공은 exit code가 아니라 산출물 내용으로 판정하고
#   ② 검증 마커는 이번 릴리스에서 새로 들어간, 이전 번들과 구별되는 문자열이어야 한다(--expect).
#
# 사용법:
#   bash deploy.sh --expect "토스로 시작하기" --expect "borderRadius:28"
#
# 스모크 테스트: bash tests/test-deploy-gate.sh

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

export PYTHONUTF8=1  # 파이썬 stdio·에러 메시지를 UTF-8로 — 한글 마커가 CP949로 깨지지 않게.
PYBIN=python
command -v python >/dev/null 2>&1 || PYBIN=python3

EXPECT=()
while [ $# -gt 0 ]; do
    case "$1" in
        # 빈 마커는 파이썬에서 `'' in body`가 항상 참이라 조용히 무력화된다 — 게이트를 지우는 것과 같아 거부한다.
        --expect)
            [ $# -ge 2 ] && [ -n "$2" ] || { echo "❌ --expect 에 검사할 문자열이 없다" >&2; exit 2; }
            EXPECT+=("$2"); shift 2 ;;
        *) echo "사용법: bash deploy.sh [--expect <이번 릴리스 새 문구>]..." >&2; exit 2 ;;
    esac
done
if [ "${#EXPECT[@]}" = 0 ]; then
    echo "⚠️  --expect 없이 배포한다 — 이번 릴리스에서 새로 들어간 문구를 주면 스테일 번들을 잡을 수 있다(T-150)." >&2
fi

# ── ① 클린 빌드 — 빌드가 조용히 중단돼도 옛 산출물이 남아 그대로 배포되는 경로를 없앤다 ──
rm -rf dist
npm run build

# ── ② dist 검증 — index.html이 참조하는 js를 실제로 열어 내용으로 판정한다 ──
JS_REL="$($PYBIN - "${EXPECT[@]+"${EXPECT[@]}"}" <<'PYEOF'
import pathlib, re, sys

dist = pathlib.Path('dist')
html = dist / 'index.html'
if not html.is_file():
    sys.exit('❌ dist/index.html이 없다 — 빌드가 산출물을 만들지 않았다(exit 0이어도 실패다)')
m = re.search(r'src="([^"]+\.js)"', html.read_text(encoding='utf-8'))
if not m:
    sys.exit('❌ dist/index.html이 js를 참조하지 않는다')
rel = m.group(1).lstrip('/')
js = dist / rel
if not js.is_file():
    sys.exit(f'❌ index.html이 참조하는 {rel} 이(가) dist에 없다 — 반쪽 빌드')

body = js.read_text(encoding='utf-8', errors='replace')
# 음성 체크 둘: env 미주입 빌드(localhost, T-148)와 브라우저 dev 목 코드 잔존(`src/dev-mock.ts`의 마커).
# 목은 dynamic import + `import.meta.env.DEV` 게이트로 잘려야 정상이라, 여기 걸리면 그 배선이 깨진 것이다.
checks = [('booktimer.app', True), ('localhost:8080', False), ('__DEV_MOCK__', False)] \
    + [(e, True) for e in sys.argv[1:]]
for needle, want in checks:
    if (needle in body) != want:
        sys.exit(f'❌ 번들 검증 실패 — {rel} 에 {needle!r} 이(가) '
                 + ('없다' if want else '남아 있다'))
print(rel)
PYEOF
)"
echo "✅ dist 검증 통과 — $JS_REL"

# ── ③ 패키징 + .ait 검증 — zip 안의 js가 방금 빌드한 dist의 js와 바이트까지 같아야 한다 ──
rm -f booktimer.ait
npx ait build

$PYBIN - "$JS_REL" <<'PYEOF'
import hashlib, pathlib, sys, zipfile

rel = sys.argv[1]
local = (pathlib.Path('dist') / rel).read_bytes()
arc = 'sources/' + rel
with zipfile.ZipFile('booktimer.ait') as z:
    if arc not in z.namelist():
        sys.exit(f'❌ booktimer.ait 에 {arc} 가 없다')
    packed = z.read(arc)
if hashlib.sha256(packed).hexdigest() != hashlib.sha256(local).hexdigest():
    sys.exit(f'❌ 스테일 패키징 — booktimer.ait 의 {arc} 가 방금 빌드한 dist/{rel} 와 다르다')
PYEOF
echo "✅ booktimer.ait 검증 통과 — dist와 바이트 동일"

# ── ④ 배포 ──
KEY_FILE=../.claude/.secrets/ait-api-key.txt
[ -f "$KEY_FILE" ] || { echo "❌ API 키가 없다: $KEY_FILE" >&2; exit 1; }

set +e
OUT="$(npx ait deploy --api-key "$(cat "$KEY_FILE")" 2>&1)"; RC=$?
set -e
printf '%s\n' "$OUT"
[ "$RC" = 0 ] || { echo "❌ 배포 실패 (exit $RC)" >&2; exit 1; }

DEPLOY_ID="$(printf '%s\n' "$OUT" \
    | grep -Eio 'deployment[ _-]?id[^A-Za-z0-9]+[A-Za-z0-9_-]+' \
    | tail -1 | grep -Eo '[A-Za-z0-9_-]+$' || true)"
echo "✅ 배포 완료 — deploymentId: ${DEPLOY_ID:-'(출력에서 못 찾음 — 위 로그 확인)'}"
