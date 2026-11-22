package com.martins.trocas.mercador;

import com.martins.trocas.TrocasMartins;
import com.martins.trocas.trocas.NiveisExtras;
import com.martins.trocas.trocas.NovasTrocas;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lê as trocas de verdade de todos os aldeões (as do Minecraft e do mascate, que no 26.x são dados do jogo,
 * as deste mod e as de outros mods) para o Mercador usar os mesmos preços.
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
		ServerLevel mundo = server.overworld();
		Villager aldeao = EntityTypes.VILLAGER.create(mundo, EntitySpawnReason.LOAD);
		if (aldeao == null) return;
		RandomSource r = RandomSource.create(20261122L);

		// trocas do jogo (todas as profissões e o mascate)
		LootContext ctx = new LootContext.Builder(new LootParams.Builder(mundo)
			.withParameter(LootContextParams.ORIGIN, aldeao.position())
			.withParameter(LootContextParams.THIS_ENTITY, aldeao)
			.withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
			.create(LootContextParamSets.VILLAGER_TRADE)).create(Optional.empty());
		for (Holder<VillagerTrade> troca : server.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).listElements().toList()) {
			for (int i = 0; i < 3; i++) {
				try {
					MerchantOffer o = troca.value().getOffer(ctx);
					if (o != null) registrar(o);
				} catch (Exception ignorado) {
					break;
				}
			}
		}
		// trocas deste mod
		for (Map.Entry<ResourceKey<VillagerProfession>, Map<Integer, List<NovasTrocas.Fabrica>>> e : NovasTrocas.todas().entrySet()) {
			for (List<NovasTrocas.Fabrica> nivel : e.getValue().values()) {
				for (NovasTrocas.Fabrica f : nivel) ler(f, aldeao, r);
			}
		}
		for (List<NovasTrocas.Fabrica> lista : NiveisExtras.todas().values()) {
			for (NovasTrocas.Fabrica f : lista) ler(f, aldeao, r);
		}
		aldeao.discard();
		TrocasMartins.LOGGER.info("Mercador: {} itens comprados e {} vendidos pelos aldeões", COMPRAM.size(), VENDEM.size());
	}

	private static void ler(NovasTrocas.Fabrica f, Villager aldeao, RandomSource r) {
		for (int i = 0; i < 3; i++) {
			try {
				MerchantOffer o = f.criar(aldeao, r);
				if (o != null) registrar(o);
			} catch (Exception ignorado) {
				return;
			}
		}
	}

	private static void registrar(MerchantOffer o) {
		ItemStack a = o.getBaseCostA();
		ItemStack b = o.getCostB();
		ItemStack venda = o.getResult();
		if (!b.isEmpty() || a.isEmpty() || venda.isEmpty()) return;

		if (venda.is(Items.EMERALD) && !a.is(Items.EMERALD) && a.getComponentsPatch().isEmpty()) {
			Taxa t = new Taxa(a.getCount(), venda.getCount());
			COMPRAM.merge(a.getItem(), t, (x, y) -> y.porItem() > x.porItem() ? y : x);
		} else if (a.is(Items.EMERALD) && !venda.is(Items.EMERALD) && venda.getComponentsPatch().isEmpty()) {
			Taxa t = new Taxa(venda.getCount(), a.getCount());
			VENDEM.merge(venda.getItem(), t, (x, y) -> y.porItem() < x.porItem() ? y : x);
		}
	}
}
