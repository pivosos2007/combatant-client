package combatant.client.features.module.modules.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.*;

class AccuratePlacementApiTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
    private static boolean activates(Class<?> type) {
        return NoDelay.overrides(type, BlockBehaviour.class, "useWithoutItem", BlockState.class, Level.class,
                BlockPos.class, Player.class, BlockHitResult.class)
                || NoDelay.overrides(type, BlockBehaviour.class, "useItemOn", ItemStack.class, BlockState.class,
                Level.class, BlockPos.class, Player.class, InteractionHand.class, BlockHitResult.class);
    }

    @Test
    void actual263ActivationSignaturesCatchInheritedAndItemSpecificInteractions() {
        for (Class<?> type : new Class<?>[]{ChestBlock.class, TrappedChestBlock.class, DoorBlock.class,
                TrapDoorBlock.class, ButtonBlock.class, LeverBlock.class, BedBlock.class, ComposterBlock.class,
                FenceGateBlock.class, CraftingTableBlock.class}) {
            assertTrue(activates(type), type.getSimpleName());
        }
        assertFalse(activates(Block.class));
        assertFalse(activates(RotatedPillarBlock.class));
        assertFalse(NoDelay.overrides(ShieldItem.class, Item.class, "use", Level.class, Player.class, InteractionHand.class));
        assertTrue(NoDelay.overrides(BucketItem.class, Item.class, "use", Level.class, Player.class, InteractionHand.class));
        assertFalse(NoDelay.overrides(Item.class, Item.class, "use", Level.class, Player.class, InteractionHand.class));
    }

    @Test
    void baseItemUseComponentsAndOriginalBucketExemptionAreRecognized() {
        assertTrue(NoDelay.hasSpecialItemUse(new ItemStack(Items.SHIELD), false));
        assertTrue(NoDelay.hasSpecialItemUse(new ItemStack(Items.APPLE), false));
        assertTrue(NoDelay.hasSpecialItemUse(new ItemStack(Items.DIAMOND_HELMET), false));
        assertTrue(NoDelay.hasSpecialItemUse(new ItemStack(Items.WATER_BUCKET), false));
        assertFalse(NoDelay.hasSpecialItemUse(new ItemStack(Items.WATER_BUCKET), true));
        assertFalse(NoDelay.hasSpecialItemUse(new ItemStack(Items.STONE), true));
        ItemStack edibleBlock = new ItemStack(Items.STONE);
        edibleBlock.set(DataComponents.CONSUMABLE, Items.APPLE.components().get(DataComponents.CONSUMABLE));
        assertTrue(NoDelay.hasSpecialItemUse(edibleBlock, true));
    }

    static class Base { protected void use(int value) { } }
    static class Override extends Base { @java.lang.Override protected void use(int value) { } }
    static class Inherited extends Override { }

    @Test
    void inheritedOverrideAndUnknownSignatureBothDelegateToVanilla() {
        assertTrue(NoDelay.overrides(Inherited.class, Base.class, "use", int.class));
        assertFalse(NoDelay.overrides(Base.class, Base.class, "use", int.class));
        assertTrue(NoDelay.overrides(Base.class, Base.class, "removedMethod", int.class));
    }

    @Test
    void backfillRetainsEveryOriginalHitIncludingFaceEdgeAndInside() {
        AccuratePlacementController<BlockHitResult> controller = new AccuratePlacementController<>();
        controller.freshPress(Vec3.ZERO);
        controller.select(null, new BlockPos(-1, 0, 0), Direction.UP, 1, true, 0, Vec3.ZERO, Vec3.ZERO);
        BlockHitResult[] hits = new BlockHitResult[6];
        int i = 0;
        for (Direction face : Direction.values()) {
            BlockPos support = new BlockPos(i, 0, i);
            Vec3 point = new Vec3(i + .000001, .999999, i + .34);
            hits[i] = new BlockHitResult(point, face, support, i % 2 == 0);
            controller.select(hits[i], support, face, 0, true, 3, Vec3.ZERO, Vec3.ZERO);
            i++;
        }
        var decision = controller.select(null, new BlockPos(10, 0, 10), Direction.UP, 0, true, 0, Vec3.ZERO, Vec3.ZERO);
        assertEquals(6, decision.backfill().size());
        for (i = 0; i < hits.length; i++) assertSame(hits[i], decision.backfill().get(i));
    }
}
