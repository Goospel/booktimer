import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * 제품 UI에 <b>기본 이모지를 쓰지 않는다</b>(2026-08-18 사용자 지정 · 미니앱 `no-emoji.test.ts`의 웹 이식).
 *
 * <p>불쾌감의 원인은 이모지라는 형식이 아니라 <b>만들지 않고 집어왔다</b>는 표식이다 — 상용 앱은 컨셉에
 * 맞는 아이콘을 자체 제작하는데, 흔한 기본 이모지가 붙어 있으면 "AI가 만든 화면"으로 읽히고 사람들은 그
 * 인상을 불쾌하게 여긴다. 기능이 멀쩡해도 그 표식 하나로 신뢰가 깎인다.
 *
 * <p>미니앱은 2026-08-18에 걷어냈는데 <b>웹은 그대로 남아</b> 61줄이 살아 있었다. 한 제품의 두 화면이
 * 다른 규칙 아래 있으면 규칙이 아니라 그때그때의 취향이 된다 — 그래서 같은 가드를 여기에도 세운다.
 *
 * <p>대안(자체 아이콘 세트 제작)을 택하지 않은 이유는 미니앱과 같다: 만드는 비용이 아니라 <b>유지</b>
 * 비용이다. 새 기능마다 같은 선 굵기·광학 크기로 계속 그려야 하고, 그 규율이 끊기면 어정쩡한 자체
 * 아이콘이 기본 이모지보다 더 아마추어로 보인다. 이 서비스는 활자가 주인공이라(종이톤 + 손글씨체)
 * 아이콘을 덜어내는 쪽이 컨셉과 같은 방향이기도 하다.
 */

/** 이모지로 읽히는 코드포인트대 — `⏱`(U+23F1)·`✅`(U+2705)처럼 기호대에 섞여 사는 것들도 함께 판다. */
const EMOJI = /[\u{1F300}-\u{1FAFF}\u{1F000}-\u{1F2FF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{23E9}-\u{23FA}\u{FE0F}]/u;

/**
 * 이 규칙에서 빼는 문자 — 미니앱 `ALLOWED`와 같은 목록이다(두 화면이 갈리면 규칙이 아니게 된다).
 *
 * <p>`✕`(U+2715)는 기호대에 살아 정규식에 걸리지만 <b>이모지가 아니다</b> — 닫기 표식이라 AI 표식으로
 * 읽히지 않는다. `🌱🌿`는 「정원·마을」이라는 이 서비스의 핵심 은유라 화면들이 직접 쓴다
 * (`BooksApp.vue`·`TimerCard.vue`·`VillageApp.vue`·`DexDetailSheet.vue`) — 서버 `GrowthStage`가
 * 근거이던 시절도 있었으나 그 사다리는 2026-08-29에 폐기됐고, 남은 근거는 이 실사용이다.
 * 여기는 지우는 게 아니라 <b>자체 일러스트로 제대로 만드는</b> 자리라 백로그로 남겼다(plan.md).
 */
const ALLOWED = new Set(['✕', '🌱', '🌿']);

/** 개발자만 보는 텍스트는 대상이 아니다 — 테스트·목·픽스처는 제품 UI가 아니다. */
const SKIP_FILES = /\.test\.(ts|tsx)$|^dev-mock\.ts$/;

const HERE = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const RESOURCES = join(HERE, '..', '..', 'src', 'main', 'resources');
const STATIC = join(RESOURCES, 'static');

/**
 * 스캔 뿌리 — Vue 섬 소스 · Thymeleaf 템플릿 · <b>손으로 쓴 정적 스크립트</b>.
 *
 * <p>세 번째가 뒤늦게 붙었다. 처음엔 앞의 둘만 봤는데, 실제 화면을 열어 DOM을 훑자
 * `pwa-install.js`가 만드는 「홈 화면에 추가」 칩에 이모지가 남아 있었다 — <b>가드가 통과시킨 채로</b>다.
 * 소스 스캔 가드의 한계는 언제나 <b>스캔 범위</b>이고, 범위 밖은 조용히 사각이 된다.
 *
 * <p>`static/<섬>/*.js`(빌드 산출물 번들)는 넣지 않는다 — 소스를 고치면 따라오는 파생물이라
 * 같은 자리를 두 번 신고하게 된다. 그래서 `static` 루트의 파일과 `static/js/`만 집는다.
 */
const ROOTS = [HERE, join(RESOURCES, 'templates'), join(STATIC, 'js')];

function sourceFiles(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const full = join(dir, entry.name);
        if (entry.isDirectory()) return sourceFiles(full);
        if (!/\.(vue|ts|html|js)$/.test(entry.name) || SKIP_FILES.test(entry.name)) return [];
        return [full];
    });
}

