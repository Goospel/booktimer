# block-npm-over-junction.ps1 — PreToolUse(Bash|PowerShell) 가드 (T-271)
#
# WARNING: UTF-8 BOM 포함으로 저장(PowerShell 5.1 한글 깨짐 회피, T-026 계열).
#
# 워크트리의 frontend·miniapp/node_modules 는 메인 체크아웃 node_modules 로 가는 정션이다(link-node-modules.ps1, N-132).
# 정션 위에서 node_modules 를 다시 짜는 npm 명령은 정션 대상(메인)을 망가뜨린다 — 2026-10-20 실측(npm 11.9.0):
#   ci 계열      : 설치 전에 node_modules 안 항목을 하나씩 지운다(lib/commands/ci.js) → 메인 128개 → 0개
#   install 계열 : 지금 npm 은 정션부터 끊고 새로 깐다(arborist #validateNodeModules) — 단 --force 면 그 검사를 건너뛰어
#                  대상 항목을 숨김 이름으로 치운다. 옛 npm 은 --force 없이도 대상 트리를 직접 고쳤다(T-124)
# 세션 시작의 전역 「설치 없음 → npm ci」 안내는 프로젝트 훅이 정션을 걸기 전 상태를 보고 찍힌다(두 SessionStart 훅 병렬).
# 그래서 npm 이 실제로 도는 폴더(--prefix·-C·cd·하위 폴더면 위로)의 node_modules 가 재분석 지점이면 막는다.
#
# 알려진 한계(테스트로 잠그지 않음): 래퍼 속 npm(bash -c "…"·cmd /c …·스크립트 파일 안) · 따옴표로 감싼 npm 전체 경로 ·
#   npm 접두 약어(install-cl)·camelCase(cleanInstall) · 동사 앞의 다른 값 옵션(--loglevel warn) · --prefix="공백 경로" ·
#   따옴표 문자열 속 `; npm ci`(산문을 명령으로 읽음 — 오탐 쪽) · 없는 --prefix 폴더면 세션 cwd 로 판정.
# 같은 명령에서 정션을 끊고 이어서 npm 을 돌리면 끊기 전 상태를 보고 막는다 — 의도다(PreToolUse 시점).
# 우회: 명령에 ALLOW_NPM_OVER_JUNCTION — 사용자가 명시적으로 지시한 경우에만.
# fail-open: 입력 파싱 실패는 통과(다른 훅과 같다). 대상 폴더를 모르면(변수 경로) 막는다(T-242 원칙).

$ErrorActionPreference = 'Stop'
try {
    # UTF-8 로 읽는다 — Console.In 은 CP949 라 따옴표 바로 앞 한글이 따옴표를 삼켜 JSON 파싱이 깨진다
    $raw  = (New-Object System.IO.StreamReader([Console]::OpenStandardInput(), (New-Object System.Text.UTF8Encoding($false)))).ReadToEnd()
    $data = $raw | ConvertFrom-Json
    $cmd  = [string]$data.tool_input.command
} catch { exit 0 }
if ($cmd -notmatch 'npm' -or $cmd -match 'ALLOW_NPM_OVER_JUNCTION') { exit 0 }

$cwd = [string]$data.cwd
if ([string]::IsNullOrWhiteSpace($cwd)) { $cwd = (Get-Location).Path }
. (Join-Path $PSScriptRoot 'lib\resolve-target-cwd.ps1')

# heredoc·PowerShell here-string 본문은 산문이다(heredoc 패턴은 lib 의 것과 같다)
$scan = [regex]::Replace($cmd, '(?s)<<-?[ \t]*([''"]?)(\w+)\1([^\n]*)\n(?:.*?\n)?[ \t]*\2[ \t]*(?=\r?\n|$)', '$3')
$scan = [regex]::Replace($scan, '(?s)@([''"])\r?\n.*?\r?\n\1@', '')

$tok   = '(?:"[^"]*"|''[^'']*''|[^\s;&|)]+)'
# 명령 자리(맨 앞 · ; & | ( { 줄바꿈 · then/do/else 뒤 — time·env·VAR=값·& 접두 허용)의 npm 만
$npmRe = '(?:^|[;&|({\r\n]|\b(?:then|do|else)\b)\s*(?:(?:time|command|nohup|env)\s+|\w+=' + $tok + '\s+|&\s*)*npm(?:\.cmd|\.ps1)?(?![\w.-])(?<rest>(?:[ \t]+' + $tok + ')*)'
# node_modules 를 다시 짜는 npm 명령 — npm 11.9.0 lib/utils/cmd-list.js 별칭까지(rebuild 는 정션을 안 건드려 뺐다)
$reify = '^(?:ci|clean-install|ic|install-clean|isntall-clean|install-ci-test|cit|clean-install-test|sit|install|add|i|in|ins|inst|insta|instal|isnt|isnta|isntal|isntall|install-test|it|uninstall|unlink|remove|rm|r|un|update|up|upgrade|udpate|dedupe|ddp|prune|link|ln)$'

