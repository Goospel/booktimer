import { TDSMobileProvider } from '@toss/tds-mobile';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ChatMessage, ChatRoomSummary, ProfileResponse } from './api';
import {
  INBOX_POLL_MS,
  InboxView,
  ROOM_POLL_MS,
  RoomView,
  initialChat,
  lockText,
  mergeMessages,
  pollIntervalMs,
  sanctionText,
} from './screens/Chat';
import { BookshopHeader } from './screens/Bookshop';
import { ProfileCard, canMessage } from './screens/Profile';
import { userAgent } from './test-fixtures';

/**
 * 맞팔 DM 미니앱 화면(설계 §5-4·§6 PR-3). 하니스가 정적 렌더라 폴링·클릭·effect는 안 돈다(T-149) —
 * 판정은 순수 함수로 꺼내 전수로 재고, 렌더는 「그 판정이 마크업에 실제로 걸렸는가」만 본다.
 */

vi.mock('./toss', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./toss')>()),
  notificationAgreementSupported: vi.fn(() => true),
  requestNotificationAgreement: vi.fn(),
  trackEvent: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
});

function render(node: ReactNode) {
  return renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);
}

function message(id: number, extra: Partial<ChatMessage> = {}): ChatMessage {
  return { id, mine: false, body: `${id}번 메시지`, flagged: false, createdAt: '2026-09-19T03:00:00Z', ...extra };
}

function room(roomId: number, extra: Partial<ChatRoomSummary> = {}): ChatRoomSummary {
  return {
    roomId,
    partner: { loginId: `user${roomId}`, nickname: `상대${roomId}` },
    writable: true,
    lockReason: null,
    lastMessage: { body: `마지막 ${roomId}`, mine: false, createdAt: '2026-09-19T03:00:00Z' },
    unread: 0,
    lastMessageId: roomId * 10,
    ...extra,
  };
}

describe('폴링 간격 (pollIntervalMs)', () => {
  it('보이는 방에서 보낼 수 있으면 3초', () => {
    expect(pollIntervalMs(true, true)).toBe(3_000);
    expect(ROOM_POLL_MS).toBe(3_000);
  });

  it('잠긴 방은 대화함과 같은 10초 — 양쪽 다 못 보내니 풀렸는지만 본다', () => {
    expect(pollIntervalMs(true, false)).toBe(10_000);
    expect(INBOX_POLL_MS).toBe(10_000);
  });

  it('문서가 숨겨지면 멈춘다(null) — 보낼 수 있든 없든', () => {
    expect(pollIntervalMs(false, true)).toBeNull();
    expect(pollIntervalMs(false, false)).toBeNull();
  });
});

describe('메시지 합치기 (mergeMessages)', () => {
  it('새로 온 것을 뒤에 붙인다', () => {
    expect(mergeMessages([message(1)], [message(2), message(3)]).map((m) => m.id)).toEqual([1, 2, 3]);
  });

  it('같은 id는 한 번만 — 폴링이 겹쳐도 말풍선이 두 번 서지 않는다', () => {
    expect(mergeMessages([message(1), message(2)], [message(2), message(3)]).map((m) => m.id)).toEqual([1, 2, 3]);
  });

  it('도착 순서가 뒤섞여도 id 오름차순이다', () => {
    expect(mergeMessages([message(5)], [message(3), message(4)]).map((m) => m.id)).toEqual([3, 4, 5]);
  });

  it('빈 쪽이 있어도 그대로다', () => {
    expect(mergeMessages([], [message(1)]).map((m) => m.id)).toEqual([1]);
    expect(mergeMessages([message(1)], []).map((m) => m.id)).toEqual([1]);
    expect(mergeMessages([], [])).toEqual([]);
  });

  it('같은 id면 새로 받은 것이 이긴다', () => {
    expect(mergeMessages([message(1, { body: '옛' })], [message(1, { body: '새' })])[0].body).toBe('새');
  });
});

