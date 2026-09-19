import { Button } from '@toss/tds-mobile';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';

import type { ChatLockReason, ChatMe, ChatMessage, ChatPartner, ChatRoomSummary, ReportReason } from '../api';
import {
  ApiError,
  blockUser,
  fetchChatMessages,
  fetchChatRooms,
  hideChatRoom,
  markChatRead,
  reportChatRoom,
  sendChatMessage,
} from '../api';
import { DM_TEMPLATE_CODE, notificationAgreementSupported, requestNotificationAgreement } from '../toss';
import { ErrorMessage, Loading, PENCIL_FRAME, Screen, Text, UnreadBadge, sectionStyle } from '../ui';
import type { SafetyState } from './Profile';
import { SafetyPanel, toggleSafety } from './Profile';

/**
 * 맞팔 DM — 대화함과 대화방(설계 §5-4). 서로 팔로우한 두 사람만 대화한다 — 자격은 서버가 매 요청 다시 보고,
 * 이 화면은 서버가 준 `writable`·`lockReason`을 그대로 그린다(판정을 클라가 흉내내지 않는다).
 *
 * <p>실시간 채널 없이 <b>짧은 폴링</b>이다: 방 3초·대화함 10초, 문서가 숨겨지면 멈춘다. 나가는 길은 토스
 * 네이티브 뒤로가기 하나다(자체 뒤로가기 금지, T-220) — App의 `useBackClose`가 방 → 대화함 → 출발 화면 순으로 닫는다.
 * 진입 직후 화면을 덮는 것은 없다(T-183): 알림 동의도 화면 안 카드, 신고·차단 확인도 눌러야 펼쳐지는 인라인이다.
 */

export const ROOM_POLL_MS = 3_000;
export const INBOX_POLL_MS = 10_000;

/**
 * 폴링 간격 — 숨겨진 문서면 `null`(멈춤). 잠긴 방은 나도 상대도 못 보내므로 풀렸는지만 대화함 속도로 본다.
 * effect 배선은 하니스 밖이라(T-149) 판정만 순수하게 뺐다 — 실기기 확인은 원장 U-5.
 */
export function pollIntervalMs(visible: boolean, writable: boolean): number | null {
  if (!visible) return null;
  return writable ? ROOM_POLL_MS : INBOX_POLL_MS;
}

/** 서버가 한 번에 주는 최대 건수(`findTop200ByRoomAndIdGreaterThan`). */
export const CHAT_PAGE_LIMIT = 200;

/** 상한을 꽉 채워 받았으면 뒤가 더 있을 수 있다 — 다음 틱(3초)을 기다리지 않고 곧바로 한 번 더 받는다. */
export function needsCatchUp(received: number): boolean {
  return received >= CHAT_PAGE_LIMIT;
}

/**
 * 방을 더 볼 수 없다(킬스위치가 꺼졌거나 내가 멤버가 아님 — 서버는 둘을 404로 통일) — 폴링을 멈추고 대화함으로 간다.
 * 차단으로 닫힌 방은 404가 아니라 `lockReason: CLOSED`로 온다.
 */
export function roomGone(error: Error): boolean {
  return error instanceof ApiError && error.status === 404;
}

/** 폴링 결과를 합친다 — id로 중복을 걷고(겹친 폴링) 오름차순. 같은 id면 새로 받은 것이 이긴다. */
export function mergeMessages(prev: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  const byId = new Map(prev.map((m) => [m.id, m]));
  for (const m of incoming) byId.set(m.id, m);
  return [...byId.values()].sort((a, b) => a.id - b.id);
}

const LOCK_TEXT: Record<ChatLockReason, string> = {
  NOT_MUTUAL: '맞팔이 풀려 대화가 잠겼어요',
  BLOCKED: '대화할 수 없는 상대예요',
  UNREACHABLE: '상대가 아직 앱에서 대화를 쓸 수 없어요',
  RESTRICTED: '지금은 대화를 보낼 수 없어요',
  CLOSED: '종료된 대화예요',
};

