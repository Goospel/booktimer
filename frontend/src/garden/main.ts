import Alpine from 'alpinejs';
import { myGarden } from './component';

// Alpine을 번들이 직접 소유한다 — Phaser와 동일하게 npm import(CDN 제거).
// 왜: CDN defer Alpine은 그 defer 스크립트 실행 '직후' 마이크로태스크 체크포인트에서
// queueMicrotask(Alpine.start)가 돌아, 뒤이어 실행되는 type=module 번들이 alpine:init
// 리스너를 걸기 '전에' init이 끝나버린다 → x-data="myGarden" 평가 시 myGarden 미등록
// → ReferenceError로 '내 정원' 전체가 죽었다(#391 회귀). 번들이 Alpine.data 등록 후
// 직접 start()하면 순서가 코드로 보장돼(스크립트 로드순서 무관) 그 사각이 구조적으로 사라진다.
(window as unknown as { Alpine: typeof Alpine }).Alpine = Alpine;
Alpine.data('myGarden', myGarden);
Alpine.start();
