import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { NAV_ICONS } from './navIcons'

// SSR 아이콘 사전(양옆 바·바 없는 화면의 하단 .link-row)과 Vue 섬이 실제 쓰는 의미 단위 아이콘 키. 하나라도 빠지면 그 라벨이
// 아이콘 없이(또는 빈 SVG로) 깨져 통일이 무너진다 — 명세로서 하드코딩한다.
const REQUIRED = [
    'home', 'back', 'books', 'history', 'search', 'user', 'personality',
    'block', 'report', 'follow', 'privacy', 'quote', 'feedback', 'users', 'lock',
    'calendar', 'note',
]

describe('navIcons', () => {
    it('통일에 필요한 아이콘 키를 모두 보유한다', () => {
        const missing = REQUIRED.filter(k => !(k in NAV_ICONS))
        expect(missing).toEqual([])
    })

    it('모든 값이 비어있지 않은 SVG 프리미티브 문자열', () => {
        for (const [k, v] of Object.entries(NAV_ICONS)) {
            expect(v, k).toMatch(/<(path|circle|rect|line|polyline|polygon)\b/)
        }
    })

    // 두 벌 동기화 — 같은 사전이 Vue(navIcons.ts)와 SSR(nav-icons.html) 두 런타임에 물리적으로
    // 둘로 존재한다. "한쪽 고치면 반드시 다른 쪽도"는 여태 주석뿐이라, 한쪽에만 키를 넣으면
    // 그 라벨이 SSR 페이지에서만 빈 아이콘으로 조용히 샜다. 계측기로 만든다.
    it('SSR 프래그먼트가 REQUIRED 키를 전부 든다', () => {
        const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')
        const html = readFileSync(
            join(repoRoot, 'src/main/resources/templates/fragments/nav-icons.html'), 'utf8')
        const ssrKeys = new Set([...html.matchAll(/th:case="'([a-z]+)'"/g)].map(m => m[1]))
        const missing = REQUIRED.filter(k => !ssrKeys.has(k))
        expect(missing).toEqual([])
    })

})
