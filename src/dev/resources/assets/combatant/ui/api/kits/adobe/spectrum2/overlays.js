/* Spectrum 2 dev-only picker popover; independent of the Google kit. */
import { AdobeSpectrum2 } from './components.js';
const S2Popup = AdobeSpectrum2;
const s2PopupAt=(node,x,y,w,h)=>({...node,style:ui.absolute(x,y,w,h)});
export function renderAdobeSpectrum2Overlay(data,viewport) {
  if(data.overlay!=='adobe-picker')return null;
  const palette=S2Popup.dark;
  const vw=viewport.vw,vh=viewport.vh;
  const width=Math.max(224,Math.min(370,ui.num(data.anchorWidth,370)));
  const items=[
    {id:'low',label:'Low quality'},
    {id:'medium',label:'Balanced'},
    {id:'high',label:'High quality'},
    {id:'ultra',label:'Ultra (unavailable)',disabled:true}
  ];
  const rowHeight=44,margin=12,height=items.length*rowHeight+16;
  const ax=ui.num(data.anchorX,24),ay=ui.num(data.anchorY,24);
  const ah=ui.num(data.anchorHeight,80);
  const x=Math.max(margin,Math.min(vw-margin-width,ax));
  const below=ay+ah+4;
  const top=below+height<=vh-margin?below:ay-height-4;
  const y=Math.max(margin,Math.min(vh-margin-height,top));
  const nodes=[
    ui.button({key:'s2:overlay:outside',class:ui.abs(0,0,vw,vh,'cursor-pointer'),
      onClick:'lab:adobe:picker:close',children:[]}),
    s2PopupAt(ui.stack({key:'s2:picker:popover',class:ui.abs(0,0,width,height),children:[
      S2Popup.rect('s2:picker:popover:bg',0,0,width,height,12,palette.raised,palette.border,1),
      ...items.flatMap((item,i)=>{
        const selected=data.adobePicker===item.id,focused=ui.num(data.overlayIndex,-1)===i;
        const yy=8+i*rowHeight;
        return [ui.button({key:`s2:picker:option:${item.id}`,
          ...(item.disabled?{}:{onClick:`lab:adobe:picker:choose:${item.id}`}),
          class:ui.abs(8,yy,width-16,rowHeight,item.disabled?'':'cursor-pointer'),
          stateValues:{disabled:!!item.disabled,selected},
          stateMotions:{hover:{durationMs:120,easing:'out-cubic'},press:{durationMs:90,easing:'out-cubic'}},
          children:[
            S2Popup.rect(`s2:picker:option:${item.id}:focus`,0,1,width-16,rowHeight-2,6,
              focused?'#FF394A79':'#00000000'),
            S2Popup.rect(`s2:picker:option:${item.id}:hover`,0,1,width-16,rowHeight-2,6,
              '#00000000',undefined,0,{stateSource:'parent',fillReactive:{
                base:palette.text,alphaBase:0,alphaTerms:item.disabled?{}:{hover:.08,press:.12}}}),
            S2Popup.text(`s2:picker:option:${item.id}:label`,item.label,14,0,width-80,rowHeight,
              {size:16,color:item.disabled?palette.disabled:palette.text}),
            ...(selected?[ui.svg({key:`s2:picker:option:${item.id}:check`,asset:'check',
              tint:palette.text,interactive:false,class:ui.abs(width-47,14,17,17)})]:[])
          ]})];
      })
    ]}),x,y,width,height)
  ];
  return ui.stack({key:'s2:overlay:root',class:ui.abs(0,0,vw,vh),children:nodes});
}
