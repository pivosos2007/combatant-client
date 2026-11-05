/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* Spectrum 2 settings for Litematica. Separate from the schematic canvas. */
import { SpectrumFoundation } from '../../api/kits/adobe/spectrum2/foundation.js';
import { SpectrumActionButton } from '../../api/kits/adobe/spectrum2/action-button.js';
import { SpectrumSearchField } from '../../api/kits/adobe/spectrum2/search-field.js';
import { SpectrumButton } from '../../api/kits/adobe/spectrum2/button.js';
const S=SpectrumFoundation;
const A=v=>Array.isArray(v)?v:[];
const n=(x,def=0)=>Number.isFinite(Number(x))?Number(x):def;
const go=id=>'lite-settings:'+id;
const bg=(key,x,y,w,h,color,stroke,rad=0)=>S.rect(key,x,y,Math.max(1,w),Math.max(1,h),rad,color,stroke,stroke?1:0);
const tx=(key,text,x,y,w,h,color,size=16,bold=false)=>S.text(key,String(text??''),x,y,Math.max(1,w),Math.max(1,h),{color,size,strong:bold});
const at=(component,x,y,w,h)=>({...component,style:ui.absolute(x,y,Math.max(1,w),Math.max(1,h))});
function action(id,icon,x,y,w,p,selected=false){
  return at(SpectrumActionButton.render({key:id,icon,width:w,size:'M',quiet:false,selected,colors:p,onClick:go(id)}),x,y,w,37);
}
function button(key,label,x,y,w,p,action,outline=true){
  return at(SpectrumButton.render({key,label,width:w,size:'S',variant:'secondary',outline,colors:p,onClick:go(action)}),x,y,w,34);
}
export function buildTemplate(ctx){
  const d=ctx.props||{},w=Math.max(100,n(d.width,900)),h=Math.max(140,n(d.height,650)),p=S.colors(d.colors||{});
  const embedded=!!d.embedded;
  const rail=embedded?0:Math.min(210,Math.max(150,w*.20)),right=Math.max(1,w-rail);
  const condensedHeader=right<580;
  const nodes=[bg('root:surface',0,0,w,h,p.background),
    ...(!embedded?[bg('root:rail',0,0,rail,h,p.surface),bg('root:separator',rail-1,0,1,h,p.border)]:[]),
    tx('root:title',embedded?(d.categoryLabel||'General'):'Litematica',rail+24,15,Math.max(90,right-300),37,p.text,24,true),
    ...(condensedHeader?[]:[tx('root:subtitle',embedded?'Litematica settings':'Configuration',rail+24,52,240,24,p.muted,14)]),
    bg('root:top:line',rail,condensedHeader?114:95,right,1,p.border)];
  if(!embedded)A(d.categories).forEach((category,i)=>{
    const y=26+i*55;
    nodes.push(ui.button({key:'category:'+category.id,class:ui.abs(11,y,rail-22,47,'cursor-pointer'),
      onClick:go('category:'+category.id),children:[
        bg('category:'+category.id+':surface',0,0,rail-22,47,category.selected?p.selected:p.surface,
          category.selected?p.accent:undefined,7),
        ui.svg({key:'category:'+category.id+':icon',asset:category.icon,tint:category.selected?p.accent:p.muted,
          interactive:false,class:ui.abs(13,11,22,22)}),
        tx('category:'+category.id+':label',category.label,44,10,rail-72,29,p.text,16,true),
      ]}));
  });
  const searchX=condensedHeader?rail+20:Math.max(rail+18,w-282),searchW=condensedHeader?Math.max(220,Math.min(240,right-40)):240;
  if(right>=266)nodes.push(at(SpectrumSearchField.render({key:'root:search',width:searchW,
    labelHidden:true,focused:!!d.searchFocused,placeholder:'Search settings',
    value:d.search||'',colors:p,onClick:go('search'),onClear:go('search-clear')}),
    searchX,condensedHeader?61:12,searchW,44));
  const narrowEdit=!!d.editing&&right<525;
  const viewX=rail+20,viewY=d.editing?(narrowEdit?225:180):(condensedHeader?126:110),viewW=Math.max(1,w-rail-40),viewH=Math.max(1,h-viewY-15);
  const opt=A(d.options),rowH=70,contentW=Math.max(1,viewW-15);
  const children=[];
  opt.forEach((o,i)=>{
    const y=i*rowH;
    const kind=String(o.type||'string');
    const row=[bg('option:'+i+':surface',0,0,contentW,rowH-1,i%2?p.raised:p.surface),
      tx('option:'+i+':label',o.label,18,10,Math.max(80,contentW-300),27,p.text,17,true),
      tx('option:'+i+':type',kind==='boolean'?'Toggle':kind==='choice'?'Selection':kind==='hotkey'?'Keybind':
        kind==='color'?'Color':kind==='list'?'List':kind==='number'?'Number':'Text',18,40,180,22,p.muted,12)];
    const cw=Math.min(252,Math.max(100,contentW*.37)),cx=contentW-cw-18;
    if(kind==='boolean'){
      row.push(bg('option:'+i+':track',cx+cw-54,17,44,27,o.value==='true'?p.accent:p.field,p.border,14));
      row.push(bg('option:'+i+':knob',cx+cw-(o.value==='true'?32:51),21,19,19,p.text,undefined,10));
    }else{
      row.push(bg('option:'+i+':field',cx,13,cw,40,o.editing?p.selected:p.field,o.editing?p.accent:p.border,6));
      row.push(tx('option:'+i+':value',o.value||'—',cx+11,21,cw-23,27,p.text,15));
    }
    children.push(ui.button({key:'option:'+o.id,class:ui.abs(0,y,contentW,rowH-1,'cursor-pointer clip'),
      onClick:go('option:'+o.id),children:row}));
  });
  if(!opt.length)children.push(tx('empty','No settings match your search',25,26,contentW-50,40,p.muted,17));
  const content=ui.stack({key:`options:content:${d.category||"generic"}`,style:ui.style({width:contentW,height:Math.max(viewH+1,opt.length*rowH+12)}),children});
  nodes.push(ui.scroll({key:`options:viewport:${d.category||"generic"}`,style:ui.absolute(viewX,viewY,viewW,viewH,{overflow:'hidden'}),
    wheelStep:65,smoothScroll:true,scrollSmoothingRate:.021,scrollbar:true,
    scrollbarWidth:8,scrollbarOffset:-10,scrollbarColor:p.muted,children:[content]}));
  if(d.editing){
    const selected=A(d.options).find(o=>o.id===d.editing);
    const bx=rail+20,bw=narrowEdit?Math.max(110,right-40):Math.max(110,Math.min(229,right-252));
    nodes.push(tx('edit:label',`Edit · ${selected?.label||d.editing}`,bx,101,Math.max(1,right-40),21,p.text,15,true));
    nodes.push(bg('edit:field',bx,123,bw,36,p.field,p.accent,5));
    nodes.push(tx('edit:value',selected?.value||'',bx+11,128,bw-22,25,p.text,16));
    const by=narrowEdit?166:123,ax=narrowEdit?bx:bx+bw+8;
    nodes.push(button('edit:apply','Apply',ax,by,84,p,'apply'));
    nodes.push(button('edit:cancel','Cancel',ax+89,by,84,p,'cancel'));
  }else{
    if(!condensedHeader)nodes.push(tx('options:counter',`${opt.length} settings`,rail+25,72,200,21,p.muted,13));
  }
  if(d.error)nodes.push(tx('root:error',d.error,rail+22,h-38,Math.max(1,right-43),29,p.danger,14,true));
  return ui.stack({key:'litematica:settings',style:ui.absolute(0,0,w,h,{overflow:'hidden'}),children:nodes});
}
