var yawAngle = Object.prototype.hasOwnProperty.call(__hmi_registry, 'yawAngle') ? __hmi_registry['yawAngle'] : (0);
var yawAngleO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'yawAngleO') ? __hmi_registry['yawAngleO'] : (0);
var pitchAngle = Object.prototype.hasOwnProperty.call(__hmi_registry, 'pitchAngle') ? __hmi_registry['pitchAngle'] : (0);
var pitchAngleO = Object.prototype.hasOwnProperty.call(__hmi_registry, 'pitchAngleO') ? __hmi_registry['pitchAngleO'] : (0);
var fall = Object.prototype.hasOwnProperty.call(__hmi_registry, 'fall') ? __hmi_registry['fall'] : (0);
var a = Object.prototype.hasOwnProperty.call(__hmi_registry, 'a') ? __hmi_registry['a'] : (0);
var walk = Object.prototype.hasOwnProperty.call(__hmi_registry, 'walk') ? __hmi_registry['walk'] : (0);
var walkSmoother = Object.prototype.hasOwnProperty.call(__hmi_registry, 'walkSmoother') ? __hmi_registry['walkSmoother'] : (0);
var fall_f = Object.prototype.hasOwnProperty.call(__hmi_registry, 'fall_f') ? __hmi_registry['fall_f'] : (0);
var jiggle_f = Object.prototype.hasOwnProperty.call(__hmi_registry, 'jiggle_f') ? __hmi_registry['jiggle_f'] : (0);
var mainHandSwitch = Object.prototype.hasOwnProperty.call(__hmi_registry, 'mainHandSwitch') ? __hmi_registry['mainHandSwitch'] : (0);
var offHandSwitch = Object.prototype.hasOwnProperty.call(__hmi_registry, 'offHandSwitch') ? __hmi_registry['offHandSwitch'] : (0);
var jiggle_i = Object.prototype.hasOwnProperty.call(__hmi_registry, 'jiggle_i') ? __hmi_registry['jiggle_i'] : (0.0);

var axolotl_anim = Object.prototype.hasOwnProperty.call(__hmi_registry, 'axolotl_anim') ? __hmi_registry['axolotl_anim'] : (1);
var pufferfish_anim = Object.prototype.hasOwnProperty.call(__hmi_registry, 'pufferfish_anim') ? __hmi_registry['pufferfish_anim'] : (1);
var salmon_anim = Object.prototype.hasOwnProperty.call(__hmi_registry, 'salmon_anim') ? __hmi_registry['salmon_anim'] : (1);
var cod_anim = Object.prototype.hasOwnProperty.call(__hmi_registry, 'cod_anim') ? __hmi_registry['cod_anim'] : (1);
var tadpole_anim = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tadpole_anim') ? __hmi_registry['tadpole_anim'] : (1);
var liquid_anim = Object.prototype.hasOwnProperty.call(__hmi_registry, 'liquid_anim') ? __hmi_registry['liquid_anim'] : (1);
var tropical_fish_anim = Object.prototype.hasOwnProperty.call(__hmi_registry, 'tropical_fish_anim') ? __hmi_registry['tropical_fish_anim'] : (1);
var chest_boat_anim = Object.prototype.hasOwnProperty.call(__hmi_registry, 'chest_boat_anim') ? __hmi_registry['chest_boat_anim'] : (1);



var l = (data.bl ? 1 : -1)
jiggle_i = jiggle_i + P.getYSpeed(data.player) * data.deltaTime * 30
if (!P.isOnGround(data.player) && P.getYSpeed(data.player) * -1 > 0.55) {
    fall_f = fall_f + 0.045 * data.deltaTime * 30;
} else {
    fall_f = fall_f - 0.07 * data.deltaTime * 30
}
fall_f = M.clamp(fall_f, 0, 1)
var wf = M.clamp(fall * 0.4, 0, 2) * (fall_f * fall_f * fall_f)
var l = (data.bl ? 1 : -1)

