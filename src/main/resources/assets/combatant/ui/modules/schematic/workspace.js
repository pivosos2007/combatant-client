/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from '../../api/kits/adobe/spectrum2/foundation.js';
import { SpectrumButton } from '../../api/kits/adobe/spectrum2/button.js';
import { SpectrumActionButton } from '../../api/kits/adobe/spectrum2/action-button.js';
import { SpectrumSearchField } from '../../api/kits/adobe/spectrum2/search-field.js';
import { SpectrumSegmentedControl } from '../../api/kits/adobe/spectrum2/segmented-control.js';
import { SpectrumProgressBar } from '../../api/kits/adobe/spectrum2/progress-bar.js';
import { SpectrumCardView } from '../../api/kits/adobe/spectrum2/card-view.js';
import { SpectrumToast } from '../../api/kits/adobe/spectrum2/toast.js';
import { SpectrumSwitch } from '../../api/kits/adobe/spectrum2/switch.js';
import { SpectrumTextField } from '../../api/kits/adobe/spectrum2/text-field.js';
import { SpectrumPicker } from '../../api/kits/adobe/spectrum2/picker.js';
import { SpectrumNumberField } from '../../api/kits/adobe/spectrum2/number-field.js';
import { SpectrumColorSwatchGroup } from '../../api/kits/adobe/spectrum2/color-swatch-group.js';
import { SpectrumColorArea } from '../../api/kits/adobe/spectrum2/color-area.js';
import { SpectrumColorSlider } from '../../api/kits/adobe/spectrum2/color-slider.js';
import { SpectrumActionGroup } from '../../api/kits/adobe/spectrum2/action-group.js';
import { argbToHsv } from '../../api/kits/adobe/spectrum2/color-utils.js';
const FORMAT_COLOR = {LITEMATIC:'#FFA88BFF', SCHEM:'#FF5CC6EF', SCHEMATIC:'#FF78D7A2', NBT:'#FFF2B966', FILE:'#FFADB3C0'};
const formatColor = format => FORMAT_COLOR[String(format||'FILE').replace(/^\./,'').toUpperCase()] || FORMAT_COLOR.FILE;

const S = SpectrumFoundation;
const act = key => `schematic:${key}`;
const at = (node,x,y,w,h) => ({...node,style:ui.absolute(x,y,w,h)});
const N=(v,f=0)=>Number.isFinite(Number(v))?Number(v):f;
const A=v=>Array.isArray(v)?v:[];
const R=v=>({x:N(v?.x),y:N(v?.y),w:Math.max(1,N(v?.w,1)),h:Math.max(1,N(v?.h,1))});
const tx=(key,s,x,y,w,h,c,size=15,strong=false,align='left')=>S.text(key,s??'',x,y,Math.max(1,w),Math.max(1,h),{color:c,size,strong,align});
const txClip=(key,s,x,y,w,h,c,size=15,strong=false,align='left')=>ui.text({
  key,text:String(s??''),color:c,interactive:false,
  class:ui.abs(x,y,Math.max(1,w),Math.max(1,h),
    `font-${strong?'SourceSans3Semibold':'SourceSans3'} font-size-${size} text-align-${align} clip`)
});
const bg=(key,x,y,w,h,c,stroke,rad=0)=>S.rect(key,x,y,w,h,rad,c,stroke,stroke?1:0);
// A short, legible file-type mark. No text fading; the surrounding list/card owns clipping.
function formatMark(key,format,x,y,p){
  const label=String(format||'FILE').replace(/^\./,'').toUpperCase();
  const w=Math.min(120,Math.max(55,label.length*8+18));
  const tone=formatColor(label);
  return {width:w,node:ui.stack({key,interactive:false,class:ui.abs(x,y,w,27),children:[
    bg(`${key}:surface`,0,0,w,27,S.withOpacity(tone,.12),S.withOpacity(tone,.42),4),
    ui.text({key:`${key}:label`,text:label,color:tone,interactive:false,verticalAlign:'center',
      class:ui.abs(5,1,w-10,24,'font-Iosevka-Regular font-size-13 text-align-center')})
  ]})};
}
const rule=(key,x,y,w,p)=>bg(key,x,y,Math.max(1,w),1,p.border);
function actionButton(key,icon,x,y,p,{selected=false,disabled=false,size=34,quiet=true,action=key}={}){
  return at(SpectrumActionButton.render({key,icon,width:size,size:'S',quiet,selected,disabled,colors:p,onClick:act(action)}),x,y,size,32);
}
function textButton(key,label,x,y,w,p,{selected=false,disabled=false,quiet=true,action=key}={}){
  return at(SpectrumActionButton.render({key,label,width:Math.max(32,w),size:'S',quiet,selected,disabled,colors:p,onClick:act(action)}),x,y,w,32);
}
function button(key,label,x,y,w,p,{disabled=false,primary=false,action=key}={}) {
  return at(SpectrumButton.render({key,label,width:w,size:'S',variant:primary?'accent':'secondary',
    outline:!primary,disabled,colors:p,onClick:act(action)}),x,y,w,32);
}
function seg(key,items,selected,x,y,w,p) {
  return at(SpectrumSegmentedControl.render({key,width:w,size:'S',selectedId:selected,
    items:items.map(([id,label,action,disabled])=>({id,label,onClick:act(action),disabled:!!disabled})),colors:p}),x,y,w,32);
}
function group(key,r,children) {return ui.stack({key,class:ui.abs(r.x,r.y,r.w,r.h),children});}

