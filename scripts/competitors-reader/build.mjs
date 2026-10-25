// 경쟁사 md 5개를 template.html의 <!--MD--> 자리에 <script type="text/markdown">으로 넣어 읽기 화면 HTML 한 장을 만든다.
// 사용: node scripts/competitors-reader/build.mjs [출력 경로]   (기본: 임시 폴더의 competitors-reader.html)
// 만든 파일을 아티팩트로 다시 게시한다 — URL은 claude-docs/competitors/README.md 「갱신 규칙」.
import { readFileSync, writeFileSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '../..');
const out = process.argv[2] || join(tmpdir(), 'competitors-reader.html');

const ids = ['serotiny', 'booktrack', 'savequotes', 'tamsikine', 'topa'];
const blocks = ids.map((id) => {
  const md = readFileSync(join(repo, 'claude-docs/competitors', `${id}.md`), 'utf8')
    .replace(/\r\n/g, '\n')
    .replace(/<\/script/gi, '<\\/script');
  return `<script type="text/markdown" id="md-${id}">\n${md}\n</script>`;
});

// 윈도우 체크아웃(core.autocrlf)은 템플릿을 CRLF로 바꿔 놓는다 — 줄끝을 LF로 맞춰 어디서 만들어도 같은 파일이 나오게 한다.
const tpl = readFileSync(join(here, 'template.html'), 'utf8').replace(/\r\n/g, '\n');
if (!tpl.includes('<!--MD-->')) throw new Error('template.html에 <!--MD--> 자리가 없다');
// 함수로 넘긴다 — 문자열로 넘기면 md 속 `$&`·`$'` 같은 글자를 replace가 특수 패턴으로 해석한다.
writeFileSync(out, tpl.replace('<!--MD-->', () => blocks.join('\n')), 'utf8');
console.log(`ok ${ids.length} docs → ${out}`);
