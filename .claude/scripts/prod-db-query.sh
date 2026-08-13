#!/usr/bin/env bash
# prod-db-query.sh — 운영 DB 읽기 전용 조회기 (SSM Send-Command → EC2 mysql 컨테이너)
#
# 목적: 운영 DB를 한 번 들여다보려고 매번 손으로 조립하던 절차(SSM 파라미터 JSON 생성 →
#   base64 SQL 임베드 → send-command → 폴링)를 도구화한다. 손 조립 때 밟은 함정 3종을
#   내장으로 회피한다:
#     ① AWS CLI는 Windows 바이너리라 MSYS 경로(file:///c/...)를 못 읽는다 → cygpath -m (T-155)
#     ② 이 머신엔 jq가 없다 → params.json은 파이썬으로 생성
#     ③ SQL을 인라인으로 넘기면 따옴표 지옥 → base64로 임베드해 원격에서 디코드
#
# 사용:
#   bash .claude/scripts/prod-db-query.sh <sql파일>              # 실행
#   bash .claude/scripts/prod-db-query.sh <sql파일> --dry-run    # 원격 명령·params.json만 출력
#
# 전제:
#   - AWS 프로필 `booktimer`(환경변수 AWS_PROFILE_NAME 로 변경) 로그인 상태.
#     만료되면 `aws login --profile booktimer` 재인증.
#   - 그 프로필에 ssm:SendCommand / ssm:GetCommandInvocation 권한.
#   - 대상 인스턴스 i-07a649585c25707a3 (환경변수 BOOKTIMER_EC2_INSTANCE 로 변경).
#   - python (params.json 생성 + 읽기 전용 가드), base64, cygpath(Git Bash).
#
# 안전: 읽기 전용 가드가 SELECT / SHOW / EXPLAIN / DESCRIBE 로 시작하는 문장만 통과시킨다.
#   ponytail: 완전한 SQL 파서가 아니라 "주석·문자열 제거 후 세미콜론 분할 → 각 문장의 첫
#   단어" 검사다. 첫 단어가 읽기 계열이면 통과하므로 `SELECT ... INTO OUTFILE` 같은
#   부작용 있는 SELECT 변종은 못 막는다. 진짜 방어가 필요하면 읽기 전용 DB 계정으로 올릴 것.
#
# 종료코드: 0 정상, 1 가드 위반·인증 만료·원격 실패, 2 사용법 오류.
set -u

PROFILE="${AWS_PROFILE_NAME:-booktimer}"
INSTANCE="${BOOKTIMER_EC2_INSTANCE:-i-07a649585c25707a3}"

SQL_FILE=""
DRYRUN=0
for a in "$@"; do
  case "$a" in
    --dry-run) DRYRUN=1 ;;
    -h|--help) sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "알 수 없는 옵션: $a" >&2; exit 2 ;;
    *)  [ -n "$SQL_FILE" ] && { echo "SQL 파일은 하나만 지정합니다." >&2; exit 2; }
        SQL_FILE="$a" ;;
  esac
done

[ -n "$SQL_FILE" ] || { echo "사용법: bash .claude/scripts/prod-db-query.sh <sql파일> [--dry-run]" >&2; exit 2; }
[ -f "$SQL_FILE" ] || { echo "SQL 파일을 찾을 수 없습니다: $SQL_FILE" >&2; exit 2; }

PY="$(command -v python3 2>/dev/null || command -v python 2>/dev/null || true)"
[ -n "$PY" ] || { echo "python 을 찾을 수 없습니다 (params.json 생성·가드에 필요)." >&2; exit 2; }

TMPDIR_RUN="$(mktemp -d)"
trap 'rm -rf "$TMPDIR_RUN"' EXIT

# ── 1. 읽기 전용 가드 ──────────────────────────────────────────────────────────
# 파이썬이 위반 토큰만 ASCII로 뱉고, 한국어 메시지는 셸이 찍는다
# (Windows 콘솔 cp949에서 파이썬이 한글 stderr를 쓰다 죽는 T-136 회피).
cat > "$TMPDIR_RUN/guard.py" <<'PYEOF'
import re, sys

ALLOWED = ("SELECT", "SHOW", "EXPLAIN", "DESCRIBE", "DESC", "WITH")

src = open(sys.argv[1], encoding="utf-8").read()
src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)        # /* 블록 주석 */
src = re.sub(r"--[^\n]*", " ", src)                      # -- 줄 주석
src = re.sub(r"#[^\n]*", " ", src)                       # # 줄 주석
src = re.sub(r"'(?:[^'\\]|\\.)*'", "''", src)            # 문자열 리터럴
src = re.sub(r'"(?:[^"\\]|\\.)*"', '""', src)