// One application header. Its identity and actions remain stable between modes.
function keycap(key,label,x,y,p,width=39){
  return group(key,{x,y,w:width,h:28},[
    bg(`${key}:outline`,0,1,width,25,p.field,p.border,5),
    tx(`${key}:text`,label,0,0,width,27,p.text,14,true,'center')]);
}
function gesture(key,asset,caption,x,y,w,p){
  return group(key,{x,y,w,h:30},[
    bg(`${key}:badge`,0,1,28,27,p.field,p.border,5),
    ui.svg({key:`${key}:icon`,asset,tint:p.text,interactive:false,class:ui.abs(5,5,18,18)}),
    tx(`${key}:caption`,caption,35,2,w-35,26,p.muted,15)]);
}
function header(d,p,w,h,rail) {
  const browsing=!!d.libraryMode;
  const configuring=!!d.liteSettingsMode;
  const name=configuring?'Litematica':
    browsing?(d.labels?.title||'Schematics'):(d.loaded?d.title:(d.labels?.title||'Schematics'));
  const left=rail+14, available=Math.max(40,w-left-(d.standalone?62:17));
  const titleW=Math.min(300,Math.max(110,available*.34));
  const nodes=[bg('app:header:bg',0,0,w,h,p.surface),rule('app:header:rule',0,h-1,w,p),
    ui.svg({key:'app:header:mark',asset:'layers',tint:p.accent,interactive:false,
      class:ui.abs(rail+14,(h-21)/2,21,21)}),
    ui.text({key:'app:header:name',text:name,color:p.text,interactive:false,verticalAlign:'center',
      runtimeMarquee:true,stateSource:'parent',marqueeSpeed:25,
      class:ui.abs(left+26,0,titleW-10,h,'font-SourceSans3Semibold font-size-19 clip overflow-hidden')})];
  if(!browsing && !configuring && d.loaded){
    const hintStart=left+titleW+20;
    const room=w-hintStart-(d.standalone?70:16);
    if(room>=420){
      nodes.push(gesture('app:help:orbit','mouse-left',d.view==='2d'?'Pan':'Orbit',hintStart,12,98,p));
      nodes.push(gesture('app:help:pan','mouse-right','Pan',hintStart+102,12,89,p));
      nodes.push(gesture('app:help:wheel','mouse-wheel','Zoom',hintStart+196,12,96,p));
      nodes.push(keycap('app:help:shift','Shift',hintStart+303,12,p,47));
      nodes.push(tx('app:help:shift-text','+ drag to pan',hintStart+354,13,Math.max(35,room-354),27,p.muted,15));
    }else if(room>=257){
      nodes.push(gesture('app:help:orbit','mouse-left',d.view==='2d'?'Pan':'Orbit',hintStart,12,92,p));
      nodes.push(gesture('app:help:pan','mouse-right','Pan',hintStart+88,12,82,p));
      nodes.push(gesture('app:help:wheel','mouse-wheel','Zoom',hintStart+178,12,83,p));
    }
  }
  if(d.standalone)nodes.push(actionButton('app:close','x',w-50,10,p,{quiet:false,action:'close'}));
  return nodes;
}

const LITE_CATEGORIES=[
  ['generic','sliders-horizontal','General'],
  ['visuals','eye','Visuals'],
  ['overlays','layout-dashboard','Info overlays'],
  ['colors','palette','Colors'],
  ['hotkeys','keyboard','Hotkeys']
];
function toolRail(d,p,h,head,rail) {
  const configuring=!!d.liteSettingsMode,editing=!d.libraryMode&&!configuring;
  const inner=[
    actionButton('rail:library','folder-open',10,14,p,
      {size:34,selected:!!d.libraryMode,action:'library'}),
    actionButton('rail:editor','layers',10,56,p,
      {size:34,selected:editing,disabled:!d.loaded&&!d.loading&&!configuring,action:'editor'}),
    rule('rail:separator',12,104,rail-24,p),
    actionButton('rail:properties','settings-2',10,119,p,
      {size:34,selected:editing&&d.drawerMode==='properties',disabled:!editing,action:'properties-toggle'}),
    actionButton('rail:materials','package',10,161,p,
      {size:34,selected:editing&&d.drawerMode==='materials',disabled:!editing,action:'materials-toggle'}),
    actionButton('rail:layers','layers',10,203,p,
      {size:34,selected:editing&&d.drawerMode==='layers',disabled:!editing,action:'layers-toggle'}),
    rule('rail:litematica:separator',12,247,rail-24,p)
  ];
  LITE_CATEGORIES.forEach(([id,icon,label],i)=>{
    inner.push(actionButton(`rail:lite:${id}`,icon,10,260+i*42,p,
      {size:34,selected:configuring&&d.liteCategory===id,action:`lite-category:${id}`}));
  });
  const contentHeight=482;
  return [bg('rail:surface',0,head,rail,Math.max(1,h-head),p.surface),
    bg('rail:right-border',rail-1,head,1,Math.max(1,h-head),p.border),
    ui.scroll({key:'rail:navigation',style:ui.absolute(0,head,rail,Math.max(1,h-head),{overflow:'hidden'}),
      wheelStep:52,smoothScroll:true,scrollSmoothingRate:.02,scrollbar:true,scrollbarWidth:3,scrollbarOffset:-1,scrollbarColor:p.muted,
      children:[ui.stack({key:'rail:navigation:content',style:ui.style({width:rail,height:contentHeight}),children:inner})]})];
}

