import { createApp } from 'vue'
import DashboardApp from './DashboardApp.vue'

const el = document.getElementById('dashboard-app')
if (el) createApp(DashboardApp).mount(el)