/** `static` 루트에 바로 놓인 스크립트(`pwa-install.js`·`sw.js`) — 하위 번들 폴더로는 내려가지 않는다. */
function staticRootScripts(): string[] {
    return readdirSync(STATIC, { withFileTypes: true })
        .filter((entry) => entry.isFile() && entry.name.endsWith('.js'))
        .map((entry) => join(STATIC, entry.name));
}

/**
 * 주석을 걷어낸 줄 — 주석의 `⚠️`·`✅`는 개발자용이라 규칙 밖이다.
 *
 * <p>`//`·`*`·`/*`에 더해 <b>HTML 주석(`<!--`)</b>까지 본다. Vue 템플릿과 Thymeleaf는 설명을 거기 담기
 * 때문이다. 완벽한 파서가 아니라 <b>줄머리 판정</b>이라, 여러 줄 HTML 주석의 <b>이어지는 줄</b>은
 * 코드로 오판한다(JS 블록 주석은 이 레포가 ` * ` 이음줄을 붙여 써서 걸러지지만 HTML 주석엔 그 표식이
 * 없다). 즉 <b>엄격한 쪽</b>으로 틀리는데, 그래도 파서를 키우지 않는다 — 걸린 주석의 이모지는 「주의:」
 * 같은 글자로 바꾸면 그만이고(landing.html에서 실제로 그렇게 했다), 그 대가로 이 함수가 열 줄에 머문다.
 */
function codeOnly(line: string): string {
    const trimmed = line.trimStart();
    if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*') || trimmed.startsWith('<!--')) {
        return '';
    }
    return line.split('//')[0];
}

describe('제품 UI에 기본 이모지가 없다', () => {
    const files = [...ROOTS.flatMap(sourceFiles), ...staticRootScripts()];

    it('스캔 대상 파일을 실제로 찾았다 — 0개면 늘 통과하는 빈 가드다', () => {
        expect(files.length).toBeGreaterThan(30);
    });

    it('세 갈래를 모두 훑는다 — 한 갈래라도 빠지면 그쪽이 조용히 사각이 된다', () => {
        expect(files.some((f) => f.endsWith('.vue'))).toBe(true);
        expect(files.some((f) => f.endsWith('.html'))).toBe(true);
        // 실제로 여기가 비어 있어서 `pwa-install.js`의 이모지가 가드를 통과했다.
        expect(files.some((f) => f.endsWith('pwa-install.js'))).toBe(true);
    });

    it('허용 목록 밖의 이모지가 소스에 남아 있지 않다', () => {
        const hits: string[] = [];

        for (const file of files) {
            readFileSync(file, 'utf8')
                .split('\n')
                .forEach((line, index) => {
                    for (const char of codeOnly(line)) {
                        if (EMOJI.test(char) && !ALLOWED.has(char)) {
                            hits.push(`${file.split(/[\\/]/).slice(-2).join('/')}:${index + 1}  ${char}  ${line.trim().slice(0, 60)}`);
                        }
                    }
                });
        }

        expect(hits).toEqual([]);
    });

    it('가드가 살아 있다 — 이모지를 넣으면 잡힌다(돌연변이 사살)', () => {
        expect(EMOJI.test('🔍')).toBe(true);
        expect(EMOJI.test('✅')).toBe(true);
        expect(EMOJI.test('⏱')).toBe(true);
        expect(EMOJI.test('🌍')).toBe(true);
        // 걸러야 할 것들 — 한글·기호·문장부호는 이모지가 아니다.
        expect(EMOJI.test('책')).toBe(false);
        expect(EMOJI.test('›')).toBe(false);
        expect(EMOJI.test('·')).toBe(false);
        expect(EMOJI.test('—')).toBe(false);
    });
});
