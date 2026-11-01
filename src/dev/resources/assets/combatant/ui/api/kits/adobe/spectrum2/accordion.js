/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Spectrum 2 accordion: flat rows, thin separators and clipped disclosure.
 * Never constructs a rounded-rectangle or a background card. */
import { SpectrumFoundation } from './foundation.js';

const flatQuad = (key,x,y,w,h,fill,extra={}) => ui.shape({
  key,shape:'rect',style:ui.absolute(x,y,w,h),fill,interactive:false,...extra
});
export class SpectrumAccordion {
  static render(props={}) {
    const key=ui.str(props.key,'s2:accordion');
    const p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(180,ui.num(props.width,420));
    const size=ui.str(props.size,'M').toUpperCase();
    const density=ui.str(props.density,'regular');
    const quiet=!!props.quiet;
    const titleSize={S:15,M:17,L:19,XL:21}[size]||17;
    const rowBase={S:40,M:48,L:56,XL:64}[size]||48;
    const spacing=density==='compact'?-8:density==='spacious'?12:0;
    const rowH=Math.max(32,rowBase+spacing);
    const children=[];
    let y=0;
    for(const item of Array.isArray(props.items)?props.items:[]){
      const id=ui.str(item.id,'item'),nodeKey=`${key}:item:${id}`;
      const disabled=!!item.disabled;
      const progress=Math.max(0,Math.min(1,ui.num(item.progress,item.expanded?1:0)));
      const contentH=Math.max(0,ui.num(item.contentHeight,76));
      const panelH=contentH*progress;
      if(!quiet)children.push(flatQuad(`${nodeKey}:divider`,0,y,width,1,p.border));
      const rowChildren=[
        // Flat quad via renderer.quad; the element has NO rounded panel/background.
        flatQuad(`${nodeKey}:hover`,0,0,width,rowH,'#00000000',{
          stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
            alphaTerms:disabled?{}:{hover:.055,press:.09}}}),
        // Use four actual quads for focus. A zero-radius roundedRectStroke is
        // still a rounded-rectangle pipeline; Spectrum accordion rows require
        // straight square edges and no pill/rounded backgrounds at hover.
        ...[
          [1,1,width-2,2], [1,rowH-3,width-2,2],
          [1,3,2,rowH-6], [width-3,3,2,rowH-6]
        ].map(([fx,fy,fw,fh],edge)=>flatQuad(`${nodeKey}:focus:${edge}`,
          fx,fy,fw,fh,'#00000000',{stateSource:'parent',
            fillReactive:{base:p.focus,alphaBase:0,
              alphaTerms:disabled?{}:{focusVisible:1}}})),
        SpectrumFoundation.text(`${nodeKey}:title`,item.title||'',15,0,width-63,rowH,
          {size:titleSize,strong:true,color:disabled?p.disabled:p.text,wrap:true,maxLines:2}),
        ui.svg({key:`${nodeKey}:chevron:right`,asset:'chevron-right',
          tint:SpectrumFoundation.withOpacity(disabled?p.disabled:p.muted,1-progress),
          interactive:false,class:ui.abs(width-36,(rowH-18)/2,18,18)}),
        ui.svg({key:`${nodeKey}:chevron:down`,asset:'chevron-down',
          tint:SpectrumFoundation.withOpacity(disabled?p.disabled:p.muted,progress),
          interactive:false,class:ui.abs(width-36,(rowH-18)/2,18,18)})
      ];
      children.push(ui.button({key:nodeKey,
        class:ui.abs(0,y,width,rowH,SpectrumFoundation.cursor(disabled)),
        ...(disabled?{}:{onClick:item.onClick}),
        stateValues:{disabled},stateMotions:{hover:{durationMs:130,easing:'out-cubic'},
          press:{durationMs:85,easing:'out-cubic'},focusVisible:{durationMs:100,easing:'out-cubic'}},
        children:rowChildren}));
      {
        // Keep this clipped panel mounted even at height zero. The Java owner
        // animates its bounds in-place without re-running the JS catalog.
        const alpha=Math.min(1,progress*2.25);
        children.push(ui.stack({key:`${nodeKey}:panel`,
          class:ui.abs(0,y+rowH,width,panelH,'clip overflow-hidden'),children:[
            SpectrumFoundation.text(`${nodeKey}:content`,item.content||'',16,13,
              width-39,Math.max(1,contentH-16),{size:16,color:
                SpectrumFoundation.withOpacity(disabled?p.disabled:p.muted,alpha),wrap:true,maxLines:4})
          ]}));
      }
      y+=rowH+panelH;
    }
    if(!quiet)children.push(flatQuad(`${key}:bottom-divider`,0,y,width,1,p.border));
    return SpectrumFoundation.surface(key,width,y+(quiet?0:1),children);
  }
}