function litematicaSettings(d,p,x,y,w,h){
  const data=d.lite||{},options=A(data.options),compact=w<720;
  const searchW=Math.min(300,Math.max(220,w-48));
  const nodes=[bg('lite:background',x,y,w,h,p.background),
    txClip('lite:heading',data.categoryLabel||'General',x+24,y+13,
      Math.max(1,compact?w-48:w-searchW-96),38,p.text,24,true),
    tx('lite:count',`${N(data.count)} settings`,x+24,y+(compact?43:49),200,22,p.muted,15),
    rule('lite:header:separator',x,y+(compact?127:81),w,p)];
  nodes.push(at(SpectrumSearchField.render({key:'lite:search',width:searchW,labelHidden:true,
    value:data.search||'',placeholder:'Search Litematica',focused:!!data.searchFocused,
    scrollOffset:N(data.searchScroll),caretOffset:N(data.searchCaret),
    selectionStart:N(data.searchSelectionStart),selectionEnd:N(data.searchSelectionEnd),
    onClick:act('lite-search'),onClear:act('lite-search-clear'),colors:p}),
    compact?x+24:x+w-searchW-24,y+(compact?67:17),searchW,44));
  const rowH=N(data.rowHeight,78),viewX=x+24,viewY=y+(compact?141:94),viewW=Math.max(1,w-48);
  const viewH=Math.max(1,h-(compact?154:107)),contentW=Math.max(1,viewW-13);
  const expandedIndex=N(data.expandedIndex,-1),extra=N(data.expandedHeight);
  const total=N(data.total),rows=[];
  const inputs={scrollOffset:N(data.editScroll),caretOffset:N(data.editCaret),
    selectionStart:N(data.editSelectionStart),selectionEnd:N(data.editSelectionEnd)};
  const palette=A(data.colorPresets);
  function inlineField(id,value,fx,fy,fw){
    // Shared Spectrum TextField handles the text scissor, caret, and selection.
    return at(SpectrumTextField.render({key:`lite:inline:${id}`,labelHidden:true,
      width:Math.max(160,fw),value,focused:!!data.editFocused,colors:p,
      ...inputs,onClick:act('lite-edit-focus')}),fx,fy,Math.max(160,fw),44);
  }
  function confirmButtons(id,x,y){
    return [
      at(SpectrumActionButton.render({key:`lite:apply:${id}`,icon:'check',width:35,
        size:'S',quiet:false,colors:p,onClick:act('lite-edit-apply')}),x,y,35,34),
      at(SpectrumActionButton.render({key:`lite:cancel:${id}`,icon:'x',width:35,
        size:'S',quiet:false,colors:p,onClick:act('lite-edit-cancel')}),x+39,y,35,34)
    ];
  }
  options.forEach(o=>{
    const i=N(o.index),yy=i*rowH+(expandedIndex>=0&&i>expandedIndex?extra:0);
    const kind=String(o.type||'string'),active=!!o.editing,opened=!!o.expanded;
    const narrow=contentW<420;
    const fieldW=narrow?Math.max(160,contentW-32):Math.min(282,Math.max(185,contentW*.38));
    const fieldX=narrow?16:contentW-fieldW-14;
    const fieldY=yy+(narrow?67:17),textW=narrow?Math.max(30,contentW-34):Math.max(60,fieldX-30);
    const fullH=rowH-1+(opened?extra:0);
    rows.push(bg(`lite:row:${o.id}:surface`,0,yy,contentW,fullH,
      active||opened?p.raised:i%2?p.surface:p.background));
    if(o.invalid){
      rows.push(bg(`lite:invalid:${o.id}`,1,yy+1,contentW-2,Math.max(1,fullH-2),
        '#00000000',S.withOpacity(p.danger,.38+Math.max(0,N(data.errorPulse))*.62),7));
    }
    rows.push(rule(`lite:row:${o.id}:divider`,0,yy+fullH,contentW,p));
    rows.push(ui.button({key:`lite:row:${o.id}:hit`,
      class:ui.abs(0,yy,contentW,rowH-1,'cursor-pointer clip'),
      onClick:act(`lite-option:${o.id}`),stateValues:{selected:opened||active},
      stateMotions:{hover:{durationMs:120,easing:'out-cubic'},press:{durationMs:85,easing:'out-cubic'}},
      children:[
        ui.shape({key:`lite:row:${o.id}:hover`,shape:'rect',interactive:false,
          style:ui.absolute(0,0,contentW,rowH-1),fill:'#00000000',
          stateSource:'parent',fillReactive:{base:p.accent,alphaBase:0,
            alphaTerms:{hover:.075,press:.13}}}),
        ui.text({key:`lite:row:${o.id}:title`,text:String(o.label),color:p.text,
          interactive:false,runtimeMarquee:true,stateSource:'parent',marqueeSpeed:30,
          class:ui.abs(17,4,textW,31,'font-SourceSans3Semibold font-size-19 clip')}),
        ...(o.description?[ui.text({key:`lite:row:${o.id}:description`,text:String(o.description),
          color:p.muted,interactive:false,runtimeMarquee:true,stateSource:'parent',marqueeSpeed:25,
          class:ui.abs(17,38,textW,24,'font-SourceSans3 font-size-16 clip')})]:[])
      ]}));
    if(kind==='boolean'){
      rows.push(at(SpectrumSwitch.render({key:`lite:switch:${o.id}`,selected:String(o.value)==='true',
        width:54,emphasized:true,colors:p,onClick:act(`lite-option:${o.id}`)}),fieldX+fieldW-58,fieldY+6,54,32));
    } else if(kind==='number'){
      rows.push(at(SpectrumNumberField.render({key:`lite:inline:${o.id}`,
        labelHidden:true,size:'M',width:fieldW,focused:active,colors:p,
        invalid:!!o.invalid,hideErrorMessage:true,
        invalidStroke:S.withOpacity(p.danger,.38+Math.max(0,N(data.errorPulse))*.62),
        minReached:o.min!==''&&N(active?data.editValue:o.value)<=(Number(o.min)),
        maxReached:o.max!==''&&N(active?data.editValue:o.value)>=(Number(o.max)),
        value:active?data.editValue:o.value,...inputs,
        onFocus:act(`lite-option:${o.id}`),
        onIncrement:act(`lite-number:${o.id}:up`),onDecrement:act(`lite-number:${o.id}:down`)}),
        fieldX,fieldY,fieldW,40));
    } else if(kind==='choice'){
      rows.push(at(SpectrumPicker.render({key:`lite:picker:${o.id}`,width:fieldW,
        value:String(o.value||''),labelHidden:true,open:opened,colors:p,
        items:A(o.choices).map(choice=>({id:String(choice),label:String(choice)})),
        onClick:act(`lite-option:${o.id}`)}),fieldX,fieldY,fieldW,40));
      if(opened){
        const choices=A(o.choices),menuH=156;
        rows.push(bg(`lite:choice:${o.id}:surface`,fieldX,yy+rowH+3,fieldW,menuH,p.raised,p.border,8));
        const items=choices.map((choice,j)=>at(SpectrumActionButton.render({
          key:`lite:choice:${o.id}:${j}`,
          ...(String(choice)===String(o.value)?{icon:'check'}:{}),
          selected:String(choice)===String(o.value),
          label:String(choice),width:fieldW-22,quiet:true,size:'S',colors:p,
          onClick:act(`lite-choice:${o.id}:${j}`)}),8,j*38+4,fieldW-22,34));
        rows.push(ui.scroll({key:`lite:choice:${o.id}:scroll`,
          style:ui.absolute(fieldX+3,yy+rowH+7,fieldW-6,menuH-8,{overflow:'hidden'}),
          wheelStep:38,smoothScroll:true,scrollbar:true,scrollbarWidth:4,
          scrollbarColor:p.muted,children:[ui.stack({key:`lite:choice:${o.id}:entries`,
            style:ui.style({width:fieldW-7,height:Math.max(menuH,choices.length*38+10)}),children:items})]}));
      }
    } else if(kind==='color'){
      const val=String(o.value||'');
      const hex=/^0x[0-9a-f]{8}$/i.test(val)?'#'+val.slice(2):val;
      rows.push(at(SpectrumActionButton.render({key:`lite:color:button:${o.id}`,icon:'palette',
        label:val,width:fieldW,quiet:false,size:'M',selected:opened,colors:p,
        onClick:act(`lite-option:${o.id}`)}),fieldX,fieldY+1,fieldW,36));
      if(/^#[0-9a-f]{6,8}$/i.test(hex))rows.push(bg(`lite:color:preview:${o.id}`,
        fieldX+fieldW-32,fieldY+7,24,24,hex,p.border,5));
      if(opened){
        const wide=contentW>=720;
        const panelW=wide?Math.min(760,Math.max(560,Math.round(contentW*.58))):Math.max(230,contentW-32);
        const panelX=contentW-panelW-16;
        const topY=yy+rowH+14;
        const hsv=argbToHsv(data.editValue||val);
        const areaSize=wide?(panelW>=700?236:panelW>=600?216:196):Math.min(208,Math.max(120,panelW-124));
        const hueX=panelX+areaSize+12;
        const alphaX=hueX+62;
        rows.push(at(SpectrumColorArea.render({key:`lite:color:area:${o.id}`,
          size:areaSize,...hsv,colors:p,onChange:act(`lite-color-area:${o.id}`)}),
          panelX,topY,areaSize,areaSize));
        rows.push(at(SpectrumColorSlider.render({key:`lite:color:hue:${o.id}`,
          orientation:'vertical',channel:'hue',length:areaSize,...hsv,colors:p,
          onChange:act(`lite-color-hue:${o.id}`)}),hueX,topY,50,areaSize));
        rows.push(at(SpectrumColorSlider.render({key:`lite:color:alpha:${o.id}`,
          orientation:'vertical',channel:'alpha',length:areaSize,...hsv,colors:p,
          onChange:act(`lite-color-alpha:${o.id}`)}),alphaX,topY,50,areaSize));
        const sideX=wide?alphaX+70:panelX;
        const sideW=wide?Math.max(176,panelX+panelW-sideX):panelW;
        const sideY=wide?topY:topY+areaSize+14;
        rows.push(tx(`lite:color:presets:label:${o.id}`,'Presets',sideX,sideY,sideW,23,p.text,16,true));
        const swatchY=sideY+27;
        const swatchSize=40,swatchGap=8;
        const columns=Math.max(1,Math.floor((sideW+swatchGap)/(swatchSize+swatchGap)));
        const swatchRows=Math.ceil(palette.length/columns);
        const swatchContentH=Math.max(1,swatchRows*(swatchSize+swatchGap)-swatchGap);
        const swatchViewportH=wide?82:60;
        const swatches=SpectrumColorSwatchGroup.render({key:`lite:color:swatches:${o.id}`,
          width:sideW,size:'M',density:'regular',rounding:'default',colors:p,
          items:palette.map((color,j)=>({id:String(j),color,
            selected:j===palette.indexOf(hex),onClick:act(`lite-color:${o.id}:${j}`)}))});
        rows.push(ui.scroll({key:`lite:color:presets-scroll:${o.id}`,
          style:ui.absolute(sideX,swatchY,sideW,swatchViewportH,{overflow:'hidden'}),
          wheelStep:48,smoothScroll:true,scrollbar:true,scrollbarWidth:4,scrollbarColor:p.muted,
          children:[{...swatches,class:'',style:ui.style({width:sideW,
            height:Math.max(swatchContentH,swatchViewportH)})}]}));
        const hexY=swatchY+swatchViewportH+7;
        rows.push(tx(`lite:color:hex:label:${o.id}`,'HEX',sideX,hexY,sideW,21,p.muted,14,true));
        const inputY=hexY+21;
        rows.push(inlineField(o.id,data.editValue||val,sideX,inputY,sideW));
        const groupW=Math.min(sideW,264);
        const actionSize=Math.floor(groupW/4);
        rows.push(at(SpectrumActionGroup.render({key:`lite:color:actions:${o.id}`,
          width:groupW,size:'M',density:'compact',quiet:false,colors:p,
          items:[
            {id:'save',icon:'heart-plus',width:actionSize,onClick:act(`lite-color-save:${o.id}`)},
            {id:'remove',icon:'trash-2',width:actionSize,onClick:act(`lite-color-remove:${o.id}`)},
            {id:'apply',icon:'check',width:actionSize,onClick:act('lite-edit-apply')},
            {id:'cancel',icon:'x',width:actionSize,onClick:act('lite-edit-cancel')}
          ]}),sideX,inputY+51,groupW,36));
      }
    } else if(kind==='hotkey') {
      const capturing=!!o.capturing;
      rows.push(at(SpectrumActionButton.render({key:`lite:hotkey:${o.id}`,
        icon:'keyboard',label:capturing?String(data.capturePreview||'Press keys…'):String(o.value||'Unbound'),
        width:fieldW,quiet:false,size:'M',selected:capturing,colors:p,
        onClick:act(`lite-option:${o.id}`)}),fieldX,fieldY+1,fieldW,36));
      if(capturing)rows.push(tx(`lite:hotkey:hint:${o.id}`,
        'Enter = apply  ·  Backspace = clear  ·  Esc = cancel',16,yy+rowH-20,
        contentW-25,18,p.accent,13));
    } else if(kind==='directory') {
      rows.push(at(SpectrumActionButton.render({key:`lite:directory:${o.id}`,
        icon:'folder-open',label:String(o.value||'Choose directory…'),width:fieldW,
        quiet:false,size:'M',colors:p,onClick:act(`lite-option:${o.id}`)}),
        fieldX,fieldY+1,fieldW,36));
    } else if(active){
      rows.push(inlineField(o.id,data.editValue||'',fieldX,fieldY,
        fieldW<245?fieldW:fieldW-82));
      if(fieldW>=245)rows.push(...confirmButtons(o.id,fieldX+fieldW-76,fieldY+4));
    } else {
      const value=String(o.value||'—');
      rows.push(at(SpectrumActionButton.render({key:`lite:value:${o.id}`,
        icon:kind==='number'?'hash':kind==='hotkey'?'keyboard':'pencil',
        label:value,width:fieldW,size:'M',quiet:false,colors:p,
        onClick:act(`lite-option:${o.id}`)}),fieldX,fieldY+1,fieldW,36));
    }
  });
  if(!total)rows.push(tx('lite:empty','No matching settings',17,16,contentW-30,38,p.muted,17));
  const content=ui.stack({key:`lite:rows:${data.category||'generic'}`,
    style:ui.style({width:contentW,height:Math.max(viewH+1,total*rowH+extra+8)}),children:rows});
  nodes.push(ui.scroll({key:`lite:scroll:${data.category||'generic'}`,
    style:ui.absolute(viewX,viewY,viewW,viewH,{overflow:'hidden'}),
    wheelStep:72,smoothScroll:true,scrollSmoothingRate:.021,
    scrollbar:true,scrollbarWidth:7,scrollbarOffset:-9,scrollbarColor:p.muted,children:[content]}));
  if(data.error)nodes.push(txClip('lite:error',data.error,x+24,y+h-42,w-48,26,
    S.withOpacity(p.danger,.7+Math.max(0,N(data.errorPulse))*.3),16,true));
  return nodes;
}

