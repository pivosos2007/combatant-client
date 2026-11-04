import { SpectrumFoundation } from './foundation.js';
import { SpectrumButton } from './button.js';
import { SpectrumLink } from './link.js';
import { SpectrumActionButton } from './action-button.js';
import { SpectrumActionGroup } from './action-group.js';
import { SpectrumActionBar } from './action-bar.js';
import { SpectrumIllustratedMessage } from './illustrated-message.js';
import { SpectrumButtonGroup } from './button-group.js';
import { SpectrumCheckbox } from './checkbox.js';
import { SpectrumCheckboxGroup } from './checkbox-group.js';
import { SpectrumSegmentedControl } from './segmented-control.js';
import { SpectrumSwitch } from './switch.js';
import { SpectrumRadio } from './radio.js';
import { SpectrumTabs } from './tabs.js';
import { SpectrumTextField } from './text-field.js';
import { SpectrumTag } from './tag.js';
import { SpectrumPicker } from './picker.js';
import { SpectrumSlider } from './slider.js';
import { SpectrumProgressBar } from './progress-bar.js';
import { SpectrumMeter } from './meter.js';
import { SpectrumSearchField } from './search-field.js';
import { SpectrumStatusLight } from './status-light.js';
import { SpectrumCard } from './card.js';
import { SpectrumActionMenu } from './action-menu.js';
import { SpectrumSideNavigation } from './side-navigation.js';
import { SpectrumAccordion } from './accordion.js';
import { SpectrumBreadcrumbs } from './breadcrumbs.js';
import { SpectrumTreeView } from './tree-view.js';
import { SpectrumBadge } from './badge.js';
import { SpectrumAvatar } from './avatar.js';
import { SpectrumAvatarGroup } from './avatar-group.js';
import { SpectrumDivider } from './divider.js';
import { SpectrumProgressCircle } from './progress-circle.js';
import { SpectrumToggleButton } from './toggle-button.js';
import { SpectrumInlineAlert } from './inline-alert.js';
import { SpectrumSkeleton } from './skeleton.js';
import { SpectrumLabeledValue } from './labeled-value.js';
import { SpectrumColorSwatch } from './color-swatch.js';
import { SpectrumColorSwatchGroup } from './color-swatch-group.js';
import { SpectrumTable } from './table.js';
import { SpectrumNumberField } from './number-field.js';
import { SpectrumCalendar } from './calendar.js';
import { SpectrumDatePicker } from './date-picker.js';
import { SpectrumRangeSlider } from './range-slider.js';
import { SpectrumTagGroup } from './tag-group.js';
import { SpectrumToggleButtonGroup } from './toggle-button-group.js';
import { SpectrumSelectBoxGroup } from './select-box-group.js';
import { SpectrumDisclosure } from './disclosure.js';
import { SpectrumComboBox } from './combo-box.js';
import { SpectrumColorArea } from './color-area.js';
import { SpectrumColorSlider } from './color-slider.js';
import { SpectrumColorWheel } from './color-wheel.js';
import { SpectrumColorField } from './color-field.js';
import { SpectrumColorSwatchPicker } from './color-swatch-picker.js';
import { hsvHex, opacityHex } from './color-utils.js';
import { SpectrumListView } from './list-view.js';
import { SpectrumCardView } from './card-view.js';
import { SpectrumRadioGroup } from './radio-group.js';
import { SpectrumContextualHelp } from './contextual-help.js';
import { SpectrumLinkButton } from './link-button.js';
import { SpectrumPopover } from './popover.js';
import { SpectrumStandardDialog } from './standard-dialog.js';

/* Spectrum 2 component-by-component dev reference. No Material tokens or imports. */
const SpectrumPalette = SpectrumFoundation.dark;
const SpectrumAt = (node,x,y,w,h) => ({...node,style:ui.absolute(x,y,w,h)});
// A real tree uses persistent child data. Breadcrumbs only visualize the selected ancestry.
function spectrumFilePath(items,id,ancestors=[]) {
  for(const item of items) {
    const next=[...ancestors,item];
    if(item.id===id)return next;
    const found=spectrumFilePath(item.children||[],id,next);
    if(found)return found;
  }
  return null;
}
const SpectrumFileItems=[{id:'home',label:'Creative Cloud',children:[
  {id:'projects',label:'Projects',children:[
    {id:'assets',label:'Brand assets',children:[
      {id:'campaign',label:'Campaign',children:[{id:'images',label:'Hero images'}]},
      {id:'logos',label:'Logos'}]},
    {id:'drafts',label:'Drafts'}]},
  {id:'shared',label:'Shared'},
  {id:'archive',label:'Legacy archive',disabled:true,icon:'folder-archive'}
]}];

