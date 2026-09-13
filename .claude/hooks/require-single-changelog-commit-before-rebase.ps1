# require-single-changelog-commit-before-rebase.ps1
#
# WARNING: UTF-8 BOM 포함으로 저장(PowerShell 5.1 한글 주석 깨짐 회피, T-026 계열).
#
# PreToolUse 게이트: `git rebase` **직전**에, 재적용될 브랜치가 claude-docs/changelog.md 를
# 건드린 커밋이 2개 이상이면 차단한다.
#
# 왜 push 가 아니라 rebase 직전인가 (T-210, 7회 재발):
#   `.gitattributes` 의 `claude-docs/changelog.md merge=union`(T-098)은 양쪽 새 행을
#   마커 없이 둘 다 남긴다. rebase 는 커밋을 하나씩 재적용하므로, 한 브랜치가 자기 행을
#   두 커밋에서 건드리면(기능 커밋에서 추가 -> 리뷰 커밋에서 보강 — 이 레포의 표준 흐름)
#   union 이 초판과 최종본을 **둘 다** 남긴다. 충돌도 테스트 실패도 없다. 변종으로,
#   초판을 지우는 정리 커밋은 git 이 `patch contents already upstream` 으로 버려서
#   다음 rebase 에 초판이 되살아난다(T-210 (7)).
#   처방은 **rebase 전에 한 커밋으로 합치기**인데, 7회 재발 동안 prose 가 그 반사를
#   한 번도 못 바꿨다. 기존 훅 require-changelog-no-dup.ps1 은 `git push` 시점이라
#   늦다 — 매번 수동 확인이 먼저 잡아 7회 연속 미발화였다. 그래서 손상이 생기는
#   자리(rebase) 바로 앞에 선다.
#
# 판정은 **행 신원이 아니라 커밋 수**다: 서로 다른 행 2개를 두 커밋에서 건드린 경우도
# 차단한다. union 이 재적용마다 남기는 것은 「같은 행인가」와 무관하기 때문이다.
#
# fail-open 지점은 다섯 곳이다(전부 stderr 에 한 줄 남긴다 — 무흔적 통과 금지):
#   ① stdin/JSON 파싱 실패  ② changelog 없는 레포  ③ merge-base 실패(명시 대상 브랜치 없음)
#   ④ rev-list 무출력       ⑤ 훅 자체 예외
# 반대로 **판정 불가는 차단**한다: 위치 인자가 셋 이상이거나, 대상 브랜치를 명시했는데
# 그 ref 를 측정할 수 없을 때 — 「모르겠으니 통과」는 이 함정이 7회 새어 나온 방식이다.
#
# 알려진 한계(이번에 못 고친 것):
#   - `gh pr update-branch --rebase` 는 **서버사이드**라 이 훅이 볼 git 명령이 없다.
#     GitHub 가 union 드라이버를 적용하는지도 미판별 — 게이트 대상 밖이다.
#   - `bash .claude/scripts/pr-merge.sh <PR> --rebase` 처럼 rebase 를 **감싼 스크립트**는
#     명령 문자열에 `git rebase` 가 없어 이 훅을 지나간다 -> 그 스크립트의 try_rebase()
#     안에 같은 판정을 따로 심었다(단일 출처가 아니라 두 군데인 것이 이 구조의 비용).
#   - PreToolUse 라 Bash/PowerShell 호출마다 powershell 프로세스 1개가 더 뜬다(≈0.5초).

$ErrorActionPreference = 'Stop'

# stdin 은 UTF-8 로 명시 디코딩한다 — 기본 입력 리더 은 CP949 로 읽어 한글이 깨지고,
# 그러면 명령 판독이 조용히 빗나가 가드가 통과한다(글로벌 CLAUDE.md Windows 셸 원칙).
try {
    $reader = New-Object System.IO.StreamReader(
        [Console]::OpenStandardInput(), (New-Object System.Text.UTF8Encoding($false)))
    $raw  = $reader.ReadToEnd()
    $data = $raw | ConvertFrom-Json
    $cmd  = [string]$data.tool_input.command
} catch {
    # 여기선 아직 lib 를 dot-source 하지 않았으므로 WriteLine 을 쓴다(영문이라 안 깨진다).
    [Console]::Error.WriteLine('[require-single-changelog-commit-before-rebase] stdin/JSON parse failed - passing (fail-open).')
    exit 0
}

