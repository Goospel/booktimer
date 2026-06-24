// BookTimer Service Worker — 앱 셸 캐싱 + 푸시 알림 (L2/L3a)
// 정적 자산(garden.js·app.css·pwa-install.js)은 Spring resource chain으로 내용 해시 URL을 가진다.
// 해시 URL은 내용이 바뀌면 URL이 달라지므로 cache-first에서도 stale이 불가 → NETWORK_FIRST 졸업.
// 아이콘·manifest는 cache-first로 빠른 재사용.
// HTML 내비게이션·API는 network-first — SSR·인증 응답이라 캐시에 개인 데이터 담지 않음.
// 내비게이션은 navigationPreload로 SW 부팅과 네트워크 요청을 병렬화(콜드스타트 지연 제거).
// 버전 상수: 기존 캐시 무효화용(activate에서 구 캐시 삭제).
// v6: 라우트 충돌(#450) 시기에 번들 500이 cache-first로 영구 캐싱된 shell-v5를 purge.
// v7: start_url 오기(/dashboard → 매핑 없음 → 콜드 런치 404) 수정 시, 구 manifest.json 캐시를 purge.
const CACHE = 'shell-v7';

// 아이콘·manifest만 프리캐시 — 해시 자산(app.css·garden.js·pwa-install.js)은
// 빌드 타임에 URL을 모르므로 프리캐시 대신 첫 요청 시 cache-first로 자동 캐시된다.
const PRECACHE_URLS = [
    '/manifest.json',
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
    event.waitUntil((async () => {
        // navigationPreload — 브라우저가 SW 부팅과 내비게이션 네트워크 요청을 병렬화(콜드스타트 지연 제거).
        // 미지원 브라우저(구형 iOS Safari 등)는 navigationPreload가 없어 자동 스킵 → fetch 폴백.
        if (self.registration.navigationPreload) {
            await self.registration.navigationPreload.enable();
        }
        const keys = await caches.keys();
        await Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)));
        await clients.claim();
    })());
});

self.addEventListener('fetch', (event) => {
    const { request } = event;
    const url = new URL(request.url);

    // 다른 오리진 요청(광고·CDN 등)은 그대로 통과
    if (url.origin !== self.location.origin) return;

    // HTML 내비게이션 — network-first(SSR·인증 콘텐츠; 실패 시 캐시 폴백)
    // navigationPreload가 켜져 있으면 브라우저가 SW 부팅과 병렬로 미리 받은 응답(event.preloadResponse)을 사용.
    // preloadResponse는 오프라인 등 네트워크 실패 시 reject되므로 try/catch로 감싸 fetch→cache 폴백을 보존한다.
    if (request.mode === 'navigate') {
        event.respondWith((async () => {
            try {
                const preload = await event.preloadResponse;
                if (preload) return preload;
            } catch (_) { /* preload 실패(오프라인 등) → 아래 네트워크/캐시 폴백으로 */ }
            return fetch(request).catch(() => caches.match(request));
        })());
        return;
    }

    // API 요청 — 캐시 금지(인증·사용자별 데이터; 개인 정보 캐시 보안 위반 방지)
    if (url.pathname.startsWith('/api/')) return;

    // 정적 자산 — cache-first(해시 URL이라 stale 불가; 아이콘·manifest·app.css·garden.js 공통)
    event.respondWith(
        caches.match(request).then((cached) => {
            if (cached) return cached;
            return fetch(request).then((res) => {
                // 2xx만 캐싱 — 500/503 등 에러 응답을 캐싱하면 cache-first가 그걸 영구 서빙해
                // 서버를 고쳐도 stale 에러가 남는다(#450: 번들 /books/books-<hash>.js 500이 캐싱된 사례).
                if (res.ok) {
                    const clone = res.clone();
                    caches.open(CACHE).then((c) => c.put(request, clone));
                }
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