describe('잠김 문구 (lockText)', () => {
  it('보낼 수 있으면(null) 문구가 없다', () => {
    expect(lockText(null)).toBeNull();
  });

  it('서버 사유마다 다른 말을 한다', () => {
    expect(lockText('NOT_MUTUAL')).toBe('맞팔이 풀려 대화가 잠겼어요');
    expect(lockText('BLOCKED')).toBe('대화할 수 없는 상대예요');
    expect(lockText('UNREACHABLE')).toBe('상대가 아직 앱에서 대화를 쓸 수 없어요');
    expect(lockText('RESTRICTED')).toBe('지금은 대화를 보낼 수 없어요');
    expect(lockText('CLOSED')).toBe('종료된 대화예요');
  });

  it('모르는 사유도 잠김은 잠김이다 — 입력이 열린 것처럼 보이지 않게', () => {
    expect(lockText('SOMETHING_NEW' as never)).toBe('지금은 대화를 보낼 수 없어요');
  });
});

describe('딥링크 (initialChat)', () => {
  it('?chat=inbox면 대화함', () => {
    expect(initialChat('?chat=inbox')).toBe('inbox');
    expect(initialChat('?utm_source=toss&chat=inbox')).toBe('inbox');
  });

  it('없거나 모르는 값이면 null — 기본 화면으로 간다', () => {
    expect(initialChat('')).toBeNull();
    expect(initialChat('?chat=')).toBeNull();
    expect(initialChat('?chat=room')).toBeNull();
    expect(initialChat('?tab=history')).toBeNull();
  });
});

describe('제재 문구 (sanctionText)', () => {
  const NOW = Date.parse('2026-09-19T00:00:00Z');

  it('제재가 없거나 모르면 없다', () => {
    expect(sanctionText(null, NOW)).toBeNull();
    expect(sanctionText({ unreadRooms: 0, restrictedUntil: null, banned: false }, NOW)).toBeNull();
  });

  it('영구 정지', () => {
    expect(sanctionText({ unreadRooms: 0, restrictedUntil: null, banned: true }, NOW)).toBe(
      '대화 기능이 정지됐어요. 새 메시지를 보낼 수 없어요.',
    );
  });

  it('일시 정지는 끝나는 날짜를 말한다', () => {
    const text = sanctionText({ unreadRooms: 0, restrictedUntil: '2026-09-26T00:00:00Z', banned: false }, NOW);
    expect(text).toContain('9월 26일');
    expect(text).toContain('보낼 수 없어요');
  });

  it('이미 끝난 정지는 말하지 않는다(경계: 지금과 같으면 끝난 것)', () => {
    expect(sanctionText({ unreadRooms: 0, restrictedUntil: '2026-09-18T00:00:00Z', banned: false }, NOW)).toBeNull();
    expect(sanctionText({ unreadRooms: 0, restrictedUntil: '2026-09-19T00:00:00Z', banned: false }, NOW)).toBeNull();
  });
});

function roomView(extra: Partial<Parameters<typeof RoomView>[0]> = {}) {
  return render(
    <RoomView
      partner={{ loginId: 'nabi', nickname: '나비독서' }}
      messages={[message(1), message(2, { mine: true })]}
      writable
      lockReason={null}
      draft=""
      busy={false}
      error={null}
      notice={null}
      safety={null}
      onDraft={() => {}}
      onSend={() => {}}
      onReport={() => {}}
      onBlock={() => {}}
      onLeave={() => {}}
      {...extra}
    />,
  );
}