function fileList(d,p,width) {
  const files=A(d.files),rowH=51;
  const children=files.map((f,i)=>{
    const y=i*rowH,selected=!!f.selected;
    const format=formatMark(`files:row:${i}:format`,f.format,0,0,p);
    const metaW=width>=490?Math.min(178,Math.max(95,width*.22)):0;
    const titleX=format.width+22;
    const textW=Math.max(36,width-titleX-metaW-26);
    const parts=[
      bg(`files:row:${i}:surface`,0,0,width,rowH-1,selected?p.selected:'#00000000'),
      ui.shape({key:`files:row:${i}:hover`,shape:'box',style:ui.absolute(0,0,width,rowH-1),
        corners:ui.corner.all(ui.corner.square()),fill:'#00000000',interactive:false,
        stateSource:'parent',fillReactive:{base:p.accent,alphaBase:0,alphaTerms:{hover:.075,press:.14}}}),
      ...(selected?[bg(`files:row:${i}:accent`,0,0,2,rowH-1,p.accent)]:[]),
      {...format.node,style:ui.absolute(9,12,format.width,27)},
      ui.text({key:`files:row:${i}:title`,text:f.title||f.file,color:p.text,interactive:false,
        stateSource:'parent',runtimeMarquee:true,marqueeSpeed:38,marqueeHoldMs:600,marqueeFadeWidth:9,
        verticalAlign:'center',class:ui.abs(titleX,3,textW,rowH-7,'font-SourceSans3Semibold font-size-18 clip')}),
      ...(metaW?[ui.text({key:`files:row:${i}:meta`,text:f.size||f.meta,color:p.muted,
        interactive:false,stateSource:'parent',runtimeMarquee:true,verticalAlign:'center',
        class:ui.abs(width-metaW-12,3,metaW,rowH-7,'font-SourceSans3 font-size-15 text-align-right clip')})]:[]),
      bg(`files:row:${i}:divider`,0,rowH-1,width,1,p.border)
    ];
    return ui.button({key:`files:row:${i}`,class:ui.abs(0,y,width,rowH,'cursor-pointer'),
      onClick:act(`file:${f.id}`),stateValues:{selected},
      stateMotions:{hover:{durationMs:150,easing:'out-cubic'},press:{durationMs:85,easing:'out-cubic'}},
      children:parts});
  });
  return ui.stack({key:'files:list:content',style:ui.style({width,height:Math.max(1,files.length*rowH)}),children});
}
function scrollNode(key,x,y,w,h,content,p,wheelStep=48){
  return ui.scroll({key,style:ui.absolute(x,y,w,h,{overflow:'hidden'}),
    wheelStep,smoothScroll:true,scrollSmoothingRate:.021,
    scrollbar:true,scrollbarWidth:8,scrollbarRadius:4,scrollbarOffset:-10,
    scrollbarMinThumb:35,scrollbarColor:p.muted,
    scrollbarBaseAlpha:.58,scrollbarHoverAlpha:.26,scrollbarDragAlpha:.16,
    scrollbarVisibilityMs:2100,children:[content]});
}
function files(d,p,r,search) {
  const w=r.w,h=r.h,detailW=w>=750?Math.min(335,Math.max(274,w*.30)):0;
  const detailGap=detailW?22:0,bodyLeft=20,bodyRight=18;
  const listW=Math.max(200,w-bodyLeft-bodyRight-detailW-detailGap);
  const detailX=bodyLeft+listW+detailGap;
  const sx=search.x-r.x,sy=search.y-r.y;
  const listY=Math.max(138,sy+61),listH=Math.max(48,h-listY-(detailW||!d.selected?13:77));
  const files=A(d.files),chosen=files.filter(f=>f.selected).map(f=>String(f.id));
  const nodes=[bg('files:background',0,0,w,h,S.withOpacity(p.background,.70)),
    tx('files:heading',d.labels?.files||'Files',20,13,Math.max(100,listW-240),38,p.text,25,true),
    tx('files:count',`${files.length} / ${d.fileCount}`,22,48,160,22,p.muted,13),
    actionButton('files:list-mode','list',bodyLeft+listW-158,18,p,
      {selected:!d.cardsMode,action:'list-mode'}),
    actionButton('files:cards-mode','layout-grid',bodyLeft+listW-119,18,p,
      {selected:!!d.cardsMode,action:'cards-mode'}),
    actionButton('files:open-folder','folder-open',bodyLeft+listW-80,18,p,{action:'folder'}),
    actionButton('files:rescan','folder-sync',bodyLeft+listW-41,18,p,{action:'refresh'}),
    at(SpectrumSearchField.render({key:'files:search',value:d.search,
      labelHidden:true,placeholder:d.labels?.search||'Search',
      focused:d.searchFocused,scrollOffset:d.searchScroll,caretOffset:d.caretOffset,
      selectionStart:d.selectionStart,selectionEnd:d.selectionEnd,colors:p,width:search.w,
      onClick:act('search'),onClear:act('search-clear')}),sx,sy,search.w,44),
    rule('files:body:rule',bodyLeft,listY-10,listW,p)];
  if(!files.length){
    nodes.push(ui.svg({key:'files:empty:icon',asset:'folder-open',tint:p.muted,interactive:false,
      class:ui.abs(bodyLeft+listW/2-22,listY+Math.max(35,listH*.22),44,44)}));
    nodes.push(tx('files:empty:title',d.search?(d.labels?.noMatches||'No matches'):
      (d.labels?.empty||'No schematics'),bodyLeft,listY+Math.max(90,listH*.22+50),listW,32,p.text,17,true,'center'));
    nodes.push(tx('files:empty:help',d.error||(d.labels?.emptyHint||'Open the folder to add a file'),
      bodyLeft+10,listY+Math.max(127,listH*.22+85),listW-20,32,d.error?p.danger:p.muted,13,false,'center'));
  }else{
    const viewportWidth=Math.max(155,listW-20);
    const cols=Math.max(1,Math.floor((viewportWidth+16)/((listW>=600?186:155)+16)));
    const actualCols=Math.min(Math.max(1,files.length),cols);
    const contentHeight=d.cardsMode?Math.ceil(files.length/actualCols)*187:files.length*51;
    const content=d.cardsMode
      ?SpectrumCardView.render({key:'files:cards',width:viewportWidth,selectionMode:'single',
        selectionStyle:'highlight',marqueeOnHover:true,selectedIds:chosen,colors:p,size:listW>=600?'M':'S',
        items:files.map(f=>({id:String(f.id),label:f.title,description:f.failed?f.meta:(f.size||f.meta),
          format:f.format,formatColor:formatColor(f.format),color:f.failed?'#FF503030':p.raised,
          icon:'layers',previewText:f.size,onClick:act(`file:${f.id}`)}))})
      :fileList(d,p,viewportWidth);
    nodes.push(scrollNode(`files:viewport:${d.cardsMode?'cards':'list'}`,
      bodyLeft,listY,listW,listH,
      ui.stack({key:`files:scroll-content:${d.cardsMode?'cards':'list'}`,
        style:ui.style({width:viewportWidth,height:Math.max(listH+1,contentHeight)}),
        children:[content]}),p,d.cardsMode?75:56));
  }
  if(detailW){
    const details=d.fileDetails||{};
    const side=[bg('files:details:surface',0,0,detailW,h-29,p.surface,p.border,12),
      tx('files:details:title',d.selected?'Details':'Select an asset',20,17,detailW-40,30,p.text,19,true),
      rule('files:details:line',20,59,detailW-40,p)];
    if(d.selected){
      side.push(txClip('files:details:name',details.name||d.selectedName,20,74,detailW-40,58,p.text,18,true));
      let y=151;
      for(const [id,label,value] of [
        ['dimensions','Dimensions',details.size],['blocks','Blocks',details.blocks],
        ['regions','Regions',details.regions],['author','Author',details.author||'—'],
        ['filename','Filename',details.file]]){
        side.push(tx(`files:details:${id}:label`,label,20,y,detailW-40,19,p.muted,12));
        side.push(txClip(`files:details:${id}:value`,String(value??'—'),20,y+19,detailW-40,27,p.text,14,true));
        y+=57;
      }
      if(details.error)side.push(tx('files:details:failure',details.error,20,y,detailW-40,40,p.danger,13));
      side.push(button('files:details:load',d.labels?.load||'Load',17,h-84,detailW-34,p,
        {primary:true,disabled:!d.canLoad||d.loading,action:'load'}));
    }else{
      side.push(ui.svg({key:'files:details:icon',asset:'layers',tint:p.muted,interactive:false,
        class:ui.abs(detailW/2-22,115,44,44)}));
      side.push(tx('files:details:hint','Choose a file to view its metadata',17,169,detailW-34,72,
        p.muted,14,false,'center'));
    }
    nodes.push(group('files:details',{x:detailX,y:15,w:detailW,h:h-15},side));
  }else if(d.selected){
    nodes.push(bg('files:selection:bg',bodyLeft,h-62,listW,51,p.surface,p.border,9));
    nodes.push(txClip('files:selection:name',d.selectedName,bodyLeft+13,h-53,Math.max(1,listW-154),30,p.text,14,true));
    nodes.push(button('files:selection:load',d.labels?.load||'Load',bodyLeft+listW-133,h-53,119,p,
      {primary:true,disabled:!d.canLoad||d.loading,action:'load'}));
  }
  return group('files:page',r,nodes);
}

