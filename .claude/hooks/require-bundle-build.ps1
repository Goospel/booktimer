# PreToolUse hook -- frontend bundle stale guard (#2)
#
# If a git commit stages files under frontend/, this hook rebuilds the bundles
# (npm --prefix frontend run build) and then checks whether any file under
# src/main/resources/static changed relative to HEAD. A diff means the commit
# would include a stale bundle -- it blocks the commit and asks the developer
# to add the rebuilt artifacts.
#
# Why needed: CI only checks garden.js (1 island); the other 9 islands are a
# blind spot (T-082, profile.js stale incident). This hook checks all 10 islands.
#
# Note: the hook intentionally runs a real build and may mutate the working tree
# (the rebuilt .js files). This is the stale-detection signal -- not a side effect.
#
# Bypass tokens (include in the commit command to skip):
#   SKIP_BUNDLE_CHECK  -- skip only the bundle check
#   SKIP_TESTS         -- shared bypass: skips both test gate and bundle check
#
# Block messages are English/ASCII only -- Korean corrupts in hook stderr.

$ErrorActionPreference = 'Stop'

try {
    $raw  = [Console]::In.ReadToEnd()
    $data = $raw | ConvertFrom-Json
    $cmd  = [string]$data.tool_input.command
} catch { exit 0 }

if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }

# Only interested in git commit commands
if ($cmd -notmatch '\bgit\b' -or $cmd -notmatch '\bcommit\b') { exit 0 }

# Bypass tokens
if ($cmd -match 'SKIP_TESTS' -or $cmd -match 'SKIP_BUNDLE_CHECK') { exit 0 }

$cwd = [string]$data.cwd
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

# Check if any staged files are under frontend/
try {
    $staged = @(& git -C $cwd diff --cached --name-only 2>$null)
} catch { $staged = @() }

$frontStaged = @($staged | Where-Object { $_ -match '^frontend/' })
if ($frontStaged.Count -eq 0) { exit 0 }   # no frontend changes -- skip

# Require node (npm depends on it); fail-open if absent
$nodeCmd = Get-Command node -ErrorAction SilentlyContinue
if (-not $nodeCmd) {
    [Console]::Error.WriteLine('[WARN] node not found -- frontend bundle check skipped (install Node.js to enforce)')
    exit 0
}

# Rebuild all frontend bundles.
# stderr is suppressed via cmd.exe to avoid NativeCommandError under $ErrorActionPreference='Stop'
# (same pattern as require-tests-before-commit.ps1).
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
cmd.exe /c "npm --prefix `"$cwd\frontend`" run build >nul 2>nul"
$buildExit = $LASTEXITCODE
$ErrorActionPreference = $prevEAP

if ($buildExit -ne 0) {
    $msg = @"
[BLOCKED] Frontend build failed -- commit aborted.

Run manually to see errors:
  npm --prefix frontend run build

Fix the build error before committing. Or bypass:
  include SKIP_BUNDLE_CHECK in the commit command.
"@
    [Console]::Error.WriteLine($msg)
    exit 2
}

# Check whether the rebuild changed anything under src/main/resources/static.
# A non-zero exit means the committed bundle is stale (out of sync with source).
$prevEAP2 = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& git -C $cwd diff --exit-code -- src/main/resources/static >$null 2>$null
$diffExit = $LASTEXITCODE
$ErrorActionPreference = $prevEAP2

if ($diffExit -ne 0) {
    $msg = @"
[BLOCKED] Bundle is stale: frontend source changed but built artifacts are not up-to-date.

Run:
  npm --prefix frontend run build
  git add src/main/resources/static

Then commit again. Or bypass:
  include SKIP_BUNDLE_CHECK in the commit command.
"@
    [Console]::Error.WriteLine($msg)
    exit 2
}

exit 0
