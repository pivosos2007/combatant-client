/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Native Spectrum 2 Tree View: independent hierarchical component, not Breadcrumbs.
 * Expansion, selection and focus are controlled by the host state. */
import { SpectrumFoundation } from './foundation.js';

const quad=(key,x,y,w,h,fill,extra={})=>ui.shape({key,shape:'rect',
  style:ui.absolute(x,y,w,h),fill,interactive:false,...extra});
export class SpectrumTreeView {
  // Public helper shared by the host and gallery; returns only currently visible rows.
  static visibleRows(items,expanded=[],level=0,rows=[]) {
    const expandedSet=expanded instanceof Set?expanded:new Set(expanded);
    for(const item of Array.isArray(items)?items:[]){
      rows.push({item,level});
      if(Array.isArray(item.children)&&item.children.length&&expandedSet.has(item.id))
        SpectrumTreeView.visibleRows(item.children,expandedSet,level+1,rows);
    }
    return rows;
  }
  static render(props={}) {
    const key=ui.str(props.key,'s2:tree'),palette=SpectrumFoundation.colors(props.colors);
    const width=Math.max(180,ui.num(props.width,450));
    const rowH=Math.max(34,ui.num(props.rowHeight,40));
    const selected=ui.str(props.selected,'');
    const rows=SpectrumTreeView.visibleRows(props.items,props.expanded||[]);
    const children=[];
    rows.forEach(({item,level},i)=>{
      const id=ui.str(item.id,'item'),k=`${key}:item:${id}`;
      const y=i*rowH,indent=Math.min(width-105,Math.max(0,level*24));
      const disabled=!!item.disabled,hasChildren=Array.isArray(item.children)&&item.children.length>0;
      const expanded=hasChildren&&(props.expanded||[]).includes(id);
      const isSelected=id===selected;
      // Text-only leaves are supported by Spectrum; no fabricated file icon asset.
      const icon=item.icon||(hasChildren?(expanded?'folder-open':'folder'):null);
      const textX=indent+(icon?47:27);
      // The selection/hover background is a flat quad, not a rounded card.
      children.push(quad(`${k}:selection`,0,y,width,rowH,isSelected?palette.selected:'#00000000'));
      children.push(ui.button({key:k,class:ui.abs(0,y,width,rowH,
        SpectrumFoundation.cursor(disabled)),stateValues:{disabled,selected:isSelected},
        ...(disabled?{}:{onClick:`${ui.str(props.selectAction,'lab:adobe:tree:select:')}${id}`}),
        stateMotions:{hover:{durationMs:110,easing:'out-cubic'},
          press:{durationMs:75,easing:'out-cubic'},focusVisible:{durationMs:100,easing:'out-cubic'}},
        children:[
          quad(`${k}:hover`,0,0,width,rowH,'#00000000',{
            stateSource:'parent',fillReactive:{base:palette.text,alphaBase:0,
              alphaTerms:disabled?{}:{hover:.055,press:.10}}}),
          ui.shape({key:`${k}:focus`,shape:'rect',interactive:false,
            style:ui.absolute(1,1,width-2,rowH-2),fill:'#00000000',
            stroke:palette.focus,strokeWidth:2,stateSource:'parent',
            strokeReactive:{base:palette.focus,alphaBase:0,
              alphaTerms:disabled?{}:{focusVisible:1}}}),
          ...(icon?[ui.svg({key:`${k}:icon`,asset:icon,
            tint:disabled?palette.disabled:palette.muted,interactive:false,
            class:ui.abs(indent+26,(rowH-17)/2,17,17)})]:[]),
          SpectrumFoundation.text(`${k}:label`,item.label||'',textX,0,
            Math.max(1,width-textX-12),rowH,{size:17,color:disabled?palette.disabled:palette.text,
              strong:isSelected})
        ]}));
      if(hasChildren)children.push(ui.button({key:`${k}:toggle`,
        class:ui.abs(indent,y,25,rowH,SpectrumFoundation.cursor(disabled)),
        stateValues:{disabled},
        ...(!disabled?{onClick:`${ui.str(props.toggleAction,'lab:adobe:tree:toggle:')}${id}`}:{}) ,
        children:[ui.svg({key:`${k}:chevron`,asset:expanded?'chevron-down':'chevron-right',
          tint:disabled?palette.disabled:palette.muted,interactive:false,
          class:ui.abs(4,(rowH-17)/2,17,17)})]}));
    });
    return SpectrumFoundation.surface(key,width,rows.length*rowH,children);
  }
}