foreach ($m in [regex]::Matches($scan, $npmRe, 'IgnoreCase')) {
    $toks = @([regex]::Matches($m.Groups['rest'].Value, $tok) | ForEach-Object { $_.Value })
    $verb = $null; $verb2 = $null; $prefix = $null
    for ($i = 0; $i -lt $toks.Count; $i++) {            # 동사 = 첫 비옵션 토큰(npm 과 같다). --prefix/-C 값은 건너뛴다
        $t = $toks[$i]
        if ($t -ceq '-C' -or $t -eq '--prefix') { if ($i + 1 -lt $toks.Count) { $prefix = $toks[$i + 1] }; $i++; continue }
        if ($t -match '^--prefix=(.+)$') { $prefix = $Matches[1]; continue }
        if ($t.StartsWith('-')) { continue }
        if (-not $verb) { $verb = $t.Trim('"', "'") } elseif (-not $verb2) { $verb2 = $t }
    }
    if ($verb -notmatch $reify -and -not ($verb -eq 'audit' -and $verb2 -eq 'fix')) { continue }

    # npm 이 실제로 도는 폴더: lib 은 「첫 `git <verb>` 앞의 이동」을 따라가므로 이 npm 자리에 합성 verb 를 찍는다.
    # --prefix X 는 `cd X` 한 번 더로 넘겨 따옴표·/c/…·~·$VAR 를 lib 과 같게 다룬다
    $probe = $scan.Substring(0, $m.Index) + $(if ($prefix) { "`ncd $prefix" } else { '' }) + "`ngit npm-here"
    $dir = Resolve-HookTargetCwd $probe $cwd 'npm-here' -NoToplevel
    if ($null -eq $dir) {
        Write-StderrUtf8 @"
[BLOCKED] npm $verb target folder is unknown (T-271)

npm이 돌 폴더가 변수·확장식(`$VAR, %VAR%, ``…``, `$(…))으로 주어져 그 node_modules가 정션인지 판정할 수 없습니다.
정션이면 npm $verb가 정션 대상(메인 체크아웃)의 node_modules를 비우거나 고칩니다. 리터럴 경로로 다시 쓰세요:
  npm --prefix "C:/Users/.../frontend" $verb
사용자가 명시적으로 지시한 경우에만: 명령에 ALLOW_NPM_OVER_JUNCTION 토큰.
"@
        exit 2
    }
    if (-not $prefix) {   # npm 은 package.json·node_modules 가 있는 가장 가까운 위 폴더를 쓴다
        $d = $dir
        while ($d -and -not (Test-Path -LiteralPath (Join-Path $d 'package.json')) -and -not (Test-Path -LiteralPath (Join-Path $d 'node_modules'))) { $d = Split-Path -Parent $d }
        if ($d) { $dir = $d }
    }
    $nm = Get-Item -LiteralPath (Join-Path $dir 'node_modules') -Force -ErrorAction SilentlyContinue
    if (-not ($nm -and ($nm.Attributes -band [IO.FileAttributes]::ReparsePoint))) { continue }

    $full   = $nm.FullName
    $fwd    = $full -replace '\\', '/'
    $target = @($nm.Target) -join ', '
    $deps   = Join-Path $PSScriptRoot 'lib\deps-match-lock.js'
    Write-StderrUtf8 @"
[BLOCKED] npm $verb over a node_modules junction (T-271)

이 node_modules는 다른 폴더로 가는 정션입니다 — 대상은 보통 메인 체크아웃이고 다른 워크트리들도 같은 대상을 씁니다.
  정션: $full
  대상: $target
정션 위의 npm ci는 설치 전에 대상 안을 지웁니다(2026-10-20 메인 frontend/node_modules 128개 → 0개).
install 계열도 --force이거나 npm이 옛 버전이면 대상 트리를 직접 고칩니다(T-124).

1) 설치가 필요한지부터 봅니다. 정션이면 의존성은 이미 대상 것을 씁니다 — 세션 시작의
   「[node_modules 점검] … 설치 없음 → npm ci」 안내는 정션이 생기기 전에 찍혔을 수 있습니다.
     node "$deps" "$dir"     → exit 0이면 이 워크트리 lock과 맞음, 할 일 없음
2) 이 워크트리에 따로 깔아야 하면 정션만 먼저 끊고(대상은 그대로) 끊긴 것을 확인한 뒤, npm은 별도 명령으로 돌립니다:
     Git Bash  : cmd //c rmdir "$full" && ! test -e "$fwd" && echo cut
     PowerShell: [IO.Directory]::Delete('$full', `$false); Test-Path '$full'    # False 여야 함
   한 명령에 이어 붙이면 이 훅은 끊기 전 상태를 보고 다시 막습니다. Git Bash에서 cmd /c(슬래시 하나)는 정션을 못 끊습니다.

사용자가 명시적으로 지시한 경우에만: 명령에 ALLOW_NPM_OVER_JUNCTION 토큰.
"@
    exit 2
}
exit 0
