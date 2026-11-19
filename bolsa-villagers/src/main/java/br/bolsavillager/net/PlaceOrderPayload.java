package br.bolsavillager.net;

import br.bolsavillager.BolsaMod;
import br.bolsavillager.market.LimitOrder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: criar uma ordem programada. */
public record PlaceOrderPayload(LimitOrder order) implements CustomPacketPayload {
	public static final Type<PlaceOrderPayload> TYPE = new Type<>(BolsaMod.id("place_order"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlaceOrderPayload> CODEC = CustomPacketPayload.codec(
			(p, buf) -> MarketSyncPayload.writeOrder(buf, p.order),
			buf -> new PlaceOrderPayload(MarketSyncPayload.readOrder(buf)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
