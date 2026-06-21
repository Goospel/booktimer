import { createApp } from 'vue';
import BookReadersApp from './BookReadersApp.vue';

const appEl = document.getElementById('book-readers-app');
if (appEl) {
    createApp(BookReadersApp).mount(appEl);
}
