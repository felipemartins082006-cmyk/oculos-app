package com.martins.trocas.rede;

import com.martins.trocas.TrocasMartins;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

/** Servidor -> cliente: a lista de "trocas" do Mercador (o que ele compra do seu inventário). */
public record OfertasPayload(int syncId, List<Oferta> ofertas) implements CustomPayload {
	public static final Id<OfertasPayload> ID = new Id<>(TrocasMartins.id("ofertas"));

	/**
	 * @param item       o item (quantidade 1)
	 * @param quantidade quantos itens o Mercador pede
	 * @param esmeraldas quantas esmeraldas ele paga por essa quantidade
	 * @param temJogador quantos desse item o jogador tem (inventário + espaços de pagamento)
	 */
	public record Oferta(ItemStack item, int quantidade, int esmeraldas, int temJogador) {
		public static final PacketCodec<RegistryByteBuf, Oferta> CODEC = PacketCodec.tuple(
			ItemStack.PACKET_CODEC, Oferta::item,
			PacketCodecs.VAR_INT, Oferta::quantidade,
			PacketCodecs.VAR_INT, Oferta::esmeraldas,
			PacketCodecs.VAR_INT, Oferta::temJogador,
			Oferta::new);

		public boolean disponivel() {
			return temJogador >= quantidade;
		}
	}

	public static final PacketCodec<RegistryByteBuf, OfertasPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.VAR_INT, OfertasPayload::syncId,
		Oferta.CODEC.collect(PacketCodecs.toList()), OfertasPayload::ofertas,
		OfertasPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
