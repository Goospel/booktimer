import { createApp } from 'vue';

import StudyApp from './StudyApp.vue';
import StudyHistoryApp from './StudyHistoryApp.vue';
import { studyView } from './pure';

const appEl = document.getElementById('study-app');
if (appEl) {
    // 같은 셸(study.html)·같은 번들, 경로로 화면을 고른다 — 서버는 라우트 한 줄만 넓혔다.
    const view = studyView(location.pathname);
    if (view === 'history') document.title = '공부 기록 — BookTimer';
    createApp(view === 'history' ? StudyHistoryApp : StudyApp).mount(appEl);
}
