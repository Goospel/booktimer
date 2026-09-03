import { createApp } from 'vue';

import StudyApp from './StudyApp.vue';

const appEl = document.getElementById('study-app');
if (appEl) {
    createApp(StudyApp).mount(appEl);
}
