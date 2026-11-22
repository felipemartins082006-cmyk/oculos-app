package com.martins.trocas.rede;

import com.martins.trocas.TrocasMartins;
import com.martins.trocas.mercador.LojaMercador;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

/**
 * Servidor -> cliente, ao abrir a tela: tudo o que o Mercador compra (para pesquisar)
 * e tudo o que ele vende.
 */
public record CatalogoPayload(int syncId, List<OfertasPayload.Oferta> compra, List<LojaMercador.Oferta> venda) implements CustomPayload {
	public static final Id<CatalogoPayload> ID = new Id<>(TrocasMartins.id("catalogo"));
	public static final PacketCodec<RegistryByteBuf, CatalogoPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.VAR_INT, CatalogoPayload::syncId,
		OfertasPayload.Oferta.CODEC.collect(PacketCodecs.toList()), CatalogoPayload::compra,
		LojaMercador.Oferta.CODEC.collect(PacketCodecs.toList()), CatalogoPayload::venda,
		CatalogoPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