describe('대화방 (RoomView)', () => {
  it('상단에 신고·차단·나가기가 있다', () => {
    const html = roomView();
    expect(html).toContain('신고');
    expect(html).toContain('차단');
    expect(html).toContain('나가기');
  });

  it('열린 방은 잠김 문구가 없고 입력이 살아 있다(대조군)', () => {
    const html = roomView({ draft: '안녕' });
    expect(html).not.toContain('대화가 잠겼어요');
    expect(html).not.toMatch(/<textarea[^>]*disabled/);
  });

  it('잠긴 방은 사유를 말하고 입력·보내기를 막는다', () => {
    const html = roomView({ writable: false, lockReason: 'NOT_MUTUAL', draft: '안녕' });
    expect(html).toContain('맞팔이 풀려 대화가 잠겼어요');
    expect(html).toMatch(/<textarea[^>]*disabled/);
    expect(html).toMatch(/<button[^>]*disabled[^>]*>(?:(?!<\/button>).)*보내기/s);
  });

  it('상대의 표시된(flagged) 메시지가 있으면 안내 배너를 띄운다', () => {
    const html = roomView({ messages: [message(1, { flagged: true })] });
    expect(html).toContain('연락처·외부 링크·금전을 요구하는 상대는 신고해 주세요');
  });

  it('내가 보낸 표시 메시지뿐이면 배너가 없다 — 배너는 수신자에게만(대조군)', () => {
    const html = roomView({ messages: [message(1), message(2, { mine: true, flagged: true })] });
    expect(html).not.toContain('신고해 주세요');
  });

  it('복호화 못 한 본문(null)은 빈 말풍선 대신 안내로 선다', () => {
    const html = roomView({ messages: [message(1, { body: null })] });
    expect(html).toContain('표시할 수 없는 메시지예요');
  });

  it('메시지가 없으면 첫 인사를 권한다', () => {
    expect(roomView({ messages: [] })).toContain('첫 메시지를 보내 보세요');
  });
});

function inbox(extra: Partial<Parameters<typeof InboxView>[0]> = {}) {
  return render(
    <InboxView
      rooms={[]}
      me={null}
      now={Date.parse('2026-09-19T00:00:00Z')}
      error={null}
      showAgreement={false}
      onAgree={() => {}}
      onOpen={() => {}}
      {...extra}
    />,
  );
}

describe('대화함 (InboxView)', () => {
  it('상대 이름·마지막 메시지·미읽음 수를 보여 준다', () => {
    const html = inbox({ rooms: [room(1, { unread: 3 })] });
    expect(html).toContain('상대1');
    expect(html).toContain('마지막 1');
    expect(html).toContain('>3<');
  });

  it('미읽음이 0이면 배지가 없다(대조군)', () => {
    expect(inbox({ rooms: [room(1, { unread: 0 })] })).not.toContain('aria-label="읽지 않은 메시지');
    expect(inbox({ rooms: [room(1, { unread: 2 })] })).toContain('aria-label="읽지 않은 메시지 2개"');
  });

  it('잠긴 방은 목록에서도 잠김을 말한다', () => {
    const html = inbox({ rooms: [room(1, { writable: false, lockReason: 'NOT_MUTUAL' })] });
    expect(html).toContain('잠김');
  });

  it('빈 대화함은 여는 법을 말한다', () => {
    expect(inbox()).toContain('서로 팔로우한 친구의 책방에서 메시지를 보낼 수 있어요');
  });

  it('알림 동의는 화면 안 카드다 — 켜졌을 때만', () => {
    expect(inbox({ showAgreement: true })).toContain('새 메시지를 토스 알림으로 받아보세요');
    expect(inbox({ showAgreement: false })).not.toContain('토스 알림으로 받아보세요');
  });

  it('제재 중이면 대화함 위에서 말한다', () => {
    expect(inbox({ me: { unreadRooms: 0, restrictedUntil: null, banned: true } })).toContain('대화 기능이 정지됐어요');
  });
});

describe('책방 헤더의 대화함 진입', () => {
  it('대화를 쓸 수 있으면(inbox) 대화함 버튼과 미읽음 배지', () => {
    const html = render(<BookshopHeader onSearch={() => {}} inbox={{ unread: 4, onOpen: () => {} }} />);
    expect(html).toContain('aria-label="대화함"');
    expect(html).toContain('>4<');
  });

  it('미읽음이 없으면 배지 없이 버튼만', () => {
    const html = render(<BookshopHeader onSearch={() => {}} inbox={{ unread: 0, onOpen: () => {} }} />);
    expect(html).toContain('aria-label="대화함"');
    expect(html).not.toContain('읽지 않은');
  });

  it('대화가 꺼져 있으면(inbox 없음) 진입이 아예 없다', () => {
    expect(render(<BookshopHeader onSearch={() => {}} />)).not.toContain('대화함');
  });
});

