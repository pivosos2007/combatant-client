/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';

export class SpectrumTable {
  static rowHeight(density='regular',size='M') {
    const scale=size==='S'?-4:size==='L'?6:0;
    return (density==='compact'?36:density==='spacious'?56:44)+scale;
  }
  static height(props={}) {
    const rows=Array.isArray(props.rows)?props.rows:[];
    return (props.hideHeader?0:44)+SpectrumTable.rowHeight(props.density,props.size)*rows.length+2;
  }
  static render(props={}) {
    const key=ui.str(props.key,'s2:table'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(260,ui.num(props.width,600)),rows=Array.isArray(props.rows)?props.rows:[];
    const columns=(Array.isArray(props.columns)?props.columns:[]).filter(c=>c && c.id);
    const selection=ui.str(props.selectionMode,'none'),check=selection!=='none'&&props.selectionStyle!=='highlight';
    const selectW=check?42:0,avail=Math.max(80,width-selectW);
    const weights=columns.map(c=>Math.max(1,ui.num(c.weight,1)));
    const total=weights.reduce((a,b)=>a+b,0)||1;
    const columnWidths=weights.map(n=>avail*n/total);
    const selected=Array.isArray(props.selectedIds)?props.selectedIds:[];
    const disabled=!!props.disabled,rowH=SpectrumTable.rowHeight(props.density,props.size),headerH=props.hideHeader?0:44;
    const H=headerH+rows.length*rowH+2;
    const children=[];
    const quad=(k,x,y,w,h,fill)=>ui.shape({key:k,shape:'rect',style:ui.absolute(x,y,w,h),fill,interactive:false});
    if(!props.quiet)children.push(SpectrumFoundation.rect(`${key}:surface`,0,0,width,H,8,p.surface,p.border,1));
    if(!props.hideHeader){
      children.push(quad(`${key}:header:fill`,1,1,width-2,headerH-1,p.raised));
      let x=selectW;
      if(check){
        const all=rows.length>0&&rows.filter(r=>!r.disabled).every(r=>selected.includes(ui.str(r.id,'')));
        const some=selected.length>0;
        children.push(ui.button({key:`${key}:select-all`,class:ui.abs(8,8,28,28,SpectrumFoundation.cursor(disabled)),
          stateValues:{disabled},...(disabled||selection!=='multiple'?{}:{onClick:props.onSelectAll}),children:[
            SpectrumFoundation.rect(`${key}:select-all:box`,4,4,18,18,4,all?p.accent:p.field,p.border,1),
            ...(all?[ui.svg({key:`${key}:select-all:check`,asset:'check',tint:p.onAccent,interactive:false,
              class:ui.abs(5,5,16,16)})]:some?[quad(`${key}:select-all:mixed`,9,12,10,2,p.text)]:[])
          ]}));
      }
      columns.forEach((c,i)=>{
        const w=columnWidths[i],sortable=!!c.sortable&&!disabled;
        const active=ui.str(props.sortColumn,'')===c.id;
        const caption=ui.str(c.label,c.id);
        children.push(ui.button({key:`${key}:header:${c.id}`,class:ui.abs(x,1,w,headerH-2,
          sortable?'cursor-pointer':''),
          stateValues:{disabled:!sortable},...(sortable?{onClick:`${ui.str(props.sortAction,'')}${c.id}`}:{}) ,children:[
            SpectrumFoundation.text(`${key}:header:${c.id}:text`,caption,12,0,Math.max(1,w-(sortable?32:18)),headerH,
              {size:15,strong:true,color:p.text,align:c.align==='end'?'right':'left'}),
            ...(sortable?[ui.svg({key:`${key}:header:${c.id}:sort`,asset:active&&props.sortDirection==='desc'?'chevron-down':'chevron-up',
              tint:active?p.text:p.muted,interactive:false,class:ui.abs(w-23,14,15,15)})]:[]),
            ui.shape({key:`${key}:header:${c.id}:hover`,shape:'rect',interactive:false,
              style:ui.absolute(0,0,w,headerH),fill:'#00000000',stateSource:'parent',
              fillReactive:{base:p.text,alphaBase:0,alphaTerms:sortable?{hover:.06,press:.10}:{}}})
          ]}));
        x+=w;
      });
      children.push(quad(`${key}:header:divider`,0,headerH-1,width,1,p.border));
    }
    rows.forEach((row,i)=>{
      const id=ui.str(row.id,String(i)),k=`${key}:row:${id}`,y=headerH+i*rowH;
      const isDisabled=disabled||!!row.disabled,isSelected=selected.includes(id);
      const selectable=selection!=='none'&&!isDisabled;
      children.push(ui.button({key:k,class:ui.abs(0,y,width,rowH,
        isDisabled?SpectrumFoundation.CURSOR_UNSUPPORTED:selection==='none'?'':'cursor-pointer'),
        stateValues:{disabled:isDisabled,selected:isSelected},
        stateMotions:{hover:{durationMs:110,easing:'out-cubic'},press:{durationMs:80,easing:'out-cubic'}},
        ...(selectable?{onClick:`${ui.str(props.rowAction,'')}${id}`}:{}) ,children:[
          quad(`${k}:selected`,0,0,width,rowH,isSelected?p.selected:'#00000000'),
          ui.shape({key:`${k}:hover`,shape:'rect',interactive:false,
            style:ui.absolute(0,0,width,rowH),fill:'#00000000',stateSource:'parent',
            fillReactive:{base:p.text,alphaBase:0,
              alphaTerms:isDisabled?{}:{hover:.055,press:.10}}}),
          ui.shape({key:`${k}:focus`,shape:'rect',interactive:false,
            style:ui.absolute(1,1,width-2,rowH-2),fill:'#00000000',
            stroke:p.focus,strokeWidth:2,stateSource:'parent',
            strokeReactive:{base:p.focus,alphaBase:0,
              alphaTerms:isDisabled?{}:{focusVisible:1}}}),
          ...(()=>{
            const nodes=[];
            if(check){
              nodes.push(SpectrumFoundation.rect(`${k}:checkbox`,14,(rowH-18)/2,18,18,4,
                isSelected?p.accent:p.field,isDisabled?p.disabled:p.border,1));
              if(isSelected)nodes.push(ui.svg({key:`${k}:check`,asset:'check',tint:p.onAccent,
                interactive:false,class:ui.abs(15,(rowH-16)/2,16,16)}));
            }
            let x=selectW;
            columns.forEach((c,j)=>{
              const w=columnWidths[j],value=row[c.id]===null||row[c.id]===undefined?'–':String(row[c.id]);
              nodes.push(SpectrumFoundation.text(`${k}:cell:${c.id}`,value,x+12,0,Math.max(1,w-24),rowH,
                {size:16,strong:!!c.rowHeader,color:isDisabled?p.disabled:p.text,
                  align:c.align==='end'?'right':'left'}));
              x+=w;
            });
            return nodes;
          })()
        ]}));
      children.push(quad(`${k}:divider`,0,y+rowH-1,width,1,p.border));
    });
    return ui.stack({key,class:ui.abs(0,0,width,H),children});
  }
}
