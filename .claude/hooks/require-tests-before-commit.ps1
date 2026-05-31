# PreToolUse hook — 커밋 전 테스트 게이트 (TDD 강제)
#
# BookTimer 규칙(CLAUDE.md): 기능 구현 시 테스트를 먼저. 이 훅은 'git commit' 을
# 가로채, 스테이징에 .java 변경이 있으면 `./gradlew test` 를 돌리고
# 실패하면 exit 2 로 커밋을 차단한다.
#
# - 문서/설정 전용 커밋(.java 변경 없음)은 자동으로 건너뛴다 (테스트 불필요).
# - 예외(override): 명령에 토큰 `SKIP_TESTS` 가 포함되면 통과
#     → TDD red 단계(실패 테스트만 먼저 커밋), 긴급 핫픽스 등. 사용자 허용 시에만.
#
# 차단 메시지는 영문(ASCII) — 훅 stderr 한글 깨짐 회피 (block-main-push.ps1 참조).

$ErrorActionPreference = 'Stop'

try {
    $raw  = [Console]::In.ReadToEnd()
    $data = $raw | ConvertFrom-Json
    $cmd  = [string]$data.tool_input.command
} catch {
    exit 0   # 입력 파싱 실패 시 fail-open
}

if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }

# git commit 이 아니면 관심 없음
if ($cmd -notmatch '\bgit\b' -or $cmd -notmatch '\bcommit\b') { exit 0 }

# 명시적 override 토큰
if ($cmd -match 'SKIP_TESTS') { exit 0 }

$cwd = [string]$data.cwd
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }

# 스테이징된 변경 중 .java 가 있는지 확인 → 없으면 테스트 불필요 (문서/설정 커밋)
try {
    $staged = & git -C $cwd diff --cached --name-only 2>$null
} catch {
    $staged = @()
}
$javaChanged = @($staged | Where-Object { $_ -match '\.java$' })
if ($javaChanged.Count -eq 0) { exit 0 }

# gradlew 가 없으면 강제 불가 → 통과 (fail-open)
$gradlew = Join-Path $cwd 'gradlew.bat'
if (-not (Test-Path $gradlew)) { exit 0 }

# 테스트 실행 (출력은 버리고 종료코드만 사용)
& $gradlew -p $cwd test --console=plain *> $null
$testExit = $LASTEXITCODE

if ($testExit -ne 0) {
    $msg = @"
[BLOCKED] Tests failed -- commit aborted (TDD gate).

BookTimer rule (CLAUDE.md): write tests first; commits with .java changes
must pass `./gradlew test`.

Fix the failing tests (or the code) and commit again. To see details:
  ./gradlew test

Override only when intentional (e.g. committing a failing RED test first,
or an emergency hotfix the user approved): include the token SKIP_TESTS
in the commit command to bypass this gate.
"@
    [Console]::Error.WriteLine($msg)
    exit 2
}

exit 0