function profile(extra: Partial<ProfileResponse> = {}): ProfileResponse {
  return {
    loginId: 'nabi',
    nickname: '나비독서',
    profileCharacterCode: null,
    followerCount: 3,
    followingCount: 5,
    following: true,
    self: false,
    personality: null,
    personalityTags: [],
    books: [],
    followsMe: true,
    dmAvailable: true,
    ...extra,
  };
}

/** `onMessage: null` = 대화가 꺼진 App(프롭 자체를 안 준다). 기본값 인자라 `undefined`로는 끌 수 없어 null로 받는다. */
function card(p: ProfileResponse, onMessage: (() => void) | null = () => {}) {
  return render(
    <ProfileCard
      profile={p}
      books={[]}
      activeTag={null}
      now={0}
      busy={false}
      personalityStatus={null}
      adBusy={false}
      earnedRetry={false}
      personalityNotice={null}
      archiveOpen={false}
      onArchive={() => {}}
      onSelectPersonality={() => {}}
      onClaimPersonality={() => {}}
      onRetryPersonality={() => {}}
      onFollowToggle={() => {}}
      onSelectTag={() => {}}
      statusFilter={null}
      onSelectStatus={() => {}}
      onMore={() => {}}
      safety={null}
      onMessage={onMessage ?? undefined}
    />,
  );
}

/** TDS Button은 글자를 `<span class="tds-mobile-button__content">`에 싣는다 — 안내 문장 속 「메시지를」과 갈리게 닫는 태그까지 본다. */
const MESSAGE_BUTTON = />메시지<\/span>/;
const HINT = '서로 팔로우하면 메시지를 보낼 수 있어요';

describe('남의 책방 「메시지」 버튼 (canMessage)', () => {
  it('맞팔 + dmAvailable이면 버튼이 선다(양성 대조군)', () => {
    expect(canMessage(profile())).toBe(true);
    expect(card(profile())).toMatch(MESSAGE_BUTTON);
  });

  it('내가 팔로우만 하면(상대가 안 함) 버튼이 없고 안내 한 줄', () => {
    const p = profile({ followsMe: false });
    expect(canMessage(p)).toBe(false);
    expect(card(p)).not.toMatch(MESSAGE_BUTTON);
    expect(card(p)).toContain(HINT);
  });

  it('상대만 나를 팔로우해도 버튼이 없다', () => {
    const p = profile({ following: false });
    expect(canMessage(p)).toBe(false);
    expect(card(p)).not.toMatch(MESSAGE_BUTTON);
    expect(card(p)).toContain(HINT);
  });

  it('맞팔이어도 dmAvailable이 아니면(웹 전용 상대) 버튼이 없다 — 맞팔 안내도 거짓이라 안 띄운다', () => {
    const p = profile({ dmAvailable: false });
    expect(canMessage(p)).toBe(false);
    expect(card(p)).not.toMatch(MESSAGE_BUTTON);
    expect(card(p)).not.toContain(HINT);
  });

  it('옛 서버(dmAvailable 없음)도 버튼이 없다', () => {
    expect(canMessage(profile({ dmAvailable: undefined }))).toBe(false);
  });

  it('대화가 꺼져 있으면(onMessage 없음) 버튼도 안내도 없다', () => {
    const html = card(profile({ followsMe: false }), null);
    expect(html).not.toMatch(MESSAGE_BUTTON);
    expect(html).not.toContain(HINT);
    expect(card(profile(), null)).not.toMatch(MESSAGE_BUTTON);
  });

  it('내 책방엔 둘 다 없다', () => {
    const html = card(profile({ self: true, following: false, followsMe: false }));
    expect(html).not.toMatch(MESSAGE_BUTTON);
    expect(html).not.toContain(HINT);
  });
});
