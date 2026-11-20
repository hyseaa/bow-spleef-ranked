// Spec 039: tests of render-env.mjs (the same file in ../api/deploy/ and ../bot/deploy/). Run: node --test deploy/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { parseManifest, manifestNames, render } from './render-env.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));

const MANIFEST = `
# a comment
.env            API_KEY            secret    required
.env            POSTGRES_DB        var       required
deploy/db/.env  POSTGRES_DB        var       required
deploy/db/.env  KEEP               var       optional
.env            API_IMAGE          workflow  required
-               SPRING_PROFILES_ACTIVE none  set by docker-compose.yml
`;

test('REQ-039-04: the manifest gives each variable its file, source and need', () => {
  const entries = parseManifest(MANIFEST);
  assert.equal(entries.length, 6);
  assert.deepEqual(entries[0], { file: '.env', name: 'API_KEY', source: 'secret', need: 'required' });
  assert.equal(entries[5].reason, 'set by docker-compose.yml');
  assert.deepEqual(manifestNames(entries), ['API_IMAGE', 'API_KEY', 'KEEP', 'POSTGRES_DB', 'SPRING_PROFILES_ACTIVE']);
});

test('REQ-039-04: a malformed manifest is refused', () => {
  assert.throws(() => parseManifest('.env api_key secret required'), /bad variable name/);
  assert.throws(() => parseManifest('.env API_KEY file required'), /bad source/);
  assert.throws(() => parseManifest('.env API_KEY secret maybe'), /bad need/);
  assert.throws(() => parseManifest('../x API_KEY secret required'), /bad file/);
  assert.throws(() => parseManifest('/etc/x API_KEY secret required'), /bad file/);
  assert.throws(() => parseManifest('- X none'), /say why/);
});

test('REQ-039-04: values are written single-quoted, each to its file, from its source', () => {
  const { files, missing, errors } = render(parseManifest(MANIFEST), {
    secrets: { API_KEY: 'a$b c#d', POSTGRES_DB: 'not this one' },
    vars: { POSTGRES_DB: 'bowspleef_ranked', API_KEY: 'not this one' },
    workflow: { API_IMAGE: 'ghcr.io/x/y:abc' },
  });
  assert.deepEqual(missing, []);
  assert.deepEqual(errors, []);
  assert.equal(files['.env'], "API_KEY='a$b c#d'\nPOSTGRES_DB='bowspleef_ranked'\nAPI_IMAGE='ghcr.io/x/y:abc'\n");
  assert.equal(files['deploy/db/.env'], "POSTGRES_DB='bowspleef_ranked'\n");
});

test('REQ-039-05: a missing required variable is named; a missing optional one is left out', () => {
  const { missing } = render(parseManifest(MANIFEST), { vars: { POSTGRES_DB: 'x' }, workflow: { API_IMAGE: 'i' } });
  assert.deepEqual(missing, ['API_KEY (secret, .env)']);
  const empty = render(parseManifest(MANIFEST), { secrets: { API_KEY: '' }, vars: { POSTGRES_DB: 'x' }, workflow: { API_IMAGE: 'i' } });
  assert.deepEqual(empty.missing, ['API_KEY (secret, .env)']);
});

test('REQ-039-04: a value with a single quote or a line break is refused', () => {
  const { errors } = render(parseManifest(MANIFEST), {
    secrets: { API_KEY: "it's" }, vars: { POSTGRES_DB: 'a\nb' }, workflow: { API_IMAGE: 'i' },
  });
  assert.equal(errors.length, 3);
});

test('REQ-039-04: Docker Compose reads a written value literally', { skip: !hasDockerCompose() }, () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'render-env-'));
  const value = 'p$ss w#rd"x\\y';
  fs.writeFileSync(path.join(dir, '.env'), render(parseManifest('.env API_KEY secret required'), { secrets: { API_KEY: value } }).files['.env']);
  fs.writeFileSync(path.join(dir, 'compose.yml'), 'services:\n  s:\n    image: busybox\n    env_file: .env\n');
  const config = JSON.parse(execFileSync('docker', ['compose', 'config', '--format', 'json'], { cwd: dir, encoding: 'utf8' }));
  // `compose config` prints a literal $ as $$ (its own escape, so that its output can be read again)
  assert.equal(config.services.s.environment.API_KEY, value.replaceAll('$', '$$$$'));
});

test('REQ-039-04, 05: the command writes the files with mode 600 and prints names only, or fails naming the missing', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'render-env-'));
  const manifest = path.join(dir, 'env.manifest');
  fs.writeFileSync(manifest, MANIFEST);
  const out = path.join(dir, 'out');
  const env = {
    ...process.env,
    SECRETS_JSON: JSON.stringify({ API_KEY: 'topsecret' }),
    VARS_JSON: JSON.stringify({ POSTGRES_DB: 'db' }),
    API_IMAGE: 'img',
  };
  const ok = spawnSync(process.execPath, [path.join(HERE, 'render-env.mjs'), manifest, out], { env, encoding: 'utf8' });
  assert.equal(ok.status, 0, ok.stderr);
  assert.ok(!ok.stdout.includes('topsecret'));
  assert.match(ok.stdout, /\.env: API_KEY, POSTGRES_DB, API_IMAGE/);
  assert.equal(fs.readFileSync(path.join(out, 'deploy/db/.env'), 'utf8'), "POSTGRES_DB='db'\n");
  if (process.platform !== 'win32') assert.equal(fs.statSync(path.join(out, '.env')).mode & 0o777, 0o600);

  const bad = spawnSync(process.execPath, [path.join(HERE, 'render-env.mjs'), manifest, path.join(dir, 'out2')],
    { env: { ...env, SECRETS_JSON: '{}' }, encoding: 'utf8' });
  assert.equal(bad.status, 1);
  assert.match(bad.stderr, /Missing in the Environment: API_KEY/);
  assert.ok(!fs.existsSync(path.join(dir, 'out2')));
});

function hasDockerCompose() {
  return spawnSync('docker', ['compose', 'version'], { encoding: 'utf8' }).status === 0;
}
