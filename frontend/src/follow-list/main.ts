import { createApp } from 'vue';
import FollowListApp from './FollowListApp.vue';

const appEl = document.getElementById('follow-list-app');
if (appEl) {
    createApp(FollowListApp).mount(appEl);
}
