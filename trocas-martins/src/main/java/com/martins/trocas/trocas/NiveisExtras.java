package com.martins.trocas.trocas;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.rede.NivelPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.martins.trocas.trocas.NovasTrocas.*;

/**
 * Níveis depois de Mestre: Mestre II, III, IV... sem fim.
 * Para subir é preciso completar a experiência do nível atual (trocando com o aldeão), e cada nível
 * novo exige mais experiência. Cada nível traz uma troca mais forte da lista da profissão; quando a
 * lista acaba, as mesmas trocas voltam mais baratas a cada volta.
 */
public final class NiveisExtras {
	private static final Map<VillagerProfession, List<TradeOffers.Factory>> ELITE = new HashMap<>();

	private NiveisExtras() {
	}

	/** Experiência total para chegar ao nível extra {@code n} (1 = Mestre II). Mestre é 250. */
	public static int xpPara(int n) {
		int xp = 250;
		for (int k = 1; k <= n; k++) xp += 150 + 50 * k;
		return xp;
	}

	public static int nivelExtra(VillagerEntity v) {
		Integer n = v.getAttached(ModRegistro.NIVEL_EXTRA);
		return n == null ? 0 : n;
	}

	public static Map<VillagerProfession, List<TradeOffers.Factory>> todas() {
		return ELITE;
	}

