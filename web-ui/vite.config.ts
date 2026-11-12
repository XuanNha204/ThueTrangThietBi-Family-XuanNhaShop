import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5175,
    strictPort: true,
    allowedHosts: ['bm4mbskm-5175.asse.devtunnels.ms'],
    fs: {
      deny: ['**/.env', '**/.env.*', '**/*.{crt,pem}', '**/.git/**', '**/TaiLieu.md', '**/.dev/**'],
    },
    proxy: {
      '/api': { target: 'http://127.0.0.1:8085' },
      '/actuator': { target: 'http://127.0.0.1:8085' },
    },
  },
  build: { sourcemap: false },
});