if ([string]::IsNullOrWhiteSpace($cmd)) { exit 0 }
if ($cmd -match 'ALLOW_MULTI_CHANGELOG_REBASE') { exit 0 }

# ── rebase 인가? ────────────────────────────────────────────────────────────
# **서브커맨드 위치를 고정한다.** 낱말 `rebase` 만 보면 `git pull --no-rebase` ·
# `git log --grep=rebase` · `git commit -m "gate before rebase"` 가 전부 오탐 차단이었다
# (리뷰 실측). lib\resolve-target-cwd.ps1 의 gitRe 와 같은 형태로, git 과 서브커맨드
# 사이에는 **전역 옵션만** 허용한다.
$globalOpt = '(?:\s+(?:-C\s+(?:"[^"]*"|''[^'']*''|[^\s;&|]+)|-c\s+\S+|-{1,2}[\w-]+(?:=\S+)?))*'
# 인자는 **그 줄까지만** 모은다(`[^;&|\r\n]*`) — 줄바꿈을 넘으면 heredoc 본문의 다음 줄까지
# 인자로 읽어, 산문 한 줄이 위치 인자 둘로 보이고 아래 「판정 불가 -> 차단」에 걸린다.
# 한 줄 = 한 명령이므로 이게 옳은 경계이기도 하다.

$verb   = $null
$rargs  = ''
# `git pull --rebase` 를 먼저 본다 — 나중에 보면 rebase 분기가 `--rebase` 뒤의
# `origin main` 을 ref 로 집어 merge-base 실패 -> fail-open 으로 무성 통과한다.
foreach ($s in [regex]::Matches($cmd, '\bgit\b' + $globalOpt + '\s+pull\b([^;&|\r\n]*)')) {
    if ($s.Groups[1].Value -match '(--rebase\b|(?<!\S)-r\b)') { $verb = 'pull'; break }
}
if (-not $verb) {
    # **모든** 매치를 돈다 — `git rebase --abort && git rebase origin/main` 은 첫 세그먼트가
    # 제어 verb 라, 첫 매치만 보면 뒤의 진짜 rebase 가 무성 통과했다(리뷰 실측).
    foreach ($s in [regex]::Matches($cmd, '\bgit\b' + $globalOpt + '\s+rebase\b([^;&|\r\n]*)')) {
        if ($s.Groups[1].Value -match '--(abort|continue|skip|quit|edit-todo)\b') { continue }
        $verb = 'rebase'; $rargs = $s.Groups[1].Value; break
    }
}
if (-not $verb) { exit 0 }

# ── 무엇을 어디에 재적용하는가 ──────────────────────────────────────────────
#   git rebase [<upstream> [<branch>]]            -> <upstream>..<branch>
#   git rebase --onto <new> <upstream> [<branch>] -> <upstream>..<branch>
# `<branch>` 를 명시하면 git 은 그것을 체크아웃해 재적용한다 — 그때 HEAD 를 세면 엉뚱한
# 브랜치를 재고 0 으로 통과한다(리뷰 실측). 값을 별도 토큰으로 받는 옵션은 그 값까지 건너뛴다.
$pos = New-Object System.Collections.ArrayList
$skipNext = $false
foreach ($t in ($rargs -split '\s+')) {
    if ([string]::IsNullOrWhiteSpace($t)) { continue }
    if ($skipNext) { $skipNext = $false; continue }
    if ($t.StartsWith('-')) {
        if ($t -match '^(--onto|--exec|-x|--strategy|-s|--strategy-option|-X|--whitespace|--gpg-sign|-S|-C)$') { $skipNext = $true }
        continue
    }
    $null = $pos.Add($t.Trim('"', "'"))
}

$ref            = if ($pos.Count -ge 1) { [string]$pos[0] } else { 'origin/main' }
$target         = if ($pos.Count -ge 2) { [string]$pos[1] } else { 'HEAD' }
$explicitTarget = ($pos.Count -ge 2)

# rebase 가 실제로 도는 워크트리를 본다(`cd "<다른 워크트리>" && git rebase …`, T-242)
$cwd = [string]$data.cwd
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }
. (Join-Path $PSScriptRoot 'lib\resolve-target-cwd.ps1')
$cwd = Resolve-HookTargetCwd $cmd $cwd $verb
if ($null -eq $cwd) { Stop-UnresolvedTarget $verb }

