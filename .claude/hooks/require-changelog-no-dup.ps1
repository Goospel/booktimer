# require-changelog-no-dup.ps1
#
# WARNING: UTF-8 BOM 포함으로 저장(PowerShell 5.1 한글 주석 깨짐 회피, T-026 계열).
#
# PreToolUse 게이트: `git push` 직전에 claude-docs/changelog.md 의 **중복 행**을 막는다.
#
# 왜 이 함정이 구조적인가 (T-210):
#   .gitattributes 의 `claude-docs/changelog.md merge=union`(T-098 하드픽스)은 양쪽 새 행을
#   마커 없이 둘 다 남긴다. 두 세션이 각자 맨 아래에 행을 붙이는 경우엔 그게 정답이다.
#   그런데 **한 브랜치가 자기 행을 「추가 -> 수정」**하면(기능 커밋에서 행 추가, 리뷰 반영
#   커밋에서 그 행 보강 — 이 레포의 표준 흐름이다) rebase 가 커밋을 하나씩 재적용하면서
#   union 이 초판과 최종본을 **둘 다** 남긴다. 충돌도 안 나고 테스트도 전부 통과한다.
#
# 그래서 이 훅이 유일한 계측기다. 판정은 「같은 날짜 + 같은 제목」 행이 둘 이상인가.
# fail-open: 무엇이든 실패하면 push 를 막지 않는다(가드가 작업을 멈춰 세우지 않는다).

$ErrorActionPreference = 'Stop'

# stdin 은 UTF-8 로 명시 디코딩한다 — [Console]::In 은 CP949 로 읽어 한글이 깨지고,
# 그러면 명령 판독이 조용히 빗나가 가드가 통과한다(글로벌 CLAUDE.md Windows 셸 원칙).
try {
    $reader = New-Object System.IO.StreamReader(
        [Console]::OpenStandardInput(), (New-Object System.Text.UTF8Encoding($false)))
    $raw  = $reader.ReadToEnd()
    $data = $raw | ConvertFrom-Json
    $cmd  = [string]$data.tool_input.command
} catch { exit 0 }

if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }
if ($cmd -notmatch '\bgit\b' -or $cmd -notmatch '\bpush\b') { exit 0 }
if ($cmd -match 'ALLOW_CHANGELOG_DUP') { exit 0 }

$cwd = [string]$data.cwd
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

$path = Join-Path $cwd 'claude-docs\changelog.md'
if (-not (Test-Path $path)) { exit 0 }

try {
    $lines = [System.IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)
} catch { exit 0 }

# 표 행의 신원 = 날짜 + 굵은 제목. 본문은 리뷰 반영으로 길어지므로 키에 넣지 않는다
# (넣으면 초판과 최종본이 다른 행으로 보여 이 훅이 아무것도 못 잡는다).
$seen = @{}
$dups = New-Object System.Collections.ArrayList
foreach ($line in $lines) {
    if ($line -notmatch '^\|\s*(\d{4}-\d{2}-\d{2})\s*\|\s*\*\*(.+?)\*\*') { continue }
    $key = $Matches[1] + ' | ' + $Matches[2]
    if ($seen.ContainsKey($key)) {
        if (-not $dups.Contains($key)) { $null = $dups.Add($key) }
    } else {
        $seen[$key] = $true
    }
}

if ($dups.Count -eq 0) { exit 0 }

$list = ($dups | ForEach-Object { '  - ' + $_ }) -join "`n"
$blockMsg = @"
[BLOCKED] claude-docs/changelog.md 에 중복 행이 있습니다 (T-210).

$list

왜 생기나: .gitattributes 의 ``claude-docs/changelog.md merge=union``(T-098)은 양쪽 새 행을
둘 다 남깁니다. 두 세션이 각자 행을 붙일 땐 정답이지만, **한 브랜치가 자기 행을
「추가 -> 수정」**하면(기능 커밋에서 추가, 리뷰 반영 커밋에서 보강) rebase 가 커밋을
하나씩 재적용하며 초판과 최종본을 **둘 다** 남깁니다. 충돌도 안 나고 테스트도 통과합니다.

고치는 법: 최종본(보통 더 긴 쪽)만 남기고 초판 행을 지운 뒤 다시 커밋하세요.

예외로 통과시키려면 명령에 ALLOW_CHANGELOG_DUP 토큰을 포함하세요.
"@
[Console]::Error.WriteLine($blockMsg)
exit 2
