package dev.hephaestus.glowcase.block.entity;

import dev.hephaestus.glowcase.Glowcase;
import dev.hephaestus.glowcase.client.render.block.entity.BakedBlockEntityRenderer;
import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.fabricmc.fabric.api.network.PacketContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class TextBlockEntity extends BlockEntity implements BlockEntityClientSerializable {
	public List<MutableText> lines = new ArrayList<>();
	public TextAlignment textAlignment = TextAlignment.CENTER;
	public  ZOffset zOffset = ZOffset.CENTER;
	public ShadowType shadowType = ShadowType.DROP;
	public float scale = 1F;
	public int color = 0xFFFFFF;
	public boolean renderDirty = true;

	public TextBlockEntity() {
		super(Glowcase.TEXT_BLOCK_ENTITY);
		lines.add((MutableText) LiteralText.EMPTY);
	}

	@Override
	public CompoundTag toTag(CompoundTag tag) {
		super.toTag(tag);

		tag.putFloat("scale", this.scale);
		tag.putInt("color", this.color);

		tag.putString("text_alignment", this.textAlignment.name());
		tag.putString("z_offset", this.zOffset.name());
		tag.putString("shadow_type", this.shadowType.name());

		ListTag lines = tag.getList("lines", 8);
		for (MutableText text : this.lines) {
			lines.add(StringTag.of(Text.Serializer.toJson(text)));
		}

		tag.put("lines", lines);

		return tag;
	}

	@Override
	public void fromTag(BlockState state, CompoundTag tag) {
		super.fromTag(state, tag);

		this.lines = new ArrayList<>();
		this.scale = tag.getFloat("scale");
		this.color = tag.getInt("color");

		this.textAlignment = TextAlignment.valueOf(tag.getString("text_alignment"));
		this.zOffset = ZOffset.valueOf(tag.getString("z_offset"));
		this.shadowType = tag.contains("shadow_type") ? ShadowType.valueOf(tag.getString("shadow_type")) : ShadowType.DROP;

		ListTag lines = tag.getList("lines", 8);
		for (Tag line : lines) {
			this.lines.add(Text.Serializer.fromJson(line.asString()));
		}
		this.renderDirty = true;
	}

	@Override
	public void fromClientTag(CompoundTag compoundTag) {
		this.fromTag(Glowcase.TEXT_BLOCK.getDefaultState(), compoundTag);
	}

	@Override
	public CompoundTag toClientTag(CompoundTag compoundTag) {
		return this.toTag(compoundTag);
	}

	public static void save(PacketContext context, PacketByteBuf buf) {
		BlockPos pos = buf.readBlockPos();
		float scale = buf.readFloat();
		int lineCount = buf.readVarInt();
		TextAlignment alignment = buf.readEnumConstant(TextAlignment.class);
		int color = buf.readVarInt();
		ZOffset zOffset = buf.readEnumConstant(ZOffset.class);
		ShadowType shadowType = buf.readEnumConstant(ShadowType.class);

		List<MutableText> lines = new ArrayList<>();
		for (int i = 0; i < lineCount; ++i) {
			lines.add((MutableText) buf.readText());
		}

		context.getTaskQueue().execute(() -> {
			BlockEntity blockEntity = context.getPlayer().getEntityWorld().getBlockEntity(pos);
			if (blockEntity instanceof TextBlockEntity) {
				((TextBlockEntity) blockEntity).scale = scale;
				((TextBlockEntity) blockEntity).lines = lines;
				((TextBlockEntity) blockEntity).textAlignment = alignment;
				((TextBlockEntity) blockEntity).color = color;
				((TextBlockEntity) blockEntity).zOffset = zOffset;
				((TextBlockEntity) blockEntity).shadowType = shadowType;
				((TextBlockEntity) blockEntity).sync();
			}
		});
	}

	@Override
	public double getSquaredRenderDistance() {
		return MathHelper.clamp(this.scale * 12D, 40D, 400D);
	}

	public enum TextAlignment {
		LEFT, CENTER, RIGHT
	}

	public enum ZOffset {
		FRONT, CENTER, BACK
	}

	public enum ShadowType {
		DROP, PLATE, NONE
	}

	@SuppressWarnings({"MethodCallSideOnly", "VariableUseSideOnly"})
	@Override
	public void markRemoved() {
		if (world != null && world.isClient) {
			BakedBlockEntityRenderer.VertexBufferManager.INSTANCE.invalidate(getPos());
		}
	}
}
