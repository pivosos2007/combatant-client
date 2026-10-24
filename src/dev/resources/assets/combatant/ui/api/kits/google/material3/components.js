/*
 * Combatant — Google Material 3 native component port (development only).
 * Reference: https://github.com/material-components/material-web (Apache-2.0, Google LLC).
 * Implemented against Combatant UiNodes; not a Lit/DOM wrapper.
 * Preserve component-level tokens/geometry/interaction instead of applying Combatant theme styling.
 */
const S = (x, y, w, h) => ui.absolute(x, y, w, h);
const N = (value, fallback) => Math.max(0, ui.num(value, fallback));

export class GoogleMaterial3 {
  static dark = Object.freeze({
    primary:'#FFD0BCFF', onPrimary:'#FF381E72', primaryContainer:'#FF4F378B',
    onPrimaryContainer:'#FFEADDFF', secondaryContainer:'#FF4A4458',
    onSecondaryContainer:'#FFE8DEF8', surface:'#FF141218',
    surfaceContainerLow:'#FF1D1B20', surfaceContainer:'#FF211F26',
    surfaceContainerHigh:'#FF2B2930', surfaceContainerHighest:'#FF36343B',
    onSurface:'#FFE6E0E9', onSurfaceVariant:'#FFCAC4D0',
    outline:'#FF938F99', outlineVariant:'#FF49454F',
    error:'#FFF2B8B5', onError:'#FF601410', inverseSurface:'#FFE6E0E9'
  });
  static colors(custom={}) { return {...GoogleMaterial3.dark, ...custom}; }
  static dimensions = Object.freeze({
    buttonHeight:40, buttonRadius:20, switchWidth:52, switchHeight:32,
    checkboxTarget:40, checkboxBox:18, tabsHeight:48, chipHeight:32,
    textFieldHeight:56, radioTarget:40, radioOuter:20,
    iconButtonSize:40, fabSize:56, extendedFabHeight:56,
    sliderHeight:40, listItemHeight:56,
  });
  static shape(key,x,y,w,h,radius,fill,stroke,strokeWidth=0,extra={}) {
    return ui.roundedRect({key,x,y,w,h,radius,fill,stroke,strokeWidth,interactive:false,...extra});
  }
  // CSS Material role "label-large" = Roboto Medium 14/20. The runtime's measured
  // glyph ink (not font-size) is vertically centered by UiTextNodeRenderer.
  /** Analytic per-corner shape. Unlike roundedRect, box honours four independent corners. */
  static cornerBox(key,x,y,w,h,radii,fill,stroke,strokeWidth=0,extra={}) {
    const [tl,tr,br,bl]=radii;
    return ui.shape({key,shape:'box',style:ui.absolute(x,y,w,h),radius:0,
      cornerTL:{kind:'rounded',radius:tl},cornerTR:{kind:'rounded',radius:tr},
      cornerBR:{kind:'rounded',radius:br},cornerBL:{kind:'rounded',radius:bl},
      fill,stroke,strokeWidth,interactive:false,...extra});
  }
  static text(key,text,x,y,w,h,opts={}) {
    const size=ui.num(opts.size,14);
    const leading=size>=32?40:size>=24?32:size>=18?26:size>=16?24:size>=14?20:16;
    return ui.text({key,text:String(text ?? ''),color:opts.color??GoogleMaterial3.dark.onSurface,
      ...(opts.reactive?{colorReactive:opts.reactive,stateSource:'parent'}:{}),
      verticalAlign:opts.verticalAlign??'center',interactive:false,
      class:ui.abs(x,y,w,h,`font-${opts.medium===false?'Roboto':'RobotoMedium'} font-size-${ui.fmt(size)} line-height-${ui.fmt(leading)} text-align-${opts.align??'center'}`)});
  }
  static surface(key,w,h,content,props={}) {
    return ui.stack({key,class:ui.abs(0,0,w,h),children:content,...props});
  }
  static action(key,w,h,children,props={}) {
    const disabled=!!props.disabled;
    return ui.button({key,class:ui.abs(0,0,w,h,disabled?'':'cursor-pointer'),
      stateValues:{disabled, selected:!!props.selected},
      stateMotions:{hover:{durationMs:90,easing:'out-cubic'},press:{durationMs:75,easing:'out-cubic'},
        selected:{durationMs:180,easing:'out-cubic'}},
      ...(!disabled && props.onClick?{onClick:props.onClick}:{}),children});
  }
  static stateLayer(key,w,h,radius,fg,disabled=false) {
    return GoogleMaterial3.shape(key,0,0,w,h,radius,'#00000000',undefined,0,{
      fillReactive:{base:fg,alphaBase:0,alphaTerms:disabled?{}:{hover:.08,press:.10}},
      stateSource:'parent'});
  }
  static button(props={}) {
    const key=ui.str(props.key,'m3-button'), variant=ui.str(props.variant,'filled').toLowerCase();
    const p=GoogleMaterial3.colors(props.colors), disabled=!!props.disabled;
    const label=ui.str(props.label,'Button');
    const width=Math.max(64,N(props.width,Math.max(84,40+label.length*9))), height=40;
    const fill=variant==='filled'?p.primary:variant==='tonal'?p.secondaryContainer:
      variant==='elevated'?p.surfaceContainerLow:'transparent';
    const fg=variant==='filled'?p.onPrimary:variant==='tonal'?p.onSecondaryContainer:
      variant==='elevated'?p.primary:p.primary;
    const bg=disabled?(variant==='outlined'||variant==='text'?'#00000000':'#1FFFFFFF'):fill;
    const foreground=disabled?'#61FFFFFF':fg;
    const children=[
      GoogleMaterial3.shape(`${key}:container`,0,0,width,height,20,bg,
        variant==='outlined'?(disabled?'#1FFFFFFF':p.outline):undefined,variant==='outlined'?1:0),
      GoogleMaterial3.stateLayer(`${key}:state`,width,height,20,foreground,disabled),
      GoogleMaterial3.text(`${key}:text`,label,12,0,width-24,height,{color:foreground})
    ];
    return GoogleMaterial3.action(key,width,height,children,props);
  }
  static iconButton(props={}) {
    const key=ui.str(props.key,'m3-icon-button'),p=GoogleMaterial3.colors(props.colors);
    const variant=ui.str(props.variant,'standard');const selected=!!props.selected;
    const filled=variant==='filled'||variant==='filledTonal';
    const bg=variant==='filled'?p.primary:variant==='filledTonal'?p.secondaryContainer:
      selected?p.primary:'#00000000';
    const fg=variant==='filled'?p.onPrimary:variant==='filledTonal'?p.onSecondaryContainer:
      selected?p.onPrimary:p.onSurfaceVariant;
    return GoogleMaterial3.action(key,40,40,[
      GoogleMaterial3.shape(`${key}:bg`,0,0,40,40,20,bg,
        variant==='outlined'?p.outline:undefined,variant==='outlined'?1:0),
      GoogleMaterial3.stateLayer(`${key}:state`,40,40,20,fg,!!props.disabled),
      ui.svg({key:`${key}:icon`,asset:ui.str(props.icon,'check'),tint:fg,
        interactive:false,class:ui.abs(10,10,20,20)})
    ],props);
  }
  static fab(props={}) {
    const key=ui.str(props.key,'m3-fab'),p=GoogleMaterial3.colors(props.colors);
    const extended=!!props.label, w=extended?Math.max(104,N(props.width,148)):56;
    const bg=ui.str(props.variant,'primary')==='surface'?p.surfaceContainerHigh:p.primaryContainer;
    const fg=ui.str(props.variant,'primary')==='surface'?p.primary:p.onPrimaryContainer;
    return GoogleMaterial3.action(key,w,56,[
      GoogleMaterial3.shape(`${key}:bg`,0,0,w,56,16,bg),
      GoogleMaterial3.stateLayer(`${key}:state`,w,56,16,fg,!!props.disabled),
      ui.svg({key:`${key}:icon`,asset:ui.str(props.icon,'user-plus'),tint:fg,interactive:false,
        class:ui.abs(extended?16:16,16,24,24)}),
      ...(extended?[GoogleMaterial3.text(`${key}:label`,props.label,48,0,w-60,56,
        {color:fg,align:'left',size:14})]:[])
    ],props);
  }
  static switch(props={}) {
    const key=ui.str(props.key,'m3-switch'),p=GoogleMaterial3.colors(props.colors);
    const selected=!!props.selected,disabled=!!props.disabled,handleSize=selected?24:(props.icons?16:16);
    const hx=selected?52-4-handleSize:8;
    return GoogleMaterial3.action(key,52,32,[
      GoogleMaterial3.shape(`${key}:track`,0,0,52,32,16,p.surfaceContainerHighest,p.outline,2,
        {fillReactive:{base:p.surfaceContainerHighest,mix:p.primary,mixTerms:{selected:1}},
         strokeReactive:{base:p.outline,alphaBase:1,alphaTerms:{selected:-1}},stateSource:'parent'}),
      GoogleMaterial3.shape(`${key}:handle`,hx,(32-handleSize)/2,handleSize,handleSize,handleSize/2,
        p.outline,undefined,0,{fillReactive:{base:p.outline,mix:p.onPrimary,
          mixTerms:{selected:1}},stateSource:'parent'}),
      ...(props.icons?[ui.svg({key:`${key}:check`,asset:'check',tint:p.primaryContainer,
        tintReactive:{base:p.primaryContainer,alphaBase:0,alphaTerms:{selected:1}},
        stateSource:'parent',class:ui.abs(hx+4,10,16,12),interactive:false})]:[]),
      GoogleMaterial3.stateLayer(`${key}:hover`,52,32,16,p.primary,disabled)
    ],props);
  }
  static checkbox(props={}) {
    const key=ui.str(props.key,'m3-checkbox'),p=GoogleMaterial3.colors(props.colors);
    const selected=!!props.selected,indeterminate=!!props.indeterminate;
    const active=selected||indeterminate;
    return GoogleMaterial3.action(key,40,40,[
      GoogleMaterial3.shape(`${key}:halo`,0,0,40,40,20,'#00000000',undefined,0,{
        fillReactive:{base:p.onSurface,alphaBase:0,alphaTerms:{hover:.08,press:.1}},stateSource:'parent'}),
      GoogleMaterial3.shape(`${key}:box`,11,11,18,18,2,p.primary,
        p.onSurfaceVariant,2,{fillReactive:{base:p.primary,alphaBase:0,alphaTerms:{selected:1}},
          strokeReactive:{base:p.onSurfaceVariant,mix:p.primary,mixTerms:{selected:1}},
          stateSource:'parent'}),
      ui.svg({key:`${key}:mark`,asset:indeterminate?'minus':'check',tint:p.onPrimary,
        tintReactive:{base:p.onPrimary,alphaBase:0,alphaTerms:{selected:1}},
        stateSource:'parent',class:ui.abs(13,13,14,14),interactive:false})
    ],{...props,selected:active});
  }
  static radio(props={}) {
    const key=ui.str(props.key,'m3-radio'),p=GoogleMaterial3.colors(props.colors);
    const selected=!!props.selected,c=selected?p.primary:p.onSurfaceVariant;
    return GoogleMaterial3.action(key,40,40,[
      GoogleMaterial3.shape(`${key}:hover`,0,0,40,40,20,'#00000000',undefined,0,{
        fillReactive:{base:c,alphaBase:0,alphaTerms:{hover:.08,press:.1}},stateSource:'parent'}),
      GoogleMaterial3.shape(`${key}:ring`,10,10,20,20,10,'#00000000',p.onSurfaceVariant,2,
        {strokeReactive:{base:p.onSurfaceVariant,mix:p.primary,mixTerms:{selected:1}},
          stateSource:'parent'}),
      GoogleMaterial3.shape(`${key}:dot`,15,15,10,10,5,p.primary,undefined,0,
        {fillReactive:{base:p.primary,alphaBase:0,alphaTerms:{selected:1}},stateSource:'parent'})
    ],props);
  }
  static chip(props={}) {
    const key=ui.str(props.key,'m3-chip'),p=GoogleMaterial3.colors(props.colors),
      label=ui.str(props.label,'Filter'),variant=ui.str(props.variant,'filter');
    const selected=!!props.selected,icon=props.icon|| (variant==='filter'?'check':null);
    const width=Math.max(70,N(props.width,44+label.length*8));
    const fg=p.onSurface, bg=selected?p.secondaryContainer:'#00000000';
    return GoogleMaterial3.action(key,width,32,[
      GoogleMaterial3.shape(`${key}:bg`,0,0,width,32,8,p.secondaryContainer,p.outline,1,
        {fillReactive:{base:p.secondaryContainer,alphaBase:0,alphaTerms:{selected:1}},
          strokeReactive:{base:p.outline,alphaBase:1,alphaTerms:{selected:-1}},
          stateSource:'parent'}),
      GoogleMaterial3.stateLayer(`${key}:state`,width,32,8,fg,!!props.disabled),
      ...(icon?[ui.svg({key:`${key}:icon`,asset:icon,tint:fg,
        ...(variant==='filter'&&!props.icon?{tintReactive:{base:fg,alphaBase:0,alphaTerms:{selected:1}},
          stateSource:'parent'}:{}),
        class:ui.abs(9,7,18,18),interactive:false})]:[]),
      GoogleMaterial3.text(`${key}:label`,label,icon?32:12,0,width-(icon?42:24),32,
        {color:fg,align:'center',size:14})
    ],props);
  }
  static tabs(props={}) {
    const key=ui.str(props.key,'m3-tabs'),items=Array.isArray(props.items)?props.items:[],
      selected=ui.str(props.selected,''),p=GoogleMaterial3.colors(props.colors);
    const width=Math.max(1,N(props.width,420)),widthEach=width/Math.max(1,items.length);
    const index=Math.max(0,items.findIndex(v=>v.id===selected));
    const children=[
      GoogleMaterial3.shape(`${key}:base`,0,0,width,48,0,p.surface),
      GoogleMaterial3.shape(`${key}:divider`,0,47,width,1,0,p.outlineVariant),
      GoogleMaterial3.shape(`${key}:indicator`,index*widthEach+12,45,
        Math.max(1,widthEach-24),3,1.5,p.primary)
    ];
    for(let i=0;i<items.length;i++) {
      const v=items[i],active=v.id===selected,tabKey=`${key}:tab:${v.id}`;
      children.push(GoogleMaterial3.action(tabKey,widthEach,45,[
        GoogleMaterial3.stateLayer(`${tabKey}:state`,widthEach,45,0,p.primary,false),
        GoogleMaterial3.text(`${tabKey}:label`,v.label,6,0,widthEach-12,45,
          {color:active?p.primary:p.onSurfaceVariant})
      ],{onClick:v.onClick, selected:active}));
      children[children.length-1]={...children[children.length-1],style:S(i*widthEach,0,widthEach,45)};
    }
    return GoogleMaterial3.surface(key,width,48,children);
  }
  /** Material 3 outlined segmented control: one continuous 40dp capsule and shared outline. */
  static segmented(props={}) {
    const key=ui.str(props.key,'m3-segmented'),items=Array.isArray(props.items)?props.items:[],
      p=GoogleMaterial3.colors(props.colors),w=Math.max(1,N(props.width,420));
    if (!items.length) return GoogleMaterial3.surface(key,w,40,[]);
    const each=w/items.length,selected=ui.str(props.selected,'');
    const children=[GoogleMaterial3.shape(`${key}:surface`,0,0,w,40,20,'#00000000')];
    for(let i=0;i<items.length;i++) {
      const v=items[i],k=`${key}:${ui.str(v.id,String(i))}`,active=v.id===selected;
      const corners=[i===0?20:0,i===items.length-1?20:0,
        i===items.length-1?20:0,i===0?20:0];
      const content=[
        GoogleMaterial3.cornerBox(`${k}:selected`,0,0,each,40,corners,p.secondaryContainer,
          undefined,0,{fillReactive:{base:p.secondaryContainer,alphaBase:0,
            alphaTerms:{selected:1}},stateSource:'parent'}),
        GoogleMaterial3.stateLayer(`${k}:state`,each,40,0,p.onSurface,!!v.disabled),
        GoogleMaterial3.text(`${k}:label`,v.label,12,0,each-24,40,{color:p.onSurface,
          reactive:{base:p.onSurface,mix:p.onSecondaryContainer,mixTerms:{selected:1}}})
      ];
      children.push({...GoogleMaterial3.action(k,each,40,content,{...v,selected:active}),
        style:S(i*each,0,each,40)});
    }
    for(let i=1;i<items.length;i++) children.push(GoogleMaterial3.shape(
      `${key}:divider:${i}`,i*each-.5,1,1,38,0,p.outline));
    children.push(GoogleMaterial3.shape(`${key}:outline`,0,0,w,40,20,'#00000000',p.outline,1));
    return GoogleMaterial3.surface(key,w,40,children);
  }
  /**
   * M3 Expressive connected group (small, 40dp): 2dp spacing, 8dp inner corners,
   * 20dp outer corners, 20dp checked corners. A selected segment morphs its
   * OWN outline. It is not a fourth pill drawn over three outlined tracks.
   * Reference: androidx.compose.material3.ButtonGroupDefaults and MDC-Android.
   */
  static connectedButtonGroup(props={}) {
    const key=ui.str(props.key,'m3-connected'),items=Array.isArray(props.items)?props.items:[],
      p=GoogleMaterial3.colors(props.colors),w=Math.max(1,N(props.width,420));
    if (!items.length) return GoogleMaterial3.surface(key,w,40,[]);
    const gap=2,each=(w-gap*(items.length-1))/items.length,selected=ui.str(props.selected,'');
    const children=[];
    for(let i=0;i<items.length;i++) {
      const v=items[i],active=ui.str(v.id,'')===selected,
        k=`${key}:${ui.str(v.id,String(i))}`;
      const radii=[i===0?20:8,i===items.length-1?20:8,
        i===items.length-1?20:8,i===0?20:8];
      const surface=GoogleMaterial3.cornerBox(`${k}:surface`,0,0,each,40,radii,
        p.surfaceContainerLow,p.outline,1,{
          cornerMorphTo:20,stateSource:'parent',
          fillReactive:{base:p.surfaceContainerLow,mix:p.secondaryContainer,mixTerms:{selected:1}}
        });
      const state=GoogleMaterial3.cornerBox(`${k}:state`,0,0,each,40,radii,
        '#00000000',undefined,0,{
          cornerMorphTo:20,stateSource:'parent',
          fillReactive:{base:p.onSurface,alphaBase:0,
            alphaTerms:{hover:0.08,press:0.10}}
        });
      const content=[surface,state,
        GoogleMaterial3.text(`${k}:label`,v.label,12,0,each-24,40,{
          color:p.onSurface,
          reactive:{base:p.onSurface,mix:p.onSecondaryContainer,mixTerms:{selected:1}}
        })];
      children.push({...GoogleMaterial3.action(k,each,40,content,{...v,selected:active}),
        style:S(i*(each+gap),0,each,40)});
    }
    return GoogleMaterial3.surface(key,w,40,children);
  }
  static textField(props={}) {
    const key=ui.str(props.key,'m3-textfield'),p=GoogleMaterial3.colors(props.colors),
      width=Math.max(160,N(props.width,320)),filled=props.variant!=='outlined',focused=!!props.focused;
    const label=ui.str(props.label,'Label'),value=typeof props.value==='string'?props.value:'';
    const bg=filled?p.surfaceContainerHighest:'#00000000';
    const labelY=(value||focused)?5:14,labelSize=(value||focused)?12:16;
    return GoogleMaterial3.action(key,width,56,[
      GoogleMaterial3.shape(`${key}:bg`,0,0,width,56,filled?4:4,bg,
        filled?undefined:(focused?p.primary:p.outline),filled?0:(focused?2:1)),
      ...(filled?[GoogleMaterial3.shape(`${key}:line`,0,54,width,focused?2:1,0,
        focused?p.primary:p.onSurfaceVariant)]:[]),
      GoogleMaterial3.text(`${key}:label`,label,16,labelY,width-32,23,
        {size:labelSize,medium:false,align:'left',color:focused?p.primary:p.onSurfaceVariant}),
      ...(value?[GoogleMaterial3.text(`${key}:value`,value,16,26,width-32,25,
        {size:16,medium:false,align:'left',color:p.onSurface})]:[]),
      ...(focused?[GoogleMaterial3.shape(`${key}:caret`,16+Math.min(width-48,Math.max(0,ui.num(props.caretOffset,value.length*8.7))),33,1.5,18,0,p.primary)]:[])
    ],props);
  }
  static slider(props={}) {
    const key=ui.str(props.key,'m3-slider'),p=GoogleMaterial3.colors(props.colors);
    const width=Math.max(72,N(props.width,280));const min=ui.num(props.min,0),max=ui.num(props.max,100);
    const value=Math.max(min,Math.min(max,ui.num(props.value,min)));
    const t=(value-min)/Math.max(1e-9,max-min);
    return GoogleMaterial3.action(key,width,40,[
      GoogleMaterial3.shape(`${key}:inactive`,0,17,width,6,3,p.secondaryContainer),
      GoogleMaterial3.shape(`${key}:active`,0,17,Math.max(1,width*t),6,3,p.primary),
      GoogleMaterial3.shape(`${key}:handle`,Math.max(0,Math.min(width-4,width*t-2)),7,4,26,2,p.primary)
    ],props);
  }
  static card(props={}) {
    const key=ui.str(props.key,'m3-card'),p=GoogleMaterial3.colors(props.colors);
    const width=Math.max(100,N(props.width,300)),height=Math.max(64,N(props.height,150));
    const variant=ui.str(props.variant,'filled');
    const bg=variant==='outlined'?p.surface:variant==='elevated'?p.surfaceContainerLow:p.surfaceContainerHighest;
    const children=[GoogleMaterial3.shape(`${key}:container`,0,0,width,height,12,bg,
      variant==='outlined'?p.outlineVariant:undefined,variant==='outlined'?1:0)];
    if(props.title)children.push(GoogleMaterial3.text(`${key}:title`,props.title,16,14,width-32,29,
      {color:p.onSurface,size:18,align:'left'}));
    if(props.body)children.push(GoogleMaterial3.text(`${key}:body`,props.body,16,53,width-32,26,
      {color:p.onSurfaceVariant,size:14,medium:false,align:'left'}));
    return props.onClick?GoogleMaterial3.action(key,width,height,children,props):GoogleMaterial3.surface(key,width,height,children);
  }
  static listItem(props={}) {
    const key=ui.str(props.key,'m3-list-item'),p=GoogleMaterial3.colors(props.colors);
    const width=Math.max(100,N(props.width,350)),h=props.supporting?72:56;
    return GoogleMaterial3.action(key,width,h,[
      GoogleMaterial3.shape(`${key}:bg`,0,0,width,h,0,props.selected?p.secondaryContainer:'#00000000'),
      GoogleMaterial3.stateLayer(`${key}:state`,width,h,0,p.onSurface,!!props.disabled),
      ...(props.icon?[ui.svg({key:`${key}:icon`,asset:props.icon,tint:p.onSurfaceVariant,
        interactive:false,class:ui.abs(16,(h-24)/2,24,24)})]:[]),
      GoogleMaterial3.text(`${key}:title`,ui.str(props.title,'List item'),props.icon?56:16,
        props.supporting?9:0,width-(props.icon?72:32),props.supporting?32:h,
        {color:p.onSurface,size:16,medium:false,align:'left'}),
      ...(props.supporting?[GoogleMaterial3.text(`${key}:support`,props.supporting,props.icon?56:16,37,
        width-(props.icon?72:32),24,{color:p.onSurfaceVariant,size:14,medium:false,align:'left'})]:[])
    ],props);
  }
  static progress(props={}) {
    const key=ui.str(props.key,'m3-progress'),p=GoogleMaterial3.colors(props.colors);
    const w=Math.max(24,N(props.width,280)),t=Math.max(0,Math.min(1,ui.num(props.value,.5)));
    return GoogleMaterial3.surface(key,w,8,[
      GoogleMaterial3.shape(`${key}:track`,0,2,w,4,2,p.secondaryContainer),
      GoogleMaterial3.shape(`${key}:active`,0,2,w*t,4,2,p.primary)
    ]);
  }
}
