import { SpectrumFoundation } from './foundation.js';
import { SpectrumMenu } from './menu.js';
import { SpectrumDialog } from './dialog.js';
import { SpectrumBreadcrumbs } from './breadcrumbs.js';
import { SpectrumToast } from './toast.js';
import { SpectrumTooltip } from './tooltip.js';
import { SpectrumCalendar } from './calendar.js';
import { SpectrumContextualHelp } from './contextual-help.js';
import { SpectrumPopover } from './popover.js';
import { SpectrumStandardDialog } from './standard-dialog.js';
/* Spectrum 2 dev-only picker popover; independent of the Google kit. */
const s2PopupAt=(node,x,y,w,h)=>({...node,style:ui.absolute(x,y,w,h)});
function renderSpectrumModalOrPopup(data,viewport) {
  if(data.overlay==='adobe-popover') {
    const vw=viewport.vw,vh=viewport.vh,margin=12,w=Math.min(320,vw-2*margin),h=160;
    const ax=ui.num(data.anchorX,24),ay=ui.num(data.anchorY,24);
    const aw=ui.num(data.anchorWidth,116),ah=ui.num(data.anchorHeight,40),offset=8;
    let placement=ui.str(data.adobePopoverPlacement,'bottom');
    const available={bottom:vh-(ay+ah)-margin-offset,top:ay-margin-offset,
      left:ax-margin-offset,right:vw-(ax+aw)-margin-offset};
    if((placement==='bottom'||placement==='top')&&available[placement]<h){
      const flipped=placement==='bottom'?'top':'bottom';
      if(available[flipped]>available[placement])placement=flipped;
    }
    if((placement==='left'||placement==='right')&&available[placement]<w){
      const flipped=placement==='left'?'right':'left';
      if(available[flipped]>available[placement])placement=flipped;
    }
    const tx=(placement==='left'?ax-w-offset:placement==='right'?ax+aw+offset:ax+aw/2-w/2);
    const ty=(placement==='top'?ay-h-offset:placement==='bottom'?ay+ah+offset:ay+ah/2-h/2);
    const x=Math.max(margin,Math.min(vw-w-margin,tx));
    const y=Math.max(margin,Math.min(vh-h-margin,ty));
    return ui.stack({key:'s2:overlay:root',class:ui.abs(0,0,vw,vh),children:[
      ui.button({key:'s2:popover:outside',class:ui.abs(0,0,vw,vh),
        onClick:'lab:adobe:popover:close',children:[]}),
      s2PopupAt(SpectrumPopover.render({key:'s2:popover:popup',width:w,height:h,
        title:'File is too large',description:'Reduce the file size to less than 10 MB and try again.'}),
        x,y,w,h)
    ]});
  }
  if(data.overlay==='adobe-standard-dialog') {
    const vw=viewport.vw,vh=viewport.vh,margin=18;
    const size=ui.str(data.adobeStandardDialogSize,'M');
    const desired={S:392,M:512,L:720}[size]||512;
    const w=Math.min(desired,vw-margin*2),h=296;
    return ui.stack({key:'s2:overlay:root',class:ui.abs(0,0,vw,vh),children:[
      SpectrumFoundation.rect('s2:standard-dialog:shade',0,0,vw,vh,0,'#9A000000'),
      ui.button({key:'s2:standard-dialog:block',class:ui.abs(0,0,vw,vh),children:[]}),
      s2PopupAt(SpectrumStandardDialog.render({key:'s2:standard-dialog',width:w,height:h,
        title:'Project settings',description:'Change how this document is stored and shared.',
        contentTitle:'Cloud sync',content:'Settings will apply to all collaborators.',
        onCancel:'lab:adobe:standard-dialog:cancel',
        onConfirm:'lab:adobe:standard-dialog:confirm'}),(vw-w)/2,(vh-h)/2,w,h)
    ]});
  }
  if(data.overlay==='adobe-context-help-help'||data.overlay==='adobe-context-help-info') {
    const vw=viewport.vw,vh=viewport.vh;
    const informative=data.overlay==='adobe-context-help-info';
    const width=320,height=informative?128:158,margin=12;
    const ax=ui.num(data.anchorX,24),ay=ui.num(data.anchorY,24);
    const ah=ui.num(data.anchorHeight,26);
    const x=Math.max(margin,Math.min(vw-width-margin,ax));
    const below=ay+ah+7;
    const y=Math.max(margin,Math.min(vh-height-margin,
      below+height<=vh-margin?below:ay-height-7));
    return ui.stack({key:'s2:overlay:root',class:ui.abs(0,0,vw,vh),children:[
      ui.button({key:'s2:help:outside',class:ui.abs(0,0,vw,vh),
        onClick:'lab:adobe:context-help:close',children:[]}),
      s2PopupAt(SpectrumContextualHelp.popover({key:'s2:help:popover',width,
        title:informative?'Sharing permissions':'Export settings',
        content:informative?'People with access to a project can view its shared files. Editing requires a separate permission.':
          'Export presets control file size and quality. Select a format that supports your target workflow.',
        footer:informative?'':'More about export options'}),x,y,width,height)
    ]});
  }
  if(data.overlay==='adobe-combo') {
    const p=SpectrumFoundation.dark,vw=viewport.vw,vh=viewport.vh;
    const labels={photoshop:'Photoshop document',illustrator:'Illustrator artwork',
      acrobat:'Acrobat PDF',premiere:'Premiere project',
      'after-effects':'After Effects composition',indesign:'InDesign document',
      lightroom:'Lightroom catalog'};
    const matches=Array.isArray(data.adobeComboMatches)?data.adobeComboMatches:[];
    const w=Math.max(220,Math.min(370,ui.num(data.anchorWidth,340)));
    const rowH=42,h=Math.max(56,Math.min(350,16+Math.max(1,matches.length)*rowH));
    const ax=ui.num(data.anchorX,24),ay=ui.num(data.anchorY,24),ah=ui.num(data.anchorHeight,75);
    const x=Math.max(12,Math.min(vw-w-12,ax));
    const below=ay+ah+5;
    const y=Math.max(12,Math.min(vh-h-12,below+h<=vh-12?below:ay-h-5));
    const rows=matches.length?matches.map((id,i)=>ui.button({
      key:`s2:combo:option:${id}`,class:ui.abs(8,8+i*rowH,w-16,rowH,'cursor-pointer'),
      onClick:`lab:adobe:combo:choose:${id}`,stateValues:{selected:ui.num(data.overlayIndex,0)===i},
      stateMotions:{hover:{durationMs:110,easing:'out-cubic'},press:{durationMs:90,easing:'out-cubic'}},
      children:[
        SpectrumFoundation.rect(`s2:combo:option:${id}:selected`,0,1,w-16,rowH-2,5,
          ui.num(data.overlayIndex,0)===i?p.selected:'#00000000'),
        SpectrumFoundation.rect(`s2:combo:option:${id}:hover`,0,1,w-16,rowH-2,5,'#00000000',undefined,0,
          {stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,alphaTerms:{hover:.07,press:.11}}}),
        SpectrumFoundation.text(`s2:combo:option:${id}:label`,labels[id]||id,13,0,w-40,rowH,{size:16})
      ]})): [SpectrumFoundation.text('s2:combo:empty','No matches',16,8,w-40,rowH,
        {size:16,color:p.muted})];
    // Preserve pointer access to the editable field while suggestions are open.
    // The backdrop is four hit regions, not a full-screen button obscuring the caret.
    const holeX=Math.max(0,Math.min(vw,ax)),holeY=Math.max(0,Math.min(vh,ay));
    const holeW=Math.max(0,Math.min(vw-holeX,ui.num(data.anchorWidth,340)));
    const holeH=Math.max(0,Math.min(vh-holeY,ah));
    const outside=(part,bx,by,bw,bh)=>ui.button({key:`s2:combo:outside:${part}`,
      class:ui.abs(bx,by,Math.max(0,bw),Math.max(0,bh)),
      onClick:'lab:adobe:combo:close',children:[]});
    return ui.stack({key:'s2:overlay:root',class:ui.abs(0,0,vw,vh),children:[
      outside('top',0,0,vw,holeY),
      outside('left',0,holeY,holeX,holeH),
      outside('right',holeX+holeW,holeY,vw-holeX-holeW,holeH),
      outside('bottom',0,holeY+holeH,vw,vh-holeY-holeH),
      s2PopupAt(ui.stack({key:'s2:combo:popover',class:ui.abs(0,0,w,h),children:[
        SpectrumFoundation.rect('s2:combo:popover:bg',0,0,w,h,10,p.raised,p.border,1),
        ...rows
      ]}),x,y,w,h)
    ]});
  }
  if(data.overlay==='adobe-date-single'||data.overlay==='adobe-date-range') {
    const p=SpectrumFoundation.dark,vw=viewport.vw,vh=viewport.vh,w=348,h=344;
    const range=data.overlay==='adobe-date-range';
    const ax=ui.num(data.anchorX,24),ay=ui.num(data.anchorY,24),ah=ui.num(data.anchorHeight,75);
    const x=Math.max(12,Math.min(vw-w-12,ax));
    const below=ay+ah+5;
    const y=Math.max(12,Math.min(vh-h-12,below+h<=vh-12?below:ay-h-5));
    return ui.stack({key:'s2:overlay:root',class:ui.abs(0,0,vw,vh),children:[
      ui.button({key:'s2:date:outside',class:ui.abs(0,0,vw,vh),
        onClick:'lab:adobe:date:close',children:[]}),
      s2PopupAt(ui.stack({key:'s2:date:popover',class:ui.abs(0,0,w,h),children:[
        SpectrumFoundation.rect('s2:date:popover:bg',0,0,w,h,10,p.raised,p.border,1),
        {...SpectrumCalendar.render({key:'s2:date:calendar',width:w-10,
          year:ui.num(data.adobeCalendarYear,2026),month:ui.num(data.adobeCalendarMonth,10),
          selected:range?'':ui.str(data.adobeDateSingle,''),
          rangeStart:range?ui.str(data.adobeDateRangeDraftStart || data.adobeDateRangeStart,''):'',
          rangeEnd:range&&!data.adobeDateRangeDraftStart?ui.str(data.adobeDateRangeEnd,''):'',
          focusDay:ui.num(data.adobeCalendarFocusDay,0),
          minValue:'2026-01-01',maxValue:'2027-12-31',
          onPrevious:'lab:adobe:calendar:previous',onNext:'lab:adobe:calendar:next',
          dayAction:'lab:adobe:calendar:day:'}),style:ui.absolute(5,5,w-10,314)},
        SpectrumFoundation.text('s2:date:popover:hint',range?'Select start and end dates':'Choose a date',
          16,310,w-32,24,{size:14,color:p.muted})
      ]}),x,y,w,h)
    ]});
  }

  if(data.overlay==='adobe-breadcrumbs') {
    const path=[{id:'home',label:'Creative Cloud'}, {id:'projects',label:'Projects'},
      {id:'assets',label:'Brand assets'}, {id:'campaign',label:'Campaign'},
      {id:'images',label:'Hero images'}];
    const current=Math.max(0,Math.min(4,ui.num(data.adobeBreadcrumbCurrent,4)));
    const w=Math.min(570,viewport.cw-52);
    const hidden=SpectrumBreadcrumbs.layout(path.slice(0,current+1),w,16,true).hidden;
    if(!hidden.length)return null;
    const palette=SpectrumFoundation.dark;
    const vw=viewport.vw,vh=viewport.vh,margin=12;
    const popW=228,rowH=42,popH=hidden.length*rowH+16;
    const x=Math.max(margin,Math.min(vw-margin-popW,ui.num(data.anchorX,40)));
    const below=ui.num(data.anchorY,40)+ui.num(data.anchorHeight,36)+4;
    const above=ui.num(data.anchorY,40)-popH-4;
    const y=Math.max(margin,Math.min(vh-margin-popH,below+popH<=vh-margin?below:above));
    const rows=hidden.map((item,i)=>ui.button({key:`s2:breadcrumbs:option:${item.id}`,
      onClick:`lab:adobe:breadcrumbs:choose:${item.id}`,
      class:ui.abs(8,8+i*rowH,popW-16,rowH,'cursor-pointer'),
      stateValues:{selected:ui.num(data.overlayIndex,0)===i},
      stateMotions:{hover:{durationMs:110,easing:'out-cubic'},selected:{durationMs:110,easing:'out-cubic'}},
      children:[
        SpectrumFoundation.rect(`s2:breadcrumbs:option:${item.id}:state`,0,1,popW-16,rowH-2,6,
          '#00000000',undefined,0,{stateSource:'parent',fillReactive:{base:palette.text,
            alphaBase:0,alphaTerms:{selected:.12,hover:.08,press:.16}}}),
        SpectrumFoundation.text(`s2:breadcrumbs:option:${item.id}:text`,item.label,13,0,popW-42,rowH,
          {size:17,color:palette.text})
      ]}));
    return ui.stack({key:'s2:overlay:root',class:ui.abs(0,0,vw,vh),children:[
      ui.button({key:'s2:breadcrumbs:outside',class:ui.abs(0,0,vw,vh),
        onClick:'lab:adobe:breadcrumbs:close',children:[]}),
      s2PopupAt(ui.stack({key:'s2:breadcrumbs:popup',class:ui.abs(0,0,popW,popH),children:[
        SpectrumFoundation.rect('s2:breadcrumbs:popup:bg',0,0,popW,popH,10,
          palette.raised,palette.border,1),...rows
      ]}),x,y,popW,popH)
    ]});
  }

  if(data.overlay==='adobe-menu') {
    const vw=viewport.vw,vh=viewport.vh,margin=12,width=276;
    const items=[
      {id:'section',type:'header',label:'Document'},
      {id:'rename',label:'Rename',shortcut:'F2',onClick:'lab:adobe:menu:choose:rename'},
      {id:'duplicate',label:'Duplicate',shortcut:'Ctrl+D',onClick:'lab:adobe:menu:choose:duplicate'},
      {id:'separator',type:'separator'},
      {id:'share',label:'Share',onClick:'lab:adobe:menu:choose:share'},
      {id:'export',label:'Export (unavailable)',disabled:true}
    ];
    const menu=SpectrumMenu.render({key:'s2:menu',items,width,focusIndex:ui.num(data.overlayIndex,1)});
    const height=16+items.reduce((sum,item)=>sum+(item.type==='separator'?13:item.type==='header'?30:42),0);
    const ax=ui.num(data.anchorX,16),ay=ui.num(data.anchorY,16),ah=ui.num(data.anchorHeight,36);
    const x=Math.max(margin,Math.min(vw-width-margin,ax));
    const proposed=ay+ah+4;
    const y=Math.max(margin,Math.min(vh-height-margin,
      proposed+height<=vh-margin?proposed:ay-height-4));
    return ui.stack({key:'s2:overlay:root',class:ui.abs(0,0,vw,vh),children:[
      ui.button({key:'s2:overlay:outside',class:ui.abs(0,0,vw,vh),
        onClick:'lab:adobe:overlay:close',children:[]}),
      s2PopupAt(menu,x,y,width,height)
    ]});
  }
  if(data.overlay==='adobe-dialog') {
    const vw=viewport.vw,vh=viewport.vh;
    const w=Math.max(290,Math.min(512,vw-36));
    const progress=Math.max(0,Math.min(1,ui.num(data.adobeModalProgress,1)));
    const compact=w<415;
    const variant=ui.str(data.adobeDialogVariant,'confirmation');
    const dialogData={
      confirmation:{title:'Enable smart filters?',description:'Smart filters are nondestructive and preserve your original images.',
        primaryActionLabel:'Enable',secondaryActionLabel:'Remind me later'},
      information:{title:'Connect your account',description:'Connect an account to sync projects across devices.',
        primaryActionLabel:'Continue'},
      warning:{title:'Unverified format',description:'This format has not been verified and may not be viewable by all users.',
        primaryActionLabel:'Continue'},
      destructive:{title:'Delete 3 documents?',description:'The selected documents will be permanently deleted. This cannot be undone.',
        primaryActionLabel:'Delete'},
      error:{title:'Unable to share',description:'An error occurred while sharing. Check the address and try again.',
        primaryActionLabel:'OK',cancelLabel:null}
    }[variant]||{title:'Confirm action',description:'Continue?',primaryActionLabel:'Confirm'};
    const buttonCount=1+(dialogData.cancelLabel===null?0:1)+(dialogData.secondaryActionLabel?1:0);
    const h=compact?316+(buttonCount===3?42:0):252;
    const ease=1-Math.pow(1-progress,3);
    const y=(vh-h)/2+Math.round((1-ease)*18);
    const shadeAlpha=Math.round(154*ease).toString(16).padStart(2,'0');
    return ui.stack({key:'s2:overlay:root',class:ui.abs(0,0,vw,vh),children:[
      SpectrumFoundation.rect('s2:dialog:shade',0,0,vw,vh,0,`#${shadeAlpha}000000`),
      // This remains modal while its closing transition is playing.
      ui.button({key:'s2:dialog:block',class:ui.abs(0,0,vw,vh),children:[]}),
      s2PopupAt(SpectrumDialog.render({key:'s2:dialog',width:w,compact,variant,
        ...dialogData,progress:ease,
        onCancel:'lab:adobe:dialog:cancel',
        onSecondary:'lab:adobe:dialog:secondary',
        onConfirm:'lab:adobe:dialog:confirm'}),(vw-w)/2,y,w,h)
    ]});
  }
  if(data.overlay!=='adobe-picker')return null;
  const palette=SpectrumFoundation.dark;
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
      SpectrumFoundation.rect('s2:picker:popover:bg',0,0,width,height,12,palette.raised,palette.border,1),
      ...items.flatMap((item,i)=>{
        const selected=data.adobePicker===item.id,focused=ui.num(data.overlayIndex,-1)===i;
        const yy=8+i*rowHeight;
        return [ui.button({key:`s2:picker:option:${item.id}`,
          ...(item.disabled?{}:{onClick:`lab:adobe:picker:choose:${item.id}`}),
          class:ui.abs(8,yy,width-16,rowHeight,SpectrumFoundation.cursor(!!item.disabled)),
          stateValues:{disabled:!!item.disabled,selected},
          stateMotions:{hover:{durationMs:120,easing:'out-cubic'},press:{durationMs:90,easing:'out-cubic'}},
          children:[
            SpectrumFoundation.rect(`s2:picker:option:${item.id}:focus`,0,1,width-16,rowHeight-2,6,
              focused?'#FF394A79':'#00000000'),
            SpectrumFoundation.rect(`s2:picker:option:${item.id}:hover`,0,1,width-16,rowHeight-2,6,
              '#00000000',undefined,0,{stateSource:'parent',fillReactive:{
                base:palette.text,alphaBase:0,alphaTerms:item.disabled?{}:{hover:.08,press:.12}}}),
            SpectrumFoundation.text(`s2:picker:option:${item.id}:label`,item.label,14,0,width-80,rowHeight,
              {size:16,color:item.disabled?palette.disabled:palette.text}),
            ...(selected?[ui.svg({key:`s2:picker:option:${item.id}:check`,asset:'check',
              tint:palette.text,interactive:false,class:ui.abs(width-47,14,17,17)})]:[])
          ]})];
      })
    ]}),x,y,width,height)
  ];
  return ui.stack({key:'s2:overlay:root',class:ui.abs(0,0,vw,vh),children:nodes});
}

