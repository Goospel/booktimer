<template>
  <div class="village-shell">
    <div v-if="loadError" class="village-loading">⚠️ 마을을 불러오지 못했어요.</div>
    <div v-else-if="!data" class="village-loading">🌱 불러오는 중…</div>
    <template v-else>
      <!-- 게임 스테이지 — 풀스크린 채움 -->
      <GardenGame ref="gameRef" :data="data" />

      <!-- HUD — .village-shell 기준 절대배치 -->
      <div class="village-hud">
        <div class="village-hud-top">
          <a href="/" class="village-hud-brand">
            <span>📚</span> BookTimer
          </a>
          <span class="village-hud-greeting">{{ nickname }}님의 마을</span>
        </div>
        <div class="village-hud-actions">
          <button type="button" class="village-hud-btn" @click="gameRef?.startEdit()">✏️ 꾸미기</button>
          <button type="button" class="village-hud-btn" @click="dexOpen = true">📖 도감</button>
          <!-- 독서 리마인더 토글 (PWA L3a) -->
          <button
            v-if="pushSupported"
            type="button"
            class="village-hud-btn"
            :title="pushToggleTitle"
            @click="togglePushReminder"
          >{{ pushEnabled ? '🔔 알림 ON' : '🔕 알림 OFF' }}</button>
          <!-- 복귀 알림 토글 (PWA L3b — §50 광고성) -->
          <button
            v-if="pushSupported"
            type="button"
            class="village-hud-btn"
            :title="marketingPushToggleTitle"
            @click="toggleMarketingPush"
          >{{ marketingPushEnabled ? '📣 복귀 알림 ON' : '📭 복귀 알림 OFF' }}</button>
          <a href="/" class="village-hud-btn">← 대시보드</a>
        </div>
        <!-- iOS 미설치 안내 (N-101: iOS에서 푸시는 홈 화면 설치 전제) -->
        <div v-if="showIosInstallHint" class="village-push-hint">
          📱 iOS에서 알림을 받으려면 먼저 "공유 → 홈 화면에 추가"로 설치해 주세요.
        </div>
      </div>

      <!-- 도감 전체 오버레이 -->
      <div v-if="dexOpen" class="village-dex-overlay" @click.self="dexOpen = false">
        <div class="village-dex-panel">
          <div class="village-dex-head">
            <span>📖 도감</span>
            <button type="button" class="village-dex-close" @click="dexOpen = false">✕</button>
          </div>
          <GardenDex :catalog="data.catalog" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import GardenGame from './GardenGame.vue';
import GardenDex from './GardenDex.vue';

const data = ref<any>(null);
const loadError = ref(false);
const dexOpen = ref(false);
const gameRef = ref<any>(null);
const nickname = ref('');

// --- PWA L3a: 독서 리마인더 푸시 토글 ---
const pushEnabled = ref(false);
const pushSupported = ref(false);
const showIosInstallHint = ref(false);

const pushToggleTitle = computed(() =>
  pushEnabled.value ? '매일 독서 알림 끄기' : '매일 독서 알림 받기'
);

// --- PWA L3b: 복귀 알림(광고) 토글 ---
const marketingPushEnabled = ref(false);

const marketingPushToggleTitle = computed(() =>
  marketingPushEnabled.value
    ? '복귀 알림 끄기 (광고 수신 철회)'
    : '복귀 알림 받기 (광고 — 오랫동안 안 읽으면 알림)'
);

function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = atob(base64);
  const arr = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; i++) arr[i] = rawData.charCodeAt(i);
  return arr;
}

function getCsrfToken(): string {
  return (document.querySelector('meta[name="_csrf"]') as HTMLMetaElement | null)?.content ?? '';
}

function getVapidPublicKey(): string {
  return (document.querySelector('meta[name="vapid-public-key"]') as HTMLMetaElement | null)?.content ?? '';
}

