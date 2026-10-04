import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const files = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], { cwd: root, encoding: 'utf8' }).split('\0').filter(Boolean);
const problems = new Set();
const secrets = [];
const publicExample = await readFile(path.join(root, '.env.example'), 'utf8');
for (const file of ['.env', 'demo-store/.env', 'demo-store/.local/fixture.json']) {
  try {
    const text = await readFile(path.join(root, file), 'utf8');
    if (file.endsWith('.json')) secrets.push(JSON.parse(text).password);
    else for (const line of text.split(/\r?\n/)) {
      const match = /^(JWT_SECRET|POSTGRES_PASSWORD|RELEASEPILOT_API_KEY)=(.+)$/.exec(line);
      // Clearly public example values are not real secrets.
      if (match && match[2] !== 'replace-me' && !publicExample.includes(`${match[1]}=${match[2]}`)) secrets.push(match[2]);
    }
  } catch (error) { if (error.code !== 'ENOENT') throw error; }
}
for (const file of files) {
  if (/(^|\/)\.env(?:$|\.)/.test(file) && !file.endsWith('.env.example')) problems.add(file);
  if (/(^|\/)(?:node_modules|dist|target|\.local)\//.test(file) || /\.(?:log|class|jar)$/.test(file)) problems.add(file);
  const text = await readFile(path.join(root, file), 'utf8');
  if (secrets.filter(Boolean).some(secret => text.includes(secret))) problems.add(file);
  if (/rp_[a-f0-9]{32}\.[A-Za-z0-9_-]{43}/.test(text)) problems.add(file);
}
if (problems.size) {
  // Only filenames: never print matched credentials or source lines.
  console.error('Review required in:', [...problems].join(', '));
  process.exitCode = 1;
} else console.log(`Repository check passed: ${files.length} tracked/new non-ignored files; no local secrets or generated artifacts detected.`);
