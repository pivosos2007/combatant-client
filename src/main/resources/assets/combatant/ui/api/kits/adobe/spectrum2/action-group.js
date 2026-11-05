/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumActionButton } from './action-button.js';

export class SpectrumActionGroup {
  static render(props={}) {
    const key=ui.str(props.key,'s2:action-group'),p=SpectrumFoundation.colors(props.colors);
    const items=Array.isArray(props.items)?props.items:[];
    const width=Math.max(64,ui.num(props.width,380));
    const vertical=props.orientation==='vertical';
    const compact=props.density==='compact';
    const quiet=!!(props.isQuiet??props.quiet);
    const connected=compact&&!quiet;
    const justified=!!(props.isJustified??props.justified);
    const disabled=!!(props.isDisabled??props.disabled);
    const emphasized=!!(props.isEmphasized??props.emphasized);
    const mode=ui.str(props.selectionMode,'none');
    const selectable=props.enableSelection===undefined?mode!=='none':!!props.enableSelection;
    const selected=Array.isArray(props.selectedIds)?props.selectedIds:[];
    const size=ui.str(props.size,'M').toUpperCase();
    const height=({XS:24,S:32,M:36,L:44,XL:52})[size]||36;
    const gap=compact?0:8, rowGap=compact?7:8;
    const safeItems=items.map((item,i)=>({id:ui.str(item.id,`item-${i}`),
      label:ui.str(item.label,''),icon:ui.str(item.icon,''),
      width:Math.max(44,ui.num(item.width,Math.max(72,ui.str(item.label,'').length*8+32))),
      disabled:disabled||!!item.disabled,onClick:item.onClick}));
    const children=[],frames=[],separators=[],rows=[];
    // Layout first, then create one frame per contiguous row. No per-frame JS state.
    if(vertical){
      safeItems.forEach((item,i)=>rows.push({items:[{...item,x:0,width}],y:i*(height+gap),width}));
    }else{
      const equal=justified?Math.max(44,(width-gap*(safeItems.length-1))/Math.max(1,safeItems.length)):0;
      let row={items:[],y:0,width:0};
      for(const item of safeItems){
        const w=Math.min(width,justified?equal:item.width);
        const extra=row.items.length?gap:0;
        if(row.items.length&&row.width+extra+w>width+.01){rows.push(row);row={items:[],
          y:row.y+height+rowGap,width:0};}
        const x=row.width+(row.items.length?gap:0);
        row.items.push({...item,x,width:w});row.width=x+w;
      }
      if(row.items.length)rows.push(row);
    }
    if(connected&&vertical&&safeItems.length){
      frames.push(ui.shape({key:`${key}:frame`,shape:'box',
        style:ui.absolute(0,0,width,rows.length*height),
        corners:ui.corner.all(ui.corner.rounded(7)),
        fill:'#00000000',stroke:p.border,strokeWidth:1,interactive:false}));
    }
    for(let r=0;r<rows.length;r++){
      const row=rows[r];
      if(connected&&!vertical){
        frames.push(ui.shape({key:`${key}:frame:${r}`,shape:'box',
          style:ui.absolute(0,row.y,row.width,height),
          corners:ui.corner.all(ui.corner.rounded(7)),
          fill:'#00000000',stroke:p.border,strokeWidth:1,interactive:false}));
      }
      for(let i=0;i<row.items.length;i++){
        const item=row.items[i];
        const first=vertical?r===0:i===0;
        const last=vertical?r===rows.length-1:i===row.items.length-1;
        const round=ui.corner.rounded(7),square=ui.corner.square();
        const corners=vertical?ui.corner.mixed(first?round:square,first?round:square,
            last?round:square,last?round:square)
          :ui.corner.mixed(first?round:square,last?round:square,
            last?round:square,first?round:square);
        const node=SpectrumActionButton.render({key:`${key}:item:${item.id}`,
          width:item.width,size,quiet,connected,emphasized,colors:p,
          icon:item.icon,label:item.label,
          corners:connected?corners:undefined,
          selected:selectable&&selected.includes(item.id),
          disabled:item.disabled,onClick:item.onClick});
        children.push({...node,style:ui.absolute(item.x,row.y,item.width,height)});
        if(connected&&(vertical?r>0:i>0))separators.push(ui.shape({
          key:`${key}:separator:${item.id}`,shape:'rect',interactive:false,
          style:vertical?ui.absolute(1,row.y,width-2,1)
            :ui.absolute(item.x,row.y+1,1,height-2),fill:p.border}));
      }
    }
    children.push(...frames,...separators);
    const totalHeight=vertical?(rows.length*height+Math.max(0,rows.length-1)*gap)
      :rows.length?(rows[rows.length-1].y+height):height;
    return SpectrumFoundation.surface(key,width,totalHeight,children);
  }
}