async function initPushState() {
  // iOS 미설치 게이트 (N-101: iOS 16.4+ 및 홈 화면 설치 상태에서만 푸시 가능)
  const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !(window as any).MSStream;
  const isStandalone = (navigator as any).standalone === true;
  if (isIOS && !isStandalone) {
    showIosInstallHint.value = true;
    pushSupported.value = false;
    return;
  }

  if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
    pushSupported.value = false;
    return;
  }

  pushSupported.value = true;

  // 기존 구독 여부로 토글 초기 상태 결정
  try {
    const reg = await navigator.serviceWorker.ready;
    const sub = await reg.pushManager.getSubscription();
    pushEnabled.value = sub !== null && Notification.permission === 'granted';
  } catch {
    pushEnabled.value = false;
  }
}

async function togglePushReminder() {
  const vapidKey = getVapidPublicKey();
  if (!vapidKey || vapidKey === 'not-configured') {
    console.warn('[Push] VAPID 공개키가 설정되지 않았습니다.');
    return;
  }

  const reg = await navigator.serviceWorker.ready;

  if (!pushEnabled.value) {
    // ON: 권한 요청 → 구독 → 서버 등록
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return;

    try {
      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidKey),
      });
      const subJson = sub.toJSON();
      await fetch('/api/push/subscribe', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-TOKEN': getCsrfToken(),
        },
        body: JSON.stringify({
          endpoint: subJson.endpoint,
          p256dh: subJson.keys?.p256dh ?? '',
          auth: subJson.keys?.auth ?? '',
        }),
      });
      pushEnabled.value = true;
    } catch (e) {
      console.error('[Push] 구독 실패:', e);
    }
  } else {
    // OFF: 구독 취소 → 서버 삭제
    try {
      const sub = await reg.pushManager.getSubscription();
      if (sub) {
        await fetch('/api/push/unsubscribe', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': getCsrfToken(),
          },
          body: JSON.stringify({ endpoint: sub.endpoint }),
        });
        await sub.unsubscribe();
      }
      pushEnabled.value = false;
    } catch (e) {
      console.error('[Push] 구독 취소 실패:', e);
    }
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && dexOpen.value) dexOpen.value = false;
}

async function toggleMarketingPush() {
  if (!marketingPushEnabled.value) {
    // ON: 구독 보장 + 동의 서버 등록 (VAPID 필요)
    const vapidKey = getVapidPublicKey();
    if (!vapidKey || vapidKey === 'not-configured') {
      console.warn('[Push] VAPID 공개키가 설정되지 않았습니다.');
      return;
    }
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return;

    try {
      const reg = await navigator.serviceWorker.ready;
      let sub = await reg.pushManager.getSubscription();
      if (!sub) {
        sub = await reg.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(vapidKey),
        });
        const subJson = sub.toJSON();
        await fetch('/api/push/subscribe', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
          body: JSON.stringify({
            endpoint: subJson.endpoint,
            p256dh: subJson.keys?.p256dh ?? '',
            auth: subJson.keys?.auth ?? '',
          }),
        });
      }
      await fetch('/api/push/marketing-consent', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
        body: JSON.stringify({ enabled: true }),
      });
      marketingPushEnabled.value = true;
    } catch (e) {
      console.error('[Push] 복귀 알림 동의 실패:', e);
    }
  } else {
    // OFF: 동의만 철회 (L3a 리마인더 구독·dailyReminderEnabled는 유지)
    try {
      await fetch('/api/push/marketing-consent', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
        body: JSON.stringify({ enabled: false }),
      });
      marketingPushEnabled.value = false;
    } catch (e) {
      console.error('[Push] 복귀 알림 철회 실패:', e);
    }
  }
}

onMounted(async () => {
  const appEl = document.getElementById('village-app') as HTMLElement | null;
  nickname.value = appEl?.dataset.nickname ?? '';
  marketingPushEnabled.value = appEl?.dataset.marketingPushConsent === 'true';
  document.addEventListener('keydown', onKeydown);
  await initPushState();

  try {
    const res = await fetch('/api/garden');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    data.value = await res.json();
  } catch (e) {
    console.error('[VillageApp] API fetch 실패:', e);
    loadError.value = true;
  }
});

onUnmounted(() => document.removeEventListener('keydown', onKeydown));
</script>
