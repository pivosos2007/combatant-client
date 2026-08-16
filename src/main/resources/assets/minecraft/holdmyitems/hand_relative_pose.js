var l = (context.bl ? 1 : -1)


var foodCount = Object.prototype.hasOwnProperty.call(__hmi_registry, 'foodCount') ? __hmi_registry['foodCount'] : (0.0);
var mainHandSwitch = Object.prototype.hasOwnProperty.call(__hmi_registry, 'mainHandSwitch') ? __hmi_registry['mainHandSwitch'] : (0.0);
var offHandSwitch = Object.prototype.hasOwnProperty.call(__hmi_registry, 'offHandSwitch') ? __hmi_registry['offHandSwitch'] : (0.0);
var drinkCount = Object.prototype.hasOwnProperty.call(__hmi_registry, 'drinkCount') ? __hmi_registry['drinkCount'] : (0.0);

if (I.isEmpty(context.item) && drinkCount > 0) {
    M.moveX(context.matrices, 1.5 * l)
    M.moveY(context.matrices, -0.3)
    M.moveZ(context.matrices, -0.47)
    M.rotateX(context.matrices, 15, 0.5 * l, 0.5, 0.5)
    M.rotateY(context.matrices, 35 * l, 0.5 * l, 0.5, 0.5)
    M.rotateZ(context.matrices, -65 * l, 0.5 * l, 0.5, 0.5)
    M.scale(context.matrices, 0.9, 0.9, 0.9)
}

var switch_val = (context.mainHand ? mainHandSwitch : offHandSwitch)
var switchAnimationVariable = Easings.easeInBack(M.sin(M.clamp(switch_val, 0.09723, 0.60632) * 3.24 * 1.65 - 0.1))

if ((I.isIn(context.item, Tags.getVanillaTag("bundles")) || I.isOf(context.item, Items.get("minecraft:ender_pearl")) || I.isOf(context.item, Items.get("minecraft:ender_eye")) || I.isThrowable(context.item) || I.isIn(context.item, Tags.getFabricTag("music_discs")) || I.isIn(context.item, Tags.getFabricTag("nuggets")) || I.isIn(context.item, Tags.getVanillaTag("skulls"))) && I.getUseAction(context.item) != "trident") {
    M.rotateX(context.matrices, 10 * switchAnimationVariable)
    M.rotateZ(context.matrices, 6 * switchAnimationVariable)
}

var musicDiscHandTilt;
if (mainHandSwitch < 0.65245) {
    musicDiscHandTilt = M.sin(M.clamp(mainHandSwitch, 0, 0.16675) * 3.14 * 3)
} else {
    musicDiscHandTilt = M.sin(M.clamp(mainHandSwitch, 0.65245, 1) * 4.4 - 1.3)
}

var musicDiscHandJump = M.sin(M.clamp(mainHandSwitch, 0.52459, 0.85809) * 3.14 * 3 - 1.8)

// This is the holding-arm basis used by the original HMI renderer after
// hand_relative_pose. It is required when an item is present; without it the vanilla
// first-person arm sits through the middle of the item instead of closing around the grip.
if (!I.isEmpty(context.item)) {
    M.translate(context.matrices, 1.5 * l, -0.3, -0.6)
    M.rotateX(context.matrices, 15, 0.5 * l, 0.5, 0.5)
    M.rotateY(context.matrices, 35 * l, 0.5 * l, 0.5, 0.5)
    M.rotateZ(context.matrices, -65 * l, 0.5 * l, 0.5, 0.5)
    M.scale(context.matrices, 0.9, 0.9, 0.9)
}

// Persist the original HMI global.* state between frames.
__hmi_registry['foodCount'] = foodCount;
__hmi_registry['mainHandSwitch'] = mainHandSwitch;
__hmi_registry['offHandSwitch'] = offHandSwitch;
__hmi_registry['drinkCount'] = drinkCount;
