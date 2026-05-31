# PreToolUse hook — main/master 브랜치로의 직접 push 차단
#
# BookTimer 규칙(CLAUDE.md): 모든 변경은 브랜치 → PR → 머지.
# 이 훅은 Claude 의 Bash/PowerShell 도구 호출 중 `git push` 가 main/master 를
# 직접 겨냥하면 exit 2 로 차단한다. 다른 브랜치 push 는 허용.
#
# 예외(override): 명령에 토큰 `ALLOW_MAIN_PUSH` 가 포함되면 통과.
#   → 사용자가 명시적으로 "main 에 바로 push" 라고 지시한 경우에만 Claude 가 부착.
#
# 차단 메시지는 의도적으로 영문(ASCII)으로 둔다 — Claude Code 가 훅 stderr 를
# 캡처/디코딩하는 경로에서 한글이 깨지기 때문 (PowerShell 5.1).

$ErrorActionPreference = 'Stop'

try {
    $raw  = [Console]::In.ReadToEnd()
    $data = $raw | ConvertFrom-Json
    $cmd  = [string]$data.tool_input.command
} catch {
    exit 0   # 입력 파싱 실패 시 fail-open (정상 작업 방해 금지)
}

if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }

# 명시적 override 토큰
if ($cmd -match 'ALLOW_MAIN_PUSH') { exit 0 }

# git push 가 아니면 관심 없음 ('git -C dir push' 처럼 사이에 옵션이 끼어도 잡도록 분리 매칭)
if ($cmd -notmatch '\bgit\b' -or $cmd -notmatch '\bpush\b') { exit 0 }

$blockMsg = @"
[BLOCKED] Direct push to main/master is not allowed.

BookTimer rule (CLAUDE.md): no direct push to main -- use branch -> PR -> merge.
Use this flow instead:
  1) git checkout -b feat/<summary>   (branch off main)
  2) commit, then  git push -u origin feat/<summary>
  3) gh pr create ...   ->   gh pr merge (after user confirms)

Override only when the user explicitly asked to push directly to main:
include the token ALLOW_MAIN_PUSH in the command to bypass this hook.
"@

# 1) 원격 + 명시적 ref 로 main/master 를 겨냥 → 차단
#    예: git push origin main / git push -u origin main / git push origin HEAD:main
if ($cmd -match 'push\s+(-{1,2}[^\s]+\s+)*\S+\s+(\S+:)?(main|master)\b') {
    [Console]::Error.WriteLine($blockMsg)
    exit 2
}

# 2) 원격 + 명시적 ref 가 있는데 main/master 가 아니면 → 다른 브랜치 push, 허용
if ($cmd -match 'push\s+(-{1,2}[^\s]+\s+)*\S+\s+\S+') {
    exit 0
}

# 3) bare 'git push' / 'git push origin' → 현재 브랜치(또는 그 upstream)로 push.
#    (a) 현재 브랜치가 main/master 거나
#    (b) 현재 브랜치의 upstream(merge ref)이 main/master 면 차단.
#    (b)는 upstream 이 잘못 main 을 가리키도록 설정된 경우(push.default 무관)까지 방어.
$cwd = [string]$data.cwd
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }
try {
    $branch = (& git -C $cwd rev-parse --abbrev-ref HEAD).Trim()
} catch {
    $branch = ''
}
if ($branch -eq 'main' -or $branch -eq 'master') {
    [Console]::Error.WriteLine($blockMsg)
    exit 2
}
try {
    $mergeRef = (& git -C $cwd config "branch.$branch.merge").Trim()
} catch {
    $mergeRef = ''
}
if ($mergeRef -match '(^|/)(main|master)$') {
    [Console]::Error.WriteLine($blockMsg)
    exit 2
}

exit 0
