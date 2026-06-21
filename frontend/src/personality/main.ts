import { createApp } from 'vue';
import PersonalityApp from './PersonalityApp.vue';

const appEl = document.getElementById('personality-app');
if (appEl) {
    createApp(PersonalityApp).mount(appEl);
}
