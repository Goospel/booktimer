#!/usr/bin/env bash
# Smoke test for render-env.sh — 토스 mTLS 인증서 렌더 단계.
#
# Invariants this guards (each a distinct real failure):
#   1) PEM 두 개가 ./toss/ 에 **여러 줄 그대로** 떨어진다.
#      get-parameters-by-path --output text 루프(탭 구분 1행=1파라미터)로 PEM을 읽으면 줄바꿈에서
#      레코드가 쪼개져 첫 줄만 남는다 — 그러면 파일은 생기는데 인증서가 잘려 mTLS 핸드셰이크만 실패한다.
#   2) 인증서가 SSM에 없으면 배포가 죽는다. 조용히 넘기면 빈/없는 PEM으로 앱이 떠서
#      "왜 토스 로그인만 안 되지" 무성 장애가 된다(.env 시크릿 누락 처리와 같은 철학).
#   3) .env 에 SSL 번들 경로 두 줄이 들어간다. 없으면 파일은 멀쩡히 있는데 Spring이 안 읽는다.
#   4) 파일 권한 600 (정적 검사 — 이 레포 개발 환경 파일시스템은 chmod를 보존하지 않아 런타임 재현 불가).
#
# aws 호출은 PATH 앞의 스텁으로 전부 우회한다(test-backup-mysql.sh 와 같은 방식).

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
S="$REPO/deploy/render-env.sh"
FAILED=0

STUB="$(mktemp -d)"
WORK="$(mktemp -d)"
trap 'rm -rf "$STUB" "$WORK"' EXIT

CERT_PEM='-----BEGIN CERTIFICATE-----
MIIBcertLINE2
MIIBcertLINE3
-----END CERTIFICATE-----'
KEY_PEM='-----BEGIN PRIVATE KEY-----
MIIBkeyLINE2
-----END PRIVATE KEY-----'

# $1 = 누락시킬 파라미터 이름(빈값이면 전부 존재)
make_stub() {
    cat > "$STUB/aws" <<STUBEOF
#!/usr/bin/env bash
MISSING="$1"
case "\$*" in
    *get-caller-identity*) echo "111122223333" ;;
    *get-parameters-by-path*)
        # 앱이 실제로 쓰는 시크릿 전부 — 하나라도 빠지면 render-env.sh가 누락으로 죽는다.
        for n in SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \\
                 GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET ALADIN_TTB_KEY \\
                 COUPANG_SEARCH_URL_TEMPLATE YES24_TRACKING_CODE YES24_SEARCH_URL_TEMPLATE \\
                 KYOBO_TRACKING_CODE KYOBO_SEARCH_URL_TEMPLATE KYOBO_MOBILE_SEARCH_URL_TEMPLATE \\
                 ADMIN_LOGIN_IDS LLM_API_KEY SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD \\
                 MYSQL_ROOT_PASSWORD MINIAPP_ALLOWED_ORIGINS \\
                 TOSS_MESSENGER_ENABLED TOSS_FINISH_TEMPLATE_CODE \\
                 TOSS_GOAL_MET_ENABLED TOSS_GOAL_MET_TEMPLATE_CODE; do
            printf '/booktimer/%s\tvalue-of-%s\n' "\$n" "\$n"
        done
        # 여러 줄 SecureString(PEM)도 같은 /booktimer 경로에 살아 이 목록에 함께 나온다.
        # --output text 는 줄바꿈에서 레코드를 쪼개므로 PEM 본문 줄이 탭 없이 뒤따르고,
        # 빈 줄은 name="" 레코드가 된다 — 이게 bad array subscript 로 운영 배포를 죽였다(#702 실측).
        printf '/booktimer/TOSS_MTLS_CERT\t%s\n' '-----BEGIN CERTIFICATE-----'
        printf '%s\n' 'MIIBcertLINE2' '' '-----END CERTIFICATE-----'
        ;;
    *get-parameter*TOSS_MTLS_CERT*)
        [ "\$MISSING" = TOSS_MTLS_CERT ] && { echo "ParameterNotFound" >&2; exit 255; }
        cat <<'PEMEOF'
$CERT_PEM
PEMEOF
        ;;
    *get-parameter*TOSS_MTLS_KEY*)
        [ "\$MISSING" = TOSS_MTLS_KEY ] && { echo "ParameterNotFound" >&2; exit 255; }
        cat <<'PEMEOF'
$KEY_PEM
PEMEOF
        ;;
    *) echo "STUB-AWS unexpected: \$*" >&2; exit 9 ;;
esac
STUBEOF
    chmod +x "$STUB/aws"
}

run() {  # $1=누락 파라미터(빈값 가능) ; echoes "<exit>\n<output>" — 작업 디렉터리는 매번 새로
    local out rc
    make_stub "$1"
    rm -rf "$WORK"; mkdir -p "$WORK"
    out="$(cd "$WORK" && PATH="$STUB:$PATH" bash "$S" 2>&1)"; rc=$?
    printf '%s\n' "$rc"
    printf '%s' "$out"
}

assert_exit() {  # $1=label $2=got $3=want
    if [ "$2" = "$3" ]; then echo "PASS: $1 (exit $2)"; else echo "FAIL: $1 exit=$2 want=$3"; FAILED=1; fi
}
assert_has() {   # $1=label $2=haystack $3=needle
    if printf '%s' "$2" | grep -qF -- "$3"; then echo "PASS: $1"; else echo "FAIL: $1 — missing '$3'"; FAILED=1; fi
}
assert_eq() {    # $1=label $2=got $3=want
    if [ "$2" = "$3" ]; then echo "PASS: $1"; else echo "FAIL: $1 — got '$2' want '$3'"; FAILED=1; fi
}
assert_no_file() {  # $1=label $2=path
    if [ -e "$2" ]; then echo "FAIL: $1 — $2 가 생겼다"; FAILED=1; else echo "PASS: $1"; fi
}

