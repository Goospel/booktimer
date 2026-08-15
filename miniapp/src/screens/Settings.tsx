import { Button, Text, TextField } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';

import type { DashboardResponse, UserRow } from '../api';
import {
  deleteAccount,
  fetchBlocks,
  logout,
  unblockUser,
  updateNickname,
  validateNicknameFormat,
} from '../api';
import { openExternal } from '../toss';
import { ErrorMessage, Screen, Sheet, sectionStyle } from '../ui';
import { HandleSheet } from './Bookshop';

/** 웹에 공개된 문서들 — 둘 다 `permitAll`이라 로그인 없이 열린다(미니앱 계정은 웹 로그인 자체가 불가). */
const PRIVACY_URL = 'https://booktimer.app/privacy';
const TERMS_URL = 'https://booktimer.app/terms';

/**
 * 로그아웃 → 로그인 화면. **무슨 일이 있어도 화면을 넘긴다.**
 *
 * <p>`api.logout()`이 이미 실패를 삼키고 `finally`로 토큰을 버리므로, 여기서 넘기지 않으면 토큰 없는
 * 화면에 남아 무엇을 눌러도 401만 나는 막다른 길이 된다. 그래서 `finally`로 이동을 못 박는다.
 */
export async function logoutAndLeave(onDone: () => void): Promise<void> {
  try {
    await logout();
  } catch {
    // 폐기 실패는 삼킨다 — 되던지면 호출부의 `void`가 unhandled rejection이 된다. 토큰은 이미 버려졌다.
  }
  onDone();
}

/**
 * 로그아웃 2단 확인 — 홈 하단에 있던 것을 이 화면으로 옮겼다(홈에는 이 화면으로 오는 손잡이만 남는다).
 *
 * <p>확인 단계를 밖에서 받는 이유는 늘 같다 — 정적 렌더 하니스가 클릭을 못 잡아, 프롭이 아니면
 * 「정말 로그아웃」 가지에 영영 닿지 못한다(서재 `confirmDelete`·책방 `confirmBlock`과 같다).
 */
export function LogoutSection({
  confirm,
  onConfirm,
  onLogout,
}: {
  confirm: boolean;
  onConfirm: (confirm: boolean) => void;
  onLogout: () => void;
}) {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginTop: 32 }}>
      {confirm ? (
        <>
          <Button size="small" color="danger" onClick={onLogout}>
            정말 로그아웃
          </Button>
          <Button size="small" variant="weak" onClick={() => onConfirm(false)}>
            취소
          </Button>
        </>
      ) : (
        <Button size="small" variant="weak" onClick={() => onConfirm(true)}>
          로그아웃
        </Button>
      )}
    </div>
  );
}

/**
 * 회원 탈퇴 2단 확인 — 진입 버튼 → 시트(경고 + danger 버튼).
 *
 * <p>미니앱 전용 계정에는 **여기 말고 탈퇴할 자리가 없다**: 비밀번호가 없어 웹 `/settings/delete`에 로그인조차
 * 못 하고, @아이디가 없으면 웹 소셜 탈퇴의 핸들 재입력도 성립하지 않는다.
 *
 * <p>되돌릴 수 없는 동작이라 게이트가 셋이다 — ① 이 진입 버튼, ② 시트의 danger 버튼, ③ 서버가 요구하는
 * 토스 재인증({@link deleteAccount}). 시트 상태를 프롭으로 받는 이유는 로그아웃과 같다: 정적 렌더
 * 하니스가 클릭을 못 잡아, 프롭이 아니면 열린 시트 가지에 영영 닿지 못한다. TDS `BottomSheet`이 아니라
 * 자체 {@link Sheet}인 것도 같은 사정이다(포털은 정적 렌더에서 마크업이 통째로 빈다).
 */
export function DeleteAccountSection({
  open,
  busy,
  error,
  onOpen,
  onClose,
  onDelete,
}: {
  open: boolean;
  busy: boolean;
  /** 서버가 준 평문(400 인가코드 만료 · 403 본인 확인 실패) — 시트 안에 띄운다. */
  error: string | null;
  onOpen: () => void;
  onClose: () => void;
  onDelete: () => void;
}) {
  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'center', marginTop: 12 }}>
        <Button size="small" variant="weak" color="danger" onClick={onOpen}>
          회원 탈퇴
        </Button>
      </div>

      {open && (
        <Sheet title="회원 탈퇴" onClose={onClose}>
          <Text typography="st11" style={{ display: 'block' }}>
            탈퇴하면 독서 기록·책장·스토리·친구 관계가 <b>영구히 삭제</b>되고, <b>되돌릴 수 없어요.</b>
          </Text>
          {/* 클라이언트는 이 계정이 웹 계정과 연결됐는지 모른다 — 조건 노출 대신 상시 고지가 정직하다. */}
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
            웹(booktimer.app)에서 쓰던 계정이라면 웹 기록까지 모두 삭제됩니다.
          </Text>
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
            본인 확인을 위해 토스 로그인을 한 번 더 거쳐요.
          </Text>

          <ErrorMessage message={error} />

          <Button display="block" color="danger" style={{ marginTop: 16 }} loading={busy} onClick={onDelete}>
            모두 삭제하고 탈퇴
          </Button>
          <Button display="block" variant="weak" style={{ marginTop: 8 }} disabled={busy} onClick={onClose}>
            취소
          </Button>
        </Sheet>
      )}
    </>
  );
}

