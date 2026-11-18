package br.bolsavillager.net;

import br.bolsavillager.BolsaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: sacar o saldo da corretora em esmeraldas. */
public record WithdrawPayload() implements CustomPacketPayload {
	public static final WithdrawPayload INSTANCE = new WithdrawPayload();
	public static final Type<WithdrawPayload> TYPE = new Type<>(BolsaMod.id("withdraw"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
