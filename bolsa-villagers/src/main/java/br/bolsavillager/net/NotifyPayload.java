package br.bolsavillager.net;

import br.bolsavillager.BolsaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor -> cliente: aviso que aparece como notificação no canto da tela (sem usar o chat).
 * {@code kind}: 0 = mercado, 1 = dividendos, 2 = ordem executada, 3 = raid.
 */
public record NotifyPayload(int kind, Component title, Component message) implements CustomPacketPayload {
	public static final int MARKET = 0, DIVIDENDS = 1, ORDER = 2, RAID = 3;
	public static final Type<NotifyPayload> TYPE = new Type<>(BolsaMod.id("notify"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NotifyPayload> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.kind);
				ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.title);
				ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.message);
			},
			buf -> new NotifyPayload(buf.readVarInt(),
					ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
					ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
