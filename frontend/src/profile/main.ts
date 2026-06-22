import { createApp } from 'vue';
import ProfileApp from './ProfileApp.vue';

const el = document.getElementById('profile-app');
if (el) {
    createApp(ProfileApp).mount(el);
}
