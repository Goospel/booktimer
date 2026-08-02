#!/usr/bin/env bash
# Smoke test for backup-mysql.sh + the cron entry that actually runs it.
#
# 배경: 이 셋이 **동시에** 깨져 있어서 일일 백업이 엿새 동안 S3에 0건을 남겼다.
# 게다가 1)이 로그 파일 생성 자체를 막아 실패 흔적조차 안 남았다 — 그래서 무성 실패였다.
# 각 단언은 그중 하나의 실제 실패에 대응한다.
#
#   1) cron 잡이 root로 돈다.
#      ec2-user면: /var/log/ 에 로그를 못 만들어 리다이렉트에서 죽고(스크립트 실행조차 안 됨),
#      root:600 인 .env 도 못 읽어 DB 비밀번호 조회가 실패한다. 한 단어가 두 실패를 만들었다.
#   2) BACKUP_BUCKET 이 없어도 계정 ID에서 버킷을 유도한다.
#      실패 시: 스크립트가 BUCKET 대입행에서 즉시 종료 → 백업이 영영 안 만들어진다.
#   3) BACKUP_BUCKET 을 명시하면 그쪽을 쓴다(오버라이드 보존 — 복구 리허설용 임시 버킷 등).
#
# Pure dry-run: BACKUP_DRYRUN=1 + PATH 앞의 aws 스텁으로 실제 AWS/docker 호출을 전부 우회한다.

S="deploy/backup-mysql.sh"
B="deploy/bootstrap-ec2.sh"
FAILED=0

STUB="$(mktemp -d)"
trap 'rm -rf "$STUB"' EXIT
cat > "$STUB/aws" <<'STUBEOF'
#!/usr/bin/env bash
case "$*" in
    *get-caller-identity*) echo "111122223333" ;;
    *) echo "STUB-AWS $*" ;;
esac
STUBEOF
chmod +x "$STUB/aws"

run() {  # $1=BACKUP_BUCKET(빈문자열이면 미설정) ; echoes "<exit>\n<output>"
    local out rc
    if [ -n "$1" ]; then
        out="$(PATH="$STUB:$PATH" BACKUP_DRYRUN=1 BACKUP_BUCKET="$1" bash "$S" 2>&1)"; rc=$?
    else
        out="$(PATH="$STUB:$PATH" BACKUP_DRYRUN=1 bash "$S" 2>&1)"; rc=$?
    fi
    printf '%s\n' "$rc"
    printf '%s' "$out"
}

assert_exit() {  # $1=label $2=got $3=want
    if [ "$2" = "$3" ]; then echo "PASS: $1 (exit $2)"; else echo "FAIL: $1 exit=$2 want=$3"; FAILED=1; fi
}
assert_has() {   # $1=label $2=haystack $3=needle
    if printf '%s' "$2" | grep -qF -- "$3"; then echo "PASS: $1"; else echo "FAIL: $1 — missing '$3'"; FAILED=1; fi
}
assert_not() {   # $1=label $2=haystack $3=needle
    if printf '%s' "$2" | grep -qF -- "$3"; then echo "FAIL: $1 — unexpected '$3'"; FAILED=1; else echo "PASS: $1"; fi
}

# ── Case 1: cron 잡이 root로 돈다 (정적 검사 — 환경 권한은 단위테스트로 재현 불가) ──
cron_line="$(grep -E '^[0-9*]' "$B" | grep 'backup-mysql.sh' || true)"
assert_has "cron entry exists"        "$cron_line" "backup-mysql.sh"
assert_has "  runs as root"           "$cron_line" " root "
assert_not "  NOT as ec2-user"        "$cron_line" "ec2-user"

# ── Case 2: BACKUP_BUCKET 미설정 → 계정 ID에서 유도, 죽지 않는다 ──
r="$(run "")"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "no BACKUP_BUCKET → derives default" "$rc" "0"
assert_has  "  targets booktimer-ops-<account>"  "$out" "s3://booktimer-ops-111122223333/mysql/"
assert_not  "  does not demand the env var"      "$out" "BACKUP_BUCKET 환경변수가 필요"

# ── Case 3: BACKUP_BUCKET 명시 → 오버라이드 보존 ──
r="$(run "my-restore-rehearsal")"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "explicit BACKUP_BUCKET honored" "$rc" "0"
assert_has  "  targets the given bucket"     "$out" "s3://my-restore-rehearsal/mysql/"

echo
if [ "$FAILED" = 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$FAILED"
