# resolve-target-cwd.ps1 — git 상태를 읽는 PreToolUse 훅들이 dot-source 한다 (T-242).
#
# WARNING: UTF-8 BOM 포함으로 저장(PowerShell 5.1 한글 주석 깨짐 회피, T-026 계열).
#
# 훅 입력의 cwd 는 **세션** cwd 다. 명령이 먼저 다른 곳으로 옮기면
#   cd "<다른 워크트리>" && git commit ...  /  git -C <path> commit  /  Set-Location <path>; git push
# 훅은 엉뚱한 레포를 검사해 게이트가 조용히 통과한다(2026-09-13 PR #1114: 테스트 게이트가
# 세션 워크트리의 빈 인덱스를 보고 다른 워크트리 커밋 2개에서 `./gradlew test` 를 건너뛰었다).
# 이 헬퍼는 명령 속 이동을 따라가 git 이 실제로 도는 워크트리의 **최상위**를 돌려준다.
#
#   이동 없음                        -> 세션 cwd 의 최상위 (하위 폴더 세션이면 gradlew 를 못 찾던 것까지 닫는다)
#   리터럴 경로인데 없음·레포 아님   -> 세션 cwd (`&&` 면 git 이 안 돌고, `;` 면 실제로 세션 cwd 에서 돈다)
#   확장식 경로($VAR, %VAR%, `, cd -) -> $null — 원리상 어느 레포인지 모른다. 차단할지 폴백할지는 훅이 정한다
#   -NoToplevel                      -> 최상위로 올리지 않고 git 이 실제로 도는 폴더(상대 `-F` 해석용)
#
# 커밋 위치와 무관한 이동은 걷어 낸다: heredoc 본문 · 닫힌 서브셸 `( … )` · 커밋 뒤의 이동.
# `cd "$(git rev-parse --show-toplevel)"` 는 세션 레포 최상위로 읽는다.
#
# 다른 레포로 옮겨 가도 따라간다 — 테스트 게이트는 그 레포의 gradlew 를 돌린다(이득). 번들 게이트는
# BookTimer 레이아웃(src/main/resources/static)이 없는 레포는 건너뛴다.
#
# 알려진 한계(→ 세션 cwd 폴백 또는 오판, 테스트로 잠그지 않음):
#   - 훅 대부분이 stdin 을 [Console]::In(CP949)으로 읽어 한글 경로·명령에서 판독이 빗나간다
#   - 한 명령 속 두 번째 커밋(`cd T && git commit && cd O && git commit` 은 T 만 본다)
#   - 래퍼 속 이동(`cmd /c "cd /d T && git commit"`, `bash -c '…'`), 옵션 붙은 이동(`cd -P T`), `popd`
#   - `--git-dir`/`--work-tree`, MSYS 마운트 경로(`/tmp/...`)
#   - 따옴표 문자열 속 `git commit`·`&& cd T` 를 진짜 명령으로 읽는다(`echo "git commit later" && …`)
#   - 의도된 차단: 변수로 여러 레포를 도는 루프(`for r in …; do git -C "$F/$r" commit`)

function Resolve-HookTargetCwd([string]$Command, [string]$SessionCwd, [string]$Verb, [switch]$NoToplevel) {
    try {
        # 첫 `git [전역옵션] <verb>` — 전역옵션 중 -C 값만 모은다(-c 와 대소문자 구분)
        $gitRe = '\bgit((?:\s+(?:-C\s+(?:"[^"]*"|''[^'']*''|[^\s;&|]+)|-c\s+\S+|-{1,2}[\w-]+(?:=\S+)?))*)\s+' + $Verb + '\b'
        $m = [regex]::Match($Command, $gitRe)
        $steps = @()
        if ($m.Success) {
            $prefix = $Command.Substring(0, $m.Index)
            $prefix = [regex]::Replace($prefix, '(?s)<<-?[ \t]*([''"]?)(\w+)\1([^\n]*)\n(?:.*?\n)?[ \t]*\2[ \t]*(?=\r?\n|$)', '$3')   # heredoc 본문
            $prefix = $prefix.Replace('$(git rev-parse --show-toplevel)', $SessionCwd)
            do { $prev = $prefix; $prefix = [regex]::Replace($prefix, '\([^()]*\)', ' ') } while ($prev -ne $prefix)   # 닫힌 서브셸
            # 명령 위치(맨 앞 · ; && || | ( { 줄바꿈 · then/do/else 뒤)의 이동만 — `echo cd x` 는 이동이 아니다
            $cdRe = '(?:^|[;&|({\r\n]|\b(?:then|do|else)\b)\s*(?:cd|chdir|pushd|sl|Set-Location|Push-Location)(?:\s+/d)?(?:\s+-(?:Path|LiteralPath))?\s+("[^"]*"|''[^'']*''|(?:\\ |[^\s;&|)])+)'
            foreach ($c in [regex]::Matches($prefix, $cdRe, 'IgnoreCase')) { $steps += $c.Groups[1].Value }
            foreach ($c in [regex]::Matches($m.Groups[1].Value, '-C\s+("[^"]*"|''[^'']*''|[^\s;&|]+)')) { $steps += $c.Groups[1].Value }
        }

        $dir = $SessionCwd
        foreach ($s in $steps) {
            $p = $s.Trim('"', "'") -replace '\\ ', ' '
            if ($p -match '[$%`]' -or $p -eq '-') { return $null }
            if ($p -match '^/([a-zA-Z])(/|$)') { $p = $Matches[1] + ':/' + $p.Substring([Math]::Min(3, $p.Length)) }   # Git Bash /c/...
            if ($p -match '^~(?=[\\/]|$)') { $p = $env:USERPROFILE + $p.Substring(1) }
            if ([System.IO.Path]::IsPathRooted($p)) { $dir = $p } else { $dir = Join-Path $dir $p }
        }

        if ($NoToplevel) {
            if ($steps.Count -gt 0 -and (Test-Path -LiteralPath $dir -PathType Container)) { return $dir }
            return $SessionCwd
        }

        # 성패는 종료코드가 아니라 출력으로 판정한다(`2>$null` 뒤 $LASTEXITCODE 는 믿을 수 없다, T-206)
        $prevEAP = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try { $top = [string](& git -C $dir rev-parse --show-toplevel 2>$null) } finally { $ErrorActionPreference = $prevEAP }
        if ([string]::IsNullOrWhiteSpace($top)) { return $SessionCwd }
        return ($top.Trim() -replace '/', '\')
    } catch {
        return $SessionCwd
    }
}

function Stop-UnresolvedTarget([string]$Verb) {
    # 훅 stderr 는 영문(ASCII) — 한글이 깨진다(block-main-push.ps1 참조)
    [Console]::Error.WriteLine(@"
[BLOCKED] Cannot tell which repository this git $Verb runs in (T-242).

The command changes directory through a variable or expansion (cd "`$VAR", %VAR%, cd -),
so this hook would inspect the session cwd instead of the real target and pass
silently. Rewrite it with a literal path, e.g.
  cd "C:/Users/.../BookTimer-feature" && git $Verb ...
  git -C "C:/Users/.../BookTimer-feature" $Verb ...
"@)
    exit 2
}
