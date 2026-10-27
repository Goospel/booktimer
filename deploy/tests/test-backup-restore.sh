#!/usr/bin/env bash
# 일일 백업 파일을 **통째로** 빈 MySQL에 적재하는 전체 재해 복구 리허설.
#
# 지키는 불변식: 통째로 적재하면 세션 표 2개가 booktimer에 생기고 facefit에는 생기지 않는다.
#
# 배경: 백업은 두 덤프를 한 스트림에 잇는다 — 본 덤프(--databases booktimer facefit, 세션 데이터 제외)
# 뒤에 세션 표 구조만(--no-data booktimer SPRING_SESSION …). 뒤 덤프는 단일 DB 모드라 USE 줄을
# 내지 않아서, 본 덤프의 마지막 `USE facefit` 아래에서 돌았다 — 세션 표가 facefit에 생기고
# booktimer엔 없었다(2026-10-27 행 단위 복구 준비 중 실측). 전체 복구였다면 Flyway는 V2를 적용됨으로
# 보고 앱은 세션 표 없이 못 뜨며, facefit에 같은 이름 표가 있으면 DROP된다.
#
# 운영 스크립트를 그대로 돌린다 — docker·aws만 PATH 스텁으로 갈아, 덤프는 테스트 MySQL에서 뜨고
# 업로드는 로컬 파일로 받는다. 사본에서 바꾸는 것은 임시 경로(/var/tmp — Git Bash엔 없다) 하나뿐이다.
#
# 실행: 레포 루트에서 `bash deploy/tests/test-backup-restore.sh` (Docker 필요 — 운영과 같은 mysql:8.4)

S="deploy/backup-mysql.sh"
V2="src/main/resources/db/migration/V2__spring_session.sql"
IMG="mysql:8.4"
C="bt-backuptest-mysql"
PW="backup-test-pw"
FAILED=0

WORK="$(mktemp -d)"
cleanup() { docker rm -f "$C" >/dev/null 2>&1; rm -rf "$WORK"; }
trap cleanup EXIT

[ -f "$S" ] || { echo "FAIL: $S 없음 — 레포 루트에서 실행할 것"; exit 1; }

sql() { docker exec -i -e MYSQL_PWD="$PW" "$C" mysql -uroot -N -B "$@"; }

assert_eq() {      # $1=label $2=got $3=want
    if [ "$2" = "$3" ]; then echo "PASS: $1"; else echo "FAIL: $1 — got '$2' want '$3'"; FAILED=1; fi
}
assert_line() {    # $1=label $2=haystack $3=정확히 일치할 한 줄
    if printf '%s\n' "$2" | grep -qxF -- "$3"; then echo "PASS: $1"; else echo "FAIL: $1 — '$3' 없음"; FAILED=1; fi
}
assert_no_re() {   # $1=label $2=haystack $3=ERE
    if printf '%s\n' "$2" | grep -qE -- "$3"; then echo "FAIL: $1 — /$3/ 있음"; FAILED=1; else echo "PASS: $1"; fi
}

# ── 원본 MySQL: 운영 모양(booktimer + facefit 동거, 세션 표는 V2 그대로) ──
docker rm -f "$C" >/dev/null 2>&1
docker run -d --name "$C" -e MYSQL_ROOT_PASSWORD="$PW" "$IMG" >/dev/null || { echo "FAIL: $IMG 기동 실패"; exit 1; }
# 초기화 중 임시 서버는 TCP를 안 연다 — TCP 접속이 되면 최종 서버다.
for _ in $(seq 90); do
    docker exec -e MYSQL_PWD="$PW" "$C" mysql -uroot -h127.0.0.1 -e 'SELECT 1' >/dev/null 2>&1 && break
    sleep 2
done
docker exec -e MYSQL_PWD="$PW" "$C" mysql -uroot -h127.0.0.1 -e 'SELECT 1' >/dev/null 2>&1 \
    || { echo "FAIL: MySQL이 3분 안에 준비되지 않았다"; exit 1; }

