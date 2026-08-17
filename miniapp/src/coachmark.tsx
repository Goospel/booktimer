import type { CSSProperties, ReactNode } from 'react';
import { useEffect, useRef, useState } from 'react';

/**
 * 코치마크 — 처음 온 사람에게 「이 버튼이 무엇인가」를 1회 가리키는 말풍선.
 *
 * <p>이 모듈이 드는 것은 두 가지다: <b>봤다는 기록</b>(기기 로컬)과 <b>말풍선의 생김새</b>.
 * 탭바 칸을 가리키는 투어는 좌표가 탭바 기하에 묶여 있어 `App`이 들고, 여기서는 그 투어와
 * 인라인 안내가 <b>같은 얼굴</b>을 쓰도록 {@link CoachmarkBubble} 하나로 모은다.
 */

/** 스텝 이름이 곧 키다 — 스텝을 늘려도 저장 스키마가 안 바뀐다. */
const storageKey = (name: string) => `booktimer.coachmark.${name}`;

/**
 * 그 안내를 봤는가 — 기기 로컬로 족하다(서버 컬럼·마이그레이션·API 왕복이 통째로 없다).
 *
 * <p>기기를 바꾸면 한 번 더 뜨는 게 유일한 대가인데, 1회성 안내에 그 정도는 손해가 아니다.
 * 키가 <b>없으면</b> 뜨므로 이미 쓰던 사람도 새 안내를 1회 보게 된다 — 화면이 바뀐 것을
 * 모른 채 옛 자리를 찾는 일이 실제 위험이라 그쪽이 오히려 요점이다.
 */
export function coachmarkSeen(name: string): boolean {
  return localStorage.getItem(storageKey(name)) !== null;
}

/**
 * 닫힘 구독 — 인라인 안내는 <b>화면 안에</b> 살아서, 닫혀도 흐름을 이끄는 쪽이 알 길이 없다.
 *
 * <p>그 통로를 prop으로 내리면 `MainTabs` → `Home`·`Library` → 그 안쪽까지 콜백을 꿰어야 한다.
 * 기록이 이미 모듈 지역(기기 저장)이므로 알림도 같은 자리에 두는 것이 싸다. 해지 함수를 돌려준다.
 */
const listeners = new Set<() => void>();

