import { describe, it, expect } from 'vitest'
import { formatReadingTime, statusBadgeClass, coverInitial, hasCover } from './format'

// ── formatReadingTime ────────────────────────────────────────────────────────
// 현행 ProfileApp 동작(Math.floor(sec/3600)시간 Math.floor((sec%3600)/60)분)을 순수함수로
// 추출만 한다. <60초(0초 포함)는 빈 문자열 — 시간 메타를 렌더하지 않게 한다(계획 §9).
describe('formatReadingTime', () => {
    it('0초 → 빈 문자열', () => {
        expect(formatReadingTime(0)).toBe('')
    })
    it('60초 미만(59) → 빈 문자열', () => {
        expect(formatReadingTime(59)).toBe('')
    })
    it('정확히 1시간(3600) → "1시간 0분"', () => {
        expect(formatReadingTime(3600)).toBe('1시간 0분')
    })
    it('1시간 1분(3661) → "1시간 1분"', () => {
        expect(formatReadingTime(3661)).toBe('1시간 1분')
    })
    it('2시간 30분(9000) → "2시간 30분"', () => {
        expect(formatReadingTime(9000)).toBe('2시간 30분')
    })
    it('1시간 미만이지만 60초 이상(1800) → "0시간 30분" (현행 분해식 유지)', () => {
        expect(formatReadingTime(1800)).toBe('0시간 30분')
    })
    it('정확히 1분(60) → "0시간 1분"', () => {
        expect(formatReadingTime(60)).toBe('0시간 1분')
    })
})

// ── statusBadgeClass ─────────────────────────────────────────────────────────
// 서버 한글 라벨 → .shop-badge 3색 클래스. 미지의 라벨은 안전 폴백(reading).
describe('statusBadgeClass', () => {
    it('완독 → finished', () => {
        expect(statusBadgeClass('완독')).toBe('shop-badge-finished')
    })
    it('읽는 중 → reading', () => {
        expect(statusBadgeClass('읽는 중')).toBe('shop-badge-reading')
    })
    it('읽고 싶음 → want', () => {
        expect(statusBadgeClass('읽고 싶음')).toBe('shop-badge-want')
    })
    it('알 수 없는 라벨 → reading 폴백', () => {
        expect(statusBadgeClass('알수없음')).toBe('shop-badge-reading')
    })
    it('빈 문자열 → reading 폴백', () => {
        expect(statusBadgeClass('')).toBe('shop-badge-reading')
    })
})

// ── coverInitial ─────────────────────────────────────────────────────────────
// 표지 폴백 글자 — 트림 후 첫 글자. 빈 값이면 '?'(avatarInitial 재사용).
describe('coverInitial', () => {
    it('제목 첫 글자', () => {
        expect(coverInitial('데미안')).toBe('데')
    })
    it('앞 공백 트림 후 첫 글자', () => {
        expect(coverInitial('  채식주의자')).toBe('채')
    })
    it('빈 문자열 → ?', () => {
        expect(coverInitial('')).toBe('?')
    })
    it('공백만 → ?', () => {
        expect(coverInitial('   ')).toBe('?')
    })
})

// ── hasCover ─────────────────────────────────────────────────────────────────
// coverUrl null/빈문자열 → false. 실제 URL이면 true.
describe('hasCover', () => {
    it('null → false', () => {
        expect(hasCover(null)).toBe(false)
    })
    it('빈 문자열 → false', () => {
        expect(hasCover('')).toBe(false)
    })
    it('공백만 → false', () => {
        expect(hasCover('   ')).toBe(false)
    })
    it('실제 URL → true', () => {
        expect(hasCover('https://example.com/cover.jpg')).toBe(true)
    })
})
