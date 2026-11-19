package br.bolsavillager.net;

import br.bolsavillager.BolsaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Cliente -> servidor: vender itens da mochila para uma guilda no balcão. */
public record SellItemsPayload(Identifier item, String guildId, int quantity) implements CustomPacketPayload {
	public static final Type<SellItemsPayload> TYPE = new Type<>(BolsaMod.id("sell_items"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SellItemsPayload> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeIdentifier(p.item);
				buf.writeUtf(p.guildId, 64);
				buf.writeVarInt(p.quantity);
			},
			buf -> new SellItemsPayload(buf.readIdentifier(), buf.readUtf(64), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
