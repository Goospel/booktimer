# BookTimer — 작업 계획 / 추후 할 일 (plan.md)

> 지금 당장 안 하지만 **놓치면 안 되는 할 일**을 모아두는 곳.
> 개요·도메인 규칙은 [README.md](README.md), 학습 개념은 [claude-docs/learning-notes.md](claude-docs/learning-notes.md),
> 함정·해결은 [claude-docs/troubleshooting.md](claude-docs/troubleshooting.md).

MVP(누적 타이머 + 인증 + 설정 + 일자별 기록 + 계정 보안)는 구현·배포 완료 상태.
아래는 그 이후 로드맵과 미뤄둔 보강 항목.

---

## 🔒 보안 / 인프라

### HTTPS 적용 — ALB TLS termination (우선순위: 높음)

**문제**: 현재 공개 서비스가 HTTP(평문)로 동작. 로그인 비밀번호·세션 쿠키(`JSESSIONID`)·
비밀번호 변경/회원 탈퇴 같은 민감 요청이 전송 구간에서 도청·변조·세션 탈취에 노출된다.

**방향**: 앱 코드에 TLS를 직접 박지 않고 **앞단(ALB) + ACM 인증서**로 TLS termination.
공개 구간(사용자↔ALB)만 HTTPS, 내부 구간(ALB↔ECS)은 VPC 사설망이라 HTTP 유지.
배경 개념은 학습 노트 **N-021** 참고.

**할 일 체크리스트**
- [ ] 도메인 확보(또는 기존 도메인 확인) — ACM DNS 검증에 필요
- [ ] ACM에서 인증서 발급 (해당 리전, DNS 검증)
- [ ] ALB에 HTTPS(443) 리스너 추가 + 인증서 연결, 타깃은 기존 ECS 서비스
- [ ] HTTP(80) → HTTPS(443) 리다이렉트 리스너 규칙
- [ ] Spring(prod): `server.forward-headers-strategy=framework` — ALB의 `X-Forwarded-Proto`
      신뢰해 앱이 자기 스킴을 `https`로 인식 (리다이렉트 URL·쿠키 판단 정상화)
- [ ] 세션 쿠키 `Secure` 플래그 (HTTPS로만 전송)
- [ ] (후속) HSTS 헤더 — 브라우저에 "다음부터 무조건 HTTPS"
- [ ] 보안 그룹: ALB 인바운드 443 허용 확인

**메모**: 인프라 변경이라 일반 코드 PR과 결이 다름 — 현재 ALB/도메인 구성 확인부터 시작.
로컬 개발은 HTTP 그대로 둔다(위협 구간 없음).

---

## 📖 기능 로드맵

### 책 단위 기록 (Book) — README §2.3
- 읽는 책(제목 등) 등록, 책별 누적 시간 추적
- SNS 확장의 핵심 컨텐츠 토대

### OAuth 소셜 로그인
- 구글/카카오 등 소셜 로그인 추가 (현재는 폼 로그인만)

### SNS 기능 — README §2.4
- 사용자 간 독서 기록 / 책별 시간 공유 (별도 설계 필요)

### 프론트엔드 전환 (SSR → SPA)
- 현재 Thymeleaf SSR. API 계약 안정성 + 인터랙션 요구가 커지면 전환 (N-017)

---

## 🔄 갱신 이력

| 일자 | 내용 |
|---|---|
| 2026-06-02 | plan.md 신설 — HTTPS(ALB TLS termination) 항목 + 기존 로드맵 정리 |
