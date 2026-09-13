import { Extension, type Extensions } from '@tiptap/core';
import { Highlight } from '@tiptap/extension-highlight';
import { TaskItem, TaskList } from '@tiptap/extension-list';
import { Markdown } from '@tiptap/markdown';
import StarterKit from '@tiptap/starter-kit';
import { Suggestion, type SuggestionOptions } from '@tiptap/suggestion';

import { tabAction } from './pure';

/** 편집기가 슬래시 팝업에 넘겨주는 몫 — 항목 목록·렌더·선택 명령은 화면(컴포넌트)이 안다. */
export type SlashSuggestion = Omit<SuggestionOptions, 'editor'>;

/**
 * Tab · Shift+Tab — 「이걸 많이 쓴다」던 자리라 목록 밖에서도 뜻이 있게 둔다.
 *
 * <p>⚠️ 슬래시 팝업이 떠 있을 때는 팝업이 Tab을 먼저 먹어야 한다 — 그래서 SlashCommand의
 * priority를 이보다 높게 둔다(플러그인 순서가 곧 키 처리 순서다).
 */
const TabKeymap = Extension.create({
    name: 'recallTabKeymap',

    addKeyboardShortcuts() {
        const run = (shift: boolean) => (): boolean => {
            const editor = this.editor;
            const inList = editor.isActive('listItem') || editor.isActive('taskItem');
            switch (tabAction(inList, shift)) {
                case 'sink':
                    return editor.commands.sinkListItem('listItem') || editor.commands.sinkListItem('taskItem');
                case 'lift':
                    return editor.commands.liftListItem('listItem') || editor.commands.liftListItem('taskItem');
                case 'toBullet':
                    return editor.commands.toggleBulletList();
                default:
                    // 목록 밖 Shift+Tab은 넘긴다 — 키보드 사용자가 편집기를 빠져나갈 길을 막지 않는다.
                    return false;
            }
        };
        return { Tab: run(false), 'Shift-Tab': run(true) };
    },
});

/** `/`로 여는 블록 메뉴. 팝업 자체는 컴포넌트가 그린다(tippy·floating-ui를 안 넣는 이유). */
function slashCommand(slash: SlashSuggestion): Extension {
    return Extension.create({
        name: 'recallSlashCommand',
        priority: 200, // Tab을 팝업이 먼저 먹게 — TabKeymap(기본 100)보다 앞
        addProseMirrorPlugins() {
            return [Suggestion({ editor: this.editor, ...slash })];
        },
    });
}

/**
 * 백지복습 편집기의 확장 묶음.
 *
 * <p>저장 형식은 마크다운 문자열이라 <b>서버·DB가 그대로</b>다. `breaks: true`가 요점 —
 * 이게 없으면 편집기 도입 전에 평문으로 저장된 글의 줄바꿈이 열 때 통째로 한 문단이 된다.
 */
export function recallExtensions(slash: SlashSuggestion): Extensions {
    return [
        StarterKit.configure({
            // 공부 메모에 코드·링크·취소선은 쓸 일이 없다 — 툴바에 없는 것을 문법으로만 열어 두지 않는다.
            code: false,
            codeBlock: false,
            link: false,
            strike: false,
            heading: { levels: [1, 2] },
        }),
        Markdown.configure({ markedOptions: { breaks: true } }),
        Highlight,
        TaskList,
        // 확장 기본 aria-label이 영어("Task item checkbox for …")라 한국어 화면에 영어가 섞인다.
        TaskItem.configure({
            nested: true,
            a11y: { checkboxLabel: (node) => `할 일 체크박스: ${node.textContent || '내용 없음'}` },
        }),
        slashCommand(slash),
        TabKeymap,
    ];
}
