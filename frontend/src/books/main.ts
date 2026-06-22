import { createApp } from 'vue'
import BooksApp from './BooksApp.vue'

const el = document.getElementById('books-app')
if (el) createApp(BooksApp).mount(el)
