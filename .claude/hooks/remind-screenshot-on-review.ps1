# UserPromptSubmit hook — 사용자가 심사·제출을 언급하면 스토어 스크린샷 갱신을 리마인드한다.
#
# 왜 훅인가: "심사 제출"은 앱인토스 콘솔에서 사용자가 하는 행위라 Claude가 감지할 단서가
# 프롬프트 텍스트뿐이다. hookify 룰은 bash 명령·파일 경로만 보므로 이 트리거를 잡을 수 없어
# 전용 훅으로 내려왔다(승격 사다리의 마지막 계단).
#
# 훅은 REMIND만 한다 — 스크린샷을 대신 찍어 주지 못한다. 판단·촬영은 CLAUDE.md 규칙(soft)이 맡고,
# 이 훅은 "그 순간에 그 규칙이 떠오르게" 하는 계측기다.
#
# ⚠️ stdin은 UTF-8로 명시 디코딩한다 — [Console]::In은 CP949로 읽어 한글 '심사'가 깨지고
#    가드가 조용히 통과한다(글로벌 CLAUDE.md 「Windows 셸 원칙」, hookify warn-console-in-stdin).
# ⚠️ 출력은 ASCII만 — PowerShell 5.1 경로에서 한글 stdout이 깨져 컨텍스트를 오염시킨다
#    (글로벌 remind-korean.ps1과 같은 이유).
#
# 등록: .claude/settings.json 의 hooks.UserPromptSubmit
# 테스트: .claude/hooks/tests/test-remind-screenshot-on-review.sh

$ErrorActionPreference = 'Stop'

try {
    $reader = New-Object System.IO.StreamReader(
        [Console]::OpenStandardInput(),
        (New-Object System.Text.UTF8Encoding($false)))
    $raw    = $reader.ReadToEnd()
    $prompt = [string]([string]$raw | ConvertFrom-Json).prompt
} catch {
    exit 0   # 파싱 실패는 fail-open — 정상 작업을 막지 않는다
}

if ([string]::IsNullOrWhiteSpace($prompt)) { exit 0 }

# '심사'와 콘솔 버튼명 '출시하기'만 본다. '제출'·'출시' 단독은 다른 맥락(리워드 광고 출시,
# PR 제출 등)에서 흔해 오탐이 커 넣지 않았다.
if ($prompt -notmatch '심사|출시하기') { exit 0 }

Write-Output "[Screenshot reminder] The user mentioned app review/submission. Before they submit to the Apps-in-Toss console, check whether miniapp/screenshots/ is still current for the shipped UI (01-home, 02-library, 03-history, 04-goal). You can only capture MOCK-mode screens yourself (npm --prefix miniapp run dev:mock, 390x844@3x viewport, blur focus rings first); real-account captures require the user's phone because the API token is issued only via the Toss SDK authorizationCode. If the UI changed since the stored shots, say so and ask the user for fresh device captures. See miniapp/screenshots/README.md."
