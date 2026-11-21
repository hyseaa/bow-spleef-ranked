#!/usr/bin/env node
// Spec 041: deletes the old versions of this repository's image on GHCR, keeping the most recent ones for a rollback.
// The same file is in ../api/deploy/ and ../bot/deploy/: change both.
//
//   GH_TOKEN=… OWNER=hyseaa PACKAGE=bow-spleef-ranked-api KEEP=10 DRY_RUN=true node deploy/cleanup-images.mjs
//
// Kept: the KEEP most recent tagged versions, and every untagged version created after the oldest of them minus 10
// minutes (the image and attestation of an image index pushed before spec 041 are untagged versions of the package:
// deleting them would break a kept image). The rest is deleted, or only listed with DRY_RUN=true.
import { appendFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const PARTS_WINDOW_MS = 10 * 60 * 1000;

/** @param versions [{ id, created_at, metadata: { container: { tags: [] } } }] @returns {{ keep: [], remove: [] }} */
export function planCleanup(versions, keepCount) {
  if (!Number.isInteger(keepCount) || keepCount < 1) throw new Error('KEEP must be a whole number of at least 1');
  const created = (v) => Date.parse(v.created_at);
  // A push whose image is identical to an older one adds its tag to that older version (GHCR keeps one version per
  // digest): its last update, not its creation, says how recent the image is (seen on the bot's package, 2026-11-21)
  const recent = (v) => Math.max(created(v), Date.parse(v.updated_at ?? v.created_at));
  const tags = (v) => v?.metadata?.container?.tags ?? [];
  const sorted = [...versions].sort((a, b) => recent(b) - recent(a));
  const tagged = sorted.filter((v) => tags(v).length > 0);
  if (tagged.length <= keepCount) {
    return { keep: sorted, remove: [] };
  }
  const keptTagged = tagged.slice(0, keepCount);
  const keptIds = new Set(keptTagged.map((v) => v.id));
  // the parts of a kept image index are untagged versions created with it
  const isPartOfKept = (v) => keptTagged.some((k) => Math.abs(created(v) - created(k)) <= PARTS_WINDOW_MS);
  const keep = [];
  const remove = [];
  for (const v of sorted) {
    const isKept = tags(v).length > 0 ? keptIds.has(v.id) : isPartOfKept(v);
    (isKept ? keep : remove).push(v);
  }
  return { keep, remove };
}

export function describe(v) {
  const tags = v?.metadata?.container?.tags ?? [];
  return tags.length ? tags.map((t) => t.slice(0, 12)).join(', ') : `untagged ${v.name?.slice(0, 19) ?? v.id}`;
}

async function github(method, url, token) {
  const response = await fetch(url, {
    method,
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28' },
  });
  if (response.status === 404 && method === 'GET') return null;
  if (!response.ok) throw new Error(`${method} ${url} answered ${response.status}: ${(await response.text()).slice(0, 200)}`);
  return method === 'GET' ? response.json() : null;
}

async function main() {
  const token = process.env.GH_TOKEN;
  const owner = process.env.OWNER;
  const name = process.env.PACKAGE;
  const keepCount = Number.parseInt(process.env.KEEP ?? '10', 10);
  const dryRun = process.env.DRY_RUN === 'true';
  if (!token || !owner || !name) throw new Error('GH_TOKEN, OWNER and PACKAGE are needed');
  const base = `https://api.github.com/users/${owner}/packages/container/${name}/versions`;
  const versions = [];
  for (let page = 1; ; page++) {
    const batch = await github('GET', `${base}?per_page=100&page=${page}`, token);
    if (batch === null) {
      console.log(`The package ${name} does not exist: nothing to do.`);
      return;
    }
    versions.push(...batch);
    if (batch.length < 100) break;
  }
  const { keep, remove } = planCleanup(versions, keepCount);
  const lines = [`## Clean-up of ${name}${dryRun ? ' (dry run: nothing deleted)' : ''}`, '',
    `- Versions: ${versions.length}; kept: ${keep.length} (the ${keepCount} most recent images and their parts); ${dryRun ? 'to delete' : 'deleted'}: ${remove.length}`];
  for (const v of remove) {
    if (!dryRun) await github('DELETE', `${base}/${v.id}`, token);
    lines.push(`- ${dryRun ? 'Would delete' : 'Deleted'} ${describe(v)} (${v.created_at})`);
  }
  lines.push('', '### Kept');
  for (const v of keep) lines.push(`- ${describe(v)} (created ${v.created_at}, updated ${v.updated_at ?? "?"})`);
  const summary = lines.join('\n');
  console.log(summary);
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${summary}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => {
    console.error(`::error::${e.message}`);
    process.exitCode = 1;
  });
}