/** 잠김 문구 — 서버 사유 그대로. 모르는 사유(새 서버)도 잠김은 잠김으로 말한다. */
export function lockText(reason: ChatLockReason | null): string | null {
  if (reason === null) return null;
  return LOCK_TEXT[reason] ?? '지금은 대화를 보낼 수 없어요';
}

/**
 * 딥링크 — 새 메시지 푸시의 이동 URL(`intoss://booktimer/?chat=inbox`). 모르는 값은 `null`(기본 화면)이다.
 * 대화가 꺼져 있으면(킬스위치) App이 이 값을 조용히 버린다.
 */
export function initialChat(search: string): 'inbox' | null {
  return new URLSearchParams(search).get('chat') === 'inbox' ? 'inbox' : null;
}

/** 내 제재 문구 — 영구 정지 / 끝나는 날짜가 아직 안 온 일시 정지만 말한다. */
export function sanctionText(me: ChatMe | null, now: number): string | null {
  if (me === null) return null;
  if (me.banned) return '대화 기능이 정지됐어요. 새 메시지를 보낼 수 없어요.';
  if (me.restrictedUntil === null) return null;
  const until = new Date(me.restrictedUntil);
  if (!(until.getTime() > now)) return null;
  return `${until.getMonth() + 1}월 ${until.getDate()}일까지 새 메시지를 보낼 수 없어요.`;
}

/** 상대의 표시된 메시지가 있는가 — 배너는 <b>받는 사람</b>에게만(내가 보낸 것에 경고할 이유는 없다). */
function hasFlaggedFromPartner(messages: ChatMessage[]): boolean {
  return messages.some((m) => !m.mine && m.flagged);
}

const DM_AGREEMENT_KEY = 'booktimer.notificationAgreement.dm';

/** 문서가 보이는가 — 폴링을 멈추고 재개하는 유일한 입력. */
function useVisible(): boolean {
  const [visible, setVisible] = useState(
    () => typeof document === 'undefined' || document.visibilityState === 'visible',
  );
  useEffect(() => {
    const on = () => setVisible(document.visibilityState === 'visible');
    document.addEventListener('visibilitychange', on);
    return () => document.removeEventListener('visibilitychange', on);
  }, []);
  return visible;
}

/** 폴링 실패는 조용히 다음 틱이다(설계 §7-4) — 첫 로드 실패만 화면에 말한다. 401은 어디서든 로그인으로. */
function pollFailure(e: Error, loaded: boolean, onError: (e: Error) => void, setError: (m: string) => void) {
  if (e.name === 'UnauthorizedError') onError(e);
  else if (!loaded && !(e instanceof ApiError && e.status === 429)) setError(e.message);
}

// ── 대화함 ──────────────────────────────────────────────────────────────────

export function ChatInbox({
  me,
  onOpenRoom,
  onError,
}: {
  me: ChatMe | null;
  onOpenRoom: (roomId: number, partner: ChatPartner) => void;
  onError: (error: Error) => void;
}) {
  const [rooms, setRooms] = useState<ChatRoomSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [agreement, setAgreement] = useState<string | null>(() => localStorage.getItem(DM_AGREEMENT_KEY));
  const [supported] = useState(notificationAgreementSupported);
  const visible = useVisible();
  const loaded = useRef(false);

  const load = useCallback(() => {
    fetchChatRooms()
      .then((page) => {
        loaded.current = true;
        setRooms(page);
        setError(null);
      })
      .catch((e: Error) => pollFailure(e, loaded.current, onError, setError));
  }, [onError]);

  useEffect(() => {
    if (!visible) return;
    load();
    const id = setInterval(load, INBOX_POLL_MS);
    return () => clearInterval(id);
  }, [visible, load]);

  /** 동의 — 결과(동의·이미동의·거절)를 캐시해 카드를 끈다. 거절한 사람을 다시 조르지 않는다(홈 동의와 같은 규칙). */
  const agree = () => {
    requestNotificationAgreement(DM_TEMPLATE_CODE)
      .then((result) => {
        if (result === null) return; // 미지원 — 캐시를 남기면 새 토스앱에서도 영영 안 묻는다
        localStorage.setItem(DM_AGREEMENT_KEY, result);
        setAgreement(result);
      })
      .catch(() => {});
  };

  return (
    <InboxView
      rooms={rooms}
      me={me}
      now={Date.now()}
      error={error}
      showAgreement={supported && agreement === null}
      onAgree={agree}
      onOpen={(room) => onOpenRoom(room.roomId, room.partner)}
    />
  );
}

