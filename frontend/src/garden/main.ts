import { myGarden } from './component';

// Alpine은 CDN defer — alpine:init 이벤트로 Alpine.start() 전에 Alpine.data 등록.
// Alpine CDN v3.14.x는 queueMicrotask(Alpine.start)라 defer 스크립트 배치 실행 후
// 마이크로태스크로 start하므로, 번들이 Alpine CDN보다 늦게 실행돼도 등록이 보장된다.
declare const Alpine: { data: (name: string, factory: () => object) => void };

document.addEventListener('alpine:init', () => {
    Alpine.data('myGarden', myGarden);
});
