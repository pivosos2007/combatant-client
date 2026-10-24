/* Dev component catalog host. Owns navigation; never imports project-specific styling tokens. */
import { renderGoogleMaterial3Gallery } from '../../api/kits/google/material3/gallery.js';
const host = Object.freeze({
  background:'#141218',panel:'#201F25',content:'#141318',border:'#49464F',
  text:'#E6E0E9',muted:'#CAC4D0',active:'#494454'
});
const kits = [{id:'google',label:'Google Material 3',pages:[
  ['buttons','Buttons'],['selection','Selection'],['inputs','Inputs'],
  ['navigation','Navigation'],['surfaces','Surfaces'],['states','States']
],render:renderGoogleMaterial3Gallery}];
const position = (node,x,y,w,h) => ({...node,style:ui.absolute(x,y,w,h)});
const rectangle = (key,x,y,w,h,r,fill,stroke) => ui.roundedRect({
  key,x,y,w,h,radius:r,fill,stroke,strokeWidth:stroke?1:0,interactive:false
});
const label = (key,value,x,y,w,h,size=16,color=host.text,medium=false) => ui.text({
  key,text:value,color,verticalAlign:'center',interactive:false,
  class:ui.abs(x,y,w,h,`font-${medium?'OnestMedium':'Onest'} font-size-${ui.fmt(size)} text-align-left`)
});
export function buildTemplate(ctx) {
  const data=ctx.props||{};
  const vw=Math.max(1,ui.num(data.width,1920)),vh=Math.max(1,ui.num(data.height,1080));
  const pw=Math.min(1400,Math.max(740,vw-100)),ph=Math.min(910,Math.max(560,vh-120));
  const px=(vw-pw)/2,py=Math.max(48,(vh-ph)/2);
  const kit=kits.find(k=>k.id===data.kit)||kits[0];
  const page=kit.pages.some(x=>x[0]===data.page)?data.page:kit.pages[0][0];
  const sidebar=224,rightX=sidebar+32,cw=pw-rightX-26;
  const children=[
    rectangle('catalog:panel',0,0,pw,ph,24,host.panel,host.border),
    label('catalog:title','Component Library',30,23,pw-64,48,30,host.text,true),
    rectangle('catalog:rule',24,103,pw-48,1,0,host.border),
    rectangle('catalog:side-rule',sidebar+13,122,1,ph-145,0,host.border),
    label('catalog:kits-title','Kits',30,127,sidebar-35,24,14,host.muted,true)
  ];
  let kitY=163;
  for(const k of kits) {
    const active=k.id===kit.id;
    children.push(rectangle(`catalog:kit:${k.id}:bg`,22,kitY,sidebar-17,42,12,
      active?host.active:'#00000000'));
    children.push(label(`catalog:kit:${k.id}:label`,k.label,37,kitY,sidebar-42,42,16,
      active?host.text:host.muted,active));
    children.push(position(ui.button({key:`catalog:kit:${k.id}`,onClick:`lab:kit:${k.id}`,
      class:ui.abs(0,0,sidebar-17,42,'cursor-pointer'),children:[]}),22,kitY,sidebar-17,42));
    kitY+=47;
  }
  const navStart=kitY+58;
  children.push(label('catalog:pages-title','Components',30,navStart-37,sidebar-36,27,14,host.muted,true));
  for(let i=0;i<kit.pages.length;i++) {
    const [id,name]=kit.pages[i],active=id===page,k=`lab:nav:${id}`,y=navStart+i*49;
    children.push(rectangle(`${k}:bg`,22,y,sidebar-17,42,20,
      active?host.active:'#00000000'));
    children.push(label(`${k}:title`,name,41,y,sidebar-52,42,16,
      active?host.text:host.muted,active));
    children.push(position(ui.button({key:k,onClick:k,class:ui.abs(0,0,sidebar-17,42,'cursor-pointer'),
      children:[],stateValues:{selected:active}}),22,y,sidebar-17,42));
  }
  children.push(rectangle('catalog:preview',rightX-12,121,cw+24,ph-144,16,host.content));
  const detail=kit.render(data,cw,page);
  const contentHeight=Math.max(760,...detail.map(node=>{
    const s=node.style||{};
    return ui.num(s.top,0)+ui.num(s.height,0)+42;
  }));
  children.push(ui.scroll({key:'lab:content',class:ui.abs(rightX,132,cw,ph-165,'clip overflow-hidden'),
    wheelStep:60,scrollbarWidth:4,children:[ui.stack({key:'lab:page',
      class:ui.abs(0,0,cw,contentHeight),children:detail})]}));
  return ui.root({key:'component-lab',class:ui.abs(0,0,vw,vh),children:[
    rectangle('catalog:backdrop',0,0,vw,vh,0,host.background),
    ui.stack({key:'catalog:window',class:ui.abs(px,py,pw,ph),children})
  ]});
}
