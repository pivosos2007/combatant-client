/* Regression tests for inline Javet module loading and Material 3 transient UI. */
const fs=require('fs'),vm=require('vm'),path=require('path'),assert=require('assert');
const root=process.argv[2]||path.resolve(__dirname,'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const ctx={console,globalThis:null,__combatant_ui_validation:true,
 __combatant_ui_contract:JSON.parse(read('resources/assets/combatant/ui/api/ui.contract.json'))};
ctx.globalThis=ctx;vm.createContext(ctx);
const strip=s=>s.replace(/\bexport\s+(?=(?:class|function|const|let|var)\b)/g,'');
vm.runInContext(strip(read('resources/assets/combatant/ui/api/ui.js')).replace(/const ui\s*=/,'globalThis.ui ='),ctx);

function inline(p,seen=new Set()){
 if(seen.has(p))return '';seen.add(p);
 const s=read(p),dir=path.posix.dirname(p);let deps='',match;
 const imports=/^\s*import\s+(?:(.+?)\s+from\s+)?['"]([^'"]+)['"]\s*;?\s*$/gm;
 while((match=imports.exec(s))){
  if(match[1]&&!/^\{\s*\w+(\s*,\s*\w+)*\s*\}$/.test(match[1])) throw Error('Unsupported inline import: '+match[0]);
  deps+=inline(path.posix.normalize(path.posix.join(dir,match[2])),seen)+'\n';
 }

 return deps+strip(s.replace(imports,''));
}
vm.runInContext(inline('dev/resources/assets/combatant/ui/modules/dev/component_gallery.js'),ctx);
const nodes=(n,result=[])=>{if(!n||typeof n!=='object')return result;
 if(Array.isArray(n)){for(const v of n)nodes(v,result);return result;}
 if(n.type){result.push(n);nodes(n.children,result);}return result;};
const rootMenu=[
 {id:'actions',label:'Actions',kind:'header',disabled:true},
 {id:'copy',label:'Copy',kind:'item',icon:'clipboard',shortcut:'Ctrl+C'},
 {id:'share',label:'Share',kind:'item'},
 {id:'divide-1',kind:'divider'},
 {id:'view',label:'View',kind:'item',submenu:'view'},
 {id:'sort',label:'Sort by',kind:'item',submenu:'sort'},
 {id:'hidden',label:'Show hidden files',kind:'item',selection:'checkbox',checked:true},
 {id:'divide-2',kind:'divider'},
 {id:'paste',label:'Paste',kind:'item',disabled:true},
 {id:'delete',label:'Delete',kind:'item'}];
const subMenu=[{id:'list',label:'List',kind:'item',selection:'radio',checked:true},
 {id:'grid',label:'Grid',kind:'item',selection:'radio'},
 {id:'details',label:'Details',kind:'item',selection:'radio'}];
const allKeys=tree=>nodes(tree).map(n=>n.key).filter(Boolean);
for(const [w,h] of [[1920,1080],[1649,928],[1280,720],[800,600]]){
 for(const [mode,submenu] of [['menu',''],['menu','view'],['select',''],['dialog','']]){
  const t=ctx.buildTemplate({props:{width:w,height:h,page:'overlays',kit:'google',
   overlay:mode,submenu,menuItems:rootMenu,menuSubItems:submenu?subMenu:[],
   anchorX:w-38,anchorY:h-34,anchorWidth:160,anchorHeight:40,
   overlayIndex:4,submenuIndex:1,selectValue:'system'}});
  const ns=nodes(t),keys=allKeys(t);
  assert.strictEqual(keys.length,new Set(keys).size,`Duplicate keys ${mode}/${submenu}/${w}`);
  const main=ns.find(n=>n.key==='m3:popup');
  if(mode==='menu'||mode==='select') assert(main,'Popup missing');
  if(main){
   assert(main.style.left>=12&&main.style.top>=12,'Popup off viewport');
   assert(main.style.left+main.style.width<=w-12,'Popup horizontal overflow');
   assert(main.style.top+main.style.height<=h-12,'Popup vertical overflow');
  }
  const sub=ns.find(n=>n.key==='m3:submenu');
  assert.strictEqual(!!sub,!!submenu,`Submenu mismatch ${mode}/${submenu}`);
  if(sub){
   assert(sub.style.left>=12 && sub.style.left+sub.style.width<=w-12,'Submenu overflow');
   assert(sub.style.top>=12 && sub.style.top+sub.style.height<=h-12,'Submenu vertical overflow');
  }
  if(mode==='menu'){
   assert(ns.some(n=>n.key==='m3:popup:actions:heading'));
   assert(ns.some(n=>n.key==='m3:popup:divide-1:line'));
   assert(ns.some(n=>n.key==='m3:popup:hidden:check'));
   assert(ns.some(n=>n.key==='m3:popup:copy:icon' && n.props?.asset==='clipboard'),
     'Copy must use existing clipboard asset instead of copyleft');
   assert(!ns.some(n=>n.key==='m3:popup:view:focus'),
     'Menu keyboard focus must share the state surface, not draw a second outline');
   assert(!ns.some(n=>n.key==='m3:popup:paste'&&n.events?.click),'Disabled menu entry has action');
  }
  console.log(`PASS ${w}x${h} ${mode}${submenu?'/'+submenu:''}`);
 }
 for(const variant of ['tooltip','snackbar']){
  const t=ctx.buildTemplate({props:{width:w,height:h,page:'overlays',kit:'google',
   ...(variant==='tooltip'?{tooltip:'More information',tooltipX:140,tooltipY:170}:
    {snackbar:true,snackMessage:'Preferences saved'})}});
  const ns=nodes(t),keys=allKeys(t);
  assert.strictEqual(keys.length,new Set(keys).size,'duplicate key in '+variant);
  assert(ns.some(n=>n.key==='m3:'+variant),variant+' missing');
  assert(!ns.some(n=>n.key==='m3:overlay:outside'),variant+' must not use modal scrim');
  console.log(`PASS ${w}x${h} ${variant}`);
 }
}
// Ensure data binding doesn't force the wrong fixed-size four-item menu.
const java=read('dev/java/combatant/client/features/gui/clickgui/sections/dev/UiComponentLabSection.java');
assert(java.includes('nextMenuIndex(items, current, 1)'));
assert(java.includes('menuSubItems'));
assert(java.includes('InputKeys.RIGHT'));
assert(java.includes('CombatantTextInputBridge'));
assert(java.includes('option("copy", "Copy", "clipboard", "Ctrl+C")'));
assert(!java.includes('option("copy", "Copy", "copyleft"'));
assert(!java.includes('GLFW'));
console.log('Material 3 transient-menu integration tests passed.');
