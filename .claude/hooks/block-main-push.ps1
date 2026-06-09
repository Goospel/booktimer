# PreToolUse hook — main/master 브랜치로의 push 가드
#
# BookTimer 규칙(CLAUDE.md): 모든 변경은 브랜치 → PR → 머지.
# Claude 의 Bash/PowerShell 도구 호출 중 `git push` 가 main/master 를 겨냥하면
# exit 2 로 차단한다. 다른 브랜치 push 는 허용.
#
# 두 단계 차단 (강도 순):
#   1) force-push to main/master → 하드 차단. 공유 히스토리를 덮어쓰는 파괴적 동작이라
#      일반 main push 허용(ALLOW_MAIN_PUSH)으로도 뚫리지 않는다.
#      예외(override): 토큰 `ALLOW_FORCE_PUSH_MAIN` 가 있을 때만 통과.
#   2) 일반 push to main/master → 차단.
#      예외(override): 토큰 `ALLOW_MAIN_PUSH` 가 있을 때 통과.
#   → override 토큰은 사용자가 그 동작을 명시적으로 지시한 경우에만 Claude 가 부착.
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

# ── git push 서브커맨드 감지 ──────────────────────────────────────────────────
# 'push' 가 브랜치명/파일경로/커밋메시지 속 부분문자열이면 무시해야 한다.
# (예: git checkout -b feat/x-push-y  →  push 가 아님)
# 그래서 단순 '\bpush\b' 가 아니라 `git` 의 서브커맨드로서의 push 만 잡는다:
#   git push ... / git -C <path> push ... / git --opt push ...
# 'git' 뒤에는 전역옵션(인자 받는 -C/-c/--git-dir/--work-tree/--namespace 는
# 값까지, 그 외 플래그는 토큰 하나씩)만 끼어들 수 있다고 보고 그 다음이 push 인지 확인.
$pushSubcmd = '\bgit\s+((-C|-c|--git-dir|--work-tree|--namespace)\s+\S+\s+|-{1,2}\S+\s+)*push(\s|$)'
if ($cmd -notmatch $pushSubcmd) { exit 0 }

# ── 이 push 가 main/master 를 겨냥하는가? ($targetsMain) ──────────────────────
$targetsMain = $false

# (a) 원격 + 명시적 ref 로 main/master 를 겨냥
#     예: git push origin main / git push -u origin main / git push origin HEAD:main
if ($cmd -match 'push\s+(-{1,2}[^\s]+\s+)*\S+\s+(\S+:)?(main|master)\b') {
    $targetsMain = $true
}
# (b) 원격 + 명시적 ref 가 있는데 main/master 가 아니면 → 다른 브랜치, 관심 없음
#     (이름에 'main' 이 들어간 브랜치 chore/...-main 도 여기서 허용으로 걸러짐)
elseif ($cmd -match 'push\s+(-{1,2}[^\s]+\s+)*\S+\s+\S+') {
    $targetsMain = $false
}
# (c) bare 'git push' / 'git push origin' / 'git push --force' → 현재 브랜치(또는 upstream)
#     (a) 현재 브랜치가 main/master 거나
#     (b) 현재 브랜치의 upstream(merge ref)이 main/master 면 main 겨냥으로 본다.
else {
    $cwd = [string]$data.cwd
    if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }
    try {
        $branch = (& git -C $cwd rev-parse --abbrev-ref HEAD).Trim()
    } catch {
        $branch = ''
    }
    if ($branch -eq 'main' -or $branch -eq 'master') {
        $targetsMain = $true
    } else {
        try {
            $mergeRef = (& git -C $cwd config "branch.$branch.merge").Trim()
        } catch {
            $mergeRef = ''
        }
        if ($mergeRef -match '(^|/)(main|master)$') { $targetsMain = $true }
    }
}

# main/master 를 안 겨냥하면 통과 (다른 브랜치는 force 든 아니든 허용)
if (-not $targetsMain) { exit 0 }

# ── force-push 인가? ($isForce) ──────────────────────────────────────────────
#   --force / --force-with-lease / --force-if-includes / -f / 단축 묶음(-fu, -uf …)
$isForce = ($cmd -match '(^|\s)--force(-with-lease|-if-includes)?\b') -or `
           ($cmd -match '(^|\s)-[A-Za-z]*f[A-Za-z]*(\s|=|$)')

# ── 차단 결정 ────────────────────────────────────────────────────────────────
# 1) force-push to main/master → ALLOW_MAIN_PUSH 로도 안 뚫림. 전용 토큰만.
if ($isForce) {
    if ($cmd -match 'ALLOW_FORCE_PUSH_MAIN') { exit 0 }
    $forceMsg = @"
[BLOCKED] Force-push to main/master is forbidden.

BookTimer rule: force-pushing main/master rewrites shared history and is never
allowed -- not even with ALLOW_MAIN_PUSH. Use a branch -> PR -> merge instead.

Override only in a genuine emergency the user explicitly demanded: include the
token ALLOW_FORCE_PUSH_MAIN in the command to bypass this hook.
"@
    [Console]::Error.WriteLine($forceMsg)
    exit 2
}

# 2) 일반 push to main/master → ALLOW_MAIN_PUSH 로 통과 가능.
if ($cmd -match 'ALLOW_MAIN_PUSH') { exit 0 }
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
[Console]::Error.WriteLine($blockMsg)
exit 2
