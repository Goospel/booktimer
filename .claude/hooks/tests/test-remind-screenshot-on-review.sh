#!/usr/bin/env bash
# TDD test for remind-screenshot-on-review.ps1 (UserPromptSubmit hook)
#
# 훅의 일: 사용자가 심사·제출을 언급하면 stdout으로 스크린샷 갱신 리마인더를 주입한다.
# 무관한 프롬프트에는 아무것도 내지 않는다(매 턴 컨텍스트를 더럽히지 않기 위해).
#
# 케이스:
#   1) "심사" 언급 → 주입    2) "출시하기" 언급 → 주입
#   3) 무관한 프롬프트 → 무주입
#   4) 한글 UTF-8 프롬프트가 깨지지 않고 매칭 (stdin을 [Console]::In으로 읽으면 CP949로 깨져 실패)
#   5) 출력은 ASCII만 (PowerShell 5.1 한글 stdout 깨짐 회피 — 글로벌 훅 remind-korean.ps1과 같은 이유)

HOOK=".claude/hooks/remind-screenshot-on-review.ps1"
FAILED=0

# 프롬프트를 JSON으로 넘기고 stdout을 돌려준다
run() {
    local prompt="$1"
    local esc
    esc=$(printf '%s' "$prompt" | sed 's/\\/\\\\/g; s/"/\\"/g')
    printf '{"prompt":"%s"}' "$esc" | powershell.exe -NoProfile -File "$HOOK" 2>/dev/null
}

# 주입 판정은 고정 마커로 한다 — 훅이 없을 때 PowerShell이 뱉는 배너를 "주입됨"으로
# 오독한 실측이 있었다(빈 문자열 여부만 보면 가짜 PASS).
MARKER='[Screenshot reminder]'

expect_injected() {
    local desc="$1" prompt="$2"
    local out; out=$(run "$prompt")
    if printf '%s' "$out" | grep -qF "$MARKER"; then
        echo "  PASS: $desc"
    else
        echo "  FAIL: $desc — 리마인더가 주입되지 않음"
        FAILED=1
    fi
}

expect_silent() {
    local desc="$1" prompt="$2"
    local out; out=$(run "$prompt")
    if ! printf '%s' "$out" | grep -qF "$MARKER"; then
        echo "  PASS: $desc"
    else
        echo "  FAIL: $desc — 주입되면 안 되는데 출력됨: $out"
        FAILED=1
    fi
}

echo "== remind-screenshot-on-review.ps1 =="
expect_injected "1) 심사 언급"        "이제 심사 넣자"
expect_injected "2) 출시하기 언급"    "콘솔에서 출시하기 누를게"
expect_silent   "3) 무관한 프롬프트"  "홈 화면 버그 고쳐줘"

# 4) 한글이 CP949로 깨지면 '심사' 매칭이 실패한다 — 인코딩 회귀 감시
expect_injected "4) 긴 한글 문장 속 심사" "오늘 작업 마무리하고 앱인토스 콘솔에 심사 제출까지 진행하려고 해"

# 5) 출력이 ASCII인지 — 한글이 섞이면 PowerShell 5.1 경로에서 깨져 컨텍스트가 오염된다
out=$(run "심사 제출")
if printf '%s' "$out" | LC_ALL=C grep -q '[^ -~]'; then
    echo "  FAIL: 5) 출력에 non-ASCII 문자가 섞임"
    FAILED=1
else
    echo "  PASS: 5) 출력은 ASCII만"
fi

if [ $FAILED -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit $FAILED
