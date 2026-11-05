// Verifies that UI dependency tracking can be installed before user module code.
// This tests JS bootstrap ordering, NOT the native V8 createSnapshot()/restore API.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const api = path.resolve(__dirname, '../../resources/assets/combatant/ui/api');
const contract = JSON.parse(fs.readFileSync(path.join(api, 'ui.contract.json'), 'utf8'));
const uiSource = fs.readFileSync(path.join(api, 'ui.js'), 'utf8')
  .replace('export const ui =', 'globalThis.ui =');
const tracker = fs.readFileSync(path.join(api, 'dependency_tracking.js'), 'utf8');
function createIsolate() {
 const context = vm.createContext({});
 context.__combatant_ui_contract = contract;
 context.__combatant_ui_validation = false;
 vm.runInContext('globalThis.global=globalThis; globalThis.__combatant_render=undefined;', context);
 vm.runInContext(uiSource, context);
 vm.runInContext(tracker, context);
 return context;
}
const a = createIsolate();
vm.runInContext(`globalThis.__combatant_render = ctx => ui.root({ children: [ui.text({ text: ctx.props.health })] });`,a);
const result = a.__combatant_render_tracked({props:{health:20,unused:1},time:0,frame:0});
assert(result && result.tree, 'tracked render missing tree');
assert(result.dependencies.length > 0, 'missing tracked dependencies');
const b = createIsolate();
assert.equal(typeof b.__combatant_render, 'undefined', 'module state escaped into a second isolate');
console.log('UI core-before-module order: independent contexts and tracked render passed');