var ywAngle = (data.mainHand ? yawAngle : yawAngleO)
var ptAngle = (data.mainHand ? pitchAngle : pitchAngleO)
if (I.isOf(data.item, Items.get("minecraft:water_bucket")) || I.isOf(data.item, Items.get("minecraft:lava_bucket")) || I.isOf(data.item, Items.get("minecraft:milk_bucket"))) {
    animator.rotateX(0, 1, M.clamp(-ptAngle * 0.6, -5, 5) * liquid_anim, 0.5, 0.7, 0.5)
    animator.rotateZ(0, 1, M.clamp(ywAngle * 0.6, -5, 5) * liquid_anim, 0.5, 0.7, 0.5)
}

if (I.isOf(data.item, Items.get("minecraft:axolotl_bucket"))) {
    // Head parts
    animator.moveY(0, 11, fall * 0.02 * axolotl_anim)
    animator.moveY(0, 11, 0.02 * Math.sin(a * -1.5)  * (1-walkSmoother) * axolotl_anim)
    animator.moveY(0, 11,  0.08 * Easings.easeInSine(Math.abs(Math.sin(walk))) * walkSmoother * axolotl_anim)
    animator.rotateX(0, 11, M.clamp(20 * fall, -20, 20) * axolotl_anim, 0.5, 0.7, 0.5)
    animator.rotateZ(0, 11, 2 * Math.sin(a) * axolotl_anim, 0.5, 0.7, 0.5)
    animator.rotateX(0, 11, ptAngle * 0.3 * axolotl_anim, 0.5, 0.7, 0.5)
    animator.rotateZ(0, 11, -ywAngle * 0.3  * axolotl_anim, 0.5, 0.7, 0.5)

    // animator:rotateZ(0, 11, 6 * math.sin(M:clamp(fall, 0, 4.75) * 4) * wf , 0.5, 0.9, 0.5)
    // animator:rotateY(0, 11, -15 * math.sin(M:clamp(fall, 0, 4.75) * 4) * wf , 0.5, 0.7, 0.5)

    // animator:rotateZ(0, 11, 7 * math.sin(a * 1) * M:pow(math.sin(a * 0.1), 20), 0.5, 0.7, 0.5)
    // animator:rotateY(0, 11, 30 * math.sin(a * 1) * M:pow(math.sin(a * 0.1), 20), 0.5, 0.5, 0.5)
    // Body parts
    animator.moveY(12, 19, 0.02 * Math.sin(a * -1.5) * (1-walkSmoother) * axolotl_anim)
    animator.moveY(12, 19, 0.08 * Easings.easeInSine(Math.abs(Math.sin(walk))) * walkSmoother * axolotl_anim)
    animator.rotateX(12, 19, M.clamp(15 * fall, -20, 20) * axolotl_anim, 0.5, 0.7, 0.5)
    animator.rotateZ(12, 19, 1 * Math.sin(a) * axolotl_anim, 0.5, 0.7, 0.5)
    animator.rotateX(12, 19, ptAngle * 0.15 * axolotl_anim, 0.5, 0.7, 0.5)
    animator.rotateZ(12, 19, -ywAngle * 0.15  * axolotl_anim, 0.5, 0.7, 0.5)
    // animator:rotateZ(12, 19, 3.5 * math.sin(a * 1) * M:pow(math.sin(a * 0.1), 20), 0.5, 0.7, 0.5)

    // animator:rotateY(12, 19, 15 * math.sin(a * 1) * M:pow(math.sin(a * 0.1), 20), 0.5, 0.5, 0.5)

    animator.rotateY(8, 9, 10 * Math.sin(a * 10) * M.pow(Math.sin(a * 0.4), 30) * axolotl_anim, 0.8, 0.5, 0.4)
    animator.rotateY(10, 11, -10 * Math.sin(a * 10) * M.pow(Math.sin(a * 0.4), 30) * axolotl_anim, 0.3, 0.5, 0.4)
    animator.rotateX(20, 21, 80 * wf * axolotl_anim, 0.5, 0.85, 0.3)
    animator.rotateZ(20, 21, 15 * wf * axolotl_anim, 0.5, 0.85, 0.3)
    animator.rotateX(22, 23, 80 * wf * axolotl_anim, 0.5, 0.85, 0.3)
    animator.rotateZ(22, 23, -15 * wf * axolotl_anim, 0.5, 0.85, 0.3)

    animator.moveX(0, 23, fall * 0.02 * axolotl_anim)
    animator.moveZ(0, 23, fall * 0.025 * axolotl_anim)
    animator.scale(0, 23,  1 - (0.03 * fall) * axolotl_anim, 1 + (0.03 * fall) * axolotl_anim, 1 - (0.03 * fall) * axolotl_anim)


    // animator:rotateZ(22, 23, 0.5 * math.sin(jiggle_i * 0.5) * wf * l)
    // animator:rotateX(22, 23, 0.5 * math.sin(jiggle_i * 0.5) * wf * l)

    // animator:rotateZ(20, 21, 0.5 * math.sin(jiggle_i * 0.5) * wf * l)
    // animator:rotateX(20, 21, 0.5 * math.sin(jiggle_i * 0.5) * wf * l)

}

