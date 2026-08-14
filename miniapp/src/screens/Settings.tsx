import { Button, Text, TextField } from '@toss/tds-mobile';
import { useState } from 'react';

import type { DashboardResponse } from '../api';
import { logout, updateNickname, validateNicknameFormat } from '../api';
import { ErrorMessage, Screen, sectionStyle } from '../ui';
import { HandleSheet } from './Social';

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
  onLogout,
  onError,
}: {
  dashboard: DashboardResponse;
  onBack: () => void;
  /** 닉네임·핸들이 바뀌면 대시보드를 다시 받는다 — 홈 인사말·소셜이 같은 값을 봐야 한다. */
  onProfileChanged: () => void;
  onGoGoal: () => void;
  onLogout: () => void;
  onError: (error: Error) => void;
}) {
  // 서버가 저장한 값을 즉시 반영한다(대시보드 재조회를 기다리지 않는다 — 핸들 배너와 같은 방식).
  const [nickname, setNickname] = useState(dashboard.nickname);
  const [draft, setDraft] = useState(dashboard.nickname);
  const [handle, setHandle] = useState(dashboard.loginId);
  const [creatingHandle, setCreatingHandle] = useState(false);
  const [confirmLogout, setConfirmLogout] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

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
        <Button display="block" variant="weak" onClick={onGoGoal}>
          하루 목표 바꾸기
        </Button>
      </section>

      <LogoutSection
        confirm={confirmLogout}
        onConfirm={setConfirmLogout}
        onLogout={() => void logoutAndLeave(onLogout)}
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
