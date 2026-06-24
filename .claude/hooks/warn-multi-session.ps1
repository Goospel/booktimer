# SessionStart hook — 다중 세션 동시 작업 감지 → 워크트리 분리 권고
#
# BookTimer 규칙(CLAUDE.md): 여러 Claude Code 세션이 한 폴더(워킹 트리)를 공유하면
# 파일·브랜치가 충돌한다. 이 훅은 세션 시작 시 현재 git 상태(브랜치/미커밋/워크트리)를
# 띄워, "이 폴더를 다른 세션이 쓰는 중인가"를 작업 전에 점검하게 만든다(하드 보조).
# 판단·분리 실행은 CLAUDE.md soft 규칙이 담당 — 이 훅은 정보 제공·경고만 한다.
#
# 출력은 의도적으로 영문(ASCII) — PowerShell 5.1 이 훅 출력을 디코딩하는 경로에서
# 한글이 깨지기 때문(N-006 계열). exit 0 고정(세션 시작을 절대 막지 않는다, fail-open).

$ErrorActionPreference = 'SilentlyContinue'

try {
    $raw  = [Console]::In.ReadToEnd()
    $data = $raw | ConvertFrom-Json
    $cwd  = [string]$data.cwd
} catch {
    $cwd = ''
}
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

# git 저장소가 아니면 조용히 통과(fail-open)
$branch = (& git -C $cwd rev-parse --abbrev-ref HEAD 2>$null)
if (-not $branch) { exit 0 }
$branch = ([string]$branch).Trim()

$dirty   = (& git -C $cwd status --porcelain 2>$null)
$isDirty = [bool]$dirty
$wtLines = (& git -C $cwd worktree list 2>$null)
$wtCount = @($wtLines).Count

Write-Output "=== BookTimer multi-session check (SessionStart) ==="
Write-Output ("branch: {0} | uncommitted: {1} | worktrees: {2}" -f $branch, $isDirty, $wtCount)

$onMain   = ($branch -eq 'main' -or $branch -eq 'master')
$occupied = (-not $onMain) -or $isDirty

if ($occupied) {
    Write-Output ""
    Write-Output "[!] This working tree looks occupied. Another Claude session may be using it:"
    if (-not $onMain) { Write-Output ("    - on branch '{0}', not main (someone may have switched it)" -f $branch) }
    if ($isDirty)     { Write-Output  "    - has uncommitted changes you may not have made" }
    Write-Output ""
    Write-Output "Before editing/committing, VERIFY no other session owns this folder."
    Write-Output "If one might, do NOT 'git checkout' here (it switches their branch too)."
    Write-Output "ISOLATE in a separate worktree instead:"
    Write-Output "    git worktree add ../BookTimer-<task> -b <type>/<summary> main"
    Write-Output "    # work there; when merged:  git worktree remove ../BookTimer-<task>"
    Write-Output "(See CLAUDE.md: multi-session worktree rule / learning-notes N-032)"
}

# ── Extension: stale merged claude/* branches ────────────────────────────────
# Squash-merged feat/* branches may show as unmerged (git limitation) — count
# only claude/* auto-branches which are safe to count. feat/* excluded to avoid
# false positives (see plan note).
$CLAUDE_BRANCH_THRESHOLD = 10
try {
    $allMerged = @(& git -C $cwd branch --merged main 2>$null)
    $mergedClaude = @($allMerged | Where-Object {
        $_.Trim().TrimStart('*').Trim() -match '^claude/'
    })
    if ($mergedClaude.Count -ge $CLAUDE_BRANCH_THRESHOLD) {
        Write-Output ""
        Write-Output ("[i] {0} merged 'claude/*' branches can be pruned: git branch -d <name>" -f $mergedClaude.Count)
        Write-Output "    Run: git branch --merged main | Select-String 'claude/' | % { git branch -d `$_.Trim() }"
    }
} catch { }

# ── Extension: orphaned worktree folders ─────────────────────────────────────
# Detects folders under .claude/worktrees/ that are NOT registered worktrees
# (git worktree prune won't remove them; they accumulate after session cleanup
# is skipped). Advisory only — never blocks (exit 0).
$wtFolderPath = Join-Path $cwd '.claude\worktrees'
if (Test-Path $wtFolderPath) {
    try {
        $rawWt = @(& git -C $cwd worktree list 2>$null)
        # Normalize registered paths: forward-slash → backslash, lowercase
        $registeredPaths = @($rawWt | ForEach-Object {
            (($_ -split '\s+')[0]).Replace('/', '\').TrimEnd('\').ToLower()
        } | Where-Object { $_ })

        $subDirs = @(Get-ChildItem $wtFolderPath -Directory -ErrorAction SilentlyContinue)
        $orphaned = @($subDirs | Where-Object {
            $norm = $_.FullName.TrimEnd('\').ToLower()
            -not ($registeredPaths | Where-Object { $_ -eq $norm })
        })
        if ($orphaned.Count -gt 0) {
            Write-Output ""
            Write-Output ("[i] {0} orphaned worktree folder(s) under .claude/worktrees (not in git worktree list)." -f $orphaned.Count)
            Write-Output "    Clean: git worktree prune; then remove leftover dirs manually."
        }
    } catch { }
}

exit 0
