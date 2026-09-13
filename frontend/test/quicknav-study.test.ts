// @vitest-environment jsdom
// QuickNav의 모드 세트 — 미니앱 STUDY_TABS처럼 한 컴포넌트가 mode로 타일 세트를 고른다.
// 독서 기본값 케이스의 「/study 타일 부재」는 연필 타일 삭제(사용자 승인)의 계측기다 —
// 공부로 가는 문은 히어로 토글 하나만 남는다(미니앱과 동일).
import { describe, test, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import QuickNav from '../src/dashboard/QuickNav.vue';

const hrefs = (w: ReturnType<typeof mount>) =>
    w.findAll('.dash-nav-tile').map(a => a.attributes('href'));

describe('QuickNav — 공부 모드 세트', () => {
    const study = () => mount(QuickNav, { props: { loginId: 'tester', mode: 'study' as const } });

    test('공부 서재·일정·공부 기록 세 타일이 선다 (독서 타일 3종은 사라진다)', () => {
        expect(hrefs(study())).toEqual(['/study/books', '/study', '/study/history']);
    });

    test('루트 카드에 공부 잉크(is-study)가 붙는다', () => {
        expect(study().find('.dash-nav').classes()).toContain('is-study');
    });

    test('타일마다 사전 아이콘이 하나씩 붙는다 (books·calendar·history 키 참조)', () => {
        expect(study().findAll('.dash-nav-tile svg.link-ico')).toHaveLength(3);
    });
});

describe('QuickNav — 독서 기본값 (mode 생략)', () => {
    const reading = () => mount(QuickNav, { props: { loginId: 'tester' } });

    test('독서 3타일뿐 — 「공부」 타일은 삭제됐다(문은 히어로 토글 하나)', () => {
        expect(hrefs(reading())).toEqual(['/books', '/u/tester', '/personality']);
        expect(reading().find('a[href="/study"]').exists()).toBe(false);
    });

    test('루트 카드에 is-study가 없다', () => {
        expect(reading().find('.dash-nav').classes()).not.toContain('is-study');
    });
});
