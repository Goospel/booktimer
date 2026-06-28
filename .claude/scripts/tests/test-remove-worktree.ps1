#!/usr/bin/env pwsh
# test-remove-worktree.ps1 - smoke test for remove-worktree.ps1
#
# Core invariant (T-110 regression guard): cutting the frontend/node_modules junction
# must NOT delete the junction TARGET (the main worktree's node_modules) contents.
# Also asserts: dry-run is non-destructive, non-worktree paths are rejected,
# and a cwd inside the target is rejected (cannot delete the folder you stand in).
#
# Run: powershell -File .claude/scripts/tests/test-remove-worktree.ps1
# Exit: 0 all pass, 1 a check failed.
$ErrorActionPreference = 'Stop'
$script:fails = 0
function ok($m)   { Write-Host "  [PASS] $m" }
function bad($m)  { Write-Host "  [FAIL] $m"; $script:fails++ }
function check($cond, $m) { if ($cond) { ok $m } else { bad $m } }

$here   = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $here '..\remove-worktree.ps1'
$target = (Resolve-Path $target -ErrorAction SilentlyContinue).Path
if (-not $target) {
  Write-Host "remove-worktree.ps1 not found yet (RED expected before implementation)."
  exit 1
}

$root = Join-Path $env:TEMP ("rmwt-test-" + $PID)
function Reset-Root {
  if (Test-Path $script:root) {
    # cut any leftover junctions first so cleanup never follows a link into a target
    Get-ChildItem $script:root -Recurse -Force -ErrorAction SilentlyContinue |
      Where-Object { $_.LinkType -eq 'Junction' } |
      ForEach-Object { try { [IO.Directory]::Delete($_.FullName) } catch {} }
    Remove-Item $script:root -Recurse -Force -ErrorAction SilentlyContinue
  }
}
Reset-Root
New-Item -ItemType Directory $root | Out-Null

function New-Fixture {
  param([string]$name)
  $main = Join-Path $root "$name-main"
  New-Item -ItemType Directory $main | Out-Null
  Push-Location $main
  try {
    git init -q
    git config user.email 't@t.t'; git config user.name 'tester'
    Set-Content -Path (Join-Path $main 'README.md') -Value 'x'
    git add -A; git commit -qm init | Out-Null
    $wt = Join-Path $root "$name-wt"
    git worktree add -q $wt -b "$name-br" | Out-Null
  } finally { Pop-Location }
  $mainNm = Join-Path $main 'frontend\node_modules'
  New-Item -ItemType Directory $mainNm | Out-Null
  Set-Content -Path (Join-Path $mainNm 'keep.txt') -Value 'precious-dep'
  $wtFront = Join-Path $wt 'frontend'
  New-Item -ItemType Directory $wtFront | Out-Null
  $wtNm = Join-Path $wtFront 'node_modules'
  New-Item -ItemType Junction -Path $wtNm -Target $mainNm | Out-Null
  return @{ Main = $main; Wt = $wt; MainNm = $mainNm; WtNm = $wtNm }
}

Write-Host "== test 1: dry-run is non-destructive =="
$f = New-Fixture 'dry'
& $target -Path $f.Wt -DryRun | Out-Null
check (Test-Path $f.Wt) "dry-run: worktree preserved"
check ((Get-Item $f.WtNm -Force).LinkType -eq 'Junction') "dry-run: junction preserved"
check (Test-Path (Join-Path $f.MainNm 'keep.txt')) "dry-run: target dep preserved"

Write-Host "== test 2: normal remove keeps junction TARGET (T-110) =="
$f = New-Fixture 'normal'
& $target -Path $f.Wt | Out-Null
check (-not (Test-Path $f.Wt)) "worktree removed"
check (Test-Path (Join-Path $f.MainNm 'keep.txt')) "*** main node_modules target PRESERVED (T-110) ***"
check (@(Get-ChildItem $f.MainNm -Force).Count -ge 1) "target not emptied"

Write-Host "== test 3: non-worktree path is rejected =="
$bogus = Join-Path $root 'bogus-not-a-worktree'
New-Item -ItemType Directory $bogus | Out-Null
& $target -Path $bogus 2>$null | Out-Null
check ($LASTEXITCODE -eq 3) "non-worktree path rejected (exit 3)"
check (Test-Path $bogus) "bogus dir untouched"

Write-Host "== test 4: cwd inside target is rejected =="
$f = New-Fixture 'cwd'
Push-Location $f.Wt
& $target -Path $f.Wt 2>$null | Out-Null
$ec = $LASTEXITCODE
Pop-Location
check ($ec -eq 3) "cwd-inside-target rejected (exit 3)"
check (Test-Path $f.Wt) "worktree preserved when cwd inside"

Set-Location $env:TEMP
Reset-Root

Write-Host ""
if ($script:fails -eq 0) { Write-Host "ALL PASS"; exit 0 } else { Write-Host "$($script:fails) CHECK(S) FAILED"; exit 1 }