/** 대화함 본문 — 순수 표시. `rooms`가 `null`이면 아직 받는 중(빈 목록과 구분). */
export function InboxView({
  rooms,
  me,
  now,
  error,
  showAgreement,
  onAgree,
  onOpen,
}: {
  rooms: ChatRoomSummary[] | null;
  me: ChatMe | null;
  now: number;
  error: string | null;
  showAgreement: boolean;
  onAgree: () => void;
  onOpen: (room: ChatRoomSummary) => void;
}) {
  const sanction = sanctionText(me, now);
  return (
    <Screen title="대화">
      {sanction !== null && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginBottom: 12 }}>
          {sanction}
        </Text>
      )}
      {showAgreement && (
        <section style={{ ...sectionStyle, marginBottom: 16 }}>
          <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
            새 메시지를 토스 알림으로 받아보세요
          </Text>
          <Button display="block" variant="weak" size="medium" onClick={onAgree}>
            알림 받기
          </Button>
        </section>
      )}
      <ErrorMessage message={error} />
      {rooms === null ? (
        error === null && <Loading />
      ) : rooms.length === 0 ? (
        <Text typography="st11" color="grey600" style={{ display: 'block' }}>
          아직 대화가 없어요. 서로 팔로우한 친구의 책방에서 메시지를 보낼 수 있어요.
        </Text>
      ) : (
        rooms.map((room) => (
          <button
            key={room.roomId}
            type="button"
            onClick={() => onOpen(room)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              width: '100%',
              padding: 16,
              marginBottom: 8,
              border: 'none',
              borderRadius: 12,
              background: 'var(--adaptiveGrey100, #FCFAF5)',
              textAlign: 'left',
              cursor: 'pointer',
            }}
          >
            <span style={{ flex: 1, minWidth: 0 }}>
              <Text typography="st11" style={{ display: 'block' }}>
                {room.partner.nickname}
                {/* 평범한 span — TDS Text를 겹치면 블록으로 떨어져 이름 아래 줄로 밀린다(목 모드 실측). */}
                {!room.writable && (
                  <span style={{ fontSize: 13, color: 'var(--adaptiveGrey600, #6F6A5E)' }}> · 잠김</span>
                )}
              </Text>
              <Text
                typography="st12"
                color="grey600"
                style={{ display: 'block', marginTop: 4, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
              >
                {room.lastMessage.mine ? '나: ' : ''}
                {room.lastMessage.body}
              </Text>
            </span>
            <UnreadBadge count={room.unread} unit="메시지" />
          </button>
        ))
      )}
    </Screen>
  );
}

// ── 대화방 ──────────────────────────────────────────────────────────────────

