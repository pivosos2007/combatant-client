/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
import { SpectrumFoundation } from '../../api/kits/adobe/spectrum2/foundation.js';
import { SpectrumActionButton } from '../../api/kits/adobe/spectrum2/action-button.js';
import { SpectrumTextField } from '../../api/kits/adobe/spectrum2/text-field.js';
import { SpectrumListView } from '../../api/kits/adobe/spectrum2/list-view.js';

const action = id => `assignments:${id}`;
const at = (node, x, y, w, h) => ui.stack({
  key: `${node.key}:position`, style: ui.absolute(x, y, w, h), children: [node]
});
const content = (node, w, h) => ui.stack({
  key: `${node.key}:content`, style: {width: w, height: h, shrink: 0}, children: [node]
});

// A bindings editor is scoped to the config card which opened it. No profile picker.
export function render(ctx) {
  const p = ctx.props, w = ctx.width, h = ctx.height;
  const f = SpectrumFoundation, colors = f.colors(p.colors);
  const t = k => p.labels[k];
  const text = (id, value, x, y, width, height, size = 18, strong = false, wrap = false) =>
    f.text(`assignments:${id}`, value, x, y, width, height,
      {size, strong, color: colors.text, ...(wrap ? {wrap: true, maxLines: 3} : {})});
  const button = (id, label, x, y, width, height = 42, fontSize = 17) => at(
    SpectrumActionButton.render({key: `assignments:${id}`, label, width,
      height, fontSize, size: 'L', colors, onClick: action(id)}), x, y, width, height);

  const gap = 28, left = Math.floor((w-gap)*.48), rx = left+gap, right = w-rx;
  const fieldY = Math.min(184, Math.max(162, h-193));
  const quickY = fieldY+57, statusY = quickY+47, footerY = h-44;
  const third = (right-16)/3;
  const listY = 130;
  const rowH = 75;
  const children = [
    button('back', t('back'), 0, 0, 104, 42),
    text('title', t('title'), 122, 0, w-122, 42, 21, true),
    text('profile_name', p.profileName, 0, 50, w, 31, 21, true),
    text('connections', t('connections'), 0, 91, left, 27, 18, true),
    ui.scroll({key:'assignments:list', style:ui.absolute(0,listY,left,h-listY,{overflow:'scroll-y'}),
      wheelStep:48,children:[
        content(SpectrumListView.render({key:'assignments:rows',width:left-8,quiet:true,
          density:'spacious', titleSize:19, descriptionSize:16,
          selectionMode:'single',selectedIds:[p.context],colors,
          items:p.rows.map(row=>({id:row.context,label:row.label,description:row.description,
            onClick:action(`edit:${row.context}`)}))}),left-8,Math.max(76,12+p.rows.length*rowH)),
        text('empty',t('empty_profile'),8,12,left-16,70,17,false,true)
      ]}),
    text('edit_mode',p.pendingRemoval?t('deleting_assignment'):
      p.editingExisting?t('editing_assignment'):t('new_assignment'),rx,91,right,27,18,true),
    text('kind',t(p.local?'local':'server'),rx,117,right,23,17),
    text('address_label',t(p.local?'world_folder':'server_address'),rx,fieldY-28,right,23,17,true),
    at(SpectrumTextField.render({key:'assignments:address',width:right,labelHidden:true,
      fontSize:20,fieldHeight:50,disabled:p.pendingRemoval,
      value:p.fieldEditing?'':p.value,
      placeholder:p.fieldEditing?'':t(p.local?'world_hint':'server_hint'),
      colors,onClick:action('address')}),rx,fieldY,right,50),
    button('current_world',p.currentLabel,rx,quickY,(right-10)/2,42,16),
    button('new_server',t('new_server'),rx+(right+10)/2,quickY,(right-10)/2,42,16),
    text('assigned',p.assignedSummary,rx,statusY,right,34,16,false,true),
    button('remove',t('remove'),rx,footerY,third,42,16),
    button('cancel',t('cancel'),rx+third+8,footerY,third,42,16),
    button('save',p.saveLabel,rx+2*(third+8),footerY,third,42,16)
  ];
  return ui.stack({key:'assignments:root',style:ui.absolute(0,0,w,h,{overflow:'hidden'}),children});
}
