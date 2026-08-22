#!/usr/bin/env bash
# TDD test for block-main-push.ps1 (PreToolUse hook)
# 판정 매트릭스(훅 주석 기준): force 하드차단 / 일반 차단 / 토큰 우회 /
# bare push 의 현재 브랜치·upstream 판정 / push 서브커맨드 오탐 방지.

HOOK=".claude/hooks/block-main-push.ps1"
FAILED=0
TMPS=()

cleanup() { for d in "${TMPS[@]}"; do rm -rf "$d" 2>/dev/null; done; }
trap cleanup EXIT

# 공용 cwd (git 레포 아님 — 명시 ref 케이스는 cwd 를 안 본다)
SHARED_TMP=$(mktemp -d); TMPS+=("$SHARED_TMP")

json_esc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }

# 훅 실행: $1=command, $2=cwd(옵션) → exit code 출력
run() {
    local cmd="$1" dir="${2:-$SHARED_TMP}"
    printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' \
        "$(json_esc "$cmd")" "$(json_esc "$(cygpath -w "$dir")")" \
        | powershell.exe -NoProfile -File "$HOOK" >/dev/null 2>&1
    echo $?
}

# 훅 실행 후 stderr 만 출력
run_stderr() {
    local cmd="$1"
    printf '{"tool_input":{"command":"%s"},"cwd":"%s"}' \
        "$(json_esc "$cmd")" "$(json_esc "$(cygpath -w "$SHARED_TMP")")" \
        | powershell.exe -NoProfile -File "$HOOK" 2>&1 >/dev/null
}

# 임시 git 레포 생성: $1=체크아웃할 브랜치(생략 시 main 그대로)
mk_repo() {
    local branch="${1:-}" d
    d=$(mktemp -d); TMPS+=("$d")
    git -C "$d" init -q -b main
    git -C "$d" -c user.email=t@t -c user.name=t commit -q --allow-empty -m init
    if [ -n "$branch" ]; then
        git -C "$d" checkout -q -b "$branch"
        # upstream 도 같은 feature 브랜치로 설정 (main 겨냥 아님)
        git -C "$d" config "branch.$branch.merge" "refs/heads/$branch"
        git -C "$d" config "branch.$branch.remote" origin
    fi
    printf '%s' "$d"
}

check() {
    local label="$1" expected="$2" got="$3"
    if [ "$got" = "$expected" ]; then
        echo "PASS: $label"
    else
        echo "FAIL: $label (expected exit $expected, got $got)"
        FAILED=1
    fi
}

# ── Case 1: git push origin main → 차단 ──
check "push origin main → exit 2" 2 "$(run 'git push origin main')"

# ── Case 2: -u 플래그 동반 → 차단 ──
check "push -u origin main → exit 2" 2 "$(run 'git push -u origin main')"

# ── Case 3: refspec 형태(HEAD:main) → 차단 ──
check "push origin HEAD:main → exit 2" 2 "$(run 'git push origin HEAD:main')"

# ── Case 4: 다른 브랜치 → 통과 ──
check "push origin feat/x → exit 0" 0 "$(run 'git push origin feat/x')"

# ── Case 5: push 가 서브커맨드가 아님(브랜치명 일부) → 오탐 없음 ──
check "checkout -b feat/x-push-y → exit 0" 0 "$(run 'git checkout -b feat/x-push-y')"

# ── Case 6: force-push to main → 차단 + 전용 메시지 ──
check "push --force origin main → exit 2" 2 "$(run 'git push --force origin main')"
if run_stderr 'git push --force origin main' | grep -q 'Force-push'; then
    echo "PASS: force-push stderr mentions Force-push"
else
    echo "FAIL: force-push stderr does not mention Force-push"
    FAILED=1
fi

# ── Case 7: 일반 토큰으로 force 는 안 뚫린다 ──
check "force + ALLOW_MAIN_PUSH → still exit 2" 2 \
      "$(run 'git push --force origin main ALLOW_MAIN_PUSH')"

# ── Case 8: 전용 토큰 우회 ──
check "force + ALLOW_FORCE_PUSH_MAIN → exit 0" 0 \
      "$(run 'git push --force origin main ALLOW_FORCE_PUSH_MAIN')"
check "plain + ALLOW_MAIN_PUSH → exit 0" 0 \
      "$(run 'git push origin main ALLOW_MAIN_PUSH')"

# ── Case 9: 한글이 섞인 명령이라도 main 겨냥이면 차단 ──
#    (stdin 을 CP949 로 읽으면 JSON 파싱이 깨져 fail-open 될 수 있다 — 그 회귀를 잡는다)
check "Korean in command + push origin main → exit 2" 2 \
      "$(run 'echo 한글메모 && git push origin main')"

# ── Case 10: bare 'git push' → 현재 브랜치/upstream 으로 판정 ──
check "bare push on main branch → exit 2" 2 "$(run 'git push' "$(mk_repo)")"
check "bare push on feature branch → exit 0" 0 "$(run 'git push' "$(mk_repo feat/x)")"

# ── Case 11: 브랜치명이 -f 가 아닌 fix/foo → 오탐 없음 ──
check "push origin fix/foo → exit 0" 0 "$(run 'git push origin fix/foo')"

exit $FAILED
