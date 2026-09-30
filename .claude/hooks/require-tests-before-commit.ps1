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
    # UTF-8 explicitly: Console.In decodes stdin as CP949, where a Korean lead byte can
    # swallow the next quote -> JSON parse fails -> fail-open silently skips this gate.
    $raw  = (New-Object System.IO.StreamReader([Console]::OpenStandardInput(), (New-Object System.Text.UTF8Encoding($false)))).ReadToEnd()
    $data = $raw | ConvertFrom-Json
    $cmd  = [string]$data.tool_input.command
} catch {
    exit 0   # 입력 파싱 실패 시 fail-open
}

if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }

# git commit 이 아니면 관심 없음. 첫 줄 = 빠른 거르기(대부분의 호출이 lib 를 안 읽고 끝난다),
# 둘째 = 서브커맨드 자리 판정 — 낱말만 보면 `rm -f .commit-msg-tmp && git add -A` 에도 걸린다(T-078 4회차)
if ($cmd -notmatch '\bgit\b' -or $cmd -notmatch '\bcommit\b') { exit 0 }
. (Join-Path $PSScriptRoot 'lib\resolve-target-cwd.ps1')
if (-not (Test-GitVerb $cmd 'commit')) { exit 0 }

# 명시적 override 토큰
if ($cmd -match 'SKIP_TESTS') { exit 0 }

$cwd = [string]$data.cwd
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }
# 세션 cwd 가 아니라 커밋이 실제로 도는 워크트리를 본다(`cd "<다른 워크트리>" && git commit`, T-242)
$cwd = Resolve-HookTargetCwd $cmd $cwd 'commit'
if ($null -eq $cwd) { Stop-UnresolvedTarget 'commit' }

# 이 커밋이 건드릴 파일 목록 → .java 가 없으면 테스트 불필요 (문서/설정 커밋)
# 인덱스만 보면 안 된다(T-228): 커밋 명령이 스스로 스테이징하면
# (`git add -A && git commit ...`, `git commit -am ...`) PreToolUse 시점의
# 인덱스는 아직 비어 있어 게이트가 조용히 통과한다 — `./gradlew test` 가
# 아예 안 도는 채로 커밋된다. 그런 명령이면 작업 트리까지 합쳐서 본다.
# fail-safe 방향: 게이트가 불필요하게 도는 쪽으로 기운다(안 도는 쪽이 아니라).
$selfStages = ($cmd -match '\bgit\s+(add|stage)\b') -or
              ($cmd -match '\bgit\s+commit\b[^|&;]*\s(--all\b|-[a-zA-Z]*a[a-zA-Z]*\b)')

# ⚠️ EAP='Stop' 에서는 git 의 stderr 경고("LF will be replaced by CRLF")가
# NativeCommandError 로 승격돼 목록 수집이 통째로 예외가 된다 → 빈 목록 → 조용한 통과.
# (이 파일 아래 gradlew 주석과 같은 PowerShell 5.1 함정.) 수집 동안만 Continue.
$prevEAP0 = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $changed = @(& git -C $cwd diff --cached --name-only 2>$null)
    if ($selfStages) {
        $changed += @(& git -C $cwd diff --name-only 2>$null)                     # 수정된 추적 파일
        $changed += @(& git -C $cwd ls-files --others --exclude-standard 2>$null)  # 새 미추적 파일
    }
} catch {
    $changed = @()
} finally {
    $ErrorActionPreference = $prevEAP0
}
$javaChanged = @($changed | Where-Object { $_ -match '\.java$' })
# ── 프론트엔드 게이트 — garden.html 또는 frontend/** 변경 시 npm test ──────────
$frontChanged = @($changed | Where-Object { $_ -match 'garden\.html$|^frontend/' })
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

