/*
 * Placement branch from ABP:R f9f1acfbb58add990ca43b778a700a4eacda38ea (MIT).
 * Copyright (c) 2023 schwar, 2023 KadTheHunter, 2022 Flourick, 2019 Clayborn.
 * See THIRD_PARTY_LICENSES/AccurateBlockPlacement-Reborn-MIT.txt.
 */
package combatant.client.features.module.modules.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReferenceHarness {
    BlockPos lastSeenBlockPos;
    BlockPos lastPlacedBlockPos;
    Vec3 lastPlayerPlacedBlockPos;
    boolean autoRepeatWaitingOnCooldown = true;
    Vec3 lastFreshPressMouseRatio;
    final ArrayList<HitResult> backFillList = new ArrayList<>();
    final Minecraft client = new Minecraft();
    final Object handOfCurrentItemInUse = "MAIN_HAND";

    void step(BlockHitResult hit, int clicks, boolean held, int cooldown, Vec3 player, Vec3 mouse) {
        client.hitResult = hit;
        client.options.keyUse.clickCount = clicks;
        client.options.keyUse.down = held;
        client.cooldown = cooldown;
        client.player.pos = player;
        client.mouseHandler.pos = mouse;
        boolean freshKeyPress = clicks > 0;
        BlockHitResult blockHitResult = hit;
        BlockPos blockHitPos = hit.getBlockPos();
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
        if (!(freshKeyPress || held)) return;
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

    public record BlockPos(int x, int y, int z) {
        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
    }
    public record Vec3(double x, double y, double z) {
        Vec3 add(Vec3 other) { return new Vec3(x + other.x, y + other.y, z + other.z); }
        double get(Direction.Axis axis) { return switch (axis) { case X -> x; case Y -> y; case Z -> z; }; }
        double distanceTo(Vec3 other) { return Math.sqrt(Math.pow(x-other.x,2)+Math.pow(y-other.y,2)+Math.pow(z-other.z,2)); }
    }
    public enum Direction {
        DOWN(0,-1,0,Axis.Y), UP(0,1,0,Axis.Y), NORTH(0,0,-1,Axis.Z), SOUTH(0,0,1,Axis.Z), WEST(-1,0,0,Axis.X), EAST(1,0,0,Axis.X);
        public enum Axis { X, Y, Z }
        final BlockPos step;
        final Axis axis;
        Direction(int x,int y,int z,Axis axis) { step = new BlockPos(x,y,z); this.axis=axis; }
        Axis getAxis() { return axis; }
        BlockPos getUnitVec3i() { return step; }
        @Override public String toString() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static class HitResult { }
    public static class BlockHitResult extends HitResult {
        final String name;
        final BlockPos support, destination;
        final Direction face;
        BlockHitResult(String name, BlockPos support, BlockPos destination, Direction face) {
            this.name=name;this.support=support;this.destination=destination;this.face=face;
        }
        BlockPos getBlockPos() { return support; }
        @Override public String toString() { return name; }
    }
    static class UseOnContext {
        final BlockHitResult hit;
        UseOnContext(Player player, Object hand, BlockHitResult hit) { this.hit=hit; }
    }
    static class BlockPlaceContext {
        final BlockHitResult hit;
        BlockPlaceContext(UseOnContext context) { hit=context.hit; }
        BlockPos getClickedPos() { return hit.destination; }
        Direction getClickedFace() { return hit.face; }
    }
    enum Block { AIR, STONE }
    record BlockState(Block block) { Block getBlock() { return block; } }
    static class Level {
        final Map<BlockPos,Block> blocks = new HashMap<>();
        BlockState getBlockState(BlockPos pos) { return new BlockState(blocks.getOrDefault(pos,Block.AIR)); }
    }
    static class Player { Vec3 pos; Vec3 position() { return pos; } }
    static class KeyMapping {
        int clickCount;
        boolean down;
        boolean isDown() { return down; }
        boolean consumeClick() { if(clickCount==0)return false;clickCount--;return true; }
    }
    static class Options { final KeyMapping keyUse = new KeyMapping(); }
    static class Mouse {
        Vec3 pos;
        double xpos() { return pos.x; }
        double ypos() { return pos.y; }
    }
    static class Window { int getScreenWidth() { return 1; } int getScreenHeight() { return 1; } }
    interface IMinecraftClientAccessor {
        int accurateblockplacement_GetItemUseCooldown();
        void accurateblockplacement_DoItemUseBypassDisable();
    }
    static class Minecraft implements IMinecraftClientAccessor {
        HitResult hitResult;
        final Options options = new Options();
        final Player player = new Player();
        final Level level = new Level();
        final Mouse mouseHandler = new Mouse();
        final List<String> calls = new ArrayList<>();
        int cooldown;
        boolean place = true;
        Window getWindow() { return new Window(); }
        public int accurateblockplacement_GetItemUseCooldown() { return cooldown; }
        public void accurateblockplacement_DoItemUseBypassDisable() {
            BlockHitResult hit = (BlockHitResult) hitResult;
            calls.add(hit.name);
            cooldown = 4;
            if(place) level.blocks.put(hit.destination,Block.STONE);
        }
    }

    static BlockHitResult hit(int x) {
        return new BlockHitResult("target-"+x,new BlockPos(x,0,0),new BlockPos(x,1,0),Direction.UP);
    }
    void print(String label) {
        System.out.println(label+" calls="+client.calls+" seen="+lastSeenBlockPos+" placed="+lastPlacedBlockPos
                +" playerHistory="+lastPlayerPlacedBlockPos+" waiting="+autoRepeatWaitingOnCooldown+" backfill="+backFillList
                +" remainingClicks="+client.options.keyUse.clickCount+" cooldown="+client.cooldown);
    }
    public static void main(String[] args) {
        System.out.println("Upstream branch f9f1acfbb58add990ca43b778a700a4eacda38ea; fake client, NOT an in-game trace.");
        Vec3 p=new Vec3(0,0,0), mouse=new Vec3(0,0,0);
        ReferenceHarness r=new ReferenceHarness();
        r.step(hit(0),1,true,0,p,mouse);r.print("fresh");
        r.step(hit(0),0,true,3,p,mouse);r.print("held same");
        for(int i=1;i<=6;i++) r.step(hit(i),0,true,3,p,mouse);
        r.print("six targets during cooldown");
        r.step(hit(6),0,true,0,p,mouse);r.print("cooldown expired, same target (does not flush)");
        r.step(hit(7),0,true,0,p,mouse);r.print("new target flushes all six in order");
        r.step(hit(0),0,true,4,p,mouse);r.print("return to first support");
        r.step(hit(8),2,false,4,p,mouse);r.print("two clicks between frames, released");
        r=new ReferenceHarness();r.step(hit(0),1,true,0,p,mouse);
        r.step(hit(1),0,true,3,p,mouse);
        r.step(hit(2),0,true,3,p,new Vec3(.1,0,0));r.print("mouse threshold flushes");
        r=new ReferenceHarness();r.step(hit(0),1,true,0,p,mouse);
        r.step(hit(1),0,true,3,p,mouse);
        r.step(hit(2),1,true,3,p,mouse);r.print("fresh resets backlog");
    }
}