if (I.isOf(data.item, Items.get("minecraft:tropical_fish_bucket"))) {
    //Body parts
    animator.moveY(0, 12, fall * 0.1 * tropical_fish_anim)
    animator.moveY(0, 12, 0.02 * Math.sin(a * -1.5)  * (1-walkSmoother) * tropical_fish_anim)
    animator.moveY(0, 12, 0.08 * Easings.easeInSine(Math.abs(Math.sin(walk))) * walkSmoother * tropical_fish_anim)
    animator.moveZ(0, 12, 0.1 * tropical_fish_anim)
    animator.moveY(0, 12, -0.03 * tropical_fish_anim)
    animator.moveY(0, 12, -ywAngle * 0.002 * tropical_fish_anim)
    animator.rotateX(0, 12, -ywAngle * tropical_fish_anim , 0.5, 0.5, 0.5)
    animator.rotateZ(0, 12, -ptAngle * tropical_fish_anim , 0.5, 0.5 , 0.5)
    animator.rotateX(0, 12, 3 * Math.sin(a * 3)  * (1-walkSmoother) * tropical_fish_anim, 0.5, 0.5, 0.5)

    //Legs? idk the name for those fish thingies
    animator.rotateZ(6, 9, 15 * Math.sin(a * 3) * tropical_fish_anim, 0.5, 0.5 , 0.5)
}

