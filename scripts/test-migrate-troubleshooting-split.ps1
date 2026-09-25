# test-migrate-troubleshooting-split.ps1 — 단일 파일 → 분할형 이관 스크립트 회귀 테스트 (TDD)
#
#   실행: powershell -NoProfile -ExecutionPolicy Bypass -File scripts\test-migrate-troubleshooting-split.ps1
#         실데이터 그룹(REQ-09~12)은 -Source 가 단일 파일 원본일 때만 돈다(아니면 SKIP 표시).
#         이관 뒤에는 허브가 목차뿐이라 SKIP — 원본으로 다시 재려면:
#           git show <이관 전 커밋>:claude-docs/troubleshooting.md > C:\w\tmp-x\ts.md
#           ... -Source C:\w\tmp-x\ts.md
#
# 판정: 마지막 줄 `TEST-SUMMARY: run=N pass=P fail=F`(ASCII). 실패 테스트는 `FAIL  <이름>` 줄.
# 설계: booktimer-split-design.md §3·§5(REQ-02~12)·§7(V-1~V-4 양성 대조).
# 이 파일은 UTF-8 BOM 포함으로 저장되어야 한다(PS 5.1 한글 리터럴 보존).
param(
  [string]$Source = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$migrate  = Join-Path $PSScriptRoot 'migrate-troubleshooting-split.ps1'
$checker  = Join-Path $PSScriptRoot 'rebuild-troubleshooting-index.ps1'
$enc = New-Object Text.UTF8Encoding($false)

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$cond, [string]$detail = '') {
  if ($cond) {
    $script:pass++
    Write-Host "  PASS  $name" -ForegroundColor Green
  } else {
    $script:fail++
    Write-Host "  FAIL  $name" -ForegroundColor Red
    if ($detail) { Write-Host "        $detail" -ForegroundColor DarkGray }
  }
}

# 이관 스크립트를 별도 프로세스로 — exit 코드와 stdout 마커로 판정한다.
function Invoke-Migrate([string]$src, [string]$root, [switch]$Force) {
  $argv = @('-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $migrate,
            '-Source', $src,
            '-OutDir', (Join-Path $root 'troubleshooting'),
            '-HubOut', (Join-Path $root 'troubleshooting.md'),
            '-TrackerOut', (Join-Path $root 'troubleshooting-tracker.md'),
            '-Date', '2026-09-25')
  if ($Force) { $argv += '-Force' }
  $out = (& powershell.exe @argv | Out-String)
  [pscustomobject]@{ Out = $out; Code = $LASTEXITCODE }
}

