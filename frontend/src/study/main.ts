import { createApp } from 'vue';

import StudyApp from './StudyApp.vue';
import StudyBooksApp from './StudyBooksApp.vue';
import StudyHistoryApp from './StudyHistoryApp.vue';
import { studyView } from './pure';

// 같은 셸(study.html)·같은 번들, 경로로 화면을 고른다 — 서버는 라우트 한 줄만 넓혔다.
const VIEWS = {
    calendar: [StudyApp, null],
    history: [StudyHistoryApp, '공부 기록 — BookTimer'],
    books: [StudyBooksApp, '공부 서재 — BookTimer'],
} as const;

const appEl = document.getElementById('study-app');
if (appEl) {
    const [Root, title] = VIEWS[studyView(location.pathname)];
    if (title) document.title = title;
    createApp(Root).mount(appEl);
}
