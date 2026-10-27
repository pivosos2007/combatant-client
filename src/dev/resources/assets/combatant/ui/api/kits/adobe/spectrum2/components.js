/*
 * Combatant Client — Adobe Spectrum 2 dev component adapter (GPL-3.0).
 * Original component guidance/tokens: Adobe Spectrum design system.
 * This is an independent native implementation, not React Spectrum or a 1:1 upstream code copy.
 * Adobe Clean Spectrum VF is restricted; Source Sans 3 is its documented open fallback.
 */
export class AdobeSpectrum2 {
  static dark = Object.freeze({
    // sRGB, 8-bit per channel. Accent blue 800/900/1000 is from Spectrum design-data dark palette.
    background:'#FF1D1D1D', surface:'#FF292929', raised:'#FF333333',
    field:'#FF242424', border:'#FF737373', muted:'#FFB9B9B9', text:'#FFF4F4F4',
    accent:'#FF4069FD', accentHover:'#FF5681FF', accentPress:'#FF6995FE',
    onAccent:'#FFFFFFFF', danger:'#FFF8796E', disabled:'#FF777777',
    selected:'#FF394A79', focus:'#FF8BAEFF',
  });
  static colors(overrides={}) { return {...AdobeSpectrum2.dark,...overrides}; }
  static size = Object.freeze({small:32,medium:40,large:48,extraLarge:56});
  static rect(key,x,y,w,h,radius,fill,stroke,strokeWidth=0,extra={}) {
    return ui.roundedRect({key,x,y,w,h,radius,fill,stroke,strokeWidth,interactive:false,...extra});
  }
  static text(key,value,x,y,w,h,opts={}) {
    const sz=ui.num(opts.size,16);
    return ui.text({key,text:String(value??''),color:opts.color||AdobeSpectrum2.dark.text,
      verticalAlign:'center',interactive:false,
      ...(opts.colorReactive?{colorReactive:opts.colorReactive,stateSource:'parent'}:{}),
      class:ui.abs(x,y,w,h,`font-${opts.strong?'SourceSans3Semibold':'SourceSans3'} font-size-${ui.fmt(sz)} line-height-${ui.fmt(sz+6)} text-align-${opts.align||'left'}`)});
  }
  static surface(key,w,h,children) {
    return ui.stack({key,class:ui.abs(0,0,w,h),children});
  }
  static button(props={}) {
    const key=ui.str(props.key,'s2:button');
    const p=AdobeSpectrum2.colors(props.colors);
    const variant=ui.str(props.variant,'primary');
    const outline=!!props.outline && (variant==='primary'||variant==='secondary');
    const height=AdobeSpectrum2.size[props.size]||40;
    const width=Math.max(80,ui.num(props.width,132));
    const radius=height/2, disabled=!!props.disabled;
    const bg=outline?'#00000000':variant==='accent'?p.accent:
      variant==='negative'?'#FFC4473B':variant==='secondary'?p.raised:p.text;
    const fg=outline?(variant==='secondary'?p.text:p.muted):
      (variant==='accent'||variant==='negative')?p.onAccent:p.background;
    const fill=disabled?p.raised:bg;
    const textColor=disabled?p.disabled:fg;
    const stateColor=(variant==='accent'||variant==='negative')?'#FFFFFFFF':
      outline?p.text:'#FF000000';
    const children=[
      AdobeSpectrum2.rect(`${key}:container`,0,0,width,height,radius,fill,
        outline?(variant==='secondary'?p.border:p.text):undefined,outline?1.5:0),
      AdobeSpectrum2.rect(`${key}:state`,0,0,width,height,radius,'#00000000',undefined,0,{
        stateSource:'parent',fillReactive:{base:stateColor,alphaBase:0,
          alphaTerms:disabled?{}:{hover:.10,press:.17}}}),
      AdobeSpectrum2.text(`${key}:label`,props.label||'Button',12,0,width-24,height,{strong:true,
        align:'center',size:16,color:textColor})
    ];
    return ui.button({key,class:ui.abs(0,0,width,height,disabled?'':'cursor-pointer'),
      stateValues:{disabled},stateMotions:{hover:{durationMs:130,easing:'out-cubic'},
        press:{durationMs:90,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children});
  }
  static actionButton(props={}) {
    const key=ui.str(props.key,'s2:action'),p=AdobeSpectrum2.colors(props.colors);
    const width=Math.max(70,ui.num(props.width,126)),height=36,selected=!!props.selected;
    const children=[
      AdobeSpectrum2.rect(`${key}:surface`,0,0,width,height,8,selected?p.selected:p.surface,p.border,1),
      AdobeSpectrum2.rect(`${key}:hover`,0,0,width,height,8,'#00000000',undefined,0,{
        stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,alphaTerms:{hover:.08,press:.12}}}),
      AdobeSpectrum2.text(`${key}:label`,props.label||'Action',12,0,width-24,height,{size:15,
        strong:selected,align:'center'})
    ];
    return ui.button({key,class:ui.abs(0,0,width,height,'cursor-pointer'),
      stateValues:{selected},stateMotions:{hover:{durationMs:140,easing:'out-cubic'},
        press:{durationMs:100,easing:'out-cubic'},selected:{durationMs:160,easing:'out-cubic'}},
      onClick:props.onClick,children});
  }
  static checkbox(props={}) {
    const key=ui.str(props.key,'s2:checkbox'),p=AdobeSpectrum2.colors(props.colors),
      selected=!!props.selected,indeterminate=!!props.indeterminate,disabled=!!props.disabled;
    const size=40,fg=disabled?p.disabled:p.accent;
    const children=[
      AdobeSpectrum2.rect(`${key}:box`,10,10,20,20,4,selected||indeterminate?fg:'#00000000',
        selected||indeterminate?undefined:p.border,selected||indeterminate?0:2),
      ...(selected||indeterminate?[AdobeSpectrum2.text(`${key}:mark`,indeterminate?'−':'✓',11,8,18,24,
        {size:18,strong:true,align:'center',color:p.onAccent})]:[]),
      AdobeSpectrum2.rect(`${key}:hover`,5,5,30,30,7,'#00000000',undefined,0,{
        stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
          alphaTerms:disabled?{}:{hover:.07,press:.1}}})
    ];
    return ui.button({key,class:ui.abs(0,0,size,size,disabled?'':'cursor-pointer'),
      stateValues:{selected,disabled},stateMotions:{hover:{durationMs:150,easing:'out-cubic'},
        press:{durationMs:100,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children});
  }
  static switch(props={}) {
    const key=ui.str(props.key,'s2:switch'),p=AdobeSpectrum2.colors(props.colors),
      selected=!!props.selected,disabled=!!props.disabled,w=54,h=32;
    const children=[
      AdobeSpectrum2.rect(`${key}:track`,0,4,w,24,12,selected?p.accent:p.surface,
        selected?undefined:p.border,selected?0:2),
      AdobeSpectrum2.rect(`${key}:thumb`,selected?32:6,8,16,16,8,selected?p.onAccent:p.muted),
      AdobeSpectrum2.rect(`${key}:state`,0,0,w,h,16,'#00000000',undefined,0,{
        stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
          alphaTerms:disabled?{}:{hover:.06,press:.10}}})
    ];
    return ui.button({key,class:ui.abs(0,0,w,h,disabled?'':'cursor-pointer'),
      stateValues:{selected,disabled},stateMotions:{selected:{durationMs:180,easing:'out-cubic'},
        hover:{durationMs:120,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children});
  }
  static radio(props={}) {
    const key=ui.str(props.key,'s2:radio'),p=AdobeSpectrum2.colors(props.colors),
      selected=!!props.selected,disabled=!!props.disabled;
    const children=[
      AdobeSpectrum2.rect(`${key}:ring`,10,10,20,20,10,'#00000000',selected?p.accent:p.border,2),
      ...(selected?[AdobeSpectrum2.rect(`${key}:dot`,16,16,8,8,4,p.accent)]:[]),
      AdobeSpectrum2.rect(`${key}:state`,4,4,32,32,16,'#00000000',undefined,0,{
        stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
          alphaTerms:disabled?{}:{hover:.07,press:.1}}})
    ];
    return ui.button({key,class:ui.abs(0,0,40,40,disabled?'':'cursor-pointer'),
      stateValues:{selected,disabled},stateMotions:{hover:{durationMs:130,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children});
  }
  static tabs(props={}) {
    const key=ui.str(props.key,'s2:tabs'),p=AdobeSpectrum2.colors(props.colors),
      items=Array.isArray(props.items)?props.items:[],w=Math.max(1,ui.num(props.width,480));
    const itemW=w/Math.max(1,items.length),selectedIndex=Math.max(0,items.findIndex(i=>i.id===props.selected));
    const children=[AdobeSpectrum2.rect(`${key}:divider`,0,46,w,1,0,p.border),
      AdobeSpectrum2.rect(`${key}:indicator`,selectedIndex*itemW+8,45,
        Math.max(1,itemW-16),3,1,p.accent)];
    items.forEach((item,i)=>{
      const selected=item.id===props.selected;
      children.push(ui.button({key:`${key}:tab:${item.id}`,onClick:item.onClick,
        class:ui.abs(i*itemW,0,itemW,48,'cursor-pointer'),stateValues:{selected},
        stateMotions:{selected:{durationMs:180,easing:'out-cubic'},hover:{durationMs:140,easing:'out-cubic'}},
        children:[
          AdobeSpectrum2.rect(`${key}:tab:${item.id}:hover`,0,0,itemW,45,6,'#00000000',undefined,0,{
            stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,alphaTerms:{hover:.06,press:.1}}}),
          AdobeSpectrum2.text(`${key}:tab:${item.id}:label`,item.label,5,0,itemW-10,44,
            {size:16,strong:selected,align:'center',color:selected?p.text:p.muted}),
        ]}));
    });
    return AdobeSpectrum2.surface(key,w,48,children);
  }
  static textField(props={}) {
    const key=ui.str(props.key,'s2:field'),p=AdobeSpectrum2.colors(props.colors),
      width=Math.max(160,ui.num(props.width,350)),focused=!!props.focused,
      value=ui.str(props.value,''),isQuiet=!!props.quiet;
    const children=[
      AdobeSpectrum2.text(`${key}:label`,props.label||'Field',0,0,width,24,
        {size:15,strong:true,color:p.text}),
      AdobeSpectrum2.rect(`${key}:background`,0,28,width,44,6,
        isQuiet?'#00000000':p.field,focused?p.accent:p.border,focused?2:1),
      AdobeSpectrum2.text(`${key}:value`,value||props.placeholder||'',13,28,width-26,44,
        {size:17,color:value?p.text:p.muted}),
      ...(focused?[AdobeSpectrum2.rect(`${key}:caret`,
        Math.min(width-16,13+Math.max(0,ui.num(props.caretOffset,0))),39,1.5,22,0,p.text)]:[]),
      ...(props.description?[AdobeSpectrum2.text(`${key}:description`,props.description,0,77,width,22,
        {size:14,color:p.muted})]:[])
    ];
    return ui.button({key,class:ui.abs(0,0,width,props.description?100:74,'cursor-text'),
      onClick:props.onClick,children});
  }
  static tag(props={}) {
    const key=ui.str(props.key,'s2:tag'),p=AdobeSpectrum2.colors(props.colors),
      w=Math.max(48,ui.num(props.width,96));
    return AdobeSpectrum2.surface(key,w,28,[
      AdobeSpectrum2.rect(`${key}:background`,0,0,w,28,6,p.raised,p.border,1),
      AdobeSpectrum2.text(`${key}:label`,props.label||'Tag',8,0,w-16,28,{size:14,align:'center'})]);
  }
  static picker(props={}) {
    const key=ui.str(props.key,'s2:picker'),palette=AdobeSpectrum2.colors(props.colors);
    const width=Math.max(220,ui.num(props.width,360)),selected=ui.str(props.value,'');
    const items=Array.isArray(props.items)?props.items:[];
    const found=items.find(item=>item.id===selected);
    const disabled=!!props.disabled,opened=!!props.open;
    return ui.button({key,class:ui.abs(0,0,width,80,disabled?'':'cursor-pointer'),
      stateValues:{disabled,selected:opened},
      stateMotions:{hover:{durationMs:120,easing:'out-cubic'},press:{durationMs:90,easing:'out-cubic'}},
      ...(disabled?{}:{onClick:props.onClick}),children:[
        AdobeSpectrum2.text(`${key}:label`,props.label||'Choose option',0,0,width,24,{size:16,strong:true}),
        AdobeSpectrum2.rect(`${key}:field`,0,30,width,44,8,palette.field,opened?palette.accent:palette.border,opened?2:1),
        AdobeSpectrum2.rect(`${key}:hover`,0,30,width,44,8,'#00000000',undefined,0,{
          stateSource:'parent',fillReactive:{base:palette.text,alphaBase:0,alphaTerms:{hover:.06,press:.10}}}),
        AdobeSpectrum2.text(`${key}:selected`,found?found.label:(props.placeholder||'Select…'),14,30,width-54,44,
          {size:17,color:found?palette.text:palette.muted}),
        ui.svg({key:`${key}:chevron`,asset:'chevron-down',tint:palette.text,interactive:false,
          class:ui.abs(width-35,43,18,18)})
      ]});
  }
  static slider(props={}) {
    const key=ui.str(props.key,'s2:slider'),palette=AdobeSpectrum2.colors(props.colors);
    const width=Math.max(170,ui.num(props.width,420));
    const min=ui.num(props.min,0),max=Math.max(min+0.0001,ui.num(props.max,100));
    const value=Math.min(max,Math.max(min,ui.num(props.value,min))),fraction=(value-min)/(max-min);
    const left=12,trackWidth=width-24,thumbX=left+trackWidth*fraction;
    const disabled=!!props.disabled;
    const children=[
      AdobeSpectrum2.text(`${key}:label`,props.label||'Amount',0,0,width-70,28,{size:17,strong:true}),
      AdobeSpectrum2.text(`${key}:value`,ui.str(props.valueLabel,String(Math.round(value))),width-75,0,75,28,
        {size:16,align:'right',color:palette.muted}),
      AdobeSpectrum2.rect(`${key}:track`,left,47,trackWidth,5,2.5,palette.raised),
      AdobeSpectrum2.rect(`${key}:fill`,left,47,Math.max(.01,trackWidth*fraction),5,2.5,
        disabled?palette.disabled:palette.accent),
      AdobeSpectrum2.rect(`${key}:thumb`,thumbX-10,39,20,20,10,disabled?palette.disabled:palette.text,
        disabled?undefined:palette.accent,disabled?0:2),
    ];
    return ui.button({key,class:ui.abs(0,0,width,68,disabled?'':'cursor-pointer'),
      stateValues:{disabled},stateMotions:{hover:{durationMs:130,easing:'out-cubic'},
        press:{durationMs:90,easing:'out-cubic'}},...(disabled?{}:{onClick:props.onClick}),children});
  }
  static progressBar(props={}) {
    const key=ui.str(props.key,'s2:progress'),palette=AdobeSpectrum2.colors(props.colors);
    const width=Math.max(100,ui.num(props.width,420));
    const min=ui.num(props.min,0),max=Math.max(min+0.0001,ui.num(props.max,100));
    const value=Math.min(max,Math.max(min,ui.num(props.value,min))),fraction=(value-min)/(max-min);
    const height=props.size==='S'?4:props.size==='L'?10:6;
    return AdobeSpectrum2.surface(key,width,54,[
      AdobeSpectrum2.text(`${key}:label`,props.label||'Progress',0,0,width-80,28,{size:16,strong:true}),
      AdobeSpectrum2.text(`${key}:value`,ui.str(props.valueLabel,`${Math.round(fraction*100)}%`),width-80,0,80,28,
        {size:16,color:palette.muted,align:'right'}),
      AdobeSpectrum2.rect(`${key}:track`,0,39,width,height,height/2,palette.raised),
      AdobeSpectrum2.rect(`${key}:fill`,0,39,Math.max(0.01,width*fraction),height,height/2,palette.accent)
    ]);
  }
  static meter(props={}) {
    const key=ui.str(props.key,'s2:meter'),palette=AdobeSpectrum2.colors(props.colors);
    const variant=ui.str(props.variant,'informative'),fill=variant==='positive'?'#FF4BAF78':
      variant==='notice'?'#FFF7B955':variant==='negative'?palette.danger:palette.accent;
    return AdobeSpectrum2.progressBar({...props,key,colors:{...palette,accent:fill}});
  }
  static searchField(props={}) {
    const key=ui.str(props.key,'s2:search'),palette=AdobeSpectrum2.colors(props.colors),
      width=Math.max(220,ui.num(props.width,420)),value=ui.str(props.value,''),focused=!!props.focused;
    const children=[
      AdobeSpectrum2.text(`${key}:label`,props.label||'Search',0,0,width,24,{size:16,strong:true}),
      AdobeSpectrum2.rect(`${key}:field`,0,30,width,44,8,palette.field,focused?palette.accent:palette.border,focused?2:1),
      ui.svg({key:`${key}:glass`,asset:'search',tint:palette.muted,interactive:false,class:ui.abs(13,42,20,20)}),
      AdobeSpectrum2.text(`${key}:text`,value||props.placeholder||'Search…',43,30,width-88,44,
        {size:17,color:value?palette.text:palette.muted}),
      ...(focused?[AdobeSpectrum2.rect(`${key}:caret`,Math.min(width-47,43+Math.max(0,ui.num(props.caretOffset,0))),
        41,1.5,23,0,palette.text)]:[]),
    ];
    if(value)children.push(ui.button({key:`${key}:clear`,onClick:props.onClear,
      class:ui.abs(width-41,33,37,38,'cursor-pointer'),children:[
        ui.svg({key:`${key}:clear-icon`,asset:'x',tint:palette.text,interactive:false,
          class:ui.abs(10,9,18,18)})
      ]}));
    return ui.button({key,class:ui.abs(0,0,width,80,'cursor-text'),onClick:props.onClick,children});
  }
}
