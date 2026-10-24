/* Verify real Combatant inline module boundaries, component geometry and stable keys. */
const fs=require('fs'),vm=require('vm'),path=require('path'),assert=require('assert');
const root=process.argv[2]||path.resolve(__dirname,'../..');
const context={console,globalThis:null};context.globalThis=context;
context.__combatant_ui_validation=true;
context.__combatant_ui_contract=JSON.parse(fs.readFileSync(path.join(root,'resources/assets/combatant/ui/api/ui.contract.json'),'utf8'));
vm.createContext(context);
const load=(relative)=>fs.readFileSync(path.join(root,relative),'utf8');
const stripExports=s=>s.replace(/\bexport\s+(?=(?:class|function|const|let|var)\b)/g,'');
let uiJs=stripExports(load('resources/assets/combatant/ui/api/ui.js')).replace(/const ui\s*=/,'globalThis.ui =');
vm.runInContext(uiJs,context,{filename:'ui.js'});
function inline(relative,seen=new Set()) {
  if(seen.has(relative))return '';
  seen.add(relative);
  const src=load(relative),dir=path.posix.dirname(relative);
  const importRe=/^\s*import\s+(?:(.+?)\s+from\s+)?['"]([^'"]+)['"]\s*;?\s*$/gm;
  let deps='',m;
  while((m=importRe.exec(src))) {
    if(m[1]&&!/^\{\s*\w+(\s*,\s*\w+)*\s*\}$/.test(m[1]))throw Error('Unsupported Combatant import: '+m[0]);
    if(!m[2].startsWith('.'))throw Error('Non-relative import: '+m[2]);
    const target=path.posix.normalize(path.posix.join(dir,m[2]));
    deps+=inline(target,seen)+'\n';
  }
  return deps+stripExports(src.replace(importRe,''));
}
const gallerySource=inline('dev/resources/assets/combatant/ui/modules/dev/component_gallery.js');
vm.runInContext(gallerySource,context,{filename:'component_gallery.inlined.js'});
const allNodes=(n,out=[])=>{if(!n||typeof n!=='object')return out;
  if(Array.isArray(n)){n.forEach(v=>allNodes(v,out));return out;}
  if(typeof n.type==='string'){out.push(n);allNodes(n.children,out);}return out;};
const pages=['buttons','selection','inputs','navigation','surfaces','states'];
for(const [w,h] of [[1920,1080],[1366,768],[1280,720]]){
 for(const page of pages){
  const tree=context.buildTemplate({props:{width:w,height:h,kit:'google',page,segment:'week',radio:1,enabled:true,checked:true}});
  const nodes=allNodes(tree);const keys=nodes.map(n=>n.key).filter(Boolean);
  assert.equal(keys.length,new Set(keys).size,`${page} duplicate keys`);
  assert(nodes.some(n=>n.key==='catalog:kit:google'),'Missing kit selector');
  assert(nodes.some(n=>n.key==='lab:content'),'Missing independent page scroll surface');
  assert(nodes.some(n=>n.key==='catalog:title'&&(n.text==='Component Library'||n.props?.text==='Component Library')),'Host title must remain kit neutral');
  assert(!nodes.some(n=>/dev.only|native ui|dev build/i.test(n.text||'')),'Promotional / debug footer leaked');
  for(const n of nodes){
   const s=n.style||{};
   for(const k of ['left','top','width','height']){
    if(s[k]!==undefined) assert(Number.isFinite(s[k])&& (k==='width'||k==='height'?s[k]>=0:true),`${page} non-finite ${k} for ${n.key}`);
   }
  }
  if(page==='selection'){
   const tracks=nodes.filter(n=>n.key?.startsWith('lab:segments:track:'));
   assert.equal(tracks.length,3,'Expected three connected tracks');
   assert.equal(tracks[1].props?.shape,'box','Middle uses independent per-corner geometry');
   assert.equal(tracks[1].props.cornerTL.radius,8,'Middle has rounded inner corners');
   const indicator=nodes.filter(n=>n.key==='lab:segments:selection');
   assert.equal(indicator.length,1,'Selection pill must be shared');
   assert.equal(indicator[0].props?.radius,20,'Selection pill must be fully rounded');
  }
  console.log(`OK ${w}x${h} ${page}: ${nodes.length} nodes`);
 }
}
// Structural check of the Google classic capsule; only the group receives its outline.
vm.runInContext(`globalThis.__segmented=GoogleMaterial3.segmented({key:'test:seg',width:360,selected:'week',items:[{id:'day',label:'Day'},{id:'week',label:'Week'},{id:'month',label:'Month'}]});`,context);
const classic=allNodes(context.__segmented);
assert.equal(classic.filter(n=>n.key==='test:seg:outline').length,1);
assert.equal(classic.filter(n=>n.key?.endsWith(':divider:1')).length,1);
assert.equal(classic.filter(n=>n.key?.endsWith(':divider:2')).length,1);
assert.equal(classic.filter(n=>n.key?.endsWith(':selected')&&n.props?.strokeWidth>0).length,0);
// Motion semantics must use signals understood by UiReactiveVisual (press, not active).
const googleSource=load('dev/resources/assets/combatant/ui/api/kits/google/material3/components.js');
assert(!/alphaTerms:\s*\{[^}]*active\s*:/.test(googleSource),'Unrecognized active signal in state layer');
assert(/press:\s*\{durationMs:110/.test(googleSource),'Press motion absent');
assert(/alphaTerms:\s*\{selected:1/.test(googleSource),'Selection transition absent');
const devSection=load('dev/java/combatant/client/features/gui/clickgui/sections/dev/UiComponentLabSection.java');
assert(devSection.includes('animatedSegment * (each + gap)'),'Animated connected selection must track computed bounds');
assert(!devSection.includes('GLFW'),'Native input integration regressed');
console.log('All component gallery and motion-contract tests passed.');
