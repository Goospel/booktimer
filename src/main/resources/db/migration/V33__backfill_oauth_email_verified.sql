-- OAuth(소셜) 사용자 이메일 검증 보정 (이메일 인프라 2단계 — OAuth emailVerified 정합)
--
-- provider(구글 등)가 이메일 소유를 보증하고, 우리 OAuth 프로비저닝도 email_verified=true만
-- 통과시키므로(N-026) 소셜 사용자는 우리 기준으로도 검증된 것으로 본다. 그런데 V31이 당시 기존
-- 행을 전부 true로 백필한 이후, registerOAuth가 verifyEmail()을 호출하지 않아 email_verified=false
-- 로 가입된 소셜 사용자가 쌓였다 — 이들은 재참여 넛지 대상에서 빠지고(emailVerified=true 필수)
-- 대시보드·설정에 인증 배너가 무의미하게 노출된다. 그 잔존분을 true로 보정한다.
--
-- 로컬(LOCAL) 계정은 실제 인증 토큰 흐름을 거쳐야 검증되므로 건드리지 않는다(미검증 false 유지).
update users set email_verified = true where auth_provider <> 'LOCAL';
