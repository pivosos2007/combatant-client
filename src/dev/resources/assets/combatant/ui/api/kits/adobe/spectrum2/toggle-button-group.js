/* Combatant Client (GPL-3.0). Native Spectrum 2 ToggleButtonGroup, controlled. */
import { SpectrumFoundation } from './foundation.js';
import { SpectrumToggleButton } from './toggle-button.js';
export class SpectrumToggleButtonGroup {
  static render(props={}) {
    const key=ui.str(props.key,'s2:toggle-group'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(85,ui.num(props.width,420)),items=Array.isArray(props.items)?props.items:[];
    const selected=Array.isArray(props.selectedIds)?props.selectedIds:[];
    const vertical=props.orientation==='vertical',compact=props.density==='compact';
    const quiet=!!props.quiet,justified=!!props.justified,disabled=!!props.disabled;
    const size=ui.str(props.size,'M').toUpperCase(),h=({XS:24,S:32,M:40,L:48,XL:56})[size]||40;
    const gap=compact?0:8,children=[];
    const rows=[];let row=[],rowW=0;
    for(const [index,item] of items.entries()) {
      const iw=vertical?width:Math.min(width,justified?(width-gap*Math.max(0,items.length-1))/Math.max(1,items.length):
        Math.max(66,ui.num(item.width,ui.str(item.label,'').length*8+32)));
      if(vertical){rows.push([item]);continue;}
      if(row.length&&rowW+gap+iw>width){rows.push(row);row=[];rowW=0;}
      row.push({...item,_width:iw,_index:index});rowW+=iw+(row.length>1?gap:0);
    }
    if(row.length)rows.push(row);
    if(vertical)for(let i=0;i<rows.length;i++)rows[i]=[{...rows[i][0],_width:width,_index:i}];
    let y=0;
    for(let ri=0;ri<rows.length;ri++){
      const group=rows[ri];let x=0;
      for(let ci=0;ci<group.length;ci++){
        const item=group[ci],id=ui.str(item.id,`item-${item._index}`),w=item._width;
        const isFirst=vertical?ri===0:ci===0,isLast=vertical?ri===rows.length-1:ci===group.length-1;
        const round=ui.corner.rounded(8),square=ui.corner.square();
        const corners=vertical?ui.corner.mixed(isFirst?round:square,isFirst?round:square,
          isLast?round:square,isLast?round:square):
          ui.corner.mixed(isFirst?round:square,isLast?round:square,
            isLast?round:square,isFirst?round:square);
        children.push({...SpectrumToggleButton.render({key:`${key}:item:${id}`,label:item.label,
          size,width:w,quiet,emphasized:!!props.emphasized,disabled:disabled||!!item.disabled,
          selected:selected.includes(id),connected:compact&&!quiet,
          corners:compact&&!quiet?corners:undefined,onClick:item.onClick}),
          style:ui.absolute(x,y,w,h)});
        if(compact&&!quiet&&(vertical?ri>0:ci>0))children.push(SpectrumFoundation.rect(
          `${key}:separator:${id}`,vertical?1:x,vertical?y:y+1,
          vertical?width-2:1,vertical?1:h-2,0,p.border));
        x+=w+gap;
      }
      if(compact&&!quiet&&!vertical)children.push(ui.shape({key:`${key}:outline:${ri}`,
        shape:'box',style:ui.absolute(0,y,vertical?width:x,h),
        corners:ui.corner.all(ui.corner.rounded(8)),fill:'#00000000',stroke:p.border,
        strokeWidth:1,interactive:false}));
      y+=h+(compact&&!vertical?7:gap);
    }
    if(compact&&!quiet&&vertical&&rows.length)children.push(ui.shape({
      key:`${key}:outline`,shape:'box',style:ui.absolute(0,0,width,rows.length*h),
      corners:ui.corner.all(ui.corner.rounded(8)),fill:'#00000000',stroke:p.border,
      strokeWidth:1,interactive:false}));
    return SpectrumFoundation.surface(key,width,rows.length?y-(compact&&!vertical?7:gap):h,children);
  }
}