/**
 * 차단 목록 — 미니앱에서 차단을 푸는 <b>유일한 자리</b>. 소셜 탭이 책방 탭으로 바뀌며 여기로 왔다
 * (책방 탭은 이제 곧장 내 책방이라 관리 목록이 설 자리가 아니다).
 *
 * <p>0명이면 섹션을 아예 안 그린다 — 빈 관리 UI는 설정 화면의 소음이고, 차단은 대부분의 계정에 0건이다.
 * 순수 표시인 이유는 이 파일의 다른 섹션들과 같다: 정적 렌더 하니스가 fetch·클릭을 못 돌린다.
 */
export function BlockedSection({
  blocked,
  busy,
  onUnblock,
}: {
  blocked: UserRow[];
  busy: boolean;
  onUnblock: (loginId: string) => void;
}) {
  if (blocked.length === 0) return null;

  return (
    <section style={sectionStyle}>
      <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
        차단한 사람 {blocked.length}
      </Text>
      {blocked.map((u) => (
        <div key={u.loginId} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
          <Text typography="st11" style={{ flex: 1 }}>
            {u.nickname} @{u.loginId}
          </Text>
          <Button size="small" variant="weak" disabled={busy} onClick={() => onUnblock(u.loginId)}>
            차단 해제
          </Button>
        </div>
      ))}
    </section>
  );
}

/**
 * 프로필·설정 — 미니앱에서 내 계정에 손대는 유일한 화면.
 *
 * <p>있는 것은 넷뿐이다: 닉네임 · @아이디 · 하루 목표 · 로그아웃. **미니앱 채널에서 실제로 작동하는 것이
 * 이 넷 전부**라서다 — 타임존은 전원 한국이고, 이메일 인증·마케팅 수신은 토스 계정의 email이 발송하지 않는
 * 자리표시(synthetic)이며, 비밀번호는 아예 없고, 토스 연결은 이미 그 신원으로 들어와 있다.
 *
 * <p>웹 `/settings`를 흉내내지 않는 이유가 바로 그것이다 — 여기 온 사람은 **웹에 로그인할 수 없는 사람**이라
 * "booktimer.app에서 하세요"는 안내가 아니라 막다른 길이었다.
 */
