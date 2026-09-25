# migrate-troubleshooting-split.ps1 — 단일 파일 troubleshooting.md → troubleshooting/T-###.md 분할 이관 (1회성)
#
# ⚠️ UTF-8 **BOM 포함**으로 저장(PS 5.1이 BOM 없는 UTF-8을 CP949로 읽어 한글 정규식이 깨진다).
#
# 설계: booktimer-split-design.md §3 (2026-09-25 승인). 테스트: scripts/test-migrate-troubleshooting-split.ps1
#
# 원본을 네 구간으로 자른다 — 머리글(버림: 새 허브 머리글로 대체) · 🔁 트래커(원문 그대로 트래커 파일로) ·
# 📑 목차(헤딩의 파생물이라 버림 — 목차 줄 말고 다른 게 섞여 있으면 크래시) · 헤딩 섹션 · 🔄 누적 갱신 표.
# 번호 1개 = 파일 1개. 원문은 한 글자도 다시 쓰지 않는다(legacy: 로 4필드 검사 면제).
#   헤딩형: 섹션 본문 그대로. 표 행형: 표에만 있는 번호 — 첫 신규 행이 항목(볼드 제목 = summary).
#   같은 번호의 뒤 행은 파일 끝 꼬리 줄: 태그 없음 → 누적 갱신 · 보강/**N회차** → N회차 · 확장 → 확장.
# 문법 밖은 조용히 넘기지 않는다 — 날짜 행인데 T-### 로 시작하지 않으면 잔여(트래커 끝에 원문), 모르는 태그는 크래시.
#
# 사용(레포 루트에서):
#   powershell -ExecutionPolicy Bypass -File scripts/migrate-troubleshooting-split.ps1 -Date 2026-09-25 [-Force]
# 리베이스 = git checkout origin/main -- claude-docs/troubleshooting.md 후 -Force 재실행(멱등).
# 마지막 줄 ASCII 마커: `MIGRATE: OK files=N tails=N residual=N` / 실패는 `MIGRATE: FAIL <이유>` + exit 1.
# 산출물은 전부 UTF-8 BOM 없음·LF(검사기 쓰기 규약과 같다). 목차는 검사기(rebuild-troubleshooting-index.ps1)가 채운다.
[CmdletBinding()]
param(
    [string]$Source     = 'claude-docs/troubleshooting.md',
    [string]$OutDir     = 'claude-docs/troubleshooting',
    [string]$HubOut     = 'claude-docs/troubleshooting.md',
    [string]$TrackerOut = 'claude-docs/troubleshooting-tracker.md',
    [Parameter(Mandatory = $true)][string]$Date,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Fail([string]$msg) {
    Write-Host "MIGRATE: FAIL $msg"
    exit 1
}

function Full([string]$p) { [System.IO.Path]::GetFullPath($(if ([System.IO.Path]::IsPathRooted($p)) { $p } else { Join-Path (Get-Location).Path $p })) }

if ($Date -notmatch '^\d{4}-\d{2}-\d{2}$') { Fail "-Date 형식(yyyy-MM-dd): $Date" }
$Source = Full $Source; $OutDir = Full $OutDir; $HubOut = Full $HubOut; $TrackerOut = Full $TrackerOut
if (-not (Test-Path $Source)) { Fail "source not found: $Source" }
# 검사기는 허브 이름에서 항목 폴더를 정한다(troubleshooting.md → troubleshooting/) — 어긋나면 엉뚱한 폴더를 검사한다.
$derived = Join-Path (Split-Path $HubOut -Parent) ([System.IO.Path]::GetFileNameWithoutExtension($HubOut))
if ($derived.TrimEnd('\') -ne $OutDir.TrimEnd('\')) { Fail "OutDir 는 허브 옆 같은 이름 폴더여야 한다: $derived" }
$checker = Join-Path $PSScriptRoot 'rebuild-troubleshooting-index.ps1'
if (-not (Test-Path $checker)) { Fail "checker not found: $checker" }
if (-not $Force -and @(Get-ChildItem $OutDir -Filter 'T-*.md' -File -ErrorAction SilentlyContinue).Count -gt 0) {
    Fail "OutDir not empty (use -Force): $OutDir"
}

# ── 1. 읽기 (전부 메모리에 — HubOut 이 Source 와 같아도 된다) ─────────────────
$raw = [System.IO.File]::ReadAllText($Source, [System.Text.Encoding]::UTF8).TrimStart([char]0xFEFF).Replace("`r`n", "`n")
$lines = $raw.Split("`n")

function Find-Line([string]$prefix) {
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i].StartsWith($prefix)) { return $i } }
    return -1
}
$iTrk = Find-Line '## 🔁'
$iToc = Find-Line '## 📑'
$iLog = Find-Line '## 🔄 누적 갱신'
$iBody = -1
for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -match '^## T-\d{3}\.') { $iBody = $i; break } }
if (-not (0 -le $iTrk -and $iTrk -lt $iToc -and $iToc -lt $iBody -and $iBody -lt $iLog)) {
    Fail "구간 순서가 다르다(🔁=$iTrk 📑=$iToc T=$iBody 🔄=$iLog)"
}

