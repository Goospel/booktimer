import { createApp } from 'vue';
import HistoryApp from './HistoryApp.vue';

const appEl = document.getElementById('history-app');
if (appEl) {
    createApp(HistoryApp).mount(appEl);
}