# ── Case 1: 정상 — PEM 2개가 여러 줄 그대로 떨어지고 .env에 경로가 박힌다 ──
r="$(run "")"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "정상 렌더" "$rc" "0"
# by-path 목록에 섞인 여러 줄 PEM(빈 줄 포함)이 시크릿 루프를 죽이지 않는다 — 죽으면 .env 자체가 안 만들어진다.
assert_has "  여러 줄 PEM 레코드가 섞여도 시크릿 루프가 산다" "$out" ".env 생성 완료"
assert_eq "  client-cert.pem 이 원문 그대로(여러 줄)" "$(cat "$WORK/toss/client-cert.pem" 2>/dev/null)" "$CERT_PEM"
assert_eq "  client-key.pem 이 원문 그대로(여러 줄)"  "$(cat "$WORK/toss/client-key.pem" 2>/dev/null)"  "$KEY_PEM"

env_out="$(cat "$WORK/.env" 2>/dev/null)"
assert_has "  .env 에 인증서 경로" "$env_out" "SPRING_SSL_BUNDLE_PEM_TOSS_KEYSTORE_CERTIFICATE=file:/etc/booktimer/toss/client-cert.pem"
assert_has "  .env 에 개인키 경로" "$env_out" "SPRING_SSL_BUNDLE_PEM_TOSS_KEYSTORE_PRIVATE_KEY=file:/etc/booktimer/toss/client-key.pem"
# PEM 본문이 .env로 새면 안 된다 — env_file은 여러 줄 값을 못 담아 파싱이 깨지고, 시크릿 중복 보관이 된다.
assert_eq "  PEM 본문은 .env 에 안 들어간다" "$(printf '%s' "$env_out" | grep -cF 'BEGIN CERTIFICATE')" "0"
# 미니앱 CORS 허용 오리진 — 매핑이 없으면 render-env가 조용히 건너뛰어(.env에 빠짐) 앱이 빈 기본값으로 뜨고,
# 토스 WebView 프리플라이트만 막히는 무성 장애가 된다.
assert_has "  .env 에 미니앱 허용 오리진" "$env_out" "BOOKTIMER_MINIAPP_ALLOWED_ORIGINS=value-of-MINIAPP_ALLOWED_ORIGINS"
# 토스 완독 축하 푸시 — 매핑이 없으면 render-env가 조용히 건너뛰어 앱이 기본값(게이트 OFF·템플릿 없음)으로 뜬다.
# 즉 SSM을 true로 켜도 푸시가 안 나가는데 로그에는 아무 흔적이 없는 무성 장애가 된다.
assert_has "  .env 에 메신저 게이트" "$env_out" "BOOKTIMER_TOSS_MESSENGER_ENABLED=value-of-TOSS_MESSENGER_ENABLED"
assert_has "  .env 에 완독 템플릿 코드" "$env_out" "BOOKTIMER_TOSS_FINISH_TEMPLATE_CODE=value-of-TOSS_FINISH_TEMPLATE_CODE"
# 목표 달성 푸시 — 게이트가 매핑에서 빠지면 스케줄러 빈이 안 떠서 "켰는데 아무 일도 안 일어나는" 무성 장애가 된다.
assert_has "  .env 에 목표달성 게이트" "$env_out" "BOOKTIMER_TOSS_GOAL_MET_ENABLED=value-of-TOSS_GOAL_MET_ENABLED"
assert_has "  .env 에 목표달성 템플릿 코드" "$env_out" "BOOKTIMER_TOSS_GOAL_MET_TEMPLATE_CODE=value-of-TOSS_GOAL_MET_TEMPLATE_CODE"

# ── Case 2: 인증서 누락 → 배포 실패, 파일도 안 남는다 ──
r="$(run TOSS_MTLS_CERT)"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "인증서 누락 → 실패" "$rc" "1"
assert_has  "  누락을 이름으로 알린다" "$out" "TOSS_MTLS_CERT"
assert_no_file "  반쪽 인증서를 남기지 않는다" "$WORK/toss/client-cert.pem"

# ── Case 3: 개인키 누락도 같은 취급 (한쪽만 검사하고 통과하는 흔한 버그) ──
r="$(run TOSS_MTLS_KEY)"; rc="${r%%$'\n'*}"; out="${r#*$'\n'}"
assert_exit "개인키 누락 → 실패" "$rc" "1"
assert_has  "  누락을 이름으로 알린다" "$out" "TOSS_MTLS_KEY"

# ── Case 4: 권한 600 (정적 — 개발 환경 파일시스템이 chmod를 보존하지 않는다) ──
src="$(cat "$S")"
assert_has "PEM 파일 권한 600" "$src" "chmod 600"

# ── Case 5: compose가 인증서 디렉터리를 컨테이너에 읽기전용 마운트한다 ──
# 렌더만 되고 마운트가 없으면 파일은 EC2에 있는데 컨테이너 안엔 없다 — 증상이 "인증서 없음"으로 같아 헷갈린다.
compose="$(cat "$REPO/deploy/compose.prod.yaml")"
assert_has "compose 마운트(읽기전용)" "$compose" "./toss:/etc/booktimer/toss:ro"

echo
if [ "$FAILED" = 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$FAILED"
