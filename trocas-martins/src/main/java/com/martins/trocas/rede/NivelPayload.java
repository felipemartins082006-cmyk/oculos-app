package com.martins.trocas.rede;

import com.martins.trocas.TrocasMartins;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Servidor -> cliente: nível extra (depois de Mestre) do aldeão com quem o jogador está trocando. */
public record NivelPayload(int extra) implements CustomPayload {
	public static final Id<NivelPayload> ID = new Id<>(TrocasMartins.id("nivel"));
	public static final PacketCodec<RegistryByteBuf, NivelPayload> CODEC =
		PacketCodecs.VAR_INT.<RegistryByteBuf>cast().xmap(NivelPayload::new, NivelPayload::extra);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
