import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Same-origin in dev: /api/* is forwarded to Spring Boot.
    proxy: { '/api': 'http://localhost:8080' },
  },
});
