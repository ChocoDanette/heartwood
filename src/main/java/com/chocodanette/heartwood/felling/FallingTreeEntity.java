package com.chocodanette.heartwood.felling;

import com.chocodanette.heartwood.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A tree on its way down: it shows the standing tree tipping over around the cut,
 * then lays the lying trunk and branches when it hits the ground.
 */
public class FallingTreeEntity extends Entity implements IEntityWithComplexSpawn {
    /** A block of the standing tree, relative to the cut. */
    public record Part(BlockPos offset, BlockState state) {}

    private List<Part> parts = List.of();
    private Direction direction = Direction.NORTH;
    private int duration = 30;
    private int age;
    private BlockPos cut = BlockPos.ZERO;
    /** Server only: where the blocks land. */
    private Map<BlockPos, BlockState> target;

    public FallingTreeEntity(EntityType<? extends FallingTreeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    static void spawn(ServerLevel level, BlockPos cut, Direction dir, List<Part> parts,
                      Map<BlockPos, BlockState> target, int duration) {
        FallingTreeEntity tree = new FallingTreeEntity(ModBlocks.FALLING_TREE.get(), level);
        tree.setPos(cut.getX() + 0.5, cut.getY(), cut.getZ() + 0.5);
        tree.cut = cut;
        tree.direction = dir;
        tree.parts = parts;
        tree.target = target;
        tree.duration = duration;
        level.addFreshEntity(tree);
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (!level().isClientSide && age >= duration) {
            if (target != null) TreeFeller.land((ServerLevel) level(), cut, direction, target, this);
            target = null;
            discard();
        }
    }

    /** Fall angle in degrees: slow at first, then faster, like a real tree. */
    public float angle(float partialTick) {
        float t = Math.min(1f, (age + partialTick) / duration);
        return 90f * t * t;
    }

    public List<Part> parts() {
        return parts;
    }

    public BlockPos cut() {
        return cut;
    }

    public Direction direction() {
        return direction;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(32);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(cut);
        buf.writeByte(direction.get3DDataValue());
        buf.writeVarInt(duration);
        buf.writeVarInt(parts.size());
        for (Part p : parts) {
            buf.writeVarInt(p.offset().getX());
            buf.writeVarInt(p.offset().getY());
            buf.writeVarInt(p.offset().getZ());
            buf.writeVarInt(Block.getId(p.state()));
        }
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        cut = buf.readBlockPos();
        direction = Direction.from3DDataValue(buf.readByte());
        duration = buf.readVarInt();
        int n = buf.readVarInt();
        List<Part> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BlockPos offset = new BlockPos(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            list.add(new Part(offset, Block.stateById(buf.readVarInt())));
        }
        parts = list;
    }
}
