// 설정 탭 맨 아래 줄(앞당김 보충 REQ-72): `v{version} · {channel} · {업데이트 id 앞 8자 | 내장 | 개발}`.
export function buildInfoLine(p: {
  version: string;
  channel: string | null;
  updateId: string | null;
  isEmbeddedLaunch: boolean;
  updatesEnabled: boolean;
}): string {
  const launch = !p.updatesEnabled ? '개발' : p.isEmbeddedLaunch || !p.updateId ? '내장' : p.updateId.slice(0, 8);
  return `v${p.version} · ${p.channel || '채널 없음'} · ${launch}`;
}