# 테스트 실행 — 종료코드만 사용. 무한 hang(T-078: gradle 데몬/빌드 락 경합) 방지를 위해 타임아웃으로 감싼다.
#   → Start(비동기) + WaitForExit(타임아웃). 초과 시 **게이트 자신의** 프로세스 트리(cmd→gradlew→java 클라이언트
#     →이 게이트가 새로 띄운 데몬)를 taskkill /T 로 죽이고 fail-closed(exit 2)로 차단한다 — 통과 여부를 모르니 차단이 안전.
#     `gradlew --stop` 은 쓰지 않는다: 이 머신의 **모든** 데몬(다른 세션이 빌드 중인 것까지)을 멈춘다(T-235 곁다리).
#     이미 떠 있던 데몬을 빌려 썼다면 클라이언트가 죽는 순간 그 데몬이 빌드를 취소하고 idle 로 돌아간다.
# ShellExecute 로 띄운다(UseShellExecute=$true): CreateProcess(=$false)는 핸들 상속을 켜서 이 훅의 stdout/stderr
#   파이프(Claude Code 가 준 것)가 cmd→java→**새 데몬**까지 내려간다. Claude Code 는 훅 종료가 아니라 파이프 EOF 를
#   기다리고 데몬은 idle 로 최대 3시간 산다 — 2026-09-29 Bash 호출이 32분·3시간 38분 멈췄다(T-078 4회차).
#   ShellExecuteEx 는 핸들을 물려주지 않는다. 회귀 가드: tests/test-require-tests-timeout.sh Case 10.
# 주의(PowerShell 5.1): gradlew 는 JDK 경고 등을 stderr 로 내보내는데,
# $ErrorActionPreference='Stop' 상태에서 native stderr 는 terminating error 로
# 승격되어(NativeCommandError) 테스트가 통과해도 스크립트가 죽는다.
# → cmd.exe 자식 프로세스로 격리 실행하고 종료코드만 본다(redirection 은 cmd 내부 >nul).
#
# 예산(T-235 4회차 실측): 배터리·모던 대기·부하면 같은 스위트가 3~4배 느리다 --
# BCrypt 강도 4 적용 전 AC·화면 켜짐 약 4분 vs 배터리 대기 11~18분+ (적용 후 AC 약 2분 15초,
# 배터리는 미측정). 그래서 기본 20분, 환경변수로도 24분까지만 (cap).
# ⚠️ 상한 + 5분 예비가 .claude/settings.json 의 이 훅 "timeout"(1800초) 이하여야 한다 --
# Claude Code 훅 타임아웃은 fail-open이다 (docs "A timed-out command ... hook doesn't
# block the tool call"). 훅이 취소되면 테스트 없이 커밋이 통과한다. 예비 5분은 위의
# 타임아웃 없는 npm frontend test와 타임아웃 뒤 taskkill 몫이다.
# 순서 불변식(기본 <= 상한, 상한 + 300s <= settings)은 tests/test-require-tests-timeout.sh Case 8이 잡는다.
$timeoutMs    = 20 * 60 * 1000   # 기본 20분
$maxTimeoutMs = 24 * 60 * 1000   # 환경변수 상한 24분 (+ 예비 5분 = 29min <= settings.json 1800s)
if ($env:BOOKTIMER_TEST_GATE_TIMEOUT_MS) {
    $parsed = 0
    if ([int]::TryParse($env:BOOKTIMER_TEST_GATE_TIMEOUT_MS, [ref]$parsed) -and $parsed -gt 0) {
        $timeoutMs = [math]::Min($parsed, $maxTimeoutMs)
    }
}

$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'Continue'

# cmd.exe /c 의 canonical 인용: 바깥 한 쌍이 명령 전체를, 안쪽이 경로를 감싼다(`"" "exe" args "`).
$gateArgs = '/c ""' + $gradlew + '" -p "' + $cwd + '" test --console=plain >nul 2>nul"'
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName        = 'cmd.exe'
$psi.Arguments       = $gateArgs
$psi.UseShellExecute = $true    # 핸들 비상속 — 위 주석(T-078 4회차). CreateNoWindow 는 이 모드에서 무시된다
$psi.WindowStyle     = [System.Diagnostics.ProcessWindowStyle]::Hidden
# 시작 실패는 fail-closed — EAP=Continue 라 $proc 가 $null 이면 아래 WaitForExit 가 문장째 건너뛰어 exit 0(무검사 커밋)이 된다
try { $proc = [System.Diagnostics.Process]::Start($psi) } catch { $proc = $null }
if ($null -eq $proc) {
    [Console]::Error.WriteLine('[BLOCKED] Could not start the gradle test gate (cmd.exe) -- commit aborted. Override: SKIP_TESTS.')
    exit 2
}

$testExit = 0
$timedOut = $false
if ($proc.WaitForExit($timeoutMs)) {
    $testExit = $proc.ExitCode
} else {
    $timedOut = $true
    # 게이트 자신의 트리만 죽인다(새로 띄운 데몬은 클라이언트의 자식이라 함께 잡힌다). --stop 은 머신 전역이라 안 쓴다.
    cmd.exe /c "taskkill /T /F /PID $($proc.Id) >nul 2>nul"
}

$ErrorActionPreference = $prevEAP

if ($timedOut) {
    $tmin = [math]::Round($timeoutMs / 60000.0, 1)
    # 전원 상태 -- 느린 정상 실행인지 가르는 첫 단서 (T-235). 조회 실패는 unknown.
    $power = 'unknown'
    try {
        $bs = @(Get-CimInstance -Namespace root/wmi -ClassName BatteryStatus -OperationTimeoutSec 5 -ErrorAction Stop)
        if ($bs.Count -gt 0 -and $null -ne $bs[0].PowerOnline) {
            if ($bs[0].PowerOnline) { $power = 'AC' } else { $power = 'battery' }
        }
    } catch { $power = 'unknown' }
    $msgTimeout = @"
[BLOCKED] Test gate exceeded ${tmin} min -- commit aborted. The gate's own gradle
process tree was killed; daemons of other sessions were left alone.

Two possible causes:
  (1) A slow but healthy run. The full suite takes ~2-2.5 min on AC with the screen
      on (after the BCrypt-4 test change; ~4 min before it). Battery / modern standby
      / load was 3-4x slower (pre-change: 11-18 min; post-change unmeasured) (T-235).
  (2) Gradle daemon / build lock contention with another session or a leftover
      bootRun daemon (T-078).
Power now: $power

Recommended: if no stray java process is running, run './gradlew test' to completion
OUTSIDE the gate, then commit again right away without touching sources -- the test
task is up-to-date, so the gate passes in seconds.

Do not clean up with 'gradlew --stop': it stops every session's daemons, including
builds running in other worktrees (T-235, T-078).

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