// Toasts are nonmodal and never install a viewport-wide pointer target.
// The Java owner retains a toast while its exit animation runs; both text and
// icons fade together via a native subtree opacity, not per-child ARGB hacks.
export function renderAdobeSpectrum2Overlay(data,viewport) {
  const overlay=renderSpectrumModalOrPopup(data,viewport);
  const toasts=Array.isArray(data.adobeToasts)?data.adobeToasts:[];
  const tooltipProgress=ui.clamp(data.adobeTooltipProgress,0,1);
  if(!toasts.length && (tooltipProgress<=.001 || data.overlay))return overlay;
  const vw=viewport.vw,vh=viewport.vh;
  const width=Math.max(240,Math.min(480,vw-32));
  const placement=ui.str(data.adobeToastPlacement,'bottom');
  const top=placement.startsWith('top');
  const end=placement.endsWith('-end');
  const messages={neutral:'File settings saved',informative:'A new version is available',
    positive:'Document exported',negative:'Unable to upload document'};
  const views=toasts.filter(entry=>ui.num(entry.progress,0)>.001).map(entry=>{
    const variant=ui.str(entry.variant,'neutral');
    const id=ui.num(entry.id,0);
    const p=ui.clamp(entry.progress,0,1);
    // Smooth cubic timing remains continuous if exit starts during enter.
    const eased=p*p*(3-2*p);
    const exiting=!!entry.exiting;
    const action=variant==='negative';
    const height=action&&width<360?112:64;
    const x=end?Math.max(16,vw-width-16):Math.round((vw-width)/2);
    const baseY=top?16+ui.num(entry.stackOffset,0):
      Math.max(12,vh-height-16-ui.num(entry.stackOffset,0));
    const y=Math.round(baseY+(1-eased)*(top?-20:20));
    const content=SpectrumToast.render({key:`s2:toast:${id}`,width,variant,
      message:messages[variant]||messages.neutral,
      onClose:exiting?null:`spectrum.toast.close:${id}`,
      ...(action?{actionLabel:'Retry',onAction:exiting?null:`spectrum.toast.retry:${id}`}:{})});
    return ui.stack({key:`s2:toast:${id}:motion`,interactive:false,
      style:ui.absolute(x,y,width,height,{opacity:eased}),children:[content]});
  });
  if(tooltipProgress>.001 && !data.overlay){
    const p=tooltipProgress*tooltipProgress*(3-2*tooltipProgress);
    const anchorX=ui.num(data.adobeTooltipX,20);
    const anchorY=ui.num(data.adobeTooltipY,20);
    const anchorH=ui.num(data.adobeTooltipH,40);
    const anchorW=ui.num(data.adobeTooltipW,160);
    const tooltipWidth=Math.min(256,Math.max(160,vw-24));
    const tooltipHeight=72;
    const top=anchorY-tooltipHeight-6>=12;
    const x=Math.max(12,Math.min(vw-12-tooltipWidth,
      anchorX+anchorW/2-tooltipWidth/2));
    const y=top?anchorY-tooltipHeight-6:anchorY+anchorH+6;
    const shift=(1-p)*(top?8:-8);
    const variant=ui.str(data.adobeTooltipVariant,'neutral');
    const labels={neutral:'Open settings',informative:'Your changes are saved automatically',
      negative:'This operation is unavailable'};
    const content=SpectrumTooltip.render({key:'s2:tooltip:active',width:tooltipWidth,
      placement:top?'top':'bottom',variant,label:labels[variant]||labels.neutral});
    views.push(ui.stack({key:'s2:tooltip:motion',interactive:false,
      style:ui.absolute(x,y+shift,tooltipWidth,tooltipHeight,{opacity:p}),
      children:[content]}));
  }
  if(!views.length)return overlay;
  if(!overlay)return ui.stack({key:'s2:toasts:layer',interactive:false,
    style:ui.absolute(0,0,vw,vh),children:views});
  return ui.stack({key:'s2:overlay:composite',interactive:false,
    style:ui.absolute(0,0,vw,vh),children:[overlay,...views]});
}