export function ChatRoomScreen({
  roomId,
  partner,
  onLeft,
  onBlocked,
  onGone,
  onError,
}: {
  roomId: number;
  partner: ChatPartner;
  /** 「나가기」 뒤 — 이 방은 대화함에서 사라지므로 머무를 자리가 없다. */
  onLeft: () => void;
  /** 「차단」 뒤 — 나가기와 따로다: 뒤에 깔린 그 사람 책방도 차단 순간 404라 돌아갈 자리가 아니다. */
  onBlocked: () => void;
  /** 방 조회가 404 — 폴링을 멈추고 나간다(3초마다 404를 되풀이하지 않게). */
  onGone: () => void;
  onError: (error: Error) => void;
}) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  /** 첫 응답을 받았는가 — 그 전엔 입력을 막고 빈 방 안내도 안 띄운다(잠긴 방이 한순간 열려 보이지 않게). */
  const [ready, setReady] = useState(false);
  // App이 매 렌더 새 화살표를 넘겨도 폴링 콜백이 다시 만들어지지 않게 최신값만 든다(back.ts의 useBackClose와 같은 관례).
  const gone = useRef(onGone);
  gone.current = onGone;
  const [writable, setWritable] = useState(true);
  const [lockReason, setLockReason] = useState<ChatLockReason | null>(null);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [more, setMore] = useState<SafetyState | null>(null);
  const visible = useVisible();
  /** 받은 것 중 가장 큰 id — `after` 커서. 보낸 직후엔 늘리지 않는다(사이에 온 상대 메시지를 건너뛰지 않게). */
  const cursor = useRef(0);
  const loaded = useRef(false);

  const poll = useCallback(() => {
    fetchChatMessages(roomId, cursor.current)
      .then((page) => {
        // 첫 로드 실패 문구는 첫 성공이 지운다 — 매번 지우면 보내기 실패 문구가 다음 폴링(3초)에 사라진다.
        if (!loaded.current) setError(null);
        loaded.current = true;
        setReady(true);
        setWritable(page.writable);
        setLockReason(page.lockReason);
        if (page.messages.length === 0) return;
        cursor.current = Math.max(cursor.current, ...page.messages.map((m) => m.id));
        setMessages((prev) => mergeMessages(prev, page.messages));
        // 보고 있는 동안 받은 것은 읽은 것이다 — 서버가 방의 마지막 메시지 이상은 자른다.
        markChatRead(roomId, cursor.current).catch(() => {});
        if (needsCatchUp(page.messages.length)) poll(); // 200건 넘는 방 따라잡기
      })
      .catch((e: Error) => (roomGone(e) ? gone.current() : pollFailure(e, loaded.current, onError, setError)));
  }, [roomId, onError]);

  const interval = pollIntervalMs(visible, writable);
  useEffect(() => {
    if (interval === null) return;
    poll(); // 다시 보이면 곧바로 한 번 — 3초를 기다리지 않는다
    const id = setInterval(poll, interval);
    return () => clearInterval(id);
  }, [interval, poll]);

  // 새 메시지가 붙으면 맨 아래로 — 입력창이 목록 끝에 있다.
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => {
    end.current?.scrollIntoView({ block: 'end' });
  }, [messages.length]);

  const act = (action: Promise<unknown>, after: () => void) => {
    setBusy(true);
    setError(null);
    action
      .then(after)
      .catch((e: Error) => (e.name === 'UnauthorizedError' ? onError(e) : setError(e.message)))
      .finally(() => setBusy(false));
  };

  const send = () => {
    const text = draft.trim();
    if (text === '') return;
    act(sendChatMessage(roomId, text), () => {
      setDraft((d) => (d.trim() === text ? '' : d)); // 보내는 동안 친 글자는 남긴다
      poll(); // 보낸 것은 다음 조회로 받는다 — 로컬에 끼우면 커서가 상대 메시지를 건너뛸 수 있다
    });
  };

  const report = (reason: ReportReason, detail: string) =>
    act(reportChatRoom(roomId, reason, detail), () => {
      setMore(null);
      setNotice('신고가 접수됐어요. 검토 후 조치할게요.');
    });

  return (
    <RoomView
      partner={partner}
      messages={messages}
      loading={!ready}
      writable={writable}
      lockReason={lockReason}
      draft={draft}
      busy={busy}
      error={error}
      notice={notice}
      safety={
        more === null ? null : (
          <SafetyPanel
            busy={busy}
            confirmBlock={more.confirmBlock}
            onConfirmBlock={(confirmBlock) => setMore({ confirmBlock })}
            onBlock={() => act(blockUser(partner.loginId), onBlocked)}
            onReport={report}
          />
        )
      }
      onDraft={setDraft}
      onSend={send}
      onReport={() => setMore(toggleSafety)}
      // 「차단」은 확인 단계로 곧장 편다 — 한 탭 더 받는 것은 책방과 같다(되돌리기 비싸다).
      onBlock={() => setMore((open) => (open?.confirmBlock === true ? null : { confirmBlock: true }))}
      onLeave={() => act(hideChatRoom(roomId), onLeft)}
      end={<div ref={end} />}
    />
  );
}

