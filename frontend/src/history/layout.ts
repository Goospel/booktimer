// 기록 카드 반응형 레이아웃 판단 — 순수함수(DOM 비의존).
// 좁으면 'stacked'(pill 탭 2개 + 선택 패널 하나), 임계폭 이상이면 'split'(좌 일자별 / 우 빠뜨린날 2단 grid).
//
// ⚠️ SPLIT_MIN_WIDTH는 app.css의 `@media (min-width: 880px) { body.history-page .container }`
//    컨테이너 확장 브레이크포인트와 **반드시 동일**해야 한다 — split이 뜰 때 컨테이너도 넓어져야
//    2단이 안 찌그러진다(둘이 어긋나면 좁은 컨테이너에 2단이 끼거나 넓은데 한 단). (값 바꾸면 양쪽 같이)

export const SPLIT_MIN_WIDTH = 880;

export type RecordsLayout = 'stacked' | 'split';

export function chooseLayout(width: number): RecordsLayout {
    return width >= SPLIT_MIN_WIDTH ? 'split' : 'stacked';
}
