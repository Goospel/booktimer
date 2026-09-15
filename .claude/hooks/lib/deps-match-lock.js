// Usage: node deps-match-lock.js <npm package dir>
// Exit 0 = installed direct dependencies match package-lock.json (or there is no lock file).
// Exit 1 = mismatch; one "  <name>: installed X != lock Y" line per package on stdout.
// Any other exit (crash) is treated as fail-open by the caller.
//
// Why a node script: PowerShell 5.1 ConvertFrom-Json cannot read package-lock.json
// (its root package key is the empty string ""). The bundle hook already requires node.
const fs = require('fs');
const path = require('path');

const dir = process.argv[2];
const lockPath = path.join(dir, 'package-lock.json');
if (!fs.existsSync(lockPath)) process.exit(0);

const pkgs = JSON.parse(fs.readFileSync(lockPath, 'utf8')).packages || {};
const root = pkgs[''] || {};
const bad = [];
for (const name of Object.keys({ ...root.dependencies, ...root.devDependencies }).sort()) {
  const want = (pkgs[`node_modules/${name}`] || {}).version;
  const pj = path.join(dir, 'node_modules', name, 'package.json');
  const got = fs.existsSync(pj) ? JSON.parse(fs.readFileSync(pj, 'utf8')).version : '(missing)';
  if (want && got !== want) bad.push(`  ${name}: installed ${got} != lock ${want}`);
}
if (bad.length) {
  console.log(bad.join('\n'));
  process.exit(1);
}