function Invoke-Check([string]$root) {
  $out = (& powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $checker `
            -HubPath (Join-Path $root 'troubleshooting.md') -Check | Out-String)
  [pscustomobject]@{ Out = $out; Code = $LASTEXITCODE }
}

function Read-Text([string]$path) { if (-not (Test-Path $path)) { return '' }; [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8).Replace("`r`n", "`n") }
function Read-Bytes([string]$path) { if (-not (Test-Path $path)) { return , [byte[]]@() }; [IO.File]::ReadAllBytes($path) }
function Has-Bom([string]$path) { $b = Read-Bytes $path; ($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF) }
function Has-Cr([string]$path) { (Read-Bytes $path) -contains 13 }

function New-Dir([string]$path) { New-Item -ItemType Directory -Path $path -Force | Out-Null; $path }

# 픽스처는 실제 체크아웃처럼 BOM + CRLF 로 쓴다(core.autocrlf=true 워킹 카피).
function Write-Fixture([string]$path, [string[]]$lines) {
  $bom = New-Object Text.UTF8Encoding($true)
  [IO.File]::WriteAllText($path, (($lines -join "`r`n") + "`r`n"), $bom)
}

# ── 픽스처: 머리글 · 트래커 2행 · 목차 · 헤딩 2섹션(T-002는 --- 없이 끝남) · 누적 갱신 표 ──
$trackerBlock = @(
  '## 🔁 재발·승격 트래커',
  '',
  '> 트래커 설명 한 줄.',
  '',
  '| 군 | 항목 | 회차 |',
  '|---|---|---|',
  '| **군A** | T-001 · T-002 | 2 |',
  '| **군B** | T-003 | 1 |'
)
$longPlain = '볼드 없는 행 a\|b 이스케이프 ' + ('가나다라마바사아자차' * 20) + ' 끝'
$fixture = @(
  '# 트러블슈팅 — 픽스처',
  '',
  '> 머리글.',
  ''
) + $trackerBlock + @(
  '',
  '## 📑 목차',
  '',
  '- [T-001. 첫째 제목](#t-001-첫째-제목)',
  '- [T-002. 둘째 · 제목](#t-002-둘째--제목)',
  '',
  '---',
  '',
  '## T-001. 첫째 제목',
  '',
  '**증상**: 증상1',
  '',
  '**원인**: 원인1',
  '',
  '---',
  '',
  '## T-002. 둘째 · 제목',
  '',
  '본문2 줄1',
  '- 목록 `a|b`',
  '',
  '## 🔄 누적 갱신',
  '',
  '| 일자 | 추가 항목 |',
  '|---|---|',
  '| 2026-05-31 | 초안 + T-001~T-002 |',
  '| 2026-06-01 | T-001 (첫째 요약 메모) |',
  '| 2026-06-02 | T-003 (**셋째 볼드 제목** / 증상: 셋 / 재발방지: 막음) |',
  "| 2026-06-03 | T-004 ($longPlain) |",
  '| 2026-06-04 | T-005 (**닫는 괄호 없음** / 증상: 괄호 안 닫힘 |',
  '| 2026-06-04 | T-002 (둘째 첫 누적 메모) |',
  '| 2026-06-05 | T-002 보강 (보강 하나) |',
  '| 2026-06-06 | T-002 보강 (보강 둘) |',
  '| 2026-06-07 | T-003 확장 (확장 한 줄) |',
  '| 2026-06-08 | T-001 **2회차** (명시 회차) |',
  '| 2026-06-09 | T-006 (**끊긴 행** / 첫 줄',
  ']둘째 줄 이어짐) |',
  '',
  '| 2026-06-10 | T-002 (둘째 누적 갱신 메모) |'
)

$tmp = Join-Path ([IO.Path]::GetTempPath()) ('mts-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Dir $tmp | Out-Null

try {
  # ════════════════════════ 픽스처 그룹 (REQ-02~08) ════════════════════════
  $fx = Join-Path $tmp 'fixture.md'
  Write-Fixture $fx $fixture
  $root = New-Dir (Join-Path $tmp 'f')
  $run = Invoke-Migrate $fx $root
  $tsDir = Join-Path $root 'troubleshooting'
  function Fx([string]$id) { $p = Join-Path $tsDir "$id.md"; if (Test-Path $p) { Read-Text $p } else { '' } }

  Write-Host "`n[REQ-02] 헤딩 섹션이 본문 원문 그대로 파일이 된다"
  Assert-That 'REQ-02 · 스크립트 exit 0 + 마커 MIGRATE: OK files=6 tails=7 residual=1' `
    ($run.Code -eq 0 -and $run.Out.Contains('MIGRATE: OK files=6 tails=7 residual=1')) "실제($($run.Code)): $($run.Out.Trim())"
  $exp001 = @(
    '---', 'summary: 첫째 제목', 'date: 2026-06-01',
    'legacy: 2026-09-25 단일 파일 이관 · 헤딩형(4필드 이전 형식)', '---', '',
    '# T-001 · 첫째 제목', '',
    '**증상**: 증상1', '', '**원인**: 원인1', '',
    '- **누적 갱신** (2026-06-01): 첫째 요약 메모',
    '- **2회차** (2026-06-08): 명시 회차', '') -join "`n"
  Assert-That 'REQ-02 · T-001(--- 로 끝난 섹션) 전문 일치' ((Fx 'T-001') -ceq $exp001) "실제:`n$(Fx 'T-001')"
  $exp002 = @(
    '---', 'summary: 둘째 · 제목', 'date: 2026-06-04',
    'legacy: 2026-09-25 단일 파일 이관 · 헤딩형(4필드 이전 형식)', '---', '',
    '# T-002 · 둘째 · 제목', '',
    '본문2 줄1', '- 목록 `a|b`', '',
    '- **누적 갱신** (2026-06-04): 둘째 첫 누적 메모',
    '- **2회차** (2026-06-05): 보강 하나',
    '- **3회차** (2026-06-06): 보강 둘',
    '- **누적 갱신** (2026-06-10): 둘째 누적 갱신 메모', '') -join "`n"

  Write-Host "`n[REQ-03] 표에만 있는 번호는 표 행형 파일이 된다"
  $exp003 = @(
    '---', 'summary: 셋째 볼드 제목', 'date: 2026-06-02',
    'legacy: 2026-09-25 단일 파일 이관 · 표 행형', '---', '',
    '# T-003 · 셋째 볼드 제목', '',
    '증상: 셋 / 재발방지: 막음', '',
    '- **확장** (2026-06-07): 확장 한 줄', '') -join "`n"
  Assert-That 'REQ-03 · T-003(볼드 제목) 전문 일치 — summary=볼드, 본문=제목 뗀 나머지' ((Fx 'T-003') -ceq $exp003) "실제:`n$(Fx 'T-003')"
  $t4 = Fx 'T-004'
  $plain = $longPlain.Replace('\|', '|')
  Assert-That 'REQ-03 · T-004(볼드 없음) summary = 앞 160자 + …' ($t4.Contains("`nsummary: $($plain.Substring(0,160))…`n")) "실제:`n$t4"
  Assert-That 'REQ-03 · T-004 본문 = 원문 한 줄, \| → | 복원' ($t4.Contains("`n`n$plain`n") -and -not $t4.Contains('\|')) "실제:`n$t4"
  $t5 = Fx 'T-005'
  Assert-That 'REQ-03 · T-005(닫는 괄호 없음) 본문 끝이 원문 그대로' ($t5.Contains("`n`n증상: 괄호 안 닫힘`n")) "실제:`n$t5"

  Write-Host "`n[REQ-04] 보강·확장·회차 행은 그 번호 파일 끝의 줄이 된다"
  Assert-That 'REQ-04 · T-002(신규 2 + 보강 2) 전문 일치 — 누적 갱신 2 + 2회차·3회차, 원문 순서' ((Fx 'T-002') -ceq $exp002) "실제:`n$(Fx 'T-002')"

  Write-Host "`n[REQ-05] 행 안 개행은 직전 행에 붙어 본문 2줄이 된다"
  $t6 = Fx 'T-006'
  Assert-That 'REQ-05 · T-006 본문 = 2줄(첫 줄 + ] 로 시작하는 연속 줄)' ($t6.Contains("`n`n첫 줄`n]둘째 줄 이어짐`n")) "실제:`n$t6"

  Write-Host "`n[REQ-06] 초안 행은 잔여로 남고 모르는 태그는 크래시한다"
  $trk = Read-Text (Join-Path $root 'troubleshooting-tracker.md')
  Assert-That 'REQ-06 · 초안 행이 트래커 끝 「누적 갱신 잔여」 절에 원문 그대로' `
    ($trk.TrimEnd().EndsWith("## 누적 갱신 잔여`n`n| 2026-05-31 | 초안 + T-001~T-002 |")) "실제 끝:`n$($trk.Substring([Math]::Max(0, $trk.Length - 200)))"
  $fxBad = Join-Path $tmp 'bad-tag.md'
  Write-Fixture $fxBad ($fixture + @('| 2026-06-11 | T-999 메모 (모르는 태그) |'))
  $rootBad = New-Dir (Join-Path $tmp 'b')
  $r = Invoke-Migrate $fxBad $rootBad
  Assert-That 'REQ-06 · 모르는 태그 → exit ≠ 0, 마커 MIGRATE: FAIL, T 파일 0개' `
    ($r.Code -ne 0 -and $r.Out.Contains('MIGRATE: FAIL') -and $r.Out.Contains('T-999') -and
     @(Get-ChildItem (Join-Path $rootBad 'troubleshooting') -Filter 'T-*.md' -ErrorAction SilentlyContinue).Count -eq 0) "실제($($r.Code)): $($r.Out.Trim())"

  Write-Host "`n[REQ-07] 트래커는 그대로 옮기고 목차는 버린다"
  Assert-That 'REQ-07 · 트래커 파일에 원본 트래커 구간이 바이트 그대로' ($trk.Contains("`n" + ($trackerBlock -join "`n") + "`n")) "실제:`n$trk"
  Assert-That 'REQ-07 · 트래커 파일 H1 = 동결 표시' ($trk.StartsWith("# 재발·승격 트래커 (동결 — 2026-09-25 분할 이관)`n")) "실제 첫 줄: $(($trk -split "`n")[0])"
  $hub = Read-Text (Join-Path $root 'troubleshooting.md')
  Assert-That 'REQ-07 · 옛 목차(📑·앵커 링크)가 허브에 없다' ($hub.Length -gt 0 -and -not $hub.Contains('📑') -and -not $hub.Contains('](#t-')) "허브:`n$hub"
  $fxToc = Join-Path $tmp 'bad-toc.md'
  $badToc = [Collections.ArrayList]@($fixture)
  $badToc.Insert($badToc.IndexOf('- [T-002. 둘째 · 제목](#t-002-둘째--제목)') + 1, '목차 사이에 낀 일반 문장.')
  Write-Fixture $fxToc ([string[]]$badToc)
  $rootToc = New-Dir (Join-Path $tmp 't')
  $r = Invoke-Migrate $fxToc $rootToc
  Assert-That 'REQ-07 · 목차에 일반 문장이 섞이면 크래시(exit ≠ 0, MIGRATE: FAIL)' ($r.Code -ne 0 -and $r.Out.Contains('MIGRATE: FAIL')) "실제($($r.Code)): $($r.Out.Trim())"

  Write-Host "`n[REQ-08] 허브·목차·인코딩·Force"
  Assert-That 'REQ-08 · 허브 H1 · 이관 이력 절 · 트래커 링크' `
    ($hub.StartsWith("# 트러블슈팅 — 작업 중 만난 함정과 해결법`n") -and $hub.Contains("`n## 이관 이력`n") -and
     $hub.Contains('[troubleshooting-tracker.md](troubleshooting-tracker.md)')) "허브:`n$hub"
  Assert-That 'REQ-08 · 목차가 검사기로 채워졌다(T-006 줄, 최신이 위)' `
    ($hub.Contains("- [T-006](troubleshooting/T-006.md) · 끊긴 행`n- [T-005](troubleshooting/T-005.md) · 닫는 괄호 없음`n")) "허브:`n$hub"
  $c = Invoke-Check $root
  Assert-That 'REQ-08 · 검사기 -Check = OK (6 entries)' ($c.Code -eq 0 -and $c.Out.Contains('INDEX-CHECK: OK (6 entries)')) "실제($($c.Code)): $($c.Out.Trim())"
  $outs = @(Get-ChildItem $tsDir -Filter '*.md' -File -ErrorAction SilentlyContinue | ForEach-Object FullName) +
          @((Join-Path $root 'troubleshooting.md'), (Join-Path $root 'troubleshooting-tracker.md'))
  $bad = @($outs | Where-Object { (Has-Bom $_) -or (Has-Cr $_) })
  Assert-That "REQ-08 · 산출물 $($outs.Count)개 전부 BOM 없음·CR 0" ($outs.Count -eq 8 -and $bad.Count -eq 0) "위반: $($bad -join ', ')"
  function Hash-All { ($outs | ForEach-Object { if (Test-Path $_) { (Get-FileHash $_ -Algorithm SHA256).Hash } else { 'missing' } }) -join ',' }
  $hash1 = Hash-All
  $r = Invoke-Migrate $fx $root
  Assert-That 'REQ-08 · 비어 있지 않은 OutDir 는 -Force 없이 거부(exit ≠ 0)' ($r.Code -ne 0 -and $r.Out.Contains('MIGRATE: FAIL')) "실제($($r.Code)): $($r.Out.Trim())"
  $r = Invoke-Migrate $fx $root -Force
  $hash2 = Hash-All
  Assert-That 'REQ-08 · -Force 재실행 = exit 0, 산출물 바이트 동일(멱등)' ($r.Code -eq 0 -and $hash1 -eq $hash2) "실제($($r.Code)): $($r.Out.Trim())"

  # ════════════════════════ 실데이터 그룹 (REQ-09~12) ════════════════════════
  if (-not $Source) { $Source = Join-Path $repoRoot 'claude-docs\troubleshooting.md' }
  $srcText = if (Test-Path $Source) { (Read-Text $Source).TrimStart([char]0xFEFF) } else { '' }
  if (-not $srcText.Contains("`n## 🔄 누적 갱신")) {
    Write-Host "`n[REQ-09~12] SKIP — -Source 가 단일 파일 원본이 아니다: $Source" -ForegroundColor Yellow
  } else {
    $real = New-Dir (Join-Path $tmp 'r')
    $realDir = Join-Path $real 'troubleshooting'
    $run = Invoke-Migrate $Source $real

    # ── 스크립트와 독립인 참조 파서(설계 §3-A 문법을 테스트 쪽에서 한 번 더) ──
    $srcLines = $srcText.Split("`n")
    $iTrk = [Array]::FindIndex($srcLines, [Predicate[string]]{ param($l) $l.StartsWith('## 🔁') })
    $iToc = [Array]::FindIndex($srcLines, [Predicate[string]]{ param($l) $l.StartsWith('## 📑') })
    $iLog = [Array]::FindIndex($srcLines, [Predicate[string]]{ param($l) $l.StartsWith('## 🔄 누적 갱신') })
    $trkRef = ($srcLines[$iTrk..($iToc - 1)] -join "`n").TrimEnd()

    $sections = @{}
    $secRx = [regex]'(?ms)^## T-(\d{3})\. (.+?)\n(.*?)(?=^## )'
    foreach ($m in $secRx.Matches($srcText)) {
      $sections[$m.Groups[1].Value] = [pscustomobject]@{
        Title   = $m.Groups[2].Value.Trim()
        Content = @($m.Groups[3].Value.Split("`n") | Where-Object { $_.Trim() -ne '' -and $_.Trim() -ne '---' })
      }
    }
    $rows = [Collections.ArrayList]@()
    foreach ($l in $srcLines[($iLog + 1)..($srcLines.Count - 1)]) {
      if ($l.StartsWith('|')) { [void]$rows.Add($l) }
      elseif ($l.Trim() -ne '' -and $rows.Count -gt 0) { $rows[$rows.Count - 1] += "`n" + $l }
    }
    $rowRx = [regex]::new('^\|\s*(\d{4}-\d{2}-\d{2})\s*\|\s*T-(\d{3})\s*(보강|확장|\*\*(\d+)회차\*\*)?\s*\((.*)$', 'Singleline')
    $parsed = @()
    foreach ($row in $rows) {
      $m = $rowRx.Match($row)
      if (-not $m.Success) { continue }
      $t = [regex]::Replace($m.Groups[5].Value, '\s*\|\s*$', '')
      if ($t.EndsWith(')')) { $t = $t.Substring(0, $t.Length - 1) }
      $parsed += [pscustomobject]@{ Date = $m.Groups[1].Value; Id = $m.Groups[2].Value; Tag = $m.Groups[3].Value; Text = $t.Replace('\|', '|') }
    }

    # 왕복 검사기: 문제 목록을 돌려준다(0건 = 손실 없음). 양성 대조에도 같은 함수를 쓴다.
    function Test-RoundTrip([string]$dir, [string]$trackerPath) {
      $probs = @()
      $files = @{}
      foreach ($f in Get-ChildItem $dir -Filter 'T-*.md' -File -ErrorAction SilentlyContinue) { $files[$f.BaseName.Substring(2)] = Read-Text $f.FullName }
      foreach ($id in $sections.Keys) {
        if (-not $files.ContainsKey($id)) { $probs += "T-$id 헤딩 섹션의 파일 없음"; continue }
        $lines = $files[$id].Split("`n")
        $fmEnd = [Array]::IndexOf($lines, '---', 1)
        if (-not $files[$id].Contains("`nsummary: $($sections[$id].Title)`n")) { $probs += "T-$id summary ≠ 헤딩 제목" }
        $content = @($lines[($fmEnd + 1)..($lines.Count - 1)] | Where-Object {
          $_.Trim() -ne '' -and $_.Trim() -ne '---' -and $_ -notmatch '^# T-\d{3} · ' -and
          $_ -notmatch '^- \*\*(누적 갱신|\d+회차|확장)\*\* \(\d{4}-\d{2}-\d{2}\): ' })
        if (($content -join "`n") -cne ($sections[$id].Content -join "`n")) { $probs += "T-$id 본문 내용 줄 불일치" }
      }
      $seen = @{}
      foreach ($p in $parsed) {
        if (-not $files.ContainsKey($p.Id)) { $probs += "T-$($p.Id) ($($p.Date)) 행의 파일 없음"; continue }
        $ft = $files[$p.Id]
        $isItem = (-not $p.Tag) -and (-not $sections.ContainsKey($p.Id)) -and (-not $seen.ContainsKey($p.Id))
        if (-not $p.Tag) { $seen[$p.Id] = $true }
        if ($isItem) {
          $bm = [regex]::Match($p.Text, '^\*\*(.+?)\*\*\s*/\s*')
          $body = if ($bm.Success) { $p.Text.Substring($bm.Length) } else { $p.Text }
          $ok = $ft.Contains("`ndate: $($p.Date)`n") -and ([regex]::Matches($ft, [regex]::Escape("`n$body`n")).Count -eq 1)
          if ($bm.Success) { $ok = $ok -and $ft.Contains("`nsummary: $($bm.Groups[1].Value)`n") }
          if (-not $ok) { $probs += "T-$($p.Id) ($($p.Date)) 표 행형 본문 불일치" }
        } else {
          $label = if (-not $p.Tag) { [regex]::Escape('누적 갱신') } elseif ($p.Tag -eq '확장') { '확장' } else { '\d+회차' }
          $rx = "\n- \*\*$label\*\* \($($p.Date)\): " + [regex]::Escape($p.Text) + '\n'
          if ([regex]::Matches($ft, $rx).Count -ne 1) { $probs += "T-$($p.Id) ($($p.Date)) 꼬리 줄 대응 실패" }
        }
      }
      $trk = if (Test-Path $trackerPath) { Read-Text $trackerPath } else { '' }
      if (-not $trk.Contains("`n$trkRef`n")) { $probs += '트래커 구간 불일치' }
      , $probs
    }

    Write-Host "`n[REQ-09] 실데이터 왕복 — 내용 줄 다중집합 일치·표 행 전부 대응"
    Assert-That 'REQ-09 · 실데이터 이관 exit 0' ($run.Code -eq 0) "실제($($run.Code)): $($run.Out.Trim())"
    $probs = Test-RoundTrip $realDir (Join-Path $real 'troubleshooting-tracker.md')
    Assert-That "REQ-09 · 왕복 문제 0건 (헤딩 $($sections.Count) · 표 행 $($parsed.Count))" ($probs.Count -eq 0) (($probs | Select-Object -First 10) -join ' / ')
    # 양성 대조(V-2): 글자 1개 변조 · 파일 1개 삭제를 같은 검사기가 잡는가.
    # (산출물이 없으면 — 돌연변이가 스크립트를 죽였을 때 — 조작만 건너뛰고 단언은 그대로 세어 실행 건수를 지킨다.)
    $victim = Join-Path $realDir 'T-150.md'; $exists = Test-Path $victim; $orig = Read-Bytes $victim
    $vt = Read-Text $victim; $vi = $vt.LastIndexOf('다')
    if ($exists -and $vi -ge 0) { [IO.File]::WriteAllText($victim, $vt.Substring(0, $vi) + '라' + $vt.Substring($vi + 1), $enc) }
    $pc = Test-RoundTrip $realDir (Join-Path $real 'troubleshooting-tracker.md')
    Assert-That 'REQ-09 · 대조군: T-150 글자 1개 변조 → 문제 정확히 1건' ($exists -and $pc.Count -eq 1) "실제 $($pc.Count)건: $(($pc | Select-Object -First 3) -join ' / ')"
    if ($exists) { Remove-Item $victim }
    $pc = Test-RoundTrip $realDir (Join-Path $real 'troubleshooting-tracker.md')
    Assert-That 'REQ-09 · 대조군: T-150 파일 삭제 → 대응 실패 보고' ($exists -and ($pc -join ' ').Contains('T-150')) "실제 $($pc.Count)건"
    if ($exists) { [IO.File]::WriteAllBytes($victim, $orig) }

    Write-Host "`n[REQ-10] 실데이터 건수"
    Assert-That 'REQ-10 · stdout 마커 = MIGRATE: OK files=253 tails=131 residual=1' ($run.Out.Contains('MIGRATE: OK files=253 tails=131 residual=1')) "실제: $($run.Out.Trim())"
    $all = @(Get-ChildItem $realDir -Filter 'T-*.md' -File -ErrorAction SilentlyContinue)
    $texts = @{}; foreach ($f in $all) { $texts[$f.BaseName.Substring(2)] = Read-Text $f.FullName }
    $nHead = @($texts.Values | Where-Object { $_ -match '\nlegacy: .* · 헤딩형' }).Count
    $nTab  = @($texts.Values | Where-Object { $_ -match '\nlegacy: .* · 표 행형\n' }).Count
    Assert-That "REQ-10 · 파일 253 = 헤딩형 126 + 표 행형 127 (실제 $($all.Count) = $nHead + $nTab)" ($all.Count -eq 253 -and $nHead -eq 126 -and $nTab -eq 127)
    $max = ($texts.Keys | ForEach-Object { [int]$_ } | Measure-Object -Maximum).Maximum
    $gaps = @(1..$max | ForEach-Object { '{0:D3}' -f $_ } | Where-Object { -not $texts.ContainsKey($_) })
    Assert-That "REQ-10 · 결번 = 024·025·026 (실제 $($gaps -join '·'))" (($gaps -join ',') -eq '024,025,026')
    $allLines = @($texts.Values | ForEach-Object { $_.Split("`n") })
    $nAcc = @($allLines -match '^- \*\*누적 갱신\*\* \(').Count
    $nRnd = @($allLines -match '^- \*\*\d+회차\*\* \(').Count
    $nExt = @($allLines -match '^- \*\*확장\*\* \(').Count
    Assert-That "REQ-10 · 꼬리 줄 131 = 누적 갱신 122 + 회차 8 + 확장 1 (실제 $nAcc + $nRnd + $nExt)" ($nAcc -eq 122 -and $nRnd -eq 8 -and $nExt -eq 1)
    $rndIds = @($texts.Keys | Where-Object { $texts[$_] -match '\n- \*\*\d+회차\*\* \(' } | Sort-Object)
    Assert-That "REQ-10 · 회차 줄 번호 = 006·027·033·049·085·107·167 (실제 $($rndIds -join '·'))" (($rndIds -join ',') -eq '006,027,033,049,085,107,167')
    Assert-That 'REQ-10 · T-033 은 3회차 줄까지' ($texts['033'] -match '\n- \*\*3회차\*\* \(')
    $trkReal = Read-Text (Join-Path $real 'troubleshooting-tracker.md')
    Assert-That 'REQ-10 · 잔여 1행(초안) = 트래커 끝 절' ($trkReal.TrimEnd().EndsWith("## 누적 갱신 잔여`n`n| 2026-05-31 | 초안 + T-001~T-004 |"))
    # 양성 대조(V-3): T-107 의 **2회차** 태그를 모르는 태그로 바꾸면 조용히 신규가 되지 않고 크래시.
    $mutSrc = Join-Path $tmp 'mut-tag.md'
    [IO.File]::WriteAllText($mutSrc, $srcText.Replace('| T-107 **2회차** (', '| T-107 메모 ('), $enc)
    Assert-That 'REQ-10 · 대조군 준비: 원본에 T-107 **2회차** 행이 있다' ($srcText.Contains('| T-107 **2회차** ('))
    $r = Invoke-Migrate $mutSrc (New-Dir (Join-Path $tmp 'm'))
    Assert-That 'REQ-10 · 대조군: T-107 태그를 「메모」로 → 크래시' ($r.Code -ne 0 -and $r.Out.Contains('MIGRATE: FAIL')) "실제($($r.Code)): $($r.Out.Trim())"

    Write-Host "`n[REQ-11] 이관 직후 검사기 OK 253"
    $c = Invoke-Check $real
    Assert-That 'REQ-11 · -Check = INDEX-CHECK: OK (253 entries)' ($c.Code -eq 0 -and $c.Out.Contains('INDEX-CHECK: OK (253 entries)')) "실제($($c.Code)): $($c.Out.Trim())"
    $v = Join-Path $realDir 'T-200.md'; $vExists = Test-Path $v; $vb = Read-Bytes $v
    if ($vExists) { [IO.File]::WriteAllText($v, ((Read-Text $v).Split("`n") | Where-Object { $_ -notlike 'legacy:*' }) -join "`n", $enc) }
    $c = Invoke-Check $real
    Assert-That 'REQ-11 · 대조군: T-200 의 legacy: 줄 삭제 → INVALID (1 problem(s))' ($vExists -and $c.Code -eq 1 -and $c.Out.Contains('INDEX-CHECK: INVALID (1 problem(s))')) "실제($($c.Code)): $($c.Out.Trim())"
    if ($vExists) { [IO.File]::WriteAllBytes($v, $vb) }

    Write-Host "`n[REQ-12] 없는 번호 참조 집합이 이관 전후 같다"
    function Get-CitedIds([string]$repo) {
      $ErrorActionPreference = 'Continue'   # PS 5.1: native stderr + Stop = 예외
      $o = & git -C $repo grep -h -o -E 'T-[0-9]{3}' -- . ':(exclude)claude-docs' ':(exclude)*.md' `
             ':(exclude)scripts/migrate-troubleshooting-split.ps1' ':(exclude)scripts/test-migrate-troubleshooting-split.ps1' 2>$null
      @($o | Sort-Object -Unique)
    }
    $cited  = Get-CitedIds $repoRoot
    $before = @($sections.Keys) + @($parsed | ForEach-Object Id) | ForEach-Object { "T-$_" } | Sort-Object -Unique
    $after  = @($texts.Keys | ForEach-Object { "T-$_" } | Sort-Object -Unique)
    $missB  = @($cited | Where-Object { $before -notcontains $_ })
    $missA  = @($cited | Where-Object { $after -notcontains $_ })
    Assert-That "REQ-12 · 없는 번호 참조: 전 {$($missB -join ',')} = 후 {$($missA -join ',')} = {T-026} (인용 $($cited.Count)종)" `
      (($missB -join ',') -eq ($missA -join ',') -and ($missA -join ',') -eq 'T-026' -and $cited.Count -gt 50)   # 50: git grep 이 빈손이면 집합 비교가 공허하다(실측 80종, md 제외)
    # 계측기 판별력: T-999 를 인용한 파일이 있는 레포에선 집합에 T-999 가 뜬다.
    $g = New-Dir (Join-Path $tmp 'g')
    $ErrorActionPreference = 'Continue'
    & git -C $g init -q 2>&1 | Out-Null; [IO.File]::WriteAllText((Join-Path $g 'a.java'), "// T-999 참조`n", $enc); & git -C $g add a.java 2>&1 | Out-Null
    $ErrorActionPreference = 'Stop'
    Assert-That 'REQ-12 · 대조군: T-999 인용을 심으면 인용 집합에 뜬다' ((Get-CitedIds $g) -contains 'T-999')
  }
} finally {
  if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue }
}

$run = $script:pass + $script:fail
Write-Host "`n=== 통과 $script:pass / 실패 $script:fail ===" -ForegroundColor $(if ($script:fail -gt 0) { 'Red' } else { 'Green' })
Write-Host "TEST-SUMMARY: run=$run pass=$($script:pass) fail=$($script:fail)"
if ($script:fail -gt 0) { exit 1 }
exit 0
