/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* HoldMyItems Recode; original project by sapling, CC0-1.0. */
package combatant.client.features.hmi_recode.script;

import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.converters.JavetObjectConverter;
import com.caoccao.javet.values.reference.V8ValueArrayBuffer;
import com.caoccao.javet.values.reference.V8ValueTypedArray;
import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.features.hmi_recode.render.HmiPackedCommands;
import combatant.client.features.hmi_recode.HmiScriptKind;
import combatant.client.features.hmi_recode.HmiContextFactory;
import combatant.client.features.hmi_recode.HoldMyItems;
import combatant.client.features.hmi_recode.render.HmiModelCommand;
import combatant.client.features.hmi_recode.render.HmiTransformCommand;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.renderer.ui.runtime.script.JavetRuntimeBootstrap;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Isolated HMI V8 runtime. Resource-pack scripts receive plain JS data only;
 * no Java object proxy is exposed to V8.
 */
public final class HmiScriptRuntime implements AutoCloseable {
    private static final String BOOTSTRAP = """
            globalThis.__hmi_registry = globalThis.__hmi_registry || Object.create(null);
            // Java writes directly into the V8-owned input Float64Array.
            // Fourteen non-numeric string/tag fields are passed in a reusable sideband.
            const __hmi_input = new Float64Array(74);
            Object.defineProperty(globalThis,'__combatant_hmi_input_buffer',{
              value:__hmi_input,writable:false,configurable:false
            });
            const __hmi_native_p = new Array(22);
            const __hmi_native_items = [new Array(13),new Array(13),new Array(13)];
            const __hmi_native_motion = new Array(10);
            const __hmi_native_packed = new Array(21);
            const __hmi_fill_native_item = (index,nbase,mbase,meta) => {
              const item=__hmi_native_items[index];
              item[0]=meta[mbase]; item[1]=meta[mbase+1];
              item[2]=__hmi_input[nbase]; item[3]=meta[mbase+2];
              item[4]=meta[mbase+3];
              for(let i=0;i<8;i++) item[5+i]=__hmi_input[nbase+1+i];
              return item;
            };
            const __hmi_make_native_context = meta => {
              for(let i=0;i<22;i++) __hmi_native_p[i]=__hmi_input[i];
              __hmi_native_p[10]=meta[12];
              const v=__hmi_native_packed;
              v[0]=__hmi_native_p;
              v[1]=__hmi_fill_native_item(0,22,0,meta);
              v[2]=__hmi_fill_native_item(1,31,4,meta);
              v[3]=__hmi_input[49];
              v[4]=__hmi_fill_native_item(2,40,8,meta);
              v[5]=__hmi_input[50] ? 'main_hand' : 'off_hand';
              for(let i=0;i<13;i++) v[6+i]=__hmi_input[51+i];
              for(let i=0;i<9;i++) __hmi_native_motion[i]=__hmi_input[64+i];
              __hmi_native_motion[9]=meta[13];
              v[19]=__hmi_native_motion;
              v[20]=__hmi_input[73];
              return v;
            };
            // Binary commands need no JS arrays; sound arrays are allocated only on demand.
            const __hmi_empty_output = Object.freeze([]);
            globalThis.__hmi_commands = __hmi_empty_output;
            globalThis.__hmi_model_commands = __hmi_empty_output;
            globalThis.__hmi_sound_events = __hmi_empty_output;
            globalThis.__hmi_collect_geometry = true;
            globalThis.global = globalThis;
            globalThis.console = globalThis.console || { log(){}, info(){}, warn(){}, error(){}, debug(){}, trace(){} };

            // Persistent V8 output: 4 stages x (transforms, model) x 256 commands x 8 doubles.
            // Each record is opcode, quad-from, quad-to, argc, a, b, c, d.
            const __hmi_capacity=256, __hmi_stride=8;
            const __hmi_buffer=new Float64Array(4*2*__hmi_capacity*__hmi_stride);
            Object.defineProperty(globalThis,'__combatant_hmi_output_buffer',{
              value:__hmi_buffer,writable:false,configurable:false
            });
            let __hmi_binary_enabled=false;
            globalThis.__combatant_hmi_enable_binary=()=>{__hmi_binary_enabled=true;};
            let __hmi_stage=0, __hmi_tcount=0, __hmi_mcount=0;
            let __hmi_tfallback=false, __hmi_mfallback=false;
            const __hmi_names=['','moveX','moveY','moveZ','translate','scale','rotateX','rotateY','rotateZ','shear','push','pop'];
            const __hmi_copy_legacy=(model,count)=>{
              const result=[];
              const base=(__hmi_stage*2+(model?1:0))*__hmi_capacity*__hmi_stride;
              for(let i=0;i<count;i++){
                const at=base+i*__hmi_stride;
                const op=__hmi_names[__hmi_buffer[at]|0], n=__hmi_buffer[at+3]|0;
                const record=model?[__hmi_buffer[at+1],__hmi_buffer[at+2],op]:[op];
                for(let j=0;j<n;j++) record.push(__hmi_buffer[at+4+j]);
                result.push(record);
              }
              return result;
            };
            const __hmi_emit=(model,op,code,n,a,b,c,d,from=0,to=0)=>{
              if (!__hmi_collect_geometry) return;
              const count=model?__hmi_mcount:__hmi_tcount;
              const fallback=model?__hmi_mfallback:__hmi_tfallback;
              const numeric=(n<1||typeof a==='number')&&(n<2||typeof b==='number')
                &&(n<3||typeof c==='number')&&(n<4||typeof d==='number')
                &&(!model||(typeof from==='number'&&typeof to==='number'));
              if(__hmi_binary_enabled && !fallback && numeric && count<__hmi_capacity){
                const at=((__hmi_stage*2+(model?1:0))*__hmi_capacity+count)*__hmi_stride;
                __hmi_buffer[at]=code;
                __hmi_buffer[at+1]=from;
                __hmi_buffer[at+2]=to;
                __hmi_buffer[at+3]=n;
                if(n>0) __hmi_buffer[at+4]=a;
                if(n>1) __hmi_buffer[at+5]=b;
                if(n>2) __hmi_buffer[at+6]=c;
                if(n>3) __hmi_buffer[at+7]=d;
                if(model) __hmi_mcount++; else __hmi_tcount++;
                return;
              }
              if(__hmi_binary_enabled && !fallback){
                if(model){__hmi_model_commands=__hmi_copy_legacy(true,count);__hmi_mfallback=true;}
                else{__hmi_commands=__hmi_copy_legacy(false,count);__hmi_tfallback=true;}
              }
              const record=model?[from,to,op]:[op];
              if(n>0) record.push(a);
              if(n>1) record.push(b);
              if(n>2) record.push(c);
              if(n>3) record.push(d);
              if(model) __hmi_model_commands.push(record); else __hmi_commands.push(record);
            };
            const __hmi_cmd1=(op,code,a)=>__hmi_emit(false,op,code,1,a);
            const __hmi_cmd3=(op,code,a,b,c)=>__hmi_emit(false,op,code,3,a,b,c);
            const __hmi_rotate=(op,code,angle,x,y,z)=>{
              if(x===undefined||y===undefined||z===undefined) __hmi_emit(false,op,code,1,angle);
              else __hmi_emit(false,op,code,4,angle,x,y,z);
            };
            const __hmi_model1=(from,to,op,code,a)=>__hmi_emit(true,op,code,1,a,0,0,0,from,to);
            const __hmi_model3=(from,to,op,code,a,b,c)=>__hmi_emit(true,op,code,3,a,b,c,0,from,to);
            const __hmi_model_rotate=(from,to,op,code,angle,x,y,z)=>{
              if(x===undefined||y===undefined||z===undefined)
                __hmi_emit(true,op,code,1,angle,0,0,0,from,to);
              else __hmi_emit(true,op,code,4,angle,x,y,z,from,to);
            };
            const __hmi_map = () => {
              const m = new Map();
              m.put = (k, v) => { m.set(k, v); return v; };
              m.getOrDefault = (k, v) => m.has(k) ? m.get(k) : v;
              return m;
            };

            const __hmi_unpack_item = v => ({
              id:v[0], name:v[1], empty:!!v[2], useAction:v[3], tags:v[4],
              block:!!v[5], lantern:!!v[6], throwable:!!v[7], enchanted:!!v[8],
              chargedCrossbow:!!v[9], cooldown:!!v[10], translate:!!v[11], customTranslate:!!v[12],
              spearData:{canDamage:true,canDismount:true,canKnockback:true,hitImpact:false}
            });
            const __hmi_unpack_context = v => {
              const p = v[0], mainItem = __hmi_unpack_item(v[1]), offItem = __hmi_unpack_item(v[2]);
              const item = v[3] === 0 ? mainItem : (v[3] === 1 ? offItem : __hmi_unpack_item(v[4]));
              const motion = v[19];
              return {
                player:{
                  health:p[0], sneaking:!!p[1], onGround:!!p[2], swimming:!!p[3], climbing:!!p[4],
                  crawling:!!p[5], underWater:!!p[6], inWater:!!p[7], riptide:!!p[8], usingItem:!!p[9],
                  activeHand:p[10], x:p[11], y:p[12], z:p[13], yaw:p[14], pitch:p[15], age:p[16],
                  swingCount:p[17], hasVehicle:!!p[18], velocity:{x:p[19],y:p[20],z:p[21]},
                  mainItem, offItem
                },
                item, hand:v[5], mainHand:!!v[6], bl:!!v[7], swingProgress:v[8], rawSwingProgress:v[9],
                mainHandSwingProgress:v[10], offHandSwingProgress:v[11], equipProgress:v[12], deltaTime:v[13],
                swingMHand:!!v[14], swingOHand:!!v[15], mainHandSwitchEvent:!!v[16],
                offHandSwitchEvent:!!v[17], blockBreaking:!!v[18],
                motion:{
                  swing:motion[0], swordSwing:motion[1], offhandSwing:motion[2], movement:motion[3],
                  look:motion[4], switch:motion[5], use:motion[6], impact:motion[7],
                  replaceSwing:!!motion[8], swingStyle:motion[9]
                },
                matrices:0, particles:[], inspectPressed:!!v[20]
              };
            };

            globalThis.renderAsBlock = __hmi_map();
            globalThis.translateItem = __hmi_map();
            globalThis.itemSwingSpeed = __hmi_map();
            globalThis.useDuration = __hmi_map();
            globalThis.usingItem = __hmi_map();
            globalThis.applyBlockRotation = __hmi_map();

            globalThis.M = {
              PI: Math.PI,
              moveX: (_m,x) => __hmi_cmd1('moveX',1,x),
              moveY: (_m,y) => __hmi_cmd1('moveY',2,y),
              moveZ: (_m,z) => __hmi_cmd1('moveZ',3,z),
              translate: (_m,x,y,z) => __hmi_cmd3('translate',4,x,y,z),
              scale: (_m,x,y,z) => __hmi_cmd3('scale',5,x,y,z),
              rotateX: (_m,a,x,y,z) => __hmi_rotate('rotateX',6,a,x,y,z),
              rotateY: (_m,a,x,y,z) => __hmi_rotate('rotateY',7,a,x,y,z),
              rotateZ: (_m,a,x,y,z) => __hmi_rotate('rotateZ',8,a,x,y,z),
              shear: (_m,x,y,z) => __hmi_cmd3('shear',9,x,y,z),
              push: () => { __hmi_emit(false,'push',10,0); },
              pop: () => { __hmi_emit(false,'pop',11,0); },
              sin: Math.sin,
              cos: Math.cos,
              floor: Math.floor,
              ceil: Math.ceil,
              abs: Math.abs,
              pow: Math.pow,
              round: Math.round,
              clamp: (v,min,max) => Math.max(min, Math.min(max,v)),
              lerp: (t,a,b) => a + (b-a)*t
            };

            const __hmi_easeOutBounce = x => {
              const n1=7.5625,d1=2.75;
              if (x < 1/d1) return n1*x*x;
              if (x < 2/d1) { x-=1.5/d1; return n1*x*x+.75; }
              if (x < 2.5/d1) { x-=2.25/d1; return n1*x*x+.9375; }
              x-=2.625/d1; return n1*x*x+.984375;
            };
            globalThis.Easings = {
              easeInSine:x=>1-Math.cos((x*Math.PI)/2),
              easeOutSine:x=>Math.sin((x*Math.PI)/2),
              easeInOutSine:x=>-(Math.cos(Math.PI*x)-1)/2,
              easeInQuad:x=>x*x, easeOutQuad:x=>1-(1-x)*(1-x),
              easeInOutQuad:x=>x<.5?2*x*x:1-Math.pow(-2*x+2,2)/2,
              easeInCubic:x=>x*x*x, easeOutCubic:x=>1-Math.pow(1-x,3),
              easeInOutCubic:x=>x<.5?4*x*x*x:1-Math.pow(-2*x+2,3)/2,
              easeInQuart:x=>x*x*x*x, easeOutQuart:x=>1-Math.pow(1-x,4),
              easeInOutQuart:x=>x<.5?8*x*x*x*x:1-Math.pow(-2*x+2,4)/2,
              easeInQuint:x=>x*x*x*x*x, easeOutQuint:x=>1-Math.pow(1-x,5),
              easeInOutQuint:x=>x<.5?16*x*x*x*x*x:1-Math.pow(-2*x+2,5)/2,
              easeInExpo:x=>x===0?0:Math.pow(2,10*x-10),
              easeOutExpo:x=>x===1?1:1-Math.pow(2,-10*x),
              easeInOutExpo:x=>x===0?0:x===1?1:x<.5?Math.pow(2,20*x-10)/2:(2-Math.pow(2,-20*x+10))/2,
              easeInCirc:x=>1-Math.sqrt(1-x*x),
              easeOutCirc:x=>Math.sqrt(1-Math.pow(x-1,2)),
              easeInOutCirc:x=>x<.5?(1-Math.sqrt(1-Math.pow(2*x,2)))/2:(Math.sqrt(1-Math.pow(-2*x+2,2))+1)/2,
              easeInBack:x=>2.70158*x*x*x-1.70158*x*x,
              easeOutBack:x=>1+2.70158*Math.pow(x-1,3)+1.70158*Math.pow(x-1,2),
              easeInOutBack:x=>x<.5?(Math.pow(2*x,2)*((2.5949095+1)*2*x-2.5949095))/2:(Math.pow(2*x-2,2)*((2.5949095+1)*(x*2-2)+2.5949095)+2)/2,
              easeOutBounce:__hmi_easeOutBounce,
              easeInBounce:x=>1-__hmi_easeOutBounce(1-x),
              easeInOutBounce:x=>x<.5?(1-__hmi_easeOutBounce(1-2*x))/2:(1+__hmi_easeOutBounce(2*x-1))/2,
              cubicEase:x=>x*x*(3-2*x)
            };

            globalThis.Items = { get:id=>String(id) };
            globalThis.Tags = {
              getVanillaTag:id=>'minecraft:'+String(id),
              getFabricTag:id=>'c:'+String(id)
            };
            globalThis.P = {
              getHealth:p=>p.health,
              isSneaking:p=>!!p.sneaking,
              isOnGround:p=>!!p.onGround,
              isSwimming:p=>!!p.swimming,
              isClimbing:p=>!!p.climbing,
              isCrawling:p=>!!p.crawling,
              isSubmergedInWater:p=>!!p.underWater,
              isTouchingWater:p=>!!p.inWater,
              isUsingRiptide:p=>!!p.riptide,
              getX:p=>p.x, getY:p=>p.y, getZ:p=>p.z,
              getXSpeed:p=>p.velocity.x, getYSpeed:p=>p.velocity.y, getZSpeed:p=>p.velocity.z,
              getSpeed:p=>Math.hypot(p.velocity.x,p.velocity.z),
              isUsingItem:p=>!!p.usingItem,
              getYaw:p=>p.yaw, getPitch:p=>p.pitch,
              getMainItem:p=>p.mainItem, getOffhandItem:p=>p.offItem,
              getActiveHand:p=>p.activeHand,
              getAge:p=>p.age,
              isItemCoolingDown:(_p,item)=>!!item.cooldown,
              getSwingCount:p=>p.swingCount,
              hasVehicle:p=>!!p.hasVehicle
            };
            globalThis.I = {
              isOf:(item,id)=>!!item && item.id===id,
              isIn:(item,tag)=>!!item && Array.isArray(item.tags) && item.tags.includes(tag),
              isEmpty:item=>!item || !!item.empty,
              getUseAction:item=>item?.useAction || 'none',
              getName:item=>item?.id || 'minecraft:air',
              getActualName:item=>item?.name || '',
              isChargedCrossbow:item=>!!item?.chargedCrossbow,
              isBlock:item=>!!item?.block,
              shouldTranslateItem:item=>!!item?.translate,
              isCustomTranslate:item=>!!item && !!translateItem.getOrDefault(item.id, !!item.customTranslate),
              isLantern:item=>!!item?.lantern,
              isThrowable:item=>!!item?.throwable,
              isEnchanted:item=>!!item?.enchanted,
              getSpearData:item=>item?.spearData || {canDamage:false,canDismount:true,canKnockback:true,hitImpact:false},
              setChestOpen:()=>{}, setShulkerOpen:()=>{}
            };
            globalThis.Texture = { of:(namespace,path)=>String(namespace)+':' + String(path) };
            globalThis.string = { find:(value,needle)=>String(value).includes(String(needle)) };
            globalThis.KeyBindManager = { isKeyPressed:key=>Number(key)===74 && !!globalThis.__hmi_inspect_pressed };
            globalThis.S = { playSound:(id,volume)=>{
              if (__hmi_sound_events === __hmi_empty_output) __hmi_sound_events = [];
              __hmi_sound_events.push([String(id),Number(volume)||1]);
            } };
            globalThis.debugger = { out:()=>{} };
            globalThis.particleManager = { addParticle:()=>{} };
            globalThis.animator = {
              moveX:(f,t,x)=>__hmi_model1(f,t,'moveX',1,x),
              moveY:(f,t,y)=>__hmi_model1(f,t,'moveY',2,y),
              moveZ:(f,t,z)=>__hmi_model1(f,t,'moveZ',3,z),
              scale:(f,t,x,y,z)=>__hmi_model3(f,t,'scale',5,x,y,z),
              rotateX:(f,t,a,x,y,z)=>__hmi_model_rotate(f,t,'rotateX',6,a,x,y,z),
              rotateY:(f,t,a,x,y,z)=>__hmi_model_rotate(f,t,'rotateY',7,a,x,y,z),
              rotateZ:(f,t,a,x,y,z)=>__hmi_model_rotate(f,t,'rotateZ',8,a,x,y,z)
            };
            globalThis.__hmi_reset_output=()=>{
              __hmi_commands=__hmi_binary_enabled ? __hmi_empty_output : [];
              __hmi_model_commands=__hmi_binary_enabled ? __hmi_empty_output : [];
              __hmi_sound_events=__hmi_empty_output;
              __hmi_tcount=0;
              __hmi_mcount=0;
              __hmi_tfallback=false;
              __hmi_mfallback=false;
            };
            const __hmi_take_output=()=>[
              __hmi_binary_enabled&&!__hmi_tfallback ? __hmi_tcount : __hmi_commands,
              __hmi_binary_enabled&&!__hmi_mfallback ? __hmi_mcount : __hmi_model_commands,
              __hmi_sound_events
            ];
            const __hmi_run=(name,stage)=>{
              __hmi_stage=stage;
              __hmi_reset_output();
              globalThis[name]();
              return __hmi_take_output();
            };
            const __hmi_run_state_only=(name,stage)=>{
              __hmi_stage=stage;
              __hmi_reset_output();
              __hmi_collect_geometry=false;
              try{
                globalThis[name]();
                return __hmi_take_output();
              }finally{__hmi_collect_geometry=true;}
            };
            // A legacy invocation retains fresh JS contexts for scripts that hold old references.
            globalThis.__combatant_hmi_reuse_context = true;
            const __hmi_cached_context = __hmi_unpack_context(__hmi_make_native_context(new Array(14)));
            const __hmi_cached_player = __hmi_cached_context.player;
            const __hmi_cached_velocity = __hmi_cached_player.velocity;
            const __hmi_cached_main_item = __hmi_cached_player.mainItem;
            const __hmi_cached_off_item = __hmi_cached_player.offItem;
            const __hmi_cached_extra_item = __hmi_unpack_item(__hmi_native_items[2]);
            const __hmi_cached_spear = [__hmi_cached_main_item.spearData,
              __hmi_cached_off_item.spearData,__hmi_cached_extra_item.spearData];
            const __hmi_cached_motion = __hmi_cached_context.motion;
            const __hmi_cached_particles = __hmi_cached_context.particles;
            const __hmi_update_item=(item,v,spear)=>{
              item.spearData=spear;
                  item.id = v[0];
                  item.name = v[1];
                  item.empty = !!v[2];
                  item.useAction = v[3];
                  item.tags = v[4];
                  item.block = !!v[5];
                  item.lantern = !!v[6];
                  item.throwable = !!v[7];
                  item.enchanted = !!v[8];
                  item.chargedCrossbow = !!v[9];
                  item.cooldown = !!v[10];
                  item.translate = !!v[11];
                  item.customTranslate = !!v[12];
                  item.spearData.canDamage = true;
                  item.spearData.canDismount = true;
                  item.spearData.canKnockback = true;
                  item.spearData.hitImpact = false;
            };
            const __hmi_update_native_context=metadata=>{
              const v=__hmi_make_native_context(metadata);
              const context=__hmi_cached_context;
              const p=v[0], motion=v[19];
              const mainItem=__hmi_cached_main_item, offItem=__hmi_cached_off_item;
              // Restore stable subobject identities after any synchronous addon mutations.
              context.player=__hmi_cached_player;
              context.player.velocity=__hmi_cached_velocity;
              context.motion=__hmi_cached_motion;
              __hmi_update_item(mainItem,v[1],__hmi_cached_spear[0]);
              __hmi_update_item(offItem,v[2],__hmi_cached_spear[1]);
              const selected=v[3];
              if (selected!==0 && selected!==1)
                __hmi_update_item(__hmi_cached_extra_item,v[4],__hmi_cached_spear[2]);
              const item=selected===0?mainItem:selected===1?offItem:__hmi_cached_extra_item;
                  context.player.health = p[0];
                  context.player.sneaking = !!p[1];
                  context.player.onGround = !!p[2];
                  context.player.swimming = !!p[3];
                  context.player.climbing = !!p[4];
                  context.player.crawling = !!p[5];
                  context.player.underWater = !!p[6];
                  context.player.inWater = !!p[7];
                  context.player.riptide = !!p[8];
                  context.player.usingItem = !!p[9];
                  context.player.activeHand = p[10];
                  context.player.x = p[11];
                  context.player.y = p[12];
                  context.player.z = p[13];
                  context.player.yaw = p[14];
                  context.player.pitch = p[15];
                  context.player.age = p[16];
                  context.player.swingCount = p[17];
                  context.player.hasVehicle = !!p[18];
                  context.player.velocity.x = p[19];
                  context.player.velocity.y = p[20];
                  context.player.velocity.z = p[21];
                  context.player.mainItem = mainItem;
                  context.player.offItem = offItem;
                  context.item = item;
                  context.hand = v[5];
                  context.mainHand = !!v[6];
                  context.bl = !!v[7];
                  context.swingProgress = v[8];
                  context.rawSwingProgress = v[9];
                  context.mainHandSwingProgress = v[10];
                  context.offHandSwingProgress = v[11];
                  context.equipProgress = v[12];
                  context.deltaTime = v[13];
                  context.swingMHand = !!v[14];
                  context.swingOHand = !!v[15];
                  context.mainHandSwitchEvent = !!v[16];
                  context.offHandSwitchEvent = !!v[17];
                  context.blockBreaking = !!v[18];
                  context.motion.swing = motion[0];
                  context.motion.swordSwing = motion[1];
                  context.motion.offhandSwing = motion[2];
                  context.motion.movement = motion[3];
                  context.motion.look = motion[4];
                  context.motion.switch = motion[5];
                  context.motion.use = motion[6];
                  context.motion.impact = motion[7];
                  context.motion.replaceSwing = !!motion[8];
                  context.motion.swingStyle = motion[9];
                  context.matrices = 0;
                  context.particles = __hmi_cached_particles;
                  __hmi_cached_particles.length = 0;
                  context.inspectPressed = !!v[20];
              return context;
            };
            const __hmi_execute_context=(mask,context)=>{
              globalThis.__hmi_context=context;
              globalThis.mainHandSwitchEvent=!!context.mainHandSwitchEvent;
              globalThis.offHandSwitchEvent=!!context.offHandSwitchEvent;
              globalThis.__hmi_inspect_pressed=!!context.inspectPressed;
              return [
                (mask&1)!==0 ? __hmi_run('__hmi_hand_pose',0)
                             : ((mask&16)!==0 ? __hmi_run_state_only('__hmi_hand_pose',0) : null),
                (mask&2)!==0 ? __hmi_run('__hmi_hand_relative_pose',1) : null,
                (mask&4)!==0 ? __hmi_run('__hmi_item_pose',2) : null,
                (mask&8)!==0 ? __hmi_run('__hmi_item_model',3) : null
              ];
            };
            globalThis.__hmi_execute_plan=(mask,packedContext)=>
              __hmi_execute_context(mask,__hmi_unpack_context(packedContext));
            globalThis.__hmi_execute_native_plan=(mask,metadata)=>
              __hmi_execute_context(mask,globalThis.__combatant_hmi_reuse_context
                ? __hmi_update_native_context(metadata)
                : __hmi_unpack_context(__hmi_make_native_context(metadata)));
            """;

