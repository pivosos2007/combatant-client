/* Combatant Client (GPL-3.0), Copyright (c) 2026 pivosos2007.
 * Spectrum 2 Calendar (Gregorian month grid). Navigation/selection use host actions,
 * with no JavaScript timer or current-time dependency. */
import { SpectrumFoundation } from './foundation.js';
export class SpectrumCalendar {
  static render(props={}) {
    const key=ui.str(props.key,'s2:calendar'),p=SpectrumFoundation.colors(props.colors);
    const width=Math.max(268,ui.num(props.width,348));
    const year=Math.max(1,Math.min(9999,Math.floor(ui.num(props.year,2026))));
    const month=Math.max(1,Math.min(12,Math.floor(ui.num(props.month,1))));
    const monday=props.firstDayOfWeek!=='sun';
    const dayNames=monday?['Mo','Tu','We','Th','Fr','Sa','Su']:['Su','Mo','Tu','We','Th','Fr','Sa'];
    const months=['January','February','March','April','May','June','July','August','September',
      'October','November','December'];
    const pad=12,gridW=width-pad*2,cellW=gridW/7,rowH=38;
    // Pure Gregorian arithmetic: no Date allocation and no locale/timezone access in JS.
    const leap=year%4===0&&(year%100!==0||year%400===0);
    const days=month===2?(leap?29:28):[4,6,9,11].includes(month)?30:31;
    const shifts=[0,3,2,5,0,3,5,1,4,6,2,4];
    const y0=month<3?year-1:year;
    const first=(y0+Math.floor(y0/4)-Math.floor(y0/100)+Math.floor(y0/400)
      +shifts[month-1]+1)%7; // Sunday=0
    const offset=monday?(first+6)%7:first;
    const selected=ui.str(props.selected,'');
    const start=ui.str(props.rangeStart,''),end=ui.str(props.rangeEnd,'');
    const min=ui.str(props.minValue,'0000-01-01'),max=ui.str(props.maxValue,'9999-12-31');
    const prevMonth=year===1&&month===1?'0000-00':`${month===1?year-1:year}-${String(month===1?12:month-1).padStart(2,'0')}`;
    const nextMonth=year===9999&&month===12?'9999-13':`${month===12?year+1:year}-${String(month===12?1:month+1).padStart(2,'0')}`;
    const previousDisabled=!!props.disabled||prevMonth<min.slice(0,7);
    const nextDisabled=!!props.disabled||nextMonth>max.slice(0,7);
    const focus=ui.num(props.focusDay,0),disabled=!!props.disabled;
    const children=[
      SpectrumFoundation.text(`${key}:title`,`${months[month-1]} ${year}`,54,4,width-108,42,
        {size:19,strong:true,align:'center'}),
      ui.button({key:`${key}:previous`,class:ui.abs(8,7,35,35,
        SpectrumFoundation.cursor(previousDisabled)),stateValues:{disabled:previousDisabled},
        ...(previousDisabled?{}:{onClick:props.onPrevious}),children:[
          ui.svg({key:`${key}:previous:icon`,asset:'chevron-left',tint:previousDisabled?p.disabled:p.text,
            interactive:false,class:ui.abs(9,8,18,18)})]}),
      ui.button({key:`${key}:next`,class:ui.abs(width-43,7,35,35,
        SpectrumFoundation.cursor(nextDisabled)),stateValues:{disabled:nextDisabled},
        ...(nextDisabled?{}:{onClick:props.onNext}),children:[
          ui.svg({key:`${key}:next:icon`,asset:'chevron-right',tint:disabled?p.disabled:p.text,
            interactive:false,class:ui.abs(9,8,18,18)})]})
    ];
    dayNames.forEach((name,i)=>children.push(SpectrumFoundation.text(`${key}:weekday:${i}`,
      name,pad+i*cellW,53,cellW,25,{size:14,color:p.muted,align:'center'})));
    for(let slot=0;slot<42;slot++){
      const day=slot-offset+1,row=Math.floor(slot/7),col=slot%7;
      if(day<1||day>days)continue;
      const iso=`${year.toString().padStart(4,'0')}-${month.toString().padStart(2,'0')}-${day.toString().padStart(2,'0')}`;
      const unavailable=disabled||iso<min||iso>max;
      const isSelected=selected===iso||start===iso||end===iso;
      const inRange=start&&end&&iso>start&&iso<end;
      const x=pad+col*cellW,y=82+row*rowH;
      children.push(ui.button({key:`${key}:day:${day}`,class:ui.abs(x,y,cellW,rowH,
        SpectrumFoundation.cursor(unavailable)),stateValues:{disabled:unavailable,selected:isSelected},
        stateMotions:{hover:{durationMs:100,easing:'out-cubic'},press:{durationMs:80,easing:'out-cubic'}},
        ...(unavailable?{}:{onClick:`${ui.str(props.dayAction,'')}${day}`}),children:[
          ...(inRange?[SpectrumFoundation.rect(`${key}:day:${day}:range`,0,4,cellW,rowH-8,0,p.selected)]:[]),
          ...(isSelected?[SpectrumFoundation.rect(`${key}:day:${day}:selected`,
            (cellW-32)/2,3,32,32,16,p.accent)]:[]),
          SpectrumFoundation.rect(`${key}:day:${day}:hover`,(cellW-32)/2,3,32,32,16,
            '#00000000',undefined,0,{stateSource:'parent',fillReactive:{base:p.text,alphaBase:0,
              alphaTerms:unavailable?{}:{hover:.10,press:.17}}}),
          SpectrumFoundation.text(`${key}:day:${day}:text`,String(day),0,0,cellW,rowH,
            {size:16,strong:isSelected,align:'center',color:unavailable?p.disabled:isSelected?p.onAccent:p.text}),
          ...(focus===day?[SpectrumFoundation.rect(`${key}:day:${day}:focus`,(cellW-34)/2,2,34,34,17,
            '#00000000',p.focus,2)]:[])
        ]}));
    }
    return ui.stack({key,class:ui.abs(0,0,width,314),children});
  }
}
