import { createApp } from 'vue';
import GardenGame from './GardenGame.vue';
import GardenDex from './GardenDex.vue';

// 정원 게임 위젯 — Vue 마운트 (#village-game)
const gameEl = document.getElementById('village-game');
if (gameEl) {
    createApp(GardenGame).mount(gameEl);
}

// 도감 위젯 — Vue 마운트 (#village-dex). Alpine 제거(S3): 도감이 유일 소비처였음.
const dexEl = document.getElementById('village-dex');
if (dexEl) {
    createApp(GardenDex).mount(dexEl);
}
