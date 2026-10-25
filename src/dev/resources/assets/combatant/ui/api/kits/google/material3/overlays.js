/* Google Material 3 top-layer examples; the kit owns drawing, the host owns
 * input/focus/lifecycle. Matches the Material Web menu/selection semantics. */
import { GoogleMaterial3 } from './components.js';
const M3OverlayKit = GoogleMaterial3;
const M3OverlayPalette = M3OverlayKit.dark;
const m3OverlayAt = (node,x,y,w,h) => ({...node,style:ui.absolute(x,y,w,h)});
const m3OverlayRect = (key,x,y,w,h,r,fill) => M3OverlayKit.shape(key,x,y,w,h,r,fill);

// Anchored surfaces stay in the viewport, flip on each axis independently and
// are never descendants of the scrollable contents that opened them.
function m3PlacePopup(anchorX,anchorY,anchorWidth,anchorHeight,w,h,vw,vh,gap=4) {
  const margin=12;
  let x=anchorX;
  if(x+w>vw-margin) x=anchorX+anchorWidth-w;
  x=Math.max(margin,Math.min(vw-margin-w,x));
  let y=anchorY+anchorHeight+gap;
  if(y+h>vh-margin) y=anchorY-h-gap;
  y=Math.max(margin,Math.min(vh-margin-h,y));
  return {x,y};
}
export function renderGoogleMaterial3Overlay(data,viewport) {
  const mode=ui.str(data.overlay,'');
  const showSnack=!!data.snackbar,showTip=!mode&&!!data.tooltip;
  if(!mode&&!showSnack&&!showTip)return null;
  const vw=viewport.vw,vh=viewport.vh;
  const nodes=[];
  if(mode){
    nodes.push(m3OverlayRect('m3:overlay:scrim',0,0,vw,vh,0,
      mode==='dialog'?'#61000000':'#00000000'));
    nodes.push(ui.button({key:'m3:overlay:outside',onClick:'lab:overlay:close',
      class:ui.abs(0,0,vw,vh,'cursor-pointer'),children:[]}));
  }
  if(mode==='dialog') {
    const w=Math.min(440,Math.max(280,vw-48)),h=238,x=Math.round((vw-w)/2),y=Math.round((vh-h)/2);
    const focus=Math.max(0,Math.min(1,ui.num(data.overlayIndex,0)));
    nodes.push(m3OverlayAt(M3OverlayKit.dialog({key:'m3:dialog',width:w,height:h,
      title:'Delete saved filter?',body:'This action will remove the selected filter.',
      onBackgroundClick:'lab:overlay:consume',
      actions:[{label:'Cancel',variant:'text',width:96,onClick:'lab:dialog:cancel',focusVisible:focus===0},
        {label:'Delete',variant:'filled',width:102,onClick:'lab:dialog:confirm',focusVisible:focus===1}]
    }),x,y,w,h));
  } else if(mode==='select') {
    const items=[
      {id:'system',label:'System default',onClick:'lab:select:system'},
      {id:'light',label:'Light',onClick:'lab:select:light'},
      {id:'dark',label:'Dark',onClick:'lab:select:dark'},
      {id:'dynamic',label:'Dynamic colors',onClick:'lab:select:dynamic'}
    ];
    const w=Math.max(220,Math.min(290,ui.num(data.anchorWidth,256)));
    const h=M3OverlayKit.menuMetrics(items).height;
    const {x,y}=m3PlacePopup(ui.num(data.anchorX,0),ui.num(data.anchorY,0),
      ui.num(data.anchorWidth,0),ui.num(data.anchorHeight,0),w,h,vw,vh);
    nodes.push(m3OverlayAt(M3OverlayKit.menu({key:'m3:popup',width:w,items:items.map(item=>({...item,
      selected:item.id===data.selectValue,selection:'radio',checked:item.id===data.selectValue})),
      keyboardIndex:ui.num(data.overlayIndex,0),onBackgroundClick:'lab:overlay:consume'}),x,y,w,h));
  } else if(mode==='menu') {
    const rootItems=Array.isArray(data.menuItems)?data.menuItems:[];
    const w=304,rootMetrics=M3OverlayKit.menuMetrics(rootItems),h=rootMetrics.height;
    const {x,y}=m3PlacePopup(ui.num(data.anchorX,0),ui.num(data.anchorY,0),
      ui.num(data.anchorWidth,0),ui.num(data.anchorHeight,0),w,h,vw,vh);
    const withActions=rootItems.map(item=>(item.kind==='divider'||item.kind==='header')?item:
      {...item,onClick:item.disabled?undefined:`lab:menu:root:${item.id}`});
    nodes.push(m3OverlayAt(M3OverlayKit.menu({key:'m3:popup',items:withActions,width:w,
      keyboardIndex:ui.num(data.overlayIndex,-1),
      onBackgroundClick:'lab:overlay:consume'}),x,y,w,h));
    const subItems=Array.isArray(data.menuSubItems)?data.menuSubItems:[];
    if(data.submenu&&subItems.length){
      const subWidth=252,subHeight=M3OverlayKit.menuMetrics(subItems).height;
      const row=rootMetrics.rows.find(r=>r.item.id===data.submenu);
      if(row){
        // Root row alignment, with a true horizontal flip at viewport edge.
        let sx=x+w+4;
        if(sx+subWidth>vw-12)sx=x-subWidth-4;
        sx=Math.max(12,Math.min(vw-12-subWidth,sx));
        const sy=Math.max(12,Math.min(vh-12-subHeight,y+row.y-8));
        nodes.push(m3OverlayAt(M3OverlayKit.menu({key:'m3:submenu',items:subItems.map(item=>({
          ...item,onClick:item.disabled?undefined:`lab:menu:sub:${item.id}`})),
          width:subWidth,keyboardIndex:ui.num(data.submenuIndex,-1),
          onBackgroundClick:'lab:overlay:consume'}),sx,sy,subWidth,subHeight));
      }
    }
  }
  if(showTip){
    const tip=M3OverlayKit.tooltip({key:'m3:tooltip',label:ui.str(data.tooltip,'Tooltip')});
    const tw=Math.max(48,Math.min(240,24+ui.str(data.tooltip,'').length*7.6));
    const tx=Math.max(12,Math.min(vw-tw-12,ui.num(data.tooltipX,0)));
    const ty=Math.max(12,Math.min(vh-40,ui.num(data.tooltipY,0)-36));
    nodes.push(m3OverlayAt(tip,tx,ty,tw,28));
  }
  if(showSnack&&!mode){
    const sw=Math.min(400,vw-48),sx=(vw-sw)*.5,sy=Math.max(12,vh-80);
    nodes.push(m3OverlayAt(M3OverlayKit.snackbar({key:'m3:snackbar',width:sw,
      message:ui.str(data.snackMessage,'Item saved'),actionLabel:'Undo',
      onAction:'lab:snackbar:undo',onDismiss:'lab:snackbar:dismiss'}),sx,sy,sw,48));
  }
  return ui.stack({key:'m3:overlay:root',interactive:false,
    class:ui.abs(0,0,vw,vh),children:nodes});
}