if (I.isOf(data.item, Items.get("minecraft:cod_bucket"))) {
    //Body parts
    animator.scale(0, 25, 1 - 0.1 * cod_anim, 1 - 0.1 * cod_anim, 1 - 0.1  * cod_anim)
    animator.moveY(0, 25, fall * 0.1 * cod_anim)
    animator.moveY(0, 25, 0.05 * cod_anim)
    animator.moveY(0, 25, 0.02 * Math.sin(a * -1.5)  * (1-walkSmoother) * cod_anim)
    animator.moveY(0, 25, 0.08 * Easings.easeInSine(Math.abs(Math.sin(walk))) * walkSmoother * cod_anim)
    animator.moveZ(0, 25, 0.1 * cod_anim)
    animator.moveY(0, 25, -0.03 * cod_anim)
    animator.moveY(0, 25, (-ywAngle * 0.002)  * cod_anim)
    animator.rotateX(0, 25, -ywAngle * 0.6 * cod_anim , 0.5, 0.5, 0.5)
    animator.rotateZ(0, 25, -ptAngle * 0.6 * cod_anim  , 0.5, 0.5, 0.5)
    animator.rotateX(0, 25, 3 * Math.sin(a * 1.5)  * (1-walkSmoother) * cod_anim, 0.5, 0.5, 0.5)
    animator.rotateX(0, 25, fall * -10 * cod_anim, 0.5, 0.5, 0.5)

    //Head parts
    animator.moveX(10,21, -0.05 * l * cod_anim)
    animator.rotateX(10,21, -ywAngle * 0.2 * cod_anim, 0, 0.6, 0.2)
    animator.rotateX(10,21, fall * -5 * cod_anim, 0, 0.6, 0.2)
}
if (I.isOf(data.item, Items.get("minecraft:salmon_bucket"))) {
    animator.scale(0, 52, 1 - 0.15 * salmon_anim, 1 - 0.15 * salmon_anim, 1 - 0.15 * salmon_anim)
    animator.moveY(0, 52, fall * 0.1 * salmon_anim)
    animator.moveY(0, 52, 0.04 * salmon_anim)
    animator.moveY(0, 52, 0.02 * Math.sin(a * -1.5)  * (1-walkSmoother) * salmon_anim)
    animator.moveY(0, 52, 0.08 * Easings.easeInSine(Math.abs(Math.sin(walk))) * walkSmoother * salmon_anim)
    animator.moveZ(0, 52, 0.1 * salmon_anim)
    animator.moveY(0, 52, (-ywAngle * 0.002) * salmon_anim )
    animator.rotateX(0, 52, -ywAngle * 0.6 * salmon_anim  , 1, 0.5, 0.5)
    animator.rotateZ(0, 52, -ptAngle * 0.6 * salmon_anim  , 0.5, 0.5, 0.5)
    animator.rotateX(0, 52, 3 * Math.sin(a * 1.5)  * (1-walkSmoother) * salmon_anim , 0.5, 0.5, 0.5)
    animator.rotateX(0, 52, fall * -10 * salmon_anim , 0.5, 0.5, 0.5)

    //Head parts
    animator.rotateX(0, 5, -ywAngle * 0.2 * salmon_anim , 0, 0.6, 0.25)
    animator.rotateX(0, 5, fall * -5 * salmon_anim , 0, 0.6, 0.25)
}

if (I.isOf(data.item, Items.get("minecraft:tadpole_bucket"))) {
    animator.moveY(0, 7, fall * 0.1 * tadpole_anim)
    animator.moveY(0, 7, 0.02 * Math.sin(a * -1.5)  * (1-walkSmoother) * tadpole_anim)
    animator.moveY(0, 7, 0.08 * Easings.easeInSine(Math.abs(Math.sin(walk))) * walkSmoother * tadpole_anim)
    animator.moveZ(0, 7, 0.1 * tadpole_anim)
    animator.moveY(0, 7, -0.03 * tadpole_anim)
    animator.moveY(0, 7, (-ywAngle * 0.002)  * tadpole_anim)
    animator.rotateY(0, 7, -ywAngle * 0.6 * tadpole_anim  , 0.5, 0.5, 0.5)
    animator.rotateX(0, 7, -ptAngle * 0.6 * tadpole_anim , 0.5, 0.5, 0.5)
    animator.rotateZ(0, 7, -ywAngle * 0.6 * tadpole_anim  , 0.5, 0.5, 0.5)
    animator.rotateX(0, 7, 3 * Math.sin(a * 3)  * (1-walkSmoother) * tadpole_anim, 0.5, 0.5, 0.5)
}

if (I.isOf(data.item, Items.get("minecraft:pufferfish_bucket"))) {
    //Body parts
    animator.moveY(0, 29, fall * 0.1 * pufferfish_anim)
    animator.moveY(0, 29, 0.02 * Math.sin(a * -1.5)  * (1-walkSmoother) * pufferfish_anim)
    animator.moveY(0, 29, 0.08 * Easings.easeInSine(Math.abs(Math.sin(walk))) * walkSmoother * pufferfish_anim)
    animator.moveZ(0, 29, 0.1 * pufferfish_anim)
    animator.moveY(0, 29, -0.03 * pufferfish_anim)
    animator.moveY(0, 29, (-ywAngle * 0.002)  * pufferfish_anim)
    animator.rotateX(0, 29, M.abs(-ywAngle) * 0.2 * pufferfish_anim, 0.5, 0.5, 0.5)
    animator.rotateX(0, 29, -ptAngle * 0.2 * pufferfish_anim, 0.5, 0.5, 0.5)
    animator.rotateZ(0, 29, -ywAngle * 0.2 * pufferfish_anim, 1, 0.5, 0.5)
    animator.rotateX(0, 29, 1 * Math.sin(a * 3)  * (1-walkSmoother) * pufferfish_anim, 0.5, 0.5, 0.5)

    //Again legs? or hands? wtf is a word for those things
    //OH FUCK, it's fins!. NVM, let this shit be here as an easter egg
    animator.rotateZ(6, 7, 10 * Math.sin(a * 10) * M.pow(Math.sin(a * 0.4), 30) * pufferfish_anim, 0.5, 0.5, 0.5)
    animator.rotateZ(8, 9, -10 * Math.sin(a * 10) * M.pow(Math.sin(a * 0.4), 30) * pufferfish_anim, 0.5, 0.5, 0.5)
}

