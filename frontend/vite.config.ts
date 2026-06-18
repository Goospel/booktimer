import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: '../src/main/resources/static/garden',
    emptyOutDir: true,
    chunkSizeWarningLimit: 2000,
    rollupOptions: {
      input: 'src/garden/main.ts',
      output: {
        entryFileNames: 'garden.js',
        assetFileNames: 'garden.[ext]',
        format: 'es',
        inlineDynamicImports: true,
      },
    },
  },
});
