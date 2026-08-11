import { describe, expect, it } from 'vitest';

// vite의 `?raw`로 파일 자체를 읽는다 — 파일이 사라지면 import가 깨져 테스트가 실패한다.
import envProduction from '../.env.production?raw';

/**
 * 운영 빌드 env 계측기 — `.env.production`이 커밋돼 있어야 `npm run build`만으로 운영 번들이 나온다.
 *
 * <p>왜 필요한가: 두 값은 **빌드 시점에 구워지고**(`import.meta.env`), 둘 다 조용한 기본값이 있다 —
 * API는 `http://localhost:8080`, 광고 그룹 ID는 빈 문자열(= 광고 기능 전체 OFF). 즉 env 없이 빌드해도
 * 빌드는 성공하고, 깨진 건 실기기에서 로그인 에러로만 드러난다(T-148). 이 파일이 지워지거나 값이
 * 비면 여기서 먼저 깨지게 한다.
 */
describe('.env.production', () => {
  it('운영 API 베이스 URL을 굽는다 — 없으면 localhost 번들이 배포된다', () => {
    expect(envProduction).toContain('VITE_API_BASE_URL=https://booktimer.app');
  });

  it('리워드 광고 그룹 ID를 굽는다 — 비면 광고 기능이 통째로 꺼진다', () => {
    expect(envProduction).toContain('VITE_REWARD_AD_GROUP_ID=ait.v2.live.b6bbeff2c57e4777');
  });
});