if (-not (Test-Path (Join-Path $cwd 'claude-docs\changelog.md'))) {
    exit 0   # ② BookTimer 아님 — 조용히 통과해도 되는 유일한 자리다
}

function Stop-Undecidable([string]$Why) {
    Write-StderrUtf8 @"
[BLOCKED] 이 rebase 가 changelog 를 몇 커밋에서 건드리는지 판정할 수 없습니다 (T-210).

$Why

「모르겠으니 통과」는 이 함정이 7회 새어 나온 방식이라, 판정 불가는 차단합니다.
직접 확인하고 진행하려면 명령에 ALLOW_MULTI_CHANGELOG_REBASE 토큰을 포함하세요.
"@
    exit 2
}

if ($pos.Count -gt 2) {
    Stop-Undecidable "위치 인자가 $($pos.Count) 개입니다($($pos -join ' ')) — 어느 것이 재적용 대상 브랜치인지 못 가립니다."
}

# 성패는 종료코드가 아니라 출력으로 판정한다(`2>$null` 뒤 $LASTEXITCODE 는 못 믿는다, T-206)
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $base = [string](& git -C $cwd merge-base $target $ref 2>$null)
    if ([string]::IsNullOrWhiteSpace($base)) {
        if ($explicitTarget) {
            $ErrorActionPreference = $prevEAP
            Stop-Undecidable "대상 브랜치 '$target' 과 '$ref' 의 merge-base 를 구할 수 없습니다 — 재적용 대상은 명시됐는데 측정이 안 됩니다."
        }
        Write-StderrUtf8 "[require-single-changelog-commit-before-rebase] merge-base $target $ref failed - passing (fail-open)."   # ③
        exit 0
    }
    $base  = $base.Trim()
    $count = [string](& git -C $cwd rev-list --count "$base..$target" -- 'claude-docs/changelog.md' 2>$null)
} catch {
    Write-StderrUtf8 '[require-single-changelog-commit-before-rebase] hook error - passing (fail-open).'   # ⑤
    exit 0
} finally { $ErrorActionPreference = $prevEAP }

if ([string]::IsNullOrWhiteSpace($count)) {
    Write-StderrUtf8 "[require-single-changelog-commit-before-rebase] rev-list gave no output for $base..$target - passing (fail-open)."   # ④
    exit 0
}
$n = 0
if (-not [int]::TryParse($count.Trim(), [ref]$n)) {
    Write-StderrUtf8 "[require-single-changelog-commit-before-rebase] unparsable rev-list count '$count' - passing (fail-open)."   # ④
    exit 0
}
if ($n -lt 2) { exit 0 }

$what = if ($explicitTarget) { "브랜치 '$target' 이" } else { '이 브랜치가' }
$blockMsg = @"
[BLOCKED] $what claude-docs/changelog.md 를 건드린 커밋이 $n 개입니다 — rebase 전에 한 커밋으로 합치세요 (T-210).

왜: .gitattributes 의 ``claude-docs/changelog.md merge=union``(T-098)은 양쪽 새 행을 둘 다 남깁니다.
rebase 는 커밋을 하나씩 재적용하므로 한 브랜치가 changelog 를 두 번 건드리면 초판과 최종본이
**둘 다** 남습니다(충돌도 테스트 실패도 없음). 초판을 지우는 정리 커밋을 쌓으면 git 이 그것을
``already upstream`` 으로 버려 다음 rebase 에 초판이 되살아납니다 — 7회 재발한 자리입니다.
세는 것은 **커밋 수**이고 행 신원이 아닙니다 — 서로 다른 행 둘을 두 커밋에서 건드려도 막습니다.

고치는 법:
  git reset --soft $base
  (.commit-msg-tmp 절차로 한 커밋으로 다시 커밋 — T-026)
  그 뒤 다시 rebase

예외로 통과시키려면 명령에 ALLOW_MULTI_CHANGELOG_REBASE 토큰을 포함하세요.
"@
# 한글 메시지는 원바이트 UTF-8 로 직접 쓴다(Write-StderrUtf8) — WriteLine 은 CP949 를 거쳐 깨진다.
# 쓰기가 실패해도 exit 2 를 유지한다: 메시지를 못 쓰는 것이 통과의 이유가 될 수는 없다.
Write-StderrUtf8 $blockMsg
exit 2
