#!/usr/bin/env node
// 런타임 규칙 검사(앞당김 보충 §6.5 · REQ-69).
//   node app/scripts/check-fingerprint.js [--base <main의 fingerprint.lock.json>]
// ① 지금 version · iOS 지문을 app/fingerprint.lock.json과 ② 그 잠금을 main의 잠금과 대조한다.
// 성공에서만 `fingerprint ok <hash 앞 12자>`를 찍고 0, 아니면 이유를 찍고 1.
// @expo/fingerprint · @expo/config는 expo가 싣는 전이 의존성을 그대로 쓴다 — package.json에 더하지 않는다(lock 동결).
'use strict';

const fs = require('fs');
const path = require('path');

/** @param {{ base: {version:string, iosHash:string} | null, lock: {version:string, iosHash:string}, current: {version:string, iosHash:string} }} p */
function decide({ base, lock, current }) {
  const lines = [];
  if (current.iosHash !== lock.iosHash) {
    lines.push(
      `iOS 지문이 잠금과 달라요 — 잠금 ${lock.iosHash} · 지금 ${current.iosHash}`,
      `네이티브가 바뀌었으면 app.config의 version을 올리고 app/fingerprint.lock.json을 { "version": "<새 version>", "iosHash": "${current.iosHash}" }로 고쳐 주세요(새 빌드가 필요합니다)`,
    );
  }
  if (current.version !== lock.version) {
    lines.push(`app.config version(${current.version})이 잠금(${lock.version})과 달라요 — 같은 PR에서 잠금 파일을 함께 고쳐 주세요`);
  }
  if (base === null) {
    lines.push('main 잠금 없음 — main 대조 건너뜀');
  } else if (lock.iosHash !== base.iosHash && lock.version === base.version) {
    lines.push(
      `잠금 지문이 main(${base.iosHash.slice(0, 12)})과 다른데 version이 main과 같아요(${lock.version}) — version을 올리고 잠금을 고쳐 주세요. 0.1.0 바이너리에 맞지 않는 업데이트가 게시되는 것을 막습니다`,
    );
  }
  const ok = lines.every((l) => l === 'main 잠금 없음 — main 대조 건너뜀');
  if (ok) lines.push(`fingerprint ok ${current.iosHash.slice(0, 12)}`);
  return { ok, lines };
}

async function main() {
  const appRoot = path.resolve(__dirname, '..'); // 절대 경로여야 한다 — 상대 경로('.')면 expoConfig 원천 해시가 달라진다(실측 d20b4cb9)
  const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'));
  const i = process.argv.indexOf('--base');
  const base = i > 0 ? readJson(process.argv[i + 1]) : null;
  const lock = readJson(path.join(appRoot, 'fingerprint.lock.json'));

  const { createFingerprintAsync } = require('@expo/fingerprint');
  const { getConfig } = require('@expo/config');
  const fp = await createFingerprintAsync(appRoot, { platforms: ['ios'], silent: true });
  const { exp } = getConfig(appRoot, { skipSDKVersionRequirement: true });

  const r = decide({ base, lock, current: { version: exp.version, iosHash: fp.hash } });
  for (const l of r.lines) (r.ok ? console.log : console.error)(l);
  process.exit(r.ok ? 0 : 1);
}

if (require.main === module) {
  main().catch((e) => {
    console.error(e);
    process.exit(1);
  });
}

module.exports = { decide };