sql <<SQL || { echo "FAIL: 원본 시드 실패"; exit 1; }
CREATE DATABASE booktimer;
CREATE DATABASE facefit;
USE booktimer;
$(cat "$V2")
INSERT INTO SPRING_SESSION VALUES ('p1', 's1', 0, 0, 1800, 0, NULL);
CREATE TABLE sentinel (id INT PRIMARY KEY);
INSERT INTO sentinel VALUES (1);
USE facefit;
CREATE TABLE sentinel (id INT PRIMARY KEY);
INSERT INTO sentinel VALUES (2);
SQL

# ── 운영 백업 스크립트 실행 ──
cp "$S" "$WORK/backup-mysql.sh"
sed -i "s#/var/tmp/#$WORK/#" "$WORK/backup-mysql.sh"
printf 'MYSQL_ROOT_PASSWORD=%s\n' "$PW" > "$WORK/.env"
mkdir "$WORK/bin"
REAL_DOCKER="$(command -v docker)"
cat > "$WORK/bin/docker" <<EOF
#!/usr/bin/env bash
# docker compose -f … exec -T mysql <명령…>  →  docker exec -i <테스트 컨테이너> <명령…>
while [ \$# -gt 0 ] && [ "\$1" != mysql ]; do shift; done; shift
exec "$REAL_DOCKER" exec -i "$C" "\$@"
EOF
cat > "$WORK/bin/aws" <<EOF
#!/usr/bin/env bash
case "\$1 \$2" in
    "s3 cp") cp "\$3" "$WORK/uploaded.sql.gz" ;;
    *) echo "aws 스텁: 예상 밖 호출 \$*" >&2; exit 1 ;;
esac
EOF
chmod +x "$WORK/bin/docker" "$WORK/bin/aws"

out="$(PATH="$WORK/bin:$PATH" BACKUP_BUCKET=restore-test bash "$WORK/backup-mysql.sh" 2>&1)" \
    || { echo "FAIL: 백업 스크립트 실패"; printf '%s\n' "$out"; exit 1; }
[ -s "$WORK/uploaded.sql.gz" ] || { echo "FAIL: 업로드된 덤프 없음"; printf '%s\n' "$out"; exit 1; }

# ── 빈 서버에 통째로 적재 ──
sql -e 'DROP DATABASE booktimer; DROP DATABASE facefit;' || { echo "FAIL: 원본 DB 비우기 실패"; exit 1; }
gunzip -c "$WORK/uploaded.sql.gz" | sql || { echo "FAIL: 덤프 적재 실패"; exit 1; }

# 대조군 — 적재 자체가 됐는지(안 됐으면 아래 「facefit엔 없다」가 공허하게 통과한다)
assert_eq "대조군: booktimer 도메인 행 복원" "$(sql -e 'SELECT id FROM booktimer.sentinel')" "1"
assert_eq "대조군: facefit 도메인 행 복원"   "$(sql -e 'SELECT id FROM facefit.sentinel')" "2"

sess="$(sql -e "SELECT CONCAT(table_schema, '.', table_name) FROM information_schema.tables WHERE table_name LIKE 'SPRING\\_SESSION%' ORDER BY 1")"
echo "세션 표 위치: $(printf '%s' "$sess" | tr '\n' ' ')"
assert_line  "booktimer.SPRING_SESSION 있음"            "$sess" "booktimer.SPRING_SESSION"
assert_line  "booktimer.SPRING_SESSION_ATTRIBUTES 있음" "$sess" "booktimer.SPRING_SESSION_ATTRIBUTES"
assert_no_re "facefit엔 세션 표 없음"                   "$sess" '^facefit\.'
assert_eq    "세션 데이터는 여전히 빠진다" "$(sql -e 'SELECT COUNT(*) FROM booktimer.SPRING_SESSION' 2>&1)" "0"

echo
if [ "$FAILED" = 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$FAILED"
