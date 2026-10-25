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
    error:'#FFF2B8B5', onError:'#FF601410', inverseSurface:'#FFE6E0E9',
    inverseOnSurface:'#FF322F35', inversePrimary:'#FF6750A4'
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
  /** Reuses Combatant's soft shadow pass. Hover intensity is interpolated by
   * UiReactiveVisual using the owner's retained state, not rerun through JS. */
  /** Retained ripple surface; start/origin/release are owned by CombatantInput. */
  static ripple(key,w,h,radius,tint) {
    return ui.panel({key:`${key}:ripple`,interactive:false,stateSource:'parent',
      materialRippleSurface:true,rippleCornerRadius:radius,rippleColor:tint,
      style:ui.absolute(0,0,w,h,{borderRadius:radius}),children:[]});
  }
  static elevation(key,w,h,radius,level=1) {
    const blur=level>=3?16:level>=2?12:8;
    return ui.panel({key,interactive:false,stateSource:'parent',
      shadowColorReactive:{base:'#FF000000',alphaBase:level>=3?.31:.23,
        alphaTerms:{hover:.09,press:-.06}},
      style:ui.absolute(0,0,w,h,{borderRadius:radius,shadowColor:'#FF000000',
        shadowBlur:blur,shadowInnerAlpha:.32}),children:[]});
  }
  static action(key,w,h,children,props={}) {
    const disabled=!!props.disabled;
    const visualChildren = props.focusVisible && !disabled
      ? [...children, GoogleMaterial3.shape(`${key}:focus-ring`,-3,-3,w+6,h+6,
          Math.min(h/2+3,28),'#00000000',GoogleMaterial3.colors(props.colors).primary,2)]
      : children;
    return ui.button({key,class:ui.abs(0,0,w,h,disabled?'':'cursor-pointer'),
      materialRipple:!!props.materialRipple && !disabled,
      stateValues:{disabled, selected:!!props.selected,...(props.stateValues||{})},
      stateMotions:{hover:{durationMs:150,easing:'out-cubic'},press:{durationMs:110,easing:'out-cubic'},
        selected:{durationMs:170,easing:'out-cubic'},...(props.stateMotions||{})},
      ...(!disabled && props.onClick?{onClick:props.onClick}:{}),children:visualChildren});
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
      ...(variant==='elevated' && !disabled
        ?[GoogleMaterial3.elevation(`${key}:elevation`,width,height,20,1)]:[]),
      GoogleMaterial3.shape(`${key}:container`,0,0,width,height,20,bg,
        variant==='outlined'?(disabled?'#1FFFFFFF':p.outline):undefined,variant==='outlined'?1:0),
      GoogleMaterial3.stateLayer(`${key}:state`,width,height,20,foreground,disabled),
      GoogleMaterial3.ripple(key,width,height,20,foreground),
      GoogleMaterial3.text(`${key}:text`,label,12,0,width-24,height,{color:foreground})
    ];
    return GoogleMaterial3.action(key,width,height,children,{...props,materialRipple:true});
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
      GoogleMaterial3.ripple(key,40,40,20,fg),
      ui.svg({key:`${key}:icon`,asset:ui.str(props.icon,'check'),tint:fg,
        interactive:false,class:ui.abs(10,10,20,20)})
    ],{...props,materialRipple:true});
  }
  static fab(props={}) {
    const key=ui.str(props.key,'m3-fab'),p=GoogleMaterial3.colors(props.colors);
    const extended=!!props.label, w=extended?Math.max(104,N(props.width,148)):56;
    const bg=ui.str(props.variant,'primary')==='surface'?p.surfaceContainerHigh:p.primaryContainer;
    const fg=ui.str(props.variant,'primary')==='surface'?p.primary:p.onPrimaryContainer;
    return GoogleMaterial3.action(key,w,56,[
      GoogleMaterial3.elevation(`${key}:elevation`,w,56,16,3),
      GoogleMaterial3.shape(`${key}:bg`,0,0,w,56,16,bg),
      GoogleMaterial3.stateLayer(`${key}:state`,w,56,16,fg,!!props.disabled),
      GoogleMaterial3.ripple(key,w,56,16,fg),
      ui.svg({key:`${key}:icon`,asset:ui.str(props.icon,'user-plus'),tint:fg,interactive:false,
        class:ui.abs(extended?16:16,16,24,24)}),
      ...(extended?[GoogleMaterial3.text(`${key}:label`,props.label,48,0,w-60,56,
        {color:fg,align:'left',size:14})]:[])
    ],{...props,materialRipple:true});
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
      ...(selected&&props.icons?[ui.svg({key:`${key}:check`,asset:'check',tint:p.onPrimaryContainer,
        class:ui.abs(hx+4,10,16,12),interactive:false})]:[]),
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
      ...(active?[ui.svg({key:`${key}:mark`,asset:indeterminate?'minus':'check',tint:p.onPrimary,
        class:ui.abs(13,13,14,14),interactive:false})]:[])
    ],props);
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
    const selected=!!props.selected,icon=props.icon|| (variant==='filter'&&selected?'check':null);
    const width=Math.max(70,N(props.width,44+label.length*8));
    const fg=p.onSurface, bg=selected?p.secondaryContainer:'#00000000';
    return GoogleMaterial3.action(key,width,32,[
      GoogleMaterial3.shape(`${key}:bg`,0,0,width,32,8,p.secondaryContainer,p.outline,1,
        {fillReactive:{base:p.secondaryContainer,alphaBase:0,alphaTerms:{selected:1}},
          strokeReactive:{base:p.outline,alphaBase:1,alphaTerms:{selected:-1}},
          stateSource:'parent'}),
      GoogleMaterial3.stateLayer(`${key}:state`,width,32,8,fg,!!props.disabled),
      ...(icon?[ui.svg({key:`${key}:icon`,asset:icon,tint:fg,class:ui.abs(9,7,18,18),interactive:false})]:[]),
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
   * M3 Expressive Connected Button Group (small / 40dp).
   * Each button owns exactly one analytic rounded surface and its clipped
   * state layer. No track underlay, duplicate outline or moving selection pill.
   * Selection/press morph the actual four corners from 8dp to 20dp;
   * the outer ends are 20dp even when unselected.
   * Reference: MDC Android MaterialButtonGroup.Connected (2dp gap).
   */
  static connectedButtonGroup(props={}) {
    const key=ui.str(props.key,'m3-connected'),items=Array.isArray(props.items)?props.items:[],
      p=GoogleMaterial3.colors(props.colors),w=Math.max(1,N(props.width,420));
    if (!items.length) return GoogleMaterial3.surface(key,w,40,[]);
    const gap=2,each=(w-gap*(items.length-1))/items.length;
    const selected=ui.str(props.selected,'');
    const children=[];
    for(let i=0;i<items.length;i++) {
      const v=items[i],id=ui.str(v.id,String(i)),k=`${key}:${id}`;
      const active=id===selected, disabled=!!v.disabled;
      const corners=[i===0?20:8,i===items.length-1?20:8,
        i===items.length-1?20:8,i===0?20:8];
      // Selection is the only morph source. Mixing the press envelope (110ms)
      // with selection (170ms) caused the radius to jump on pointer release.
      const layer={stateSource:'parent',cornerMorphTo:20};
      const content=[
        GoogleMaterial3.cornerBox(`${k}:container`,0,0,each,40,corners,
          p.surfaceContainerLow,p.outline,1,{
            ...layer,
            fillReactive:{base:p.surfaceContainerLow,mix:p.secondaryContainer,
              mixTerms:{selected:1}},
            // Keep the contour visible while selected. Its opacity is driven by
            // the SAME retained selection signal as fill and corner geometry.
            strokeReactive:{base:p.outline,alphaBase:1,
              alphaTerms:{selected:-0.32}}
          }),
        GoogleMaterial3.cornerBox(`${k}:state`,0,0,each,40,corners,
          '#00000000',undefined,0,{
            ...layer,
            fillReactive:{base:p.onSurface,alphaBase:0,
              alphaTerms:disabled?{}:{hover:.08,press:.10}}
          }),
        GoogleMaterial3.text(`${k}:label`,ui.str(v.label,''),12,0,each-24,40,{
          color:p.onSurface,
          reactive:{base:p.onSurface,mix:p.onSecondaryContainer,
            mixTerms:{selected:1}}
        })
      ];
      children.push({...GoogleMaterial3.action(k,each,40,content,
        {onClick:v.onClick,selected:active,disabled,
          stateMotions:{selected:{durationMs:240,easing:'in-out-cubic'}}}),
        style:S(i*(each+gap),0,each,40)});
    }
    return GoogleMaterial3.surface(key,w,40,children);
  }
  static textField(props={}) {
    const key=ui.str(props.key,'m3-textfield'),p=GoogleMaterial3.colors(props.colors);
    const width=Math.max(160,N(props.width,320));
    const filled=props.variant!=='outlined',focused=!!props.focused;
    const label=ui.str(props.label,'Label');
    const value=typeof props.value==='string'?props.value:'';
    const floating=focused||value.length>0;
    const bg=filled?p.surfaceContainerHighest:'#00000000';
    const focusMotions={focusVisual:{durationMs:180,easing:'in-out-cubic'},
      floatingLabel:{durationMs:190,easing:'in-out-cubic'}};
    // Native retained signals: no label position, font size, or stroke jump when
    // the JS model is rebuilt. Both styles share the same component interaction.
    const frame=GoogleMaterial3.shape(`${key}:bg`,0,0,width,56,4,bg,
      filled?undefined:p.outline,filled?0:1,filled?{}:{
        strokeReactive:{base:p.outline,mix:p.primary,mixTerms:{focusVisual:1}},
        strokeWidthMorphTo:2,strokeWidthMorphSignal:'focusVisual',stateSource:'parent'
      });
    const elements=[frame];
    if(filled){
      elements.push(GoogleMaterial3.shape(`${key}:line`,0,55,width,1,0,p.onSurfaceVariant));
      elements.push(GoogleMaterial3.shape(`${key}:active-line`,0,54,width,2,0,p.primary,
        undefined,0,{fillReactive:{base:p.primary,alphaBase:0,
          alphaTerms:{focusVisual:1}},stateSource:'parent'}));
    }
    // Material text field typography: 16sp resting/value and 12sp floating.
    // Slightly larger physical text is intentional in the Combatant preview:
    // the MSDF font metrics and game UI scale differ from CSS dp.
    if(!filled){
      // Under the floating label only: a local notch cutout matching the host
      // surface. Fade it with the same animation instead of swapping outlines.
      elements.push(GoogleMaterial3.shape(`${key}:notch`,11,-1,
        Math.min(width-24,Math.max(28,label.length*8.2+11)),3,0,p.surface,undefined,0,{
          fillReactive:{base:p.surface,alphaBase:0,alphaTerms:{floatingLabel:1}},
          stateSource:'parent'
        }));
    }
    elements.push(ui.text({
      key:`${key}:label`,text:label,color:p.onSurfaceVariant,
      colorReactive:{base:p.onSurfaceVariant,mix:p.primary,mixTerms:{focusVisual:1}},
      stateSource:'parent',verticalAlign:'center',
      // Shared generic renderer text morph: font size and Y offset use precisely
      // the same retained signal. Never replace the text node during transition.
      fontSizeMorphTo:14,textMorphOffsetY:filled?-15:-23,
      textMorphSignal:'floatingLabel',interactive:false,
      class:ui.abs(16,12,width-32,30,'font-Roboto font-size-18 line-height-26 text-align-left')
    }));
    if(value){
      elements.push(GoogleMaterial3.text(`${key}:value`,value,16,27,width-32,25,
        {size:18,medium:false,align:'left',color:p.onSurface}));
    }
    if(focused){
      elements.push(GoogleMaterial3.shape(`${key}:caret`,
        16+Math.min(width-48,Math.max(0,ui.num(props.caretOffset,value.length*9.6))),32,
        1.5,20,0,p.primary,undefined,0,{
          fillReactive:{base:p.primary,alphaBase:0,alphaTerms:{focusVisual:1}},
          stateSource:'parent'
        }));
    }
    return GoogleMaterial3.action(key,width,56,elements,{
      ...props,stateValues:{floatingLabel:floating,focusVisual:focused},
      stateMotions:focusMotions
    });
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
    const children=[
      ...(variant==='elevated'?[GoogleMaterial3.elevation(`${key}:elevation`,width,height,12,1)]:[]),
      GoogleMaterial3.shape(`${key}:container`,0,0,width,height,12,bg,
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

  /** Menu layout is intrinsic to the items, not a fixed N*48 heuristic.
   * Dividers and non-interactive group headings participate in measuring but
   * never receive a hit target or keyboard focus. */
  static menuMetrics(items) {
    let y=8;
    const rows=[];
    for(const item of (Array.isArray(items)?items:[])) {
      const kind=ui.str(item.kind,'item');
      const height=kind==='divider'?16:kind==='header'?36:48;
      rows.push({item,kind,y,height,interactive:kind==='item'&&!item.disabled});
      y+=height;
    }
    return {height:y+8,rows};
  }
  /** Reusable Material menu. Anchor placement and dismissal belong to the
   * caller (a top-layer host), not to each menu row. Supports leading icons,
   * checkbox/radio selection, trailing shortcut, disabled rows, dividers,
   * group headings and submenu disclosure. */
  static menu(props={}) {
    const key=ui.str(props.key,'m3-menu'),p=GoogleMaterial3.colors(props.colors);
    const items=Array.isArray(props.items)?props.items:[];
    const w=Math.max(112,N(props.width,264)),metrics=GoogleMaterial3.menuMetrics(items);
    const h=metrics.height,keyboard=ui.num(props.keyboardIndex,-1);
    const children=[
      GoogleMaterial3.elevation(`${key}:elevation`,w,h,4,2),
      GoogleMaterial3.shape(`${key}:surface`,0,0,w,h,4,p.surfaceContainer),
      ui.button({key:`${key}:hit`,onClick:props.onBackgroundClick,
        class:ui.abs(0,0,w,h),children:[]})
    ];
    for(let i=0;i<metrics.rows.length;i++) {
      const {item,kind,y,height}=metrics.rows[i];
      const rowKey=`${key}:${ui.str(item.id,String(i))}`;
      if(kind==='divider') {
        children.push(GoogleMaterial3.shape(`${rowKey}:line`,12,y+7,w-24,1,0,p.outlineVariant));
        continue;
      }
      if(kind==='header') {
        children.push(GoogleMaterial3.text(`${rowKey}:heading`,ui.str(item.label,''),16,y,w-32,height,
          {size:16,color:p.onSurfaceVariant,medium:true,align:'left'}));
        continue;
      }
      const disabled=!!item.disabled,checked=!!item.checked,selected=!!item.selected;
      const fg=disabled?'#61FFFFFF':(selected||checked?p.onSurface:p.onSurface);
      const hasIcon=!!item.icon,kindSelect=item.selection==='checkbox'||item.selection==='radio';
      const left=hasIcon||kindSelect?52:16;
      const trailing=item.submenu?'submenu':ui.str(item.shortcut,'');
      const trailingWidth=trailing?Math.min(106,Math.max(36,trailing.length*9+14)):12;
      const row=[
        GoogleMaterial3.shape(`${rowKey}:state`,0,0,w-8,48,4,'#00000000',undefined,0,{
          fillReactive:{base:p.onSurface,alphaBase:0,
            alphaTerms:disabled?{}:{hover:.08,press:.10,selected:.10}},stateSource:'parent'}),
        ...(hasIcon?[ui.svg({key:`${rowKey}:icon`,asset:ui.str(item.icon,''),
          tint:disabled?'#61FFFFFF':p.onSurfaceVariant,
          class:ui.abs(15,14,20,20),interactive:false})]:[]),
        ...(kindSelect&&checked?[ui.svg({key:`${rowKey}:check`,asset:'check',
          tint:disabled?'#61FFFFFF':p.primary,
          class:ui.abs(15,14,20,20),interactive:false})]:[]),
        GoogleMaterial3.text(`${rowKey}:label`,ui.str(item.label,''),left,0,
          Math.max(20,w-left-trailingWidth-16),48,
          {size:20,medium:false,align:'left',color:fg}),
        ...(item.submenu?[ui.svg({key:`${rowKey}:submenu`,asset:'chevron-right',
          tint:disabled?'#61FFFFFF':p.onSurfaceVariant,
          class:ui.abs(w-34,15,18,18),interactive:false})]:[]),
        ...(!item.submenu&&item.shortcut?[GoogleMaterial3.text(`${rowKey}:shortcut`,
          ui.str(item.shortcut,''),w-trailingWidth-12,0,trailingWidth,48,
          {size:16,medium:false,align:'right',color:p.onSurfaceVariant})]:[]),
      ];
      children.push({...GoogleMaterial3.action(rowKey,w-8,48,row,{
        onClick:item.onClick,disabled,selected:selected||checked||keyboard===i,
        stateMotions:{selected:{durationMs:140,easing:'out-cubic'}}}),
        style:ui.absolute(4,y,w-8,48)});
    }
    return GoogleMaterial3.surface(key,w,h,children);
  }
  /** Material 3 plain tooltip: non-modal, separate from the target control. */
  static tooltip(props={}) {
    const key=ui.str(props.key,'m3-tooltip'),p=GoogleMaterial3.colors(props.colors);
    const label=ui.str(props.label,'Tooltip');
    const w=Math.max(48,N(props.width,Math.min(240,24+label.length*7.6)));
    return GoogleMaterial3.surface(key,w,28,[
      GoogleMaterial3.shape(`${key}:surface`,0,0,w,28,4,p.inverseSurface),
      GoogleMaterial3.text(`${key}:label`,label,8,0,w-16,28,{size:12,
        medium:false,align:'center',color:p.inverseOnSurface})
    ]);
  }
  /** Material 3 snackbar is a transient status surface, not a dialog. */
  static snackbar(props={}) {
    const key=ui.str(props.key,'m3-snackbar'),p=GoogleMaterial3.colors(props.colors);
    const message=ui.str(props.message,'Changes saved');
    const actionLabel=ui.str(props.actionLabel,'');
    const w=Math.max(280,N(props.width,400)),h=48;
    const actionWidth=actionLabel?Math.max(64,actionLabel.length*10+20):0;
    return GoogleMaterial3.surface(key,w,h,[
      GoogleMaterial3.elevation(`${key}:elevation`,w,h,4,2),
      GoogleMaterial3.shape(`${key}:surface`,0,0,w,h,4,p.inverseSurface),
      GoogleMaterial3.text(`${key}:label`,message,16,0,w-actionWidth-48,h,
        {size:14,medium:false,align:'left',color:p.inverseOnSurface}),
      ...(actionWidth?[{...GoogleMaterial3.button({key:`${key}:action`,label:actionLabel,
        variant:'text',width:actionWidth,colors:{...p,primary:p.inversePrimary},
        onClick:props.onAction}),style:ui.absolute(w-actionWidth-40,4,actionWidth,40)}]:[]),
      {...GoogleMaterial3.iconButton({key:`${key}:close`,icon:'x',onClick:props.onDismiss,
        colors:{...p,onSurfaceVariant:p.inverseOnSurface}}),style:ui.absolute(w-44,4,40,40)}
    ]);
  }
  /** Alert dialog content, independent of the host's scrim and focus lifecycle. */
  static dialog(props={}) {
    const key=ui.str(props.key,'m3-dialog'),p=GoogleMaterial3.colors(props.colors);
    const w=Math.max(280,N(props.width,440)),h=Math.max(200,N(props.height,238));
    const actions=Array.isArray(props.actions)?props.actions:[];
    const children=[
      GoogleMaterial3.elevation(`${key}:elevation`,w,h,28,3),
      GoogleMaterial3.shape(`${key}:surface`,0,0,w,h,28,p.surfaceContainerHigh),
      ui.button({key:`${key}:hit`,onClick:props.onBackgroundClick,
        class:ui.abs(0,0,w,h),children:[]}),
      GoogleMaterial3.text(`${key}:title`,ui.str(props.title,'Dialog'),24,18,w-48,42,
        {size:24,medium:false,align:'left'}),
      GoogleMaterial3.text(`${key}:body`,ui.str(props.body,''),24,74,w-48,52,
        {size:16,medium:false,align:'left',color:p.onSurfaceVariant})
    ];
    let cursor=w-24;
    for(let i=actions.length-1;i>=0;i--) {
      const action=actions[i],label=ui.str(action.label,'Action');
      const bw=Math.max(88,N(action.width,96));cursor-=bw;
      children.push({...GoogleMaterial3.button({key:`${key}:action:${i}`,
        width:bw,label,variant:ui.str(action.variant,'text'),onClick:action.onClick,
        focusVisible:!!action.focusVisible}),style:ui.absolute(cursor,h-70,bw,40)});
      cursor-=8;
    }
    return GoogleMaterial3.surface(key,w,h,children);
  }
  /** M3 filled/outlined select field; popup is rendered in the kit's top layer. */
  static select(props={}) {
    const key=ui.str(props.key,'m3-select'),p=GoogleMaterial3.colors(props.colors);
    const w=Math.max(160,N(props.width,300)),h=56,outlined=props.variant==='outlined';
    const children=[
      GoogleMaterial3.shape(`${key}:surface`,0,0,w,h,4,
        outlined?'#00000000':p.surfaceContainerHighest,
        outlined?p.outline:undefined,outlined?1:0),
      GoogleMaterial3.text(`${key}:label`,ui.str(props.label,'Appearance'),16,2,w-60,22,
        {size:12,color:p.onSurfaceVariant,medium:false,align:'left'}),
      GoogleMaterial3.text(`${key}:value`,ui.str(props.value,'System default'),16,20,w-62,32,
        {size:16,color:p.onSurface,medium:false,align:'left'}),
      ui.svg({key:`${key}:arrow`,asset:'chevron-down',tint:p.onSurfaceVariant,
        class:ui.abs(w-36,18,20,20),interactive:false}),
      GoogleMaterial3.stateLayer(`${key}:state`,w,h,4,p.onSurface,!!props.disabled)
    ];
    return GoogleMaterial3.action(key,w,h,children,props);
  }
  static searchBar(props={}) {
    const key=ui.str(props.key,'m3-search'),p=GoogleMaterial3.colors(props.colors),
      w=Math.max(220,N(props.width,440)),h=56;
    const value=ui.str(props.value,''),focused=!!props.focused;
    return GoogleMaterial3.action(key,w,h,[
      GoogleMaterial3.shape(`${key}:surface`,0,0,w,h,28,p.surfaceContainerHigh),
      ui.svg({key:`${key}:leading`,asset:'search',tint:p.onSurfaceVariant,
        class:ui.abs(16,16,24,24),interactive:false}),
      GoogleMaterial3.text(`${key}:value`,value||'Search',56,0,w-96,h,
        {size:16,align:'left',medium:false,color:value?p.onSurface:p.onSurfaceVariant}),
      ...(focused?[GoogleMaterial3.shape(`${key}:cursor`,56+Math.min(w-104,value.length*9),17,1.5,22,0,p.primary)]:[]),
      GoogleMaterial3.stateLayer(`${key}:state`,w,h,28,p.onSurface,!!props.disabled)
    ],props);
  }
  static navigationRail(props={}) {
    const key=ui.str(props.key,'m3-navigation-rail'),p=GoogleMaterial3.colors(props.colors),
      items=Array.isArray(props.items)?props.items:[],w=96,h=Math.max(80,items.length*80+20);
    const children=[GoogleMaterial3.shape(`${key}:background`,0,0,w,h,0,p.surface)];
    items.forEach((item,i)=>{
      const id=ui.str(item.id,String(i)),chosen=id===props.selected,y=10+i*80,k=`${key}:${id}`;
      const slot=[
        GoogleMaterial3.shape(`${k}:indicator`,16,0,64,32,16,p.secondaryContainer,
          undefined,0,{fillReactive:{base:p.secondaryContainer,alphaBase:0,
            alphaTerms:{selected:1}},stateSource:'parent'}),
        ui.svg({key:`${k}:icon`,asset:ui.str(item.icon,'check'),
          tint:chosen?p.onSecondaryContainer:p.onSurfaceVariant,
          interactive:false,class:ui.abs(36,4,24,24)}),
        GoogleMaterial3.text(`${k}:label`,ui.str(item.label,''),0,34,w,28,
          {size:12,medium:chosen,color:chosen?p.onSurface:p.onSurfaceVariant})
      ];
      children.push({...GoogleMaterial3.action(k,w,72,slot,
        {selected:chosen,onClick:item.onClick}),style:S(0,y,w,72)});
    });
    return GoogleMaterial3.surface(key,w,h,children);
  }
  static badge(props={}) {
    const key=ui.str(props.key,'m3-badge'),p=GoogleMaterial3.colors(props.colors);
    const text=ui.str(props.label,''),w=text?Math.max(16,14+text.length*8):6,h=text?16:6;
    return GoogleMaterial3.surface(key,w,h,[
      GoogleMaterial3.shape(`${key}:surface`,0,0,w,h,h/2,p.error),
      ...(text?[GoogleMaterial3.text(`${key}:label`,text,4,0,w-8,h,
        {size:11,color:p.onError})]:[])
    ]);
  }
}
