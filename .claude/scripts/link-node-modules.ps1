#!/usr/bin/env pwsh
# link-node-modules.ps1 — 워크트리의 frontend/node_modules 를 main 워크트리 것으로 정션 연결
#
# 목적: git worktree 로 작업 폴더를 분리할 때마다 frontend 의존성을 새로 npm install 하는
#   마찰을 없앤다. node_modules 는 git 미추적(.gitignore:55)이라 워크트리엔 안 따라오는데,
#   커밋(추적)은 안티패턴(수만 파일·sharp 등 OS별 네이티브 바이너리·머지지옥)이라 답이 아니다.
#   대신 main 워크트리의 node_modules 를 워크트리에서 Windows 디렉토리 정션(junction)으로
#   재사용한다 — 다운로드 0·디스크 추가 0. frontend 는 빌드 전용(watch 아님)이라 읽기 공유 안전.
#   배경 개념: claude-docs/learning-notes.md N-132.
#
# 동작:
#   1) main 워크트리의 frontend/node_modules 가 비어있으면 거기서 'npm ci' 로 채운다(정션 타깃 확보).
#   2) 현재 워크트리가 main 이면 정션 불필요 — 그대로 둔다.
#   3) 현재 워크트리의 frontend/node_modules 가
#        - 이미 정션               → skip(멱등)
#        - 실제 폴더 + 패키지 있음  → 보존(이미 install). 바꾸려면 -Force.
#        - 없음 / 빈 폴더           → main 을 가리키는 정션 생성.
#
# 사용:
#   .claude/scripts/link-node-modules.ps1            # 현재 워크트리에 정션 연결
#   .claude/scripts/link-node-modules.ps1 -Force     # 기존 실제 node_modules 를 지우고 정션으로 교체
#   .claude/scripts/link-node-modules.ps1 -DryRun    # 무엇을 할지만 출력, 실제 변경 안 함
#
#   ⚠️ 어떤 브랜치가 frontend/package-lock.json 을 바꿨다면 그 워크트리는 정션을 쓰지 말고
#      따로 'npm --prefix frontend ci' 로 자기 의존성을 깔아라(정션은 main 의 의존성을 본다).
#
# 정션은 Windows 전용 · 관리자권한 불필요(심볼릭 링크와 달리). 종료코드: 0 정상, 1 오류.
param([switch]$Force, [switch]$DryRun)

function info($m) { Write-Host "[link-node-modules] $m" }

# ── 1. main 워크트리 / 현재 워크트리 경로 ──────────────────────────────────────────
# --git-common-dir 은 워크트리에서도 항상 main 의 .git 을 가리킨다(absolute 로 강제).
$gitCommonDir = (git rev-parse --path-format=absolute --git-common-dir 2>$null)
if ($LASTEXITCODE -ne 0 -or -not $gitCommonDir) {
  Write-Error "git 저장소가 아닙니다 (저장소 폴더 안에서 실행하세요)."; exit 1
}
$mainDir = Split-Path -Parent $gitCommonDir        # main 의 .git 부모 = main 워킹트리
$curDir  = (git rev-parse --show-toplevel 2>$null)
if (-not $curDir) { Write-Error "현재 워크트리 루트를 찾지 못했습니다."; exit 1 }

$mainNm = Join-Path $mainDir 'frontend\node_modules'
$curNm  = Join-Path $curDir  'frontend\node_modules'

# 경로 정규화 후 main==현재 판별(슬래시·대소문자 차이 흡수)
$norm = { param($p) try { (Resolve-Path $p -EA Stop).Path.TrimEnd('\', '/') } catch { $p.TrimEnd('\', '/') } }
$isMain = ((& $norm $curDir) -ieq (& $norm $mainDir))

# ── 2. 정션 타깃(main 의 node_modules) 확보 ───────────────────────────────────────
$mainEmpty = (-not (Test-Path $mainNm)) -or (@(Get-ChildItem $mainNm -Force -EA SilentlyContinue).Count -eq 0)
if ($mainEmpty) {
  $mainFront = Join-Path $mainDir 'frontend'
  info "main 의 node_modules 가 비어있음 → 'npm ci' 로 채웁니다: $mainFront"
  if (-not $DryRun) {
    Push-Location $mainFront
    try {
      npm ci
      if ($LASTEXITCODE -ne 0) { throw "npm ci 실패 (exit $LASTEXITCODE)" }
    } finally { Pop-Location }
  }
}

# ── 3. 현재가 main 이면 정션 불필요 ───────────────────────────────────────────────
if ($isMain) {
  info "여긴 main 워크트리 — 정션 불필요(실제 node_modules 유지). 끝."
  exit 0
}

# ── 4. 현재 워크트리 상태별 분기 ──────────────────────────────────────────────────
if (Test-Path $curNm) {
  $item = Get-Item $curNm -Force
  if ($item.LinkType -eq 'Junction') {
    info "이미 정션으로 연결돼 있습니다 → skip(멱등). (대상: $($item.Target))"
    exit 0
  }
  $count = @(Get-ChildItem $curNm -Force -EA SilentlyContinue).Count
  if ($count -gt 0 -and -not $Force) {
    info "실제 node_modules 가 이미 있습니다(${count}개 항목) → 보존."
    info "→ 정션으로 교체하려면 -Force 를 주거나 이 폴더를 지우고 재실행하세요."
    exit 0
  }
  info "기존 폴더 제거 후 정션으로 교체합니다 (빈 폴더이거나 -Force)."
  if (-not $DryRun) { Remove-Item $curNm -Recurse -Force }
}

$curFront = Join-Path $curDir 'frontend'
if (-not (Test-Path $curFront)) {
  Write-Error "frontend 디렉토리가 없습니다: $curFront"; exit 1
}

info "정션 생성: $curNm  →  $mainNm"
if ($DryRun) {
  info "(DryRun — 실제로 만들지 않음)"
} else {
  New-Item -ItemType Junction -Path $curNm -Target $mainNm -EA Stop | Out-Null
  info "✅ 완료. 이제 'npm --prefix frontend run build' 가 main 의존성을 재사용합니다."
}
