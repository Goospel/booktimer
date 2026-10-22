// 앱이 부르는 경로의 응답형만(v1 §5.2). 단일 출처는 서버 record다.

/** `AppAuthApiController.Intent` */
export type AppleIntent = 'LOGIN' | 'REGISTER' | 'LINK';

/** `AppAuthApiController.AppAuthResponse` — registered가 false면 token·userId가 null이다. */
export interface AppAuthResponse {
  registered: boolean;
  token: string | null;
  created: boolean;
  webAccount: boolean;
  userId: number | null;
}
