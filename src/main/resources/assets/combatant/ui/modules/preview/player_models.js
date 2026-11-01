/*
 * Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * PlayerModel collection selector. Layout/tokens follow the native Spectrum 2
 * foundation, tabs, quiet actions and InlineAlert clipped-edge convention.
 * This is a main-resource UI: the Spectrum gallery itself is dev-only.
 */
const num=ui.num, str=ui.str;
const transparent='#00000000';

function colors(data) {
  const p=data||{};
  return {
    bg:str(p.background,'#FF1D1D1D'),surface:str(p.raised,'#FF292929'),
    field:str(p.field,'#FF242424'),border:str(p.border,'#FF737373'),
    text:str(p.text,'#FFF4F4F4'),muted:str(p.muted,'#FFB9B9B9'),
    accent:str(p.accent,'#FF4069FD'),selected:str(p.selected,'#FF394A79'),
    onAccent:str(p.onAccent,'#FFFFFFFF'),focus:str(p.focus,p.accent)
  };
}

function alpha(arg,amount) {
  const src=str(arg,'#FFFFFFFF');
  if(!/^#[0-9a-fA-F]{8}$/.test(src))return src;
  const a=Math.max(0,Math.min(255,Math.round(parseInt(src.slice(1,3),16)*Math.max(0,Math.min(1,amount)))));
  return '#'+a.toString(16).padStart(2,'0')+src.slice(3);
}
function box(key,x,y,w,h,r,fill,stroke,strokeWidth=0,extra={}) {
  return ui.roundedRect({key,x,y,w,h,radius:r,fill,stroke,strokeWidth,interactive:false,...extra});
}
function text(key,value,x,y,w,h,color,size=16,strong=false,align='left',wrap=false) {
  return ui.text({key,text:String(value??''),color,interactive:false,
    verticalAlign:wrap?'top':'center',
    style:ui.style({maxLines:wrap?3:1,whiteSpace:wrap?'normal':'nowrap',
      overflowWrap:wrap?'break-word':'normal',textOverflow:wrap?'clip':'ellipsis'}),
    class:ui.abs(x,y,Math.max(1,w),Math.max(1,h),
      `font-${strong?'InterMedium':'Inter'} font-size-${ui.fmt(size)} line-height-${ui.fmt(size+5)} text-align-${align}`)});
}
function icon(key,asset,x,y,size,tint) {
  return ui.svg({key,asset,assetType:'svg',tint,interactive:false,
    style:ui.absolute(x,y,size,size)});
}
function place(key,x,y,w,h,children) {
  return ui.stack({key,style:ui.absolute(x,y,w,h),interactive:false,children});
}
function stateOverlay(key,w,h,r,color,enabled=true) {
  return box(key,0,0,w,h,r,transparent,undefined,0,{
    stateSource:'parent',fillReactive:{base:color,alphaBase:0,
      alphaTerms:enabled?{hover:.075,press:.14}:{}},interactive:false});
}
function button(key,x,y,w,h,action,p,options={}) {
  const quiet=!!options.quiet,primary=!!options.primary,disabled=!!options.disabled;
  const radius=primary?h/2:Math.min(11,h*.27);
  const bg=primary?p.accent:quiet?transparent:p.surface;
  const stroke=quiet||primary?undefined:p.border;
  const iconSize=options.icon?Math.min(20,h-14):0;
  const label=str(options.label,'');
  const iconOnly=!!options.icon&&!label;
  const iconX=iconOnly?(w-iconSize)*.5:14;
  const labelX=options.icon?iconX+iconSize+9:12;
  const contents=[
    box(`${key}:surface`,0,0,w,h,radius,disabled?p.field:bg,stroke,stroke?1:0),
    stateOverlay(`${key}:state`,w,h,radius,primary?p.onAccent:p.text,!disabled)
  ];
  if(options.icon)contents.push(icon(`${key}:icon`,options.icon,iconX,(h-iconSize)/2,
    iconSize,disabled?p.muted:primary?p.onAccent:p.text));
  if(label)contents.push(text(`${key}:label`,label,labelX,0,w-labelX-12,h,
    disabled?p.muted:primary?p.onAccent:p.text,16,true,
    options.icon?'left':'center'));
  return ui.button({key,style:ui.absolute(x,y,w,h),
    class:disabled?'cursor-unsupported':'cursor-pointer',
    stateValues:{disabled},stateMotions:{hover:{durationMs:130,easing:'out-cubic'},
      press:{durationMs:85,easing:'out-cubic'}},
    ...(disabled?{}:{onClick:action}),children:contents});
}
function tab(key,label,asset,x,y,w,active,p,action) {
  const h=49,iconSize=19;
  const labelApprox=Math.min(w-50,Math.max(45,label.length*8));
  const left=Math.max(8,(w-iconSize-9-labelApprox)*.5);
  const nodes=[
    stateOverlay(`${key}:hover`,w,h-3,7,p.text),
    icon(`${key}:icon`,asset,left,(h-iconSize)/2-2,iconSize,active?p.text:p.muted),
    text(`${key}:label`,label,left+iconSize+9,0,w-(left+iconSize+9)-8,h-4,
      active?p.text:p.muted,16.5,active)
  ];
  if(active)nodes.push(box(`${key}:indicator`,9,h-3,w-18,3,1.5,p.accent));
  return ui.button({key,style:ui.absolute(x,y,w,h),class:'cursor-pointer',
    onClick:action,stateValues:{selected:active},
    stateMotions:{hover:{durationMs:120,easing:'out-cubic'},
      press:{durationMs:90,easing:'out-cubic'},selected:{durationMs:150,easing:'out-cubic'}},
    children:nodes});
}
function entry(item,index,w,selected,p,step) {
  const id=str(item.id,`entry-${index}`),name=str(item.label,id),art=str(item.icon,'');
  const detail=str(item.detail,id);
  const key=`models:entry:${id}`,height=82,radius=11;
  const contents=[
    box(`${key}:surface`,0,0,w,height,radius,
      selected?alpha(p.accent,.13):p.surface,
      selected?alpha(p.accent,.95):alpha(p.border,.55),selected?1.4:.9),
    stateOverlay(`${key}:state`,w,height,radius,p.text)
  ];
  if(selected) {
    // Exactly as SpectrumInlineAlert: clip the original full-width contour.
    // A 4px rounded bar would be a capsule and cannot follow the card corner.
    contents.push(ui.stack({key:`${key}:accent-clip`,interactive:false,
      style:ui.absolute(0,0,4,height,{overflow:'hidden'}),
      children:[box(`${key}:accent-contour`,0,0,w,height,radius,p.accent)]}));
  }
  contents.push(box(`${key}:art-background`,14,14,54,54,9,p.field,alpha(p.border,.75),.9));
  if(art)contents.push(ui.image({key:`${key}:art`,assetType:'texture',asset:art,
    fit:'contain',interactive:false,style:ui.absolute(19,19,44,44,{overflow:'hidden',borderRadius:6})}));
  else contents.push(icon(`${key}:placeholder`,'user',30,30,22,p.muted));
  const tx=82,checkSpace=selected?32:8;
  contents.push(
    text(`${key}:title`,name,tx,13,w-tx-checkSpace-12,30,p.text,18,true),
    text(`${key}:id`,detail,tx,44,w-tx-15,23,p.muted,14,false)
  );
  if(selected) {
    contents.push(box(`${key}:check-bg`,w-39,17,24,24,12,p.accent));
    contents.push(icon(`${key}:check`,'check',w-34,22,14,p.onAccent));
  }
  return ui.button({key,style:ui.absolute(0,index*step,w,height),
    class:'cursor-pointer',onClick:`models:select:${id}`,stateValues:{selected},
    stateMotions:{hover:{durationMs:120,easing:'out-cubic'},
      press:{durationMs:90,easing:'out-cubic'},selected:{durationMs:160,easing:'out-cubic'}},
    children:contents});
}

export function buildTemplate(ctx) {
  const props=ctx.props||{},p=colors(props.palette);
  const w=Math.max(180,num(props.width,408)),h=Math.max(340,num(props.height,780));
  const pad=16,innerW=w-pad*2,headerH=77,tabTop=82,tabH=49;
  const listTop=174,footerH=132,scrollH=Math.max(36,h-listTop-footerH);
  const variants=!!props.variants,selected=str(props.selected,'');
  const items=Array.isArray(props.items)?props.items:[];
  const labelCurrent=str(props.currentLabel,'Selected appearance');
  const compact=w<330;
  const nodes=[
    box('models:panel',0,0,w,h,13,p.bg,alpha(p.border,.63),1),
    box('models:header-icon-bg',pad,17,42,42,10,p.surface,alpha(p.border,.5),1),
    icon('models:header-icon','user',pad+11,28,20,p.text),
    text('models:heading',str(props.title,'Player appearance'),pad+53,17,
      Math.max(34,w-pad-53-(compact?61:128)),32,p.text,20,true),
    text('models:caption',str(props.subtitle,'Model collection'),pad+53,49,
      Math.max(34,w-pad-53-(compact?61:128)),21,p.muted,13.5),
    button('models:reload',w-pad-(compact?42:110),20,compact?42:110,38,'models:reload',p,{
      label:compact?'':str(props.reloadShortLabel,'Refresh'),icon:'rotate-ccw'}),
    box('models:header-divider',pad,headerH,innerW,1,0,alpha(p.border,.45)),
    tab('models:tab:models:ui',str(props.modelsLabel,'Models'),'layout-grid',
      pad,tabTop,innerW/2,!variants,p,'models:tab:models'),
    tab('models:tab:variants:ui',str(props.variantsLabel,'Textures'),'layers',
      pad+innerW/2,tabTop,innerW/2,variants,p,'models:tab:variants'),
    box('models:tabs-divider',pad,tabTop+tabH-3,innerW,1,0,alpha(p.border,.47)),
    text('models:list-title',str(variants?props.variantsLabel:props.modelsLabel,
      variants?'Textures':'Models'),pad+1,143,innerW-57,26,p.text,15.5,true),
    box('models:count-bg',w-pad-40,145,40,23,11.5,p.field,alpha(p.border,.65),.9),
    text('models:count',String(items.length),w-pad-40,145,40,23,p.muted,13.5,true,'center')
  ];
  if(items.length){
    const gap=9,cardW=innerW-20,step=82+gap;
    nodes.push(ui.scroll({key:'models:browser',
      style:ui.absolute(pad,listTop,innerW,scrollH,{overflow:'hidden'}),
      wheelStep:64,smoothScroll:true,scrollSmoothingRate:.02,
      scrollbar:true,scrollbarWidth:10,scrollbarMinThumb:54,
      scrollbarRadius:5,scrollbarLeadingInset:4,scrollbarTrailingInset:4,
      scrollbarColor:p.muted,scrollbarOffset:-13,scrollbarBaseAlpha:.72,
      scrollbarHoverAlpha:.22,scrollbarDragAlpha:.28,scrollbarVisibilityMs:2400,
      children:[ui.stack({key:'models:entries',interactive:false,
        style:ui.style({width:cardW,height:items.length*step+2}),
        children:items.map((item,index)=>entry(item,index,cardW,
          str(item.id,'')===selected,p,step))})]}));
  }else{
    const empty=variants&&props.hasSelection?
      str(props.emptyVariantsLabel,'No alternate textures for this model.'):
      str(props.emptyLabel,'No player models installed.');
    const emptyH=Math.max(125,Math.min(245,scrollH-4));
    nodes.push(place('models:empty',pad,listTop,innerW,emptyH,[
      box('models:empty:surface',0,0,innerW,emptyH,11,p.surface,alpha(p.border,.55),1),
      icon('models:empty:icon',variants?'layers':'user',
        (innerW-34)/2,22,34,p.muted),
      text('models:empty:message',empty,21,71,innerW-42,emptyH-82,p.muted,16,false,'center',true)
    ]));
  }
  const fy=h-footerH;
  nodes.push(
    box('models:footer-divider',pad,fy,innerW,1,0,alpha(p.border,.52)),
    text('models:current-heading',labelCurrent,pad,fy+12,innerW,21,p.muted,13.5),
    icon('models:selection-icon',selected?'check':'user',pad,fy+41,18,
      selected?p.accent:p.muted),
    text('models:current-value',str(props.selection,str(props.noSelectionLabel,'None')),
      pad+25,fy+34,innerW-25,33,p.text,17.5,true),
    button('models:apply',pad,fy+83,innerW,42,'models:apply',p,{
      label:props.enabled?str(props.restoreLabel,'Restore vanilla appearance'):
        str(props.applyLabel,'Use this appearance'),
      icon:props.enabled?'rotate-ccw':'check',primary:!props.enabled,
      disabled:!props.hasSelection})
  );
  return ui.root({key:'player-model-selector',style:ui.absolute(0,0,w,h),children:nodes});
}
export function render(ctx){return buildTemplate(ctx);}