bad = []
for stmt in src.split(";"):
    stmt = stmt.strip().lstrip("(")
    if not stmt:
        continue
    first = re.split(r"[\s(]+", stmt)[0].upper()
    if first not in ALLOWED:
        bad.append(re.sub(r"[^A-Za-z0-9_]", "?", first)[:20] or "?")
if bad:
    sys.stdout.write(",".join(bad))
    sys.exit(1)
PYEOF

if ! BAD="$(PYTHONUTF8=1 "$PY" "$TMPDIR_RUN/guard.py" "$SQL_FILE" 2>"$TMPDIR_RUN/guard.err")"; then
  if [ -n "$BAD" ]; then
    echo "❌ 읽기 전용 위반 — 허용되지 않는 문장으로 시작합니다: $BAD" >&2
    echo "   허용: SELECT / SHOW / EXPLAIN / DESCRIBE / WITH 로 시작하는 문장만." >&2
  else
    echo "❌ SQL 파일을 검사하지 못했습니다:" >&2
    cat "$TMPDIR_RUN/guard.err" >&2
  fi
  exit 1
fi

# ── 2. 원격 명령 조립 (SQL은 base64 임베드) ───────────────────────────────────
B64="$(base64 -w0 < "$SQL_FILE")"
REMOTE_CMD="cd /opt/booktimer && PW=\$(grep '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-) && echo ${B64} | base64 -d > /tmp/q.sql && docker compose -f compose.prod.yaml exec -T mysql mysql -uroot -p\"\$PW\" booktimer < /tmp/q.sql; rc=\$?; rm -f /tmp/q.sql; exit \$rc"

# ── 3. params.json (jq 없음 → 파이썬) + Windows 경로 (T-155) ──────────────────
PARAMS="$TMPDIR_RUN/params.json"
REMOTE_CMD="$REMOTE_CMD" "$PY" -c \
  'import json,os,sys; sys.stdout.write(json.dumps({"commands":[os.environ["REMOTE_CMD"]]}))' \
  > "$PARAMS" || { echo "params.json 생성 실패" >&2; exit 1; }
# AWS CLI는 Windows 바이너리다 — MSYS 경로(/c/...)를 주면 Unable to load paramfile 로 죽는다.
PARAMS_WIN="$(cygpath -m "$PARAMS" 2>/dev/null || printf '%s' "$PARAMS")"

if [ "$DRYRUN" = "1" ]; then
  echo "── 원격 명령 ──"
  echo "$REMOTE_CMD"
  echo
  echo "── params.json ($PARAMS_WIN) ──"
  cat "$PARAMS"; echo
  echo
  echo "── aws 명령 (dry-run: 실행 안 함) ──"
  echo "aws ssm send-command --profile $PROFILE --instance-ids $INSTANCE --document-name AWS-RunShellScript --parameters \"file://$PARAMS_WIN\""
  exit 0
fi

# ── 4. 인증 확인 ──────────────────────────────────────────────────────────────
if ! aws sts get-caller-identity --profile "$PROFILE" >/dev/null 2>&1; then
  echo "❌ AWS 인증이 만료됐거나 프로필 '$PROFILE' 을 쓸 수 없습니다." >&2
  echo "   aws login --profile $PROFILE 로 재인증한 뒤 다시 실행하세요." >&2
  exit 1
fi

# ── 5. 전송 + 폴링 ────────────────────────────────────────────────────────────
CMD_ID="$(aws ssm send-command \
  --profile "$PROFILE" \
  --instance-ids "$INSTANCE" \
  --document-name AWS-RunShellScript \
  --parameters "file://$PARAMS_WIN" \
  --query 'Command.CommandId' --output text)" || { echo "send-command 실패" >&2; exit 1; }
[ -n "$CMD_ID" ] && [ "$CMD_ID" != "None" ] || { echo "CommandId 를 받지 못했습니다." >&2; exit 1; }
echo "[prod-db-query] CommandId=$CMD_ID — 결과 대기 중..." >&2

get_field() {  # $1=필드명
  aws ssm get-command-invocation --profile "$PROFILE" \
    --command-id "$CMD_ID" --instance-id "$INSTANCE" \
    --query "$1" --output text 2>/dev/null
}

STATUS="Pending"
for _ in $(seq 1 20); do
  sleep 3
  STATUS="$(get_field Status)"
  case "$STATUS" in
    Success) break ;;
    Failed|Cancelled|TimedOut) break ;;
    *) : ;;   # Pending / InProgress / Delayed / 조회 실패(빈 값) → 계속
  esac
done

if [ "$STATUS" = "Success" ]; then
  get_field StandardOutputContent
  exit 0
fi

echo "❌ 원격 실행 실패 (Status=${STATUS:-조회불가}, 60초 폴링 종료)" >&2
get_field StandardErrorContent >&2
exit 1
