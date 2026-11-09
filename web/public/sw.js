/*
 * Service worker do MarAberto (manual, versionado).
 *
 * Regras de segurança e de produto:
 * - NUNCA cachear `/api/**` (SOS, autenticação e dados operacionais precisam
 *   ser sempre frescos) nem qualquer requisição não-GET.
 * - NUNCA cachear `/tiles/**` (PMTiles do ADR-005) nem tráfego de WebSocket.
 * - NUNCA cachear requisições com `Range` (vídeo/stream).
 * - Navegação: network-first com fallback para o shell em cache e, por fim,
 *   para `/offline.html`, que orienta a ligar 193.
 * - Assets com hash (`/assets/`) e ícones: stale-while-revalidate.
 * - O SOS não funciona offline; o cache existe só para leitura de conteúdo.
 *
 * Ao mudar o shell, incremente CACHE_VERSION para descartar caches antigos.
 */
const CACHE_VERSION = 'maraberto-shell-v2-map';
const OFFLINE_URL = '/offline.html';
const PRECACHE_URLS = [
  '/',
  OFFLINE_URL,
  '/manifest.webmanifest',
  '/favicon.svg',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    (async () => {
      const cache = await caches.open(CACHE_VERSION);
      await cache.addAll(
        PRECACHE_URLS.map((url) => new Request(url, { cache: 'reload' })),
      );
      await self.skipWaiting();
    })(),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(
        keys.filter((key) => key !== CACHE_VERSION).map((key) => caches.delete(key)),
      );
      await self.clients.claim();
    })(),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;

  if (request.method !== 'GET') {
    return;
  }
  if (request.headers.has('range')) {
    return;
  }

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) {
    return;
  }
  if (
    url.pathname.startsWith('/api/') ||
    url.pathname.startsWith('/actuator/') ||
    url.pathname.startsWith('/tiles/') ||
    url.pathname === '/ws'
  ) {
    return;
  }

  if (request.mode === 'navigate') {
    event.respondWith(networkFirstNavigation(request));
    return;
  }

  if (url.pathname.startsWith('/assets/') || url.pathname.startsWith('/icons/')) {
    event.respondWith(staleWhileRevalidate(request, event));
    return;
  }

  event.respondWith(networkFirst(request, event));
});

async function networkFirstNavigation(request) {
  const cache = await caches.open(CACHE_VERSION);
  try {
    const response = await fetch(request);
    if (response.ok && (response.headers.get('content-type') || '').includes('text/html')) {
      // Todas as rotas devolvem o mesmo index.html; guardar em '/' é suficiente.
      await cache.put('/', response.clone());
    }
    return response;
  } catch {
    const cachedShell = await cache.match('/');
    if (cachedShell) {
      return cachedShell;
    }
    const offline = await cache.match(OFFLINE_URL);
    if (offline) {
      return offline;
    }
    return new Response('Você está offline. Em emergência, ligue 193.', {
      status: 503,
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
    });
  }
}

async function staleWhileRevalidate(request, event) {
  const cache = await caches.open(CACHE_VERSION);
  const cached = await cache.match(request);

  const network = fetch(request)
    .then((response) => {
      if (response.ok) {
        return cache.put(request, response.clone()).then(() => response);
      }
      return response;
    })
    .catch(() => cached);

  if (cached) {
    event.waitUntil(network);
    return cached;
  }
  return network;
}

async function networkFirst(request, event) {
  const cache = await caches.open(CACHE_VERSION);
  try {
    const response = await fetch(request);
    if (response.ok) {
      event.waitUntil(cache.put(request, response.clone()));
    }
    return response;
  } catch {
    const cached = await cache.match(request);
    if (cached) {
      return cached;
    }
    return new Response('', { status: 503, statusText: 'Offline' });
  }
}