if (I.isIn(data.item, Tags.getVanillaTag("chest_boats"))) {
    animator.moveY(0, 15, M.clamp(fall * 0.1, 0, 1) * chest_boat_anim)
    //animator:moveY(0, 15, 0.08 * Easings:easeInSine(math.abs(math.sin(walk))) * walkSmoother)
    animator.rotateX(0, 11, M.clamp(ptAngle * 1.35, 0, 999) * chest_boat_anim , 0.5, 0.5, 0.8)
    //animator:rotateX(0, 15, ptAngle * 0.25, 0.5, 0.5, 0.5)
    animator.rotateZ(0, 15, -ywAngle * 0.12 * chest_boat_anim, 0.5, 0, 0.5)

}

//---- BODY---------------------------------------------------------------------------------
//if I:isOf(renderedItem, Items:get("minecraft:pufferfish_bucket")) and index >= 0 and index <= 29 then

//	if index >= 6 and index <= 7 then
//		M:rotateZ(matrices, 10 * math.sin(a * 6), 0.6, 0.75 , 0.3)
//	end
//	if index >= 8 and index <= 9 then
//		M:rotateZ(matrices, -10 * math.sin(a * 6), 0.6, 0.75 , 0.3)
//	end
//end


//-- if I:isIn(renderedItem, Tags:getVanillaTag("saplings")) and index >= 0 and index <= 3 and data.mainHand then
//-- 	M:rotateX(matrices, -ywAngle * 0.6, -0.4, 0 , 0.4)
//-- 	M:rotateZ(matrices, ptAngle * 0.6, 0.4 ,0, -0.4)
//-- end

//-- if I:isIn(renderedItem, Tags:getVanillaTag("saplings")) and index >= 0 and index <= 3 and not data.mainHand then
//-- 	M:rotateZ(matrices, ywAngle * 0.6, 0.4 ,0, -0.4)
//-- 	M:rotateX(matrices, -ptAngle * 0.6, -0.4, 0 , 0.4)
//-- end

//-- if I:isOf(renderedItem, Items:get("minecraft:mangrove_propagule")) and index >= 4 and index <= 7 and data.mainHand then
//-- 	M:rotateX(matrices, -ywAngle * 0.6, -0.4, 0 , 0.4)
//-- 	M:rotateZ(matrices, ptAngle * 0.6, 0.4 ,0, -0.4)
//-- end
//-- if I:isOf(renderedItem, Items:get("minecraft:mangrove_propagule")) and index >= 4 and index <= 7 and not data.mainHand then
//-- 	M:rotateZ(matrices, ywAngle * 0.6, 0.4 ,0, -0.4)
//-- 	M:rotateX(matrices, -ptAngle * 0.6, -0.4, 0 , 0.4)
//-- end

//-- if (I:isOf(renderedItem, Items:get("minecraft:short_grass")) or I:isOf(renderedItem, Items:get("minecraft:short_dry_grass")) or I:isOf(renderedItem, Items:get("minecraft:tall_dry_grass"))) and index >= 0 and index <= 3 and data.mainHand then
//-- 	M:rotateX(matrices, -ywAngle * 0.45, -0.4, 0 , 0.4)
//-- 	M:rotateZ(matrices, ptAngle * 0.45, 0.4 ,0, -0.4)
//-- end