    public static final int HAND_POSE = 1;
    public static final int HAND_RELATIVE_POSE = 1 << 1;
    public static final int ITEM_POSE = 1 << 2;
    public static final int ITEM_MODEL = 1 << 3;
    public static final int HAND_POSE_STATE_ONLY = 1 << 4;
    private static final HmiScriptKind[] KINDS = HmiScriptKind.values();

    private V8Runtime runtime;
    private boolean dirty = true;
    private V8ValueArrayBuffer packedOutputBuffer;
    private ByteBuffer packedOutputBytes;
    private V8ValueArrayBuffer packedInputBuffer;
    private HmiInputBuffer packedInput;
    private final Object[] nativeInvokeArguments = new Object[2];

    public synchronized Result execute(HmiScriptKind kind, Object[] context) {
        Result[] results = executePlan(mask(kind), context);
        Result result = results[kind.ordinal()];
        return result != null ? result : Result.EMPTY;
    }

    public synchronized Result execute(HmiScriptKind kind, HoldMyItems.RenderScope scope) {
        Result[] results = executePlan(mask(kind), scope);
        Result result = results[kind.ordinal()];
        return result != null ? result : Result.EMPTY;
    }

    /**
     * Executes all requested HMI stages in one V8 call. The scripts still run in their original
     * hand/relative/item/model order and each stage retains an isolated output collector.
     */
    public synchronized Result[] executePlan(int plan, Object[] context) {
        return executePlanInternal(plan, context, null);
    }

