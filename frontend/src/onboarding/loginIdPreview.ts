// 온보딩 아이디(@핸들) 클라이언트 정제 — 서버 User.normalizeLoginId의 거울.
//
// 서버(단일 진실): rawLoginId.strip().toLowerCase(Locale.ROOT) → ^[a-z0-9_]{3,20}$ 검증 → 예약어 검증.
// 여기선 "입력하는 대로 최종 핸들이 보이도록" 실시간 정제한다 — 앞뒤 공백 제거·소문자화·허용 외 문자
// 제거·20자 clamp까지만(길이 하한·예약어·유니크는 서버가 최종 판정). 이 정제값을 미리보기·input 되쓰기·
// 제출값에 공통으로 써서 "미리보기와 실제 저장이 어긋나는" 간극을 원천 차단한다(계획 PR-D 리스크).

/** 서버가 받아들일 문자만 남긴 정제 핸들(소문자·[a-z0-9_]·최대 20자). 하한(3자)은 검사하지 않는다. */
export function sanitizeLoginId(raw: string): string {
    return (raw ?? '')
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9_]/g, '')
        .slice(0, 20);
}

/** 서버 LOGIN_ID_PATTERN(3~20자 [a-z0-9_])과 동일한 형식 유효성. 예약어·유니크는 서버 몫. */
export function isValidLoginId(sanitized: string): boolean {
    return /^[a-z0-9_]{3,20}$/.test(sanitized);
}
