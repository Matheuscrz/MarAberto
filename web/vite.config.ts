import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

/**
 * Configuração do Vite.
 *
 * Em desenvolvimento, `/api` é encaminhado ao backend Spring Boot para o
 * frontend usar a mesma origem (e o mesmo contrato de cookies/CSRF do
 * ambiente de produção). Os cabeçalhos de segurança completos, incluindo a CSP,
 * ficam no Nginx (`nginx.conf`), que serve o build de produção.
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3000,
    strictPort: true,
    headers: {
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'DENY',
      'Referrer-Policy': 'strict-origin-when-cross-origin',
      'Permissions-Policy': 'geolocation=(self), camera=(), microphone=()',
    },
    proxy: {
      '/api': {
        target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: {
    port: 3000,
  },
  build: {
    target: 'es2022',
    sourcemap: false,
  },
});