    /** Uses native input if available; otherwise preserves the original resource-pack protocol. */
    public synchronized Result[] executePlan(int plan, HoldMyItems.RenderScope scope) {
        return executePlanInternal(plan, null, scope);
    }

    private Result[] executePlanInternal(int plan, Object[] context, HoldMyItems.RenderScope scope) {
        Result[] results = new Result[KINDS.length];
        try {
            ensureReady();
            if (runtime == null || plan == 0) return fillMissing(plan, results);

            try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("hmi:v8_execute_plan")) {
                Object raw;
                if (scope != null && packedInput != null) {
                    try (ProfilerPhase.Scope packing = ProfilerPhase.scope("hmi:context_pack")) {
                        HmiContextFactory.writeNative(scope, packedInput);
                    }
                    nativeInvokeArguments[0] = plan;
                    nativeInvokeArguments[1] = packedInput.metadata();
                    try {
                        try (ProfilerPhase.Scope invoke = ProfilerPhase.scope("hmi:javet_invoke")) {
                            raw = runtime.getGlobalObject().invokeObject(
                                    "__hmi_execute_native_plan", nativeInvokeArguments);
                        }
                    } finally {
                        nativeInvokeArguments[0] = null;
                        nativeInvokeArguments[1] = null;
                    }
                } else {
                    if (scope != null) {
                        try (ProfilerPhase.Scope packing = ProfilerPhase.scope("hmi:context_pack")) {
                            context = HmiContextFactory.renderContext(scope);
                        }
                    }
                    try (ProfilerPhase.Scope invoke = ProfilerPhase.scope("hmi:javet_invoke")) {
                        raw = runtime.getGlobalObject().invokeObject("__hmi_execute_plan", plan, context);
                    }
                }
                if (!(raw instanceof List<?> stages)) return fillMissing(plan, results);
                try (ProfilerPhase.Scope decode = ProfilerPhase.scope("hmi:decode_plan")) {
                    int count = Math.min(stages.size(), results.length);
                    for (int i = 0; i < count; i++) {
                        Object stage = stages.get(i);
                        if (!(stage instanceof List<?> output) || output.size() < 3) continue;
                        HmiPackedCommands.Snapshot transforms = output.get(0) instanceof Number n && packedOutputBytes != null
                                ? HmiPackedCommands.copy(packedOutputBytes, i, false, n.intValue())
                                : HmiPackedCommands.Snapshot.EMPTY;
                        HmiPackedCommands.Snapshot models = output.get(1) instanceof Number n && packedOutputBytes != null
                                ? HmiPackedCommands.copy(packedOutputBytes, i, true, n.intValue())
                                : HmiPackedCommands.Snapshot.EMPTY;
                        results[i] = new Result(
                                output.get(0) instanceof Number ? List.of() : HmiTransformCommand.decode(output.get(0)),
                                output.get(1) instanceof Number ? List.of() : HmiModelCommand.decode(output.get(1)),
                                HmiSoundCommand.decode(output.get(2)), transforms, models
                        );
                    }
                }
            }
        } catch (Throwable t) {
            DebugLog.error("[HMI] JavaScript execution failed for plan %d: %s", t, plan, t.getMessage());
        }
        return fillMissing(plan, results);
    }

    private static Result[] fillMissing(int plan, Result[] results) {
        for (HmiScriptKind kind : KINDS) {
            boolean requested = (plan & mask(kind)) != 0
                    || kind == HmiScriptKind.HAND_POSE && (plan & HAND_POSE_STATE_ONLY) != 0;
            if (requested && results[kind.ordinal()] == null) {
                results[kind.ordinal()] = Result.EMPTY;
            }
        }
        return results;
    }

    private static int mask(HmiScriptKind kind) {
        return switch (kind) {
            case HAND_POSE -> HAND_POSE;
            case HAND_RELATIVE_POSE -> HAND_RELATIVE_POSE;
            case ITEM_POSE -> ITEM_POSE;
            case ITEM_MODEL -> ITEM_MODEL;
        };
    }

    public synchronized void invalidate() {
        dirty = true;
    }

    private void ensureReady() throws Exception {
        if (!dirty && runtime != null) return;
        closeRuntime();
        JavetRuntimeBootstrap.installNativeLoader();
        runtime = JavetRuntimeBootstrap.createRuntime(this);
        runtime.setConverter(new JavetObjectConverter());
        runtime.setMemorySaverModeEnabled(false);
        runtime.setBatterySaverModeEnabled(false);
        runtime.getExecutor(BOOTSTRAP).setResourceName("combatant:hmi/bootstrap.js").executeVoid();
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        for (HmiScriptKind kind : KINDS) {
            String source = loadStack(manager, kind);
            String function = "__hmi_" + kind.name().toLowerCase();
            String wrapped = "globalThis." + function + " = function() {\n" +
                    "const " + kind.argumentName() + " = globalThis.__hmi_context;\n" +
                    source + "\n};\n" +
                    "globalThis." + function + "_ready = true;";
            runtime.getExecutor(wrapped).setResourceName(kind.resourceId().toString()).executeVoid();
        }
        bindPackedOutput();
        bindPackedInput();
        dirty = false;
    }

    private void bindPackedOutput() {
        V8ValueArrayBuffer buffer = null;
        try (V8ValueTypedArray array = runtime.getGlobalObject().get("__combatant_hmi_output_buffer")) {
            if (array == null || array.getLength() != HmiPackedCommands.VALUE_COUNT) return;
            buffer = array.getBuffer();
            ByteBuffer bytes = buffer.getByteBuffer();
            if (bytes == null || !bytes.isDirect() || bytes.capacity() < HmiPackedCommands.BYTE_COUNT) return;
            ByteBuffer view = bytes.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
            runtime.getExecutor("globalThis.__combatant_hmi_enable_binary()").executeVoid();
            packedOutputBuffer = buffer;
            packedOutputBytes = view;
            buffer = null;
        } catch (Exception ignored) {
            // On unsupported Javet backends retain the legacy List output protocol.
        } finally {
            if (buffer != null) try { buffer.close(); } catch (Exception ignored) { }
        }
    }

    private void bindPackedInput() {
        V8ValueArrayBuffer buffer = null;
        try (V8ValueTypedArray array = runtime.getGlobalObject().get("__combatant_hmi_input_buffer")) {
            if (array == null || array.getLength() != HmiInputBuffer.VALUE_COUNT) return;
            buffer = array.getBuffer();
            ByteBuffer bytes = buffer.getByteBuffer();
            if (bytes == null || !bytes.isDirect() || bytes.isReadOnly()
                    || bytes.capacity() < HmiInputBuffer.BYTE_COUNT) return;
            packedInput = new HmiInputBuffer(bytes);
            packedInputBuffer = buffer;
            buffer = null;
        } catch (Exception ignored) {
            // Retain the legacy Object[] input path on unsupported native backends.
        } finally {
            if (buffer != null) try { buffer.close(); } catch (Exception ignored) { }
        }
    }

    private static String loadStack(ResourceManager manager, HmiScriptKind kind) {
        List<String> chunks = new ArrayList<>();
        manager.getResource(kind.resourceId()).ifPresent(resource -> readResource(kind.resourceId().toString(), resource, chunks));
        for (Resource addon : manager.getResourceStack(kind.addonResourceId())) {
            readResource(kind.addonResourceId().toString(), addon, chunks);
        }
        return String.join("\n", chunks);
    }

    private static void readResource(String id, Resource resource, List<String> chunks) {
        try (BufferedReader reader = resource.openAsReader()) {
            chunks.add(reader.lines().collect(Collectors.joining("\n")));
        } catch (Exception e) {
            DebugLog.error("[HMI] Failed to load %s from %s", e, id, resource.sourcePackId());
        }
    }

    @Override
    public synchronized void close() {
        closeRuntime();
        dirty = true;
    }

    private void closeRuntime() {
        nativeInvokeArguments[0] = null;
        nativeInvokeArguments[1] = null;
        packedInput = null;
        if (packedInputBuffer != null) {
            try { packedInputBuffer.close(); } catch (Exception ignored) { }
            packedInputBuffer = null;
        }
        packedOutputBytes = null;
        if (packedOutputBuffer != null) {
            try { packedOutputBuffer.close(); } catch (Exception ignored) { }
            packedOutputBuffer = null;
        }
        if (runtime == null) return;
        try {
            JavetRuntimeBootstrap.closeRuntime(this, runtime);
        } catch (Throwable ignored) {
        } finally {
            runtime = null;
        }
    }

    /** Immutable per-stage snapshot; native buffers never escape the V8 call. */
    public record Result(List<HmiTransformCommand> commands, List<HmiModelCommand> modelCommands,
                         List<HmiSoundCommand> sounds, HmiPackedCommands.Snapshot transforms,
                         HmiPackedCommands.Snapshot models) {
        public static final Result EMPTY = new Result(List.of(), List.of(), List.of());

        public Result(List<HmiTransformCommand> commands, List<HmiModelCommand> modelCommands,
                      List<HmiSoundCommand> sounds) {
            this(commands, modelCommands, sounds,
                    HmiPackedCommands.Snapshot.EMPTY, HmiPackedCommands.Snapshot.EMPTY);
        }

        public void applyTransforms(PoseStack stack) {
            transforms.applyTransforms(stack);
            for (HmiTransformCommand command : commands) command.apply(stack);
        }

        public boolean hasModelCommands() {
            return !models.isEmpty() || !modelCommands.isEmpty();
        }

        public void applyModelCommands(int quadIndex, PoseStack stack) {
            // Model pose order is newest first (reverse insertion order).
            HmiModelCommand.apply(modelCommands, quadIndex, stack);
            models.applyModels(quadIndex, stack);
        }

        /** Legacy accessors stay usable by non-optimized clients when needed. */
        @Override public List<HmiTransformCommand> commands() {
            if (transforms.isEmpty()) return commands;
            List<HmiTransformCommand> combined = new ArrayList<>(transforms.toTransforms());
            combined.addAll(commands);
            return combined;
        }
        @Override public List<HmiModelCommand> modelCommands() {
            if (models.isEmpty()) return modelCommands;
            List<HmiModelCommand> combined = new ArrayList<>(models.toModels());
            combined.addAll(modelCommands);
            return combined;
        }
    }
}
