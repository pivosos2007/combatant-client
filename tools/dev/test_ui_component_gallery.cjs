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
const pages=['buttons','selection','inputs','navigation','surfaces','overlays','states'];
for(const [w,h] of [[1920,1080],[1649,928],[1366,768],[1280,720]]){
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
   const surfaces=nodes.filter(n=>/^lab:segments:(day|week|month):container$/.test(n.key||''));
   const hoverLayers=nodes.filter(n=>/^lab:segments:(day|week|month):state$/.test(n.key||''));
   assert.equal(surfaces.length,3,'Each segment needs one analytic surface');
   assert.equal(hoverLayers.length,3,'Each segment needs a matching interactive state layer');
   assert(surfaces.every(n=>n.props.shape==='box' && n.props.cornerMorphTo===20),
     'All segment silhouettes must animate their true corner geometry');
   assert(surfaces.every(n=>n.props.cornerMorphOnPress!==true),
     'Connected selection geometry must not combine press and selection clocks');
   assert(surfaces.every(n=>n.props.strokeWidth===1),
     'One outline per segment, not overlay outlines');
   assert.equal(surfaces[1].props.cornerTL.radius,8,'Unselected inner corner uses 8dp');
   assert(!nodes.some(n=>/lab:segments:(selection|track:\d+)/.test(n.key||'')),
     'Old overlapping track/selection geometry leaked into connected group');
  }
  if(page==='inputs') assert(nodes.some(n=>n.key==='lab:select-field'),'Missing select field');
  if(page==='navigation') {
   const listNodes=nodes.filter(n=>/^lab:list:(profile|notifications|settings)$/.test(n.key||''));
   assert.equal(listNodes.length,3,'Navigation must show all three examples');
   const previewHeight=Math.min(910,Math.max(560,h-120))-165;
   if(w>=1280){
    assert(nodes.some(n=>n.key==='nav:column-rule'),'Wide navigation should have two columns');
    if(previewHeight>=560) assert(listNodes.every(n=>n.style.top+n.style.height<=previewHeight),
      'Wide navigation list is clipped by its own viewport');
   }
  }
  if(page==='overlays'){
   assert(nodes.some(n=>n.key==='lab:menu-trigger'),'Missing menu trigger');
   assert(nodes.some(n=>n.key==='lab:dialog-trigger'),'Missing dialog trigger');
  }
  console.log(`OK ${w}x${h} ${page}: ${nodes.length} nodes`);
 }
}
// Every selection position must retain the same true geometry and stable keys.
for(const selected of ['day','week','month']){
 vm.runInContext(`globalThis.__connected = GoogleMaterial3.connectedButtonGroup({key:'check:connected',width:420,selected:'${selected}',items:[{id:'day',label:'Day'},{id:'week',label:'Week'},{id:'month',label:'Month'}]});`,context);
 const ns=allNodes(context.__connected);
 const buttons=ns.filter(n=>/^check:connected:(day|week|month)$/.test(n.key||''));
 assert.equal(buttons.length,3,'Three interactive segments required');
 assert.equal(buttons.filter(n=>n.props.stateValues.selected).length,1,'Exactly one selected segment');
 const layers=ns.filter(n=>/^check:connected:(day|week|month):(container|state)$/.test(n.key||''));
 assert.equal(layers.length,6,'No redundant moving track or stroke layers');
 assert(layers.every(n=>n.props.cornerMorphTo===20 && n.props.stateSource==='parent'));
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
assert(!devSection.includes('lab:segments:selection'),'Connected selection must be native per-segment geometry, not patched overlay');
assert(!devSection.includes('GLFW'),'Native input integration regressed');
console.log('All component gallery and motion-contract tests passed.');

const renderer=load('java/combatant/client/render/engine/renderer/Renderer2D.java');
assert(renderer.includes('isAnalyticRoundedBox(box)'), 'Round-per-corner boxes must render through analytic SDF path');
assert(renderer.includes('roundedRectStrokeCorners(b.x(), b.y()'), 'Analytic border path is required');
console.log('Analytic rounded-box rendering path verified in source.');
assert(renderer.includes('double qRight = x + w + fringe'), 'Analytic AA border needs a fragment fringe beyond bounds');
