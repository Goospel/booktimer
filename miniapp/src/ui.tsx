import { Text } from '@toss/tds-mobile';
import type { ReactNode } from 'react';

/** 화면 공통 껍데기 — 제목 + 본문 여백. 미니앱은 화면이 다섯 뿐이라 레이아웃도 이 하나면 된다. */
export function Screen({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main style={{ padding: '24px 20px 40px', maxWidth: 480, margin: '0 auto' }}>
      <Text typography="t3" fontWeight="bold" style={{ marginBottom: 20 }}>
        {title}
      </Text>
      {children}
    </main>
  );
}

/** 서버가 준 실패 메시지를 그대로 보여준다(연결 코드 오류·409 등은 문구 자체가 안내다). */
export function ErrorMessage({ message }: { message: string | null }) {
  if (message === null) return null;
  return (
    <Text typography="st11" color="red500" style={{ display: 'block', marginTop: 12 }}>
      {message}
    </Text>
  );
}

export function Loading({ message = '불러오는 중…' }: { message?: string }) {
  return (
    <main style={{ padding: 40, textAlign: 'center' }}>
      <Text typography="st11" color="grey600">
        {message}
      </Text>
    </main>
  );
}
