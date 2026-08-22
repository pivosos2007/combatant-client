/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ItemBatch {
    final ObjectArrayList<ItemDrawCommand> commands = new ObjectArrayList<>(64);
    final PoseStack matrices = new PoseStack();
    @Nullable GuiGraphicsExtractor context;
    @Nullable ScreenRectangle scissor;

    void begin(@Nullable GuiGraphicsExtractor context, @Nullable ScreenRectangle scissor) {
        this.context = context;
        this.scissor = scissor;
        this.commands.clear();
    }

    boolean canMerge(@Nullable GuiGraphicsExtractor context, @Nullable ScreenRectangle scissor) {
        return this.context == context && Objects.equals(this.scissor, scissor);
    }

    public void add(ItemDrawCommand command) {
        commands.add(command);
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }
}
