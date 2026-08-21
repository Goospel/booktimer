#!/usr/bin/env bash
# SSM Parameter Store → .env 생성 (compose가 env_file로 읽는다).
#
# 시크릿을 EC2 디스크에 영구 보관하지 않기 위해 배포 때마다 다시 만든다.
# 파라미터 경로(/booktimer/*)는 ECS 시절 그대로 재사용하므로 시크릿 이관이 필요 없다.
#
# ⚠️ SSM 파라미터 이름과 앱 환경변수 이름이 일치하지 않는 것들이 있다(ECS task-definition이
#    secrets[].name 으로 바꿔 주입했었다). 그 매핑이 아래 SECRET_MAP 이며, task-definition.json 이
#    삭제된 뒤에는 **이 파일이 매핑의 유일한 출처**다.
set -euo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
OUT="${1:-.env}"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text --region "$REGION")"

# SSM 파라미터 이름 → 앱 환경변수 이름
declare -A SECRET_MAP=(
  [SPRING_DATASOURCE_URL]=SPRING_DATASOURCE_URL
  [SPRING_DATASOURCE_USERNAME]=SPRING_DATASOURCE_USERNAME
  [SPRING_DATASOURCE_PASSWORD]=SPRING_DATASOURCE_PASSWORD
  [GOOGLE_CLIENT_ID]=GOOGLE_CLIENT_ID
  [GOOGLE_CLIENT_SECRET]=GOOGLE_CLIENT_SECRET
  [ALADIN_TTB_KEY]=BOOKTIMER_ALADIN_TTB_KEY
  [COUPANG_SEARCH_URL_TEMPLATE]=BOOKTIMER_COUPANG_SEARCH_URL_TEMPLATE
  [YES24_TRACKING_CODE]=BOOKTIMER_YES24_TRACKING_CODE
  [YES24_SEARCH_URL_TEMPLATE]=BOOKTIMER_YES24_SEARCH_URL_TEMPLATE
  [KYOBO_TRACKING_CODE]=BOOKTIMER_KYOBO_TRACKING_CODE
  [KYOBO_SEARCH_URL_TEMPLATE]=BOOKTIMER_KYOBO_SEARCH_URL_TEMPLATE
  [KYOBO_MOBILE_SEARCH_URL_TEMPLATE]=BOOKTIMER_KYOBO_MOBILE_SEARCH_URL_TEMPLATE
  [MINIAPP_ALLOWED_ORIGINS]=BOOKTIMER_MINIAPP_ALLOWED_ORIGINS
  [TOSS_MESSENGER_ENABLED]=BOOKTIMER_TOSS_MESSENGER_ENABLED
  [TOSS_FINISH_TEMPLATE_CODE]=BOOKTIMER_TOSS_FINISH_TEMPLATE_CODE
  [TOSS_GOAL_MET_ENABLED]=BOOKTIMER_TOSS_GOAL_MET_ENABLED
  [TOSS_GOAL_MET_TEMPLATE_CODE]=BOOKTIMER_TOSS_GOAL_MET_TEMPLATE_CODE
  [TOSS_RETENTION_ENABLED]=BOOKTIMER_TOSS_RETENTION_ENABLED
  [TOSS_RETENTION_TEMPLATE_CODE]=BOOKTIMER_TOSS_RETENTION_TEMPLATE_CODE
  [ADMIN_LOGIN_IDS]=BOOKTIMER_ADMIN_LOGIN_IDS
  [LLM_API_KEY]=BOOKTIMER_LLM_API_KEY
  [SPRING_MAIL_USERNAME]=SPRING_MAIL_USERNAME
  [SPRING_MAIL_PASSWORD]=SPRING_MAIL_PASSWORD
  [MYSQL_ROOT_PASSWORD]=MYSQL_ROOT_PASSWORD
)

