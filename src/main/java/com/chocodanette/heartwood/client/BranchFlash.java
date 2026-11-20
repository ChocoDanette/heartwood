package com.chocodanette.heartwood.client;

import com.chocodanette.heartwood.Heartwood;
import com.chocodanette.heartwood.felling.FallenTree;
import com.chocodanette.heartwood.tree.BoughBlock;
import com.chocodanette.heartwood.tree.TrunkShellBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Swinging an axe at the trunk of a fallen tree that still has branches makes the branches left to
 * cut off flash white for a few seconds, fading in and out.
 */
@EventBusSubscriber(modid = Heartwood.MOD_ID, value = Dist.CLIENT)
public final class BranchFlash {
    private static final long DURATION_MS = 3500;
    private static final double PULSE_MS = 700;

    private static VoxelShape shape;
    private static BlockPos origin;
    private static long start;

    @SubscribeEvent
    public static void onHit(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (!event.getEntity().getMainHandItem().is(ItemTags.AXES)) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TrunkShellBlock) {
            pos = TrunkShellBlock.core(state, pos);
            state = level.getBlockState(pos);
        }
        if (!(state.getBlock() instanceof BoughBlock) || BoughBlock.isRooted(level, pos)) return;
        FallenTree tree = FallenTree.of(level, pos);
        if (!tree.stemSet.contains(pos) || tree.limbed()) return;
        List<BlockPos> part = new ArrayList<>();
        for (BlockPos b : tree.branches) {
            part.add(b);
            part.addAll(BoughBlock.descendants(level, b, 1000));
        }
        shape = shapeOf(level, pos, part);
        origin = pos;
        start = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || shape == null) return;
        long t = System.currentTimeMillis() - start;
        if (t > DURATION_MS || Minecraft.getInstance().level == null) {
            shape = null;
            return;
        }
        // Fades in and out a few times, softer towards the end.
        float pulse = (float) (0.5 - 0.5 * Math.cos(t / PULSE_MS * Mth.TWO_PI));
        float fade = Math.min(1f, (DURATION_MS - t) / 800f);
        float alpha = 0.15f + 0.85f * pulse * fade;

        PoseStack poseStack = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(OutlineRenderType.THICK_LINES);
        PoseStack.Pose pose = poseStack.last();
        double ox = origin.getX() - cam.x, oy = origin.getY() - cam.y, oz = origin.getZ() - cam.z;
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
            float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 0) { dx /= len; dy /= len; dz /= len; }
            lines.addVertex(pose, (float) (x1 + ox), (float) (y1 + oy), (float) (z1 + oz)).setColor(1f, 1f, 1f, alpha).setNormal(pose, dx, dy, dz);
            lines.addVertex(pose, (float) (x2 + ox), (float) (y2 + oy), (float) (z2 + oz)).setColor(1f, 1f, 1f, alpha).setNormal(pose, dx, dy, dz);
        });
        buffers.endBatch(OutlineRenderType.THICK_LINES);
    }

    /** The outline of these pieces, relative to origin. */
    private static VoxelShape shapeOf(Level level, BlockPos origin, List<BlockPos> part) {
        VoxelShape shape = Shapes.empty();
        for (BlockPos p : part) {
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof BoughBlock)) continue;
            int r = s.getValue(BoughBlock.RADIUS);
            VoxelShape piece;
            if (r > 8) {
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
            shape = Shapes.joinUnoptimized(shape, piece.move(p.getX() - origin.getX(), p.getY() - origin.getY(), p.getZ() - origin.getZ()),
                    BooleanOp.OR);
        }
        return shape.optimize();
    }

    private BranchFlash() {}
}
