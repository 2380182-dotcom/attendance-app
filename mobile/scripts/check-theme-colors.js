#!/usr/bin/env node
/**
 * Verification script: greps a screen file (or every screen, if no path given)
 * for `colors.X` references and checks each against the real keys defined in
 * theme.js's color palettes. Catches dangling references like the `colors.action`
 * bug found during the Dawn Bread redesign — where a key gets removed/renamed in
 * theme.js but a screen still references the old name, silently resolving to
 * `undefined` at runtime instead of failing to build.
 *
 * Also cross-checks that lightColors and darkColors define the exact same set
 * of keys, so a color that exists in light mode but not dark mode (or vice
 * versa) gets caught too.
 *
 * Usage:
 *   node scripts/check-theme-colors.js                       # scan all screens
 *   node scripts/check-theme-colors.js src/screens/x/Y.js     # scan one file
 */
const fs = require('fs');
const path = require('path');

const THEME_PATH = path.join(__dirname, '../src/theme.js');
const SCREENS_DIR = path.join(__dirname, '../src/screens');
const COMPONENTS_DIR = path.join(__dirname, '../src/components');

function extractObjectKeys(themeSrc, constName) {
  const re = new RegExp(`const ${constName} = \\{([\\s\\S]*?)\\n\\};`);
  const match = themeSrc.match(re);
  if (!match) {
    throw new Error(`Could not locate ${constName} object in theme.js`);
  }
  const body = match[1];
  const keys = new Set();
  const keyRegex = /^\s*([a-zA-Z0-9]+):/gm;
  let m;
  while ((m = keyRegex.exec(body))) {
    keys.add(m[1]);
  }
  return keys;
}

function getPaletteKeys() {
  const themeSrc = fs.readFileSync(THEME_PATH, 'utf8');
  const light = extractObjectKeys(themeSrc, 'lightColors');
  const dark = extractObjectKeys(themeSrc, 'darkColors');
  return { light, dark };
}

function findJsFiles(dir, files = []) {
  if (!fs.existsSync(dir)) return files;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) findJsFiles(full, files);
    else if (entry.name.endsWith('.js')) files.push(full);
  }
  return files;
}

function checkFile(filePath, validKeys) {
  const src = fs.readFileSync(filePath, 'utf8');
  const refs = new Set();
  const refRegex = /\bcolors\.([a-zA-Z0-9]+)/g;
  let m;
  while ((m = refRegex.exec(src))) {
    refs.add(m[1]);
  }
  const invalid = [...refs].filter((k) => !validKeys.has(k));
  return { refs: [...refs].sort(), invalid };
}

function main() {
  const { light, dark } = getPaletteKeys();

  // Cross-check light vs dark palettes define the same keys.
  const lightOnly = [...light].filter((k) => !dark.has(k));
  const darkOnly = [...dark].filter((k) => !light.has(k));
  if (lightOnly.length || darkOnly.length) {
    console.log('WARNING: lightColors and darkColors have mismatched keys.');
    if (lightOnly.length) console.log('  Only in lightColors:', lightOnly.join(', '));
    if (darkOnly.length) console.log('  Only in darkColors:', darkOnly.join(', '));
    console.log('');
  }

  const validKeys = light; // light/dark should match; light is the reference set

  const argPath = process.argv[2];
  const targets = argPath
    ? [path.resolve(argPath)]
    : [...findJsFiles(SCREENS_DIR), ...findJsFiles(COMPONENTS_DIR)];

  let anyInvalid = false;
  let filesWithRefs = 0;
  for (const file of targets) {
    const { refs, invalid } = checkFile(file, validKeys);
    if (refs.length === 0) continue;
    filesWithRefs++;
    const rel = path.relative(process.cwd(), file);
    if (invalid.length > 0) {
      anyInvalid = true;
      console.log(`FAIL  ${rel}`);
      console.log(`      invalid: ${invalid.map((k) => `colors.${k}`).join(', ')}`);
    } else {
      console.log(`OK    ${rel}  (${refs.length} color refs, all valid)`);
    }
  }

  console.log('');
  if (anyInvalid) {
    console.log(`Checked ${filesWithRefs} file(s) with color refs — some invalid references found.`);
    process.exitCode = 1;
  } else {
    console.log(`Checked ${filesWithRefs} file(s) with color refs — all colors.X references resolve to real theme keys.`);
  }
}

main();
