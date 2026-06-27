# SessionEnd hook — 세션 종료 시 bootRun이 남긴 BookTimer 도커 컨테이너 자동 정리
#
# BookTimer 규칙(CLAUDE.md): bootRun(spring-boot-docker-compose)이 워크트리별로 띄운
# MySQL 컨테이너가 미정리(Up 좀비)·고아(Exited)로 누적된다. 이 훅은 세션이 끝날 때
# docker-cleanup.sh를 "기본 모드(Exited만 — 멀티세션 안전, Up은 보존)"로 1회 호출해
# 일상적 누적을 사람 손 없이 청소한다(gap#3 자동배선). --all은 절대 쓰지 않는다.
#
# SessionEnd 훅은 세션 종료를 막지 못한다(부작용 전용) — 그러므로 docker 미설치·미기동·
# bash 부재 등 무슨 일이 있어도 조용히 exit 0 한다(fail-open). 출력도 버린다(정리는
# 배경 작업이라 종료 흐름에 잡음을 남기지 않는다).
#
# 테스트(test-cleanup-docker-on-session-end.sh)는 env BOOKTIMER_CLEANUP_SCRIPT로
# 정리 스크립트를 스텁으로 갈아끼워 ① 기본 모드(--all 없음) ② fail-open을 검증한다.

$ErrorActionPreference = 'SilentlyContinue'

# stdin(JSON: session_id/reason/cwd 등)은 읽되 쓰지 않는다(블로킹 방지). 필터는 matcher가 한다.
try { $null = [Console]::In.ReadToEnd() } catch { }

# 정리 스크립트 경로: 기본은 이 훅 옆의 ../scripts/docker-cleanup.sh, 테스트는 env로 주입.
$cleanup = $env:BOOKTIMER_CLEANUP_SCRIPT
if ([string]::IsNullOrWhiteSpace($cleanup)) {
    $cleanup = Join-Path $PSScriptRoot '..\scripts\docker-cleanup.sh'
}
# bash가 여는 경로라 역슬래시를 슬래시로(Windows 절대경로도 bash가 인식). 인자 없음 = 기본 모드.
$cleanup = $cleanup -replace '\\', '/'

# bash나 스크립트가 없거나 실패해도 무시 — 어떤 경우에도 종료를 막지 않는다.
try { & bash $cleanup *> $null } catch { }

exit 0
