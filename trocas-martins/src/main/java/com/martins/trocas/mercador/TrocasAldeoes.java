package com.martins.trocas.mercador;

import com.martins.trocas.TrocasMartins;
import com.martins.trocas.trocas.NovasTrocas;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê as trocas de verdade de todos os aldeões (as do Minecraft, as do mascate, as deste mod e as
 * de outros mods) para o Mercador usar os mesmos preços.
 */
public final class TrocasAldeoes {
	/** Uma troca simples "N itens <-> E esmeraldas". */
	public record Taxa(int itens, int esmeraldas) {
		public double porItem() {
			return (double) esmeraldas / itens;
		}
	}

	/** O melhor que algum aldeão PAGA por cada item (mais esmeraldas por item). */
	public static final Map<Item, Taxa> COMPRAM = new HashMap<>();
	/** O mais barato que algum aldeão ou o mascate VENDE cada item (menos esmeraldas por item). */
	public static final Map<Item, Taxa> VENDEM = new HashMap<>();

	private TrocasAldeoes() {
	}

	public static void ler(MinecraftServer server) {
		COMPRAM.clear();
		VENDEM.clear();
		ServerWorld mundo = server.getOverworld();
		VillagerEntity aldeao = EntityType.VILLAGER.create(mundo);
		if (aldeao == null) return;
		Random r = Random.create(20261122L);

		for (Map.Entry<VillagerProfession, Int2ObjectMap<TradeOffers.Factory[]>> e : TradeOffers.PROFESSION_TO_LEVELED_TRADE.entrySet()) {
			aldeao.setVillagerData(aldeao.getVillagerData().withProfession(e.getKey()));
			for (TradeOffers.Factory[] nivel : e.getValue().values()) {
				for (TradeOffers.Factory f : nivel) ler(f, aldeao, r);
			}
		}
		for (Map.Entry<VillagerProfession, Map<Integer, List<TradeOffers.Factory>>> e : NovasTrocas.todas().entrySet()) {
			aldeao.setVillagerData(aldeao.getVillagerData().withProfession(e.getKey()));
			for (List<TradeOffers.Factory> nivel : e.getValue().values()) {
				for (TradeOffers.Factory f : nivel) ler(f, aldeao, r);
			}
		}
		for (Map.Entry<VillagerProfession, List<TradeOffers.Factory>> e : com.martins.trocas.trocas.NiveisExtras.todas().entrySet()) {
			aldeao.setVillagerData(aldeao.getVillagerData().withProfession(e.getKey()));
			for (TradeOffers.Factory f : e.getValue()) ler(f, aldeao, r);
		}
		aldeao.setVillagerData(aldeao.getVillagerData().withProfession(VillagerProfession.NONE));
		for (TradeOffers.Factory[] nivel : TradeOffers.WANDERING_TRADER_TRADES.values()) {
			for (TradeOffers.Factory f : nivel) ler(f, aldeao, r);
		}
		aldeao.discard();
		TrocasMartins.LOGGER.info("Mercador: {} itens comprados e {} vendidos pelos aldeões", COMPRAM.size(), VENDEM.size());
	}

	private static void ler(TradeOffers.Factory f, VillagerEntity aldeao, Random r) {
		// mapas de exploração procuram estruturas no mundo: não precisa e seria lento
		if (f instanceof TradeOffers.SellMapFactory) return;
		List<TradeOffer> ofertas = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			try {
				TradeOffer o = f.create(aldeao, r);
				if (o != null) ofertas.add(o);
			} catch (Exception ignorado) {
				return;
			}
		}
		for (TradeOffer o : ofertas) registrar(o);
	}

	private static void registrar(TradeOffer o) {
		ItemStack a = o.getOriginalFirstBuyItem();
		ItemStack b = o.getDisplayedSecondBuyItem();
		ItemStack venda = o.getSellItem();
		if (!b.isEmpty() || a.isEmpty() || venda.isEmpty()) return;

		if (venda.isOf(Items.EMERALD) && !a.isOf(Items.EMERALD) && a.getComponentChanges().isEmpty()) {
			Taxa t = new Taxa(a.getCount(), venda.getCount());
			COMPRAM.merge(a.getItem(), t, (x, y) -> y.porItem() > x.porItem() ? y : x);
		} else if (a.isOf(Items.EMERALD) && !venda.isOf(Items.EMERALD) && venda.getComponentChanges().isEmpty()) {
			Taxa t = new Taxa(venda.getCount(), a.getCount());
			VENDEM.merge(venda.getItem(), t, (x, y) -> y.porItem() < x.porItem() ? y : x);
		}
	}
}