	/** Chamado depois de cada troca: sobe quantos níveis extras a experiência permitir. */
	public static void verificar(VillagerEntity v) {
		if (v.getVillagerData().getLevel() < 5) return;
		List<TradeOffers.Factory> lista = ELITE.get(v.getVillagerData().getProfession());
		if (lista == null || lista.isEmpty()) return;
		int extra = nivelExtra(v);
		boolean subiu = false;
		while (v.getExperience() >= xpPara(extra + 1)) {
			extra++;
			subiu = true;
			TradeOffer o = criar(lista, extra, v);
			if (o != null) v.getOffers().add(o);
		}
		if (!subiu) return;
		v.setAttached(ModRegistro.NIVEL_EXTRA, extra);
		v.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0));
		v.playSound(SoundEvents.ENTITY_VILLAGER_CELEBRATE, 1.0f, 1.0f);
		if (v.getCustomer() instanceof ServerPlayerEntity p && p.currentScreenHandler != null) {
			p.sendTradeOffers(p.currentScreenHandler.syncId, v.getOffers(), 5, v.getExperience(), true, true);
			enviar(p, v);
		}
	}

	public static void enviar(ServerPlayerEntity p, VillagerEntity v) {
		if (ServerPlayNetworking.canSend(p, NivelPayload.ID)) ServerPlayNetworking.send(p, new NivelPayload(nivelExtra(v)));
	}

	/** A troca do nível extra {@code n}: a n-ésima da lista; a cada volta completa fica 15% mais barata. */
	private static TradeOffer criar(List<TradeOffers.Factory> lista, int n, VillagerEntity v) {
		int idx = (n - 1) % lista.size();
		int volta = (n - 1) / lista.size();
		TradeOffer base = lista.get(idx).create(v, v.getRandom());
		if (base == null || volta == 0) return base;
		double f = Math.pow(0.85, volta);
		ItemStack a = base.getOriginalFirstBuyItem();
		TradedItem na = new TradedItem(a.getItem(), Math.max(1, (int) Math.round(a.getCount() * f)));
		Optional<TradedItem> nb = base.getSecondBuyItem().map(b -> new TradedItem(b.item().value(), Math.max(1, (int) Math.round(b.count() * f))));
		return new TradeOffer(na, nb, base.getSellItem(), base.getMaxUses() + volta, base.getMerchantExperience(), base.getPriceMultiplier());
	}

	private static void add(VillagerProfession p, TradeOffers.Factory... f) {
		ELITE.computeIfAbsent(p, k -> new ArrayList<>()).addAll(List.of(f));
	}

	public static void registrar() {
		add(VillagerProfession.ARMORER,
			vendeEncantado(Items.DIAMOND_HELMET, 40, 2, 30, en(Enchantments.PROTECTION, 4), en(Enchantments.RESPIRATION, 3), en(Enchantments.AQUA_AFFINITY, 1)),
			vendeEncantado(Items.DIAMOND_LEGGINGS, 48, 2, 30, en(Enchantments.PROTECTION, 4), en(Enchantments.UNBREAKING, 3)),
			vendeEncantado(Items.DIAMOND_BOOTS, 44, 2, 30, en(Enchantments.FEATHER_FALLING, 4), en(Enchantments.DEPTH_STRIDER, 3)),
			vendeCom(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 32, Items.DIAMOND, 2, 2, 30),
			vendeEncantado(Items.DIAMOND_CHESTPLATE, 64, 1, 30, en(Enchantments.PROTECTION, 4), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1)),
			vendeCom(Items.NETHERITE_INGOT, 1, 64, Items.EMERALD_BLOCK, 8, 1, 30));
		add(VillagerProfession.BUTCHER,
			vende(Items.GOLDEN_APPLE, 3, 16, 4, 30),
			vende(Items.GOLDEN_CARROT, 16, 8, 8, 30),
			vende(Items.RABBIT_FOOT, 4, 12, 4, 30),
			vendeCom(Items.ENCHANTED_GOLDEN_APPLE, 1, 64, Items.GOLD_BLOCK, 4, 1, 30));
		add(VillagerProfession.CARTOGRAPHER,
			vende(Items.RECOVERY_COMPASS, 1, 20, 2, 30),
			vende(Items.ECHO_SHARD, 2, 20, 4, 30),
			vende(Items.LODESTONE, 2, 24, 2, 30),
			vendeCom(Items.ELYTRA, 1, 64, Items.PHANTOM_MEMBRANE, 16, 1, 30));
		add(VillagerProfession.CLERIC,
			vende(Items.EXPERIENCE_BOTTLE, 16, 12, 8, 30),
			vende(Items.ENDER_EYE, 4, 12, 6, 30),
			vende(Items.DRAGON_BREATH, 4, 16, 4, 30),
			vendeCom(Items.TOTEM_OF_UNDYING, 1, 48, Items.DIAMOND, 2, 2, 30),
			vende(Items.SHULKER_SHELL, 2, 32, 2, 30),
			vendeCom(Items.NETHER_STAR, 1, 64, Items.WITHER_SKELETON_SKULL, 3, 1, 30));
		add(VillagerProfession.FARMER,
			vende(Items.GLISTERING_MELON_SLICE, 4, 8, 8, 30),
			vende(Items.SNIFFER_EGG, 1, 20, 2, 30),
			vende(Items.GOLDEN_CARROT, 32, 16, 8, 30),
			vendeCom(Items.ENCHANTED_GOLDEN_APPLE, 1, 64, Items.GOLD_BLOCK, 6, 1, 30));
		add(VillagerProfession.FISHERMAN,
			vende(Items.NAUTILUS_SHELL, 4, 24, 4, 30),
			vendeEncantado(Items.FISHING_ROD, 32, 2, 30, en(Enchantments.LUCK_OF_THE_SEA, 3), en(Enchantments.LURE, 3), en(Enchantments.MENDING, 1)),
			vende(Items.HEART_OF_THE_SEA, 1, 32, 2, 30),
			vendeEncantado(Items.TRIDENT, 64, 1, 30, en(Enchantments.LOYALTY, 3), en(Enchantments.IMPALING, 3)),
			vendeCom(Items.CONDUIT, 1, 64, Items.HEART_OF_THE_SEA, 1, 1, 30));
		add(VillagerProfession.FLETCHER,
			vendeFlechas(Potions.STRONG_HARMING, 16, 12, 30),
			vendeEncantado(Items.BOW, 40, 2, 30, en(Enchantments.POWER, 5), en(Enchantments.INFINITY, 1), en(Enchantments.FLAME, 1)),
			vendeEncantado(Items.CROSSBOW, 40, 2, 30, en(Enchantments.MULTISHOT, 1), en(Enchantments.QUICK_CHARGE, 3), en(Enchantments.MENDING, 1)),
			vende(Items.BREEZE_ROD, 4, 24, 4, 30),
			vendeEncantado(Items.BOW, 64, 1, 30, en(Enchantments.POWER, 5), en(Enchantments.INFINITY, 1), en(Enchantments.UNBREAKING, 3), en(Enchantments.PUNCH, 2)));
		add(VillagerProfession.LEATHERWORKER,
			vende(Items.SADDLE, 2, 12, 4, 30),
			vendeEncantado(Items.TURTLE_HELMET, 32, 2, 30, en(Enchantments.RESPIRATION, 3), en(Enchantments.UNBREAKING, 3)),
			vende(Items.SHULKER_SHELL, 2, 32, 2, 30),
			vendeCom(Items.ELYTRA, 1, 64, Items.LEATHER, 32, 1, 30));
		add(VillagerProfession.LIBRARIAN,
			vendeLivro(Enchantments.SHARPNESS, 5, 32, 30),
			vendeLivro(Enchantments.EFFICIENCY, 5, 32, 30),
			vendeLivro(Enchantments.PROTECTION, 4, 24, 30),
			vendeLivro(Enchantments.SWIFT_SNEAK, 3, 48, 30),
			vendeLivro(Enchantments.SOUL_SPEED, 3, 40, 30),
			vendeLivro(Enchantments.FROST_WALKER, 2, 32, 30),
			vendeLivro(Enchantments.MENDING, 1, 24, 30));
		add(VillagerProfession.MASON,
			vende(Items.CRYING_OBSIDIAN, 16, 12, 8, 30),
			vende(Items.SCULK_CATALYST, 1, 20, 4, 30),
			vende(Items.ANCIENT_DEBRIS, 1, 48, 2, 30),
			vendeCom(Items.BEACON, 1, 64, Items.DIAMOND_BLOCK, 2, 1, 30));
		add(VillagerProfession.SHEPHERD,
			vende(Items.MUSIC_DISC_PIGSTEP, 1, 32, 1, 30),
			vende(Items.MUSIC_DISC_OTHERSIDE, 1, 32, 1, 30),
			vende(Items.MUSIC_DISC_RELIC, 1, 32, 1, 30),
			vende(Items.DRAGON_HEAD, 1, 64, 1, 30));
		add(VillagerProfession.TOOLSMITH,
			vendeEncantado(Items.DIAMOND_PICKAXE, 48, 2, 30, en(Enchantments.EFFICIENCY, 5), en(Enchantments.FORTUNE, 3), en(Enchantments.UNBREAKING, 3)),
			vendeEncantado(Items.DIAMOND_PICKAXE, 48, 2, 30, en(Enchantments.EFFICIENCY, 5), en(Enchantments.SILK_TOUCH, 1), en(Enchantments.UNBREAKING, 3)),
			vendeCom(Items.NETHERITE_SCRAP, 2, 48, Items.GOLD_INGOT, 4, 2, 30),
			vendeCom(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 32, Items.DIAMOND, 2, 2, 30),
			vendeEncantado(Items.DIAMOND_PICKAXE, 64, 1, 30, en(Enchantments.EFFICIENCY, 5), en(Enchantments.FORTUNE, 3), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1)));
		add(VillagerProfession.WEAPONSMITH,
			vendeEncantado(Items.DIAMOND_SWORD, 48, 2, 30, en(Enchantments.SHARPNESS, 5), en(Enchantments.LOOTING, 3), en(Enchantments.UNBREAKING, 3)),
			vende(Items.HEAVY_CORE, 1, 40, 1, 30),
			vendeEncantado(Items.DIAMOND_AXE, 48, 2, 30, en(Enchantments.SHARPNESS, 5), en(Enchantments.UNBREAKING, 3)),
			vendeCom(Items.MACE, 1, 64, Items.BREEZE_ROD, 8, 1, 30),
			vendeEncantado(Items.DIAMOND_SWORD, 64, 1, 30, en(Enchantments.SHARPNESS, 5), en(Enchantments.FIRE_ASPECT, 2), en(Enchantments.LOOTING, 3), en(Enchantments.MENDING, 1)));
	}
}
