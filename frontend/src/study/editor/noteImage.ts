import { Node } from '@tiptap/core';

import { OWN_IMAGE_PATH } from './pure';

/** 본문에 저장되는 꼴 하나 — alt 없는 `![](/api/study/notes/images/N)`(서버 StudyNoteImage.MARKDOWN_REF와 같은 꼴). */
const OWN_MARKDOWN = /^!\[\]\((\/api\/study\/notes\/images\/[1-9]\d*)\)/;

/** 붙여넣은 `<img>`의 src를 우리 주소로 좁힌다 — 같은 출처 절대주소는 경로로, 외부·data:·blob:·쿼리·조각이 붙은 것은 null. */
export function ownImageSrc(raw: string | null): string | null {
    if (!raw) return null;
    try {
        const url = new URL(raw, window.location.origin);
        return url.origin === window.location.origin && OWN_IMAGE_PATH.test(url.pathname) && !url.search && !url.hash
            ? url.pathname : null;
    } catch {
        return null;
    }
}

/** 붙여넣은 HTML 속 남의 사진 수 — 노드 규칙이 그 img를 조용히 버리므로, 0이 아니면 화면이 말한다. */
export function foreignImageCount(html: string): number {
    if (!html.includes('<img')) return 0;
    const doc = new DOMParser().parseFromString(html, 'text/html');
    return Array.from(doc.querySelectorAll('img')).filter((img) => !ownImageSrc(img.getAttribute('src'))).length;
}

/**
 * 필기 사진 — 인라인 원자 노드, 마크다운 `![](/api/study/notes/images/N)`과 1:1.
 *
 * ⚠️ 이름이 `image`가 아니고 marked 토큰도 자기 것인 이유(2026-10-19 jsdom 실측 — 설계 §2-4):
 *  ① @tiptap/extension-paragraph의 parseMarkdown이 「image 토큰 하나뿐인 문단」을 블록으로 푼다(paragraph.ts:64-72) —
 *     토큰 이름이 noteImage면 그 특례를 타지 않아 Paragraph를 덮을 필요가 없다.
 *  ② 그 특례를 끄려고 paragraph 토큰에 확장을 하나 더 걸면 직렬화가 그 확장을 먼저 집어 모든 문단이 빈 문자열이 된다.
 *  ③ src 속성의 parseHTML이 getAttrs를 이긴다 — 여기서 정규화하지 않으면 같은 출처 절대주소가 저장되고 다음에 열 때 사라진다.
 * 삽입은 JSON 노드로만 한다 — insertContent(HTML 문자열)은 거절된 <img>를 글자 그대로 넣는다.
 * 남은 기존 결함: 우리 꼴이 아닌 `![alt](…)`가 혼자 선 줄은 지금처럼 스키마 위반(타이핑 시 RangeError)이다. UI로는 만들 수 없다
 * (친 `![`는 이스케이프된다). alt를 붙인 꼴을 저장하게 되는 날엔 Paragraph.extend + this.parent + StarterKit paragraph:false가 필요하다.
 */
export const NoteImage = Node.create({
    name: 'noteImage',
    inline: true,
    group: 'inline',
    atom: true,
    draggable: true,   // 끌어 옮기기를 ProseMirror의 노드 이동으로 — 사진 9모양 왕복에 영향 없음(실측)
    addAttributes() {
        return { src: { default: null, parseHTML: (el: HTMLElement) => ownImageSrc(el.getAttribute('src')) } };
    },
    parseHTML() {
        return [{ tag: 'img[src]', getAttrs: (el) => (ownImageSrc((el as HTMLElement).getAttribute('src')) ? null : false) }];
    },
    renderHTML({ node }) {
        return ['img', { src: node.attrs.src, alt: '필기 사진', loading: 'lazy' }];
    },
    markdownTokenName: 'noteImage',
    markdownTokenizer: {
        name: 'noteImage',
        level: 'inline',
        start: (src: string) => src.indexOf('![]('),
        tokenize: (src: string) => {
            const m = OWN_MARKDOWN.exec(src);
            return m ? { type: 'noteImage', raw: m[0], src: m[1] } : undefined;
        },
    },
    parseMarkdown: (token, h) => h.createNode('noteImage', { src: (token as { src?: string }).src }),
    renderMarkdown: (node) => `![](${node.attrs?.src ?? ''})`,
});
