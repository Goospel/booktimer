import Alpine from 'alpinejs';
import { createApp } from 'vue';
import GardenGame from './GardenGame.vue';

// Alpine을 번들이 직접 소유한다 — CDN 제거(#391 로드순서 버그 차단).
// S2: myGarden Alpine 컴포넌트는 Vue로 이전 — Alpine은 도감 탭/필터 x-data 전용으로 축소.
(window as unknown as { Alpine: typeof Alpine }).Alpine = Alpine;
Alpine.start();

// 정원 게임 위젯 — Vue 마운트 (#village-game)
const gameEl = document.getElementById('village-game');
if (gameEl) {
    createApp(GardenGame).mount(gameEl);
}
