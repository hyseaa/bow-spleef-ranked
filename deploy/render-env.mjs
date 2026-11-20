#!/usr/bin/env node
// Spec 039 (REQ-039-04, REQ-039-05), ADR-0037: writes the .env files of the VPS from the GitHub Environment.
// The same file is in ../api/deploy/ and ../bot/deploy/: change both.
//
//   SECRETS_JSON='${{ toJSON(secrets) }}' VARS_JSON='${{ toJSON(vars) }}' node deploy/render-env.mjs deploy/env.manifest <out dir>
//
// The manifest lists every environment variable of the project, one per line:
//   <file> <NAME> <source> <need>      source: secret | var | workflow; need: required | optional
//   -      <NAME> none     <reason…>   documented, but never written by the deployment
// A `workflow` value comes from the environment of this process (the image the workflow deploys). Values are written
// single-quoted: Docker Compose and the shell then take them literally (no `$` interpolation). Nothing is written if a
// required variable is missing or a value cannot be written; the names are printed, never the values.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SOURCES = new Set(['secret', 'var', 'workflow', 'none']);
const NEEDS = new Set(['required', 'optional']);

/** The entries of a manifest; throws on a malformed line. */
export function parseManifest(text) {
  const entries = [];
  text.split(/\r?\n/).forEach((raw, i) => {
    const line = raw.trim();
    if (line === '' || line.startsWith('#')) return;
    const [file, name, source, need, ...rest] = line.split(/\s+/);
    const where = `line ${i + 1}`;
    if (!/^[A-Z][A-Z0-9_]*$/.test(name ?? '')) throw new Error(`${where}: bad variable name "${name}"`);
    if (!SOURCES.has(source)) throw new Error(`${where}: bad source "${source}" (secret, var, workflow, none)`);
    if (source === 'none') {
      if (file !== '-') throw new Error(`${where}: a variable that is not deployed has "-" as its file`);
      const reason = [need, ...rest].filter(Boolean).join(' ');
      if (!reason) throw new Error(`${where}: say why ${name} is not deployed`);
      entries.push({ file: null, name, source, need: null, reason });
      return;
    }
    if (!file || file === '-' || file.startsWith('/') || file.split('/').includes('..')) {
      throw new Error(`${where}: bad file "${file}"`);
    }
    if (!NEEDS.has(need)) throw new Error(`${where}: bad need "${need}" (required, optional)`);
    if (rest.length) throw new Error(`${where}: unexpected "${rest.join(' ')}"`);
    entries.push({ file, name, source, need });
  });
  return entries;
}

/** Every name of a manifest, deployed or not (what `tools/check-docs.mjs` compares with the documentation). */
export function manifestNames(entries) {
  return [...new Set(entries.map(e => e.name))].sort();
}

/**
 * The contents of the files to write, from the values of the Environment.
 * @returns {{ files: Record<string, string>, written: Record<string, string[]>, missing: string[], errors: string[] }}
 */
export function render(entries, { secrets = {}, vars = {}, workflow = {} }) {
  const files = {};
  const written = {};
  const missing = [];
  const errors = [];
  for (const e of entries) {
    if (e.source === 'none') continue;
    const from = e.source === 'secret' ? secrets : e.source === 'var' ? vars : workflow;
    const value = from[e.name];
    if (value === undefined || value === null || value === '') {
      if (e.need === 'required') missing.push(`${e.name} (${e.source}, ${e.file})`);
      continue;
    }
    const text = String(value);
    if (text.includes("'") || /[\r\n]/.test(text)) {
      errors.push(`${e.name}: a value with a single quote or a line break cannot be written to ${e.file}`);
      continue;
    }
    files[e.file] = (files[e.file] ?? '') + `${e.name}='${text}'\n`;
    (written[e.file] ??= []).push(e.name);
  }
  return { files, written, missing, errors };
}

function parseJsonEnv(name) {
  const raw = process.env[name];
  if (!raw) return {};
  try {
    return JSON.parse(raw);
  } catch {
    throw new Error(`${name} is not JSON`);
  }
}

function main([manifestPath, outDir]) {
  if (!manifestPath || !outDir) {
    console.error('usage: node render-env.mjs <manifest> <out dir>');
    return 2;
  }
  const entries = parseManifest(fs.readFileSync(manifestPath, 'utf8'));
  const workflow = Object.fromEntries(
    entries.filter(e => e.source === 'workflow').map(e => [e.name, process.env[e.name]]));
  const { files, written, missing, errors } = render(entries, {
    secrets: parseJsonEnv('SECRETS_JSON'), vars: parseJsonEnv('VARS_JSON'), workflow,
  });
  if (missing.length || errors.length) {
    for (const m of missing) console.error(`::error::Missing in the Environment: ${m}`);
    for (const m of errors) console.error(`::error::${m}`);
    return 1;
  }
  for (const [file, content] of Object.entries(files)) {
    const target = path.join(outDir, file);
    fs.mkdirSync(path.dirname(target), { recursive: true, mode: 0o700 });
    fs.writeFileSync(target, content, { mode: 0o600 });
    console.log(`${file}: ${written[file].join(', ')}`);
  }
  return 0;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (err) {
    console.error(`::error::${err.message}`);
    process.exitCode = 1;
  }
}
