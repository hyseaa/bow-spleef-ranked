#!/usr/bin/env node
// Spec 039 (REQ-039-04, REQ-039-05), ADR-0037: writes the .env files of the VPS from the GitHub Environment.
// The same file is in ../api/deploy/ and ../bot/deploy/: change both.
//
//   node deploy/render-env.mjs deploy/env.manifest <out dir>             (in the step "The .env files" of deploy.yml)
//   node deploy/render-env.mjs --check-workflow deploy/env.manifest .github/workflows/deploy.yml   (in the CI)
//
// The manifest lists every environment variable of the project, one per line:
//   <file> <NAME> <source> <need>      source: secret | var | workflow; need: required | optional
//   -      <NAME> none     <reason…>   documented, but never written by the deployment
// Each value comes from the environment of this process: the step "The .env files" maps each variable of the manifest
// by name, `NAME: ${{ secrets.NAME }}` for a secret, `NAME: ${{ vars.NAME }}` for a variable, and sets the `workflow`
// ones itself (the image). Never `toJSON(secrets)`: the step gets only the secrets it writes (GitHub flags a workflow
// that hands every secret to one step). `--check-workflow` checks that this mapping and the manifest agree. Values are
// written single-quoted: Docker Compose and the shell then take them literally (no `$` interpolation). Nothing is written if a
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
 * The contents of the files to write, from the values by name (the environment of the step).
 * @returns {{ files: Record<string, string>, written: Record<string, string[]>, missing: string[], errors: string[] }}
 */
export function render(entries, values) {
  const files = {};
  const written = {};
  const missing = [];
  const errors = [];
  for (const e of entries) {
    if (e.source === 'none') continue;
    const value = values[e.name];
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

const STEP = 'The .env files';

/**
 * The differences between the manifest and the `env:` of the step "The .env files" of a workflow: each secret mapped
 * from `secrets.<same name>`, each variable from `vars.<same name>`, each `workflow` one set, nothing else.
 * @returns {string[]} the problems, empty when they agree
 */
export function checkWorkflowMapping(entries, workflowText) {
  const lines = workflowText.split(/\r?\n/);
  const start = lines.findIndex(l => l.trim() === `- name: ${STEP}`);
  if (start < 0) return [`no step "${STEP}"`];
  const envAt = lines.findIndex((l, i) => i > start && /^\s+env:\s*$/.test(l));
  const next = lines.findIndex((l, i) => i > start && /^\s+- (name|uses):/.test(l));
  if (envAt < 0 || (next >= 0 && envAt > next)) return [`the step "${STEP}" has no env:`];
  const indent = lines[envAt].match(/^\s*/)[0].length;
  const mapped = new Map();
  for (let i = envAt + 1; i < lines.length; i++) {
    const l = lines[i];
    if (l.trim() === '' || l.trim().startsWith('#')) continue;
    if (l.match(/^\s*/)[0].length <= indent) break;
    const m = l.match(/^\s+([A-Z][A-Z0-9_]*):\s*(.*)$/);
    if (m) mapped.set(m[1], m[2].trim());
  }
  const problems = [];
  const deployed = new Map();
  for (const e of entries) if (e.source !== 'none') deployed.set(e.name, e.source);
  for (const [name, source] of deployed) {
    const value = mapped.get(name);
    if (value === undefined) { problems.push(`${name} (${source}) is not in the env: of "${STEP}"`); continue; }
    if (source === 'secret' && value !== `\${{ secrets.${name} }}`) problems.push(`${name} must be \${{ secrets.${name} }}`);
    if (source === 'var' && value !== `\${{ vars.${name} }}`) problems.push(`${name} must be \${{ vars.${name} }}`);
    if (source === 'workflow' && /\$\{\{\s*(secrets|vars)\./.test(value)) problems.push(`${name} is set by the workflow, not from the Environment`);
  }
  for (const [name, value] of mapped) {
    if (!deployed.has(name)) problems.push(`${name} is in the env: of "${STEP}" but not in the manifest`);
    if (/toJSON\(\s*(secrets|vars)\s*\)/.test(value)) problems.push(`${name}: never toJSON(secrets) or toJSON(vars)`);
  }
  return problems;
}

function main(args) {
  if (args[0] === '--check-workflow') {
    const [, manifestPath, workflowPath] = args;
    const problems = checkWorkflowMapping(parseManifest(fs.readFileSync(manifestPath, 'utf8')),
      fs.readFileSync(workflowPath, 'utf8'));
    for (const p of problems) console.error(`::error::${workflowPath}: ${p}`);
    if (!problems.length) console.log(`${workflowPath} maps every variable of ${manifestPath}`);
    return problems.length ? 1 : 0;
  }
  const [manifestPath, outDir] = args;
  if (!manifestPath || !outDir) {
    console.error('usage: node render-env.mjs <manifest> <out dir> | --check-workflow <manifest> <workflow>');
    return 2;
  }
  const entries = parseManifest(fs.readFileSync(manifestPath, 'utf8'));
  const { files, written, missing, errors } = render(entries, process.env);
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
