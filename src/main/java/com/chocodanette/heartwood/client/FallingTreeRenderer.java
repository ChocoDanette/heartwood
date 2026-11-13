package com.chocodanette.heartwood.client;

import com.chocodanette.heartwood.felling.FallingTreeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Quaternionf;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Draws the standing tree rotated around the centre of the cut block. */
public class FallingTreeRenderer extends EntityRenderer<FallingTreeEntity> {
    public FallingTreeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FallingTreeEntity tree, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        Direction d = tree.direction();
        // Rotating "up" towards d is a rotation around up x d.
        float ax = d.getStepZ(), az = -d.getStepX();
        float angle = (float) Math.toRadians(tree.angle(partialTick));

        pose.pushPose();
        pose.translate(0, 0.5, 0);
        pose.mulPose(new Quaternionf().rotateAxis(angle, ax, 0, az));
        for (FallingTreeEntity.Part part : tree.parts()) {
            pose.pushPose();
            pose.translate(part.offset().getX() - 0.5, part.offset().getY() - 0.5, part.offset().getZ() - 0.5);
            renderTinted(blocks, tree, part, pose, buffers, light);
            pose.popPose();
        }
        pose.popPose();
        super.render(tree, yaw, partialTick, pose, buffers, light);
    }

    /** Like renderSingleBlock, but tinted with the colours of the block's original spot (biome foliage). */
    private static void renderTinted(BlockRenderDispatcher blocks, FallingTreeEntity tree, FallingTreeEntity.Part part,
                                     PoseStack pose, MultiBufferSource buffers, int light) {
        BlockState state = part.state();
        if (state.getRenderShape() != RenderShape.MODEL) return;
        BakedModel model = blocks.getBlockModel(state);
        BlockPos at = tree.cut().offset(part.offset());
        int color = Minecraft.getInstance().getBlockColors().getColor(state, tree.level(), at, 0);
        float r = 1f, g = 1f, b = 1f;
        if (color != -1) {
            r = (color >> 16 & 255) / 255f;
            g = (color >> 8 & 255) / 255f;
            b = (color & 255) / 255f;
        }
        for (RenderType type : model.getRenderTypes(state, RandomSource.create(42), ModelData.EMPTY)) {
            blocks.getModelRenderer().renderModel(pose.last(),
                    buffers.getBuffer(RenderTypeHelper.getEntityRenderType(type, false)),
                    state, model, r, g, b, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
        }
    }

    @Override
    public boolean shouldRender(FallingTreeEntity tree, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(FallingTreeEntity tree) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
