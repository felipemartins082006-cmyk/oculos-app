package com.martins.trocas.rede;

import com.martins.trocas.TrocasMartins;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Cliente -> servidor: o jogador clicou numa troca da lista.
 * comprar=false: vender {@code item} (o servidor coloca esse item no pagamento).
 * comprar=true: comprar a troca {@code indice} da loja (o servidor coloca as esmeraldas no pagamento).
 */
public record EscolherOfertaPayload(boolean comprar, int indice, ItemStack item) implements CustomPayload {
	public static final Id<EscolherOfertaPayload> ID = new Id<>(TrocasMartins.id("escolher_oferta"));
	public static final PacketCodec<RegistryByteBuf, EscolherOfertaPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.BOOL, EscolherOfertaPayload::comprar,
		PacketCodecs.VAR_INT, EscolherOfertaPayload::indice,
		ItemStack.OPTIONAL_PACKET_CODEC, EscolherOfertaPayload::item,
		EscolherOfertaPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
