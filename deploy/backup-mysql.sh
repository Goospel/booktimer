#!/usr/bin/env bash
# 매일 MySQL 덤프 → S3. RDS 자동 백업을 대체한다(EC2 자체 MySQL은 백업이 없다).
# cron: 매일 18:00 UTC(=03:00 KST). 보존은 S3 lifecycle 규칙(7일)에 맡긴다.
set -euo pipefail

cd "$(dirname "$0")"

REGION="${AWS_REGION:-ap-northeast-2}"
# 배포 자산과 같은 ops 버킷을 쓴다. cron 환경에는 어떤 변수도 실려 오지 않으므로
# 계정 ID에서 유도한다 — 예전엔 BACKUP_BUCKET 이 필수라 cron이 매번 이 줄에서 즉시
# 종료했고, 그 탓에 백업이 엿새 동안 0건이었다(T-138). 명시 지정은 그대로 우선한다.
BUCKET="${BACKUP_BUCKET:-booktimer-ops-$(aws sts get-caller-identity --query Account --output text --region "$REGION")}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="booktimer-${STAMP}.sql.gz"
TMP="/tmp/${FILE}"
trap 'rm -f "$TMP"' EXIT

# 테스트용 — 실제 덤프·업로드 없이 확정된 대상만 찍고 끝낸다.
if [ "${BACKUP_DRYRUN:-0}" = 1 ]; then
    echo "[backup][dryrun] s3://${BUCKET}/mysql/${FILE}"
    exit 0
fi

# .env 에서 MYSQL_ROOT_PASSWORD 만 읽는다(전체 source는 다른 변수까지 셸에 퍼뜨린다).
MYSQL_ROOT_PASSWORD="$(grep -m1 '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)"
[ -n "$MYSQL_ROOT_PASSWORD" ] || { echo "[backup] .env에 MYSQL_ROOT_PASSWORD가 없습니다" >&2; exit 1; }

dump() { docker compose -f compose.prod.yaml exec -T mysql \
    mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" "$@"; }

# --single-transaction: InnoDB를 잠그지 않고 일관 스냅샷(서비스 중단 없음)
# flyway_schema_history 도 booktimer DB 안에 있으므로 --databases 로 통째로 뜨면 함께 포함된다.
#
# 세션 테이블은 **데이터만** 뺀다(백업의 ~95%가 이것이었다 — 익명 세션 17,648/17,658행,
# 디스크 97MB인데 진짜 도메인 데이터는 ~0.5MB). 복원해도 의미 없는 휘발성 데이터라
# 크기만 부풀려 "진짜 데이터가 얼마나 변했나"를 가린다.
# 단, --ignore-table 은 구조까지 통째로 빼는데 세션 스키마는 Flyway가 관리하고
# flyway_schema_history(백업에 포함됨)엔 "이미 적용됨"으로 남아 있다 — 테이블 없이 history만
# 복원되면 마이그레이션이 재실행되지 않아 앱이 세션 테이블 부재로 기동 실패한다.
# 그래서 뒤에 --no-data 덤프를 같은 스트림에 이어붙여 빈 테이블 구조는 남긴다
# (본 덤프의 USE booktimer; 가 먼저 나오므로 뒤 덤프도 같은 DB 컨텍스트에서 실행된다).
{
    dump --single-transaction --routines --triggers --databases booktimer \
        --ignore-table=booktimer.SPRING_SESSION \
        --ignore-table=booktimer.SPRING_SESSION_ATTRIBUTES
    dump --no-data booktimer SPRING_SESSION SPRING_SESSION_ATTRIBUTES
} | gzip > "$TMP"

# 덤프가 비었는데 성공으로 넘어가면 "백업이 있다"는 착각만 남는다.
if [ ! -s "$TMP" ] || [ "$(stat -c%s "$TMP")" -lt 1024 ]; then
    echo "[backup] 덤프가 비정상적으로 작습니다 — 업로드 중단" >&2
    exit 1
fi

aws s3 cp "$TMP" "s3://${BUCKET}/mysql/${FILE}" --region "$REGION"
echo "[backup] s3://${BUCKET}/mysql/${FILE} ($(du -h "$TMP" | cut -f1))"
