package br.bolsavillager.net;

import br.bolsavillager.BolsaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: cancelar a ordem programada número {@code index}. */
public record CancelOrderPayload(int index) implements CustomPacketPayload {
	public static final Type<CancelOrderPayload> TYPE = new Type<>(BolsaMod.id("cancel_order"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CancelOrderPayload> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.index), buf -> new CancelOrderPayload(buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
