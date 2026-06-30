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
# ── 프론트엔드 게이트 — garden.html 또는 frontend/** 변경 시 npm test ──────────
$frontChanged = @($staged | Where-Object { $_ -match 'garden\.html$|^frontend/' })
if ($frontChanged.Count -gt 0) {
    $nodeCmd = (Get-Command node -ErrorAction SilentlyContinue)
    if (-not $nodeCmd) {
        [Console]::Error.WriteLine('[WARN] node not found — frontend test gate skipped (install Node.js to enforce)')
    } else {
        $prevEAP2 = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        cmd.exe /c "npm --prefix `"$cwd\frontend`" test >nul 2>nul"
        $frontExit = $LASTEXITCODE
        $ErrorActionPreference = $prevEAP2
        if ($frontExit -ne 0) {
            $msg2 = @"
[BLOCKED] Frontend tests failed -- commit aborted (frontend TDD gate).

BookTimer rule (CLAUDE.md): commits with garden.html or frontend/** changes
must pass `npm --prefix frontend test`.

Fix the failing tests (or the code) and commit again. To see details:
  npm --prefix frontend test

Override: include the token SKIP_TESTS in the commit command to bypass.
"@
            [Console]::Error.WriteLine($msg2)
            exit 2
        }
    }
}

if ($javaChanged.Count -eq 0) { exit 0 }

# gradlew 가 없으면 강제 불가 → 통과 (fail-open)
$gradlew = Join-Path $cwd 'gradlew.bat'
if (-not (Test-Path $gradlew)) { exit 0 }

# 테스트 실행 — 종료코드만 사용. 무한 hang(T-078: gradle 데몬/빌드 락 경합) 방지를 위해
# 타임아웃으로 감싼다. 멀티세션이거나 정리 안 된 bootRun 데몬이 빌드 락을 쥐고 있으면
# `./gradlew test` 가 영영 안 끝나 커밋 게이트가 통째로 얼어붙는다(과거 45분 freeze).
#   → Start(비동기) + WaitForExit(타임아웃). 초과 시 프로세스 트리(cmd→gradlew→java)를
#     taskkill /T 로 죽이고 `gradlew --stop` 으로 데몬을 정리(자가복구)한 뒤,
#     fail-closed(exit 2)로 차단한다 — 테스트 통과 여부를 알 수 없으니 차단이 안전.
# 주의(PowerShell 5.1): gradlew 는 JDK 경고 등을 stderr 로 내보내는데,
# $ErrorActionPreference='Stop' 상태에서 native stderr 는 terminating error 로
# 승격되어(NativeCommandError) 테스트가 통과해도 스크립트가 죽는다.
# → cmd.exe 자식 프로세스로 격리 실행하고 종료코드만 본다(redirection 은 cmd 내부 >nul).
$timeoutMs = 8 * 60 * 1000   # 기본 8분 — 정상 전체 테스트(~2.5분)보다 충분히 큼
if ($env:BOOKTIMER_TEST_GATE_TIMEOUT_MS) {
    $parsed = 0
    if ([int]::TryParse($env:BOOKTIMER_TEST_GATE_TIMEOUT_MS, [ref]$parsed) -and $parsed -gt 0) {
        $timeoutMs = $parsed
    }
}

$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'Continue'

# cmd.exe /c 의 canonical 인용: 바깥 한 쌍이 명령 전체를, 안쪽이 경로를 감싼다(`"" "exe" args "`).
$gateArgs = '/c ""' + $gradlew + '" -p "' + $cwd + '" test --console=plain >nul 2>nul"'
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName        = 'cmd.exe'
$psi.Arguments       = $gateArgs
$psi.UseShellExecute = $false
$psi.CreateNoWindow  = $true
$proc = [System.Diagnostics.Process]::Start($psi)

$testExit = 0
$timedOut = $false
if ($proc.WaitForExit($timeoutMs)) {
    $testExit = $proc.ExitCode
} else {
    $timedOut = $true
    # 매달린 프로세스 트리 강제 종료 + 데몬 정리(다음 커밋이 같은 경합에 또 안 걸리게)
    cmd.exe /c "taskkill /T /F /PID $($proc.Id) >nul 2>nul"
    cmd.exe /c "`"$gradlew`" -p `"$cwd`" --stop >nul 2>nul"
}

$ErrorActionPreference = $prevEAP

if ($timedOut) {
    $tmin = [math]::Round($timeoutMs / 60000.0, 1)
    $msgTimeout = @"
[BLOCKED] Test gate exceeded ${tmin} min -- commit aborted (likely gradle daemon/lock
contention, T-078). The hung gradle process tree was killed and `gradlew --stop` was
run to clear daemons.

This usually means another session (or a leftover bootRun daemon) is holding the gradle
build lock. End stray builds (`./gradlew --stop`; free port 8080) and commit again.

Override only when intentional: include the token SKIP_TESTS in the commit command.
"@
    [Console]::Error.WriteLine($msgTimeout)
    exit 2
}

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
