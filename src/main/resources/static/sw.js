// BookTimer Service Worker — 앱 셸 캐싱 (L2)
// garden.js는 파일명 고정(해시 없음)이라 network-first로 stale 방지.
// HTML 내비게이션도 network-first — SSR·인증 응답이라 캐시에 개인 데이터 담지 않음.
// 정적 자산(CSS·아이콘·manifest)은 cache-first로 빠른 재사용.
// 버전 상수: 정적 자산 갱신 시 올려 activate에서 구 캐시 삭제.
const CACHE = 'shell-v1';

const PRECACHE_URLS = [
    '/manifest.json',
    '/css/app.css',
    '/garden/garden.js',
    '/icons/icon-192.png',
    '/icons/icon-512.png',
    '/icons/maskable-512.png',
    '/icons/apple-touch-icon.png',
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE).then((cache) => cache.addAll(PRECACHE_URLS))
    );
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) =>
            Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
        )
    );
    clients.claim();
});

self.addEventListener('fetch', (event) => {
    const { request } = event;
    const url = new URL(request.url);

    // 다른 오리진 요청(광고·CDN 등)은 그대로 통과
    if (url.origin !== self.location.origin) return;

    // HTML 내비게이션 — network-first(SSR·인증 콘텐츠; 실패 시 캐시 폴백)
    if (request.mode === 'navigate') {
        event.respondWith(
            fetch(request).catch(() => caches.match(request))
        );
        return;
    }

    // garden.js — network-first(파일명 고정이라 cache-first면 stale)
    if (url.pathname === '/garden/garden.js') {
        event.respondWith(
            fetch(request)
                .then((res) => {
                    const clone = res.clone();
                    caches.open(CACHE).then((c) => c.put(request, clone));
                    return res;
                })
                .catch(() => caches.match(request))
        );
        return;
    }

    // 정적 자산 — cache-first(CSS·아이콘·manifest)
    event.respondWith(
        caches.match(request).then((cached) => {
            if (cached) return cached;
            return fetch(request).then((res) => {
                const clone = res.clone();
                caches.open(CACHE).then((c) => c.put(request, clone));
                return res;
            });
        })
    );
});
