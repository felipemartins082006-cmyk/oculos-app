package br.bolsavillager.net;

import br.bolsavillager.BolsaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: depositar esmeraldas da mochila no saldo da corretora (0 = todas). */
public record DepositPayload(int amount) implements CustomPacketPayload {
	public static final Type<DepositPayload> TYPE = new Type<>(BolsaMod.id("deposit"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DepositPayload> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.amount), buf -> new DepositPayload(buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
