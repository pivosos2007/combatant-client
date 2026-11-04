/* Combatant Client (GPL-3.0).
 * Main Settings / System: configuration directory, using production Spectrum 2 primitives.
 * All file operations are owned by the Java host, never by the UI script.
 */
import { SpectrumFoundation } from '../../api/kits/adobe/spectrum2/foundation.js';
const S2 = SpectrumFoundation;
import { SpectrumButton } from '../../api/kits/adobe/spectrum2/button.js';

const num = (value, fallback) => Math.max(0, ui.num(value, fallback));

function string(p, key, fallback = '') { return ui.str(p[key], fallback); }

function field(key, value, x, y, width, height, colors, onClick, disabled) {
  const children = [
    S2.rect(`${key}:surface`, 0, 0, width, height, 6,
      disabled ? colors.disabledSurface : colors.field, colors.border, 1),
    S2.rect(`${key}:hover`, 0, 0, width, height, 6, '#00000000', undefined, 0, {
      stateSource: 'parent',
      fillReactive: {base:colors.text, alphaBase:0, alphaTerms:disabled?{}:{hover:.06, press:.10}}
    }),
    S2.text(`${key}:path`, value, 11, 7, width - 22, height - 14,
      {size:14, color:disabled?colors.disabled:colors.text, wrap:true, maxLines:3})
  ];
  return ui.button({key, class:ui.abs(x,y,width,height,S2.cursor(disabled)),
    stateValues:{disabled}, stateMotions:{hover:{durationMs:130,easing:'out-cubic'},
      press:{durationMs:100,easing:'out-cubic'}},
    ...(disabled?{}:{onClick}), children});
}

function action(key, label, x, y, w, onClick, disabled, primary=false) {
  const node = SpectrumButton.render({key,label,width:w,size:'XS',variant:primary?'accent':'secondary',
    outline:!primary,disabled,onClick});
  return {...node,style:ui.absolute(x,y,w,32)};
}

function pathDescription(key, label, path, y, width, colors) {
  return [
    S2.text(`${key}:label`,label,0,y,width,20,{size:14,strong:true,color:colors.muted}),
    S2.text(`${key}:value`,path,0,y+21,width,46,
      {size:14,color:colors.text,wrap:true,maxLines:2})
  ];
}

export function buildTemplate(ctx) {
  const p=ctx.props||{};
  const w=Math.max(190,num(p.width,270));
  const colors=S2.colors(p.colors||{});
  const disabled=!!p.busy || !!p.pending;
  const confirming=!!p.confirming && !p.pending;
  const pending=!!p.pending;
  const error=string(p,'error');
  const base=186;
  const continuation=confirming?204:pending?124:0;
  const total=base+continuation+(error?65:0);
  const gap=8;
  const leftW=Math.max(80,Math.floor((w-gap)/2));
  const rightW=Math.max(80,w-leftW-gap);
  const nodes=[
    S2.text('configdir:title',string(p,'title'),0,0,w,25,{size:18,strong:true,color:colors.text}),
    S2.text('configdir:current-label',string(p,'currentLabel'),0,25,w,21,
      {size:14,color:colors.muted}),
    field('configdir:current-path',string(p,'current'),0,48,w,64,colors,'configdir:change',disabled),
    action('configdir:change-button',string(p,'change'),0,121,leftW,'configdir:change',disabled),
    action('configdir:reset-button',string(p,'reset'),leftW+gap,121,rightW,
      'configdir:restore',disabled || !!p.defaultActive),
    S2.text('configdir:location-hint',string(p,'hint'),0,154,w,30,
      {size:13,color:colors.muted,wrap:true,maxLines:2})
  ];
  if(confirming){
    nodes.push(S2.rect('configdir:confirm-line',0,182,w,1,0,colors.border));
    nodes.push(S2.text('configdir:confirm-title',string(p,'confirmTitle'),0,188,w,25,
      {size:17,strong:true,color:colors.text}));
    nodes.push(...pathDescription('configdir:from',string(p,'fromLabel'),string(p,'current'),217,w,colors));
    nodes.push(...pathDescription('configdir:to',string(p,'toLabel'),string(p,'destination'),282,w,colors));
    nodes.push(action('configdir:confirm',string(p,'confirm'),0,350,leftW,
      'configdir:confirm',!!p.busy,true));
    nodes.push(action('configdir:cancel',string(p,'cancel'),leftW+gap,350,rightW,
      'configdir:cancel',!!p.busy));
  } else if(pending){
    nodes.push(S2.rect('configdir:pending-line',0,182,w,1,0,colors.border));
    nodes.push(S2.text('configdir:pending-title',string(p,'pendingTitle'),0,191,w,22,
      {size:15,strong:true,color:colors.text,wrap:true,maxLines:2}));
    nodes.push(S2.text('configdir:pending-destination',string(p,'destination'),0,216,w,46,
      {size:14,color:colors.muted,wrap:true,maxLines:2}));
    nodes.push(action('configdir:cancel-pending',string(p,'cancelPending'),0,270,w,
      'configdir:cancel-pending',!!p.busy || !p.canCancel));
  }
  if(error) {
    nodes.push(S2.text('configdir:error-title',string(p,'failureTitle'),0,base+continuation+5,w,18,
      {size:15,strong:true,color:colors.danger}));
    nodes.push(S2.text('configdir:error',error,0,base+continuation+24,w,39,
      {size:14,color:colors.danger,wrap:true,maxLines:2}));
  }
  return ui.stack({key:'configdir:root',class:ui.abs(0,0,w,total),children:nodes});
}
