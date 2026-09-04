import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { NAV_ICONS } from './navIcons'

// 전 페이지 하단 네비(.link-row)가 실제 쓰는 의미 단위 아이콘 키. 하나라도 빠지면 그 라벨이
// 아이콘 없이(또는 빈 SVG로) 깨져 통일이 무너진다 — 명세로서 하드코딩한다.
const REQUIRED = [
    'home', 'back', 'books', 'history', 'search', 'user', 'personality',
    'block', 'report', 'follow', 'privacy', 'quote', 'feedback', 'users', 'lock',
    'calendar',
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

    // 참조 무결성 — 각 페이지가 <NavLinks :links="[{ icon: 'X' }]"> 로 넘기는 icon 키와
    // <NavIcon name="X"> 로 직접 박는 리터럴이 모두 사전에 있어야 한다. 오타(icon: 'homee',
    // name="calendarr")는 Vue 템플릿 문자열이라 컴파일타임에 안 잡히고, NavIcon이 미지 키를
    // v-html로 빈 <g>로 그려 예외도 안 난다 — 「svg가 2개 있다」류 렌더 테스트는 그대로
    // 통과한다(존재는 행위의 증거가 아니다). 두 사용 형태를 모두 훑는다.
    it('모든 .vue의 NavIcon 아이콘 리터럴이 사전 키의 부분집합', () => {
        const root = join(dirname(fileURLToPath(import.meta.url)), '..')
        const used = new Set<string>()
        const walk = (dir: string) => {
            for (const e of readdirSync(dir, { withFileTypes: true })) {
                const p = join(dir, e.name)
                if (e.isDirectory()) walk(p)
                else if (e.name.endsWith('.vue')) {
                    const src = readFileSync(p, 'utf8')
                    for (const m of src.matchAll(/\bicon:\s*'([^']+)'/g)) used.add(m[1])
                    for (const m of src.matchAll(/<NavIcon[^>]*\sname="([^"]+)"/g)) used.add(m[1])
                }
            }
        }
        walk(root)
        const unknown = [...used].filter(k => !(k in NAV_ICONS))
        expect(unknown).toEqual([])
    })
})