# ── 평문 설정 (구 task-definition의 environment 블록) ──
cat > "$TMP" <<EOF
SPRING_PROFILES_ACTIVE=prod
BOOKTIMER_BASE_URL=https://booktimer.app
SPRING_MAIL_HOST=email-smtp.${REGION}.amazonaws.com
SPRING_MAIL_PORT=587
BOOKTIMER_EMAIL_ENABLED=true
BOOKTIMER_SES_SNS_TOPIC_ARN=arn:aws:sns:${REGION}:${ACCOUNT_ID}:booktimer-ses-bounce-complaint
GA_MEASUREMENT_ID=G-JFVDPJ036E
BOOKTIMER_COUPANG_PARTNER_ENABLED=false
BOOKTIMER_IMAGE=${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/booktimer:latest
SPRING_SSL_BUNDLE_PEM_TOSS_KEYSTORE_CERTIFICATE=file:/etc/booktimer/toss/client-cert.pem
SPRING_SSL_BUNDLE_PEM_TOSS_KEYSTORE_PRIVATE_KEY=file:/etc/booktimer/toss/client-key.pem
EOF

# ── 시크릿 ──
missing=()
while IFS=$'\t' read -r name value; do
    key="${name##*/}"
    # /booktimer 아래 여러 줄 SecureString(PEM 등)이 있으면 --output text 가 줄바꿈마다 레코드를
    # 쪼개 본문 줄이 그대로 흘러든다. 본문 줄은 SECRET_MAP 에 없어 아래 continue 로 걸러지지만,
    # 빈 줄은 key="" 가 되고 연관배열의 빈 첨자 접근은 bad array subscript 로 죽는다(#702 배포 실패).
    [ -n "$key" ] || continue
    env_name="${SECRET_MAP[$key]:-}"
    [ -n "$env_name" ] || continue          # 매핑에 없는 파라미터는 앱이 안 쓰는 것
    printf '%s=%s\n' "$env_name" "$value" >> "$TMP"
    unset 'SECRET_MAP[$key]'                # 받은 것은 제거 → 남은 게 곧 누락분
done < <(aws ssm get-parameters-by-path --path /booktimer --with-decryption \
             --region "$REGION" --query 'Parameters[].[Name,Value]' --output text)

# 누락은 조용히 넘기지 않는다 — 빈 값으로 앱이 뜨면 "왜 검색이 안 되지" 같은 무성 장애가 된다.
for leftover in "${!SECRET_MAP[@]}"; do missing+=("/booktimer/$leftover"); done
if [ "${#missing[@]}" -gt 0 ]; then
    printf '[render-env] SSM 파라미터 누락: %s\n' "${missing[*]}" >&2
    exit 1
fi

install -m 600 "$TMP" "$OUT"     # 시크릿 파일은 소유자만 읽게
echo "[render-env] $OUT 생성 완료 ($(grep -c '=' "$OUT") 개 변수)"

# ── 토스 mTLS 클라이언트 인증서 (PEM 2개) ──
# .env가 아니라 파일로 떨어뜨린다 — env_file은 여러 줄 값을 담지 못하고, Spring SSL 번들도 경로를 받는다.
# 위 SECRET_MAP 루프에 얹지 않는 이유도 같다: get-parameters-by-path --output text는 한 줄에 한 파라미터라
# 여러 줄 PEM이 들어오면 레코드가 쪼개진다(그래서 파라미터별로 따로 받는다).
# compose가 이 디렉터리를 /etc/booktimer/toss 로 읽기전용 마운트한다. 위 평문 블록의 SPRING_SSL_BUNDLE_* 두 줄이 짝.
# 순서 주의는 없다 — Spring은 번들을 지연 생성해서 PEM이 없어도 앱은 뜨고 토스 호출 시점에만 실패한다(실측).
TOSS_DIR="${TOSS_DIR:-./toss}"
mkdir -p "$TOSS_DIR"

# 앱 컨테이너가 도는 uid — Dockerfile 의 `USER 10001:10001` 과 **한 쌍**이다.
# PEM 은 600 이라 소유자만 읽을 수 있는데, 이 배포는 root 로 돈다(SSM Run Command — 실측 uid=0).
# 그대로 두면 root:root 600 이 되어 비root 컨테이너가 못 읽고, Spring SSL 번들은 지연 생성이라
# **앱은 뜨고 토스 로그인만 조용히 죽는다**. 그래서 권한은 600 그대로 두고 소유자만 넘긴다.
APP_UID="${APP_UID:-10001}"

render_pem() {  # $1=SSM 파라미터 이름  $2=출력 파일
    local value
    # 누락은 조용히 넘기지 않는다 — 빈 PEM으로 앱이 뜨면 토스 로그인만 죽는 무성 장애가 된다.
    value="$(aws ssm get-parameter --name "$1" --with-decryption --region "$REGION" \
                 --query Parameter.Value --output text)" || {
        printf '[render-env] SSM 파라미터를 못 읽었다: %s\n' "$1" >&2
        exit 1
    }
    printf '%s\n' "$value" > "$2"
    chmod 600 "$2"
    # root 일 때만 넘길 수 있다. 실 배포는 root 이므로 항상 실행되고, 실패하면 set -e 가 배포를 죽인다.
    # root 가 아닌 실행(로컬 스모크 테스트)은 건너뛰되 조용히 넘어가지 않는다.
    if [ "$(id -u)" = 0 ]; then
        chown "$APP_UID:$APP_UID" "$2"
    else
        printf '[render-env] root가 아니라 %s 소유권 이전을 건너뛴다(실 배포는 root)\n' "$2" >&2
    fi
}

render_pem /booktimer/TOSS_MTLS_CERT "$TOSS_DIR/client-cert.pem"
render_pem /booktimer/TOSS_MTLS_KEY  "$TOSS_DIR/client-key.pem"
echo "[render-env] $TOSS_DIR 토스 mTLS 인증서 2개 생성 완료"
