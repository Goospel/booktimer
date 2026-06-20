// BookTimer Service Worker — 앱 셸 캐싱 + 푸시 알림 (L2/L3a)
// 파일명 고정 코드/스타일 자산(garden.js·app.css·pwa-install.js)은 network-first로 stale 방지.
// HTML 내비게이션·API는 network-first — SSR·인증 응답이라 캐시에 개인 데이터 담지 않음.
// 아이콘·manifest는 cache-first로 빠른 재사용.
// 버전 상수: 정적 자산 갱신 시 올려 activate에서 구 캐시 삭제.
const CACHE = 'shell-v4';

const PRECACHE_URLS = [
    '/manifest.json',
    '/css/app.css',
    '/garden/garden.js',
    '/icons/icon-192.png',
    '/icons/icon-512.png',
    '/icons/maskable-512.png',
    '/icons/apple-touch-icon.png',
];

const NETWORK_FIRST = ['/garden/garden.js', '/css/app.css', '/pwa-install.js'];

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

    // API 요청 — 캐시 금지(인증·사용자별 데이터; 개인 정보 캐시 보안 위반 방지)
    if (url.pathname.startsWith('/api/')) return;

    // 파일명 고정 자산(garden.js·app.css·pwa-install.js) — network-first(cache-first면 stale)
    if (NETWORK_FIRST.includes(url.pathname)) {
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

// --- PWA L3a: 푸시 알림 수신 ---
self.addEventListener('push', (event) => {
    let d = {};
    try { d = event.data ? event.data.json() : {}; } catch (_) {}
    event.waitUntil(
        self.registration.showNotification(d.title ?? '독서 마을', {
            body: d.body ?? '오늘도 책 한 장 읽어볼까요? 📚',
            icon: '/icons/icon-192.png',
            data: d.url ?? '/village',
        })
    );
});

// 알림 클릭 시 /village 열기(또는 페이로드의 url)
self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then((wins) => {
            const target = event.notification.data || '/village';
            const existing = wins.find((w) => new URL(w.url).pathname === target);
            if (existing) return existing.focus();
            return clients.openWindow(target);
        })
    );
});
