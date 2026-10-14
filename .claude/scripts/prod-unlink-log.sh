#!/usr/bin/env bash
# prod-unlink-log.sh — 운영 토스 연결 끊기 콜백 점검기(읽기 전용, SSM Send-Command → EC2)
#
# 보는 것: ① 운영 .env에 콜백 시크릿 줄이 있는지(개수·길이만 — 값은 찍지 않는다)
#          ② 떠 있는 컨테이너 ③ 최근 N분의 `toss-unlink` 로그 줄과 앱 기동 줄.
# 로그 꼴(TossUnlinkCallbackController): `toss-unlink outcome=PURGED|SEVERED|NOT_FOUND referrer=… keyShape=digits:N`
#   · `toss-unlink auth-failed scheme=…`(콘솔 Basic 값과 SSM이 어긋남) · `toss-unlink bad-request reason=…`
#   · 기동 때 `toss-unlink secret-missing`(서버에 시크릿이 안 들어감). 줄이 아예 없으면 토스가 부르지 않았다.
#
# 사용: bash .claude/scripts/prod-unlink-log.sh [분, 기본 90]
# 전제: AWS 프로필 booktimer 로그인(만료면 `aws login --profile booktimer`), python, cygpath(Git Bash).
# 같은 계열: prod-db-query.sh(DB 읽기). 2026-10-14 ② 시행 때 세션 스크래치에서 쓰던 것을 레포로 옮겼다.
set -u
MIN="${1:-90}"
case "$MIN" in ''|*[!0-9]*) echo "분은 숫자로 준다: $MIN" >&2; exit 2 ;; esac
PROFILE="${AWS_PROFILE_NAME:-booktimer}"
INSTANCE="${BOOKTIMER_EC2_INSTANCE:-i-07a649585c25707a3}"

aws sts get-caller-identity --profile "$PROFILE" >/dev/null 2>&1 || {
  echo "❌ AWS 인증이 만료됐거나 프로필 '$PROFILE'을 쓸 수 없다 — aws login --profile $PROFILE" >&2; exit 1; }

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
REMOTE="cd /opt/booktimer && echo env_lines=\$(grep -c '^BOOKTIMER_TOSS_UNLINK_BASIC_AUTH=.' .env) && echo env_len=\$(grep '^BOOKTIMER_TOSS_UNLINK_BASIC_AUTH=' .env | head -1 | cut -d= -f2- | tr -d '\\r\\n' | wc -c) && echo running=\$(docker compose -f compose.prod.yaml ps --services --status running | tr '\\n' ' ') && docker compose -f compose.prod.yaml logs --since ${MIN}m app-blue app-green 2>&1 | grep -E 'toss-unlink|Started [A-Za-z]+ in ' | cut -c1-260 | tail -40"
REMOTE="$REMOTE" python -c 'import json,os,sys; sys.stdout.write(json.dumps({"commands":[os.environ["REMOTE"]]}))' > "$TMP/params.json"
P="$(cygpath -m "$TMP/params.json" 2>/dev/null || printf '%s' "$TMP/params.json")"

ID="$(aws ssm send-command --profile "$PROFILE" --instance-ids "$INSTANCE" --document-name AWS-RunShellScript \
  --parameters "file://$P" --query Command.CommandId --output text)" || { echo "send-command 실패" >&2; exit 1; }
ST=""
for _ in $(seq 1 30); do
  sleep 3
  ST="$(aws ssm get-command-invocation --profile "$PROFILE" --command-id "$ID" --instance-id "$INSTANCE" --query Status --output text 2>/dev/null)"
  case "$ST" in Success|Failed|Cancelled|TimedOut) break ;; esac
done
echo "status=$ST"
aws ssm get-command-invocation --profile "$PROFILE" --command-id "$ID" --instance-id "$INSTANCE" --query StandardOutputContent --output text
aws ssm get-command-invocation --profile "$PROFILE" --command-id "$ID" --instance-id "$INSTANCE" --query StandardErrorContent --output text | head -5
[ "$ST" = Success ]
