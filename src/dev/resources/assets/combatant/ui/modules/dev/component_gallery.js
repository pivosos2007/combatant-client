/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Neutral dev component catalog. Each kit owns its own renderer and tokens. */
import { renderGoogleMaterial3Gallery } from '../../api/kits/google/material3/gallery.js';
import { renderGoogleMaterial3Overlay } from '../../api/kits/google/material3/overlays.js';
import { renderAdobeSpectrum2Gallery } from '../../api/kits/adobe/spectrum2/gallery.js';
import { renderAdobeSpectrum2Overlay } from '../../api/kits/adobe/spectrum2/overlays.js';
const host = Object.freeze({
  background:'#141218',panel:'#201F25',content:'#141318',border:'#49464F',
  text:'#E6E0E9',muted:'#CAC4D0',active:'#494454'
});
const kits = [
  {id:'google',label:'Google Material 3',groups:[['Components',[
    ['buttons','Buttons'],['selection','Selection'],['inputs','Inputs'],
    ['navigation','Navigation'],['surfaces','Surfaces'],['overlays','Overlays'],['states','States']
  ]]],render:renderGoogleMaterial3Gallery,overlay:renderGoogleMaterial3Overlay},
  {id:'adobe',label:'Adobe Spectrum 2',groups:[
    ['Actions', [['buttons','Buttons'],['action-bar','Action bar'],['button-group','Button group'],['action-buttons','Action buttons'],['action-group','Action group'],['toggle-button','Toggle button'],['toggle-button-group','Toggle button group'],['links','Links'],['link-button','Link button']]],
    ['Selection', [['checkbox','Checkbox'],['checkbox-group','Checkbox group'],['select-box-group','Select box group'],['segmented-control','Segmented control'],['switch','Switch'],['radio','Radio'],['radio-group','Radio group']]],
    ['Inputs', [['text-field','Text field'],['search-field','Search field'],['picker','Picker'],['combo-box','Combo box'],['number-field','Number field'],['date-picker','Date picker'],['calendar','Calendar'],['slider','Slider'],['range-slider','Range slider'],['color-area','Color area'],['color-slider','Color slider'],['color-wheel','Color wheel'],['color-field','Color field'],['color-swatch-picker','Color swatch picker']]],
    ['Navigation', [['tabs','Tabs'],['side-navigation','Side navigation'],['breadcrumbs','Breadcrumbs'],['tree-view','Tree view']]],
    ['Disclosure', [['accordion','Accordion'],['disclosure','Disclosure']]],
    ['Feedback', [['inline-alert','Inline alert'],['contextual-help','Contextual help'],['skeleton','Skeleton']]],
    ['Data display', [['table','Table'],['list-view','List view'],['card-view','Card view'],['labeled-value','Labeled value'],['color-swatch','Color swatch'],['illustrated-message','Illustrated message'],['badges','Badges'],['avatars','Avatars'],['avatar-group','Avatar group'],['tags','Tags'],['tag-group','Tag group'],['cards','Cards'],['status-light','Status light'],['progress-bar','Progress bar'],['progress-circle','Progress circle'],['meter','Meter'],['dividers','Dividers']]],
    ['Overlays', [['menu','Menu'],['popover','Popover'],['standard-dialog','Standard dialog'],['alert-dialog','Alert dialog'],['toast','Toast'],['tooltip','Tooltip']]],
    ['States', [['states','Disabled states']]]
  ],render:renderAdobeSpectrum2Gallery,overlay:renderAdobeSpectrum2Overlay}
];
const position = (node,x,y,w,h) => ({...node,style:ui.absolute(x,y,w,h)});
const rectangle = (key,x,y,w,h,r,fill,stroke) => ui.roundedRect({
  key,x,y,w,h,radius:r,fill,stroke,strokeWidth:stroke?1:0,interactive:false
});
const label = (key,value,x,y,w,h,size=16,color=host.text,medium=false) => ui.text({
  key,text:value,color,verticalAlign:'center',interactive:false,
  class:ui.abs(x,y,w,h,`font-${medium?'OnestMedium':'Onest'} font-size-${ui.fmt(size)} text-align-left`)
});
export function buildTemplate(ctx) {
  const data=ctx.props||{};
  const vw=Math.max(1,ui.num(data.width,1920)),vh=Math.max(1,ui.num(data.height,1080));
  const pw=Math.min(1450,Math.max(740,vw-88)),ph=Math.min(920,Math.max(560,vh-112));
  const px=(vw-pw)/2,py=Math.max(40,(vh-ph)/2);
  const kit=kits.find(k=>k.id===data.kit)||kits[0];
  const pages=kit.groups.flatMap(group=>group[1]);
  const page=pages.some(entry=>entry[0]===data.page)?data.page:pages[0][0];
  const sidebar=252,rightX=sidebar+32,cw=pw-rightX-27;
  const children=[
    rectangle('catalog:panel',0,0,pw,ph,20,host.panel,host.border),
    label('catalog:title','Component Library',30,23,pw-64,48,30,host.text,true),
    rectangle('catalog:rule',24,103,pw-48,1,0,host.border),
    rectangle('catalog:side-rule',sidebar+13,121,1,ph-144,0,host.border),
    label('catalog:kits-title','Kits',30,124,sidebar-35,24,14,host.muted,true)
  ];
  let kitY=153;
  for(const entry of kits){
    const active=entry.id===kit.id;
    children.push(rectangle(`catalog:kit:${entry.id}:bg`,22,kitY,sidebar-17,38,9,
      active?host.active:'#00000000'));
    children.push(label(`catalog:kit:${entry.id}:label`,entry.label,37,kitY,sidebar-42,38,16,
      active?host.text:host.muted,active));
    children.push(position(ui.button({key:`catalog:kit:${entry.id}`,onClick:`lab:kit:${entry.id}`,
      class:ui.abs(0,0,sidebar-17,38,'cursor-pointer'),children:[]}),22,kitY,sidebar-17,38));
    kitY+=43;
  }
  const navTop=kitY+31;
  children.push(label('catalog:pages-title','Components',30,navTop-29,sidebar-36,26,14,host.muted,true));
  // One shared search widget, filtering only the currently selected kit.
  // Query is owned by Java's SingleLineTextInput, not by a parallel JS state loop.
  const searchX=28,searchY=navTop+5,searchW=sidebar-38;
  const query=ui.str(data.catalogQuery,'');
  children.push(rectangle('catalog:search:surface',searchX,searchY,searchW,40,7,'#29272F',
    data.catalogFocused?'#9795FF':host.border));
  children.push(position(ui.svg({key:'catalog:search:icon',asset:'search',tint:host.muted,
    interactive:false,class:ui.abs(0,0,17,17)}),searchX+10,searchY+12,17,17));
  // The visible text, selection and IME preedit all share the native editor's
  // horizontal viewport. Only this inset is clipped; the clear action stays hit-testable.
  const insetX=searchX+32,insetW=searchW-68;
  const scroll=Math.max(0,ui.num(data.catalogScroll,0));
  const painted=ui.str(data.catalogDisplay,query);
  const displayW=Math.max(insetW+10,ui.num(data.catalogTextWidth,0)+12);
  const textNodes=[];
  if(data.catalogHasSelection){
    const x=ui.num(data.catalogSelectionStart,0)-scroll;
    const right=ui.num(data.catalogSelectionEnd,0)-scroll;
    textNodes.push(rectangle('catalog:search:selection',x,9,
      Math.max(0,right-x),22,2,'#6555A7'));
  }
  textNodes.push(ui.text({key:'catalog:search:text',text:painted||'Search components…',
    color:painted?host.text:host.muted,verticalAlign:'center',interactive:false,
    style:ui.absolute(-scroll,0,displayW,40),
    class:'font-Onest font-size-15 text-align-left'}));
  if(data.catalogFocused&&ui.num(data.catalogCompositionWidth,0)>0){
    textNodes.push(rectangle('catalog:search:composition-underline',
      ui.num(data.catalogCompositionStart,0)-scroll,32,
      ui.num(data.catalogCompositionWidth,0),1,0,host.text));
  }
  if(data.catalogFocused){
    const cx=ui.num(data.catalogCaretOffset,0)-scroll;
    textNodes.push(rectangle('catalog:search:caret',cx,9,1.5,22,0,host.text));
  }
  children.push(ui.stack({key:'catalog:search:viewport',interactive:false,
    class:ui.abs(insetX,searchY,insetW,40,'clip overflow-hidden'),children:textNodes}));
  children.push(position(ui.button({key:'catalog:search',onClick:'lab:catalog:search:focus',
    class:ui.abs(0,0,searchW,40,'cursor-text'),children:[]}),searchX,searchY,searchW,40));
  if(query){
    children.push(position(ui.button({key:'catalog:search:clear',
      onClick:'lab:catalog:search:clear',class:ui.abs(0,0,28,36,'cursor-pointer'),children:[
        ui.svg({key:'catalog:search:clear-icon',asset:'x',tint:host.muted,interactive:false,
          class:ui.abs(6,9,16,16)})
      ]}),searchX+searchW-30,searchY+2,28,36));
  }
  const navScrollTop=navTop+55;
  const navViewportH=Math.max(90,ph-navScrollTop-32);
  const navItems=[];let navY=0;
  const needle=query.trim().toLocaleLowerCase();
  for(const [group,allEntries] of kit.groups){
    const entries=needle?allEntries.filter(([id,name])=>
      name.toLocaleLowerCase().includes(needle)||id.replace(/-/g,' ').includes(needle)):allEntries;
    if(!entries.length)continue;
    if(kit.groups.length>1){
      navItems.push(label(`catalog:group:${group}`,group,13,navY+7,sidebar-42,29,13,host.muted,true));
      navY+=40;
    }
    for(const [id,name] of entries){
      const active=page===id,action=`lab:nav:${id}`;
      navItems.push(rectangle(`catalog:page:${id}:bg`,2,navY,sidebar-36,36,8,
        active?host.active:'#00000000'));
      navItems.push(label(`catalog:page:${id}:title`,name,17,navY,sidebar-62,36,16,
        active?host.text:host.muted,active));
      navItems.push(position(ui.button({key:action,onClick:action,
        class:ui.abs(0,0,sidebar-36,36,'cursor-pointer'),children:[],
        stateValues:{selected:active}}),2,navY,sidebar-36,36));
      navY+=39;
    }
    navY+=kit.groups.length>1?8:0;
  }
  if(navY===0)navItems.push(label('catalog:search:empty','No matching components',13,16,sidebar-42,50,15,host.muted));
  // The child MUST be in normal flow. Absolute content reports 0 intrinsic height to
  // UiLayoutEngine, which used to make every scroll range 0 (including Disabled controls).
  children.push(ui.scroll({key:'catalog:nav-scroll',
    class:ui.abs(22,navScrollTop,sidebar-19,navViewportH,'clip overflow-hidden'),
    wheelStep:42,scrollbarWidth:3,children:[
      ui.stack({key:'catalog:nav-items',style:ui.style({width:sidebar-19,height:navY+12}),children:navItems})
    ]}));
  children.push(rectangle('catalog:preview',rightX-12,121,cw+24,ph-144,16,host.content));
  const detail=kit.render(data,cw,page);
  const contentHeight=Math.max(ph-165,...detail.map(node=>{
    const s=node.style||{};
    return ui.num(s.top,0)+ui.num(s.height,0)+44;
  }));
  children.push(ui.scroll({key:'lab:content',
    class:ui.abs(rightX,132,cw,ph-165,'clip overflow-hidden'),
    wheelStep:60,scrollbarWidth:4,children:[
      ui.stack({key:'lab:page',style:ui.style({width:cw,height:contentHeight}),children:detail})
    ]}));
  const overlay=kit.overlay?kit.overlay(data,{vw,vh,px,py,pw,ph,cw,rightX}):null;
  return ui.root({key:'component-lab',class:ui.abs(0,0,vw,vh),children:[
    rectangle('catalog:backdrop',0,0,vw,vh,0,host.background),
    ui.stack({key:'catalog:window',class:ui.abs(px,py,pw,ph),children}),
    ...(overlay?[overlay]:[])
  ]});
}
