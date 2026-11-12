import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv, type Plugin } from 'vite';

/**
 * Configuração do Vite.
 *
 * Em desenvolvimento, `/api` é encaminhado ao backend Spring Boot para o
 * frontend usar a mesma origem (e o mesmo contrato de cookies/CSRF do
 * ambiente de produção). `/ws` precisa de `ws: true` para o upgrade do STOMP
 * do console do operador. Os cabeçalhos de segurança completos, incluindo a CSP,
 * ficam no Nginx (`nginx.conf`), que serve o build de produção.
 *
 * `--mode capacitor` gera o bundle que o Capacitor copia para o app (ADR-019).
 * Nesse modo a API precisa ser absoluta (o WebView roda em `https://localhost`)
 * e a CSP vai por `<meta>`, porque no app não existe Nginx.
 */

const NATIVE_MODE = 'capacitor';

/**
 * CSP do app empacotado.
 *
 * Espelha a política do Nginx (mesmas diretivas e origens), acrescentando a
 * origem da API/WebSocket e, quando usado, a dos tiles PMTiles. `frame-ancestors`
 * não é aplicável em `<meta>` e fica de fora.
 */
function nativeCspPlugin(apiBaseUrl: string, pmtilesUrl: string): Plugin {
  const api = new URL(apiBaseUrl);
  const connect = new Set<string>([
    "'self'",
    api.origin,
    `${api.protocol === 'https:' ? 'wss' : 'ws'}://${api.host}`,
  ]);
  if (pmtilesUrl) {
    connect.add(new URL(pmtilesUrl).origin);
  }

  const policy = [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self'",
    "img-src 'self' data: blob:",
    "font-src 'self'",
    `connect-src ${[...connect].join(' ')}`,
    'frame-src https://www.youtube-nocookie.com',
    "media-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "worker-src 'self' blob:",
    "manifest-src 'self'",
  ].join('; ');

  return {
    name: 'maraberto:native-csp',
    transformIndexHtml(html) {
      return html.replace(
        '</head>',
        `    <meta http-equiv="Content-Security-Policy" content="${policy}" />\n  </head>`,
      );
    },
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const native = mode === NATIVE_MODE;
  const apiBaseUrl = (env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

  if (native && !/^https:\/\/[^/]+/.test(apiBaseUrl)) {
    throw new Error(
      'No build do app (--mode capacitor), VITE_API_BASE_URL precisa ser uma URL absoluta https:// do backend. ' +
        'Copie `.env.capacitor.example` para `.env.capacitor` e ajuste o host.',
    );
  }

  return {
    plugins: [
      react(),
      tailwindcss(),
      native ? nativeCspPlugin(apiBaseUrl, env.VITE_PMTILES_URL ?? '') : null,
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
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
        // Console do operador: STOMP sobre WebSocket, JWT no frame CONNECT.
        '/ws': {
          target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080',
          changeOrigin: true,
          ws: true,
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
  };
});
