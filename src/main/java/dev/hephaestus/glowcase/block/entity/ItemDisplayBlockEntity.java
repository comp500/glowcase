package dev.hephaestus.glowcase.block.entity;

import dev.hephaestus.glowcase.Glowcase;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.fabricmc.fabric.api.network.PacketContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.world.World;

public class ItemDisplayBlockEntity extends BlockEntity implements BlockEntityClientSerializable, Tickable {
	private ItemStack stack = ItemStack.EMPTY;
	private Entity displayEntity = null;

	public RotationType rotationType = RotationType.TRACKING;
	public boolean givesItem = true;
	public boolean showName = true;
	public float pitch;
	public float yaw;

	public ItemDisplayBlockEntity() {
		super(Glowcase.ITEM_DISPLAY_BLOCK_ENTITY);
	}

	@Override
	public void fromTag(BlockState state, CompoundTag tag) {
		super.fromTag(state, tag);

		this.stack = ItemStack.fromTag(tag.getCompound("item"));

		if (tag.contains("tracking")) {
			this.rotationType = tag.getBoolean("tracking") ? RotationType.TRACKING : RotationType.LOCKED;
		} else if (tag.contains("rotation_type")) {
			this.rotationType = RotationType.valueOf(tag.getString("rotation_type"));
		} else {
			this.rotationType = RotationType.TRACKING;
		}

		if (tag.contains("pitch")) {
			this.pitch = tag.getFloat("pitch");
			this.yaw = tag.getFloat("yaw");
		}

		if (tag.contains("show_name")) {
			this.showName = tag.getBoolean("show_name");
		}
	}

	@Override
	public CompoundTag toTag(CompoundTag tag) {
		tag.put("item", this.stack.toTag(new CompoundTag()));
		tag.putString("rotation_type", this.rotationType.name());
		tag.putFloat("pitch", this.pitch);
		tag.putFloat("yaw", this.yaw);
		tag.putBoolean("show_name", this.showName);

		return super.toTag(tag);
	}

	@Override
	public void fromClientTag(CompoundTag compoundTag) {
		this.fromTag(null, compoundTag);
		this.setDisplayEntity();
	}

	@Override
	public CompoundTag toClientTag(CompoundTag compoundTag) {
		return this.toTag(compoundTag);
	}

	public boolean hasItem() {
		return this.stack != null && !this.stack.isEmpty();
	}

	public void setStack(ItemStack stack) {
		this.stack = stack;

		this.setDisplayEntity();

		this.sync();
	}

	private void setDisplayEntity() {
		if (this.stack.getItem() instanceof SpawnEggItem) {
			this.displayEntity = ((SpawnEggItem) this.stack.getItem()).getEntityType(this.stack.getTag()).create(this.world);
		} else {
			this.displayEntity = null;
		}
	}

	public Entity getDisplayEntity() {
		return this.displayEntity;
	}

	public ItemStack getUseStack() {
		return this.stack;
	}

	@Override
	public void tick() {
		if (this.displayEntity != null) {
			this.displayEntity.tick();
			++displayEntity.age;
		}
	}

	public static void save(PacketContext context, PacketByteBuf packetByteBuf) {
		BlockPos pos = packetByteBuf.readBlockPos();
		RotationType rotationType = packetByteBuf.readEnumConstant(RotationType.class);
		boolean givesItem = packetByteBuf.readBoolean();
		int rotation = packetByteBuf.readVarInt();
		boolean showName = packetByteBuf.readBoolean();

		float pitch = packetByteBuf.readFloat();
		float yaw = packetByteBuf.readFloat();

		context.getTaskQueue().execute(() -> {
			BlockEntity blockEntity = context.getPlayer().getEntityWorld().getBlockEntity(pos);
			if (blockEntity instanceof ItemDisplayBlockEntity) {
				((ItemDisplayBlockEntity) blockEntity).givesItem = givesItem;
				((ItemDisplayBlockEntity) blockEntity).rotationType = rotationType;
				((ItemDisplayBlockEntity) blockEntity).pitch = pitch;
				((ItemDisplayBlockEntity) blockEntity).yaw = yaw;
				((ItemDisplayBlockEntity) blockEntity).showName = showName;

				World world = context.getPlayer().getEntityWorld();
				world.setBlockState(pos, world.getBlockState(pos).with(Properties.ROTATION, rotation));

				((ItemDisplayBlockEntity) blockEntity).sync();
			}
		});
	}

	public void cycleRotationType(PlayerEntity playerEntity) {
		switch (this.rotationType) {
			case TRACKING:
				this.rotationType = RotationType.HORIZONTAL;
				if (this.world != null) {
					this.world.setBlockState(this.pos, this.getCachedState().with(Properties.ROTATION, MathHelper.floor((double) ((playerEntity.yaw) * 16.0F / 360.0F) + 0.5D) & 15));
				}
				break;
			case HORIZONTAL:
				this.rotationType = RotationType.LOCKED;
				break;
			case LOCKED:
				this.rotationType = RotationType.TRACKING;
		}
	}

	@Environment(EnvType.CLIENT)
	public static Vec2f getPitchAndYaw(PlayerEntity player, BlockPos pos) {
		double d = pos.getX() - player.getPos().x + 0.5;
		double e = pos.getY() - player.getEyeY() + 0.5;
		double f = pos.getZ() - player.getPos().z + 0.5;
		double g = MathHelper.sqrt(d * d + f * f);

		float pitch = (float) ((-MathHelper.atan2(e, g)));
		float yaw = (float) (-MathHelper.atan2(f, d) + Math.PI / 2);

		return new Vec2f(pitch, yaw);
	}

	public enum RotationType {
		LOCKED, TRACKING, HORIZONTAL
	}
}
