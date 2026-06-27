# SessionStart hook — auto-link frontend/node_modules junction in worktrees
#
# BookTimer rule (CLAUDE.md / learning-notes N-132): splitting work into a
# git worktree leaves frontend/node_modules empty (node_modules is git-ignored,
# so git never copies it into a worktree), forcing a fresh npm install each time.
# This hook runs link-node-modules.ps1 in the session cwd at startup so the
# worktree reuses main's node_modules via a Windows junction — zero manual input.
# The script is idempotent (already-linked -> instant skip) and a no-op on main.
#
# SessionStart must NEVER block startup (side-effect only): missing git, missing
# script, junction failure — whatever happens, exit 0 quietly (fail-open). Output
# is ASCII-only one line; the SessionStart injection path mangles Korean under
# PowerShell 5.1 (N-006 family).
#
# Test (test-link-node-modules-on-session-start.sh) swaps the link script via env
# BOOKTIMER_LINK_SCRIPT to verify: invoked in a git repo, fail-open on failure /
# missing script, and skipped (not invoked) when cwd is not a git repo.

$ErrorActionPreference = 'SilentlyContinue'

# Read stdin (JSON: cwd, ...) — drain to avoid blocking, extract cwd.
try {
    $raw  = [Console]::In.ReadToEnd()
    $data = $raw | ConvertFrom-Json
    $cwd  = [string]$data.cwd
} catch { $cwd = '' }
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

# Not a git work tree -> nothing to link; pass quietly (fail-open).
$null = (& git -C $cwd rev-parse --is-inside-work-tree 2>$null)
if ($LASTEXITCODE -ne 0) { exit 0 }

# Link script path: default is ../scripts/link-node-modules.ps1 next to this hook;
# tests inject a stub via env.
$link = $env:BOOKTIMER_LINK_SCRIPT
if ([string]::IsNullOrWhiteSpace($link)) {
    $link = Join-Path $PSScriptRoot '..\scripts\link-node-modules.ps1'
}
if (-not (Test-Path $link)) { exit 0 }   # missing script -> tolerate, pass

# Run the link script in the session cwd (child process; discard its output —
# it is Korean and would mangle in the SessionStart injection path). Any failure
# is swallowed; startup is never blocked.
try {
    Push-Location $cwd
    & powershell.exe -NoProfile -File $link *> $null
    Pop-Location
    Write-Output "[node_modules] worktree junction checked (link-node-modules.ps1)"
} catch {
    try { Pop-Location } catch { }
    Write-Output "[node_modules] junction link skipped (non-blocking)"
}

exit 0
