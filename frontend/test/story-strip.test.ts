// @vitest-environment jsdom
// StoryStrip "+ 올리기" 별도 타일 (발견 8) — 아바타에 겹치던 + 배지(story-ring-plus)를
// 맨 앞 독립 타일로 분리해 터치 타깃을 키우고 겹침을 없앤다.
// 순수 시각(52px 점선 원)은 jsdom 무의미 → 실 브라우저 게이트. 여기선 구조(별도 타일·겹침 제거)만.
import { describe, test, expect, vi, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import StoryStrip from '../src/shared/story/StoryStrip.vue';

const EMPTY_FEED = { mine: null, groups: [] };
const FEED_WITH_FRIEND = {
    mine: null,
    groups: [{
        loginId: 'friend', nickname: '친구', profileCharacterCode: null, allViewed: false,
        stories: [{ id: 1, text: 'hi', bgCode: null, bookTitle: null, bookCoverUrl: null, createdAt: '2026-07-06T00:00:00Z', viewed: false }],
    }],
};

function stubFeed(feed: unknown) {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: true, status: 200, json: async () => feed })));
}

const PROPS = { myLoginId: 'testid', myNickname: '테스터', myProfileCharacterCode: null };

afterEach(() => { vi.unstubAllGlobals(); document.body.innerHTML = ''; });

describe('StoryStrip — "+ 올리기" 별도 타일 (발견 8)', () => {
    test('맨 앞에 별도 "+올리기" 타일이 있고, 아바타 겹침 배지(story-ring-plus)는 없다', async () => {
        stubFeed(EMPTY_FEED);
        const w = mount(StoryStrip, { props: PROPS, attachTo: document.body });
        await vi.waitFor(() => expect(w.find('.story-strip').exists()).toBe(true));
        expect(w.find('.story-add-tile').exists()).toBe(true);   // 별도 타일 도입
        expect(w.find('.story-ring-plus').exists()).toBe(false); // 겹침 배지 제거
    });

    test('"+올리기" 타일이 스트립의 첫 자식이다(맨 앞)', async () => {
        stubFeed(FEED_WITH_FRIEND);
        const w = mount(StoryStrip, { props: PROPS, attachTo: document.body });
        await vi.waitFor(() => expect(w.find('.story-strip').exists()).toBe(true));
        const first = w.find('.story-strip').element.firstElementChild as HTMLElement;
        expect(first.className).toContain('story-add-tile');
    });

    test('내 스토리가 없으면 빈 내 링(story-ring-none)을 그리지 않는다(작성은 +타일로 일원화)', async () => {
        stubFeed(EMPTY_FEED);
        const w = mount(StoryStrip, { props: PROPS, attachTo: document.body });
        await vi.waitFor(() => expect(w.find('.story-strip').exists()).toBe(true));
        expect(w.find('.story-ring-none').exists()).toBe(false);
    });
});
