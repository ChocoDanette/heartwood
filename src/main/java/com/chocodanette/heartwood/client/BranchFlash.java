package com.chocodanette.heartwood.client;

import com.chocodanette.heartwood.Heartwood;
import com.chocodanette.heartwood.felling.FallenTree;
import com.chocodanette.heartwood.tree.BoughBlock;
import com.chocodanette.heartwood.tree.TrunkShellBlock;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
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

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Swinging an axe (or bare hands, with handCutting) at the trunk of a fallen tree that still has
 * branches makes the whole of each branch left to cut off glow white for a few seconds, fading in
 * and out. On a standing tree, hitting the trunk above its foot (where it can't be cut) makes the
 * foot and the branches glow the same way.
 */
@EventBusSubscriber(modid = Heartwood.MOD_ID, value = Dist.CLIENT)
public final class BranchFlash {
    private static final long DURATION_MS = 3500;
    private static final double PULSE_MS = 700;

    /** Drawn just outside the wood, so it doesn't flicker against it. */
    private static final float E = 0.004f;

    private static VoxelShape shape;
    private static List<AABB> boxes = List.of();
    private static BlockPos origin;
    private static long start;

    @SubscribeEvent
    public static void onHit(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (!com.chocodanette.heartwood.Config.cuts(event.getEntity().getMainHandItem())) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TrunkShellBlock) {
            pos = TrunkShellBlock.core(state, pos);
            state = level.getBlockState(pos);
        }
        if (!(state.getBlock() instanceof BoughBlock)) return;
        FallenTree tree = FallenTree.of(level, pos);
        if (!tree.stemSet.contains(pos)) return;
        List<BlockPos> part = new ArrayList<>();
        if (BoughBlock.isRooted(level, pos)) {
            // A standing tree: its trunk can only be cut at the foot. Hitting it higher up shows
            // where it can be cut: the foot and every branch.
            if (tree.base.equals(pos) || state.getValue(BoughBlock.NOTCH).dir != null) return;
            part.add(tree.base);
        } else if (tree.limbed()) {
            return;
        }
        for (BlockPos b : tree.branches) {
            part.add(b);
            part.addAll(BoughBlock.descendants(level, b, 1000));
        }
        shape = shapeOf(level, pos, part);
        boxes = shape.toAabbs();
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

        float alpha = 0.08f + 0.47f * pulse * fade;

        PoseStack poseStack = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f m = poseStack.last().pose();
        float ox = (float) (origin.getX() - cam.x), oy = (float) (origin.getY() - cam.y), oz = (float) (origin.getZ() - cam.z);

        // The whole branch glows as one piece: a first pass only writes depth, so the second only
        // lights its outer surface, with no seams where its pieces meet.
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(true);
        draw(m, ox, oy, oz, 0f);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(false);
        draw(m, ox, oy, oz, alpha);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static void draw(Matrix4f m, float ox, float oy, float oz, float alpha) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (AABB box : boxes) {
            float x0 = (float) box.minX + ox - E, y0 = (float) box.minY + oy - E, z0 = (float) box.minZ + oz - E;
            float x1 = (float) box.maxX + ox + E, y1 = (float) box.maxY + oy + E, z1 = (float) box.maxZ + oz + E;
            quad(b, m, alpha, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            quad(b, m, alpha, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
            quad(b, m, alpha, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
            quad(b, m, alpha, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            quad(b, m, alpha, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            quad(b, m, alpha, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        }
        MeshData mesh = b.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static void quad(BufferBuilder b, Matrix4f m, float a, float... v) {
        for (int i = 0; i < 12; i += 3) b.addVertex(m, v[i], v[i + 1], v[i + 2]).setColor(1f, 1f, 1f, a);
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
                piece = Shapes.or(s.getShape(level, p, CollisionContext.empty()), armBeyond(s));
            }
            shape = Shapes.joinUnoptimized(shape, piece.move(p.getX() - origin.getX(), p.getY() - origin.getY(), p.getZ() - origin.getZ()),
                    BooleanOp.OR);
        }
        return shape.optimize();
    }

    /**
     * The part of a piece's arm drawn inside its parent's block, reaching the surface of the
     * parent's core: the block shape stops at the block, so the joint would not glow otherwise.
     */
    private static VoxelShape armBeyond(BlockState s) {
        int r = s.getValue(BoughBlock.RADIUS), pr = s.getValue(BoughBlock.PARENT_RADIUS);
        if (r >= 8 || pr >= 8) return Shapes.empty();
        double lo = 8 - r, hi = 8 + r, d = 8 - pr;
        return switch (s.getValue(BoughBlock.PARENT)) {
            case DOWN -> net.minecraft.world.level.block.Block.box(lo, -d, lo, hi, 0, hi);
            case UP -> net.minecraft.world.level.block.Block.box(lo, 16, lo, hi, 16 + d, hi);
            case NORTH -> net.minecraft.world.level.block.Block.box(lo, lo, -d, hi, hi, 0);
            case SOUTH -> net.minecraft.world.level.block.Block.box(lo, lo, 16, hi, hi, 16 + d);
            case WEST -> net.minecraft.world.level.block.Block.box(-d, lo, lo, 0, hi, hi);
            case EAST -> net.minecraft.world.level.block.Block.box(16, lo, lo, 16 + d, hi, hi);
            default -> Shapes.empty();
        };
    }

    private BranchFlash() {}
}