# ── 2. 트래커 구간 — 원문 그대로 ─────────────────────────────────────────────
$trackerBlock = ($lines[$iTrk..($iToc - 1)] -join "`n").TrimEnd()

# ── 3. 목차 — 파생물이라 버린다. 목차 줄 말고 다른 게 섞였으면 크래시 ──────────
for ($i = $iToc + 1; $i -lt $iBody; $i++) {
    $l = $lines[$i]
    if ($l.Trim() -eq '' -or $l.Trim() -eq '---') { continue }
    if ($l -notmatch '^- \[T-\d{3}\. .+\]\(#.+\)$') { Fail "목차 구간에 목차 줄이 아닌 것(line $($i + 1)): $l" }
}

# ── 4. 헤딩 섹션 ─────────────────────────────────────────────────────────────
$items = @{}
function New-Item-Obj($id, $kind, $summary, $date, [string[]]$body) {
    [pscustomobject]@{ Id = $id; Kind = $kind; Summary = $summary; Date = $date; Body = $body
                       Round = 1; Tails = (New-Object System.Collections.ArrayList) }
}
$cur = $null; $buf = $null
function Close-Section {
    if (-not $script:cur) { return }
    $b = [System.Collections.ArrayList]@($script:buf)
    while ($b.Count -gt 0 -and $b[0].Trim() -eq '') { $b.RemoveAt(0) }
    while ($b.Count -gt 0 -and $b[$b.Count - 1].Trim() -eq '') { $b.RemoveAt($b.Count - 1) }
    if ($b.Count -gt 0 -and $b[$b.Count - 1].Trim() -eq '---') { $b.RemoveAt($b.Count - 1) }
    while ($b.Count -gt 0 -and $b[$b.Count - 1].Trim() -eq '') { $b.RemoveAt($b.Count - 1) }
    $script:cur.Body = [string[]]$b
}
for ($i = $iBody; $i -lt $iLog; $i++) {
    $l = $lines[$i]
    if ($l.StartsWith('## ')) {
        if ($l -notmatch '^## T-(\d{3})\. (.+)$') { Fail "본문 구간에 T 헤딩이 아닌 ## (line $($i + 1)): $l" }
        Close-Section
        $id = $matches[1]; $title = $matches[2].Trim()
        if ($items.ContainsKey($id)) { Fail "헤딩 번호 중복: T-$id" }
        $script:cur = New-Item-Obj $id 'heading' $title $null @()
        $items[$id] = $script:cur
        $script:buf = New-Object System.Collections.ArrayList
        continue
    }
    [void]$script:buf.Add($l)
}
Close-Section
$nHeading = $items.Count

