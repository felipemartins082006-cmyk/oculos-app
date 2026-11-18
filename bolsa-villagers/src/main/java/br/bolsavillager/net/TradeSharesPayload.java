package br.bolsavillager.net;

import br.bolsavillager.BolsaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: ordem de compra (quantity > 0) ou venda (quantity < 0) de ações. */
public record TradeSharesPayload(String stockId, int quantity) implements CustomPacketPayload {
	public static final Type<TradeSharesPayload> TYPE = new Type<>(BolsaMod.id("trade_shares"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TradeSharesPayload> CODEC = CustomPacketPayload.codec(
			(payload, buf) -> {
				buf.writeUtf(payload.stockId, 64);
				buf.writeInt(payload.quantity);
			},
			buf -> new TradeSharesPayload(buf.readUtf(64), buf.readInt())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
