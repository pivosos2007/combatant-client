package net.clayborn.accurateblockplacement.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.clayborn.accurateblockplacement.AccurateBlockPlacementMod;
import net.clayborn.accurateblockplacement.IMinecraftClientAccessor;
import net.clayborn.accurateblockplacement.IKeyBindingAccessor;
import net.clayborn.accurateblockplacement.config.AccurateBlockPlacementConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin implements IMinecraftClientAccessor {
	@Shadow
	protected abstract void startUseItem();

	@Shadow
	private int rightClickDelay;
	
	@Override
	public void accurateblockplacement_DoItemUseBypassDisable() {
		Boolean oldValue = AccurateBlockPlacementMod.disableNormalItemUse;
		AccurateBlockPlacementMod.disableNormalItemUse = false;
		startUseItem();
		AccurateBlockPlacementMod.disableNormalItemUse = oldValue;
	}
	
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	void OnDoItemUse(CallbackInfo info) {
		if(AccurateBlockPlacementMod.disableNormalItemUse) {
			info.cancel();
		}
	}
	
	@Override
	public void accurateblockplacement_SetItemUseCooldown(int cooldown) {
		rightClickDelay = cooldown;
	}
	
	@Override
	public int accurateblockplacement_GetItemUseCooldown() {
		return rightClickDelay;
	}

	// Note: In 26.1+ GameRenderer.pick() was moved to Minecraft.pick()
	// Everything below this point is the code previously living in GameRendererMixin.java
	// ABP:R is absolutely dependent on .pick() and the raycast/hitresult it returns,
	// and basically nothing else, so wherever it moves we move with it.
	// This is going to make supporting older versions interesting innit?
	@Unique
	private static final String blockActivateMethodName = getBlockActivateMethodName();
	@Unique
	private static final String itemUseMethodName = getItemUseMethodName();
	@Unique
	private BlockPos lastSeenBlockPos = null;
	@Unique
	private BlockPos lastPlacedBlockPos = null;
	@Unique
	private Vec3 lastPlayerPlacedBlockPos = null;
	@Unique
	private Boolean autoRepeatWaitingOnCooldown = true;
	@Unique
	private Vec3 lastFreshPressMouseRatio = null;
	@Unique
	private final ArrayList<HitResult> backFillList = new ArrayList<>();

	@Unique
	InteractionHand handOfCurrentItemInUse;

	@Unique
	private static boolean isItemAllowed(ItemStack stack) {
		if (stack.isEmpty()) return false;
		Item item = stack.getItem();

		return item instanceof BlockItem ||
				(AccurateBlockPlacementConfig.toolsEnabled &&
						(stack.has(DataComponents.BLOCK_TRANSFORMER) || item instanceof FlintAndSteelItem)) ||
				(AccurateBlockPlacementConfig.bucketEnabled && item instanceof BucketItem) ||
				(AccurateBlockPlacementConfig.armorStandEnabled && item instanceof ArmorStandItem) ||
				(AccurateBlockPlacementConfig.itemFrameEnabled && item instanceof ItemFrameItem) ||
				(AccurateBlockPlacementConfig.spawnEggsEnabled && item instanceof SpawnEggItem);
	}

	@Unique
	private Item getItemInUse(Minecraft client) {
		// have to check each hand
		InteractionHand[] hands = InteractionHand.values();

        for (InteractionHand thisHand : hands) {
            assert client.player != null;
            ItemStack itemInHand = client.player.getItemInHand(thisHand);

            if (itemInHand.isEmpty()) {
				// hand is empty try the next one
				continue;
			}
            if (itemInHand instanceof ItemStack && isItemAllowed(itemInHand)) {
                // found a block
                // or found an item that can be placed, used or interacted with a block
                handOfCurrentItemInUse = thisHand;
                return itemInHand.getItem();
            }
        }

		return null;
	}

	@Unique
	private static String getBlockActivateMethodName() {
		Method[] methods = Block.class.getMethods();

		for(Method method : methods) {
			Class<?>[] types = method.getParameterTypes();

			if(types.length != 6) {
				continue;
			}
			if(types[0] != BlockState.class) {
				continue;
			}
			if(types[1] != Level.class) {
				continue;
			}
			if(types[2] != BlockPos.class) {
				continue;
			}
			if(types[3] != Player.class) {
				continue;
			}
			if(types[4] != InteractionHand.class) {
				continue;
			}
			if(types[5] != BlockHitResult.class) {
				continue;
			}
			return method.getName();
		}
		return null;
	}

	@Unique
	private static String getItemUseMethodName() {
		try {
			Method useMethod = Item.class.getDeclaredMethod("use", Level.class, Player.class, InteractionHand.class);
			return useMethod.getName();
		}
		catch (NoSuchMethodException e) {
			return null;
		}
	}

	@Unique
	private static boolean doesBlockHaveOverriddenActivateMethod(Block block) {
		if(blockActivateMethodName == null) {
			return false;
		}

		try {
			Method activateMethod = block.getClass().getDeclaredMethod(blockActivateMethodName, BlockState.class, Level.class, BlockPos.class, Player.class, InteractionHand.class, BlockHitResult.class);
			return activateMethod.getDeclaringClass()!= Block.class;
		}
		catch (NoSuchMethodException e) {
			return false;
		}

	}

	@Unique
	private static boolean doesItemHaveOverriddenUseMethod(ItemStack stack) {
		if (stack.isEmpty()) return false;
		Item item = stack.getItem();
		/*
		  Have to mark other Item types via isItemAllowed(),
		  because they have vanilla usages that would get
		  flagged, despite being usable in ABP:R.
		 */
		if(itemUseMethodName == null || isItemAllowed(stack)) {
			return false;
		}

		try {
			Method useMethod = item.getClass().getDeclaredMethod(itemUseMethodName, ItemStack.class, Level.class, Player.class, InteractionHand.class, BlockHitResult.class);
			return useMethod.getDeclaringClass()!= Item.class;
		}
		catch (NoSuchMethodException e) {
			return false;
		}
	}

	@Inject(method = "pick", at = @At("RETURN"))
	private void onUpdateTargetedEntityComplete(CallbackInfo info) {
		if(!AccurateBlockPlacementMod.isAccurateBlockPlacementEnabled) {
			// reset all state just in case
			AccurateBlockPlacementMod.disableNormalItemUse = false;

			lastSeenBlockPos = null;
			lastPlacedBlockPos = null;
			lastPlayerPlacedBlockPos = null;

			autoRepeatWaitingOnCooldown = true;
			backFillList.clear();

			lastFreshPressMouseRatio = null;

			return;
		}

		Minecraft client = Minecraft.getInstance();

		// safety checks
		if(client.hitResult == null || client.player == null || client.level == null) {
			return;
		}

		// will be set to true only if needed
		AccurateBlockPlacementMod.disableNormalItemUse = false;
		IKeyBindingAccessor keyUseAccessor = (IKeyBindingAccessor) client.options.keyUse;
		boolean freshKeyPress = keyUseAccessor.accurateblockplacement_GetTimesPressed() > 0;

		Item currentItem = getItemInUse(client);

		// if nothing in hand, let vanilla take over
		if (currentItem == null) return;

		ItemStack currentItemStack = client.player.getItemInHand(handOfCurrentItemInUse);

		// reset state if the key was actually pressed
		// note: at very low frame rates they might have let go and hit it again before
		// we get back here
		if(freshKeyPress) {
			// clear history since they let go of the button
			lastSeenBlockPos = null;
			lastPlacedBlockPos = null;
			lastPlayerPlacedBlockPos = null;

			autoRepeatWaitingOnCooldown = true;
			backFillList.clear();

			if(client.getWindow().getScreenWidth() > 0 && client.getWindow().getScreenHeight() > 0) {
				lastFreshPressMouseRatio = new Vec3(client.mouseHandler.xpos() / client.getWindow().getScreenWidth(), client.mouseHandler.ypos() / client.getWindow().getScreenHeight(), 0);
			}
			else {
				lastFreshPressMouseRatio = null;
			}
		}

		// if player is actively using an item (i.e. a shield), let vanilla take over
		if(client.player.isUsingItem()) {
			return;
		}

		// if the item isn't allowed, let vanilla take over
		if(!isItemAllowed(currentItemStack)) {
			return;
		}

		// if the item we are holding is activatable, let vanilla take over
		// important to note that this will NOT catch Hoes, Shovels or Axes, as they're exempted in the method called
		if(doesItemHaveOverriddenUseMethod(currentItemStack)) {
			return;
		}

		// if we aren't looking at a block, let vanilla take over
		if(client.hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}

		// check the other hand if it has something in use and if so let vanilla take over
		InteractionHand otherHand = handOfCurrentItemInUse == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack otherHandItemStack = client.player.getItemInHand(otherHand);
		if(!otherHandItemStack.isEmpty() && (doesItemHaveOverriddenUseMethod(otherHandItemStack)) && client.player.isUsingItem()) {
			return;
		}

		BlockHitResult blockHitResult = (BlockHitResult) client.hitResult;
		BlockPos blockHitPos = blockHitResult.getBlockPos();
		Block targetBlock = client.level.getBlockState(blockHitPos).getBlock();
		boolean isTargetBlockActivatable = doesBlockHaveOverriddenActivateMethod(targetBlock);

		// don't override behavior of clicking activatable blocks (and stairs) unless holding SNEAKING to replicate vanilla behaviors
		if(isTargetBlockActivatable && !(targetBlock instanceof StairBlock) && !client.player.isShiftKeyDown()) {
			return;
		}

		// if the target block is a BaseEntityBlock (i.e. storage container) or BedBlock and we are actively placing blocks, prevent interaction
		if ((targetBlock instanceof BaseEntityBlock || targetBlock instanceof BedBlock) && !freshKeyPress && client.options.keyUse.isDown()) {
			return;
		}

		// if the hand item and target block are both scaffolding, let vanilla take over
		if ((currentItem instanceof ScaffoldingBlockItem) && (targetBlock instanceof ScaffoldingBlock)) {
			return;
		}

		// if the target block is a composter and the held item is compostable, let vanilla take over
		if (targetBlock instanceof ComposterBlock && currentItemStack.has(DataComponents.COMPOSTABLE)) {
			return;
		}

		// if the mainhand item is bonemeal and the target block is bonemealable, let vanilla take over
		ItemStack mainHandItem = client.player.getItemInHand(InteractionHand.MAIN_HAND);
		if (mainHandItem.getItem() instanceof BoneMealItem && targetBlock instanceof BonemealableBlock) {
			return;
		}
		
		// are they holding the use key and is the item to use a block?
		// also is the SAME item we started with if we are in repeat mode?
		// note: check both freshKey and current state in cause of shitty frame rates
		if((freshKeyPress || client.options.keyUse.isDown())) {
			// it's a block!! it's go time!
			AccurateBlockPlacementMod.disableNormalItemUse = true;

			BlockPlaceContext targetPlacement = new BlockPlaceContext(new UseOnContext(client.player, handOfCurrentItemInUse, blockHitResult));

			// remember what was there before
			Block oldBlock = client.level.getBlockState(targetPlacement.getClickedPos()).getBlock();

			double facingAxisPlayerPos = 0.0d;
			double facingAxisPlayerLastPos = 0.0d;
			double facingAxisLastPlacedPos = 0.0d;

			if(lastPlacedBlockPos != null && lastPlayerPlacedBlockPos != null) {
				Direction.Axis axis = targetPlacement.getClickedFace().getAxis();

				facingAxisPlayerPos = client.player.position().get(axis);
				facingAxisPlayerLastPos = lastPlayerPlacedBlockPos.get(axis);
				facingAxisLastPlacedPos = new Vec3(lastPlacedBlockPos.getX(), lastPlacedBlockPos.getY(), lastPlacedBlockPos.getZ()).get(axis);

				// fixes placement being directional because getting the correct side pos is apparently too hard
				if(targetPlacement.getClickedFace().toString().equals("west") || targetPlacement.getClickedFace().toString().equals("north")) {
					facingAxisLastPlacedPos += 1.0d;
				}
			}

			IMinecraftClientAccessor clientAccessor = (IMinecraftClientAccessor) client;

			Vec3 currentMouseRatio = null;

			if(client.getWindow().getScreenWidth() > 0 && client.getWindow().getScreenHeight() > 0) {
				currentMouseRatio = new Vec3(client.mouseHandler.xpos() / client.getWindow().getScreenWidth(), client.mouseHandler.ypos() / client.getWindow().getScreenHeight(), 0);
			}

			// Condition:
			// [ [ we have a fresh key press ] OR
			// [ [ we have no 'seen' history or the 'seen' history isn't a match ] AND
			// [ we have no 'place' history or the 'place' history isn't a match ] ] OR
			// [ we have 'place' history, it is a match, the player is building toward
			// themselves and has moved one block backwards] ]
			boolean isPlacementTargetFresh = ((lastSeenBlockPos == null || !lastSeenBlockPos.equals(blockHitPos))
					&& (lastPlacedBlockPos == null || !lastPlacedBlockPos.equals(blockHitPos)))
					|| (lastPlacedBlockPos != null && lastPlayerPlacedBlockPos != null
					&& lastPlacedBlockPos.equals(blockHitPos)
					&& Math.abs(facingAxisPlayerLastPos - facingAxisPlayerPos) >= 0.99d // because precision
					&& Math.abs(facingAxisPlayerLastPos - facingAxisLastPlacedPos) < Math.abs(facingAxisPlayerPos - facingAxisLastPlacedPos));

			boolean hasMouseMoved = (currentMouseRatio != null && lastFreshPressMouseRatio != null && lastFreshPressMouseRatio.distanceTo(currentMouseRatio) >= 0.1);

			boolean isOnCooldown = autoRepeatWaitingOnCooldown && clientAccessor.accurateblockplacement_GetItemUseCooldown() > 0 && !hasMouseMoved;

			// if [ [ this is a fresh keypress ] OR
			// [ [ we have a fresh place to put a block ] AND
			// [ auto-repeat isn't on cooldown OR the mouse has moved enough ] ]
			// we can try to place a block
			// note: this is always true on a fresh keypress
			if(freshKeyPress || (isPlacementTargetFresh && !isOnCooldown)) {
				// update if we are repeating
				if(autoRepeatWaitingOnCooldown && !freshKeyPress) {
					autoRepeatWaitingOnCooldown = false;

					HitResult currentHitResult = client.hitResult;

					// try to place the backlog
					for(HitResult prevHitResult : backFillList)	{
						client.hitResult = prevHitResult;
						// use item
						clientAccessor.accurateblockplacement_DoItemUseBypassDisable();
					}

					backFillList.clear();

					client.hitResult = currentHitResult;
				}

				// always run at least once if we reach here
				// if this isn't a fresh key press, turn on the run once flag
				boolean runOnceFlag = !freshKeyPress;

				// in case they manage to push the button multiple times per frame
				// note: we already subtracted one from the press count earlier so the total
				// should be the same
				while(runOnceFlag || client.options.keyUse.consumeClick()) {
					// use item
					clientAccessor.accurateblockplacement_DoItemUseBypassDisable();

					// update last placed
					if(!oldBlock.equals(client.level.getBlockState(targetPlacement.getClickedPos()).getBlock())) {
						lastPlacedBlockPos = targetPlacement.getClickedPos();

						if(lastPlayerPlacedBlockPos == null) {
							lastPlayerPlacedBlockPos = client.player.position();
						}
						else {
							// prevent slow rounding error from eventually moving the player out of range
							Vec3 summedLastPlayerPos = lastPlayerPlacedBlockPos.add(new Vec3(targetPlacement.getClickedFace().getUnitVec3i().getX(), targetPlacement.getClickedFace().getUnitVec3i().getY(), targetPlacement.getClickedFace().getUnitVec3i().getZ()));

                            lastPlayerPlacedBlockPos = switch (targetPlacement.getClickedFace().getAxis()) {
                                case X ->
                                        new Vec3(summedLastPlayerPos.x, client.player.position().y, client.player.position().z);
                                case Y ->
                                        new Vec3(client.player.position().x, summedLastPlayerPos.y, client.player.position().z);
                                case Z ->
                                        new Vec3(client.player.position().x, client.player.position().y, summedLastPlayerPos.z);
                            };
						}
					}

					runOnceFlag = false;
				}
			}
			else if(isPlacementTargetFresh) {
				// populate the backfill list just in case
				backFillList.add(client.hitResult);
			}

			// update the last block we looked at
			lastSeenBlockPos = blockHitResult.getBlockPos();
		}
	}
}
