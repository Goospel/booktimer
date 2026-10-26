// 라우트 폴더(src/app) 안의 파일은 화면으로 잡히므로 테스트는 여기에 둔다.
import { redirectSystemPath } from '../app/+native-intent';

describe('redirectSystemPath — 카카오톡 로그인 복귀', () => {
  it('카카오 복귀 주소는 라우터에 넘기지 않는다(카카오 SDK 몫 — 넘기면 Unmatched Route)', () => {
    expect(redirectSystemPath({ path: 'kakaobfbfa22db12bfb12770dbac22e8215b0://oauth?code=x', initial: false })).toBeNull();
    expect(redirectSystemPath({ path: 'kakao0fb70962dd03100ad2d0efa10c947c31://oauth?code=x', initial: true })).toBeNull();
  });

  it('앱 주소는 그대로 넘긴다 — 쿼리에 kakao가 들어 있어도', () => {
    expect(redirectSystemPath({ path: 'booktimer-dev:///book/1', initial: false })).toBe('booktimer-dev:///book/1');
    expect(redirectSystemPath({ path: 'booktimer:///search?q=kakao', initial: true })).toBe('booktimer:///search?q=kakao');
  });

  it('카카오가 아닌 등록 스킴도 그대로 넘긴다 — 허용 목록으로 바꾸면 구글 역순 스킴이 조용히 버려진다', () => {
    const google = 'com.googleusercontent.apps.20889772436-j1vun6jjup350gg3l26igcc0421tanph:/oauthredirect?code=x';
    expect(redirectSystemPath({ path: google, initial: false })).toBe(google);
  });
});
