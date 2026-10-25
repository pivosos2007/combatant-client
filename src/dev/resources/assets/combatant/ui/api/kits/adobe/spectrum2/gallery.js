/* Adobe Spectrum 2 gallery. Independent of the neutral multi-kit host. */
import { AdobeSpectrum2 } from './components.js';
const SpectrumKit = AdobeSpectrum2;
const SpectrumPalette = SpectrumKit.dark;
const SpectrumAt = (node,x,y,w,h) => ({...node,style:ui.absolute(x,y,w,h)});
export function renderAdobeSpectrum2Gallery(data,width,page) {
  const nodes=[];
  const heading=(key,value,y)=>nodes.push(SpectrumKit.text(key,value,24,y,width-48,36,
    {size:25,strong:true}));
  const detail=(key,value,y)=>nodes.push(SpectrumKit.text(key,value,24,y,width-48,28,
    {size:15,color:SpectrumPalette.muted}));
  const divider=(key,y)=>nodes.push(SpectrumKit.rect(key,24,y,width-48,1,0,SpectrumPalette.border));
  const place=(node,x,y,w,h)=>nodes.push(SpectrumAt(node,x,y,w,h));
  if(page==='buttons') {
    heading('s2g:button-head','Buttons',22);
    detail('s2g:button-description','Spectrum: Accent, Primary, Secondary and Negative',63);
    const variants=[['accent','Accent',116],['primary','Primary',120],
      ['secondary','Secondary',144],['negative','Negative',124]];
    let x=24,y=127;
    for(const [variant,label,bw] of variants){
      if(x+bw>width-24){x=24;y+=60;}
      place(SpectrumKit.button({key:`s2:button:${variant}`,variant,label,width:bw,
        onClick:`lab:adobe:button:${variant}`}),x,y,bw,40);x+=bw+16;
    }
    divider('s2g:button-line',y+85);
    heading('s2g:outline-title','Outline and sizes',y+105);
    let xx=24;
    for(const [size,label] of [['small','Small'],['medium','Medium'],['large','Large']]) {
      const bw=110,hh=SpectrumKit.size[size];
      place(SpectrumKit.button({key:`s2:outline:${size}`,variant:'primary',outline:true,
        size,label,width:bw,onClick:`lab:adobe:button:${size}`}),xx,y+177,bw,hh);
      xx+=bw+16;
    }
    divider('s2g:action-line',y+252);
    heading('s2g:action-title','Action buttons',y+276);
    detail('s2g:action-desc','Lower emphasis, used in toolbars and dense panels',y+315);
    place(SpectrumKit.actionButton({key:'s2:action:edit',label:'Edit',width:108,
      onClick:'lab:adobe:action:edit'}),24,y+369,108,36);
    place(SpectrumKit.actionButton({key:'s2:action:preview',label:'Preview',width:125,
      selected:!!data.adobeAction,onClick:'lab:adobe:action:preview'}),150,y+369,125,36);
    place(SpectrumKit.button({key:'s2:button:disabled',variant:'accent',label:'Disabled',
      width:132,disabled:true}),293,y+369,132,40);
  } else if(page==='selection') {
    heading('s2g:selection-head','Selection controls',22);
    detail('s2g:selection-desc','Switch, Checkbox and Radio group states',64);
    place(SpectrumKit.checkbox({key:'s2:checkbox',selected:!!data.adobeChecked,
      onClick:'lab:adobe:checkbox'}),24,132,40,40);
    nodes.push(SpectrumKit.text('s2g:check-label','Include metadata',77,132,185,40,{size:17}));
    place(SpectrumKit.checkbox({key:'s2:indeterminate',indeterminate:true,
      onClick:'lab:adobe:checkbox'}),283,132,40,40);
    nodes.push(SpectrumKit.text('s2g:check-mixed','Mixed',332,132,125,40,{size:17}));
    divider('s2g:selection-divider',209);
    heading('s2g:switch-head','Switch',234);
    place(SpectrumKit.switch({key:'s2:switch',selected:!!data.adobeEnabled,
      onClick:'lab:adobe:switch'}),24,310,54,32);
    nodes.push(SpectrumKit.text('s2g:switch-label','Automatic corrections',91,306,250,40,{size:17}));
    divider('s2g:radio-divider',391);
    heading('s2g:radio-head','Radio group',413);
    for(let i=0;i<3;i++){
      const x=24+i*180;
      place(SpectrumKit.radio({key:`s2:radio:${i}`,selected:ui.num(data.adobeRadio,0)===i,
        onClick:`lab:adobe:radio:${i}`}),x,482,40,40);
      nodes.push(SpectrumKit.text(`s2g:radio:${i}:text`,['Original','Edited','Proof'][i],
        x+43,482,120,40,{size:16}));
    }
    divider('s2g:disabled-divider',561);
    heading('s2g:disabled-head','Disabled',585);
    place(SpectrumKit.checkbox({key:'s2:checkbox:disabled',selected:true,disabled:true}),24,655,40,40);
    place(SpectrumKit.switch({key:'s2:switch:disabled',selected:true,disabled:true}),110,659,54,32);
    place(SpectrumKit.radio({key:'s2:radio:disabled',selected:true,disabled:true}),203,655,40,40);
  } else if(page==='inputs') {
    heading('s2g:input-head','Text fields',22);
    detail('s2g:input-note','Visible label above the value; the native Combatant editor handles input',64);
    const w=Math.min(430,width-58);
    place(SpectrumKit.textField({key:'s2:field',label:'Project name',value:data.value||'',
      placeholder:'Untitled project',focused:data.focus==='adobe-field',
      caretOffset:data.caretOffset,width:w,onClick:'lab:adobe:focus:field',
      description:'The project name appears in exported files.'}),24,124,w,100);
    place(SpectrumKit.textField({key:'s2:quiet-field',label:'Read-only preview',value:'sample.png',
      width:w,quiet:true}),24,256,w,74);
    divider('s2g:input-divider',369);
    heading('s2g:tag-head','Tags',397);
    detail('s2g:tag-desc','Compact labels used for metadata and filtering',435);
    for(const [i,label] of ['Design','Review','Published'].entries()) {
      place(SpectrumKit.tag({key:`s2:tag:${i}`,label,width:110}),24+i*128,490,110,28);
    }
    divider('s2g:search-divider',566);
    heading('s2g:search-head','Search field',588);
    place(SpectrumKit.searchField({key:'s2:search',label:'Search files',
      value:data.adobeSearch||'',placeholder:'Search documents',width:w,
      caretOffset:data.adobeSearchCaretOffset,focused:data.focus==='adobe-search',
      onClick:'lab:adobe:focus:search',onClear:'lab:adobe:search:clear'}),24,646,w,80);
  } else if(page==='navigation') {
    heading('s2g:navigation-head','Navigation',22);
    detail('s2g:navigation-note','Tabs use the Spectrum underline selection pattern',64);
    const w=Math.min(570,width-48);
    place(SpectrumKit.tabs({key:'s2:tabs',width:w,selected:data.adobeTab||'files',items:[
      {id:'files',label:'Files',onClick:'lab:adobe:tab:files'},
      {id:'activity',label:'Activity',onClick:'lab:adobe:tab:activity'},
      {id:'sharing',label:'Sharing',onClick:'lab:adobe:tab:sharing'}
    ]}),24,130,w,48);
    divider('s2g:navigation-divider',241);
    heading('s2g:nav-action-title','Action selection',270);
    detail('s2g:nav-action-desc','A compact selection tool made from independent action buttons',308);
    for(const [i,id] of ['grid','list','detail'].entries()) {
      place(SpectrumKit.actionButton({key:`s2:nav:${id}`,label:['Grid','List','Details'][i],
        selected:(data.adobeView||'list')===id,width:110,
        onClick:`lab:adobe:view:${id}`}),24+i*126,376,110,36);
    }
  } else if(page==='pickers') {
    heading('s2g:picker-head','Picker',22);
    detail('s2g:picker-note','Single selection, keyboard navigation and disabled items',63);
    const choices=[
      {id:'low',label:'Low quality'},
      {id:'medium',label:'Balanced'},
      {id:'high',label:'High quality'},
      {id:'ultra',label:'Ultra (unavailable)',disabled:true}
    ];
    place(SpectrumKit.picker({key:'s2:picker',label:'Export quality',width:370,
      value:data.adobePicker||'medium',items:choices,open:data.overlay==='adobe-picker',
      onClick:'lab:adobe:picker:open'}),24,136,370,80);
    divider('s2g:picker-divider',278);
    heading('s2g:picker-subhead','Selection preview',305);
    detail('s2g:picker-summary',`Selected: ${ui.str(data.adobePicker,'medium')}`,349);
    place(SpectrumKit.picker({key:'s2:picker:disabled',label:'Unavailable picker',
      width:370,disabled:true,placeholder:'Unavailable',items:[]}),24,408,370,80);
  } else if(page==='feedback') {
    heading('s2g:feedback-head','Values and feedback',22);
    detail('s2g:feedback-note','Slider, ProgressBar and Meter share a real controlled value',63);
    const value=ui.num(data.adobeSlider,40),w=Math.min(500,width-56);
    place(SpectrumKit.slider({key:'s2:slider',label:'Export quality',width:w,
      value,min:0,max:100,valueLabel:`${Math.round(value)}%`,
      onClick:'lab:adobe:slider:focus'}),24,134,w,68);
    divider('s2g:feedback-divider',244);
    heading('s2g:progress-head','ProgressBar',267);
    place(SpectrumKit.progressBar({key:'s2:progress',label:'Preparing assets',width:w,value}),24,330,w,54);
    divider('s2g:meter-divider',415);
    heading('s2g:meter-head','Meter',436);
    detail('s2g:meter-note','User-controlled values, with semantic variants',478);
    place(SpectrumKit.meter({key:'s2:meter',label:'Storage capacity',width:w,
      variant:value>80?'negative':value>55?'notice':'positive',value}),24,530,w,54);
  }
  return nodes;
}
