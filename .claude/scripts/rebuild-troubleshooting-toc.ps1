# rebuild-troubleshooting-toc.ps1
#
# WARNING: 이 파일은 UTF-8 BOM 포함으로 저장해야 한다 — PowerShell 5.1은 BOM 없는
#    UTF-8을 시스템 ANSI(CP949)로 오독해 아래 한글 주석·매칭 리터럴이 깨진다(T-026 계열).
#    편집 도구로 재저장 시 BOM이 떨어지면 재부착할 것.
#
# troubleshooting.md의 "## 📑 목차" 섹션을 본문 "## T-###" 헤딩에서 자동 재생성한다.
# rebuild-memory-index.ps1의 troubleshooting 판 — 목차를 손으로 관리하다 항목이 빠지는
# drift를 구조적으로 제거한다. 목차는 100% 파생물이라 덮어써도 정보 손실이 0이다.
#
# anchor = GitHub 규칙: 소문자화 -> [^\p{L}\p{N} _-] 제거 -> 공백을 '-'로.
# stdout: 'changed'(목차 갱신) | 'unchanged'(이미 동기 또는 비대상).
# 실패해도 본문은 절대 건드리지 않는다(목차 자동화 실패가 작업을 막지 않게).
[CmdletBinding()]
param(
    [string]$Path = 'claude-docs/troubleshooting.md'
)

$ErrorActionPreference = 'Stop'
try {
    if (-not (Test-Path $Path)) { Write-Output 'unchanged'; return }

    # 원본 BOM 유무를 보존한다(ReadAllText는 BOM을 떼므로 바이트로 따로 감지).
    $head = [System.IO.File]::ReadAllBytes($Path)
    $hasBom = ($head.Length -ge 3 -and $head[0] -eq 0xEF -and $head[1] -eq 0xBB -and $head[2] -eq 0xBF)

    $raw = [System.IO.File]::ReadAllText($Path)
    if ([string]::IsNullOrEmpty($raw)) { Write-Output 'unchanged'; return }

    # EOL과 끝 개행을 원본대로 보존한다(phantom CRLF 전체 diff 회피, T-093 정신).
    $nl = if ($raw -match "`r`n") { "`r`n" } else { "`n" }
    $hadTrailing = $raw.EndsWith("`n")
    $parts = [System.Collections.Generic.List[string]]($raw -split "`r`n|`n")
    if ($hadTrailing -and $parts.Count -gt 0 -and $parts[$parts.Count - 1] -eq '') {
        $parts.RemoveAt($parts.Count - 1)
    }
    $lines = $parts.ToArray()

    # 목차 섹션 경계: '## ... 목차' 라인 ~ 그 다음 '---' 또는 '## ' 라인(exclusive).
    $tocStart = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^##\s+.*목차') { $tocStart = $i; break }
    }
    if ($tocStart -lt 0) { Write-Output 'unchanged'; return }  # 목차 섹션 없음 -> 본문 불변

    $tocEnd = $lines.Count
    for ($i = $tocStart + 1; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^---\s*$' -or $lines[$i] -match '^##\s') { $tocEnd = $i; break }
    }

    function Get-Anchor([string]$h) {
        $a = $h.ToLower()
        $a = [regex]::Replace($a, '[^\p{L}\p{N} _-]', '')
        return ($a -replace ' ', '-')
    }

    # 본문 '## T-###. 제목' 헤딩을 등장 순서대로 수집.
    $items = New-Object System.Collections.Generic.List[string]
    foreach ($l in $lines) {
        $m = [regex]::Match($l, '^##\s+(T-\d+\..*?)\s*$')
        if ($m.Success) {
            $h = $m.Groups[1].Value
            $items.Add("- [$h](#$(Get-Anchor $h))")
        }
    }
    if ($items.Count -eq 0) { Write-Output 'unchanged'; return }

    # 새 목차 블록 = 헤딩 라인 유지 + 빈줄 + 항목들 + 빈줄. 나머지(본문)는 그대로.
    $rebuilt = New-Object System.Collections.Generic.List[string]
    for ($i = 0; $i -lt $tocStart; $i++) { $rebuilt.Add($lines[$i]) }
    $rebuilt.Add($lines[$tocStart])
    $rebuilt.Add('')
    foreach ($it in $items) { $rebuilt.Add($it) }
    $rebuilt.Add('')
    for ($i = $tocEnd; $i -lt $lines.Count; $i++) { $rebuilt.Add($lines[$i]) }

    $newText = ($rebuilt.ToArray() -join $nl)
    $oldText = ($lines -join $nl)
    if ($hadTrailing) { $newText += $nl; $oldText += $nl }

    if ($newText -eq $oldText) { Write-Output 'unchanged'; return }

    $enc = New-Object System.Text.UTF8Encoding($hasBom)  # 원본 BOM 유무 보존
    [System.IO.File]::WriteAllText($Path, $newText, $enc)
    Write-Output 'changed'
}
catch {
    # 비치명: 본문을 망치지 않도록 조용히 동기로 간주하고 종료.
    Write-Output 'unchanged'
}