# ── 5. 누적 갱신 표 — 행 모으기(연속 줄은 직전 행에 이어 붙인다) ─────────────
$rows = New-Object System.Collections.ArrayList
for ($i = $iLog + 1; $i -lt $lines.Count; $i++) {
    $l = $lines[$i]
    if ($l.Trim() -eq '') { continue }
    if ($l -match '^\|\s*일자\s*\|' -or $l -match '^\|[\s\-|:]+$') { continue }   # 표 머리 2줄
    if ($l.StartsWith('|')) { [void]$rows.Add($l); continue }
    if ($rows.Count -eq 0) { Fail "표 앞에 표가 아닌 줄(line $($i + 1)): $l" }
    $rows[$rows.Count - 1] = $rows[$rows.Count - 1] + "`n" + $l
}

$rowRx = New-Object System.Text.RegularExpressions.Regex(
    '^\|\s*(?<date>\d{4}-\d{2}-\d{2})\s*\|\s*T-(?<id>\d{3})\s*(?<tag>보강|확장|\*\*(?<n>\d+)회차\*\*)?\s*\((?<text>.*)$',
    [System.Text.RegularExpressions.RegexOptions]::Singleline)
$residual = New-Object System.Collections.ArrayList
$nTails = 0
foreach ($row in $rows) {
    $m = $rowRx.Match($row)
    if (-not $m.Success) {
        if ($row -match '^\|\s*\d{4}-\d{2}-\d{2}\s*\|\s*T-\d{3}') { $h = ($row -split "`n")[0]; Fail "모르는 태그: $($h.Substring(0, [Math]::Min(80, $h.Length)))" }
        if ($row -match '^\|\s*\d{4}-\d{2}-\d{2}\s*\|') { [void]$residual.Add($row); continue }
        Fail "날짜로 시작하지 않는 표 행: $row"
    }
    $rowDate = $m.Groups['date'].Value; $id = $m.Groups['id'].Value; $tag = $m.Groups['tag'].Value
    $text = [regex]::Replace($m.Groups['text'].Value, '\s*\|\s*$', '')
    # 끝 `)` 는 바깥 `(`(정규식이 먹은 것)의 짝일 때만 뗀다 — 깊이 1에서 훑어 처음 0이 되는 곳이 마지막 글자일 때.
    # 바깥 괄호가 먼저 닫혔거나(`(**제목**) / … T-093(설명)`) 안 닫힌 행(T-176·T-250)의 끝 `)` 는 본문 괄호다.
    $depth = 1; $close = -1
    for ($k = 0; $k -lt $text.Length -and $close -lt 0; $k++) {
        if ($text[$k] -eq '(') { $depth++ } elseif ($text[$k] -eq ')') { $depth--; if ($depth -eq 0) { $close = $k } }
    }
    if ($close -ge 0 -and $close -eq $text.Length - 1) { $text = $text.Substring(0, $close) }
    $text = $text.Replace('\|', '|')

    $it = $items[$id]
    if (-not $tag) {
        if (-not $it) {
            # `\)?`: 14행(T-221·223~235)은 괄호가 제목 바로 뒤에서 닫힌다 — `(**제목**) / **1회차** / …`.
            $bm = [regex]::Match($text, '^\*\*(.+?)\*\*\)?\s*/\s*')
            if ($bm.Success) {
                $summary = $bm.Groups[1].Value.Trim(); $body = $text.Substring($bm.Length)
            } else {
                $first = ($text -split "`n")[0].Trim()
                $cut = 160; if ($first.Length -gt $cut -and [char]::IsHighSurrogate($first[$cut - 1])) { $cut-- }
                $summary = if ($first.Length -gt 160) { $first.Substring(0, $cut) + '…' } else { $first }
                $body = $text
            }
            if (-not $summary) { Fail "summary 가 빈 행: T-$id ($rowDate)" }
            $items[$id] = New-Item-Obj $id 'table' $summary $rowDate ([string[]]($body -split "`n"))
            continue
        }
        if (-not $it.Date) { $it.Date = $rowDate }
        [void]$it.Tails.Add("- **누적 갱신** ($rowDate): $text"); $nTails++
        continue
    }
    if (-not $it) { Fail "기준 항목 없는 $tag 행: T-$id ($rowDate)" }
    if ($tag -eq '확장') {
        [void]$it.Tails.Add("- **확장** ($rowDate): $text"); $nTails++
        continue
    }
    $it.Round++
    if ($m.Groups['n'].Success -and [int]$m.Groups['n'].Value -ne $it.Round) {
        Fail "회차 불일치: T-$id 행은 $($m.Groups['n'].Value)회차, 계산값 $($it.Round)"
    }
    [void]$it.Tails.Add("- **$($it.Round)회차** ($rowDate): $text"); $nTails++
}

