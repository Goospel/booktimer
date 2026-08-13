#!/usr/bin/env bash
# Smoke test for prod-db-query.sh (BookTimer, T-155 도구화).
# 실 AWS를 부르지 않는다 — 전부 --dry-run + PATH 스텁으로 계측한다.
#
# 이 테스트가 겨누는 distinct 실패:
#   1) 읽기 전용 가드가 뚫린다 (INSERT/UPDATE/DROP/TRUNCATE 가 운영 DB에 나간다).
#   2) 가드가 주석에 속는다 (선행 주석이 있으면 첫 단어 검사가 빗나감).
#   3) 가드가 첫 문장만 보고 통과시킨다 (`SELECT 1; DROP TABLE x;`).
#   4) 가드가 문자열 리터럴에 오탐한다 (`SELECT 'DROP TABLE'` 이 거부되면 못 쓴다).
#   5) SQL 이 base64 로 임베드되지 않는다 (따옴표 지옥 → 원격에서 깨짐).
#   6) --parameters 경로가 MSYS 형식(/c/...)이라 AWS CLI 가 못 읽는다 (T-155 본체).
#   7) --dry-run 이 실제로 aws 를 부른다 (검토용인데 운영을 건드림).

S=".claude/scripts/prod-db-query.sh"
FAILED=0
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

sql() {  # $1=파일명 $2=내용 → 경로 echo
    printf '%s' "$2" > "$TMP/$1"
    printf '%s' "$TMP/$1"
}
run() {  # $@ = 인자 ; echoes "<exit>\n<output>"
    local out rc
    out="$(bash "$S" "$@" 2>&1)"; rc=$?
    printf '%s\n' "$rc"
    printf '%s' "$out"
}
assert_exit() {  # $1=label $2=got $3=want
    if [ "$2" = "$3" ]; then echo "PASS: $1 (exit $2)"; else echo "FAIL: $1 exit=$2 want=$3"; FAILED=1; fi
}
assert_has() {   # $1=label $2=haystack $3=needle
    if printf '%s' "$2" | grep -qF -- "$3"; then echo "PASS: $1"; else echo "FAIL: $1 — missing '$3'"; FAILED=1; fi
}

# ── 가드: 거부해야 하는 것 ───────────────────────────────────────────────────
for case in "insert:INSERT INTO users(id) VALUES(1);" \
            "update:UPDATE users SET name='x';" \
            "drop:DROP TABLE users;" \
            "truncate:TRUNCATE TABLE users;" \
            "set:SET autocommit=0;"; do
    name="${case%%:*}"; body="${case#*:}"
    f="$(sql "$name.sql" "$body")"
    r="$(run "$f" --dry-run)"; rc="${r%%$'\n'*}"
    assert_exit "가드 거부 — $name" "$rc" "1"
done

# 주석 뒤에 숨은 쓰기 문장 (선행 주석에 속으면 안 됨)
f="$(sql "commented-write.sql" $'-- 사용자 이름 정리\n/* 여러 줄\n   주석 */\nUPDATE users SET name=NULL;')"
r="$(run "$f" --dry-run)"; rc="${r%%$'\n'*}"
assert_exit "가드 거부 — 주석 뒤 UPDATE" "$rc" "1"

# 첫 문장만 보고 통과하면 안 됨
f="$(sql "mixed.sql" "SELECT 1; DROP TABLE users;")"
r="$(run "$f" --dry-run)"; rc="${r%%$'\n'*}"
assert_exit "가드 거부 — SELECT 뒤 DROP" "$rc" "1"

# ── 가드: 통과해야 하는 것 ───────────────────────────────────────────────────
f="$(sql "select.sql" "SELECT COUNT(*) FROM users;")"
r="$(run "$f" --dry-run)"; rc="${r%%$'\n'*}"
assert_exit "가드 통과 — SELECT" "$rc" "0"

f="$(sql "commented-read.sql" $'-- 최근 가입자\n# 해시 주석\n/* 블록 주석 */\nSHOW TABLES;\nDESCRIBE users;\nEXPLAIN SELECT 1;')"
r="$(run "$f" --dry-run)"; rc="${r%%$'\n'*}"
assert_exit "가드 통과 — 주석 섞인 SHOW/DESCRIBE/EXPLAIN" "$rc" "0"

# 문자열 리터럴 안의 위험 단어는 오탐 금지 (세미콜론까지 품은 리터럴 — 문장 분할이 속으면 안 됨)
f="$(sql "literal.sql" "SELECT * FROM logs WHERE msg = 'x; DROP TABLE users';")"
r="$(run "$f" --dry-run)"; rc="${r%%$'\n'*}"
assert_exit "가드 통과 — 문자열 리터럴 속 DROP" "$rc" "0"

# ── --dry-run 출력: base64 임베드 + Windows 경로 ─────────────────────────────
BODY="SELECT COUNT(*) FROM users;"
f="$(sql "dry.sql" "$BODY")"
B64="$(printf '%s' "$BODY" | base64 -w0)"
r="$(run "$f" --dry-run)"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "dry-run 종료코드" "$rc" "0"
assert_has  "dry-run 에 base64 임베드" "$out" "$B64"
assert_has  "dry-run 에 base64 -d 디코드" "$out" "base64 -d"
assert_has  "dry-run 에 Windows 경로(file://C:/)" "$out" "file://C:/"
assert_has  "dry-run 에 params.json 내용" "$out" '{"commands":'

# ── --dry-run 은 aws 를 절대 부르지 않는다 ───────────────────────────────────
STUB="$TMP/stub"; mkdir -p "$STUB"
cat > "$STUB/aws" <<EOF
#!/usr/bin/env bash
echo called >> "$TMP/aws-called"
exit 0
EOF
chmod +x "$STUB/aws"
PATH="$STUB:$PATH" bash "$S" "$f" --dry-run >/dev/null 2>&1
if [ -e "$TMP/aws-called" ]; then
    echo "FAIL: dry-run 이 aws 를 호출했다"; FAILED=1
else
    echo "PASS: dry-run 은 aws 를 호출하지 않는다"
fi

# ── 사용법 오류 ──────────────────────────────────────────────────────────────
r="$(run)"; rc="${r%%$'\n'*}"
assert_exit "인자 없음 → 사용법 오류" "$rc" "2"
r="$(run "$TMP/없는파일.sql")"; rc="${r%%$'\n'*}"
assert_exit "없는 파일 → 오류" "$rc" "2"

echo
if [ "$FAILED" = "0" ]; then echo "ALL PASS"; exit 0; else echo "SOME FAILED"; exit 1; fi
