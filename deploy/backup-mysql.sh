#!/usr/bin/env bash
# 매일 MySQL 덤프 → S3. RDS 자동 백업을 대체한다(EC2 자체 MySQL은 백업이 없다).
# cron: 매일 18:00 UTC(=03:00 KST). 보존은 S3 lifecycle 규칙(7일)에 맡긴다.
set -euo pipefail

cd "$(dirname "$0")"

BUCKET="${BACKUP_BUCKET:?BACKUP_BUCKET 환경변수가 필요합니다 (예: booktimer-backup)}"
REGION="${AWS_REGION:-ap-northeast-2}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="booktimer-${STAMP}.sql.gz"
TMP="/tmp/${FILE}"
trap 'rm -f "$TMP"' EXIT

# .env 에서 MYSQL_ROOT_PASSWORD 만 읽는다(전체 source는 다른 변수까지 셸에 퍼뜨린다).
MYSQL_ROOT_PASSWORD="$(grep -m1 '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)"
[ -n "$MYSQL_ROOT_PASSWORD" ] || { echo "[backup] .env에 MYSQL_ROOT_PASSWORD가 없습니다" >&2; exit 1; }

# --single-transaction: InnoDB를 잠그지 않고 일관 스냅샷(서비스 중단 없음)
# flyway_schema_history 도 booktimer DB 안에 있으므로 --databases 로 통째로 뜨면 함께 포함된다.
docker compose -f compose.prod.yaml exec -T mysql \
    mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
        --single-transaction --routines --triggers --databases booktimer \
    | gzip > "$TMP"

# 덤프가 비었는데 성공으로 넘어가면 "백업이 있다"는 착각만 남는다.
if [ ! -s "$TMP" ] || [ "$(stat -c%s "$TMP")" -lt 1024 ]; then
    echo "[backup] 덤프가 비정상적으로 작습니다 — 업로드 중단" >&2
    exit 1
fi

aws s3 cp "$TMP" "s3://${BUCKET}/mysql/${FILE}" --region "$REGION"
echo "[backup] s3://${BUCKET}/mysql/${FILE} ($(du -h "$TMP" | cut -f1))"
