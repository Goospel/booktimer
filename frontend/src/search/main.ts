import { createApp } from 'vue';
import SearchApp from './SearchApp.vue';

const appEl = document.getElementById('search-app');
if (appEl) {
    createApp(SearchApp).mount(appEl);
}
