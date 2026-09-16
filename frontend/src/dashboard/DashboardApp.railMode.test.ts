// @vitest-environment jsdom
// 응답 전(loading) 구간의 양옆 바 모드 — 섬 밖 DOM(#side-rails[data-mode])을 언제 건드리는가.
//
// 인라인 부트(fragments/side-rails)는 램프 힌트까지 보고 첫 페인트 전에 모드를 세운다. 그런데 Vue가
// 마운트되자마자 저장값으로 그걸 덮으면, 응답이 올 때까지 밤 배경(body.study-lamp)에 독서 바가 선명한
// 구간이 남는다 — 실측 271ms(#1147 리뷰 M-2 후속). 독서등(syncStudyLamp)은 이미 같은 이유로 응답
// 뒤에만 쓰는데 바 모드만 빠져 있었다. 문자열 검사로는 안 잡히므로 실제로 마운트해서 본다.
import { describe, test, expect, vi, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';

import DashboardApp from './DashboardApp.vue';
import { MODE_KEY, LAMP_KEY } from './timerMode';

const railMode = () => document.getElementById('side-rails')?.getAttribute('data-mode');

/** 인라인 부트가 첫 페인트 전에 남겨 둔 DOM을 그대로 재현한다. */
function bootLeft(mode: 'reading' | 'study') {
    document.body.innerHTML = `<div id="side-rails" data-mode="${mode}"></div>`;
}

const pendingFetch = () => vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})));

let wrapper: VueWrapper | null = null;

afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    vi.unstubAllGlobals();
    localStorage.clear();
});

describe('DashboardApp — 응답 전 바 모드는 인라인 부트가 세운 값을 유지한다', () => {
    test('램프 힌트 ON + 저장값 reading → 응답 전에도 study(밤 배경에 독서 바가 선명한 구간 없음)', async () => {
        // 공부 측정 중에 왼쪽 바 「내 책장」을 들르면 저장값이 reading이 된다 — 그 뒤 로고로 홈에 온 상황.
        localStorage.setItem(MODE_KEY, 'reading');
        localStorage.setItem(LAMP_KEY, '1');
        bootLeft('study');
        pendingFetch();

        wrapper = mount(DashboardApp);
        await flushPromises();

        expect(railMode()).toBe('study');
    });

    test('램프 힌트가 없으면 응답 전 모드를 study로 밀지 않는다 — 부트가 남긴 reading 그대로(과잉 교정 방지)', async () => {
        localStorage.setItem(MODE_KEY, 'reading');
        bootLeft('reading');
        pendingFetch();

        wrapper = mount(DashboardApp);
        await flushPromises();

        expect(railMode()).toBe('reading');
    });

    test('응답이 오면(실패여도) 다시 동기화한다 — 「응답 전 보류」가 「영영 안 함」이 되지 않게', async () => {
        // 양성 대조군: 이게 없으면 syncRailMode를 통째로 지운 구현도 위 두 건을 통과한다.
        localStorage.setItem(MODE_KEY, 'reading');
        localStorage.setItem(LAMP_KEY, '1');
        bootLeft('study');
        vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('offline'))));

        wrapper = mount(DashboardApp);
        await flushPromises();

        expect(railMode()).toBe('reading');
    });
});
