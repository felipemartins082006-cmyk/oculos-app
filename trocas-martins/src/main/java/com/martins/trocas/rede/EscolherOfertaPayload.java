package com.martins.trocas.rede;

import com.martins.trocas.TrocasMartins;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Cliente -> servidor: o jogador clicou numa troca da lista (igual a escolher uma troca de aldeão). */
public record EscolherOfertaPayload(int indice) implements CustomPayload {
	public static final Id<EscolherOfertaPayload> ID = new Id<>(TrocasMartins.id("escolher_oferta"));
	public static final PacketCodec<RegistryByteBuf, EscolherOfertaPayload> CODEC =
		PacketCodecs.VAR_INT.<RegistryByteBuf>cast().xmap(EscolherOfertaPayload::new, EscolherOfertaPayload::indice);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
