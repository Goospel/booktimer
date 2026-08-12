# 토스 미니앱 개발용 Android 에뮬레이터 준비 (멱등)
# 에뮬레이터 부팅 → 샌드박스 앱 설치 → adb reverse 포트 매핑까지.
# dev 서버(npm --prefix miniapp run dev)는 이 스크립트가 띄우지 않는다 — HMR 로그를 따로 보기 위해.
param(
    [string]$Avd = 'Medium_Phone_API_36.1',
    [switch]$ColdBoot
)

$ErrorActionPreference = 'Stop'

$SandboxPkg = 'viva.republica.toss.test'
$SandboxZip = 'https://static.toss.im/appsintoss/rn-miniapp-real-release-protected.zip'
$BootTimeoutSec = 300

$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emu = Join-Path $sdk 'emulator\emulator.exe'
if (-not (Test-Path $adb) -or -not (Test-Path $emu)) {
    Write-Host "[에러] Android SDK를 찾지 못했습니다: $sdk" -ForegroundColor Red
    Write-Host "       emulator.exe / platform-tools\adb.exe 가 있어야 합니다. Android Studio의 SDK Manager로 설치하세요."
    exit 1
}

# adb devices 첫 에뮬레이터의 상태('device'/'offline'/'unauthorized') 또는 $null
function Get-EmulatorState {
    $line = (& $adb devices) | Where-Object { $_ -match '^emulator-\S+\s+(\S+)' } | Select-Object -First 1
    if ($line) { return ([regex]::Match($line, '^emulator-\S+\s+(\S+)').Groups[1].Value) }
    return $null
}

$state = Get-EmulatorState
if ($state -eq 'device') {
    Write-Host "[건너뜀] 에뮬레이터가 이미 떠 있습니다(device)."
} else {
    if ($state) {
        Write-Warning "에뮬레이터가 '$state' 상태입니다 — Quick Boot 스냅샷 꼬임일 수 있습니다(부팅이 안 끝나면 아래 안내 참고)."
    } else {
        $emuArgs = @('-avd', $Avd)   # $args는 자동 변수라 쓰지 않는다
        if ($ColdBoot) { $emuArgs += '-no-snapshot-load' }
        Write-Host "[부팅] $Avd $(if ($ColdBoot) { '(콜드 부트)' })"
        Start-Process -FilePath $emu -ArgumentList $emuArgs -WorkingDirectory (Split-Path $emu) | Out-Null
    }

    Write-Host "[대기] sys.boot_completed=1 (최대 $([int]($BootTimeoutSec / 60))분)"
    $deadline = (Get-Date).AddSeconds($BootTimeoutSec)
    $booted = $false
    while ((Get-Date) -lt $deadline) {
        $prop = (& $adb shell getprop sys.boot_completed 2>$null | Out-String).Trim()
        if ($prop -eq '1') { $booted = $true; break }
        Start-Sleep -Seconds 5
    }
    if (-not $booted) {
        Write-Host "[에러] 부팅이 끝나지 않았습니다(현재 상태: $(Get-EmulatorState))." -ForegroundColor Red
        Write-Host "       Quick Boot 스냅샷 꼬임일 수 있습니다(adb devices가 20분+ offline, qemu만 CPU 소모 — T-151)."
        Write-Host "       조치: 프로세스를 강제 종료한 뒤 콜드 부트로 재시도하세요."
        Write-Host "         Get-Process qemu-system-x86_64, emulator -EA SilentlyContinue | Stop-Process -Force"
        Write-Host "         powershell -File .claude/scripts/miniapp-emulator.ps1 -ColdBoot"
        exit 1
    }
    Write-Host "[완료] 부팅됨."
}

$installed = ((& $adb shell pm list packages $SandboxPkg) | Out-String) -match [regex]::Escape($SandboxPkg)
if ($installed) {
    Write-Host "[건너뜀] 샌드박스 앱($SandboxPkg)이 이미 설치돼 있습니다."
} else {
    $zip = Join-Path $env:TEMP 'toss-miniapp-sandbox.zip'
    $dir = Join-Path $env:TEMP 'toss-miniapp-sandbox'
    if (-not (Test-Path $zip)) {
        Write-Host "[다운로드] $SandboxZip"
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $SandboxZip -OutFile $zip -UseBasicParsing
    }
    Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive -Path $zip -DestinationPath $dir -Force
    $apk = Get-ChildItem $dir -Filter *.apk -Recurse | Select-Object -First 1
    if (-not $apk) {
        Write-Host "[에러] 압축 안에 APK가 없습니다: $dir" -ForegroundColor Red
        exit 1
    }
    Write-Host "[설치] $($apk.Name)"
    & $adb install -r $apk.FullName
    $installed = ((& $adb shell pm list packages $SandboxPkg) | Out-String) -match [regex]::Escape($SandboxPkg)
    if (-not $installed) {
        Write-Host "[에러] 설치 후에도 패키지가 보이지 않습니다: $SandboxPkg" -ForegroundColor Red
        exit 1
    }
}

# 미니앱 vite는 5174인데 샌드박스 앱은 5173을 본다 — 매핑 주의. 재실행해도 무해.
& $adb reverse tcp:5173 tcp:5174 | Out-Null
& $adb reverse tcp:8081 tcp:8081 | Out-Null
$reverses = (& $adb reverse --list) -join '; '

Write-Host ""
Write-Host "===== 미니앱 에뮬레이터 준비 완료 ====="
Write-Host "  부팅       : $(Get-EmulatorState)"
Write-Host "  샌드박스   : $(if ($installed) { "설치됨 ($SandboxPkg)" } else { '미설치' })"
Write-Host "  adb reverse: $reverses"
Write-Host "  다음       : npm --prefix miniapp run dev 실행 후, 에뮬레이터 샌드박스 앱에서 스킴 intoss://booktimer"
