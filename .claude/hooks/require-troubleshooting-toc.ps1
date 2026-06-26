# require-troubleshooting-toc.ps1
#
# WARNING: UTF-8 BOM 포함으로 저장(PowerShell 5.1 한글 주석 깨짐 회피, T-026 계열).
#
# PreToolUse 게이트(자동수정형): `git commit`이고 스테이징에
# claude-docs/troubleshooting.md가 있으면, 목차를 재생성(rebuild-troubleshooting-toc.ps1)
# 하고 변경됐으면 다시 add해 목차 갱신이 그 커밋에 함께 들어가게 한다.
# rebuild-memory-index(SessionStart)의 repo-내 판 — 추적 파일이라 커밋 시점에 잡아야
# git dirty/멀티세션 충돌이 없다.
# fail-open: 무엇이든 실패하면 커밋을 막지 않는다(목차는 순수 파생물).

$ErrorActionPreference = 'Stop'

try {
    $raw  = [Console]::In.ReadToEnd()
    $data = $raw | ConvertFrom-Json
    $cmd  = [string]$data.tool_input.command
} catch { exit 0 }

if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }

# git commit 명령에만 관심.
if ($cmd -notmatch '\bgit\b' -or $cmd -notmatch '\bcommit\b') { exit 0 }

$cwd = [string]$data.cwd
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

$rel = 'claude-docs/troubleshooting.md'

# 스테이징에 troubleshooting.md가 없으면 통과.
try {
    $staged = & git -C $cwd diff --cached --name-only 2>$null
} catch { exit 0 }
if (-not ($staged -contains $rel)) { exit 0 }

$tsPath     = Join-Path $cwd $rel
$scriptPath = Join-Path $cwd '.claude\scripts\rebuild-troubleshooting-toc.ps1'
if (-not (Test-Path $scriptPath)) { exit 0 }  # 코어 스크립트 없으면 비치명 통과

try {
    $result = & powershell.exe -NoProfile -File $scriptPath -Path $tsPath 2>$null
    if ("$result" -match 'changed') {
        & git -C $cwd add -- $rel 2>$null
    }
} catch { exit 0 }

exit 0
