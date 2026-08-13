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
  # both node_modules dirs (frontend AND miniapp) are junction-linked in a worktree
  $mainNm   = Join-Path $main 'frontend\node_modules'
  $mainMini = Join-Path $main 'miniapp\node_modules'
  $wtNm     = Join-Path $wt   'frontend\node_modules'
  $wtMini   = Join-Path $wt   'miniapp\node_modules'
  foreach ($d in 'frontend', 'miniapp') {
    $tgt = Join-Path $main "$d\node_modules"
    $lnk = Join-Path $wt   "$d\node_modules"
    New-Item -ItemType Directory $tgt | Out-Null
    Set-Content -Path (Join-Path $tgt 'keep.txt') -Value 'precious-dep'
    New-Item -ItemType Directory (Split-Path -Parent $lnk) | Out-Null
    New-Item -ItemType Junction -Path $lnk -Target $tgt | Out-Null
  }
  return @{ Main = $main; Wt = $wt; MainNm = $mainNm; WtNm = $wtNm; MainMini = $mainMini; WtMini = $wtMini }
}

# main worktree on 'main' tracking a bare origin/main (clean), plus a feature worktree.
# Used to exercise the end-of-run "refresh main" (git pull) step.
function New-FixtureWithOrigin {
  param([string]$name)
  $origin = Join-Path $root "$name-origin.git"
  git init -q --bare $origin | Out-Null
  $main = Join-Path $root "$name-main"
  New-Item -ItemType Directory $main | Out-Null
  Push-Location $main
  try {
    git init -q
    git config user.email 't@t.t'; git config user.name 'tester'
    Set-Content -Path (Join-Path $main 'README.md') -Value 'v1'
    git add -A; git commit -qm init | Out-Null
    git branch -M main
    git remote add origin $origin
    git push -q -u origin main | Out-Null
    $wt = Join-Path $root "$name-wt"
    git worktree add -q $wt -b "$name-br" | Out-Null
  } finally { Pop-Location }
  return @{ Origin = $origin; Main = $main; Wt = $wt }
}

# push a new commit ($file) to origin/main from a throwaway clone, so the
# fixture's main worktree becomes exactly 1 behind origin/main.
function Advance-Origin {
  param($fx, [string]$file, [string]$content)
  $bump = "$($fx.Main)-bump"
  git clone -q $fx.Origin $bump | Out-Null
  Push-Location $bump
  try {
    git config user.email 't2@t.t'; git config user.name 'tester2'
    git checkout -q -B main origin/main
    Set-Content -Path (Join-Path $bump $file) -Value $content
    git add -A; git commit -qm bump | Out-Null
    git push -q origin main | Out-Null
  } finally { Pop-Location }
  Remove-Item $bump -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "== test 1: dry-run is non-destructive =="
$f = New-Fixture 'dry'
& $target -Path $f.Wt -DryRun | Out-Null
check (Test-Path $f.Wt) "dry-run: worktree preserved"
check ((Get-Item $f.WtNm -Force).LinkType -eq 'Junction') "dry-run: frontend junction preserved"
check ((Get-Item $f.WtMini -Force).LinkType -eq 'Junction') "dry-run: miniapp junction preserved"
check (Test-Path (Join-Path $f.MainNm 'keep.txt')) "dry-run: frontend target dep preserved"
check (Test-Path (Join-Path $f.MainMini 'keep.txt')) "dry-run: miniapp target dep preserved"

Write-Host "== test 2: normal remove keeps junction TARGET (T-110) =="
$f = New-Fixture 'normal'
& $target -Path $f.Wt | Out-Null
check (-not (Test-Path $f.Wt)) "worktree removed"
check (Test-Path (Join-Path $f.MainNm 'keep.txt')) "*** main frontend/node_modules target PRESERVED (T-110) ***"
check (Test-Path (Join-Path $f.MainMini 'keep.txt')) "*** main miniapp/node_modules target PRESERVED (T-110) ***"
check (@(Get-ChildItem $f.MainNm -Force).Count -ge 1) "frontend target not emptied"
check (@(Get-ChildItem $f.MainMini -Force).Count -ge 1) "miniapp target not emptied"

Write-Host "== test 2b: -Force remove keeps junction TARGETs (the actual T-110 path) =="
$f = New-Fixture 'forced'
& $target -Path $f.Wt -Force | Out-Null
check (-not (Test-Path $f.Wt)) "worktree removed (-Force)"
check (Test-Path (Join-Path $f.MainNm 'keep.txt')) "*** frontend target PRESERVED under -Force ***"
check (Test-Path (Join-Path $f.MainMini 'keep.txt')) "*** miniapp target PRESERVED under -Force ***"

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

Write-Host "== test 5: default run fast-forwards the main worktree's main (git pull) =="
$f = New-FixtureWithOrigin 'pull'
Advance-Origin $f 'NEWFILE.txt' 'from-origin'
& $target -Path $f.Wt | Out-Null
check (Test-Path (Join-Path $f.Main 'NEWFILE.txt')) "*** main worktree fast-forwarded (origin commit pulled) ***"

Write-Host "== test 6: dirty main worktree is NOT pulled (skip guard) =="
$f = New-FixtureWithOrigin 'dirty'
Advance-Origin $f 'NEWFILE.txt' 'from-origin'
Set-Content -Path (Join-Path $f.Main 'README.md') -Value 'locally-modified'
& $target -Path $f.Wt | Out-Null
check (-not (Test-Path (Join-Path $f.Main 'NEWFILE.txt'))) "dirty main not fast-forwarded"
check ((Get-Content (Join-Path $f.Main 'README.md')) -eq 'locally-modified') "local edit preserved"

Write-Host "== test 7: main worktree on a feature branch is NOT pulled =="
$f = New-FixtureWithOrigin 'featbr'
Advance-Origin $f 'NEWFILE.txt' 'from-origin'
Push-Location $f.Main; git checkout -q -b side-work; Pop-Location
& $target -Path $f.Wt | Out-Null
check (-not (Test-Path (Join-Path $f.Main 'NEWFILE.txt'))) "feature-branch main not fast-forwarded"

Write-Host "== test 8: -NoPull skips the refresh =="
$f = New-FixtureWithOrigin 'nopull'
Advance-Origin $f 'NEWFILE.txt' 'from-origin'
& $target -Path $f.Wt -NoPull | Out-Null
check (-not (Test-Path (Join-Path $f.Main 'NEWFILE.txt'))) "-NoPull leaves main un-refreshed"

Set-Location $env:TEMP
Reset-Root

Write-Host ""
if ($script:fails -eq 0) { Write-Host "ALL PASS"; exit 0 } else { Write-Host "$($script:fails) CHECK(S) FAILED"; exit 1 }
