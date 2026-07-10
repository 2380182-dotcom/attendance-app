#!/usr/bin/env node
/**
 * Fails the build if any temporary diagnostic code has outlived its own
 * stated removal date. Every ad hoc debug marker in this codebase must be
 * written as:
 *
 *   // DIAG(2026-07-20): why this is here, and what to check before removing
 *
 * The date is a promise, not a suggestion — this script is what makes it one.
 * "Temporary" instrumentation with no enforced deadline is exactly how
 * Finding 05 (raw biometric data logged to device consoles) shipped and sat
 * in production code for the length of an entire investigation.
 */
const fs = require('fs');
const path = require('path');

const SRC_DIR = path.join(__dirname, '..', 'src');
const MARKER_RE = /DIAG\((\d{4}-\d{2}-\d{2})\)/g;
const FILE_RE = /\.(js|jsx)$/;

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, files);
    } else if (FILE_RE.test(entry.name)) {
      files.push(full);
    }
  }
  return files;
}

function main() {
  const today = new Date().toISOString().slice(0, 10);
  const files = walk(SRC_DIR);

  const active = [];
  const expired = [];

  for (const file of files) {
    const content = fs.readFileSync(file, 'utf8');
    const lines = content.split('\n');
    lines.forEach((line, idx) => {
      MARKER_RE.lastIndex = 0;
      const match = MARKER_RE.exec(line);
      if (!match) return;
      const relPath = path.relative(process.cwd(), file);
      const record = { file: relPath, lineNo: idx + 1, date: match[1], line: line.trim() };
      if (match[1] < today) {
        expired.push(record);
      } else {
        active.push(record);
      }
    });
  }

  if (active.length > 0) {
    console.log(`${active.length} active DIAG marker(s):`);
    for (const r of active) {
      console.log(`  OK    ${r.file}:${r.lineNo}  (expires ${r.date})`);
    }
  }

  if (expired.length > 0) {
    console.error(`\n${expired.length} EXPIRED DIAG marker(s) — remove the code or renew the date:`);
    for (const r of expired) {
      console.error(`  EXPIRED  ${r.file}:${r.lineNo}  (was due ${r.date})`);
      console.error(`           ${r.line}`);
    }
    console.error('\nBuild failed: temporary diagnostic code has outlived its stated removal date.');
    process.exit(1);
  }

  console.log(active.length === 0 ? 'No DIAG markers found.' : 'All DIAG markers are within their stated window.');
}

main();
