# 프론트엔드 디자인 작업 워크플로 — 두 세션 협업

> 입구(랜딩·가입·온보딩·대시보드 첫인상)에서 미감 때문에 이탈하는 것을 막기 위한 디자인 트랙.
> 백엔드 세션(컨트롤러·데이터)과 디자인 세션(미감·CSS)을 **충돌 없이 병렬**로 굴리는 방법을 못 박는다.
> (합의: 2026-06-09)

관련: 워크트리 분리 개념 [learning-notes N-032](learning-notes.md) · 데이터 계약 [template-data-contract.md](template-data-contract.md) · 프로젝트 규칙 [CLAUDE.md](../CLAUDE.md).

---

## 1. 전제 — 이 프로젝트의 프론트 현실

**Thymeleaf SSR 모놀리식**이다. 별도 프론트엔드 레포·빌드가 **없다**.

- "프론트"의 실체 = `src/main/resources/templates/*.html` (25개) + `static/css/app.css` **단일 파일** + `static/js/` (소량).
- 상호작용은 **htmx 부분 swap**(예: 책장 필터 #271, 공개 토글 #263) — SPA 아님.
- 입구 화면은 이미 존재: `landing` · `login` · `signup` · `onboarding` · `dashboard`(첫 로그인 빈 상태).
- **정적 목업 패턴을 이미 사용 중**: `.preview/*-mock.html` + `serve.js`(static-preview). 캐러셀(#267/#269)을 이 방식으로 디자인했다.

> ⚠️ React/Vue처럼 프론트를 떼는 건 **API화(백엔드를 JSON 서버로 전환)**가 전제 — 입구 미감 개선엔 과한 수술이라 **하지 않는다**. 디자인은 같은 레포의 `app.css` + 템플릿에서 일어난다.

---

## 2. "새 디렉토리"의 두 가지 올바른 해석

| 해석 | 정체 | 언제 |
|---|---|---|
| **(A) 정적 디자인 샌드박스** — `.preview/` 확장 | 순수 HTML/CSS 목업. Spring·DB·로그인 불필요. preview 툴로 **즉시 시각 피드백** | **미감 반복(기본)** — 색/타이포/여백/레이아웃 실험 |
| **(B) 워크트리** — `../BookTimer-design` | 같은 레포 다른 폴더(N-032). 실 템플릿·`app.css` 직접 편집 | 합의된 디자인을 **실제 포팅**할 때 |

핵심 원칙: **디자인은 (A)에서 빠르게 굴리고, 확정되면 (B)로 포팅**한다.
(A)가 백엔드 세션과 안 부딪히는 이유 — 전부 **새 파일**이라 충돌 표면이 0이다.

---

## 3. 디자인 트랙 흐름 (4단계)

**Phase 0 — 진단(입구부터).** 첫인상 = 전환이라 `landing → signup → onboarding → dashboard 빈 상태` 순으로 "정 떨어지는 지점"을 먼저 박는다. 내부 기능 화면(`/books`, `profile`)은 그 다음.

**Phase 1 — 페이지보다 디자인 시스템 먼저.** `app.css` 최상단에 **CSS custom properties**(색 팔레트·타입 스케일·여백 스텝·radius·그림자)를 박아 "시스템"을 만든다. 이게 있어야 25개 화면이 따로 놀지 않고, 두 세션이 같은 어휘(`var(--space-4)`, `var(--brand)`)로 말한다.

**Phase 2 — 정적 목업으로 화면별 반복.** `.preview/<page>-mock.html`로 실 마크업을 떼어와 순수 HTML/CSS로 미감을 굴린다. preview 툴(스크린샷·resize로 모바일/다크)로 즉시 확인. **Spring 안 띄워도 됨** → 반복 속도 10배.

**Phase 3 — 포팅.** 합의된 CSS는 `app.css`로, 구조는 Thymeleaf 템플릿으로 옮긴다. 시각이라 TDD 거의 무관 → preview/`bootRun` 수동 검증이 게이트.

---

## 4. 두 세션 협업 메커닉 (핵심)

### 4-1. 소유권 분할 — "자연스러운 협업"의 본질

| | 백엔드 세션 | 디자인 세션 |
|---|---|---|
| **소유** | 컨트롤러 · 모델 적재 · `th:*` **데이터 바인딩** · 라우트 | `app.css` **단독** · 레이아웃/클래스/마크업 구조 · 정적 목업 |
| **안 건드림** | 시각 클래스 · CSS | 데이터 의미(`th:each`/`th:if` 조건) |

### 4-2. 공유 계약 2개 (= 인터페이스)

1. **템플릿 데이터 계약**(백→디자인) — [template-data-contract.md](template-data-contract.md). 각 화면이 받는 모델 변수 + nullable + **빈 상태**. 없으면 디자인이 존재하지 않는 데이터로 화면을 그리거나 빈 상태를 빠뜨린다(N-055의 디자인판).
2. **디자인 시스템 토큰**(디자인→백) — `app.css` 상단 토큰 + 클래스 사전. 백엔드가 새 화면 마크업 짤 때 쓴다.

### 4-3. 충돌 핫스팟 & 회피

| 자원 | 위험 | 회피 |
|---|---|---|
| `app.css` (단일 파일) | 양쪽 동시 편집 시 머지 충돌 | **디자인 세션 단독 소유.** 백엔드는 CSS 안 건드림 |
| 같은 `.html`(데이터+표현 한 파일) | 가장 위험 — "File modified since read" 가드·충돌 | **목업→포팅 흐름으로 우회**(디자인은 목업, 포팅은 한 세션 순차) |
| Flyway·포트 8080·DB | — | 디자인 작업엔 무관(시각만 건드림) |

### 4-4. 워크트리 분리(포팅 단계)

```
git worktree add ../BookTimer-design -b feat/design-<surface> main
# 디자인 세션이 그 폴더에서 작업(절대경로 편집/커밋), 머지 후:
git worktree remove ../BookTimer-design
```

PR 우선 워크플로 그대로. 입구는 surface별 작은 PR로(landing → signup → …).

---

## 5. 진행 순서 (이번 사이클)

1. ✅ 이 워크플로 + 데이터 계약 문서화.
2. **랜딩부터** — `landing`은 모델 데이터 0(완전 정적, [data-contract](template-data-contract.md) 참고)이라 디자인 세션이 통째 소유 가능. Phase 1(토큰) + Phase 2(목업)을 landing에 적용.
3. 이후 signup → onboarding → dashboard 빈 상태 순으로 확장.

> 범위 기본값: **"토큰 먼저 깐 리프레시"**(app.css 토큰 + 점진적 화면 적용, 마크업 최소 변경). 깊은 리디자인(정보구조 변경)은 surface별로 필요할 때 opt-in.
