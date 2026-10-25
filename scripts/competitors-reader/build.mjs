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

const tpl = readFileSync(join(here, 'template.html'), 'utf8');
if (!tpl.includes('<!--MD-->')) throw new Error('template.html에 <!--MD--> 자리가 없다');
writeFileSync(out, tpl.replace('<!--MD-->', blocks.join('\n')), 'utf8');
console.log(`ok ${ids.length} docs → ${out}`);
