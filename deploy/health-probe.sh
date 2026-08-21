#!/usr/bin/env bash
# 운영 헬스 감시기 — 살아 있으면 조용히 끝나고(exit 0), 죽었으면 시끄럽게 죽는다(exit 1).
#
# 어디서 도는가: `.github/workflows/uptime.yml`(스케줄)이 부른다. 실패하면 Actions 런이 빨개지고
# GitHub이 레포 소유자에게 메일을 보낸다 — 그게 알림 채널이다(서버에 아무것도 올리지 않는다).
#
# 왜 이 방식인가: 운영은 EC2 t3.small 1대(2GB)이고 메모리 예산이 이미 평시 1.23GB·배포 순간
# 1.78GB로 다 팔려 있다(compose.prod.yaml). Prometheus·Grafana를 그 위에 얹으면 배포 순간
# OOM killer가 MySQL을 잡는다 — bootstrap-ec2.sh가 스왑을 만든 이유가 바로 그 시나리오다.
# 그래서 「죽었을 때 아는 것」만 **서버 밖에서** 공짜로 챙긴다. 대시보드는 그다음 문제다.
#
# ⚠️ 성공 판정을 HTTP 코드만으로 하지 않는다. 도메인이 남의 곳을 가리키거나 앞단이 파킹·에러
#    페이지를 200으로 돌려주면 코드만 보는 감시는 장애를 통과시킨다 — 이 레포가 배포 게이트에서
#    이미 배운 원칙이다(T-150: 성공은 exit code가 아니라 응답 내용으로 판정한다).
#
# ⚠️ 재시도가 있는 이유는 **오탐이 감시를 죽이기 때문**이다. 멀쩡한데 메일이 오면 사람은 두 번째
#    메일부터 안 읽는다. 네트워크 blip 한 번은 장애가 아니므로, 연속 PROBE_TRIES번 실패해야 쏜다.
#
# 사용: BASE_URL=https://booktimer.app bash deploy/health-probe.sh
# 테스트: bash deploy/tests/test-health-probe.sh (curl을 PATH 스텁으로 갈아 오프라인에서 돈다)

BASE_URL="${BASE_URL:-https://booktimer.app}"
PROBE_TRIES="${PROBE_TRIES:-3}"
PROBE_DELAY="${PROBE_DELAY:-20}"
URL="${BASE_URL}/actuator/health"

for i in $(seq 1 "$PROBE_TRIES"); do
    # 본문과 HTTP 코드를 한 번에 받는다(마지막 줄 = 코드). curl이 아예 실패하면 출력이 비어 둘 다 없다.
    resp=$(curl -s --max-time 10 -w '\n%{http_code}' "$URL" 2>/dev/null)
    code="${resp##*$'\n'}"
    body="${resp%$'\n'*}"
    [ "$resp" = "$code" ] && body=""   # 개행이 없었다 = 본문 없음(연결 실패)

    if [ "$code" = "200" ] && printf '%s' "$body" | grep -qF '"status":"UP"'; then
        echo "OK: $URL 정상 (${i}번째 시도, http=200)"
        exit 0
    fi

    echo "시도 ${i}/${PROBE_TRIES} 실패 — http=${code:-없음(연결 불가)} body=${body:-없음}"
    if [ "$i" -lt "$PROBE_TRIES" ]; then
        sleep "$PROBE_DELAY"
    fi
done

echo "::error::운영 헬스체크 실패 — ${URL} 가 ${PROBE_TRIES}회 연속 응답하지 않았다 (마지막 http=${code:-없음})"
exit 1