# ── 6. 쓰기: T 파일 → 트래커 → 허브 → 검사기 ─────────────────────────────────
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
Get-ChildItem $OutDir -Filter 'T-*.md' -File | Remove-Item -Force
$kindLabel = @{ heading = '헤딩형(4필드 이전 형식)'; table = '표 행형' }
foreach ($id in ($items.Keys | Sort-Object)) {
    $it = $items[$id]
    $out = New-Object System.Collections.ArrayList
    [void]$out.AddRange(@('---', "summary: $($it.Summary)"))
    if ($it.Date) { [void]$out.Add("date: $($it.Date)") }
    [void]$out.AddRange(@("legacy: $Date 단일 파일 이관 · $($kindLabel[$it.Kind])", '---', '', "# T-$id · $($it.Summary)"))
    if ($it.Body.Count -gt 0) { [void]$out.Add(''); [void]$out.AddRange($it.Body) }
    if ($it.Tails.Count -gt 0) { [void]$out.Add(''); [void]$out.AddRange($it.Tails) }
    [System.IO.File]::WriteAllText((Join-Path $OutDir "T-$id.md"), (($out -join "`n") + "`n"), $utf8)
}

$tracker = @(
    "# 재발·승격 트래커 (동결 — $Date 분할 이관)",
    '',
    "> 단일 파일 시절(2026-06-25~$Date) 손으로 갱신하던 표. **더 갱신하지 않는다** — 회차는 각 ``troubleshooting/T-###.md`` 끝 ``- **N회차**`` 줄, 승격은 frontmatter ``promoted:``/``guard:``, 교차 프로젝트 재발은 ``~/.claude/scripts/scan-troubleshooting-recurrence.ps1`` 보고서가 센다. 44군의 ✅ 하드픽스 경로를 ``guard:``로 옮기는 소급은 별건.",
    '',
    $trackerBlock
)
if ($residual.Count -gt 0) { $tracker += @('', '## 누적 갱신 잔여', '') + @($residual) }
[System.IO.File]::WriteAllText($TrackerOut, (($tracker -join "`n") + "`n"), $utf8)

