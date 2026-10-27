/* Standalone dev-only smoke test. Uses the same recursive import inlining
 * restrictions as UiScriptModuleLoader; no npm dependencies. */
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'../..');
const api=path.join(root,'resources/assets/combatant/ui/api');
const modDir=path.join(root,'dev/resources/assets/combatant/ui');
const codePath=path.join(modDir,'modules/dev/component_gallery.js');
const contract=JSON.parse(fs.readFileSync(path.join(api,'ui.contract.json'),'utf8'));
const read=p=>fs.readFileSync(p,'utf8');
const importRe=/^\s*import\s+(?:(.+?)\s+from\s+)?['"]([^'"]+)['"]\s*;?\s*$/gm;
const seen=new Set();
function inline(file) {
  const abs=path.resolve(file);
  if(seen.has(abs))return '';
  seen.add(abs);
  const content=read(abs);
  let deps='';
  for(const match of content.matchAll(importRe)) {
    const names=(match[1]||'').trim();
    if(names&&!/^\{\s*[A-Za-z_$][\w$]*(?:\s*,\s*[A-Za-z_$][\w$]*)*\s*\}$/.test(names)){
      throw Error(`unsupported import: ${names} from ${match[2]}`);
    }
    deps+=inline(path.resolve(path.dirname(abs),match[2]))+'\n';
  }
  return deps+content.replace(importRe,'').replace(/^\s*export\s+/gm,'');
}
const code=read(path.join(api,'ui.js')).replace(/^\s*export\s+/gm,'')+'\n'+inline(codePath);
const context=vm.createContext({__combatant_ui_contract:contract,__combatant_ui_validation:true});
vm.runInContext(code,context,{filename:'m3-inline-runtime.js',timeout:15000});
function findKey(n,key){
  if(n?.key===key)return n;
  for(const child of n?.children||[]){const match=findKey(child,key);if(match)return match;}
  return null;
}
function validate(n,keys=new Set()){
  if(!n||typeof n!=='object')return;
  if(n.key){if(keys.has(n.key))throw Error('duplicate key '+n.key);keys.add(n.key);}
  for(const name of ['width','height','left','top']) {
    const v=n.style?.[name];
    if(v!==undefined&&typeof v==='number'&&!Number.isFinite(v))throw Error('invalid bounds '+n.key+' '+name+'='+v);
  }
  for(const child of n.children||[])validate(child,keys);
}
let runs=0;
const pages=['buttons','selection','inputs','navigation','surfaces','overlays','states'];
for(const [width,height] of [[1920,1080],[1366,768],[1280,720]]) {
  for(const page of pages) {
    for(const overlay of (page==='overlays'?['','menu','select','dialog']:[''])) {
      const data={width,height,kit:'google',page,overlay,
        anchorX:Math.round(width*.45),anchorY:Math.round(height*.55),anchorWidth:340,anchorHeight:56,
        overlayIndex:2,enabled:true,checked:true,radio:0,slider:50,selectValue:'light',
        value:'Example',focus:'',tab:'activity',rail:'favorites'};
      const tree=vm.runInContext('buildTemplate({props:'+JSON.stringify(data)+'})',context,{timeout:15000});
      validate(tree);runs++;
      if(page==='buttons') {
        const filled=findKey(tree,'lab:button:filled');
        if(!filled?.props?.materialRipple || !findKey(filled,'lab:button:filled:ripple')) {
          throw Error('Material 3 button lost native ripple');
        }
      }
      if(overlay) {
        const over=tree.children?.find(x=>x.key==='m3:overlay:root');
        if(!over)throw Error('Overlay missing');
        if(over.children.some(x=>x.key==='m3:popup:surface') && page!=='overlays')throw Error('Bad popup scope');
      }
    }
  }
}
console.log(`Material 3 UI smoke: ${runs} valid inlined script trees; no duplicate keys or bad bounds`);