//-- if (I:isOf(renderedItem, Items:get("minecraft:short_grass")) or I:isOf(renderedItem, Items:get("minecraft:short_dry_grass")) or I:isOf(renderedItem, Items:get("minecraft:tall_dry_grass")) ) and index >= 0 and index <= 3 and not data.mainHand then
//-- 	M:rotateZ(matrices, ywAngle * 0.6, 0.4 ,0, -0.4)
//-- 	M:rotateX(matrices, -ptAngle * 0.45, -0.4, 0 , 0.4)
//-- end

//-- if I:isOf(renderedItem, Items:get("minecraft:tall_grass")) and index >= 0 and index <= 6 and data.mainHand then
//-- 	M:rotateX(matrices, -ywAngle * 0.6, -0.4, 0 , 0.4)
//-- 	M:rotateZ(matrices, ptAngle * 0.6, 0.4 ,0, -0.4)
//-- 	if index >= 0 and index <= 3 then
//-- 		M:rotateX(matrices, -ywAngle * 0.2, -0.45, 0 , 0.45)
//-- 		M:rotateZ(matrices, ptAngle * 0.2, 0.45 ,0, -0.45)
//-- 	end
//-- end
//-- if I:isOf(renderedItem, Items:get("minecraft:tall_grass")) and index >= 0 and index <= 6 and not data.mainHand then
//-- 	M:rotateZ(matrices, ywAngle * 0.6, 0.4 ,0, -0.4)
//-- 	M:rotateX(matrices, -ptAngle * 0.6, -0.4, 0 , 0.4)
//-- 	if index >= 0 and index <= 3 then
//-- 		M:rotateZ(matrices, ywAngle * 0.2, 0.45 ,0, -0.45)
//-- 	M:rotateX(matrices, -ptAngle * 0.2, -0.45, 0 , 0.45)
//-- 	end
//-- end




//if (I:isOf(renderedItem, Items:get("minecraft:oak_chest_boat")) or
//I:isOf(renderedItem, Items:get("minecraft:spruce_chest_boat")) or
//I:isOf(renderedItem, Items:get("minecraft:dark_oak_chest_boat")) or
//I:isOf(renderedItem, Items:get("minecraft:pale_oak_chest_boat")) or
//I:isOf(renderedItem, Items:get("minecraft:acacia_chest_boat")) or
//I:isOf(renderedItem, Items:get("minecraft:jungle_chest_boat")) or
//I:isOf(renderedItem, Items:get("minecraft:birch_chest_boat")) or
//I:isOf(renderedItem, Items:get("minecraft:bamboo_chest_raft"))
//)then
//    if not I:isOf(renderedItem, Items:get("minecraft:bamboo_chest_raft")) and index >= 60 and index <= 71 then
//        M:rotateX(matrices, M:clamp(ptAngle, 0, 999), -0.4, 0.4 , 0.7)
//    elseif I:isOf(renderedItem, Items:get("minecraft:bamboo_chest_raft")) and index >= 42 and index <= 53 then
//        M:rotateX(matrices, M:clamp(ptAngle, 0, 999), -0.4, 0.4 , 0.7)
//    end
//    if index >= 0 and index <= 11 then
//        M:moveY(matrices, ptAngle * -0.003)
//        M:rotateZ(matrices, M:clamp(ptAngle, 0, 999), 0.4, 0.4 , -0.4)
//    end
//    if index >= 12 and index <= 23 then
//        M:moveY(matrices, ptAngle * 0.003)
//        M:rotateZ(matrices, M:clamp(-ptAngle, 0, 999), 0.4, 0.4 , -0.4)
//    end
//end


//---- HEAD---------------------------------------------------------------------------------
//if I:isOf(renderedItem, Items:get("minecraft:axolotl_bucket")) and index >= 0 and index <= 11 then
//	M:moveY(matrices, fall * 0.02)
//	M:moveY(matrices, 0.02 * math.sin(a * -1.5)  * (1-walkSmoother))
//	M:moveY(matrices, 0.08 * Easings:easeInSine(math.abs(math.sin(walk))) * walkSmoother)

