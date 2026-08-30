// Combatant animation registry/runtime. Concrete animations live one class per file under animations/.
(() => {
  const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
  const ease=(code,t)=>code===1?t*t:code===2?1-(1-t)*(1-t):code===3?(t<.5?2*t*t:1-Math.pow(-2*t+2,2)/2):t;
  const sample=(channel,time,fallback)=>{
    if(!channel||channel.length<3)return fallback;
    if(time<=channel[0]){const ft=channel[0],fv=channel[1];if(ft<=1e-8)return fv;const t=ease(channel[2]|0,clamp(time/ft,0,1));return fallback+(fv-fallback)*t;}
    const n=channel.length;if(time>=channel[n-3])return channel[n-2];
    let lo=0,hi=n/3-1;while(lo+1<hi){const mid=(lo+hi)>>1;if(time<channel[mid*3])hi=mid;else lo=mid;}
    const ai=lo*3,bi=hi*3,dt=channel[bi]-channel[ai];let t=dt>1e-8?(time-channel[ai])/dt:1;t=ease(channel[bi+2]|0,clamp(t,0,1));
    return channel[ai+1]+(channel[bi+1]-channel[ai+1])*t;
  };
  class RigKeyframeClip {
    constructor(name,length,mode,source,tracks){this.name=name;this.length=Math.max(1e-4,Number(length)||1e-4);this.mode=mode;this.source=source;this.tracks=tracks.map(track=>Object.freeze({...track,b:playerRig.bone(track.b)}));Object.freeze(this.tracks);Object.freeze(this);}
    time(raw){raw=Number.isFinite(raw)?raw:0;if(this.mode==='loop')return ((raw%this.length)+this.length)%this.length;return clamp(raw,0,this.length);}
    apply(rig,rawTime,weight=1,options=null){weight=clamp(Number(weight)||0,0,2);if(weight<=1e-5)return false;const t=this.time(rawTime),mirror=!!options?.mirror;for(let i=0;i<this.tracks.length;i++){const tr=this.tracks[i];let bone=tr.b;if(mirror)bone=globalThis.RigAnimationLibrary.mirrorBone(bone);let rx=sample(tr.rx,t,0),ry=sample(tr.ry,t,0),rz=sample(tr.rz,t,0);let px=sample(tr.px,t,0),py=sample(tr.py,t,0),pz=sample(tr.pz,t,0);if(mirror){ry=-ry;rz=-rz;px=-px;}if(rx||ry||rz)rig.rotate(bone,rx*weight,ry*weight,rz*weight);if(px||py||pz)rig.move(bone,px*weight,py*weight,pz*weight);if(tr.sx||tr.sy||tr.sz){const sx=sample(tr.sx,t,1),sy=sample(tr.sy,t,1),sz=sample(tr.sz,t,1);rig.setScale(bone,1+(sx-1)*weight,1+(sy-1)*weight,1+(sz-1)*weight);}}return true;}
    finished(rawTime){return this.mode==='once'&&rawTime>=this.length;}
  }
  class ProceduralAnimation {constructor(name,fn){this.name=name;this.fn=fn;Object.freeze(this);}apply(rig,time,weight=1,options=null){const context=globalThis.__combatant_player_rig_context||Object.freeze({});return this.fn(context,rig,time,weight,options)||false;}}
  const clips=Object.create(null),procedural=Object.create(null),aliases=Object.create(null);
  aliases["motion:boat_forward"]="motion:boat_forward/boat_forward";
  aliases["motion:boat_backward"]="motion:boat_forward/boat_backward";
  aliases["motion:boat_idle"]="motion:boat_idle/boat_idle";
  aliases["motion:boat_turn_right"]="motion:boat_left_paddle/boat_turn_right";
  aliases["motion:boat_left_paddle"]="motion:boat_left_paddle/boat_left_paddle";
  aliases["motion:boat_turn_left"]="motion:boat_right_paddle/boat_turn_left";
  aliases["motion:boat_right_paddle"]="motion:boat_right_paddle/boat_right_paddle";
  aliases["motion:bounce_fall"]="motion:bounce_fall/bounce_fall";
  aliases["motion:bounce_jump"]="motion:bounce_jump/bounce_jump";
  aliases["motion:idle_hurt"]="motion:climbing/idle_hurt";
  aliases["motion:climbing"]="motion:climbing/climbing";
  aliases["motion:climbing_idle"]="motion:climbing_idle/climbing_idle";
  aliases["motion:climbing_idle_mit"]="motion:climbing_idle_mit/climbing_idle_mit";
  aliases["motion:crawl_idle"]="motion:crawl_idle/crawl_idle";
  aliases["motion:crawling"]="motion:crawling/crawling";
  aliases["motion:drinking"]="motion:drinking/drinking";
  aliases["motion:eating"]="motion:eating/eating";
  aliases["motion:edge_idle"]="motion:edge_idle/edge_idle";
  aliases["motion:elytra_fly"]="motion:elytra_fly/elytra_fly";
  aliases["motion:elytra_fly_mit"]="motion:elytra_fly_mit/elytra_fly_mit";
  aliases["motion:fall_first"]="motion:fall_first/fall_first";
  aliases["motion:fall_first_mit"]="motion:fall_first_mit/fall_first_mit";
  aliases["motion:fall_second"]="motion:fall_second/fall_second";
  aliases["motion:fall_second_mit"]="motion:fall_second_mit/fall_second_mit";
  aliases["motion:falling"]="motion:falling/falling";
  aliases["motion:fence_idle"]="motion:fence_idle/fence_idle";
  aliases["motion:fence_walk"]="motion:fence_walk/fence_walk";
  aliases["motion:fence_turn_right"]="motion:fence_walk/fence_turn_right";
  aliases["motion:flint_and_steel"]="motion:flint_and_steel/flint_and_steel";
  aliases["motion:flint_and_steel_sneak"]="motion:flint_and_steel_sneak/flint_and_steel_sneak";
  aliases["motion:hold_crossbow"]="motion:hold_crossbow/hold_crossbow";
  aliases["motion:hold_lantern"]="motion:hold_lantern/hold_lantern";
  aliases["motion:hold_lantern_both"]="motion:hold_lantern/hold_lantern_both";
  aliases["motion:hold_light"]="motion:hold_light/hold_light";
  aliases["motion:hold_spear"]="motion:hold_spear/hold_spear";
  aliases["motion:horse_riding"]="motion:horse_riding/horse_riding";
  aliases["motion:horse_riding_idle"]="motion:horse_riding_idle/horse_riding_idle";
  aliases["motion:idle"]="motion:idle/idle";
  aliases["motion:idle_item_rose_intro"]="motion:idle_item_rose_intro/idle_item_rose_intro";
  aliases["motion:idle_item_rose_loop"]="motion:idle_item_rose_loop/idle_item_rose_loop";
  aliases["motion:idle_special_1"]="motion:idle_special_1/idle_special_1";
  aliases["motion:idle_special_2"]="motion:idle_special_2/idle_special_2";
  aliases["motion:jump_first"]="motion:jump_first/jump_first";
  aliases["motion:jump_first_mit"]="motion:jump_first_mit/jump_first_mit";
  aliases["motion:jump_second"]="motion:jump_second/jump_second";
  aliases["motion:jump_second_mit"]="motion:jump_second_mit/jump_second_mit";
  aliases["motion:jump_slow_first"]="motion:jump_slow_first/jump_slow_first";
  aliases["motion:jump_slow_second"]="motion:jump_slow_second/jump_slow_second";
  aliases["motion:landing"]="motion:landing/landing";
  aliases["motion:punch"]="motion:punch/punch";
  aliases["motion:release_bow"]="motion:release_bow/release_bow";
  aliases["motion:release_crossbow"]="motion:release_crossbow/release_crossbow";
  aliases["motion:release_trident"]="motion:release_trident/release_trident";
  aliases["motion:rolling"]="motion:rolling/rolling";
  aliases["motion:running"]="motion:running/running";
  aliases["motion:running_mit"]="motion:running_mit/running_mit";
  aliases["motion:slow_falling"]="motion:slow_falling/slow_falling";
  aliases["motion:sneak_idle"]="motion:sneak_idle/sneak_idle";
  aliases["motion:sneak_walk"]="motion:sneak_walk/sneak_walk";
  aliases["motion:sprint_stop"]="motion:sprint_stop/sprint_stop";
  aliases["motion:sprint_stop_mit"]="motion:sprint_stop_mit/sprint_stop_mit";
  aliases["motion:step_down_left"]="motion:step_down_left/step_down_left";
  aliases["motion:step_down_right"]="motion:step_down_right/step_down_right";
  aliases["motion:swim_idle"]="motion:swim_idle/swim_idle";
  aliases["motion:swimming"]="motion:swimming/swimming";
  aliases["motion:sword_swing_first"]="motion:sword_swing_first/sword_swing_first";
  aliases["motion:sword_swing_second"]="motion:sword_swing_second/sword_swing_second";
  aliases["motion:sword_swing_sneak_first"]="motion:sword_swing_sneak_first/sword_swing_sneak_first";
  aliases["motion:sword_swing_sneak_second"]="motion:sword_swing_sneak_second/sword_swing_sneak_second";
  aliases["motion:tool_axe_swing"]="motion:tool_axe_swing/tool_axe_swing";
  aliases["motion:tool_hoe_swing"]="motion:tool_hoe_swing/tool_hoe_swing";
  aliases["motion:tool_pickaxe_swing"]="motion:tool_pickaxe_swing/tool_pickaxe_swing";
  aliases["motion:tool_shovel_swing"]="motion:tool_shovel_swing/tool_shovel_swing";
  aliases["motion:tool_sword_swing"]="motion:tool_sword_swing/tool_sword_swing";
  aliases["motion:tool_trident_swing"]="motion:tool_trident_swing/tool_trident_swing";
  aliases["motion:turn_right"]="motion:turn_left/turn_right";
  aliases["motion:turn_left"]="motion:turn_left/turn_left";
  aliases["motion:use_bow"]="motion:use_bow/use_bow";
  aliases["motion:use_crossbow"]="motion:use_crossbow/use_crossbow";
  aliases["motion:use_shield"]="motion:use_shield/use_shield";
  aliases["motion:use_trident_throw"]="motion:use_trident_throw/use_trident_throw";
  aliases["motion:walk_back"]="motion:walk_back/walk_back";
  aliases["motion:walk_slow"]="motion:walk_slow/walk_slow";
  aliases["motion:walking"]="motion:walking/walking";
  aliases["motion:walking_mit"]="motion:walking_mit/walking_mit";
  const mirrorPairs=new Map();
  const pair=(a,b)=>{a=playerRig.bone(a);b=playerRig.bone(b);if(a>=0&&b>=0){mirrorPairs.set(a,b);mirrorPairs.set(b,a);}};
  pair('left_upper_arm','right_upper_arm');pair('left_forearm','right_forearm');pair('left_hand','right_hand');
  pair('left_item_control','right_item_control');pair('left_thigh','right_thigh');pair('left_shin','right_shin');pair('left_foot','right_foot');
  const L=globalThis.RigAnimationLibrary={
    RigKeyframeClip,ProceduralAnimation,
    registerKeyframe(clip){clips[clip.name]=clip;return clip;},
    registerProcedural(animation){procedural[animation.name]=animation;return animation;},
    get(name){const alias=aliases[name];return clips[name]||procedural[name]||(alias?(clips[alias]||procedural[alias]):null)||null;},
    play(name,rig,time,weight=1,options=null){const clip=this.get(name);return !!clip&&clip.apply(rig,time,weight,options);},
    names(){return [...Object.keys(clips),...Object.keys(procedural)];},
    sourceNames(source){return Object.keys(clips).filter(name=>clips[name].source===source);},
    mirrorBone(index){return mirrorPairs.get(index)??index;},
    clips,procedural,aliases
  };
  L.utility=Object.freeze({
    play:(name,context,rig,time,weight,options)=>!!globalThis.RigAnimationLibrary&&globalThis.RigAnimationLibrary.play(name,rig,time,weight,options),
    arm:context=>context.mainArm==='left'?'left':'right',
    other:side=>side==='left'?'right':'left',
    upper:side=>playerRig.bone(side+'_upper_arm'),
    elbow:side=>playerRig.bone(side+'_elbow'),
    thigh:side=>playerRig.bone(side+'_thigh'),
    knee:side=>playerRig.bone(side+'_knee'),
    foot:side=>playerRig.bone(side+'_foot')
  });
})();