function stage(d,p,r,canvas,strip,gap) {
  const w=r.w,h=r.h,loaded=!!d.loaded;
  const nodes=[...(loaded?[]:[bg('stage:pasteboard',0,0,w,h,'#E8171717')]),
    bg('stage:toolbar:background',0,0,w,53,p.surface),
    rule('stage:toolbar:divider',0,52,w,p)];
  const maxTabs=Math.max(100,Math.min(210,w*.30));
  nodes.push(seg('stage:view',[['3d','3D','view-3d',!loaded],['2d','2D','view-2d',!loaded]],
    d.view,13,8,maxTabs,p));
  let x= maxTabs+27;
  if(w>=360){
    nodes.push(actionButton('stage:camera','crosshair',x,8,p,{disabled:!loaded,action:'reset-camera'})); x+=38;
    nodes.push(actionButton('stage:turntable','rotate-ccw',x,8,p,{selected:d.autoRotate,disabled:!loaded,action:'auto-rotate'})); x+=38;
  }
  if(w>=640){
    nodes.push(actionButton('stage:free-fly','navigation',x,8,p,
      {selected:!!d.freeFly,disabled:!loaded||d.view!=='3d',action:'free-fly'})); x+=38;
  }
  if(w>=535){
    nodes.push(textButton('stage:render-mode',d.viewModeLabel||'View',x+3,8,Math.max(55,Math.min(148,w-x-45)),p,
      {disabled:!loaded||d.view!=='3d',action:'view-mode'}));
  }

  if(loaded && d.freeFly && d.view==='3d'){
    nodes.push(tx('stage:free-fly:help',`Fly: WASD  |  Space / Shift  |  Drag to look  |  Wheel: ${d.flySpeed} blocks/s`,
      15,59,Math.max(100,w-30),24,p.muted,12,false,'left'));
  }
  if(loaded && !d.previewAvailable){
    nodes.push(tx('stage:native:notice','Placement is rendered in the world by Litematica',
      20,Math.max(80,h/2-20),Math.max(1,w-40),42,p.muted,16,false,'center'));
  }
  if(!loaded){
    nodes.push(ui.svg({key:'stage:empty:icon',asset:d.loading?'folder-sync':'layout-grid',tint:p.muted,
      interactive:false,class:ui.abs(Math.max(1,w/2-23),Math.max(72,h/2-62),46,46)}));
    nodes.push(tx('stage:empty:title',d.loaded?'Rendered by Litematica in the world':d.loading?'Opening…':d.error||d.labels?.pick||'Choose a file',
      15,Math.max(124,h/2+4),w-30,35,d.error?p.danger:p.muted,17,true,'center'));
    nodes.push(button('stage:empty:browse',d.labels?.files||'Files',Math.max(8,w/2-63),
      Math.max(168,h/2+46),126,p,{action:'library'}));
  }
  // Contextual timeline. No permanently embedded properties/materials columns.
  if(loaded){
    const dockH=68,dockY=h-dockH;
    nodes.push(bg('stage:dock:bg',0,dockY,w,dockH,p.surface));
    nodes.push(rule('stage:dock:top',0,dockY,w,p));
    nodes.push(actionButton('stage:dock:back','chevron-left',12,dockY+11,p,{action:'layer-prev'}));
    nodes.push(textButton('stage:dock:play',d.playing?'Ⅱ':'▶',51,dockY+11,34,p,
      {selected:d.playing,action:'play'}));
    nodes.push(actionButton('stage:dock:forward','chevron-right',90,dockY+11,p,{action:'layer-next'}));
    const lx=strip.x-r.x,ly=strip.y-r.y,sw=strip.w;
    nodes.push(bg('stage:dock:timeline:track',lx,ly+11,sw,5,p.field,undefined,3));
    const progress=Math.max(0,Math.min(1,(N(d.layer)-1)/Math.max(1,N(d.layerCount)-1)));
    nodes.push(bg('stage:dock:timeline:fill',lx,ly+11,Math.max(1,sw*progress),5,p.accent,undefined,3));
    const count=Math.max(1,A(d.layers).length);
    A(d.layers).forEach((layer,i)=>{
      const tickX=lx+sw*(N(layer.index,i)/Math.max(1,N(d.layerCount)-1));
      if(i%Math.max(1,Math.ceil(count/60))===0)nodes.push(bg(`stage:dock:timeline:tick:${i}`,
        tickX,ly+4,1,18,layer.active?p.accent:p.border));
    });
    nodes.push(bg('stage:dock:timeline:thumb',lx+sw*progress-4,ly+7,9,13,p.text,undefined,4));
    nodes.push(tx('stage:dock:frame',`${d.layer} / ${d.layerCount}`,
      lx,ly+22,sw,20,p.muted,12,false,'center'));
    if(w>=470){
      nodes.push(textButton('stage:dock:speed',`${d.speed}×`,w-101,dockY+11,79,p,{action:'speed'}));
    }
    if(w>=600){
      nodes.push(bg('stage:dock:progress:track',lx, dockY+6,sw,2,p.raised));
      nodes.push(bg('stage:dock:progress:fill',lx,dockY+6,Math.max(1,sw*Math.min(1,N(d.progress))),2,p.accent));
      nodes.push(tx('stage:dock:progress:label',`${Math.round(N(d.progress)*100)}%`,
        Math.max(0,w-159),dockY+10,48,30,p.muted,13,true,'right'));
    }
    if(d.exploded&&d.view==='3d'&&w>=810){
      const gx=gap.x-r.x,gy=gap.y-r.y;
      nodes.push(tx('stage:gap:text',`Gap ${N(d.gap).toFixed(1)}`,
        gx,gy-3,gap.w,17,p.muted,12));
      nodes.push(bg('stage:gap:track',gx,gy+20,gap.w,4,p.border,undefined,2));
      nodes.push(bg('stage:gap:fill',gx,gy+20,Math.max(1,gap.w*Math.min(1,N(d.gap)/4)),4,p.accent,undefined,2));
    }
  }
  return group('stage',r,nodes);
}
function coordinateControl(d,p,axis,x,y,w){
  const active=d.coordActive===axis;
  const value=active?String(d.coordText||''):String(d.origin?.[axis]??0);
  const labelW=53,fieldW=Math.max(160,w-labelW);
  return group(`origin:${axis}`,{x,y,w,h:42},[
    tx(`origin:${axis}:label`,axis.toUpperCase(),0,0,38,40,p.muted,17,true),
    at(SpectrumNumberField.render({
      key:`origin:${axis}:number`,labelHidden:true,width:fieldW,size:'M',
      colors:p,value,focused:active,
      scrollOffset:active?N(d.coordScroll):0,
      caretOffset:active?N(d.coordCaret)+N(d.coordScroll):0,
      selectionStart:active?N(d.coordSelectionStart)+N(d.coordScroll):0,
      selectionEnd:active?N(d.coordSelectionEnd)+N(d.coordScroll):0,
      onFocus:act(`origin-edit:${axis}`),
      onIncrement:act(`origin-step:${axis}:up`),
      onDecrement:act(`origin-step:${axis}:down`)
    }),labelW,0,fieldW,40)
  ]);
}
function propertyDrawer(d,p,r) {
  const w=r.w,h=r.h;
  const children=[bg('prop:surface',0,0,w,h,p.surface),bg('prop:left-border',0,0,1,h,p.border),
    tx('prop:title','Properties',18,7,w-68,42,p.text,22,true),
    actionButton('prop:close','x',w-47,12,p,{action:'drawer-close'}),
    rule('prop:header:line',0,52,w,p),
    tx('prop:origin:title','Origin',17,62,w-34,25,p.text,19,true),
    coordinateControl(d,p,'x',17,95,w-34),
    coordinateControl(d,p,'y',17,140,w-34),
    coordinateControl(d,p,'z',17,185,w-34),
    rule('prop:origin:line',0,233,w,p)];
  let y=11;
  const rows=[];
  const caption=(id,label)=>{rows.push(tx(`prop:section:${id}`,label,17,y,w-34,28,p.text,19,true));y+=39;};
  const pair=(id,label,value)=>{
    rows.push(tx(`prop:${id}:key`,label,17,y,w*.43,29,p.muted,17));
    rows.push(txClip(`prop:${id}:val`,value,w*.48,y,w*.45,29,p.text,17,true,'right'));
    y+=34;
  };
  const buttons=(id,options)=>{
    const n=options.length,ww=(w-34-8*(n-1))/n;
    options.forEach(([label,action,disabled,selected],i)=>rows.push(textButton(`prop:${id}:${i}`,label,
      17+i*(ww+8),y,ww,p,{action,disabled,selected,quiet:false})));
    y+=45;
  };
  caption('placement','Placement');
  buttons('origin',[['Here','origin-here',!d.loaded],['Target','origin-look',!d.loaded]]);
  buttons('transform',[['Rotate','rotate',!d.placed],['Mirror','mirror',!d.placed]]);
  pair('mode','Layer mode',String(d.layerMode||'').replaceAll('_',' ').toLowerCase());
  buttons('layer-mode',[['Change mode','layer-mode',!d.loaded]]);
  if(d.exploded){pair('gap','Layer gap',N(d.gap).toFixed(2));
    buttons('gap-controls',[['−','gap-decrease',!d.loaded],['+','gap-increase',!d.loaded]]);}
  y+=8;rows.push(rule('prop:render',0,y,w,p));y+=15;
  caption('rendering','3D rendering');
  pair('fps-target','Adaptive threshold',`${Math.round(N(d.renderFpsTarget,40))} FPS`);
  buttons('fps-target',[['−','render-fps-down',!d.loaded],['+','render-fps-up',!d.loaded]]);
  pair('draw-distance','Draw distance',`x${N(d.renderDistanceScale,1).toFixed(2)}`);
  buttons('draw-distance',[['−','render-distance-down',!d.loaded],['+','render-distance-up',!d.loaded]]);
  y+=8;rows.push(rule('prop:sep:1',0,y,w,p));y+=15;
  caption('automation','Automation');
  buttons('builder',[[(d.building?(d.labels?.builderStop||'Stop'):(d.labels?.builderStart||'Build')),
    'builder',!d.placed&&!d.building,d.building],['Settings','builder-settings',false]]);
  buttons('projection',[[d.projecting?'Hide in world':'Show in world',
    'projection',false,d.projecting]]);
  if(d.builderStatus){rows.push(tx('prop:builder:status',d.builderStatus,17,y,w-34,28,p.muted,14));y+=32;}
  y+=8;rows.push(rule('prop:sep:2',0,y,w,p));y+=15;
  caption('metadata','Document');
  A(d.info).filter(item=>!String(item.label||'').toLowerCase().includes('origin')).forEach((row,i)=>pair(`metadata:${i}`,row.label,row.value));
  y+=12;buttons('unload',[[d.labels?.unload||'Unload','unload',!d.loaded]]);
  children.push(scrollNode('prop:scroll',0,234,w,Math.max(32,h-234),
    ui.stack({key:'prop:content',style:ui.style({width:w-12,height:Math.max(y+8,h-233)}),children:rows}),p,54));
  return group('prop:drawer',r,children);
}
function materialDrawer(d,p,r){
  const w=r.w,h=r.h,children=[bg('materials:surface',0,0,w,h,p.surface),
    bg('materials:border',0,0,1,h,p.border),
    tx('materials:title',d.labels?.materials||'Materials',18,7,w-68,42,p.text,22,true),
    actionButton('materials:close','x',w-47,13,p,{action:'drawer-close'}),
    rule('materials:head-line',0,52,w,p),
    seg('materials:views',[['layer','Layer','material-layer'],['total','Total','material-total'],
      ['issues',`Issues ${d.issueCount||0}`,'material-issues']],d.materialView,14,63,w-28,p)];
  const items=[];
  if(!A(d.inventory).length){
    items.push(tx('materials:empty',d.loaded?(d.labels?.materialsNone||'No entries'):'No file',
      16,18,w-32,31,p.muted,14));
  }else A(d.inventory).forEach((item,i)=>{
    const y=i*66;
    items.push(ui.item({key:`materials:item:${i}:icon`,item:item.item,
      overlay:false,class:ui.abs(16,y+13,29,29)}));
    items.push(txClip(`materials:item:${i}:name`,item.name,53,y+6,Math.max(40,w-172),28,p.text,16,true));
    items.push(tx(`materials:item:${i}:qty`,item.quantity,w-116,y+6,97,25,
      item.warning?p.danger:p.muted,15,true,'right'));
    if(item.detail)items.push(tx(`materials:item:${i}:detail`,item.detail,53,y+31,Math.max(40,w-72),21,
      item.warning?p.danger:p.muted,14));
    else{
      items.push(bg(`materials:item:${i}:track`,53,y+43,Math.max(1,w-69),3,p.field,undefined,2));
      items.push(bg(`materials:item:${i}:fill`,53,y+43,Math.max(1,(w-69)*Math.min(1,N(item.ratio))),3,
        p.accent,undefined,2));
    }
    items.push(rule(`materials:item:${i}:separator`,15,y+63,w-30,p));
  });
  children.push(scrollNode('materials:scroll',0,105,w,Math.max(30,h-105),
    ui.stack({key:'materials:rows',style:ui.style({width:w-12,height:Math.max(h-104,A(d.inventory).length*66+20)}),children:items}),p,49));
  return group('materials:drawer',r,children);
}
function layersDrawer(d,p,r) {
  const w=r.w,h=r.h;
  const children=[bg('layers:surface',0,0,w,h,p.surface),bg('layers:border',0,0,1,h,p.border),
    tx('layers:title','Layers',18,7,w-70,42,p.text,22,true),
    actionButton('layers:close','x',w-47,13,p,{action:'drawer-close'}),
    rule('layers:heading:line',0,52,w,p),
    tx('layers:current',`Layer ${d.layer} of ${d.layerCount}`,18,65,w-36,30,p.text,16,true),
    seg('layers:range',[['one','−10','layer-prev10'],['prev','−1','layer-prev'],
      ['next','+1','layer-next'],['ten','+10','layer-next10']], 'none',15,103,w-30,p),
    tx('layers:display','Build progress',18,154,w-36,22,p.muted,13)];
  children.push(at(SpectrumProgressBar.render({key:'layers:progress',width:w-36,
    value:N(d.progress)*100,min:0,max:100,label:'',valueLabel:`${Math.round(N(d.progress)*100)}%`,size:'S',colors:p}),
    18,181,w-36,54));
  children.push(tx('layers:hint','Use the timeline to scrub through layers.',18,250,w-36,44,p.muted,13));
  return group('layers:drawer',r,children);
}
function localToasts(d,p,w,h){
  const nodes=[];
  A(d.toasts).forEach((toast,i)=>{
    const width=Math.min(430,Math.max(240,w-32));
    const y=h-100-N(toast.stack,(A(d.toasts).length-1-i)*73);
    const slide=(1-Math.max(0,Math.min(1,N(toast.alpha,1))))*22;
    const label=toast.count>1?`${toast.message}  ×${toast.count}`:toast.message;
    const rendered=SpectrumToast.render({key:`schematic:toast:${toast.id}`,colors:p,width,
      message:label,variant:toast.variant,onClose:act(`toast-close:${toast.id}`)});
    nodes.push({...at(rendered,w-width-24+slide,y,width,64),
      style:ui.absolute(w-width-24+slide,y,width,64,
        {opacity:Math.max(0,Math.min(1,N(toast.alpha,1)))})});
  });
  return nodes;
}

