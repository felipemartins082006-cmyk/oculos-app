package com.martins.trocas.rede;

import com.martins.trocas.TrocasMartins;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;

/** Servidor -> cliente: o que o Mercador compra do seu inventário e os usos de cada troca. */
public record OfertasPayload(int syncId, List<Oferta> ofertas, HashMap<String, Integer> usos) implements CustomPacketPayload {
	public static final Type<OfertasPayload> TYPE = new Type<>(TrocasMartins.id("ofertas"));

	/**
	 * @param item       o item (quantidade 1)
	 * @param quantidade quantos itens o Mercador pede
	 * @param esmeraldas quantas esmeraldas ele paga por essa quantidade
	 * @param temJogador quantos desse item o jogador tem
	 */
	public record Oferta(ItemStack item, int quantidade, int esmeraldas, int temJogador) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Oferta> CODEC = StreamCodec.composite(
			ItemStack.STREAM_CODEC, Oferta::item,
			ByteBufCodecs.VAR_INT, Oferta::quantidade,
			ByteBufCodecs.VAR_INT, Oferta::esmeraldas,
			ByteBufCodecs.VAR_INT, Oferta::temJogador,
			Oferta::new);

		public boolean disponivel() {
			return temJogador >= quantidade;
		}
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, OfertasPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, OfertasPayload::syncId,
		Oferta.CODEC.apply(ByteBufCodecs.list()), OfertasPayload::ofertas,
		ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), OfertasPayload::usos,
		OfertasPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
