import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const APP = process.env.APP ?? 'garden';

const apps = {
  garden: {
    input: 'src/garden/main.ts',
    dir: '../src/main/resources/static/garden',
    entry: 'garden.js',
  },
  search: {
    input: 'src/search/main.ts',
    dir: '../src/main/resources/static/search',
    entry: 'search.js',
  },
  history: {
    input: 'src/history/main.ts',
    dir: '../src/main/resources/static/history',
    entry: 'history.js',
  },
  personality: {
    input: 'src/personality/main.ts',
    dir: '../src/main/resources/static/personality',
    entry: 'personality.js',
  },
  'follow-list': {
    input: 'src/follow-list/main.ts',
    dir: '../src/main/resources/static/follow-list',
    entry: 'follow-list.js',
  },
  'book-readers': {
    input: 'src/book-readers/main.ts',
    dir: '../src/main/resources/static/book-readers',
    entry: 'book-readers.js',
  },
  'block-list': {
    input: 'src/block-list/main.ts',
    dir: '../src/main/resources/static/block-list',
    entry: 'block-list.js',
  },
  profile: {
    input: 'src/profile/main.ts',
    dir: '../src/main/resources/static/profile',
    entry: 'profile.js',
  },
  books: {
    input: 'src/books/main.ts',
    dir: '../src/main/resources/static/books',
    entry: 'books.js',
  },
  dashboard: {
    input: 'src/dashboard/main.ts',
    dir: '../src/main/resources/static/dashboard',
    entry: 'dashboard.js',
  },
} as const;

const cfg = apps[APP as keyof typeof apps] ?? apps.garden;

export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: cfg.dir,
    emptyOutDir: true,
    chunkSizeWarningLimit: 2000,
    rollupOptions: {
      input: cfg.input,
      output: {
        entryFileNames: cfg.entry,
        assetFileNames: `${APP}.[ext]`,
        format: 'es',
        inlineDynamicImports: true,
      },
    },
  },
  test: {
    environment: 'node',
  },
});
