#!/usr/bin/env bash
# BookTimer 무중단 배포 (EC2 단일 인스턴스, blue-green).
#
# GitHub Actions가 SSM Send-Command로 EC2에서 이 스크립트를 실행한다.
# 앞단 Caddy가 app-blue·app-green 두 upstream을 active health check로 지켜보므로,
# **blue-green 전환 자체는 Caddy 설정을 건드릴 필요가 없다** — 새 쪽이 healthy가 되면 Caddy가
# 알아서 붙이고, 옛 쪽이 사라지면 알아서 뗀다. 그래서 본체는 "반대 색을 띄우고 → 확인되면 옛 색을 지운다" 뿐이다.
# 다만 **Caddyfile 자체가 바뀐 배포**는 다르다 — 워크플로가 파일을 S3로 내려주지만 Caddy는 그걸
# 스스로 다시 읽지 않으므로, 2.5)에서 reload를 쳐 준다(안 그러면 프록시 설정 변경이 조용히 무반영).
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

pem_readable() {  # $1 = 서비스명. 컨테이너가 토스 mTLS 인증서를 **실제로 읽을 수 있는지** 본다.
                  # 컨테이너는 비root(Dockerfile USER)로 돌고 PEM은 600이라, render-env.sh 의 chown 과
                  # uid가 어긋나면 못 읽는다. 그런데 Spring SSL 번들은 **지연 생성**이라 앱은 멀쩡히 뜨고
                  # 위 헬스체크도 통과한다 — 토스 로그인만 죽어 미니앱이 시작조차 못 하는 무성 장애가 된다.
                  # 그래서 uid 산수를 믿지 않고 전환 전에 직접 읽어 본다(권한 비트가 아니라 실제 read).
    if [ "$DRYRUN" = 1 ]; then [ "${DEPLOY_FAKE_PEM:-ok}" = ok ]; return $?; fi
    docker compose -f "$COMPOSE_FILE" exec -T "$1" sh -c \
        'head -c1 /etc/booktimer/toss/client-cert.pem >/dev/null && head -c1 /etc/booktimer/toss/client-key.pem >/dev/null' \
        >/dev/null 2>&1
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

# ── 2.5) Caddyfile 반영 ──
# S3 sync가 caddy/Caddyfile을 갱신해도 **Caddy는 그걸 스스로 다시 읽지 않는다** — reload를 쳐야 한다.
# 안 치면 프록시 설정 변경이 배포 성공 뒤에도 조용히 무반영으로 남는다(무성 실패, T-167).
# reload는 무중단이다(실측: active health check가 붙은 상태에서 reload 2회 × 부하 400건, 실패 0건).
#
# 순서가 중요하다:
#  ① validate — 일회용 컨테이너에 **운영과 동일한 마운트**로 붙여 설정을 검사한다. 문법 오류나
#     마운트 경로 오타를 **라이브 Caddy를 건드리기 전에** 잡는다(여긴 유일한 입구라 잘못 뜨면 전면 장애).
#  ② up -d caddy — 서비스 **정의**(마운트 등)가 바뀐 경우에만 compose가 재생성한다. 내용만 바뀐
#     평시 배포엔 no-op이라 순단이 없다.
#  ③ reload — 내용 변경을 무중단 반영.
# 앱 전환 전에 두는 이유: 여기서 실패하면 앱을 아직 안 건드린 상태로 멈추는 게 안전하다.
if [ "$DRYRUN" = 1 ]; then
    echo "docker run --rm -v ./caddy:/etc/caddy:ro caddy:2-alpine caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile"
else
    docker run --rm -v "$(pwd)/caddy:/etc/caddy:ro" caddy:2-alpine \
        caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
fi
echo "[deploy] Caddyfile 검증 통과"
dc up -d caddy
# ③ reload — up -d가 caddy 기동을 보장하므로 "실행중인가" 가드는 필요 없다. 대신 **재시도**한다:
#    방금 재생성된 경우 admin API(:2019)가 아직 안 떠 첫 시도가 실패할 수 있고, 그대로 두면
#    set -e가 배포를 죽인다. 이미 떠 있던 평시 배포는 첫 시도에 성공한다.
echo "[deploy] Caddyfile 반영 (caddy reload)"
reloaded=0
for _ in 1 2 3 4 5 6 7 8 9 10; do
    if dc exec -T caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile; then reloaded=1; break; fi
    sleep 1
done
[ "$reloaded" = 1 ] || { echo "[deploy] caddy reload 실패 — 앱은 건드리지 않고 중단합니다" >&2; exit 1; }

# ── 3) 새 컨테이너 기동 (옛 컨테이너는 계속 트래픽을 받는 중) ──
dc up -d "$NEW"

# ── 4) 헬스 대기 ──
ok=0
for _ in $(seq 1 "$HEALTH_RETRIES"); do
    if health_ok "$NEW"; then ok=1; break; fi
    sleep "$HEALTH_SLEEP"
done

# ── 4.5) 토스 mTLS 인증서 가독성 게이트 ──
# 헬스 통과 ≠ 토스 로그인 가능. 위 pem_readable 주석대로 SSL 번들이 지연 생성이라 인증서를 못 읽어도
# 헬스는 초록이다. 여기서 걸러 **아래 5)의 롤백 경로로 합류**시킨다 — 옛 컨테이너는 안 건드린다.
if [ "$ok" = 1 ] && ! pem_readable "$NEW"; then
    echo "[deploy] $NEW 가 토스 mTLS 인증서를 못 읽습니다 — 컨테이너 uid(Dockerfile USER)와" >&2
    echo "         PEM 소유자(render-env.sh APP_UID)가 어긋났을 수 있습니다." >&2
    ok=0
fi

# ── 5) 실패: 새 것만 버리고 옛 것은 그대로 살려둔다 ──
if [ "$ok" != 1 ]; then
    echo "[deploy] $NEW 기동 검증 실패 — $OLD 를 유지한 채 롤백합니다" >&2
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
