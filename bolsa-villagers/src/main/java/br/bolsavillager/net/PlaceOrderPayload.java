package br.bolsavillager.net;

import br.bolsavillager.BolsaMod;
import br.bolsavillager.market.LimitOrder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: criar uma ordem programada (alvo em esmeraldas inteiras). */
public record PlaceOrderPayload(LimitOrder.Type orderType, String stockId, int quantity, int target) implements CustomPacketPayload {
	public static final Type<PlaceOrderPayload> TYPE = new Type<>(BolsaMod.id("place_order"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlaceOrderPayload> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.orderType.ordinal());
				buf.writeUtf(p.stockId, 64);
				buf.writeVarInt(p.quantity);
				buf.writeVarInt(p.target);
			},
			buf -> new PlaceOrderPayload(LimitOrder.Type.values()[Math.floorMod(buf.readVarInt(), LimitOrder.Type.values().length)],
					buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
