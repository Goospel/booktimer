# facefit 동거 — 이 EC2에는 BookTimer 말고 하나가 더 산다

> **상태: 계획 확정 · 미배포**(2026-09-01). 실제로 뜨면 이 줄을 「**가동 중** · YYYY-MM-DD」로 고친다.
> 단일 출처는 facefit 레포의 `docs/2026-08-30-server-design.md` — §2-5(AWS 실측) ·
> §3-6(호스팅 결정) · **§3-6-1(분리 경로)**.

## 왜 이 문서가 있나

같은 사용자의 다른 미니앱 **facefit**의 API 서버가 **이 인스턴스에 얹혀 산다.**

BookTimer 세션이 AWS 장애를 디버깅할 때 **정체 모를 `facefit-api` 컨테이너가 메모리를 먹고
있으면 원인을 엉뚱한 데서 찾게 된다.** 그걸 막는 것이 이 문서의 유일한 목적이다.

## 무엇이 얹혀 있나

| 항목 | 값 |
|---|---|
| 컨테이너 | **`facefit-api` 1개** — `mem_limit 320m` · `-Xmx160m` · **호스트 포트 노출 없음** |
| compose | **facefit 레포**의 `server/deploy/compose.facefit.yaml` — **별개 compose 프로젝트**다. 이 레포 `deploy/compose.prod.yaml`에는 **없다**(그래서 `docker ps`에만 보이고 compose 파일엔 안 보인다 — 이 문서가 필요한 이유 중 하나) |
| 프록시 | 이 레포 `deploy/caddy/Caddyfile`의 `facefit-api.booktimer.app` 블록 — **이 레포에서 유일하게 facefit을 아는 파일** |
| DB | `booktimer-mysql-1` 안의 **`facefit` 데이터베이스** + 전용 사용자 `facefit`. 권한은 그 DB에만 — **BookTimer 데이터엔 못 닿는다** |
| 배포 | facefit 레포의 GitHub Actions(ECR `facefit` 리포지토리 → SSM). **BookTimer 배포와 무관하게 돈다** |
| 도메인 | `facefit-api.booktimer.app` → 같은 EIP `15.165.95.129`(Route 53, TTL 60초) |

## 문제가 생겼을 때

**facefit만 끄면 된다 — BookTimer는 안 죽는다:**

```bash
docker stop facefit-api
```

메모리가 급하면 이게 즉효다(약 300MB 회수). 되살리기는 `docker start facefit-api`.

**증상별 감별:**

| 증상 | facefit 탓인가 |
|---|---|
| 메모리 부족 · swap 급증 | **가능** — `docker stats`로 `facefit-api` 사용량 확인. 상한 320MB라 **그 이상은 구조적으로 못 먹는다** |
| BookTimer 502/503 · 응답 없음 | **아니다** — facefit은 8080을 호스트에 안 열고, Caddy가 호스트명으로 가른다. `booktimer.app` 라우팅과 분리돼 있다 |
| MySQL 느림 | **가능하나 낮다** — facefit 쿼리는 수십 KB 블롭 읽기/쓰기가 전부다. `SHOW PROCESSLIST`에서 `facefit` 사용자를 본다 |
| 디스크 부족 | **낮다** — 이미지 + 수 MB 데이터 |
| 인증서 문제 | Caddy가 두 호스트를 다 처리한다. facefit 도메인만 안 뜨면 그 A 레코드부터 본다 |

⚠️ **알려진 상호작용 — 배포 창**: blue/green 배포로 app 컨테이너가 2개 뜨는 수십 초 동안,
facefit까지 합치면 물리 메모리(1.9GB)를 **약 200MB 넘겨 swap으로 밀린다**
(`compose.prod.yaml`의 예산 주석이 잡은 1.78GB + facefit ~300MB). facefit 쪽은 무음 폴백
구조라 사용자에게 도달하지 않지만, **그 창에 배포가 느려 보이면 이게 원인일 수 있다.**

## 왜 여기에 얹었나 (한 줄)

이 계정엔 **프리티어가 없고**(2026-08 실결제 $32.80) facefit의 부하는 수십 KB 왕복이라,
전용 인스턴스는 월 ~$15.9를 쓰고 얻는 게 격리뿐이었다. 대신 **`mem_limit`으로 격리를 사고
비용은 0원**으로 뒀다.

## 떼어내려면

facefit 설계 **§3-6-1**에 결합 목록·분리 신호·절차·사전 준비가 있다.
**이 레포에서 할 일은 `deploy/caddy/Caddyfile`의 facefit 블록 삭제 하나**이고,
나머지(컨테이너·DB·DNS)는 facefit 쪽에서 처리한다.
