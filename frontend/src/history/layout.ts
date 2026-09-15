// 기록 카드 반응형 레이아웃 판단 — 순수함수(DOM 비의존).
// 좁으면 'stacked'(pill 탭 2개 + 선택 패널 하나), 임계폭 이상이면 'split'(좌 일자별 / 우 빠뜨린날 2단 grid).
//
// SPLIT_MIN_WIDTH 1100 = app.css `body.has-rails` 여백 248을 뺀 852가 2단(1.45fr/1fr)이 서는 최소 폭
// (app.css 「본문 폭(바 있는 화면)」 절의 데스크톱 경계와 같은 수). 컨테이너 940은 CSS @media가 아니라
// `body.history-wide`(HistoryApp이 JS로 토글, app.css 「독서 기록 반응형」)가 켠다.

export const SPLIT_MIN_WIDTH = 1100;

export type RecordsLayout = 'stacked' | 'split';

export function chooseLayout(width: number): RecordsLayout {
    return width >= SPLIT_MIN_WIDTH ? 'split' : 'stacked';
}