export function buildTemplate(ctx) {
  const d=ctx.props||{},w=Math.max(1,N(d.width,900)),h=Math.max(1,N(d.height,600));
  const p=S.colors(d.colors||{}),l=d.layout||{},head=N(l.header,52),rail=N(l.rail,54);
  const view=R(l.view),library=R(l.library),search=R(l.search),canvas=R(l.canvas),
    strip=R(l.strip),gap=R(l.gap),drawer=R(l.inspector);
  const nodes=[...(d.libraryMode?[bg('app:background',0,0,w,h,S.withOpacity(p.background,.68))]:[]),
    ...header(d,p,w,head,rail),
    ...toolRail(d,p,h,head,rail)];
  if(d.liteSettingsMode){
    nodes.push(...litematicaSettings(d,p,rail,head,Math.max(1,w-rail),Math.max(1,h-head)));
  } else if(d.libraryMode) nodes.push(files(d,p,library,search));
  else {
    nodes.push(stage(d,p,view,canvas,strip,gap));
    const enter=Math.max(0,Math.min(1,N(d.drawerProgress)));
    const drawerRect={...drawer};
    if(enter>0.006){
      let pane;
      if(d.drawerMode==='properties')pane=propertyDrawer(d,p,drawerRect);
      else if(d.drawerMode==='materials')pane=materialDrawer(d,p,drawerRect);
      else if(d.drawerMode==='layers')pane=layersDrawer(d,p,drawerRect);
      if(pane){
        const finalX=w-drawerRect.w;
        nodes.push(ui.stack({key:'drawer:reveal',interactive:false,
          style:ui.absolute(finalX,drawerRect.y,drawerRect.w,drawerRect.h,{overflow:'hidden'}),
          children:[{...pane,style:ui.absolute(0,0,drawerRect.w,drawerRect.h)}]}));
      }
    }
  }
  nodes.push(...localToasts(d,p,w,h));
  return ui.stack({key:'schematics:app',style:ui.absolute(0,0,w,h,{overflow:'hidden'}),children:nodes});
}
