// Standalone regression for the real PlayerRigScriptRuntime Java text block.
// Usage: node test/js/player_rig_packed_buffer.test.js
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const file = path.resolve(__dirname, '../../java/combatant/client/features/playeranimator/script/PlayerRigScriptRuntime.java');
const java = fs.readFileSync(file, 'utf8');
const start = java.indexOf('                globalThis.global = globalThis;');
const end = java.indexOf('                """;', start);
assert(start >= 0 && end > start, 'could not extract real JS bootstrap');
const bootstrap = java.slice(start, end).split('\n').map(line => line.replace(/^                /, '')).join('\n');

function isolate() {
  const context = vm.createContext({});
  vm.runInContext(`globalThis.__combatant_player_rig_bones = Object.freeze({
    root:0, head:1, left_upper_arm:2, right_upper_arm:3,
    left_item_control:4, right_item_control:5
  });
  globalThis.__combatant_player_rig_deforms = Object.freeze({cape:2});`, context);
  vm.runInContext(bootstrap, context);
  return context;
}
function plain(raw) { return JSON.parse(JSON.stringify(raw)); }
function packed(context, count) {
  const data = context.__combatant_rig_output_buffer;
  const out = [];
  for (let i = 0; i < count; i++) {
    const base = i * 11;
    out.push([data[base], data[base+1], ...data.slice(base+3, base+3+data[base+2])]);
  }
  return plain(out);
}
{
  const ctx = isolate();
  vm.runInContext(`playerRig.onPose((context, rig) => {
    rig.reset(); rig.resetBone('head');
    rig.setPosition('head', 1,2,3); rig.move('head', 4,5,6);
    rig.setRotation('head', 90,30,-45); rig.rotate('head',10,20,30);
    rig.setRotationRadians('head', .25,.5,.75); rig.rotateRadians('head', .4,.3,.2);
    rig.setQuaternion('head', .1,.2,.3,1);
    rig.setScale('head', 1,2,3);
    rig.bend('cape',45,.4); rig.bendRadians('cape',.2,.3);
    rig.twist('cape',90,.5); rig.twistRadians('cape',.8,.9); rig.clearDeform('cape');
    rig.reachHand('left','head',1,2,3,4,5,6,.7);
    rig.placeItem('right','root',1,2,3,10,20,30,.5);
  });`, ctx);
  const legacy = plain(ctx.__combatant_player_rig_execute([]));
  ctx.__combatant_rig_enable_binary();
  const count = ctx.__combatant_player_rig_execute([]);
  assert.equal(typeof count, 'number');
  assert.equal(count, legacy.length);
  assert.deepEqual(packed(ctx, count), legacy);
  assert.equal(ctx.__combatant_rig_output_buffer.length, 256*11);
}
{
  const ctx = isolate();
  vm.runInContext(`playerRig.onPose((context, rig) => {
    for (let i=0; i<300; i++) rig.move('head',i,i+1,i+2);
  });`, ctx);
  const legacy = plain(ctx.__combatant_player_rig_execute([]));
  ctx.__combatant_rig_enable_binary();
  const [count, tail] = ctx.__combatant_player_rig_execute([]);
  assert.equal(count, 256);
  assert.equal(tail.length, 44);
  assert.deepEqual([...packed(ctx, count), ...plain(tail)], legacy);
  const [count2, tail2] = ctx.__combatant_player_rig_execute([]);
  assert.equal(count2, 256);
  assert.equal(tail2.length, 44);
}
console.log('PlayerRig packed Float64Array: normal, every opcode, and overflow tests passed');
