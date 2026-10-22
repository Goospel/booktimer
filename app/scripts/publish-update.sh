#!/usr/bin/env bash
# EAS Update 게시(앞당김 보충 §6.3 · REQ-70 · 71) — app/**을 바꾼 PR이 머지된 뒤(state=MERGED 확인) 그 세션이 부른다.
#   bash app/scripts/publish-update.sh "<PR 제목> (#N)"
#   PREVIEW=1 bash app/scripts/publish-update.sh "#N <요지>"   # 머지 전 미리보기 — 메시지 앞에 「미리보기」가 붙는다
# 네이티브를 바꾸는 PR(지문이 바뀌는 PR — PR-L)은 게시하지 않고 빌드한다.
# 받는 쪽 확인: 아이폰에서 열기 → 완전히 닫기 → 다시 열기 → 설정 탭 맨 아래 줄의 id 앞 8자 = 아래 출력의 iOS update id 앞 8자.
set -euo pipefail

msg="${1:?게시 메시지가 필요해요 — \"<PR 제목> (#N)\"}"
root="$(git rev-parse --show-toplevel)"
cd "$root"
git fetch origin

# 로컬 .env는 Metro 시험용 LAN 주소를 담을 수 있다 — 내보내기 단계가 읽으면 TestFlight 앱이 그 주소로 붙는다(§6.6).
if compgen -G 'app/.env*' >/dev/null; then
  echo "app/.env* 파일이 있어요 — 로컬 주소가 섞일 수 있어 게시하지 않습니다: $(ls app/.env*)" >&2
  exit 1
fi
if [ -n "$(git status --porcelain)" ]; then
  echo "작업 트리가 깨끗하지 않아요 — 게시할 화면은 커밋돼 있어야 합니다" >&2
  exit 1
fi
if [ "${PREVIEW:-}" = 1 ]; then
  if ! git merge-base --is-ancestor origin/main HEAD; then
    echo "origin/main이 HEAD의 조상이 아니에요 — 머리를 origin/main 위로 맞춘 뒤 다시 해 주세요" >&2
    exit 1
  fi
  case "$msg" in 미리보기*) ;; *) msg="미리보기 $msg" ;; esac
elif [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]; then
  echo "HEAD가 origin/main과 달라요 — production 게시는 머지된 main에서만 합니다(미리보기는 PREVIEW=1)" >&2
  exit 1
fi

cd app
npx --yes eas-cli@24.11.0 update --channel production --environment production --platform ios \
  --message "$msg" --non-interactive
