import { createApp } from 'vue';
import VillageApp from './VillageApp.vue';

const appEl = document.getElementById('village-app');
if (appEl) {
    createApp(VillageApp).mount(appEl);
}
