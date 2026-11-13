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
            blocks.renderSingleBlock(part.state(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
        super.render(tree, yaw, partialTick, pose, buffers, light);
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
