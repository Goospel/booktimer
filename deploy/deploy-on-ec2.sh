#!/usr/bin/env bash
# BookTimer 무중단 배포 (EC2 단일 인스턴스, blue-green).
#
# GitHub Actions가 SSM Send-Command로 EC2에서 이 스크립트를 실행한다.
# 앞단 Caddy가 app-blue·app-green 두 upstream을 active health check로 지켜보므로,
# **이 스크립트는 Caddy 설정을 건드리지 않는다** — 새 쪽이 healthy가 되면 Caddy가 알아서 붙이고,
# 옛 쪽이 사라지면 알아서 뗀다. 그래서 하는 일은 "반대 색을 띄우고 → 확인되면 옛 색을 지운다" 뿐이다.
#
# 실패 원칙: 새 컨테이너가 헬스체크를 통과하지 못하면 **옛 컨테이너를 절대 건드리지 않고** 종료한다.
# (옛 것을 먼저 내리면 배포 실패 = 서비스 전면 중단이 된다.)
#
# 테스트: deploy/tests/test-deploy-bluegreen.sh (DEPLOY_DRYRUN=1로 docker 호출 전부 우회)
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-compose.prod.yaml}"
HEALTH_RETRIES="${HEALTH_RETRIES:-60}"   # 60 × 3초 = 최대 3분 (Spring Boot 부팅 여유)
HEALTH_SLEEP="${HEALTH_SLEEP:-3}"
DRYRUN="${DEPLOY_DRYRUN:-0}"

dc() {  # docker compose 래퍼 — dry-run이면 실행 대신 명령만 출력
    if [ "$DRYRUN" = 1 ]; then echo "docker compose -f $COMPOSE_FILE $*"; return 0; fi
    docker compose -f "$COMPOSE_FILE" "$@"
}

running_services() {
    if [ "$DRYRUN" = 1 ]; then printf '%s\n' ${DEPLOY_FAKE_RUNNING:-}; return 0; fi
    docker compose -f "$COMPOSE_FILE" ps --services --filter status=running
}

health_ok() {  # $1 = 서비스명. 전체 /actuator/health가 아니라 readiness 그룹을 본다 —
               # 전체 health는 mail indicator를 포함해 SES 장애만으로 DOWN이 되고,
               # 그러면 멀쩡한 배포가 실패로 뒤집힌다(T-009 계열).
    if [ "$DRYRUN" = 1 ]; then [ "${DEPLOY_FAKE_HEALTH:-ok}" = ok ]; return $?; fi
    docker compose -f "$COMPOSE_FILE" exec -T "$1" \
        curl -sf --max-time 5 http://localhost:8080/actuator/health/readiness >/dev/null 2>&1
}

# ── 1) 전환 방향 결정 (blue ↔ green 대칭) ──
if running_services | grep -qx app-blue; then
    NEW=app-green; OLD=app-blue
else
    NEW=app-blue;  OLD=app-green
fi
echo "[deploy] $OLD → $NEW 로 전환합니다"

# ── 2) 시크릿 갱신 + ECR 로그인 + 새 이미지 가져오기 ──
if [ "$DRYRUN" != 1 ]; then
    ./render-env.sh          # SSM Parameter Store → .env (컨테이너가 env_file로 읽음)

    # 인스턴스 역할에 ecr:GetAuthorizationToken 이 있어도 docker 자체는 로그인이 필요하다.
    # 토큰은 12시간 유효하므로 배포마다 갱신한다.
    registry="$(grep -m1 '^BOOKTIMER_IMAGE=' .env | cut -d= -f2- | cut -d/ -f1)"
    aws ecr get-login-password --region "${AWS_REGION:-ap-northeast-2}" \
        | docker login --username AWS --password-stdin "$registry"
fi
dc pull "$NEW"

# ── 3) 새 컨테이너 기동 (옛 컨테이너는 계속 트래픽을 받는 중) ──
dc up -d "$NEW"

# ── 4) 헬스 대기 ──
ok=0
for _ in $(seq 1 "$HEALTH_RETRIES"); do
    if health_ok "$NEW"; then ok=1; break; fi
    sleep "$HEALTH_SLEEP"
done

# ── 5) 실패: 새 것만 버리고 옛 것은 그대로 살려둔다 ──
if [ "$ok" != 1 ]; then
    echo "[deploy] $NEW 헬스체크 실패 — $OLD 를 유지한 채 롤백합니다" >&2
    dc rm -sf "$NEW" || true
    exit 1
fi

# ── 6) 성공: 옛 컨테이너를 graceful 종료 후 제거 ──
# stop(SIGTERM) + 유예로 진행 중 요청을 마무리시킨다. rm -sf(SIGKILL)로 즉시 죽이면
# 그 순간 처리 중이던 요청이 끊긴다(실측으로 확인). graceful이어도 stop 시점의 in-flight
# 1건 정도는 남을 수 있다 — Caddyfile 주석 참고.
echo "[deploy] $NEW healthy — $OLD 를 내립니다"
dc stop -t 30 "$OLD" || true
dc rm -f "$OLD" || true       # 콜드 스타트라 상대가 없을 수도 있다(정상)
if [ "$DRYRUN" = 1 ]; then echo "docker image prune -f"; else docker image prune -f; fi
echo "[deploy] 완료 ($NEW 서비스 중)"
