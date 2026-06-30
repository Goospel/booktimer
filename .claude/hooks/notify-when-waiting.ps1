# Notification hook (B1) — 사용자 확인/입력 대기 시 Windows 토스트 알림.
#
# 목적: 머지 승인 같은 확인을 기다리며 턴이 멈췄을 때, 다른 탭을 보던 사용자에게도
#       OS 토스트로 신호가 닿게 한다(2026-06-30 45분 freeze 회고의 B1).
# 성격: SIDE-EFFECT 전용 — 무슨 일이 있어도 exit 0(세션을 절대 차단·지연하지 않음).
#       토스트 발사 실패도 조용히 무시(fail-open). settings.json 에서 timeout 으로 한 번 더 가드.
# 인코딩: 한글 토스트 본문 때문에 이 파일은 UTF-8 BOM 으로 저장한다(PS 5.1 CP949 깨짐 회피, T-026 계열).
#
# 환경변수:
#   BOOKTIMER_NOTIFY_DRYRUN  설정 시 실제 토스트 대신 'WOULD_TOAST: <본문>'만 출력(테스트/헤드리스).
#   BOOKTIMER_NOTIFY_LOG     디버그 로그 경로 override(기본: 스크립트 옆 notify-debug.log).

$ErrorActionPreference = 'Continue'

# ── stdin(UTF-8) 읽기 ──────────────────────────────────────────────────────
$raw = ''
try {
    $reader = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
    $raw = $reader.ReadToEnd()
} catch { exit 0 }   # stdin 못 읽어도 절대 차단 안 함

# ── 페이로드 파싱(실패해도 fail-open) ──────────────────────────────────────
$evt = ''; $ntype = ''; $msg = ''
try {
    if (-not [string]::IsNullOrWhiteSpace($raw)) {
        $data  = $raw | ConvertFrom-Json
        $evt   = [string]$data.hook_event_name
        # 알림 타입 필드명이 환경마다 다를 수 있어 후보를 차례로 확인
        $names = @($data.PSObject.Properties.Name)
        if     ($names -contains 'notificationType')  { $ntype = [string]$data.notificationType }
        elseif ($names -contains 'notification_type') { $ntype = [string]$data.notification_type }
        $msg = [string]$data.message
    }
} catch { }   # 깨진 JSON = fail-open(일반 문구로 진행)

# ── 디버그 로그(실측용: 데스크톱 앱이 실제로 어떤 이벤트/타입을 쏘는지 학습) ──
$logPath = $env:BOOKTIMER_NOTIFY_LOG
if ([string]::IsNullOrWhiteSpace($logPath)) { $logPath = Join-Path $PSScriptRoot 'notify-debug.log' }
try {
    $stamp = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    Add-Content -LiteralPath $logPath -Value "[$stamp] event=$evt type=$ntype msg=$($msg -replace '\s+',' ')" -Encoding UTF8 -EA SilentlyContinue
} catch { }

# ── 토스트 본문 구성 ───────────────────────────────────────────────────────
$title = 'BookTimer — Claude Code'
$body  = if (-not [string]::IsNullOrWhiteSpace($msg)) { $msg } else { '확인/입력을 기다리는 중입니다' }
if (-not [string]::IsNullOrWhiteSpace($ntype)) { $body = "$body  ($ntype)" }

# ── DRY-RUN: 실제 토스트 대신 의도만 출력(테스트/헤드리스) ──────────────────
if ($env:BOOKTIMER_NOTIFY_DRYRUN) {
    Write-Output "WOULD_TOAST: $body"
    exit 0
}

# ── Windows 토스트 발사(등록된 시스템 AppID=Explorer). 실패해도 무시. ───────
try {
    $null  = [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime]
    $tpl   = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)
    $texts = $tpl.GetElementsByTagName('text')
    $texts.Item(0).AppendChild($tpl.CreateTextNode($title)) | Out-Null
    $texts.Item(1).AppendChild($tpl.CreateTextNode($body))  | Out-Null
    $toast = [Windows.UI.Notifications.ToastNotification]::new($tpl)
    [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('Microsoft.Windows.Explorer').Show($toast)
} catch { }

exit 0