//	M:rotateX(matrices, M:clamp(20 * fall, -20, 20), -0.4, 0.45 , 0.3)
//	M:rotateZ(matrices, 2 * math.sin(a), 0.4, 0.45 , -0.3)
//	-- M:rotateY(matrices, M:clamp(80 * math.sin(a * 0.8), -10, 10) * (1-walkSmoother), 0.4, 0.45 , 0.3)
//	-- M:rotateY(matrices, 2 * math.sin(a) * (1-walkSmoother), 0.4, 0.45 , 0.3)
//	M:rotateX(matrices, ptAngle * 0.3 , -0.4, 0.55 , 0.3)
//	M:rotateZ(matrices, -ywAngle * 0.3 , 0.4, 0.65 , -0.3)
//end
//------------------------------------------------------------------------------------------

//---- BODY---------------------------------------------------------------------------------
//if I:isOf(renderedItem, Items:get("minecraft:axolotl_bucket")) and index >= 12 and index <= 19 then
//	M:moveY(matrices, fall * 0.02)
//	M:moveY(matrices, 0.02 * math.sin(a * -1.5) * (1-walkSmoother))
//	M:moveY(matrices, 0.08 * Easings:easeInSine(math.abs(math.sin(walk))) * walkSmoother)

//	M:rotateX(matrices, M:clamp(15 * fall, -20, 20), -0.4, 0.45 , 0.3)
//	M:rotateZ(matrices, 1 * math.sin(a), 0.4, 0.45 , -0.3)

//	M:rotateX(matrices, ptAngle * 0.15 , -0.4, 0.55 , 0.3)
//	M:rotateZ(matrices, -ywAngle * 0.15 , 0.4, 0.65 , -0.3)
//end
//------------------------------------------------------------------------------------------



//---- BODY---------------------------------------------------------------------------------
//if I:isOf(renderedItem, Items:get("minecraft:tropical_fish_bucket")) and index >= 0 and index <= 12 then
//	M:moveY(matrices, fall * 0.1)
//	M:moveY(matrices, 0.02 * math.sin(a * -1.5)  * (1-walkSmoother))
//	M:moveY(matrices, 0.08 * Easings:easeInSine(math.abs(math.sin(walk))) * walkSmoother)
//	M:moveZ(matrices, 0.1)
//	M:moveY(matrices, -0.03)
//	M:moveY(matrices, (-ywAngle * 0.002) )
//	--M:moveZ(matrices, ywAngle * 0.007 )
//	M:rotateX(matrices, -ywAngle , -0.8, 0.55 , 0.3)
//	M:rotateZ(matrices, -ptAngle , 0.8, 0.75 , 0.3)
//	M:rotateX(matrices, 3 * math.sin(a * 3)  * (1-walkSmoother), -0.6, 0.5 , 0.3)
//end
//------------------------------------------------------------------------------------------

//---- LEGS---------------------------------------------------------------------------------
//if I:isOf(renderedItem, Items:get("minecraft:tropical_fish_bucket")) and index >= 6 and index <= 9 then
//	--M:moveZ(matrices, ywAngle * 0.007 )
//	M:rotateZ(matrices, 15 * math.sin(a * 3), 0.4, 0.45 , -0.3)
//end
//------------------------------------------------------------------------------------------

//---- LEGS---------------------------------------------------------------------------------
//if I:isOf(renderedItem, Items:get("minecraft:tropical_fish_bucket")) and index >= 12 and index <= 13 then
//	--M:moveZ(matrices, ywAngle * 0.007 )
//	M:rotateY(matrices, 25 * math.sin(a * 3), 0.45, 0.45 , 0.45)
//end
//------------------------------------------------------------------------------------------


