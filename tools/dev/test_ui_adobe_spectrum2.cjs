/* Spectrum 2 dev-kit regression. Models the Combatant inline loader (no ES runtime). */
const fs=require('fs'),vm=require('vm'),path=require('path'),assert=require('assert');
const root=process.argv[2]||path.resolve(__dirname,'../..');
const context={console,globalThis:null};context.globalThis=context;
context.__combatant_ui_validation=true;
context.__combatant_ui_contract=JSON.parse(fs.readFileSync(path.join(root,'resources/assets/combatant/ui/api/ui.contract.json'),'utf8'));
vm.createContext(context);
const load=r=>fs.readFileSync(path.join(root,r),'utf8');
const strip=s=>s.replace(/\bexport\s+(?=(?:class|function|const|let|var)\b)/g,'');
vm.runInContext(strip(load('resources/assets/combatant/ui/api/ui.js')).replace(/const ui\s*=/,'globalThis.ui ='),context);
function inline(relative,seen=new Set()) {
  if(seen.has(relative))return '';
  seen.add(relative);
  const src=load(relative),dir=path.posix.dirname(relative);
  const re=/^\s*import\s+(?:(.+?)\s+from\s+)?['"]([^'"]+)['"]\s*;?\s*$/gm;
  let deps='',m;
  while((m=re.exec(src))) {
    assert(!m[1]||/^\{\s*\w+(\s*,\s*\w+)*\s*\}$/.test(m[1]),'Unsupported import: '+m[0]);
    assert(m[2].startsWith('.'));
    deps+=inline(path.posix.normalize(path.posix.join(dir,m[2])),seen)+'\n';
  }
  return deps+strip(src.replace(re,''));
}
vm.runInContext(inline('dev/resources/assets/combatant/ui/modules/dev/component_gallery.js'),context,{filename:'combatant-inline-gallery.js'});
const walk=(n,out=[])=>{
  if(!n||typeof n!=='object')return out;
  if(Array.isArray(n)){n.forEach(v=>walk(v,out));return out;}
  if(typeof n.type==='string'){out.push(n);walk(n.children,out);}return out;
};
let count=0;
for(const [w,h] of [[1920,1080],[1649,928],[1366,768],[1280,720],[960,600]]) {
  for(const page of ['buttons','selection','inputs','navigation','pickers','feedback']) {
    const rootNode=context.buildTemplate({props:{width:w,height:h,kit:'adobe',page,
      adobeChecked:true,adobeEnabled:true,adobeRadio:2,adobeTab:'sharing',
      adobeAction:true,focus:'adobe-field',value:'example',caretOffset:33}});
    const nodes=walk(rootNode),keys=nodes.map(n=>n.key).filter(Boolean);
    assert.equal(keys.length,new Set(keys).size,`${w}x${h} ${page} duplicate keys`);
    assert(nodes.some(n=>n.key==='catalog:kit:adobe'));
    assert(nodes.some(n=>n.key==='catalog:kit:google'));
    assert(nodes.some(n=>n.key==='lab:content'));
    // The second kit must not push the final navigation row outside the window.
    const lastNav=nodes.find(n=>n.key===`lab:nav:${page==='buttons'?'navigation':'navigation'}`);
    if(lastNav) {
      const ph=Math.min(910,Math.max(560,h-120));
      assert(lastNav.style.top+lastNav.style.height<=ph-18,
        `Navigation last row clips at ${w}x${h}`);
    }
    for(const n of nodes){const s=n.style||{};
      for(const k of ['left','top','width','height'])if(s[k]!==undefined)
        assert(Number.isFinite(s[k])&&(!['width','height'].includes(k)||s[k]>=0),`invalid ${n.key}:${k}`);
    }
    if(page==='buttons')assert(nodes.some(n=>n.key==='s2:button:accent'));
    if(page==='selection'){
      assert(nodes.some(n=>n.key==='s2:switch:thumb'));
      assert(nodes.some(n=>n.key==='s2:checkbox'));
    }
    if(page==='inputs'){
      assert(nodes.some(n=>n.key==='s2:field'));
      assert(nodes.some(n=>n.key==='s2:field:caret'));
    }
    if(page==='pickers') {
      assert(nodes.some(n=>n.key==='s2:picker'));
      assert(nodes.some(n=>n.key==='s2:picker:disabled'));
    }
    if(page==='feedback') {
      for(const key of ['s2:slider','s2:slider:thumb','s2:progress:fill','s2:meter:fill'])
        assert(nodes.some(n=>n.key===key),`Missing ${key}`);
    }
    if(page==='navigation'){
      assert.equal(nodes.filter(n=>n.key==='s2:tabs:indicator').length,1);
      assert.equal(nodes.filter(n=>n.key?.startsWith('s2:tabs:tab:')).length>=3,true);
    }
    count++;
  }
}
const java=load('dev/java/combatant/client/features/gui/clickgui/sections/dev/UiComponentLabSection.java');
assert(java.includes('"lab:kit:adobe"'));
assert(java.includes('"s2:field"'));
assert(java.includes('"SourceSans3"'));
assert(!java.includes('GLFW'));
assert(java.includes('CombatantTextInputBridge'));
assert(java.includes('s2:tabs:indicator'));
assert(java.includes('CombatantInput.state()'));
assert(java.includes('InputKeys.HOME'));
assert(java.includes('"s2:slider"'));
assert(java.includes('"adobe-picker"'));
assert(java.includes('"adobe-search"'));
const build=load('build.gradle');
assert(build.includes('prepareDevSpectrumFonts'));
assert(build.includes('SourceSans3-Regular.ttf'));
assert(build.includes('SourceSans3-OFL-1.1.txt'));
assert(build.includes('3.052R'));
const font=load('dev/java/combatant/client/render/engine/text/dev/AdobeSpectrumFonts.java');
assert(font.includes('@FontCatalog'));
// Popup is a separate top-layer tree, never part of the scroll viewport.
let popupCount=0;
for(const [w,h] of [[1920,1080],[1280,720],[960,600],[800,550]]) {
  const anchors=[{x:100,y:100},{x:w-400,y:h-220},{x:8,y:h-100}];
  for(const a of anchors) {
    const props={width:w,height:h,kit:'adobe',page:'pickers',overlay:'adobe-picker',
      adobePicker:'medium',overlayIndex:1,
      anchorX:a.x,anchorY:a.y,anchorWidth:370,anchorHeight:80};
    const tree=context.buildTemplate({props}),nodes=walk(tree);
    const container=tree.children.find(n=>n.key==='s2:overlay:root');
    assert(container,'Overlay root not at top level');
    assert(nodes.some(n=>n.key==='s2:overlay:outside'),'Outside click target absent');
    const positioned=container.children.find(n=>n.key==='s2:picker:popover');
    assert(positioned,'Popup positioning absent');
    const keys=nodes.map(n=>n.key).filter(Boolean);
    assert.equal(keys.length,new Set(keys).size,'Popup duplicate keys');
    assert(positioned.style.left>=11 && positioned.style.top>=11);
    assert(positioned.style.left+positioned.style.width<=w-11);
    assert(positioned.style.top+positioned.style.height<=h-11);
    for(const item of ['low','medium','high','ultra']) {
      const node=nodes.find(n=>n.key==='s2:picker:option:'+item);
      assert(node,`Missing picker item ${item}`);
      if(item==='ultra')assert(!node.events?.click,'Disabled item has action');
      else assert.equal(node.events?.click,'lab:adobe:picker:choose:'+item);
    }
    popupCount++;
  }
}
const componentSource=load('dev/resources/assets/combatant/ui/api/kits/adobe/spectrum2/components.js');
assert(componentSource.includes('static searchField(props='));
assert(componentSource.includes('static progressBar(props='));
assert(componentSource.includes('static meter(props='));
console.log(`Adobe Spectrum 2: ${count} gallery scenarios and ${popupCount} popup scenarios passed with inline loader`);