export function onCoachmarkChange(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** 봤다고 못 박는다 — 덮개 탭·대상 누름이 모두 이 한 자리로 온다. */
export function dismissCoachmark(name: string): void {
  localStorage.setItem(storageKey(name), 'seen');
  listeners.forEach((listener) => listener());
}

/**
 * 인라인 안내의 딤 층 — 탭바(`App`의 `TAB_BAR_Z_INDEX` = 100) <b>위</b>를 덮는다.
 *
 * <p>탭바 투어는 딤을 탭바 아래 깔아 바를 밝게 남기지만, 인라인 안내는 반대가 맞다:
 * 가리킨 것은 콘텐츠 안의 버튼이고, 그때 탭바까지 밝으면 화면에 밝은 것이 둘이 된다.
 * 시트(`ui`의 200)는 이 위라 시트가 열리면 안내가 가려진다 — 그 순서가 맞다.
 */
export const INLINE_DIM_Z_INDEX = 101;

/**
 * 말풍선 — 두 줄(제목·설명)과 꼬리, 투어면 진행 점.
 *
 * <p>위치는 부르는 쪽이 `style`로 준다(탭바 투어는 `fixed`, 인라인은 대상 아래 흐름).
 * 애니메이션은 두지 않는다 — 등장 효과 하나가 표지를 재래스터화하던 실측 사고 클래스다(T-176).
 */
export function CoachmarkBubble({
  title,
  detail,
  tail,
  tailLeft = '50%',
  dots,
  style,
}: {
  title: string;
  detail: string;
  /** 꼬리 방향 — `down`은 아래(탭바 칸), `up`은 위(바로 위에 선 대상). */
  tail: 'down' | 'up';
  /** 꼬리의 가로 위치 — 탭바 칸을 가리킬 때만 넘긴다(기본은 말풍선 중앙). */
  tailLeft?: string;
  /** 투어 진행 표시 — 한 걸음짜리 안내엔 없다. */
  dots?: { index: number; total: number };
  style: CSSProperties;
}) {
  const sage = 'var(--adaptiveBlue500, #6E8A6A)';

  return (
    <div
      style={{
        padding: '14px 16px',
        borderRadius: 14,
        background: sage,
        boxShadow: '0 6px 20px rgba(0, 0, 0, 0.25)',
        textAlign: 'center',
        pointerEvents: 'none', // 말풍선은 읽는 것뿐 — 눌린 곳은 덮개(닫기)나 대상 자신이어야 한다.
        ...style,
      }}
    >
      <div style={{ color: '#FFFFFF', fontSize: 15, fontWeight: 600, lineHeight: 1.45 }}>{title}</div>
      <div style={{ color: '#FFFFFF', fontSize: 13, lineHeight: 1.45, opacity: 0.85 }}>{detail}</div>

      {dots !== undefined && (
        <div style={{ display: 'flex', gap: 5, justifyContent: 'center', marginTop: 9 }}>
          {Array.from({ length: dots.total }, (_, i) => (
            <span
              key={i}
              data-tour-dot={i === dots.index ? 'now' : 'rest'}
              style={{
                width: 5,
                height: 5,
                borderRadius: '50%',
                background: '#FFFFFF',
                opacity: i === dots.index ? 0.95 : 0.35,
              }}
            />
          ))}
        </div>
      )}

      {/* 꼬리 — 말풍선이 담는 블록이라 가로 위치만 주면 가리킬 것을 가리킨다. */}
      <div
        style={{
          position: 'absolute',
          left: tailLeft,
          marginLeft: -9,
          width: 0,
          height: 0,
          borderLeft: '9px solid transparent',
          borderRight: '9px solid transparent',
          ...(tail === 'down'
            ? { bottom: -9, borderTop: `9px solid ${sage}` }
            : { top: -9, borderBottom: `9px solid ${sage}` }),
        }}
      />
    </div>
  );
}

/**
 * 인라인 코치마크 — 콘텐츠 안의 버튼을 감싸 가리킨다.
 *
 * <p><b>마스크도, 좌표 계산도 없다</b>: 딤을 깔고 <b>대상이 스스로 그 위로 올라온다</b>
 * (`position: relative` + 층). 말풍선은 대상 바로 아래 <b>흐름에 놓인 형제</b>라 위치를
 * 레이아웃이 계산해 준다 — ref·`getBoundingClientRect`·리사이즈 관측이 통째로 없다.
 * 덤으로 가리킨 버튼이 그대로 눌려, 안내를 읽고 곧장 그 일을 할 수 있다.
 *
 * <p>{@link after}는 순서다: 앞선 안내(투어 마지막 걸음)를 보기 전에는 뜨지 않는다 —
 * 딤 두 장이 겹치면 무엇을 가리키는 안내인지 알 수 없다.
 */
export function Coachmark({
  name,
  after,
  title,
  detail,
  children,
}: {
  name: string;
  /** 이 안내를 본 뒤에만 뜬다 — 없으면 곧바로 차례다. */
  after?: string;
  title: string;
  detail: string;
  children: ReactNode;
}) {
  // 이 안내를 닫았는가 — 상태로 드는 것은 이 한 가지다(마운트 때 기기 기록에서 시작한다).
  const [closed, setClosed] = useState(() => coachmarkSeen(name));

  /**
   * 뜰 차례인가는 <b>매 렌더 파생</b>이다 — 마운트 때 한 번만 판정하면, 투어를 막 끝낸 그 순간
   * 이미 서 있던 화면(홈)에서는 차례가 온 것을 모른 채 다음 진입까지 침묵한다(실측).
   */
  const open = !closed && (after === undefined || coachmarkSeen(after));

  const close = () => {
    dismissCoachmark(name);
    setClosed(true);
  };

  const target = useRef<HTMLDivElement>(null);

  /**
   * 가리킨 것을 화면 안으로 데려온다 — 대상이 스크롤 아래에 있으면 <b>딤만 깔린 검은 화면</b>이
   * 된다(서재에 책이 쌓인 사람의 「책 추가하기」가 실제로 그랬다). 부드러운 스크롤은 쓰지 않는다(T-176).
   *
   * <p>한 번 스크롤하고 끝내면 <b>늦게 도착한 콘텐츠</b>가 대상을 다시 밀어낸다(서재의 인라인 여백
   * 박스가 실제로 그랬다 — 실측). 그래서 안내가 떠 있는 동안만 문서 크기 변화를 따라간다.
   */
  useEffect(() => {
    const el = target.current;
    if (!open || el === null) return;

    const show = () => el.scrollIntoView({ block: 'center' });
    show();
    const observer = new ResizeObserver(show);
    observer.observe(document.body);
    return () => observer.disconnect();
  }, [open]);

  if (!open) return <>{children}</>;

  return (
    <>
      {/* 덮개 자체가 닫기 버튼이다 — 아무 데나 누르면 닫힌다(요소 하나로 접근성 이름까지 얻는다). */}
      <button
        type="button"
        aria-label="안내 닫기"
        onClick={close}
        style={{
          position: 'fixed',
          inset: 0,
          zIndex: INLINE_DIM_Z_INDEX,
          border: 'none',
          padding: 0,
          background: 'rgba(0, 0, 0, 0.55)',
          cursor: 'pointer',
        }}
      />

      {/* 대상 누름도 닫기다 — 안내를 보고 곧장 그 버튼을 누른 경우, 안내는 역할을 다했다. */}
      <div ref={target} style={{ position: 'relative', zIndex: INLINE_DIM_Z_INDEX + 1 }} onClick={close}>
        {children}
      </div>

      <CoachmarkBubble
        title={title}
        detail={detail}
        tail="up"
        style={{ position: 'relative', zIndex: INLINE_DIM_Z_INDEX + 1, marginTop: 12 }}
      />
    </>
  );
}