/** 대화방 본문 — 순수 표시. 잠김이면 사유를 말하고 입력·보내기를 막는다. */
export function RoomView({
  partner,
  messages,
  loading = false,
  writable,
  lockReason,
  draft,
  busy,
  error,
  notice,
  safety,
  onDraft,
  onSend,
  onReport,
  onBlock,
  onLeave,
  end,
}: {
  partner: ChatPartner;
  messages: ChatMessage[];
  /** 첫 응답 전 — 입력을 막고 빈 방 안내를 띄우지 않는다. */
  loading?: boolean;
  writable: boolean;
  lockReason: ChatLockReason | null;
  draft: string;
  busy: boolean;
  error: string | null;
  notice: string | null;
  /** 펼친 신고·차단 패널 — 책방의 `SafetyPanel`을 그대로 쓴다. */
  safety: ReactNode;
  onDraft: (text: string) => void;
  onSend: () => void;
  onReport: () => void;
  onBlock: () => void;
  onLeave: () => void;
  /** 스크롤 앵커 — 목록 끝에 선다. */
  end?: ReactNode;
}) {
  const locked = lockText(writable ? null : (lockReason ?? 'RESTRICTED'));
  return (
    <Screen
      title={partner.nickname}
      subtitle={
        <Text typography="st12" color="grey600" style={{ display: 'block', marginBottom: 16 }}>
          @{partner.loginId}
        </Text>
      }
    >
      <div style={{ display: 'flex', gap: 8 }}>
        <Button size="small" variant="weak" disabled={busy} onClick={onReport}>
          신고
        </Button>
        <Button size="small" variant="weak" disabled={busy} onClick={onBlock}>
          차단
        </Button>
        <Button size="small" variant="weak" disabled={busy} onClick={onLeave}>
          나가기
        </Button>
      </div>
      {safety}
      {notice !== null && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
          {notice}
        </Text>
      )}
      {hasFlaggedFromPartner(messages) && (
        <div style={{ marginTop: 12, padding: 12, borderRadius: 10, background: 'var(--adaptiveGrey200, #E4DDD0)' }}>
          <Text typography="st12" style={{ display: 'block', wordBreak: 'keep-all' }}>
            연락처·외부 링크·금전을 요구하는 상대는 신고해 주세요
          </Text>
        </div>
      )}

      <div style={{ marginTop: 16 }}>
        {messages.length === 0 ? (
          !loading && (
            <Text typography="st12" color="grey600" style={{ display: 'block' }}>
              첫 메시지를 보내 보세요.
            </Text>
          )
        ) : (
          messages.map((m) => (
            <div key={m.id} style={{ display: 'flex', justifyContent: m.mine ? 'flex-end' : 'flex-start', marginBottom: 8 }}>
              <div
                style={{
                  maxWidth: '78%',
                  padding: '8px 12px',
                  borderRadius: 14,
                  background: m.mine ? 'var(--adaptiveBlue500, #6E8A6A)' : 'var(--adaptiveGrey100, #FCFAF5)',
                  color: m.mine ? '#FFFDF8' : 'var(--adaptiveGrey900, #3A362E)',
                  fontSize: 15,
                  lineHeight: 1.45,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                }}
              >
                {m.body ?? '표시할 수 없는 메시지예요'}
              </div>
            </div>
          ))
        )}
        {end}
      </div>

      {locked !== null && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 12 }}>
          {locked}
        </Text>
      )}
      <div style={{ display: 'flex', gap: 8, marginTop: 12, alignItems: 'flex-end' }}>
        <textarea
          value={draft}
          // busy는 넣지 않는다 — 보낼 때마다 disabled가 되면 포커스가 빠져 모바일 키보드가 내려간다(연타 방지는 버튼 몫).
          disabled={!writable || loading}
          maxLength={1000}
          rows={2}
          placeholder={writable ? '메시지 입력' : undefined}
          aria-label="메시지 입력"
          onChange={(e) => onDraft(e.target.value)}
          style={{
            flex: 1,
            padding: 10,
            borderRadius: 10,
            border: '1px solid transparent',
            borderImage: PENCIL_FRAME,
            fontSize: 15,
            resize: 'none',
          }}
        />
        <Button size="medium" disabled={!writable || loading || busy || draft.trim() === ''} onClick={onSend}>
          보내기
        </Button>
      </div>
      <ErrorMessage message={error} />
    </Screen>
  );
}
