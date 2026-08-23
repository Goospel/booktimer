import { describe, expect, it } from 'vitest';

// vite의 `?raw`로 파일 자체를 읽는다 — 파일이 사라지면 import가 깨져 테스트가 실패한다.
import envProduction from '../.env.production?raw';
import homeSource from './screens/Home.tsx?raw';
import profileSource from './screens/Profile.tsx?raw';

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
    expect(envProduction).toMatch(/^VITE_API_BASE_URL=https:\/\/booktimer\.app\s*$/m);
  });

  it('리워드 광고 그룹 ID를 굽는다 — 비면 광고 기능이 통째로 꺼진다', () => {
    expect(envProduction).toMatch(/^VITE_REWARD_AD_GROUP_ID=ait\.v2\.live\.b6bbeff2c57e4777\s*$/m);
  });

  it('전면 광고 그룹 ID를 굽는다 — 리워드와 다른 그룹이라 섞이면 정산·성과가 어긋난다', () => {
    expect(envProduction).toMatch(/^VITE_INTERSTITIAL_AD_GROUP_ID=ait\.v2\.live\.32f30171507d4dd1\s*$/m);
  });

  it('성향 분석 광고 그룹 ID를 굽는다 — 비면 책방의 성향 광고 버튼이 통째로 사라진다', () => {
    expect(envProduction).toMatch(/^VITE_PERSONALITY_AD_GROUP_ID=ait\.v2\.live\.75558b51a0444cc7\s*$/m);
  });
});

/**
 * 지면↔광고 그룹 배선 — 콘솔 리포트의 단위가 광고 그룹이라, 두 지면이 한 그룹을 보면 노출·시청·수익이
 * 합산돼 <b>어느 자리가 버는지 영영 못 가린다</b>. 그게 이 분리의 전부이므로 뒤바뀜을 여기서 막는다.
 *
 * <p>왜 렌더 테스트가 아니라 소스 판독인가: 홈의 지우개 버튼은 「남은시간 ⓘ」 접힌 상자 안에 있고
 * 그 상자는 `showNote` 초기 false로 시작한다 — 하니스가 정적 렌더라 펼 수가 없어 <b>홈 방향은 렌더로
 * 관측 자체가 불가능</b>하다(책방 쪽은 첫 렌더에 보여서 `bookshop.test.tsx`의 목이 가드를 겸한다).
 * 소스에 실린 식별자를 읽는 건 이 파일이 `.env.production`에 이미 쓰는 방식(`?raw`) 그대로다.
 */
describe('지면별 광고 그룹 배선', () => {
  it('홈은 부채 지우개 그룹만 본다 — 성향 그룹으로 새면 책방 수익이 홈에 잡힌다', () => {
    expect(homeSource).toContain('REWARD_AD_GROUP_ID');
    expect(homeSource).not.toContain('PERSONALITY_AD_GROUP_ID');
  });

  it('책방은 성향 그룹만 본다 — 부채 그룹으로 되돌아가면 두 지면이 다시 합산된다', () => {
    expect(profileSource).toContain('PERSONALITY_AD_GROUP_ID');
    expect(profileSource).not.toContain('REWARD_AD_GROUP_ID');
  });
});
