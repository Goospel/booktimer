// @vitest-environment jsdom
// 긴 책 제목은 칩 안에서 잘린다(CSS 2줄 클램프) — 잘린 뒤에도 전문을 확인할 길이 남아야 한다.
// 그 길이 title 속성(호버 툴팁)이고, 독서·공부 두 칩이 같은 마크업이라 한쪽만 고치면 갈린다.
// 그래서 두 컴포넌트를 같은 단언으로 함께 못 박는다.
import { describe, test, expect } from 'vitest';
import { mount } from '@vue/test-utils';

import BookPickForm from './BookPickForm.vue';
import StudyTimerCard from './StudyTimerCard.vue';

/** 실측한 최장 제목(운영 계정, 57자) — 2줄로도 안 들어가 title이 유일한 확인 경로인 길이. */
const LONG = '2026 이기적 정보보안기사 필기 + 실기 올인원 - 동영상 강의 무료 + 2025년 기출문제 수록 + CBT 온라인 문제집';

const studyBook = (title: string) => ({
    id: 1, title, author: null, coverUrl: null, isbn13: null,
    readCount: 0, purchaseLink: null, totalSeconds: 0,
});

describe('책 칩 제목 — 잘려도 전문을 확인할 수 있다', () => {
    test('독서 칩이 제목 전문을 title 속성에 싣는다', () => {
        const w = mount(BookPickForm, {
            props: {
                readingBooks: [{ id: 1, title: LONG }],
                finishedBooks: [],
                wantToReadBooks: [],
                recentBookId: 1,
            },
        });
        expect(w.find('.dash-book-chip-title').attributes('title')).toBe(LONG);
    });

    test('공부 칩이 제목 전문을 title 속성에 싣는다', () => {
        const w = mount(StudyTimerCard, {
            props: {
                todaySeconds: 0,
                hasActiveSession: false,
                activeStartedAt: null,
                books: [studyBook(LONG)],
                recentBookId: 1,
            },
        });
        expect(w.find('.dash-book-chip-title').attributes('title')).toBe(LONG);
    });
});
