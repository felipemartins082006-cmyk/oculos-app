package com.martins.trocas.rede;

import com.martins.trocas.TrocasMartins;
import com.martins.trocas.mercador.LojaMercador;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Servidor -> cliente, ao abrir a tela: tudo o que o Mercador compra (para pesquisar) e o que ele vende. */
public record CatalogoPayload(int syncId, List<OfertasPayload.Oferta> compra, List<LojaMercador.Oferta> venda) implements CustomPacketPayload {
	public static final Type<CatalogoPayload> TYPE = new Type<>(TrocasMartins.id("catalogo"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CatalogoPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, CatalogoPayload::syncId,
		OfertasPayload.Oferta.CODEC.apply(ByteBufCodecs.list()), CatalogoPayload::compra,
		LojaMercador.Oferta.CODEC.apply(ByteBufCodecs.list()), CatalogoPayload::venda,
		CatalogoPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
