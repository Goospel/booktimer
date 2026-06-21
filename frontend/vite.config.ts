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