const SpectrumDescriptions = Object.freeze({
  buttons:['Buttons','Semantic hierarchy: accent, primary, secondary, negative and outline variants.'],
  'action-buttons':['Action buttons','Toolbar actions and selected state; lower emphasis than buttons.'],
  'action-group':['Action group','Connected compact groups; selection, density, orientation and justified layout.'],
  'button-group':['Button group','Related actions with wrapping, vertical layout, and disabled members.'],
  'action-bar':['Action bar','Selection-scoped actions, a clear control and adaptive layout.'],
  'illustrated-message':['Illustrated message','Empty and error states with an original geometric illustration and actions.'],
  links:['Links','Primary, secondary and quiet hyperlinks. Spectrum primary is blue.'],
  checkbox:['Checkbox','Independent multi-selection. Default is monochrome; emphasized is optional.'],
  'checkbox-group':['Checkbox group','Group label, multiple selection, horizontal or vertical orientation and validation.'],
  'segmented-control':['Segmented control','Switch between views using one mutually exclusive selected segment.'],
  switch:['Switch','Immediate on/off setting. The ring appears only with keyboard focus.'],
  radio:['Radio','A selected Spectrum radio thickens its ring instead of adding a center dot.'],
  'text-field':['Text field','Labels remain above the value. Focus, read-only and disabled are distinct.'],
  'search-field':['Search field','Editable search using the native Combatant text input pipeline.'],
  picker:['Picker','Single choice; a real popup, keyboard navigation and disabled option.'],
  slider:['Slider','Click and drag the track. Arrow keys and Home/End also update the value.'],
  tabs:['Tabs','One tab is active at a time; its underline moves with the selection.'],
  'side-navigation':['Side navigation','Selected routes, nested items, expansion and disabled navigation.'],
  accordion:['Accordion','Progressive disclosure with compact, regular and spacious density.'],
  breadcrumbs:['Breadcrumbs','A linear path to the current location. Click ancestors; use Tree view to explore branches.'],
  'tree-view':['Tree view','Browse a nested collection, expand branches, and select files independently of breadcrumbs.'],
  'inline-alert':['Inline alert','Semantic messages with border, subtleFill and boldFill appearances.'],
  skeleton:['Skeleton','Static loading placeholders; no per-frame JavaScript or layout invalidation.'],
  'labeled-value':['Labeled value','Read-only property and value pairs in vertical and horizontal layouts.'],
  'color-swatch':['Color swatch','Shape, rounding, size and group density are independent.'],
  'color-area':['Color area','Two-channel saturation and brightness selection; one GPU gradient quad.'],
  'color-slider':['Color slider','Hue and alpha tracks with a visible selected-color handle.'],
  'color-wheel':['Color wheel','Radial hue selection, combined with a saturation/brightness area.'],
  'color-field':['Color field','Editable hexadecimal color value, validated by the native input bridge.'],
  'color-swatch-picker':['Color swatch picker','A controlled palette of selectable color samples.'],
  'table':['Table','Sortable headers, explicit selection, densities and quiet appearance.'],
  'number-field':['Number field','Native numeric text entry, min/max stepping, units and validation.'],
  'combo-box':['Combo box','A searchable selection field with keyboard-accessible suggestions.'],
  'calendar':['Calendar','Month navigation and date selection, including unavailable dates.'],
  'date-picker':['Date picker','Single date and range selection through a calendar popover.'],
  'range-slider':['Range slider','Two constrained thumbs select a subset of a bounded range.'],
  'tag-group':['Tag group','Selectable and removable tags, wrapping and maximum rows.'],
  'toggle-button-group':['Toggle button group','Single and multiple pressed states, connected compact density.'],
  'select-box-group':['Select box group','Large visual selection cards, single and multiple selection.'],
  'disclosure':['Disclosure','Independent expandable sections with a flat header and panel.'],
  badges:['Badges','Semantic metadata with bold, subtle and outline fills; not interactive.'],
  avatars:['Avatars','Initials and guest fallbacks with configurable sizes. No external images.'],
  'avatar-group':['Avatar group','Overlapping collaborator avatars with a compact overflow count.'],
  dividers:['Dividers','Non-interactive horizontal and vertical separators.'],
  'progress-circle':['Progress circle','Native arc strokes: determinate and clock-driven indeterminate progress.'],
  'toggle-button':['Toggle button','Independent pressed state, emphasized and quiet variants.'],
  tags:['Tags','Compact metadata labels, separate from interactive selection.'],
  cards:['Cards','Content surfaces with quiet and selected alternatives.'],
  'status-light':['Status light','Semantic status shown with a small color indicator and text.'],
  'progress-bar':['Progress bar','A determinate progress indicator.'],
  meter:['Meter','Semantic feedback for a measurement rather than task progress.'],
  menu:['Menu','Popup actions, shortcuts, headers, separators and disabled items.'],
  popover:['Popover','Anchored transient content with viewport-aware flipping and offset.'],
  'standard-dialog':['Standard dialog','A modal content container with S, M and L sizes and actions.'],
  'alert-dialog':['Alert dialog','Five Spectrum variants, modal actions and an animated lifecycle.'],
  toast:['Toast','Non-modal status feedback with a close button and an optional contextual action.'],
  tooltip:['Tooltip','Brief contextual labels; hover to test delayed appearance and animated exit.'],
  'list-view':['List view','Selectable collection with descriptions, unavailable items and checkbox/highlight selection.'],
  'card-view':['Card view','Responsive grid of selectable asset cards with controlled selection.'],
  'radio-group':['Radio group','A single-choice field with label, help text and validation.'],
  'contextual-help':['Contextual help','Small info/help triggers with an anchored popover.'],
  'link-button':['Link button','A button with a navigation affordance, variants and disabled state.'],
  states:['Disabled states','Side-by-side enabled and unavailable controls; this page scrolls.']
});
export function renderAdobeSpectrum2Gallery(data,width,page) {
  const nodes=[];
  const heading=(key,value,y)=>nodes.push(SpectrumFoundation.text(key,value,24,y,width-48,38,
    {size:26,strong:true}));
  const detail=(key,value,y)=>nodes.push(SpectrumFoundation.text(key,value,24,y,width-48,31,
    {size:16,color:SpectrumPalette.muted}));
  const divider=(key,y)=>nodes.push(SpectrumFoundation.rect(key,24,y,width-48,1,0,SpectrumPalette.border));
  const place=(node,x,y,w,h)=>nodes.push(SpectrumAt(node,x,y,w,h));
  const sub=(key,text,y)=>heading(key,text,y);
  const desc=SpectrumDescriptions[page]||['Spectrum 2',''];
  heading(`s2g:${page}:heading`,desc[0],22);
  detail(`s2g:${page}:intro`,desc[1],68);
  divider(`s2g:${page}:intro-rule`,111);
  const col2=width>=660?Math.floor(width/2)+12:24;
  const wide=width>=660;
  const fieldWidth=Math.min(440,width-52);
  if(page==='popover') {
    sub('s2g:pop:heading','Placement and adaptive flipping',136);
    const options=[['bottom','Below'],['top','Above'],['left','Left'],['right','Right']];
    let px=24,py=205;
    for(const [id,label] of options){
      const bw=116;
      if(px+bw>width-20){px=24;py+=58;}
      place(SpectrumButton.render({key:`s2:popover:trigger:${id}`,width:bw,label,
        variant:'secondary',outline:true,onClick:`lab:adobe:popover:open:${id}`}),px,py,bw,40);
      px+=bw+12;
    }
    divider('s2g:pop:divider',py+87);
    detail('s2g:pop:hint','Click a trigger to inspect its anchored overlay. Escape closes it.',py+108);
  } else if(page==='standard-dialog') {
    sub('s2g:standard-dialog:title','Standard dialog sizes',136);
    let dx=24,dy=206;
    for(const [id,label] of [['S','Small'],['M','Medium'],['L','Large']]){
      if(dx+128>width-20){dx=24;dy+=58;}
      place(SpectrumButton.render({key:`s2:standard-dialog:trigger:${id}`,width:122,
        label,onClick:`lab:adobe:standard-dialog:open:${id}`}),dx,dy,122,40);
      dx+=138;
    }
    divider('s2g:standard-dialog:rule',dy+88);
    detail('s2g:standard-dialog:help','Modal content with keyboard focus and dismiss actions.',dy+108);
  } else if(page==='buttons') {
    sub('s2g:button-sub','Emphasis',132);
    let x=24,y=198;
    for(const [variant,label,bw] of [['accent','Accent',116],['primary','Primary',126],
      ['secondary','Secondary',146],['negative','Negative',130]]){
      if(x+bw>width-24){x=24;y+=57;}
      place(SpectrumButton.render({key:`s2:button:${variant}`,variant,label,width:bw,
        onClick:`lab:adobe:button:${variant}`}),x,y,bw,40);x+=bw+14;
    }
    divider('s2g:button-divider',y+82);
    sub('s2g:button-sizing','Outline and sizes',y+105);
    let bx=24,by=y+172;
    for(const [size,label] of [['small','Small'],['medium','Medium'],['large','Large']]){
      if(bx+118>width-22){bx=24;by+=64;}
      const h=SpectrumFoundation.size[size];
      place(SpectrumButton.render({key:`s2:outline:${size}`,variant:'primary',outline:true,
        label,size,width:110,onClick:`lab:adobe:button:${size}`}),bx,by,110,h);bx+=130;
    }
    divider('s2g:button-disabled-divider',by+79);
    sub('s2g:button-disabled-heading','Disabled',by+96);
    place(SpectrumButton.render({key:'s2:button:disabled',variant:'accent',label:'Disabled',
      width:140,disabled:true}),24,by+165,140,40);
  } else if(page==='action-buttons') {
    sub('s2g:action-sub','Default and selected',133);
    place(SpectrumActionButton.render({key:'s2:action:edit',label:'Edit',width:126,
      onClick:'lab:adobe:action:edit'}),24,205,126,36);
    place(SpectrumActionButton.render({key:'s2:action:preview',label:'Preview',width:134,
      selected:!!data.adobeAction,onClick:'lab:adobe:action:preview'}),166,205,134,36);
    divider('s2g:action-divider',295);
    detail('s2g:action-help','Click Preview to test selected/unselected transitions.',325);
  } else if(page==='action-group') {
    const groupWidth=Math.min(450,width-48);
    sub('s2g:ag-heading','Compact · connected control',133);
    place(SpectrumActionGroup.render({key:'s2:ag:single',width:groupWidth,density:'compact',
      isJustified:true,enableSelection:true,selectionMode:'single',
      selectedIds:[ui.str(data.adobeActionGroupSingle,'list')],
      items:[['list','List','list'],['grid','Grid',null],['details','Details',null]].map(([id,label,icon])=>({
        id,label,icon,onClick:`lab:adobe:action-group:single:${id}`}))}),24,202,groupWidth,36);
    divider('s2g:ag-divider',288);
    sub('s2g:ag-multi-heading','Regular · multiple selection',308);
    place(SpectrumActionGroup.render({key:'s2:ag:multi',width:groupWidth,
      density:'regular',enableSelection:true,selectionMode:'multiple',isEmphasized:true,
      selectedIds:data.adobeActionGroupMulti||[],
      items:[['pin','Pin'],['favorite','Favorite'],['lock','Lock']].map(([id,label])=>({
        id,label,width:108,onClick:`lab:adobe:action-group:multi:${id}`}))}),24,380,groupWidth,36);
    divider('s2g:ag-quiet-divider',465);
    sub('s2g:ag-quiet-heading','Quiet · vertical · disabled',485);
    place(SpectrumActionGroup.render({key:'s2:ag:quiet',width:206,orientation:'vertical',
      isQuiet:true,items:[{id:'edit',label:'Edit',onClick:'lab:adobe:action-group:action:edit'},
        {id:'copy',label:'Copy',onClick:'lab:adobe:action-group:action:copy'},
        {id:'delete',label:'Unavailable',disabled:true}]}),24,555,206,124);
    sub('s2g:ag-wrap-heading','Responsive overflow · wrap',710);
    place(SpectrumActionGroup.render({key:'s2:ag:wrap',width:Math.min(235,groupWidth),
      size:'S',density:'regular',items:[
        {id:'edit',label:'Edit',width:92,onClick:'lab:adobe:action-group:action:edit'},
        {id:'copy',label:'Copy',width:90,onClick:'lab:adobe:action-group:action:copy'},
        {id:'export',label:'Export',width:104,disabled:true}]}),24,785,Math.min(235,groupWidth),72);
  } else if(page==='button-group') {
    sub('s2g:bg-heading','Related actions',133);
    const groupWidth=Math.min(444,width-48);
    place(SpectrumButtonGroup.render({key:'s2:bg:default',width:groupWidth,
      items:[{id:'cancel',label:'Cancel',variant:'secondary',width:116,
        onClick:'lab:adobe:button-group:cancel'},
      {id:'preview',label:'Preview',variant:'primary',width:120,
        onClick:'lab:adobe:button-group:preview'},
      {id:'save',label:'Save',variant:'accent',width:112,
        onClick:'lab:adobe:button-group:save'}]}),24,205,groupWidth,100);
    divider('s2g:bg-rule',359);
    sub('s2g:bg-vertical','Stacked with unavailable action',385);
    place(SpectrumButtonGroup.render({key:'s2:bg:stack',width:224,orientation:'vertical',
      items:[{id:'import',label:'Import',variant:'primary',width:224,
        onClick:'lab:adobe:button-group:import'},
      {id:'export',label:'Export',variant:'secondary',width:224,disabled:true}]}),24,455,224,92);
  } else if(page==='action-bar') {
    sub('s2g:bar-heading','Selected items',133);
    const w=Math.min(590,width-48);
    place(SpectrumActionBar.render({key:'s2:action-bar',width:w,
      selectedItemCount:ui.num(data.adobeActionBarSelected,3),
      onClear:'lab:adobe:action-bar:clear',items:[
        {id:'edit',label:'Edit',onClick:'lab:adobe:action-bar:edit'},
        {id:'copy',label:'Copy',onClick:'lab:adobe:action-bar:copy'},
        {id:'delete',label:'Delete',disabled:true}
      ]}),24,201,w,w<540?112:60);
    place(SpectrumButton.render({key:'s2:action-bar:select',variant:'secondary',
      label:'Select 3 items',width:170,onClick:'lab:adobe:action-bar:select'}),24,350,170,40);
    divider('s2g:bar-divider',445);
    sub('s2g:bar-emphasis','Emphasized · all selected',465);
    place(SpectrumActionBar.render({key:'s2:action-bar:emphasized',width:w,emphasized:true,
      selectedItemCount:'all',onClear:'lab:adobe:action-bar:clear',items:[
        {id:'share',label:'Share',onClick:'lab:adobe:action-bar:share'},
        {id:'export',label:'Export',onClick:'lab:adobe:action-bar:export'}
      ]}),24,534,w,w<540?112:60);
  } else if(page==='illustrated-message') {
    sub('s2g:illustration-heading','Empty state · vertical',133);
    const w=Math.min(540,width-48);
    place(SpectrumIllustratedMessage.render({key:'s2:illustration:vertical',width:w,
      title:'Create your first asset',description:'Start by uploading or importing a project asset.',
      actions:[{id:'import',label:'Import',variant:'secondary',onClick:'lab:adobe:illustrated:import'},
        {id:'upload',label:'Upload',variant:'accent',onClick:'lab:adobe:illustrated:upload'}]}),
      24,200,w,312);
    divider('s2g:illustration-divider',563);
    sub('s2g:illustration-horizontal-heading','Compact and horizontal',585);
    const horizontal=SpectrumIllustratedMessage.render({key:'s2:illustration:horizontal',
      orientation:'horizontal',size:'S',width:w,title:'Nothing found',
      description:'Try another search or adjust your filters.',actions:[
        {id:'clear',label:'Clear filters',variant:'secondary',onClick:'lab:adobe:illustrated:clear'}]});
    place(horizontal,24,651,w,w>=470?180:270);
  } else if(page==='inline-alert') {
    sub('s2g:alert:heading','Semantic feedback · border',133);
    const w=Math.min(560,width-48);
    const variants=[
      ['neutral','Payment information','Enter your billing address and payment method.','border'],
      ['informative','Changes available','Review the updated settings before applying them.','border'],
      ['positive','Saved successfully','Your changes are now available.','subtleFill'],
      ['notice','Storage nearly full','Remove unused assets to free up space.','subtleFill'],
      ['negative','Upload failed','The service could not accept your file.','boldFill']
    ];
    let y=202;
    for(const [variant,title,description,fillStyle] of variants){
      const props={key:`s2:inline-alert:${variant}`,variant,fillStyle,width:w,title,description};
      const h=SpectrumInlineAlert.height(props);
      place(SpectrumInlineAlert.render(props),24,y,w,h);
      y+=h+18;
    }
  } else if(page==='skeleton') {
    sub('s2g:skeleton:section-heading','Loading structures',133);
    const w=Math.min(450,width-52);
    place(SpectrumSkeleton.render({key:'s2:skeleton:text',width:w,lines:4}),24,207,w,85);
    divider('s2g:skeleton:divider',333);
    sub('s2g:skeleton:shapes','Circle and media rectangle',357);
    place(SpectrumSkeleton.render({key:'s2:skeleton:circle',variant:'circle',width:72}),24,433,72,72);
    place(SpectrumSkeleton.render({key:'s2:skeleton:rectangle',variant:'rectangle',
      width:Math.max(100,Math.min(280,width-150)),height:110}),115,433,
      Math.max(100,Math.min(280,width-150)),110);
    detail('s2g:skeleton:caption','Skeletons stay static while loading; the owner controls completion.',587);
  } else if(page==='labeled-value') {
    sub('s2g:label:heading','Vertical and horizontal properties',133);
    const w=Math.min(470,width-52);
    place(SpectrumLabeledValue.render({key:'s2:labeled:value:vertical',width:w,
      label:'Document',value:'Campaign brief.pdf'}),24,202,w,70);
    place(SpectrumLabeledValue.render({key:'s2:labeled:value:author',width:w,
      label:'Owner',value:'Design team'}),24,296,w,70);
    divider('s2g:label:rule',407);
    sub('s2g:label:horizontal-title','Horizontal and quiet',429);
    place(SpectrumLabeledValue.render({key:'s2:labeled:value:horizontal',width:w,
      orientation:'horizontal',label:'File size',value:'12.8 MB'}),24,502,w,40);
    place(SpectrumLabeledValue.render({key:'s2:labeled:value:quiet',width:w,
      orientation:'horizontal',quiet:true,label:'Modified',value:'Today'}),24,558,w,40);
  } else if(page==='color-swatch') {
    const swShape=ui.str(data.adobeSwatchShape,'square');
    const swRounding=ui.str(data.adobeSwatchRounding,'default');
    const swDensity=ui.str(data.adobeSwatchDensity,'regular');
    const colors=['#4069FD','#4CAF82','#F6B44D','#F8796E','#B185F6',
      '#169A87','#FF809D','#C3BDFF','#61B6DF','#F0CA7A','#C5D4A7','#D3799C'];
    sub('s2g:swatch:heading','Shape',133);
    const options=(prefix,labels,active,y)=>{
      let x=24;
      for(const [id,label] of labels){
        const w=Math.max(92,label.length*8+24);
        if(x+w>width-12)break;
        place(SpectrumActionButton.render({key:`s2:swatch:option:${prefix}:${id}`,
          label,width:w,selected:active===id,
          onClick:`lab:adobe:swatch:${prefix}:${id}`}),x,y,w,36);
        x+=w+10;
      }
    };
    options('shape',[['square','Square'],['rectangle','Rectangle']],swShape,202);
    sub('s2g:swatch:rounding-heading','Rounding',271);
    options('rounding',[['none','None'],['default','Default'],['full','Full']],swRounding,337);
    sub('s2g:swatch:density-heading','Density',406);
    options('density',[['compact','Compact'],['regular','Regular'],['spacious','Spacious']],swDensity,473);
    sub('s2g:swatch:preview-heading','Swatch group',541);
    const groupWidth=Math.min(490,width-50);
    const selected=ui.str(data.adobeSwatchSelected,'0');
    const group=SpectrumColorSwatchGroup.render({key:'s2:swatch:group',width:groupWidth,
      size:'M',shape:swShape,rounding:swRounding,density:swDensity,
      items:colors.map((color,i)=>({id:`${i}`,color,selected:selected===`${i}`,
        onClick:`lab:adobe:swatch:select:${i}`}))});
    place(group,24,613,groupWidth,group.style.height);
    divider('s2g:swatch:divider',856);
    sub('s2g:swatch:sample-sizes','Sizes · XS / S / M / L',873);
    let x=24;
    for(const size of ['XS','S','M','L']){
      const {width:sw,height:sh}=SpectrumColorSwatch.dimensions({shape:swShape,size});
      if(x+sw>width-18)break;
      place(SpectrumColorSwatch.render({key:`s2:swatch:size:${size}`,
        color:'#FF809D',shape:swShape,rounding:swRounding,size}),x,950,sw,sh);
      x+=sw+15;
    }
  } else if(page==='links') {
    sub('s2g:link-sub','Inline and standalone',133);
    place(SpectrumLink.render({key:'s2:link:primary',label:'Primary link',width:168,
      onClick:'lab:adobe:link:primary'}),24,198,168,36);
    place(SpectrumLink.render({key:'s2:link:secondary',label:'Secondary link',width:184,
      variant:'secondary',onClick:'lab:adobe:link:secondary'}),wide?225:24,wide?198:253,184,36);
    divider('s2g:links-divider',330);
    sub('s2g:quiet-link-sub','Quiet',355);
    detail('s2g:quiet-link-help','A quiet link omits its underline when context already makes the link clear.',405);
    place(SpectrumLink.render({key:'s2:link:quiet',label:'Quiet link',quiet:true,width:140,
      onClick:'lab:adobe:link:quiet'}),24,458,140,36);
  } else if(page==='checkbox-group') {
    const w=Math.min(475,width-48);
    sub('s2g:check-group:head','Vertical · required',133);
    const opts=[['design','Design'],['photography','Photography'],['video','Video editing'],['archive','Archive']];
    const selected=Array.isArray(data.adobeCheckboxGroup)?data.adobeCheckboxGroup:[];
    const items=opts.map(([id,label])=>({id,label,disabled:id==='archive',
      onClick:`lab:adobe:check-group:${id}`}));
    const vertical={key:'s2:check-group:vertical',width:w,label:'Interests',required:true,
      emphasized:true,selectedIds:selected,items,invalid:selected.length===0,
      errorMessage:'Select at least one interest.',description:'Choose any interests that apply.'};
    place(SpectrumCheckboxGroup.render(vertical),24,199,w,SpectrumCheckboxGroup.height(vertical));
    divider('s2g:check-group:divider',522);
    sub('s2g:check-group:horizontal-heading','Horizontal · responsive',542);
    const horizontal={key:'s2:check-group:horizontal',width:w,label:'Interests',
      orientation:'horizontal',selectedIds:selected,items:items.slice(0,3)};
    place(SpectrumCheckboxGroup.render(horizontal),24,611,w,SpectrumCheckboxGroup.height(horizontal));
    divider('s2g:check-group:disabled-rule',828);
    sub('s2g:check-group:disabled-heading','Unavailable group',846);
    const off={key:'s2:check-group:disabled',width:w,label:'Restricted interests',
      disabled:true,description:'This field is unavailable.',selectedIds:['design'],
      items:items.slice(0,2)};
    place(SpectrumCheckboxGroup.render(off),24,914,w,SpectrumCheckboxGroup.height(off));
  } else if(page==='segmented-control') {
    const w=Math.min(440,width-48);
    sub('s2g:segments:heading','Choose a view',133);
    const modes=[['list','List'],['grid','Grid'],['details','Details']];
    const selected=ui.str(data.adobeSegmentedView,'list');
    const items=modes.map(([id,label])=>({id,label,
      onClick:`lab:adobe:segments:${id}`}));
    place(SpectrumSegmentedControl.render({key:'s2:segments:views',width:w,selectedId:selected,
      items,isJustified:true}),24,203,w,40);
    detail('s2g:segments:result',`Active view: ${selected}`,275);
    divider('s2g:segments:divider',349);
    sub('s2g:segments:trackless-heading','Without track',371);
    place(SpectrumSegmentedControl.render({key:'s2:segments:trackless',width:w,
      hideTrack:true,selectedId:selected,items}),24,444,w,40);
    divider('s2g:segments:disabled-rule',532);
    sub('s2g:segments:disabled-heading','Disabled segment',555);
    place(SpectrumSegmentedControl.render({key:'s2:segments:disabled-item',width:w,
      selectedId:selected,items:items.map(item=>({...item,disabled:item.id==='grid'}))}),24,627,w,40);
    divider('s2g:segments:vertical-rule',712);
    sub('s2g:segments:vertical-head','Vertical orientation',734);
    place(SpectrumSegmentedControl.render({key:'s2:segments:vertical',width:200,
      orientation:'vertical',selectedId:selected,items}),24,810,200,132);
  } else if(page==='checkbox') {
    sub('s2g:check-sub','Default (not emphasized)',134);
    place(SpectrumCheckbox.render({key:'s2:checkbox',label:'Include metadata',width:280,
      selected:!!data.adobeChecked,onClick:'lab:adobe:checkbox'}),24,205,280,40);
    place(SpectrumCheckbox.render({key:'s2:indeterminate',label:'Mixed selection',width:280,
      indeterminate:true,onClick:'lab:adobe:checkbox'}),24,268,280,40);
    divider('s2g:checkbox-divider',361);
    sub('s2g:checkbox-disabled','Disabled',385);
    place(SpectrumCheckbox.render({key:'s2:checkbox:disabled',label:'Unavailable',width:280,
      selected:true,disabled:true}),24,456,280,40);
  } else if(page==='switch') {
    sub('s2g:switch-sub','Default',133);
    place(SpectrumSwitch.render({key:'s2:switch',label:'Automatic corrections',width:360,
      selected:!!data.adobeEnabled,onClick:'lab:adobe:switch'}),24,202,360,40);
    divider('s2g:switch-divider',293);
    sub('s2g:switch-disabled','Disabled',317);
    place(SpectrumSwitch.render({key:'s2:switch:disabled',label:'Unavailable',width:320,
      selected:true,disabled:true}),24,385,320,40);
    detail('s2g:switch-tip','Mouse click should never leave a keyboard focus outline.',492);
  } else if(page==='radio') {
    sub('s2g:radio-default-heading','Default (monochrome)',134);
    const x2=wide?col2:24, y2=wide?200:437;
    if(wide) sub('s2g:radio-accent-heading','Emphasized',134);
    for(let i=0;i<3;i++){
      const label=['Original','Edited','Proof'][i];
      place(SpectrumRadio.render({key:`s2:radio:${i}`,label,width:220,
        selected:ui.num(data.adobeRadio,0)===i,onClick:`lab:adobe:radio:${i}`}),24,205+i*49,220,38);
      place(SpectrumRadio.render({key:`s2:radio:emphasis:${i}`,label,width:220,emphasized:true,
        selected:ui.num(data.adobeRadio,0)===i,onClick:`lab:adobe:radio:${i}`}),x2,y2+i*49,220,38);
    }
    const disabledY=wide?410:667;
    divider('s2g:radio-divider',disabledY);
    sub('s2g:radio-disabled-heading','Disabled selection',disabledY+21);
    place(SpectrumRadio.render({key:'s2:radio:disabled',label:'Unavailable',width:240,
      selected:true,disabled:true}),24,disabledY+99,240,38);
  } else if(page==='text-field') {
    sub('s2g:field-sub','Default',133);
    place(SpectrumTextField.render({key:'s2:field',label:'Project name',width:fieldWidth,
      value:data.value||'',placeholder:'Untitled project',focused:data.focus==='adobe-field',
      caretOffset:data.caretOffset,onClick:'lab:adobe:focus:field',
      description:'The project name appears in exported files.'}),24,203,fieldWidth,100);
    divider('s2g:field-divider',342);
    sub('s2g:quiet-field-heading','Quiet and read-only',361);
    place(SpectrumTextField.render({key:'s2:quiet-field',label:'Read-only preview',value:'sample.png',
      width:fieldWidth,quiet:true,readOnly:true}),24,434,fieldWidth,74);
    divider('s2g:field-disabled-divider',548);
    sub('s2g:field-disabled-heading','Disabled',572);
    place(SpectrumTextField.render({key:'s2:field:disabled',label:'File name',
      width:fieldWidth,disabled:true,value:'Unavailable'}),24,646,fieldWidth,74);
  } else if(page==='search-field') {
    sub('s2g:search-sub','Editable search',133);
    place(SpectrumSearchField.render({key:'s2:search',label:'Search files',width:fieldWidth,
      value:data.adobeSearch||'',placeholder:'Search documents',
      caretOffset:data.adobeSearchCaretOffset,focused:data.focus==='adobe-search',
      onClick:'lab:adobe:focus:search',onClear:'lab:adobe:search:clear'}),24,205,fieldWidth,80);
    divider('s2g:search-disabled-divider',339);
    sub('s2g:search-disabled-heading','Disabled',365);
    place(SpectrumSearchField.render({key:'s2:search:disabled',label:'Search unavailable',
      width:fieldWidth,disabled:true,value:'Disabled'}),24,440,fieldWidth,80);
  } else if(page==='picker') {
    sub('s2g:picker-sub','Single selection',133);
    const choices=[{id:'low',label:'Low quality'},{id:'medium',label:'Balanced'},
      {id:'high',label:'High quality'},{id:'ultra',label:'Ultra (unavailable)',disabled:true}];
    const w=Math.min(370,fieldWidth);
    place(SpectrumPicker.render({key:'s2:picker',label:'Export quality',width:w,
      value:data.adobePicker||'medium',items:choices,open:data.overlay==='adobe-picker',
      onClick:'lab:adobe:picker:open'}),24,201,w,80);
    detail('s2g:picker-current',`Selected: ${ui.str(data.adobePicker,'medium')}`,317);
    divider('s2g:picker-disabled-divider',371);
    sub('s2g:picker-disabled-heading','Disabled',393);
    place(SpectrumPicker.render({key:'s2:picker:disabled',label:'Unavailable picker',
      width:w,disabled:true,placeholder:'Unavailable',items:[]}),24,470,w,80);
  } else if(page==='slider') {
    sub('s2g:slider-sub','Controlled value',133);
    const val=ui.num(data.adobeSlider,40),w=Math.min(510,width-52);
    place(SpectrumSlider.render({key:'s2:slider',label:'Export quality',width:w,
      value:val,min:0,max:100,valueLabel:`${Math.round(val)}%`,
      onClick:'lab:adobe:slider:focus'}),24,209,w,68);
    divider('s2g:slider-divider',332);
    detail('s2g:slider-help','Drag the track or use arrow keys; Shift changes the value by ten.',356);
  } else if(page==='tabs') {
    sub('s2g:tabs-sub','Horizontal tabs',133);
    const w=Math.min(590,width-48);
    place(SpectrumTabs.render({key:'s2:tabs',width:w,selected:data.adobeTab||'files',items:[
      {id:'files',label:'Files',onClick:'lab:adobe:tab:files'},
      {id:'activity',label:'Activity',onClick:'lab:adobe:tab:activity'},
      {id:'sharing',label:'Sharing',onClick:'lab:adobe:tab:sharing'}
    ]}),24,204,w,48);
    divider('s2g:tabs-divider',327);
    detail('s2g:tabs-help','The selected tab changes the current view immediately.',351);
  } else if(page==='side-navigation') {
    sub('s2g:nav-sub','Nested navigation',133);
    const expanded=!!data.adobeLibrariesExpanded;
    const items=[{id:'projects',label:'Projects',onClick:'lab:adobe:nav:projects'},
      {id:'recents',label:'Recents',onClick:'lab:adobe:nav:recents'},
      {id:'libraries',label:'Libraries',expanded,onClick:'lab:adobe:nav:expand',children:[
        {id:'shared',label:'Shared',onClick:'lab:adobe:nav:shared'},
        {id:'assets',label:'Assets',onClick:'lab:adobe:nav:assets'}]},
      {id:'archive',label:'Archive (unavailable)',disabled:true}];
    const w=Math.min(355,width-52);
    place(SpectrumSideNavigation.render({key:'s2:side-nav',width:w,items,
      selected:data.adobeNav||'projects'}),24,201,w,expanded?290:202);
  } else if(page==='accordion') {
    sub('s2g:accordion-heading','Disclosure items',133);
    const w=Math.min(560,width-52);
    // The host patches disclosure bounds in-place. No frame-varying JS model props.
    const progress=[1,0,0,0];
    const specs=[
      {id:'0',title:'General settings',content:'Project title, metadata and workspace defaults.'},
      {id:'1',title:'Sharing and access',content:'Manage invitations, public links and access permissions.'},
      {id:'2',title:'Unavailable section',content:'This section is unavailable.',disabled:true},
      {id:'3',title:'Export options',content:'Configure output resolution and file format.'}
    ];
    const items=specs.map((item,i)=>({...item,progress:ui.num(progress[i],i===0?1:0),
      contentHeight:86,onClick:`lab:adobe:accordion:${i}`}));
    const dynamicHeight=items.reduce((total,item)=>total+48+86*item.progress,1);
    // The component owns the exact height; do not pin it to a fabricated 480px card.
    place(SpectrumAccordion.render({key:'s2:accordion',width:w,items,
      density:'regular'}),24,202,w,dynamicHeight);
    divider('s2g:accordion-divider',202+dynamicHeight+34);
    sub('s2g:accordion-multiple-title','Expansion policy',202+dynamicHeight+51);
    place(SpectrumCheckbox.render({key:'s2:accordion:multiple',width:300,
      label:'Allow multiple expanded items',selected:!!data.adobeAccordionMultiple,
      onClick:'lab:adobe:accordion:multiple'}),24,202+dynamicHeight+111,300,40);
    detail('s2g:accordion-caption','Disabled rows do not open; regular uses flat dividers, quiet omits them.',
      202+dynamicHeight+176);
    divider('s2g:accordion:quiet-separator',202+dynamicHeight+226);
    sub('s2g:accordion:quiet-heading','Quiet (no dividers)',202+dynamicHeight+241);
    const quietY=202+dynamicHeight+306;
    place(SpectrumAccordion.render({key:'s2:accordion:quiet',width:w,quiet:true,
      density:'compact',items:[{id:'sample',title:'Appearance',content:'No borders or rounded containers.',
        expanded:true,contentHeight:68}]}),24,quietY,w,110);
    // Reserve scroll height for every panel opened. Scroll range stays stable
    // during the animation, so no layout/JS pass is required by disclosure.
    place(SpectrumFoundation.surface('s2g:accordion:scroll-reserve',w,1,[]),
      24,202+4*48+4*86+420,w,1);
  } else if(page==='breadcrumbs') {
    sub('s2g:breadcrumbs-heading','Ancestor path',133);
    const path=[{id:'home',label:'Creative Cloud'},
      {id:'projects',label:'Projects'}, {id:'assets',label:'Brand assets'},
      {id:'campaign',label:'Campaign'}, {id:'images',label:'Hero images'}];
    const current=Math.max(0,Math.min(path.length-1,ui.num(data.adobeBreadcrumbCurrent,4)));
    const w=Math.min(570,width-52);
    place(SpectrumBreadcrumbs.render({key:'s2:breadcrumbs',width:w,
      items:path.slice(0,current+1).map(item=>({...item,
        onClick:`lab:adobe:breadcrumbs:choose:${item.id}`})),
      collapse:true,onOverflow:'lab:adobe:breadcrumbs:overflow'}),24,201,w,44);
    divider('s2g:breadcrumbs-divider',300);
    sub('s2g:breadcrumbs-actions','Navigate the path',327);
    detail('s2g:breadcrumbs-explanation',
      'Breadcrumbs show ancestry, not open folders or sibling branches.',390);
    place(SpectrumButton.render({key:'s2:breadcrumbs:restore',variant:'secondary',
      label:'Restore full path',width:172,onClick:'lab:adobe:breadcrumbs:reset'}),
      24,457,172,40);
    place(SpectrumButton.render({key:'s2:breadcrumbs:open-tree',variant:'primary',
      outline:true,label:'Browse tree',width:150,onClick:'lab:nav:tree-view'}),
      215,457,150,40);
  } else if(page==='tree-view') {
    sub('s2g:tree-heading','Hierarchical collection',133);
    const expanded=Array.isArray(data.adobeTreeExpanded)?data.adobeTreeExpanded:
      ['home','projects','assets','campaign'];
    const selection=ui.str(data.adobeTreeSelected,'images');
    const currentPath=spectrumFilePath(SpectrumFileItems,selection)||[SpectrumFileItems[0]];
    const w=Math.min(570,width-52);
    place(SpectrumBreadcrumbs.render({key:'s2:tree:location',width:w,
      items:currentPath.map(item=>({id:item.id,label:item.label,
        onClick:`lab:adobe:tree:select:${item.id}`}))}),24,196,w,44);
    detail('s2g:tree-description','The path above follows the selected tree item.',266);
    const rows=SpectrumTreeView.visibleRows(SpectrumFileItems,expanded);
    const treeY=331;
    place(SpectrumTreeView.render({key:'s2:tree',width:w,
      items:SpectrumFileItems,expanded,selected:selection}),
      24,treeY,w,rows.length*40);
    const controlsY=treeY+rows.length*40+28;
    place(SpectrumButton.render({key:'s2:tree:expand-all',variant:'secondary',
      label:'Expand all',width:133,onClick:'lab:adobe:tree:expand-all'}),24,controlsY,133,40);
    place(SpectrumButton.render({key:'s2:tree:collapse-all',variant:'secondary',
      label:'Collapse all',width:143,onClick:'lab:adobe:tree:collapse-all'}),172,controlsY,143,40);
    detail('s2g:tree-help','Expand or collapse any branch repeatedly. Selected files retain their path.',
      controlsY+64);
  } else if(page==='badges') {
    sub('s2g:badges-heading','Semantic variants',133);
    const semantic=[['neutral','Neutral'],['accent','Accent'],['informative','Informative'],
      ['positive','Positive'],['notice','Notice'],['negative','Negative']];
    const stepX=wide?Math.min(230,Math.max(165,Math.floor((width-52)/3))):Math.max(140,Math.floor((width-52)/2));
    const cols=wide?3:2;
    semantic.forEach(([variant,label],i)=>{
      const x=24+(i%cols)*stepX,y=202+Math.floor(i/cols)*53;
      const bw=Math.min(stepX-10,Math.max(100,Math.ceil(label.length*10+46)));
      place(SpectrumBadge.render({key:`s2:badge:${variant}`,variant,label,width:bw,size:'M'}),x,y,bw,28);
    });
    const nextY=202+Math.ceil(semantic.length/cols)*53+30;
    divider('s2g:badges-divider',nextY);
    sub('s2g:badges-fill-heading','Fill styles',nextY+21);
    ['bold','subtle','outline'].forEach((fillStyle,i)=>{
      const x=24+(i%2)*Math.min(220,Math.floor((width-48)/2)),y=nextY+90+Math.floor(i/2)*50;
      place(SpectrumBadge.render({key:`s2:badge:fill:${fillStyle}`,variant:'positive',
        label:fillStyle,width:120,fillStyle,size:'L'}),x,y,120,32);
    });
    detail('s2g:badges-note','Badges describe status; use Tag for removable metadata.',nextY+230);
  } else if(page==='avatars') {
    sub('s2g:avatar-heading','Identity fallbacks',133);
    const variants=[{name:'Alex Chen',tone:0,size:24},{name:'Morgan Reed',tone:1,size:32},
      {name:'Taylor Wu',tone:2,size:40},{name:'Guest',guest:true,tone:3,size:48},
      {name:'Non-Latin fallback',guest:true,tone:4,size:56}];
    let xpos=24;
    variants.forEach((item,i)=>{
      place(SpectrumAvatar.render({key:`s2:avatar:${i}`,...item}),xpos,210,item.size,item.size);
      xpos+=item.size+16;
    });
    divider('s2g:avatar-rule',327);
    sub('s2g:avatar-disabled-heading','Disabled and outlined',350);
    place(SpectrumAvatar.render({key:'s2:avatar:disabled',name:'Alex Chen',size:56,
      disabled:true,showStroke:true}),24,431,56,56);
    place(SpectrumAvatar.render({key:'s2:avatar:outline',name:'Morgan Reed',size:56,
      showStroke:true}),100,431,56,56);
    detail('s2g:avatar-note','Unspecified images use initials or a neutral guest symbol.',533);
  } else if(page==='avatar-group') {
    sub('s2g:avatar-group-heading','Collaborators',133);
    const members=[{id:'alex',name:'Alex Chen',tone:0},
      {id:'morgan',name:'Morgan Reed',tone:1},
      {id:'taylor',name:'Taylor Wu',tone:2},
      {id:'sam',name:'Sam Ruiz',tone:3},
      {id:'guest',guest:true,tone:4},
      {id:'pat',name:'Pat Lane',tone:5},
      {id:'jordan',name:'Jordan Lee',tone:0}];
    const w=Math.min(500,width-52);
    place(SpectrumAvatarGroup.render({key:'s2:avatar-group:compact',size:32,
      items:members,maxVisible:4,label:'7 collaborators',labelWidth:160}),24,211,w,32);
    divider('s2g:avatar-group-rule',303);
    sub('s2g:avatar-group-more','Complete team',324);
    place(SpectrumAvatarGroup.render({key:'s2:avatar-group:all',size:40,
      items:members,maxVisible:7,label:'Project reviewers',labelWidth:180}),24,404,w,40);
    detail('s2g:avatar-group-note','Member identity is retained; overlap is visual only.',510);
  } else if(page==='dividers') {
    sub('s2g:dividers-heading','Horizontal sizes',133);
    const w=Math.min(500,width-52);
    ['S','M','L'].forEach((size,i)=>{
      detail(`s2g:divider-label:${size}`,`${size} divider`,200+i*77);
      place(SpectrumDivider.render({key:`s2:divider:${size}`,size,width:w}),
        24,235+i*77,w,({S:1,M:2,L:4})[size]);
    });
    divider('s2g:dividers-rule',463);
    sub('s2g:dividers-vertical-heading','Vertical orientation',485);
    const vH=115;
    ['S','M','L'].forEach((size,i)=>{
      const x=75+i*90;
      place(SpectrumDivider.render({key:`s2:divider:vertical:${size}`,
        size,orientation:'vertical',height:vH}),x,566,4,vH);
    });
    detail('s2g:dividers-note','These are flat geometry, not rounded rectangles.',732);
  } else if(page==='progress-circle') {
    sub('s2g:progress-circle-heading','Determinate',133);
    const value=ui.num(data.adobeSlider,40);
    ['S','M','L'].forEach((size,i)=>{
      const diameter=({S:24,M:40,L:64})[size];
      const x=24+i*115;
      place(SpectrumProgressCircle.render({key:`s2:progress-circle:${size}`,size,value}),
        x,220,diameter,diameter);
      place(SpectrumFoundation.text(`s2g:progress-circle:size:${size}`,size,x,292,50,24,
        {size:15,color:SpectrumPalette.muted}),x,292,50,24);
    });
    detail('s2g:progress-circle-value',`${Math.round(value)}% complete`,347);
    divider('s2g:progress-circle-rule',405);
    sub('s2g:progress-circle-indeterminate','Indeterminate',427);
    ['S','M','L'].forEach((size,i)=>{
      const d=({S:24,M:40,L:64})[size];
      place(SpectrumProgressCircle.render({key:`s2:progress-circle:spin:${size}`,
        size,indeterminate:true,phase:data.adobeProgressPhase}),24+i*115,516,d,d);
    });
    detail('s2g:progress-circle-note','Animation uses the Java host clock. No JS timer or new renderer.',634);
  } else if(page==='toggle-button') {
    sub('s2g:toggle-button-heading','Toggle selection',133);
    const entries=[['pin','Pin item'],['favorite','Favorite'],['filter','Filter']];
    const w=Math.min(178,Math.floor((width-58)/2));
    entries.forEach(([id,label],i)=>{
      const x=24+(i%2)*(w+16),y=212+Math.floor(i/2)*68;
      place(SpectrumToggleButton.render({key:`s2:toggle-button:${id}`,width:w,label,
        selected:Array.isArray(data.adobeToggles)&&data.adobeToggles.includes(id),
        onClick:`lab:adobe:toggle:${id}`}),x,y,w,40);
    });
    divider('s2g:toggle-button-rule',408);
    sub('s2g:toggle-button-style','Emphasized / quiet',430);
    const selected=Array.isArray(data.adobeToggles)&&data.adobeToggles.includes('pin');
    place(SpectrumToggleButton.render({key:'s2:toggle-button:emphasized',width:155,
      label:'Emphasized',selected,emphasized:true,onClick:'lab:adobe:toggle:pin'}),24,511,155,40);
    place(SpectrumToggleButton.render({key:'s2:toggle-button:quiet',width:155,
      label:'Quiet',selected,quiet:true,onClick:'lab:adobe:toggle:pin'}),196,511,155,40);
    divider('s2g:toggle-button-disabled-rule',612);
    sub('s2g:toggle-button-disabled-head','Unavailable',636);
    place(SpectrumToggleButton.render({key:'s2:toggle-button:disabled',width:162,
      label:'Disabled',selected:true,disabled:true}),24,707,162,40);
  } else if(page==='tags') {
    sub('s2g:tags-sub','Metadata',133);
    ['Design','Review','Published'].forEach((label,i)=>{
      const x=24+i*126;
      if(x+112<=width) place(SpectrumTag.render({key:`s2:tag:${i}`,label,width:108}),x,203,108,28);
    });
  } else if(page==='cards') {
    sub('s2g:cards-sub','Selectable card',133);
    const w=Math.min(350,width-50);
    place(SpectrumCard.render({key:'s2:card:one',title:'Design system',width:w,
      description:'Reusable interface assets and patterns',selected:!!data.adobeCardSelected,
      onClick:'lab:adobe:card:toggle'}),24,204,w,182);
    const sx=wide?24+w+22:24,sy=wide?204:426;
    place(SpectrumCard.render({key:'s2:card:two',variant:'quiet',title:'Review needed',width:w,
      description:'Awaiting approval from the team',status:'warning',
      onClick:'lab:adobe:card:review'}),sx,sy,w,182);
  } else if(page==='status-light') {
    sub('s2g:status-sub','Semantic variants',133);
    [['positive','Published'],['notice','Pending review'],
      ['negative','Failed'],['informative','Syncing']].forEach(([variant,label],i)=>{
      const x=wide?24+(i%2)*265:24,y=205+(wide?Math.floor(i/2):i)*57;
      place(SpectrumStatusLight.render({key:`s2:status:${variant}`,variant,label,width:235}),x,y,235,28);
    });
  } else if(page==='progress-bar') {
    sub('s2g:progress-sub','Determinate progress',133);
    const val=ui.num(data.adobeSlider,40),w=Math.min(515,width-52);
    place(SpectrumProgressBar.render({key:'s2:progress',label:'Preparing assets',width:w,
      value:val}),24,212,w,54);
    detail('s2g:progress-note','Adjust the same value on the Slider page.',322);
  } else if(page==='meter') {
    sub('s2g:meter-sub','Capacity',133);
    const val=ui.num(data.adobeSlider,40),w=Math.min(515,width-52);
    place(SpectrumMeter.render({key:'s2:meter',label:'Storage capacity',width:w,value:val,
      variant:val>80?'negative':val>55?'notice':'positive'}),24,212,w,54);
    detail('s2g:meter-note','Semantic status changes as the controlled value grows.',322);
  } else if(page==='menu') {
    sub('s2g:menu-sub','Action menu',133);
    place(SpectrumActionMenu.render({key:'s2:action-menu',width:168,label:'Actions',
      open:data.overlay==='adobe-menu',onClick:'lab:adobe:menu:open'}),24,204,168,36);
    divider('s2g:menu-divider',295);
    detail('s2g:menu-help','Click Actions for the overlay. Test keyboard navigation and outside click.',321);
  } else if(page==='alert-dialog') {
    sub('s2g:dialog-sub','Variants',133);
    [['confirmation','Confirmation'],['information','Information'],['warning','Warning'],
      ['destructive','Destructive'],['error','Error']].forEach(([variant,label],i)=>{
      const ncols=width>=600?3:2,col=i%ncols,row=Math.floor(i/ncols);
      const bw=Math.min(166,(width-55-(ncols-1)*12)/ncols);
      place(SpectrumButton.render({key:`s2:dialog-trigger:${variant}`,variant:'secondary',
        outline:true,label,width:bw,onClick:`lab:adobe:dialog:open:${variant}`}),
        24+col*(bw+12),206+row*62,bw,40);
    });
    divider('s2g:dialog-divider',429);
    sub('s2g:dialog-action-head','Modal result',451);
    place(SpectrumStatusLight.render({key:'s2:overlay:result',variant:'informative',
      label:`Last action: ${ui.str(data.adobeLastAction,'None')}`,width:440}),24,527,440,28);
  } else if(page==='toast') {
    sub('s2g:toast-heading','Notification variants',133);
    const specs=[['neutral','Neutral'],['informative','Informative'],
      ['positive','Positive'],['negative','Negative']];
    specs.forEach(([variant,label],i)=>{
      const x=wide?24+(i%2)*200:24;
      const y=205+(wide?Math.floor(i/2):i)*69;
      place(SpectrumButton.render({key:`s2:toast:trigger:${variant}`,
        variant:'secondary',label,width:174,onClick:`lab:adobe:toast:show:${variant}`}),
        x,y,174,40);
    });
    const markerY=wide?395:535;
    divider('s2g:toast-rule',markerY);
    sub('s2g:toast-placement','Placement',markerY+25);
    const placements=[['bottom','Bottom'],['bottom-end','Bottom end'],
      ['top','Top'],['top-end','Top end']];
    placements.forEach(([value,label],i)=>{
      const px=wide?24+(i%2)*188:24;
      const py=markerY+94+(wide?Math.floor(i/2):i)*52;
      place(SpectrumButton.render({key:`s2:toast:placement:${value}`,
        variant:value===ui.str(data.adobeToastPlacement,'bottom')?'accent':'secondary',
        width:164,label,onClick:`lab:adobe:toast:placement:${value}`}),px,py,164,40);
    });
    const behaviorY=markerY+94+(wide?2:4)*52+26;
    divider('s2g:toast:behavior-rule',behaviorY);
    sub('s2g:toast-behavior','Multiple notifications',behaviorY+21);
    detail('s2g:toast-caption','Up to three toasts remain visible. More notifications enter after previous ones close.',behaviorY+88);
    detail('s2g:toast-accessibility','Close, Retry and Escape play the exit transition. Toasts do not disappear on a timer.',behaviorY+126);
  } else if(page==='tooltip') {
    sub('s2g:tooltip:head','Tooltip variants',133);
    const examples=[['neutral','Neutral'],['informative','Informative'],['negative','Negative']];
    examples.forEach(([variant,label],i)=>{
      const x=wide?24+(i%2)*220:24;
      const y=205+(wide?Math.floor(i/2):i)*86;
      place(SpectrumButton.render({key:`s2:tooltip:trigger:${variant}`,label,
        variant:'secondary',width:188,onClick:`lab:adobe:tooltip:noop:${variant}`}),x,y,188,40);
    });
    const rule=wide?395:510;
    divider('s2g:tooltip:rule',rule);
    sub('s2g:tooltip:behavior','Placement and motion',rule+21);
    detail('s2g:tooltip:hint','Hover a trigger for 1.5 seconds. The tooltip flips below it near the top edge.',rule+85);
    detail('s2g:tooltip:motion','The popup slides and fades on both entry and exit, without intercepting clicks.',rule+122);
  } else if(page==='table') {
    sub('s2g:table:section','Files · sortable columns',133);
    const w=Math.max(270,Math.min(660,width-48));
    const source=[
      {id:'budget',name:'Budget.xlsx',type:'Spreadsheet',size:250},
      {id:'roadmap',name:'Roadmap.pdf',type:'Document',size:124},
      {id:'proposal',name:'Proposal.pptx',type:'Presentation',size:456},
      {id:'welcome',name:'Welcome.txt',type:'Text',size:8},
      {id:'locked',name:'Archive.zip',type:'Archive',size:991,disabled:true}
    ];
    const column=ui.str(data.adobeTableSort,'name'),direction=ui.str(data.adobeTableDirection,'asc');
    const rows=source.slice().sort((a,b)=>{
      const aa=a[column],bb=b[column],diff=typeof aa==='number'?aa-bb:String(aa).localeCompare(String(bb));
      return direction==='desc'?-diff:diff;
    }).map(item=>({...item,size:`${item.size} KB`}));
    const columns=[{id:'name',label:'File name',weight:2.4,sortable:true,rowHeader:true},
      {id:'type',label:'Type',weight:1.6,sortable:true},
      {id:'size',label:'Size',weight:1.1,sortable:true,align:'end'}];
    place(SpectrumTable.render({key:'s2:table:files',width:w,rows,columns,selectionMode:'multiple',
      selectedIds:data.adobeTableSelected||[],sortColumn:column,sortDirection:direction,
      rowAction:'lab:adobe:table:row:',sortAction:'lab:adobe:table:sort:',
      onSelectAll:'lab:adobe:table:all',density:ui.str(data.adobeTableDensity,'regular')}),
      24,202,w,SpectrumTable.height({rows,density:data.adobeTableDensity}));
    const controlsY=202+SpectrumTable.height({rows,density:data.adobeTableDensity})+22;
    detail('s2g:table:selection',`${(data.adobeTableSelected||[]).length} files selected`,controlsY);
    const choices=[['compact','Compact'],['regular','Regular'],['spacious','Spacious']];
    let x=24,y=controlsY+52;
    choices.forEach(([id,label])=>{
      const bw=118;if(x+bw>width-24){x=24;y+=51;}
      place(SpectrumButton.render({key:`s2:table:density:${id}`,label,width:bw,
        variant:data.adobeTableDensity===id?'accent':'secondary',
        onClick:`lab:adobe:table:density:${id}`}),x,y,bw,40);x+=bw+12;
    });
    divider('s2g:table:quiet:rule',y+74);
    sub('s2g:table:quiet:heading','Quiet · single selection',y+98);
    place(SpectrumTable.render({key:'s2:table:quiet',width:w,quiet:true,hideHeader:true,
      selectionMode:'single',selectionStyle:'highlight',selectedIds:[ui.str(data.adobeTableQuiet,'roadmap')],
      rowAction:'lab:adobe:table:quiet:',columns:[{id:'name',label:'Name',weight:2},
        {id:'type',label:'Type'}],rows:source.slice(0,3)}),24,y+164,w,3*44+2);
  } else if(page==='number-field') {
    sub('s2g:number:section','Quantity with in-field stepper',133);
    const w=Math.min(320,width-48),value=ui.num(data.adobeNumberValue,50);
    place(SpectrumNumberField.render({key:'s2:number:quantity',width:w,label:'Copies',
      value:ui.str(data.adobeNumberText,String(value)),focused:data.focus==='adobe-number',
      caretOffset:data.adobeNumberCaretOffset,minReached:value<=0,maxReached:value>=100,
      description:'Enter 0–100 copies.',onIncrement:'lab:adobe:number:inc',
      onDecrement:'lab:adobe:number:dec',onFocus:'lab:adobe:number:focus'}),24,207,w,110);
    divider('s2g:number:divider',350);
    sub('s2g:number:variants','Sizes and validation',375);
    place(SpectrumNumberField.render({key:'s2:number:readonly',width:Math.min(265,width-48),
      label:'Opacity',value:72,suffix:' %',readOnly:true,hideStepper:true,size:'S'}),24,444,
      Math.min(265,width-48),64);
    place(SpectrumNumberField.render({key:'s2:number:invalid',width:Math.min(310,width-48),
      label:'Required units',value:'',invalid:true,hideStepper:true,
      errorMessage:'Enter a numeric measurement.'}),24,550,Math.min(310,width-48),108);
    place(SpectrumNumberField.render({key:'s2:number:disabled',width:Math.min(310,width-48),
      label:'Unavailable',value:10,disabled:true}),24,694,Math.min(310,width-48),76);
  } else if(page==='combo-box') {
    sub('s2g:combo:section','Search a collection',133);
    place(SpectrumComboBox.render({key:'s2:combo:input',width:Math.min(370,width-48),
      label:'Asset type',placeholder:'Select an asset type',
      value:ui.str(data.adobeComboSelectedLabel,''),query:ui.str(data.adobeComboQuery,''),
      caretOffset:ui.num(data.adobeComboCaretOffset,0),
      open:data.overlay==='adobe-combo',description:'Start typing to filter the suggestions.',
      onOpen:'lab:adobe:combo:open'}),24,206,Math.min(370,width-48),104);
    divider('s2g:combo:divider',365);
    sub('s2g:combo:disabled:heading','Disabled and invalid states',393);
    place(SpectrumComboBox.render({key:'s2:combo:disabled',width:Math.min(320,width-48),
      label:'Unavailable',value:'PDF document',disabled:true}),24,470,Math.min(320,width-48),76);
    place(SpectrumComboBox.render({key:'s2:combo:invalid',width:Math.min(320,width-48),
      label:'Format',invalid:true,errorMessage:'Choose a supported format.',
      disabled:true}),24,582,Math.min(320,width-48),104);
  } else if(page==='calendar') {
    sub('s2g:calendar:title','Month navigation and selected date',133);
    const w=Math.min(348,width-48);
    place(SpectrumCalendar.render({key:'s2:calendar:standalone',width:w,
      year:ui.num(data.adobeCalendarYear,2026),month:ui.num(data.adobeCalendarMonth,10),
      selected:ui.str(data.adobeDateSingle,''),focusDay:ui.num(data.adobeCalendarFocusDay,0),
      minValue:'2026-01-01',maxValue:'2027-12-31',
      dayAction:'lab:adobe:calendar:day:',onPrevious:'lab:adobe:calendar:previous',
      onNext:'lab:adobe:calendar:next'}),24,207,w,314);
    divider('s2g:calendar:divider',560);
    detail('s2g:calendar:hint','Select a day or use previous / next month; unavailable days are disabled.',582);
  } else if(page==='date-picker') {
    sub('s2g:date:section','Single date',133);
    const w=Math.min(350,width-48);
    place(SpectrumDatePicker.render({key:'s2:date:single',width:w,label:'Start date',
      value:ui.str(data.adobeDateSingle,''),required:true,
      open:data.overlay==='adobe-date-single',onOpen:'lab:adobe:date:open:single',
      description:'Choose a date from the calendar.'}),24,207,w,105);
    divider('s2g:date:divider',365);
    sub('s2g:date:range:heading','Date range',388);
    const range=[ui.str(data.adobeDateRangeStart,''),ui.str(data.adobeDateRangeEnd,'')];
    place(SpectrumDatePicker.render({key:'s2:date:range',width:w,label:'Schedule',
      value:range.filter(Boolean).join(' – '),placeholder:'Choose a date range',
      open:data.overlay==='adobe-date-range',onOpen:'lab:adobe:date:open:range',
      description:'Select the start and end dates.'}),24,459,w,105);
    place(SpectrumButton.render({key:'s2:date:range:clear',label:'Clear range',width:144,
      variant:'secondary',onClick:'lab:adobe:date:clear'}),24,590,144,40);
    divider('s2g:date:disabled:divider',680);
    sub('s2g:date:disabled:heading','Unavailable state',700);
    place(SpectrumDatePicker.render({key:'s2:date:disabled',width:w,
      label:'Unavailable',disabled:true}),24,765,w,77);
  } else if(page==='color-area') {
    sub('s2g:ca:title','Saturation × brightness',134);
    const size=Math.min(270,Math.max(120,width-52));
    const hue=ui.num(data.adobeColorHue,250),sat=ui.num(data.adobeColorSaturation,62);
    const bright=ui.num(data.adobeColorBrightness,100);
    place(SpectrumColorArea.render({key:'s2:color:area',size,hue,saturation:sat,
      brightness:bright}),24,212,size,size);
    const sliderY=240+size;
    detail('s2g:ca:hint','Horizontal: saturation · Vertical: brightness',sliderY);
    place(SpectrumColorSlider.render({key:'s2:color:hue',width:Math.min(360,width-48),
      hue,saturation:sat,brightness:bright}),24,sliderY+50,Math.min(360,width-48),50);
    place(SpectrumColorSwatch.render({key:'s2:ca:preview',color:hsvHex(hue,sat,bright),
      size:'L',rounding:'default'}),24,sliderY+126,48,48);
    detail('s2g:ca:value',`${ui.str(data.adobeColorHex,'#7B61FF')} · H ${Math.round(hue)}° · S ${Math.round(sat)}% · B ${Math.round(bright)}%`,
      sliderY+186);
    divider('s2g:ca:rule',sliderY+242);
    sub('s2g:ca:disabled:title','Disabled',sliderY+255);
    place(SpectrumColorArea.render({key:'s2:ca:disabled',size:144,hue:45,
      saturation:65,brightness:90,disabled:true}),24,sliderY+316,144,144);
  } else if(page==='color-slider') {
    sub('s2g:cs:heading','Hue',134);
    const w=Math.min(390,width-48);
    const hue=ui.num(data.adobeColorHue,250),sat=ui.num(data.adobeColorSaturation,62),
      bright=ui.num(data.adobeColorBrightness,100),alpha=ui.num(data.adobeColorAlpha,100);
    place(SpectrumColorSlider.render({key:'s2:color:hue',width:w,hue,
      saturation:sat,brightness:bright}),24,214,w,50);
    detail('s2g:cs:hue:readout',`${Math.round(hue)}°`,284);
    divider('s2g:cs:divider',326);
    sub('s2g:cs:alpha:title','Opacity',350);
    place(SpectrumColorSlider.render({key:'s2:color:alpha',channel:'alpha',width:w,
      hue,saturation:sat,brightness:bright,alpha}),24,426,w,50);
    detail('s2g:cs:alpha:readout',`${Math.round(alpha)}%`,495);
    divider('s2g:cs:vertical:rule',555);
    sub('s2g:cs:vertical:title','Vertical hue',576);
    place(SpectrumColorSlider.render({key:'s2:color:vertical-hue',orientation:'vertical',
      length:180,hue,saturation:sat,brightness:bright}),24,649,50,180);
    place(SpectrumColorSwatch.render({key:'s2:cs:result',color:opacityHex(hue,sat,bright,alpha),
      size:'L'}),108,656,48,48);
    place(SpectrumColorSlider.render({key:'s2:cs:disabled',width:Math.min(210,w),
      hue:200,disabled:true}),108,760,Math.min(210,w),50);
  } else if(page==='color-wheel') {
    sub('s2g:cw:title','Radial hue and saturation',133);
    const size=Math.min(284,Math.max(156,width-52));
    const hue=ui.num(data.adobeColorHue,250),sat=ui.num(data.adobeColorSaturation,62),
      bright=ui.num(data.adobeColorBrightness,100);
    place(SpectrumColorWheel.render({key:'s2:color:wheel',size,hue}),24,210,size,size);
    if(size>=220){
      const inside=Math.floor(size*.43),center=24+(size-inside)/2;
      place(SpectrumColorArea.render({key:'s2:color:wheel-area',size:inside,
        hue,saturation:sat,brightness:bright}),center,210+(size-inside)/2,inside,inside);
    }
    detail('s2g:cw:hint','Drag the ring to adjust hue. Arrow keys adjust one degree.',size+249);
    divider('s2g:cw:rule',size+306);
    sub('s2g:cw:disabled:title','Disabled state',size+330);
    place(SpectrumColorWheel.render({key:'s2:cw:disabled',size:144,hue:270,disabled:true}),
      24,size+402,144,144);
  } else if(page==='color-field') {
    sub('s2g:cf:title','Hex color value',134);
    const w=Math.min(355,width-48);
    place(SpectrumColorField.render({key:'s2:color:field',width:w,label:'Color',
      value:ui.str(data.adobeColorInput,'#7B61FF'),focused:!!data.adobeColorFieldFocused,
      caretOffset:ui.num(data.adobeColorCaretOffset,0),
      description:'Type a #RRGGBB value to update the active color.',
      onFocus:'lab:adobe:color:field:focus'}),24,211,w,104);
    divider('s2g:cf:rule',368);
    sub('s2g:cf:variants','Read-only and unavailable',391);
    place(SpectrumColorField.render({key:'s2:cf:readonly',width:w,label:'Read-only',
      value:'#4069FD',readOnly:true}),24,466,w,76);
    place(SpectrumColorField.render({key:'s2:cf:disabled',width:w,label:'Disabled',
      value:'#E6E6E6',disabled:true}),24,579,w,76);
    place(SpectrumColorField.render({key:'s2:cf:invalid',width:w,label:'Validation',
      value:'#FFGG00',invalid:true,errorMessage:'Invalid hexadecimal characters.',
      readOnly:true}),24,690,w,104);
  } else if(page==='color-swatch-picker') {
    sub('s2g:csp:title','Choose a palette color',134);
    const swatches=['#7B61FF','#4069FD','#12A594','#53C94C','#F9CD46','#F7894A',
      '#EA5A5A','#DD72D4','#8B8B8B','#FFFFFF','#181818','#2685FF'];
    const items=swatches.map((color,i)=>({id:String(i),color,
      onClick:`lab:adobe:color:preset:${i}`}));
    const w=Math.min(374,width-48);
    place(SpectrumColorSwatchPicker.render({key:'s2:color:picker',width:w,
      items,selectedId:ui.str(data.adobeColorPreset,'0'),label:'Brand palette',
      size:'M',density:'regular'}),24,216,w,180);
    detail('s2g:csp:readout',`Active color: ${ui.str(data.adobeColorHex,'#7B61FF')}`,414);
    divider('s2g:csp:rule',472);
    sub('s2g:csp:density','Compact density · rounded swatches',495);
    place(SpectrumColorSwatchPicker.render({key:'s2:csp:compact',width:Math.min(255,w),
      size:'S',rounding:'full',density:'compact',label:'Available presets',
      selectedId:ui.str(data.adobeColorPreset,'0'),items}),24,567,Math.min(255,w),190);
  } else if(page==='range-slider') {
    sub('s2g:range:heading','Adjust a bounded interval',133);
    const w=Math.min(470,width-48);
    place(SpectrumRangeSlider.render({key:'s2:range-slider',width:w,
      label:'Creative asset size',startValue:ui.num(data.adobeRangeStart,25),
      endValue:ui.num(data.adobeRangeEnd,75),trackStyle:'thick',
      onClick:'lab:adobe:range:focus'}),24,210,w,72);
    detail('s2g:range:hint','Drag either thumb. Arrow keys move the active thumb; Shift selects the end thumb.',320);
    divider('s2g:range:rule',407);
    sub('s2g:range:disabled','Disabled and precise thumb',432);
    place(SpectrumRangeSlider.render({key:'s2:range:disabled',width:w,
      label:'Fixed interval',startValue:15,endValue:90,disabled:true,
      thumbStyle:'precise'}),24,504,w,72);
  } else if(page==='tag-group') {
    sub('s2g:tags:heading','Multiple selection and removable tags',133);
    const w=Math.min(490,width-48);
    const items=[['design','Design'],['photo','Photography'],['video','Video'],
      ['brand','Brand identity'],['export','Export'],['review','Needs review']].map(([id,label])=>({
      id,label,removable:true,onSelect:`lab:adobe:tag-group:select:${id}`,
      onRemove:`lab:adobe:tag-group:remove:${id}`}));
    place(SpectrumTagGroup.render({key:'s2:tags:edit',width:w,label:'Categories',
      items:items.filter(item=>(data.adobeTagItems||['design','photo','video','brand','export','review']).includes(item.id)),
      selectionMode:'multiple',emphasized:true,selectedIds:data.adobeTagSelected||[],
      description:'Use the close controls to remove tags.'}),24,203,w,230);
    divider('s2g:tags:rule',476);
    sub('s2g:tags:limit','Max rows and small density',498);
    place(SpectrumTagGroup.render({key:'s2:tags:limited',width:Math.min(270,w),size:'S',
      label:'Recent filters',maxRows:2,items:items.map(i=>({...i,removable:false})),
      selectionMode:'none'}),24,562,Math.min(270,w),142);
    place(SpectrumActionButton.render({key:'s2:tags:reset',label:'Restore tags',width:130,
      onClick:'lab:adobe:tag-group:reset'}),24,746,130,36);
  } else if(page==='toggle-button-group') {
    sub('s2g:tbg:heading','Compact · multiple selection',133);
    const w=Math.min(480,width-48);
    place(SpectrumToggleButtonGroup.render({key:'s2:tbg:multi',width:w,
      density:'compact',emphasized:true,selectionMode:'multiple',
      selectedIds:data.adobeToggleGroupMulti||[],items:[
        {id:'bold',label:'Bold',width:100,onClick:'lab:adobe:toggle-group:multi:bold'},
        {id:'italic',label:'Italic',width:100,onClick:'lab:adobe:toggle-group:multi:italic'},
        {id:'underline',label:'Underline',width:118,onClick:'lab:adobe:toggle-group:multi:underline'},
        {id:'strike',label:'Strike',width:100,disabled:true}
      ]}),24,206,w,40);
    divider('s2g:tbg:rule',320);
    sub('s2g:tbg:single:heading','Regular · single selection',345);
    place(SpectrumToggleButtonGroup.render({key:'s2:tbg:single',width:w,
      selectedIds:[ui.str(data.adobeToggleGroupSingle,'left')],justified:true,
      selectionMode:'single',items:[
        {id:'left',label:'Left',onClick:'lab:adobe:toggle-group:single:left'},
        {id:'center',label:'Center',onClick:'lab:adobe:toggle-group:single:center'},
        {id:'right',label:'Right',onClick:'lab:adobe:toggle-group:single:right'}
      ]}),24,418,w,40);
    divider('s2g:tbg:vertical:rule',530);
    sub('s2g:tbg:vertical:heading','Vertical, quiet and disabled',553);
    place(SpectrumToggleButtonGroup.render({key:'s2:tbg:vertical',width:182,
      orientation:'vertical',size:'S',quiet:true,selectedIds:['first'],items:[
        {id:'first',label:'Selection'}, {id:'second',label:'Unavailable',disabled:true}
      ]}),24,623,182,80);
  } else if(page==='select-box-group') {
    sub('s2g:sbg:heading','Choose a cloud provider · single selection',133);
    const w=Math.min(590,width-48);
    place(SpectrumSelectBoxGroup.render({key:'s2:sbg:single',width:w,
      label:'Cloud provider',selectedIds:[ui.str(data.adobeSelectBoxSingle,'aws')],
      items:[['aws','Amazon Web Services','Cloud infrastructure'],
        ['azure','Microsoft Azure','Business cloud services'],
        ['gcp','Google Cloud','Data and machine learning']].map(([id,label,description])=>({
          id,label,description,onClick:`lab:adobe:select-box:single:${id}`}))}),
      24,210,w,154);
    divider('s2g:sbg:rule',443);
    sub('s2g:sbg:multi:heading','Additional services · multiple selection',467);
    place(SpectrumSelectBoxGroup.render({key:'s2:sbg:multi',width:w,
      label:'Integrations',selectionMode:'multiple',selectedIds:data.adobeSelectBoxMulti||[],
      items:[['storage','Storage','Store assets securely'],
        ['cdn','Content delivery','Optimize global delivery'],
        ['review','Review','Unavailable service']].map(([id,label,description])=>({
          id,label,description,disabled:id==='review',onClick:`lab:adobe:select-box:multi:${id}`}))}),
      24,532,w,154);
  } else if(page==='disclosure') {
    sub('s2g:disclosure:section-heading','Independent disclosure sections',133);
    const w=Math.min(480,width-48);
    let y=207;
    for(const [id,title,content] of [
      ['requirements','System requirements','A current browser and an active Creative Cloud account are required.'],
      ['setup','Install and set up','Download, install, then choose a workspace for new projects.'],
      ['restricted','Unavailable settings','This section is disabled by an administrator.']]) {
      const expanded=(data.adobeDisclosures||['requirements']).includes(id);
      const disabled=id==='restricted';
      const panelHeight=88;
      const node=SpectrumDisclosure.render({key:`s2:disclosure:${id}`,width:w,title,content,
        expanded,disabled,quiet:id==='setup',panelHeight,
        onClick:`lab:adobe:disclosure:${id}`});
      const h=48+(expanded?panelHeight:0)+1;
      place(node,24,y,w,h);y+=h+14;
    }
    detail('s2g:disclosure:help','Each trigger retains its own expanded state. No per-frame script tick.',y+30);
  } else if(page==='list-view') {
    sub('s2g:lv:heading','Multiple selection · checkbox',133);
    const w=Math.min(490,width-48);
    const rows=[['ps','Photoshop','Image editing'],['ai','Illustrator','Vector graphics'],
      ['pdf','Acrobat','Document editing'],['locked','Unavailable item','Restricted access']];
    place(SpectrumListView.render({key:'s2:lv:files',width:w,selectionMode:'multiple',
      selectionStyle:'checkbox',selectedIds:data.adobeListViewSelected||['ps'],
      items:rows.map(([id,label,description])=>({id,label,description,
        disabled:id==='locked',onClick:`lab:adobe:list-view:toggle:${id}`}))}),24,205,w,260);
    divider('s2g:lv:separator',516);
    sub('s2g:lv:single-heading','Single selection · highlight',539);
    place(SpectrumListView.render({key:'s2:lv:single',width:w,quiet:true,density:'compact',
      selectionMode:'single',selectionStyle:'highlight',
      selectedIds:[ui.str(data.adobeListViewSingle,'grid')],items:[
        {id:'grid',label:'Grid view',onClick:'lab:adobe:list-view:single:grid'},
        {id:'list',label:'List view',onClick:'lab:adobe:list-view:single:list'},
        {id:'detail',label:'Details',onClick:'lab:adobe:list-view:single:detail'}]}),24,607,w,153);
  } else if(page==='card-view') {
    sub('s2g:cv:heading','Asset cards · responsive grid',133);
    const w=Math.min(720,width-48);
    const cards=[['brand','Brand identity','Updated today','#FF6A4C98'],
      ['campaign','Campaign hero','2 days ago','#FF2E7A76'],
      ['logo','Logo variants','Yesterday','#FFB56F40'],
      ['restricted','Private archive','Restricted','#FF53565C']];
    place(SpectrumCardView.render({key:'s2:cv:assets',width:w,selectionMode:'multiple',
      selectionStyle:'checkbox',selectedIds:data.adobeCardViewSelected||['brand'],
      items:cards.map(([id,label,description,color])=>({id,label,description,color,
        disabled:id==='restricted',onClick:`lab:adobe:card-view:toggle:${id}`}))}),
      24,204,w,410);
    divider('s2g:cv:separator',642);
    sub('s2g:cv:small','Compact · single selection',664);
    place(SpectrumCardView.render({key:'s2:cv:compact',width:w,size:'S',density:'compact',
      selectionMode:'single',selectionStyle:'highlight',
      selectedIds:[ui.str(data.adobeCardViewSingle,'recent')],items:[
        {id:'recent',label:'Recent',description:'6 assets',color:'#FF4B6AC7',onClick:'lab:adobe:card-view:single:recent'},
        {id:'shared',label:'Shared',description:'8 assets',color:'#FF487B68',onClick:'lab:adobe:card-view:single:shared'}]}),24,734,w,171);
  } else if(page==='radio-group') {
    sub('s2g:rg:heading','Grouped options',133);
    const w=Math.min(450,width-48);
    place(SpectrumRadioGroup.render({key:'s2:rg:quality',width:w,label:'Export quality',required:true,
      selectedId:ui.str(data.adobeRadioGroupSelected,'balanced'),
      description:'Choose the level of compression for the export.',items:[
        {id:'fast',label:'Fast',onClick:'lab:adobe:radio-group:fast'},
        {id:'balanced',label:'Balanced',onClick:'lab:adobe:radio-group:balanced'},
        {id:'high',label:'High quality',onClick:'lab:adobe:radio-group:high'},
        {id:'offline',label:'Unavailable',disabled:true}]}),24,207,w,245);
    divider('s2g:rg:divider',511);
    sub('s2g:rg:horizontal','Horizontal · emphasized',533);
    place(SpectrumRadioGroup.render({key:'s2:rg:orientation',width:w,label:'Orientation',
      orientation:'horizontal',emphasized:true,
      selectedId:ui.str(data.adobeRadioGroupOrientation,'landscape'),items:[
        {id:'portrait',label:'Portrait',onClick:'lab:adobe:radio-group:orientation:portrait'},
        {id:'landscape',label:'Landscape',onClick:'lab:adobe:radio-group:orientation:landscape'}]}),24,604,w,90);
    divider('s2g:rg:error-divider',738);
    sub('s2g:rg:error-heading','Invalid / disabled',762);
    place(SpectrumRadioGroup.render({key:'s2:rg:error',width:w,label:'Access level',invalid:true,
      errorMessage:'You must select an access level.',items:[
        {id:'reader',label:'Reader',disabled:true}, {id:'writer',label:'Writer',disabled:true}]}),24,830,w,185);
  } else if(page==='contextual-help') {
    sub('s2g:help:heading','Attached help and information',133);
    const p=SpectrumFoundation.dark;
    for(const [id,title,description,y] of [
      ['help','Export settings','Learn how to configure document exports.',217],
      ['info','Sharing permissions','Who can access this project?',316]]) {
      const w=Math.min(430,width-48);
      place(SpectrumFoundation.rect(`s2:help:${id}:surface`,0,0,w,72,8,p.surface,p.border,1),24,y,w,72);
      place(SpectrumFoundation.text(`s2:help:${id}:title`,title,0,0,w-80,28,
        {size:17,strong:true}),42,y+7,w-80,28);
      place(SpectrumFoundation.text(`s2:help:${id}:description`,description,0,0,w-80,24,
        {size:14,color:p.muted}),42,y+35,w-80,24);
      place(SpectrumContextualHelp.render({key:`s2:help:${id}`,variant:id,
        open:data.overlay==='adobe-context-help-'+id,
        onClick:`lab:adobe:context-help:open:${id}`}),24+w-43,y+23,26,26);
    }
    divider('s2g:help:rule',463);
    detail('s2g:help:hint','Click the question mark or info icon; Escape closes the popover.',490);
    place(SpectrumContextualHelp.render({key:'s2:help:disabled',disabled:true}),24,560,26,26);
    detail('s2g:help:disabled-hint','Unavailable contextual help',64,558);
  } else if(page==='link-button') {
    sub('s2g:lb:heading','Link buttons · navigational action',133);
    place(SpectrumLinkButton.render({key:'s2:lb:primary',label:'View documentation',width:205,
      onClick:'lab:adobe:link-button:docs'}),24,212,205,40);
    place(SpectrumLinkButton.render({key:'s2:lb:secondary',label:'Show details',width:165,
      variant:'secondary',onClick:'lab:adobe:link-button:details'}),24,280,165,40);
    divider('s2g:lb:divider',383);
    sub('s2g:lb:quiet','Quiet and disabled',403);
    place(SpectrumLinkButton.render({key:'s2:lb:quiet',label:'Learn more',quiet:true,width:162,
      onClick:'lab:adobe:link-button:learn'}),24,472,162,40);
    place(SpectrumLinkButton.render({key:'s2:lb:disabled',label:'Unavailable',disabled:true,
      width:172}),24,534,172,40);
    detail('s2g:lb:last',ui.str(data.adobeLastAction,'Click a link button to invoke the action router.'),603);
  } else if(page==='states') {
    sub('s2g:states-button-head','Buttons',133);
    place(SpectrumButton.render({key:'s2:states:button',variant:'accent',label:'Enabled',
      onClick:'lab:adobe:button:accent'}),24,199,134,40);
    place(SpectrumButton.render({key:'s2:states:disabled',variant:'accent',label:'Disabled',
      disabled:true}),181,199,134,40);
    divider('s2g:states-radio-divider',291);
    sub('s2g:states-radio-head','Radio',312);
    place(SpectrumRadio.render({key:'s2:states:radio:off',label:'First option',width:190,
      selected:ui.num(data.adobeStateRadio,0)===0,onClick:'lab:adobe:states:radio:0'}),24,374,190,40);
    place(SpectrumRadio.render({key:'s2:states:radio:on',label:'Second option',width:190,
      selected:ui.num(data.adobeStateRadio,0)===1,onClick:'lab:adobe:states:radio:1'}),24,426,190,40);
    place(SpectrumRadio.render({key:'s2:states:radio:disabled',label:'Unavailable',
      disabled:true,selected:true,width:220}),24,478,220,40);
    divider('s2g:states-checkbox-divider',557);
    sub('s2g:states-check-head','Checkbox',579);
    place(SpectrumCheckbox.render({key:'s2:states:check:off',selected:!!data.adobeStateCheckOff,
      onClick:'lab:adobe:states:check:off'}),24,649,40,40);
    place(SpectrumCheckbox.render({key:'s2:states:check:on',selected:data.adobeStateCheckOn!==false,
      onClick:'lab:adobe:states:check:on'}),89,649,40,40);
    place(SpectrumCheckbox.render({key:'s2:states:check:disabled',selected:true,disabled:true}),154,649,40,40);
    divider('s2g:states-switch-divider',730);
    sub('s2g:states-switch-head','Switch',751);
    place(SpectrumSwitch.render({key:'s2:states:switch:off',selected:!!data.adobeStateSwitchOff,
      onClick:'lab:adobe:states:switch:off'}),24,818,54,32);
    place(SpectrumSwitch.render({key:'s2:states:switch:on',selected:data.adobeStateSwitchOn!==false,
      onClick:'lab:adobe:states:switch:on'}),120,818,54,32);
    place(SpectrumSwitch.render({key:'s2:states:switch:disabled',selected:true,disabled:true}),218,818,54,32);
    divider('s2g:states-field-divider',912);
    sub('s2g:states-field-head','Text field',935);
    place(SpectrumTextField.render({key:'s2:states:textfield:disabled',label:'Unavailable field',
      width:fieldWidth,value:'Disabled',disabled:true}),24,1008,fieldWidth,74);
    divider('s2g:states-picker-divider',1117);
    sub('s2g:states-picker-head','Picker',1139);
    place(SpectrumPicker.render({key:'s2:states:picker:disabled',label:'Unavailable picker',
      width:Math.min(370,fieldWidth),disabled:true,items:[]}),24,1210,Math.min(370,fieldWidth),80);
  }
  return nodes;
}
