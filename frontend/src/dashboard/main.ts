import { createApp } from 'vue'
import DashboardApp from './DashboardApp.vue'

const el = document.getElementById('dashboard-app')
if (el) {
    // 온보딩 직후 셸이 심은 data-just-onboarded="true"면 1회 환영 배너를 띄운다(§6.4).
    const justOnboarded = el.dataset.justOnboarded === 'true'
    createApp(DashboardApp, { justOnboarded }).mount(el)
}
