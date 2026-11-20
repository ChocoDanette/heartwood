package com.chocodanette.heartwood.client;

import com.chocodanette.heartwood.Heartwood;
import com.chocodanette.heartwood.felling.FallenTree;
import com.chocodanette.heartwood.tree.BoughBlock;
import com.chocodanette.heartwood.tree.TrunkShellBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The outline of what the player aims at on a tree made of boughs is the outline of the whole
 * part concerned, not of one block: the whole trunk when aiming at the trunk, and the branch with
 * all it carries when aiming at a branch (what a cut there takes off).
 */
@EventBusSubscriber(modid = Heartwood.MOD_ID, value = Dist.CLIENT)
public final class TreeOutline {
    private static BlockPos cachedPos;
    private static BlockState cachedState;
    private static long cachedTime;
    private static VoxelShape cachedShape;

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos pos = event.getTarget().getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TrunkShellBlock) {
            pos = TrunkShellBlock.core(state, pos);
            state = level.getBlockState(pos);
        }
        if (!(state.getBlock() instanceof BoughBlock)) return;

        long now = level.getGameTime();
        if (!pos.equals(cachedPos) || state != cachedState || now - cachedTime > 10 || now < cachedTime) {
            cachedShape = outline(level, pos);
            cachedPos = pos;
            cachedState = state;
            cachedTime = now;
        }
        if (cachedShape == null || cachedShape.isEmpty()) return;

        Vec3 cam = event.getCamera().getPosition();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        PoseStack.Pose pose = event.getPoseStack().last();
        double ox = pos.getX() - cam.x, oy = pos.getY() - cam.y, oz = pos.getZ() - cam.z;
        cachedShape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
            float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 0) { dx /= len; dy /= len; dz /= len; }
            lines.addVertex(pose, (float) (x1 + ox), (float) (y1 + oy), (float) (z1 + oz)).setColor(0f, 0f, 0f, 0.4f).setNormal(pose, dx, dy, dz);
            lines.addVertex(pose, (float) (x2 + ox), (float) (y2 + oy), (float) (z2 + oz)).setColor(0f, 0f, 0f, 0.4f).setNormal(pose, dx, dy, dz);
        });
        event.setCanceled(true);
    }

    /** The shape of the part aimed at, relative to pos. */
    private static VoxelShape outline(Level level, BlockPos pos) {
        FallenTree tree = FallenTree.of(level, pos);
        List<BlockPos> part = new ArrayList<>();
        if (tree.stemSet.contains(pos)) {
            part.addAll(tree.stem);
        } else {
            part.add(pos);
            part.addAll(BoughBlock.descendants(level, pos, 1000));
        }
        VoxelShape shape = Shapes.empty();
        for (BlockPos p : part) {
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof BoughBlock)) continue;
            int r = s.getValue(BoughBlock.RADIUS);
            VoxelShape piece;
            if (r > 8) {
                // A thick trunk piece spreads over its neighbours, across its axis.
                double lo = (8 - r) / 16.0, hi = (8 + r) / 16.0;
                AABB box = switch (BoughBlock.axis(s)) {
                    case Y -> new AABB(lo, 0, lo, hi, 1, hi);
                    case X -> new AABB(0, lo, lo, 1, hi, hi);
                    case Z -> new AABB(lo, lo, 0, hi, hi, 1);
                };
                piece = Shapes.create(box);
            } else {
                piece = s.getShape(level, p, CollisionContext.empty());
            }
            shape = Shapes.joinUnoptimized(shape, piece.move(p.getX() - pos.getX(), p.getY() - pos.getY(), p.getZ() - pos.getZ()),
                    net.minecraft.world.phys.shapes.BooleanOp.OR);
        }
        return shape.optimize();
    }

    private TreeOutline() {}
}