$ids = @($items.Keys | Sort-Object)
$max = [int]$ids[-1]
$gaps = @(1..$max | ForEach-Object { 'T-{0:D3}' -f $_ } | Where-Object { -not $items.ContainsKey($_.Substring(2)) })
$kb = [Math]::Floor($utf8.GetByteCount($raw) / 1000)   # LF 정규화 기준 — CRLF 체크아웃에서 돌려도 같은 허브가 나온다
$nTable = $items.Count - $nHeading
$hub = @(
    '# 트러블슈팅 — 작업 중 만난 함정과 해결법',
    '',
    '> 1분+ 디버깅한 함정은 원인이 잡히면 **먼저 가드(테스트·코드 가드·설정·훅)로 막는다**. 막았으면 `summary:`+`guard:` 두 줄 항목, 문서로만 막을 수 있을 때만 4필드(**증상 / 원인 / 해결 / 재발방지**).',
    '> 각 항목은 **파일 1개**(`troubleshooting/T-###.md`)다. 아래 목차는 **자동 생성** — 손대지 않는다.',
    "> 찾을 때는 이 파일을 Read하지 말고 ``summary:``를 검색한다: ``Select-String -Path claude-docs/troubleshooting/T-*.md -Pattern '^summary:' | Select-String '<키워드>'``.",
    '',
    '## 규칙',
    '',
    '- **항목 1건 = 파일 1개**: `troubleshooting/T-###.md`. T번호는 프로젝트 전역 시퀀스(빈 번호 없이 이어붙인다).',
    '- **4필드는 `guard:`가 없는 항목에만 필수**: `- **증상**:` / `- **원인**:` / `- **해결**:` / `- **재발방지**:`. 검사기가 강제한다(누락 시 커밋 거부). frontmatter `guard:`(가드의 경로 한 구절 — 테스트 파일:줄·훅 파일·설정 키)가 있으면 면제되고 목차에 `→ 가드:`로 닫힘 표시된다. 단일 파일에서 이관한 옛 항목은 `legacy: <날짜 · 출처>`로 4필드 면제 — 새 항목에는 쓰지 않는다.',
    '- **frontmatter `summary:`**: 목차 한 줄의 **단일 출처**. 본문 H1은 `# T-### · 제목`(파일명과 번호 일치).',
    '- **2회차**: 새 번호를 만들지 않는다 — 먼저 이 프로젝트 `summary:`를 검색해 같은 함정이면 그 파일 끝에 `- **2회차** (날짜): …` 한 줄. 가드가 있던 항목이면 가드가 샌 것이니 가드를 고치고 `guard:`를 갱신한다.',
    '- **승격**: 같은 함정을 2회+ 다른 맥락에서 만나면 프로젝트 로컬 → 글로벌(계단 선택은 `/hookify`의 배치·승격 사다리). 승격 후 본문은 지우지 말고 frontmatter에 `promoted: <대상>`을 달아 이력을 보존한다.',
    '- **목차는 자동 생성**: 손으로 고치지 말고 `scripts/rebuild-troubleshooting-index.ps1`을 돌린다. pre-commit 훅이 stale이면 커밋을 거부한다.',
    '',
    '## 이관 이력',
    '',
    "$Date 단일 파일($($kb)KB · T-$($ids[0])~T-$($ids[-1]), 결번 $($gaps -join '·'))을 분할했다. 이관 항목은 frontmatter ``legacy:``로 표시되어 4필드 검사를 면제받는다(원문 그대로 — 헤딩형 $nHeading · 표 행형 $nTable). 옛 「🔁 재발·승격 트래커」 표는 [troubleshooting-tracker.md](troubleshooting-tracker.md)에 **동결** — 이후 회차는 각 T 파일 끝의 ``- **N회차**`` 줄, 승격은 ``promoted:``/``guard:``. 변환 스크립트: ``scripts/migrate-troubleshooting-split.ps1``.",
    '',
    '## 항목 목차 (자동 생성 — 직접 편집 금지)',
    '',
    '<!-- INDEX:START -->',
    '<!-- INDEX:END -->'
)
[System.IO.File]::WriteAllText($HubOut, (($hub -join "`n") + "`n"), $utf8)

$chk = (& powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $checker -HubPath $HubOut | Out-String)
if ($LASTEXITCODE -ne 0 -or -not $chk.Contains('INDEX-CHECK: REBUILT')) { Write-Host $chk; Fail "checker did not rebuild (exit $LASTEXITCODE)" }

Write-Host "heading=$nHeading table=$nTable gaps=$($gaps -join ',')"
Write-Host "MIGRATE: OK files=$($items.Count) tails=$nTails residual=$($residual.Count)"
exit 0
