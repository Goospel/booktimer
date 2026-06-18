import { defineConfig } from 'vite';

export default defineConfig({
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
