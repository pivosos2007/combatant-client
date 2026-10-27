/* Google Material 3 preview content. UI Kit is independent from the catalog host. */
import { GoogleMaterial3 } from './components.js';
const M = GoogleMaterial3;
const P = M.dark;
const A = (node,x,y,w,h) => ({...node,style:ui.absolute(x,y,w,h)});
const T = (key,label,x,y,w,h,opts={}) => M.text(key,label,x,y,w,h,{
  size:opts.size??16,medium:opts.medium??false,color:opts.color??P.onSurface,
  align:opts.align??'left'
});

export function renderGoogleMaterial3Gallery(data,cw,page) {
  const detail=[];
  const H=(key,label,y)=>detail.push(T(key,label,24,y,cw-50,35,{size:24,medium:true}));
  const D=(key,label,y)=>detail.push(T(key,label,24,y,cw-50,26,{size:15,color:P.onSurfaceVariant}));
  const sep=(key,y)=>detail.push(M.shape(key,24,y,cw-48,1,0,P.outlineVariant));
  const place=(node,x,y,w,h)=>detail.push(A(node,x,y,w,h));
  const action=(id)=>`lab:clicked:${id}`;
  if(page==='buttons'){
    H('head:buttons','Buttons',20);D('sub:buttons','Material Web • 40dp container • Roboto Medium, label-large 14/20',61);
    const variants=[['filled','Filled',118],['tonal','Filled tonal',156],['elevated','Elevated',124],
      ['outlined','Outlined',130],['text','Text',96]];
    let x=24,y=116;
    for(const [variant,label,bw] of variants){
      if(x+bw>cw-24){x=24;y+=60;}
      place(M.button({key:`lab:button:${variant}`,variant,label,width:bw,onClick:action(variant)}),x,y,bw,40);
      x+=bw+15;
    }
    sep('button-sep',y+84);
    H('head:icons','Icon buttons',y+105);
    D('sub:icons','Standard, filled, filled tonal and outlined',y+141);
    const variants2=['standard','filled','filledTonal','outlined'];
    for(let i=0;i<variants2.length;i++){
      place(M.iconButton({key:`lab:icon:${i}`,variant:variants2[i],icon:i%2?'search':'check',
        onClick:action('icon')}),24+i*80,y+189,40,40);
    }
    H('head:fab','Floating action buttons',y+260);
    place(M.fab({key:'lab:fab',icon:'user-plus',onClick:action('fab')}),24,y+311,56,56);
    place(M.fab({key:'lab:fab:extended',icon:'user-plus',label:'Create',width:150,
      onClick:action('extended')}),102,y+311,150,56);
    sep('fab-sep',y+400);
    H('head:buttons-disabled','States',y+420);
    place(M.button({key:'lab:disabled',variant:'filled',label:'Disabled',disabled:true,width:130}),24,y+479,130,40);
    place(M.button({key:'lab:active',variant:'outlined',label:'Enabled',width:130,
      onClick:action('enabled')}),174,y+479,130,40);
  } else if(page==='selection'){
    H('sel:head','Selection controls',20);D('sel:sub','Switches, checkboxes and radio buttons',61);
    const y=126;
    detail.push(T('sel:sw-label','Wi-Fi',24,y,148,40,{size:17}));
    place(M.switch({key:'lab:switch',selected:!!data.enabled,icons:true,onClick:'lab:toggle'}),175,y+4,52,32);
    detail.push(T('sel:checkbox-label','Remember selection',273,y,190,40,{size:17}));
    place(M.checkbox({key:'lab:checkbox',selected:!!data.checked,onClick:'lab:check'}),484,y,40,40);
    sep('sel:sep',212);
    H('sel:radio-head','Radio buttons',240);
    for(let i=0;i<3;i++){
      const k=`lab:radio:${i}`,x=24+i*185;
      place(M.radio({key:k,selected:ui.num(data.radio,0)===i,onClick:k}),x,301,40,40);
      detail.push(T(`${k}:title`,['Daily','Weekly','Monthly'][i],x+48,301,115,40,{size:16}));
    }
    sep('sel:sep2',380);
    H('sel:chips-head','Chips',402);
    D('sel:chips-sub','Filter chips: selected / unselected; Assist and Suggestion',443);
    const chips=[['all','All',80],['design','Design',116],['code','Code',96],['assist','Assist',104]];
    let xx=24;
    for(const [id,label,bw] of chips){
      place(M.chip({key:`lab:chip:${id}`,label,width:bw,selected:(data.chip||'all')===id,
        onClick:`lab:chip:${id}`}),xx,502,bw,32);xx+=bw+14;
    }
    sep('sel:sep3',580);
    H('sel:segment-head','Connected button group',603);
    place(M.connectedButtonGroup({key:'lab:segments',width:Math.min(480,cw-60),selected:data.segment||'week',
      items:[{id:'day',label:'Day',onClick:'lab:segment:day'},
        {id:'week',label:'Week',onClick:'lab:segment:week'},
        {id:'month',label:'Month',onClick:'lab:segment:month'}]}),24,659,Math.min(480,cw-60),40);
  } else if(page==='inputs'){
    H('inp:head','Text fields and sliders',20);
    D('inp:sub','Material Web text-field geometry • 56dp • Roboto',61);
    place(M.textField({key:'lab:field:filled',variant:'filled',width:Math.min(380,cw-55),
      label:'Nickname',value:data.value||'',caretOffset:data.caretOffset,focused:data.focus==='filled',onClick:'lab:focus:filled'}),
      24,122,Math.min(380,cw-55),56);
    place(M.textField({key:'lab:field:outlined',variant:'outlined',width:Math.min(380,cw-55),
      label:'Nickname',value:data.value||'',caretOffset:data.caretOffset,focused:data.focus==='outlined',onClick:'lab:focus:outlined'}),
      24,202,Math.min(380,cw-55),56);
    D('inp:keyboard','Text input uses the existing Combatant editor.',280);
    sep('inp:sep',338);
    H('inp:slider-head','Sliders',367);
    D('inp:slider-sub','Drag the handle, click the track or use keyboard arrows.',411);
    place(M.slider({key:'lab:slider',width:Math.min(500,cw-60),value:ui.num(data.slider,45)}),24,468,Math.min(500,cw-60),40);
    D('inp:slider-value',`Value ${ui.num(data.slider,45).toFixed(0)} / 100`,522);
    sep('inp:sep2',574);
    H('inp:progress','Progress indicators',602);
    place(M.progress({key:'lab:progress',width:Math.min(500,cw-60),value:ui.num(data.slider,45)/100}),
      24,665,Math.min(500,cw-60),8);
    sep('inp:select-sep',720);
    H('inp:select-head','Select',743);
    D('inp:select-sub','Choose an option from an anchored menu.',782);
    const valueNames={system:'System default',light:'Light',dark:'Dark',dynamic:'Dynamic colors'};
    place(M.select({key:'lab:select-field',label:'Appearance',variant:'outlined',
      width:Math.min(340,cw-55),value:valueNames[data.selectValue]||valueNames.system,
      onClick:'lab:overlay:select'}),24,828,Math.min(340,cw-55),56);
  } else if(page==='navigation'){
    H('nav:head','Navigation components',20);
    D('nav:sub','Primary tabs and composed navigation',61);
    place(M.tabs({key:'lab:tabs',width:Math.min(570,cw-40),selected:data.tab||'overview',
      items:[{id:'overview',label:'Overview',onClick:'lab:tab:overview'},
        {id:'activity',label:'Activity',onClick:'lab:tab:activity'},
        {id:'settings',label:'Settings',onClick:'lab:tab:settings'}]}),24,124,Math.min(570,cw-40),48);
    sep('nav:top-separator',204);
    // Rail and its companion list are independent components, not a single
    // 900px-high fixed-position column. On narrow previews the same examples
    // stack and are reachable through the host's retained scroll container.
    const sideBySide=cw>=660;
    const listX=sideBySide?Math.min(310,Math.round(cw*0.34)):24;
    const listY=sideBySide?300:611;
    const listWidth=Math.max(130,Math.min(490,cw-listX-24));
    H('nav:rail-head','Navigation rail',242);
    place(M.navigationRail({key:'lab:rail',selected:data.rail||'home',items:[
      {id:'home',label:'Home',icon:'material3/home',onClick:'lab:rail:home'},
      {id:'favorites',label:'Saved',icon:'heart-pulse',onClick:'lab:rail:favorites'},
      {id:'settings',label:'Settings',icon:'material3/settings',onClick:'lab:rail:settings'}
    ]}),24,301,96,260);
    if(sideBySide) {
      detail.push(M.shape('nav:column-rule',listX-22,249,1,322,0,P.outlineVariant));
      detail.push(T('nav:list-head','List items',listX,242,listWidth,35,{size:24,medium:true}));
    } else {
      sep('nav:stack-rule',577);
      H('nav:list-head','List items',585);
    }
    for(let i=0;i<3;i++){
      const id=['profile','notifications','settings'][i];
      const supporting=i===1?'Choose what you want to receive':undefined;
      place(M.listItem({key:`lab:list:${id}`,width:listWidth,
        title:['Profile','Notifications','Preferences'][i],
        supporting,icon:['user','heart-pulse','palette'][i],selected:data.list===id,
        onClick:`lab:list:${id}`}),listX,listY+i*78,listWidth,supporting?72:56);
    }
  } else if(page==='surfaces'){
    H('surf:head','Cards',20);D('surf:sub','Material 3 filled, elevated and outlined surfaces',61);
    const variants=['filled','elevated','outlined'];let y=122;
    for(let i=0;i<3;i++){
      const width=Math.min(280,(cw-90)/2),x=i%2===0?24:width+48;
      if(i===2)y=300;
      place(M.card({key:`lab:card:${i}`,variant:variants[i],width,height:150,
        title:variants[i][0].toUpperCase()+variants[i].slice(1)+' card',
        body:'Native Material surface'}),x,y,width,150);
    }
    sep('surf:sep',495);
    H('surf:head2','List and action composition',521);
    D('surf:desc2','Surface roles, outlines and typography remain project-specific',566);
    place(M.button({key:'surf:save',label:'Save',variant:'filled',width:115,
      onClick:action('save')}),24,620,115,40);
    place(M.button({key:'surf:cancel',label:'Cancel',variant:'text',width:115,
      onClick:action('cancel')}),154,620,115,40);
  } else if(page==='overlays'){
    H('overlay:head','Menus and dialogs',20);
    D('overlay:sub','Temporary surfaces render above the page and its scroll clip.',61);
    place(M.button({key:'lab:menu-trigger',label:'Open menu',variant:'outlined',width:152,
      onClick:'lab:overlay:menu'}),24,124,152,40);
    place(M.button({key:'lab:dialog-trigger',label:'Open dialog',variant:'filled',width:152,
      onClick:'lab:overlay:dialog'}),195,124,152,40);
    sep('overlay:sep',211);
    H('overlay:search-head','Search',240);
    D('overlay:search-desc','Live input and local suggestions',282);
    place(M.searchBar({key:'lab:search:field',width:Math.min(490,cw-55),
      value:data.search||'',focused:data.focus==='search',onClick:'lab:focus:search'}),
      24,334,Math.min(490,cw-55),56);
    const q=ui.str(data.search,'').trim().toLowerCase();
    const results=['Account','Appearance','Audio','Controls','Language','Notifications','Privacy'];
    const filtered=results.filter(v=>!q||v.toLowerCase().includes(q)).slice(0,5);
    if(data.focus==='search'||q){
      for(let i=0;i<filtered.length;i++){
        const name=filtered[i],y=414+i*52;
        place(M.listItem({key:`lab:result:${name.toLowerCase()}`,width:Math.min(490,cw-55),
          title:name,onClick:`lab:result:${name.toLowerCase()}`}),24,y,Math.min(490,cw-55),52);
      }
      if(!filtered.length) D('overlay:noresults','No results',420);
    }
    sep('overlay:sep2',724);
    H('overlay:badge-head','Badges',752);
    place(M.badge({key:'lab:badge:number',label:'3'}),24,820,24,16);
    place(M.badge({key:'lab:badge:dot'}),98,825,6,6);
    sep('overlay:sep3',872);
    H('overlay:feedback-head','Feedback',892);
    D('overlay:feedback-desc','Tooltip on hover; snackbar with action and timeout.',934);
    place(M.button({key:'lab:tooltip:trigger',label:'Hover for tooltip',variant:'outlined',
      width:190,onClick:'lab:tooltip:noop'}),24,990,190,40);
    place(M.button({key:'lab:snackbar:trigger',label:'Show snackbar',variant:'filled',
      width:162,onClick:'lab:snackbar:show'}),238,990,162,40);
  } else if(page==='states'){
    H('states:head','Component states',20);D('states:sub','Selected, unselected, disabled, hover and pressed',61);
    place(M.button({key:'states:enabled',label:'Enabled',width:142,onClick:action('enabled')}),24,126,142,40);
    place(M.button({key:'states:disabled',label:'Disabled',width:142,disabled:true}),188,126,142,40);
    place(M.button({key:'states:outlined',label:'Outlined',variant:'outlined',width:142,
      onClick:action('outlined')}),352,126,142,40);
    sep('states:sep',220);
    for(let i=0;i<2;i++){
      place(M.switch({key:`states:switch:${i}`,selected:!!i,disabled:!!(i===0)}),24+i*118,265,52,32);
      place(M.checkbox({key:`states:checkbox:${i}`,selected:!!i,disabled:!!(i===0)}),24+i*118,338,40,40);
      place(M.radio({key:`states:radio:${i}`,selected:!!i,disabled:!!(i===0)}),24+i*118,402,40,40);
    }
    sep('states:sep2',498);
    H('states:head2','Interaction log',525);
    D('states:counter',`Events: ${ui.num(data.clicks,0)} • Last: ${ui.str(data.last,'none')}`,572);
  }
  return detail;
}