//---- BODY---------------------------------------------------------------------------------
//if I:isOf(renderedItem, Items:get("minecraft:cod_bucket")) and index >= 0 and index <= 25 then
//	M:scale(matrices, 0.9, 0.9, 0.9)
//	M:moveY(matrices, fall * 0.1)
//	M:moveY(matrices, 0.05)
//	M:moveY(matrices, 0.02 * math.sin(a * -1.5)  * (1-walkSmoother))
//	M:moveY(matrices, 0.08 * Easings:easeInSine(math.abs(math.sin(walk))) * walkSmoother)
//	M:moveZ(matrices, 0.1)
//	M:moveY(matrices, -0.03)
//	M:moveY(matrices, (-ywAngle * 0.002) )
//	--M:moveZ(matrices, ywAngle * 0.007 )
//	M:rotateX(matrices, -ywAngle * 0.6 , -0.8, 0.55 , 0.3)
//	M:rotateZ(matrices, -ptAngle * 0.6  , 0.8, 0.75 , 0.3)
//	M:rotateX(matrices, 3 * math.sin(a * 1.5)  * (1-walkSmoother), -0.6, 0.5 , 0.3)
//	M:rotateX(matrices, fall * -10, -0.8, 0.55 , 0.3)
//	if index >= 10 and index <= 21 then
//		M:rotateX(matrices, -ywAngle * 0.2 , -0.8, 0.55 , 0.3)
//		--M:rotateZ(matrices, -ptAngle * 0.2  , 0.8, 0.75 , 0.3)
//		M:rotateX(matrices, fall * -5, -0.8, 0.55 , 0.3)
//	end
//end
//------------------------------------------------------------------------------------------


//------------------------------------------------------------------------------------------




//if I:isOf(renderedItem, Items:get("minecraft:bordure_indented_banner_pattern")) or I:isOf(renderedItem, Items:get("minecraft:creeper_banner_pattern")) or I:isOf(renderedItem, Items:get("minecraft:piglin_banner_pattern")) or I:isOf(renderedItem, Items:get("minecraft:flower_banner_pattern")) or I:isOf(renderedItem, Items:get("minecraft:field_masoned_banner_pattern")) or I:isOf(renderedItem, Items:get("minecraft:skull_banner_pattern")) or I:isOf(renderedItem, Items:get("minecraft:mojang_banner_pattern")) or I:isOf(renderedItem, Items:get("minecraft:guster_banner_pattern")) or I:isOf(renderedItem, Items:get("minecraft:globe_banner_pattern")) or I:isOf(renderedItem, Items:get("minecraft:flow_banner_pattern")) then
//    if index >= 5 and index <= 11 then
//        M:rotateX(matrices, M:clamp(P:getPitch(data.player) / 2.5, -20, 90) + ptAngle, 0, 0.6, 0.4)
//        M:rotateZ(matrices, ywAngle * -0.24, 0.4, 0.6, -0.4)
//    end
//end


// Persist the original HMI global.* state between frames.
__hmi_registry['yawAngle'] = yawAngle;
__hmi_registry['yawAngleO'] = yawAngleO;
__hmi_registry['pitchAngle'] = pitchAngle;
__hmi_registry['pitchAngleO'] = pitchAngleO;
__hmi_registry['fall'] = fall;
__hmi_registry['a'] = a;
__hmi_registry['walk'] = walk;
__hmi_registry['walkSmoother'] = walkSmoother;
__hmi_registry['fall_f'] = fall_f;
__hmi_registry['jiggle_f'] = jiggle_f;
__hmi_registry['mainHandSwitch'] = mainHandSwitch;
__hmi_registry['offHandSwitch'] = offHandSwitch;
__hmi_registry['jiggle_i'] = jiggle_i;
__hmi_registry['axolotl_anim'] = axolotl_anim;
__hmi_registry['pufferfish_anim'] = pufferfish_anim;
__hmi_registry['salmon_anim'] = salmon_anim;
__hmi_registry['cod_anim'] = cod_anim;
__hmi_registry['tadpole_anim'] = tadpole_anim;
__hmi_registry['liquid_anim'] = liquid_anim;
__hmi_registry['tropical_fish_anim'] = tropical_fish_anim;
__hmi_registry['chest_boat_anim'] = chest_boat_anim;
