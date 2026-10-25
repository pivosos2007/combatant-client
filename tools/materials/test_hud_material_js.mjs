/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import vm from 'node:vm';
import assert from 'node:assert/strict';

const abs = (x, y, w, h, extras = {}) => ({ left: x, top: y, width: w, height: h, ...(typeof extras === 'object' ? extras : {}) });
const node = (type) => (props = {}, ...rest) => ({ type, ...props, children: props.children ?? rest.flat() });
const ui = {
  num: (value, def = 0) => Number.isFinite(Number(value)) && value != null ? Number(value) : def,
  str: (value, def = '') => value == null ? def : String(value),
  fmt: (value) => String(value),
  cls: (...parts) => parts.flat().filter(Boolean).join(' '),
  abs,
  absolute: abs,
  arr: (value) => Array.isArray(value) ? value : [],
  prop: (obj, key, def) => obj?.[key] ?? def,
  color: {
    get: (obj, key, def) => obj?.[key] ?? def,
    alpha: (value) => value ?? '#FFFFFFFF',
    opacity: (value) => typeof value === 'string' && value.startsWith('#') && value.length === 9
      ? parseInt(value.slice(1, 3), 16) / 255 : 1,
    mix: (a) => a,
  },
};
for (const name of ['shape','root','stack','text','svg','item','blurSurface',
                    'roundedGradient','roundedStrokeGradient','roundedSoftShadow','clippedText','rect','line']) ui[name] = node(name);
const context = vm.createContext({ ui, Math, Number, String, Array, Object });
const dir = resolve('resources/assets/combatant/ui/modules/hud/draggable');
async function load(name) {
  const module = new vm.SourceTextModule(readFileSync(resolve(dir, name + '.js'), 'utf8'), { context });
  await module.link(async () => { throw new Error('Unexpected JS import in ' + name); });
  await module.evaluate();
  return module.namespace;
}
const flat = (entry) => entry == null ? [] : Array.isArray(entry)
  ? entry.flatMap(flat) : [entry, ...flat(entry.children ?? [])];

const { HudPanelLayout } = await load('panel_base');
for (const materialMode of ['', 'graphite', 'ceramic']) {
  const panel = new HudPanelLayout({ props: { materialMode, palette: {} } });
  const shape = panel.panelShape('body', 0, 0, 140, 36,
      [5, 5, 0, 0], ['#FF112233','#FF112233','#FF445566','#FF445566'], '#55334455', 1);
  assert.equal(shape.shape, 'rounded-corners');
  assert.equal(shape.materialMode, materialMode);
  assert.equal(shape.materialMicrotexture, true);
  assert.equal(shape.style.width, 140);
  assert.equal(panel.panelShape('divider',0,0,1,12,[0,0,0,0],['#FFFFFFFF','#FFFFFFFF','#FFFFFFFF','#FFFFFFFF'],'#00000000',0).materialMode, '');
}
const stat = await load('compact_stat');
for (const materialMode of ['', 'satin-titanium']) {
  const tree = stat.render({ props: { id: 'fps', width: 95, height: 22, radius: 5, materialMode, palette: {} } });
  const shapes = flat(tree);
  assert.equal(shapes.find(x => x.key === 'fill')?.materialMode, materialMode);
  assert.equal(shapes.filter(x => x.key === 'top-glint').length, materialMode ? 0 : 1);
}
const armor = await load('armor');
for (const materialMode of ['', 'graphite']) {
  const tree = armor.render({ props: { width: 95, height: 25, scale: 1, items: [], materialMode } });
  const shapes = flat(tree);
  const base = shapes.find(x => x.key === 'armor:matte-base');
  assert(base, 'Armor background missing');
  assert.equal(base.materialMode ?? '', materialMode);
  if (materialMode) assert(base.style?.width > 0, 'Armor material must have actual bounds');
}
const itemizer = await load('itemizer');
for (const materialMode of ['', 'ceramic']) {
  const tree = itemizer.render({ props: { width: 90, height: 22, materialMode,
    items: [{ x: 0, y: 0, w: 90, h: 22, visible: true, item: 'example', stack: null }], palette: {} } });
  const nodes = flat(tree);
  const base = nodes.find(x => x.key === '0:panel');
  assert(base, 'Itemizer panel missing');
  assert.equal(base.materialMode ?? '', materialMode);
  if (materialMode) assert(base.style?.width > 0, 'Itemizer panel missing style bounds');
}
console.log('Draggable HUD JS materials: panels, compact stats, armor, itemizer OK');
