# require-troubleshooting-toc.ps1
#
# WARNING: UTF-8 BOM 포함으로 저장(PowerShell 5.1 한글 주석 깨짐 회피, T-026 계열).
#
# PreToolUse 게이트: `git commit`이고 스테이징에 허브 claude-docs/troubleshooting.md 또는
# claude-docs/troubleshooting/ 아래 파일이 있으면 —
#   1. 허브에 옛 단일 파일 형식 줄(`| 날짜 | T-###` 표 행 · `## T-###.` 헤딩)이 추가됐으면 차단(exit 2).
#      2026-09-25 분할 이관 뒤에도 옛 CLAUDE.md 로 도는 세션이 리베이스 충돌을 「허브에 행 복원」으로 풀 때를 겨눈다.
#   2. 검사기 scripts/rebuild-troubleshooting-index.ps1 -HubPath <허브> (재생성 모드)를 돌린다.
#      exit 0 → 재생성된 허브를 다시 add(목차는 파생물 — 자동 수정)
#      INVALID(exit ≠ 0) → 검사기 출력을 그대로 보여 주고 차단 — 형식 오류는 막는다(검사기 없는 규약 방지)
#      출력에 `INDEX-CHECK:` 마커가 없으면 차단 — 검사가 돌지 않은 것이다(템플릿 pre-commit 과 같은 양성 대조)
# fail-open: JSON 파싱 실패 · 커밋 아님 · 스테이징 무관 · 검사기 파일 없음 → 통과.
# 설계: booktimer-split-design.md §3-F. 테스트: .claude/hooks/tests/test-require-troubleshooting-toc.sh

$ErrorActionPreference = 'Stop'

try {
    # stdin 은 UTF-8 로 명시 디코딩 — Console.In 은 CP949 로 읽어 한글 선행바이트가 뒤 따옴표를
    # 삼키고, JSON 파싱 실패 → fail-open 으로 게이트가 조용히 빠진다.
    $raw  = (New-Object System.IO.StreamReader([Console]::OpenStandardInput(), (New-Object System.Text.UTF8Encoding($false)))).ReadToEnd()
    $data = $raw | ConvertFrom-Json
    $cmd  = [string]$data.tool_input.command
} catch { exit 0 }

if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }

# git commit 명령에만 관심.
if ($cmd -notmatch '\bgit\b' -or $cmd -notmatch '\bcommit\b') { exit 0 }

$cwd = [string]$data.cwd
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }
# 커밋이 실제로 도는 워크트리를 본다(T-242). 확장식 경로면 세션 cwd 로 폴백.
. (Join-Path $PSScriptRoot 'lib\resolve-target-cwd.ps1')
$target = Resolve-HookTargetCwd $cmd $cwd 'commit'
if ($target) { $cwd = $target }

$rel = 'claude-docs/troubleshooting.md'

try {
    $staged = @(& git -C $cwd diff --cached --name-only 2>$null)
} catch { exit 0 }
if (-not ($staged | Where-Object { $_ -eq $rel -or $_.StartsWith('claude-docs/troubleshooting/') })) { exit 0 }

$checker = Join-Path $cwd 'scripts\rebuild-troubleshooting-index.ps1'
if (-not (Test-Path $checker)) { exit 0 }  # 검사기 없으면 비치명 통과

# git·검사기 출력(UTF-8)을 CP949 로 디코딩하면 [BLOCKED] 메시지의 한글·—·✗ 가 깨진다 — 끝에서 원래 값 복원.
# 콘솔 없는 프로세스에선 대입이 예외(핸들이 잘못됨)를 던진다 — 삼키지 않으면 exit 1 로 게이트 전체가 빠진다.
$prevOutEnc = $null
try { $prevOutEnc = [Console]::OutputEncoding; [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
try {

# ── 1. 옛 형식 차단 ─────────────────────────────────────────────────────────
try {
    $added = @(& git -C $cwd diff --cached -U0 -- $rel 2>$null)
} catch { $added = @() }
$old = @($added | Where-Object { $_ -match '^\+\|\s*\d{4}-\d{2}-\d{2}\s*\|\s*T-\d{3}' -or $_ -match '^\+##\s+T-\d{3}\.' })
if ($old.Count -gt 0) {
    Write-StderrUtf8 @"
[BLOCKED] claude-docs/troubleshooting.md 에 옛 단일 파일 형식 줄이 추가됐습니다 — 2026-09-25 분할 이관됨.

$(($old | Select-Object -First 3 | ForEach-Object { '  ' + $_.Substring(0, [Math]::Min(120, $_.Length)) }) -join "`n")

항목은 claude-docs/troubleshooting/T-###.md 파일 1개로 쓰고(frontmatter summary: + H1 ``# T-### · 제목``),
허브 목차는 자동 생성입니다(이 훅이 커밋 때 다시 만든다). 리베이스 충돌이면 허브는 origin/main 쪽을 받고
새 행의 내용을 T 파일로 옮기세요. 옛 재발 트래커 표는 claude-docs/troubleshooting-tracker.md(동결).
"@
    exit 2
}

# ── 2. 검사기(재생성 모드) ───────────────────────────────────────────────────
$hub = Join-Path $cwd $rel
$ErrorActionPreference = 'Continue'   # 검사기 stderr 가 예외로 바뀌지 않게
$out = (& powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $checker -HubPath $hub 2>&1 | Out-String)
$code = $LASTEXITCODE
if ($out -notmatch 'INDEX-CHECK:') {
    Write-StderrUtf8 "[BLOCKED] troubleshooting 검사가 돌지 않았다 — scripts/rebuild-troubleshooting-index.ps1 출력에 INDEX-CHECK: 마커가 없다(exit $code).`n$out"
    exit 2
}
if ($code -ne 0) {
    Write-StderrUtf8 "[BLOCKED] troubleshooting 항목 형식 오류 — 고친 뒤 다시 커밋하세요.`n$out"
    exit 2
}
& git -C $cwd add -- $rel 2>$null | Out-Null
exit 0

} finally { if ($prevOutEnc) { try { [Console]::OutputEncoding = $prevOutEnc } catch {} } }
