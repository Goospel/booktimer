import { createApp } from 'vue';
import BlockListApp from './BlockListApp.vue';

const el = document.getElementById('block-list-app');
if (el) createApp(BlockListApp).mount(el);
