/* Combatant Client, GPL-3.0.
 * Schematics: Spectrum 2 editing / browsing frame.
 * The library is a separate browsing context. In editing mode the native canvas
 * is the content, never a card. Only Java owns the voxel renderer and hit tests.
 */
import { SpectrumFoundation } from '../../api/kits/adobe/spectrum2/foundation.js';
import { SpectrumButton } from '../../api/kits/adobe/spectrum2/button.js';
import { SpectrumActionButton } from '../../api/kits/adobe/spectrum2/action-button.js';
import { SpectrumSearchField } from '../../api/kits/adobe/spectrum2/search-field.js';
import { SpectrumSegmentedControl } from '../../api/kits/adobe/spectrum2/segmented-control.js';
import { SpectrumProgressBar } from '../../api/kits/adobe/spectrum2/progress-bar.js';
import { SpectrumCardView } from '../../api/kits/adobe/spectrum2/card-view.js';
import { SpectrumToast } from '../../api/kits/adobe/spectrum2/toast.js';

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
  const w=Math.min(110,Math.max(46,label.length*7+20));
  return {width:w,node:ui.stack({key,interactive:false,class:ui.abs(x,y,w,24),children:[
    bg(`${key}:surface`,0,0,w,24,S.withOpacity(p.accent,.13),S.withOpacity(p.accent,.50),5),
    ui.text({key:`${key}:label`,text:label,color:p.text,interactive:false,verticalAlign:'center',
      class:ui.abs(5,1,w-10,22,'font-Iosevka-Regular font-size-11 text-align-center')})
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
    tx(`${key}:text`,label,0,0,width,27,p.text,12,true,'center')]);
}
// Mouse pictograms are monochrome SVG assets (left/right drag + wheel) drawn like
// key-cap hints; these are not additional buttons or hit-test surfaces.
function gesture(key,asset,caption,x,y,w,p){
  return group(key,{x,y,w,h:30},[
    bg(`${key}:badge`,0,1,28,27,p.field,p.border,5),
    ui.svg({key:`${key}:icon`,asset,tint:p.text,interactive:false,class:ui.abs(5,5,18,18)}),
    tx(`${key}:caption`,caption,35,2,w-35,26,p.muted,12)]);
}
function header(d,p,w,h,rail) {
  const browsing=!!d.libraryMode;
  const name=browsing?(d.labels?.title||'Schematics'):(d.loaded?d.title:(d.labels?.title||'Schematics'));
  const left=rail+22, available=Math.max(40,w-left-(d.standalone?62:17));
  const titleW=Math.min(290,Math.max(105,available*.33));
  const nodes=[bg('app:header:bg',0,0,w,h,p.surface),rule('app:header:rule',0,h-1,w,p),
    ui.svg({key:'app:header:mark',asset:'layers',tint:p.accent,interactive:false,
      class:ui.abs(rail+14,Math.max(11,(h-21)/2),20,20)}),
    ui.text({key:'app:header:name',text:name,color:p.text,interactive:false,
      runtimeMarquee:true,stateSource:'parent',marqueeSpeed:25,
      class:ui.abs(left+16,5,titleW-6,h-10,'font-SourceSans3Semibold font-size-17 clip overflow-hidden')})];
  if(!browsing && d.loaded){
    const hintStart=left+titleW+20;
    const room=w-hintStart-(d.standalone?70:16);
    if(room>=420){
      nodes.push(gesture('app:help:orbit','mouse-left',d.view==='2d'?'Pan':'Orbit',hintStart,12,98,p));
      nodes.push(gesture('app:help:pan','mouse-right','Pan',hintStart+102,12,89,p));
      nodes.push(gesture('app:help:wheel','mouse-wheel','Zoom',hintStart+196,12,96,p));
      nodes.push(keycap('app:help:shift','Shift',hintStart+303,12,p,47));
      nodes.push(tx('app:help:shift-text','+ drag to pan',hintStart+354,13,Math.max(35,room-354),27,p.muted,12));
    }else if(room>=257){
      nodes.push(gesture('app:help:orbit','mouse-left',d.view==='2d'?'Pan':'Orbit',hintStart,12,92,p));
      nodes.push(gesture('app:help:pan','mouse-right','Pan',hintStart+88,12,82,p));
      nodes.push(gesture('app:help:wheel','mouse-wheel','Zoom',hintStart+178,12,83,p));
    }
  }
  if(d.standalone)nodes.push(actionButton('app:close','x',w-50,10,p,{quiet:false,action:'close'}));
  return nodes;
}

function toolRail(d,p,h,head,rail) {
  const editing=!d.libraryMode;
  const nodes=[bg('rail:surface',0,head,rail,h-head,p.surface),
    bg('rail:right-border',rail-1,head,1,h-head,p.border),
    actionButton('rail:library','folder-open',10,head+14,p,{size:34,selected:!editing,action:'library'}),
    actionButton('rail:editor','layers',10,head+56,p,{size:34,selected:editing,disabled:!d.loaded&&!d.loading,action:'editor'}),
    rule('rail:separator',12,head+104,rail-24,p),
    actionButton('rail:properties','settings-2',10,head+119,p,
      {size:34,selected:editing&&d.drawerMode==='properties',disabled:!editing,action:'properties-toggle'}),
    actionButton('rail:materials','package',10,head+161,p,
      {size:34,selected:editing&&d.drawerMode==='materials',disabled:!editing,action:'materials-toggle'}),
    actionButton('rail:layers','layers',10,head+203,p,
      {size:34,selected:editing&&d.drawerMode==='layers',disabled:!editing,action:'layers-toggle'})];
  if(h-head>=340){
    nodes.push(rule('rail:bottom:separator',12,h-67,rail-24,p));
    nodes.push(actionButton('rail:folder','folder-open',10,h-55,p,{size:34,action:'folder'}));
  }
  return nodes;
}

// List rows intentionally use quads, not rounded cards. The viewport owns clipping,
// and runtimeMarquee animates *only* the hovered title without JS frame updates.
function fileList(d,p,width) {
  const files=A(d.files),rowH=67;
  const children=[];
  files.forEach((f,i)=>{
    const selected=!!f.selected,y=i*rowH;
    const mark=formatMark(`files:row:${i}:format`,f.format,0,0,p);
    const textWidth=Math.max(30,width-46-mark.width-26);
    // Keep the format in its own fixed-width column. The file name scrolls
    // horizontally within its clipped area on hover, never under the badge.
    const content=[
      bg(`files:row:${i}:surface`,0,0,width,rowH-1,selected?p.selected:'#00000000'),
      ui.shape({key:`files:row:${i}:hover`,shape:'box',style:ui.absolute(0,0,width,rowH-1),
        corners:ui.corner.all(ui.corner.square()),fill:'#00000000',interactive:false,
        stateSource:'parent',fillReactive:{base:p.accent,alphaBase:0,
          alphaTerms:{hover:.075,press:.15}}}),
      ...(selected?[bg(`files:row:${i}:accent`,0,0,3,rowH-1,p.accent)]:[]),
      ui.svg({key:`files:row:${i}:icon`,asset:'file',tint:f.failed?p.danger:p.muted,
        interactive:false,class:ui.abs(12,18,22,22)}),
      ui.text({key:`files:row:${i}:title`,text:f.title||'',color:p.text,
        stateSource:'parent',interactive:false,runtimeMarquee:true,
        marqueeSpeed:38,marqueeHoldMs:650,marqueeFadeWidth:10,
        class:ui.abs(46,8,textWidth,29,'font-SourceSans3Semibold font-size-16 clip')}),
      ui.text({key:`files:row:${i}:subtitle`,text:f.failed?f.meta:`${f.file}  ·  ${f.meta}`,
        color:f.failed?p.danger:p.muted,interactive:false,stateSource:'parent',
        runtimeMarquee:true,marqueeSpeed:32,marqueeHoldMs:740,marqueeFadeWidth:9,
        class:ui.abs(46,34,textWidth,23,'font-SourceSans3 font-size-13 clip')}),
      {...mark.node,style:ui.absolute(width-mark.width-13,21,mark.width,24)},
      bg(`files:row:${i}:divider`,0,rowH-1,width,1,p.border)
    ];
    children.push(ui.button({key:`files:row:${i}`,class:ui.abs(0,y,width,rowH,'cursor-pointer'),
      onClick:act(`file:${f.id}`),stateValues:{selected},
      stateMotions:{hover:{durationMs:120,easing:'out-cubic'},press:{durationMs:85,easing:'out-cubic'}},
      children:content}));
  });
  return ui.stack({key:'files:list:content',style:ui.style({width,height:Math.max(1,files.length*rowH)}),
    children});
}
function scrollNode(key,x,y,w,h,content,p,wheelStep=48){
  return ui.scroll({key,style:ui.absolute(x,y,w,h,{overflow:'hidden'}),
    wheelStep,smoothScroll:true,scrollSmoothingRate:.021,
    scrollbar:true,scrollbarWidth:8,scrollbarRadius:4,scrollbarOffset:-10,
    scrollbarMinThumb:35,scrollbarColor:p.muted,
    scrollbarBaseAlpha:.58,scrollbarHoverAlpha:.26,scrollbarDragAlpha:.16,
    scrollbarVisibilityMs:2100,children:[content]});
}
// Full browsing page (NOT a permanent sidebar alongside the viewport).
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
    // These two icons really switch representations, they are not editor navigation.
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
    const contentHeight=d.cardsMode?Math.ceil(files.length/actualCols)*187:files.length*67;
    const content=d.cardsMode
      ?SpectrumCardView.render({key:'files:cards',width:viewportWidth,selectionMode:'single',
        selectionStyle:'highlight',marqueeOnHover:true,selectedIds:chosen,colors:p,size:listW>=600?'M':'S',
        items:files.map(f=>({id:String(f.id),label:f.title,description:f.failed?f.meta:(f.size||f.meta),
          format:f.format,color:f.failed?'#FF503030':p.raised,
          icon:'layers',previewText:f.size,onClick:act(`file:${f.id}`)}))})
      :fileList(d,p,viewportWidth);
    // Do not absolutely position the scroll content: UiLayoutEngine excludes absolute
    // children from measured content height, which previously made wheel scroll inert.
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

// The stage is the native 3D/2D canvas; the script only supplies unobtrusive toolbars.
function stage(d,p,r,canvas,strip,gap) {
  const w=r.w,h=r.h,loaded=!!d.loaded;
  const nodes=[...(loaded?[]:[bg('stage:pasteboard',0,0,w,h,'#E8171717')]),
    // Tools are above the native viewport and never rendered over its contents.
    bg('stage:toolbar:background',0,0,w,49,p.surface),
    rule('stage:toolbar:divider',0,48,w,p)];
  const maxTabs=Math.max(100,Math.min(210,w*.30));
  nodes.push(seg('stage:view',[['3d','3D','view-3d',!loaded],['2d','2D','view-2d',!loaded]],
    d.view,13,8,maxTabs,p));
  let x= maxTabs+27;
  if(w>=360){
    nodes.push(actionButton('stage:camera','crosshair',x,8,p,{disabled:!loaded,action:'reset-camera'})); x+=38;
    nodes.push(actionButton('stage:turntable','rotate-ccw',x,8,p,{selected:d.autoRotate,disabled:!loaded,action:'auto-rotate'})); x+=38;
  }
  if(w>=535){
    nodes.push(textButton('stage:render-mode',d.viewModeLabel||'View',x+3,8,Math.min(148,w-x-160),p,
      {disabled:!loaded||d.view!=='3d',action:'view-mode'}));
  }

  if(!loaded){
    nodes.push(ui.svg({key:'stage:empty:icon',asset:d.loading?'folder-sync':'layout-grid',tint:p.muted,
      interactive:false,class:ui.abs(Math.max(1,w/2-23),Math.max(72,h/2-62),46,46)}));
    nodes.push(tx('stage:empty:title',d.loading?'Opening…':d.error||d.labels?.pick||'Choose a file',
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
function propertyDrawer(d,p,r) {
  const w=r.w,h=r.h;let y=4;
  const children=[bg('prop:surface',0,0,w,h,p.surface),bg('prop:left-border',0,0,1,h,p.border),
    tx('prop:title','Properties',18,11,w-68,33,p.text,19,true),
    actionButton('prop:close','x',w-47,13,p,{action:'drawer-close'}),
    rule('prop:header:line',0,51,w,p)];
  const rows=[];
  const caption=(id,text)=>{rows.push(tx(`prop:section:${id}`,text,16,y,w-32,27,p.text,16,true));y+=36;};
  const pair=(id,label,value)=>{
    rows.push(tx(`prop:${id}:key`,label,16,y,w*.46,24,p.muted,13));
    rows.push(txClip(`prop:${id}:val`,value,w*.47,y,w*.46,24,p.text,14,true,'right'));y+=27;
  };
  const buttons=(id,options)=>{
    const n=options.length,ww=(w-32-7*(n-1))/n;
    options.forEach(([label,action,disabled,selected],i)=>rows.push(textButton(`prop:${id}:${i}`,label,16+i*(ww+7),y,
      ww,p,{action,disabled,selected,quiet:false})));y+=41;
  };
  caption('placement','Placement');
  buttons('origin',[['Here','origin-here',!d.loaded],['Target','origin-look',!d.loaded]]);
  buttons('transform',[['Rotate','rotate',!d.placed],['Mirror','mirror',!d.placed]]);
  pair('mode','Layer mode',String(d.layerMode||'').replaceAll('_',' ').toLowerCase());
  buttons('layer-mode',[['Change mode','layer-mode',!d.loaded]]);
  if(d.exploded){
    pair('gap','Layer gap',N(d.gap).toFixed(2));
    buttons('gap-controls',[['−','gap-decrease',!d.loaded],['+','gap-increase',!d.loaded]]);
  }
  y+=12;rows.push(rule('prop:sep:1',16,y,w-32,p));y+=18;
  caption('automation','Automation');
  buttons('builder',[[(d.building?(d.labels?.builderStop||'Stop'):(d.labels?.builderStart||'Build')),'builder',!d.placed&&!d.building,d.building],
    ['Settings','builder-settings',false]]);
  buttons('projection',[[d.projecting?(d.labels?.projectionHide||'Hide'):(d.labels?.projectionShow||'Project'),
    'projection',false,d.projecting],['Settings','projection-settings',false]]);
  if(d.builderStatus){rows.push(tx('prop:builder:status',d.builderStatus,16,y,w-32,27,p.muted,13));y+=28;}
  y+=12;rows.push(rule('prop:sep:2',16,y,w-32,p));y+=18;
  caption('metadata','Document');
  A(d.info).forEach((row,i)=>pair(`metadata:${i}`,row.label,row.value));
  y+=12;
  buttons('unload',[[d.labels?.unload||'Unload','unload',!d.loaded]]);
  children.push(scrollNode('prop:scroll',0,54,w,Math.max(30,h-54),
    ui.stack({key:'prop:content',style:ui.style({width:w-12,height:Math.max(y+8,h-53)}),children:rows}),p,47));
  return group('prop:drawer',r,children);
}
// Material and layer tools are separate contextual panels, never stacked inside Properties.
function materialDrawer(d,p,r){
  const w=r.w,h=r.h,children=[bg('materials:surface',0,0,w,h,p.surface),
    bg('materials:border',0,0,1,h,p.border),
    tx('materials:title',d.labels?.materials||'Materials',18,10,w-68,33,p.text,19,true),
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
    // Use the existing native item bridge. Item thumbnails are not hand-drawn or
    // copied to new textures, and are clipped by the enclosing scroll viewport.
    items.push(ui.item({key:`materials:item:${i}:icon`,item:item.item,
      overlay:false,class:ui.abs(16,y+13,29,29)}));
    items.push(txClip(`materials:item:${i}:name`,item.name,53,y+6,Math.max(40,w-172),25,p.text,14,true));
    items.push(tx(`materials:item:${i}:qty`,item.quantity,w-116,y+6,97,25,
      item.warning?p.danger:p.muted,13,true,'right'));
    if(item.detail)items.push(tx(`materials:item:${i}:detail`,item.detail,53,y+31,Math.max(40,w-72),21,
      item.warning?p.danger:p.muted,12));
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
    tx('layers:title','Layers',18,10,w-70,33,p.text,19,true),
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
    const y=h-82-(A(d.toasts).length-1-i)*74;
    const t=SpectrumToast.render({key:`schematic:toast:${toast.id}`,colors:p,width,
      message:toast.message,variant:toast.variant,onClose:act(`toast-close:${toast.id}`)});
    // Keep toasts in the workspace overlay, not in the global notifier.
    nodes.push({...at(t,w-width-16,y,width,64),style:ui.absolute(w-width-16,y,width,64,
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
  if(d.libraryMode) nodes.push(files(d,p,library,search));
  else {
    nodes.push(stage(d,p,view,canvas,strip,gap));
    const enter=Math.max(0,Math.min(1,N(d.drawerProgress)));
    const drawerRect={...drawer,x:drawer.x+Math.round((1-enter)*Math.min(100,drawer.w))};
    if(d.drawerMode==='properties')nodes.push(propertyDrawer(d,p,drawerRect));
    else if(d.drawerMode==='materials')nodes.push(materialDrawer(d,p,drawerRect));
    else if(d.drawerMode==='layers')nodes.push(layersDrawer(d,p,drawerRect));
  }
  nodes.push(...localToasts(d,p,w,h));
  return group('schematics:app',{x:0,y:0,w,h},nodes);
}
