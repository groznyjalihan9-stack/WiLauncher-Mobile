// Builds the WiLauncher mod catalog from catalog/catalog.source.json.
//   node scripts/build-catalog.mjs [out.json]
// Looks every entry up on Modrinth (id, name, icon, type, loaders) and writes the file the app
// bundles (FCL/src/main/assets/catalog/catalog.json). The same file is published as
// android/catalog.json in the WiLauncher-releases repository, from where the app refreshes it.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const out = process.argv[2] || join(root, 'FCL/src/main/assets/catalog/catalog.json');
const source = JSON.parse(readFileSync(join(root, 'catalog/catalog.source.json'), 'utf8'));
const UA = 'WiLauncher-Mobile catalog builder (github.com/groznyjalihan9-stack/WiLauncher-Mobile)';

const slugs = [...new Set(source.items.flatMap((i) => [i.slug, ...(i.requires || [])]))];
const url = `https://api.modrinth.com/v2/projects?ids=${encodeURIComponent(JSON.stringify(slugs))}`;
const res = await fetch(url, { headers: { 'User-Agent': UA } });
if (!res.ok) throw new Error(`Modrinth: HTTP ${res.status}`);
const bySlug = new Map((await res.json()).map((p) => [p.slug, p]));

const items = source.items.map((item) => {
  const p = bySlug.get(item.slug);
  if (!p) throw new Error(`Not on Modrinth: ${item.slug}`);
  const loaders = (item.loaders || p.loaders).filter((l) => p.loaders.includes(l));
  return {
    id: p.id,
    slug: p.slug,
    name: p.title,
    icon: p.icon_url || '',
    type: p.project_type,
    category: item.category,
    loaders,
    summary: item.summary,
    ...(item.heavy ? { heavy: true } : {}),
    ...(item.requires
      ? { requires: item.requires.map((s) => { const r = bySlug.get(s); if (!r) throw new Error(`Not on Modrinth: ${s}`); return r.id; }) }
      : {}),
  };
});

const now = new Date();
const version = Number(`${now.getUTCFullYear()}${String(now.getUTCMonth() + 1).padStart(2, '0')}${String(now.getUTCDate()).padStart(2, '0')}`);
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify({ version, categories: source.categories, items }, null, 1) + '\n');
console.log(`catalog v${version}: ${items.length} items -> ${out}`);
