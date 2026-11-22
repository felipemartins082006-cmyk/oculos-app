package com.martins.trocas.rede;

import com.martins.trocas.TrocasMartins;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Cliente -> servidor: o jogador clicou numa troca da lista.
 * comprar=false: vender {@code item}; comprar=true: comprar a troca {@code indice} da loja.
 */
public record EscolherOfertaPayload(boolean comprar, int indice, ItemStack item) implements CustomPacketPayload {
	public static final Type<EscolherOfertaPayload> TYPE = new Type<>(TrocasMartins.id("escolher_oferta"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EscolherOfertaPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, EscolherOfertaPayload::comprar,
		ByteBufCodecs.VAR_INT, EscolherOfertaPayload::indice,
		ItemStack.OPTIONAL_STREAM_CODEC, EscolherOfertaPayload::item,
		EscolherOfertaPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
