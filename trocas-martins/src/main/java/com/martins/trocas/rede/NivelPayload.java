package com.martins.trocas.rede;

import com.martins.trocas.TrocasMartins;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: nível extra (depois de Mestre) do aldeão com quem o jogador está trocando. */
public record NivelPayload(int extra) implements CustomPacketPayload {
	public static final Type<NivelPayload> TYPE = new Type<>(TrocasMartins.id("nivel"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NivelPayload> CODEC =
		ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(NivelPayload::new, NivelPayload::extra);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