export function Settings({
  dashboard,
  onBack,
  onProfileChanged,
  onGoGoal,
  goalAdPending,
  onLogout,
  onError,
}: {
  dashboard: DashboardResponse;
  onBack: () => void;
  /** 닉네임·핸들이 바뀌면 대시보드를 다시 받는다 — 홈 인사말·소셜이 같은 값을 봐야 한다. */
  onProfileChanged: () => void;
  onGoGoal: () => void;
  /** 전면광고를 기다리는 중 — 홈 손잡이와 같은 신호를 준다(두 진입점이 같은 경로를 타므로 표시도 같다). */
  goalAdPending: boolean;
  onLogout: () => void;
  onError: (error: Error) => void;
}) {
  // 서버가 저장한 값을 즉시 반영한다(대시보드 재조회를 기다리지 않는다 — 핸들 배너와 같은 방식).
  const [nickname, setNickname] = useState(dashboard.nickname);
  const [draft, setDraft] = useState(dashboard.nickname);
  const [handle, setHandle] = useState(dashboard.loginId);
  const [creatingHandle, setCreatingHandle] = useState(false);
  const [confirmLogout, setConfirmLogout] = useState(false);
  const [quitting, setQuitting] = useState(false);
  const [quitBusy, setQuitBusy] = useState(false);
  const [quitError, setQuitError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [blocked, setBlocked] = useState<UserRow[]>([]);
  const [blockBusy, setBlockBusy] = useState(false);

  /**
   * 차단 목록은 마운트 때 받는다. 401만 밖으로 올리고 나머지 실패는 <b>조용히 빈 목록</b>으로 둔다 —
   * 차단 조회가 실패했다고 설정 화면 전체에 빨간 줄을 띄울 일이 아니고, 0건이면 섹션 자체가 안 선다.
   */
  const loadBlocks = useCallback(() => {
    fetchBlocks()
      .then((page) => setBlocked(page.blocked))
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onError(e);
      });
  }, [onError]);

  useEffect(loadBlocks, [loadBlocks]);

  const unblock = (loginId: string) => {
    setBlockBusy(true);
    // 서버가 준 목록이 진실 — 낙관 제거 대신 다시 받는다(해제 실패면 그 사람이 그대로 남아야 한다).
    unblockUser(loginId)
      .then(loadBlocks)
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onError(e);
      })
      .finally(() => setBlockBusy(false));
  };

  const fail = (e: Error) => {
    if (e.name === 'UnauthorizedError') onError(e);
    else setError(e.message); // 400 평문 — 문구가 곧 안내다
  };

  // 빈 입력에까지 빨간 문구를 띄우진 않는다 — 아직 지우고 다시 치는 중일 수 있다.
  const formatError = draft.trim() === '' ? null : validateNicknameFormat(draft);
  const unchanged = draft.trim() === nickname;

  const save = () => {
    setBusy(true);
    setError(null);
    setSaved(false);
    updateNickname(draft.trim())
      .then((result) => {
        setNickname(result.nickname); // 서버가 저장한 값이 진실
        setDraft(result.nickname);
        setSaved(true);
        onProfileChanged();
      })
      .catch(fail)
      .finally(() => setBusy(false));
  };

  return (
    <Screen title="프로필·설정" onBack={onBack}>
      <section style={{ ...sectionStyle, marginTop: 0 }}>
        {/* 입력 하나짜리 form — 키보드 「완료」가 곧 저장이다(버튼은 밖에 둬 다른 버튼이 제출로 새지 않게). */}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!busy && !unchanged && formatError === null && draft.trim() !== '') save();
          }}
        >
          <TextField
            variant="box"
            label="닉네임"
            placeholder="예: 독서왕"
            value={draft}
            disabled={busy}
            onChange={(e) => setDraft(e.target.value)}
          />
        </form>
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
          친구와 책방에 보이는 이름이에요. 언제든 바꿀 수 있어요.
        </Text>

        <ErrorMessage message={formatError ?? error} />
        {saved && error === null && formatError === null && (
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
            ✅ 닉네임을 바꿨어요.
          </Text>
        )}

        <Button
          display="block"
          style={{ marginTop: 16 }}
          loading={busy}
          disabled={draft.trim() === '' || unchanged || formatError !== null}
          onClick={save}
        >
          닉네임 저장
        </Button>
      </section>

      <section style={sectionStyle}>
        {handle === null ? (
          <>
            <Text typography="st12" color="grey600" style={{ display: 'block' }}>
              @아이디를 만들면 친구가 나를 찾을 수 있고, 내 책방이 생겨요.
            </Text>
            <Button display="block" variant="weak" style={{ marginTop: 12 }} onClick={() => setCreatingHandle(true)}>
              아이디 만들기
            </Button>
          </>
        ) : (
          <>
            <Text typography="st11" style={{ display: 'block' }}>
              @{handle}
            </Text>
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 4 }}>
              아이디는 한 번 정하면 바꿀 수 없어요.
            </Text>
          </>
        )}
      </section>

      <section style={sectionStyle}>
        <Button display="block" variant="weak" onClick={onGoGoal} disabled={goalAdPending}>
          {goalAdPending ? '준비 중…' : '하루 목표 바꾸기'}
        </Button>
      </section>

      <BlockedSection blocked={blocked} busy={blockBusy} onUnblock={unblock} />

      {/* 약관·처리방침 — 기기 기본 브라우저로 넘긴다. 인앱 렌더는 같은 문서를 두 곳에 두는 일이라 안 한다. */}
      <section style={sectionStyle}>
        <Button display="block" variant="weak" onClick={() => openExternal(PRIVACY_URL)}>
          개인정보 처리방침
        </Button>
        <Button display="block" variant="weak" style={{ marginTop: 8 }} onClick={() => openExternal(TERMS_URL)}>
          이용약관
        </Button>
      </section>

      <LogoutSection
        confirm={confirmLogout}
        onConfirm={setConfirmLogout}
        onLogout={() => void logoutAndLeave(onLogout)}
      />

      <DeleteAccountSection
        open={quitting}
        busy={quitBusy}
        error={quitError}
        onOpen={() => {
          setQuitError(null);
          setQuitting(true);
        }}
        onClose={() => setQuitting(false)}
        onDelete={() => {
          setQuitBusy(true);
          setQuitError(null);
          deleteAccount()
            // 계정이 사라졌으니 로그인 브릿지로 — 거기서 `registered:false`를 받아 "새로 시작" 화면이 뜬다.
            .then(onLogout)
            // 실패는 시트를 닫지 않는다 — 닫으면 왜 안 됐는지(만료된 인가코드인지) 알 길이 없다.
            .catch((e: Error) => setQuitError(e.message))
            .finally(() => setQuitBusy(false));
        }}
      />

      {creatingHandle && (
        <HandleSheet
          onClose={() => setCreatingHandle(false)}
          onCreated={(loginId) => {
            setHandle(loginId); // 서버가 정규화한 값 — 이 화면이 그 자리에서 @표시로 바뀐다
            setCreatingHandle(false);
            onProfileChanged(); // 대시보드 재조회로 진실을 서버와 맞춘다
          }}
          onFail={fail}
        />
      )}
    </Screen>
  );
}
