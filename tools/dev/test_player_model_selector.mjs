import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const assets=path.join(root,'src/main/resources/assets/combatant');
globalThis.__combatant_ui_contract=JSON.parse(fs.readFileSync(path.join(assets,'ui/api/ui.contract.json'),'utf8'));
globalThis.__combatant_ui_validation=true;
const moduleUrl=file=>{
  let source=fs.readFileSync(file,'utf8');
  source=source.replace(/from ['"]([^'"]+)['"]/g,(_,name)=>'from '+JSON.stringify(moduleUrl(path.resolve(path.dirname(file),name))));
  return 'data:text/javascript;base64,'+Buffer.from(source).toString('base64');
};
const load=async file=>import(moduleUrl(file));
globalThis.ui=(await load(path.join(assets,'ui/api/ui.js'))).ui;
const {buildTemplate}=await load(path.join(assets,'ui/modules/preview/player_models.js'));
const catalog=Array.from({length:44},(_,i)=>({id:'community:armor_'+i,model:'models/player/armor/model.obj',
  icon:'community:textures/icons/armor_'+i+'.png'}));
const walk=node=>[node,...(node.children||[]).flatMap(walk)];
const palettes=[
  {surface:'#FF202020',raised:'#FF303030',field:'#FF252525',border:'#FF606060',text:'#FFFFFFFF',muted:'#FFCCCCCC',accent:'#FF60A0F0',selected:'#FF334466'},
  {surface:'#FFF5F5F5',raised:'#FFFFFFFF',field:'#FFEAEAEA',border:'#FF909090',text:'#FF202020',muted:'#FF606060',accent:'#FFA01070',selected:'#FFFFD0E0'}
];
for(const width of [220,298,310]) for(const height of [460,636,900]) for(const palette of palettes) {
  for(const variants of [false,true]) {
    const entries=variants?catalog.slice(0,4):catalog;
    const root=buildTemplate({props:{width,height,palette,variants,selected:'tank',selection:'Tank',
      hasSelection:true,geometry:'community:armor',enabled:true,title:'Appearance',modelsLabel:'Models',variantsLabel:'Textures',applyLabel:'Apply',
      restoreLabel:'Restore',reloadLabel:'Reload',status:'Ready',items:entries.map(e=>({id:e.id,label:e.id,
        icon:e.icon}))}});
    const nodes=walk(root),keys=nodes.map(n=>n.key).filter(Boolean);
    assert.equal(new Set(keys).size,keys.length,'Duplicate retained UI keys');
    assert.equal(nodes.filter(n=>n.type==='image').length,entries.length);
    assert.equal(nodes.filter(n=>n.events?.click?.startsWith('models:select:')).length,entries.length);
    assert.ok(nodes.some(n=>n.type==='scroll'));
    assert.ok(nodes.some(n=>JSON.stringify(n).includes(palette.accent)),'Active theme accent missing');
    for(const node of nodes) for(const [key,value] of Object.entries(node.style||{})) {
      if(typeof value==='number') assert.ok(Number.isFinite(value),`${node.key}: ${key}`);
    }
  }
}
const empty=buildTemplate({props:{width:298,height:636,palette:palettes[0],items:[],hasSelection:false,
  emptyLabel:'Install a model addon'}});
assert.ok(walk(empty).some(n=>n.key==='models:empty'));
assert.ok(!walk(empty).some(n=>n.events?.click==='models:apply'));
console.log('Player model selector: validated authoring contract, actions, 3 sizes and both theme palettes.');
