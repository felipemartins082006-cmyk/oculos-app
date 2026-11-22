package com.martins.trocas.trocas;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.rede.NivelPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

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
	private static final Map<ResourceKey<VillagerProfession>, List<Fabrica>> ELITE = new HashMap<>();

	private NiveisExtras() {
	}

	/** Experiência total para chegar ao nível extra {@code n} (1 = Mestre II). Mestre é 250. */
	public static int xpPara(int n) {
		int xp = 250;
		for (int k = 1; k <= n; k++) xp += 150 + 50 * k;
		return xp;
	}

	public static int nivelExtra(Villager v) {
		Integer n = v.getAttached(ModRegistro.NIVEL_EXTRA);
		return n == null ? 0 : n;
	}

	public static Map<ResourceKey<VillagerProfession>, List<Fabrica>> todas() {
		return ELITE;
	}

	/** Chamado depois de cada troca: sobe quantos níveis extras a experiência permitir. */
	public static void verificar(Villager v) {
		if (v.getVillagerData().level() < 5) return;
		List<Fabrica> lista = v.getVillagerData().profession().unwrapKey().map(ELITE::get).orElse(null);
		if (lista == null || lista.isEmpty()) return;
		int extra = nivelExtra(v);
		boolean subiu = false;
		while (v.getVillagerXp() >= xpPara(extra + 1)) {
			extra++;
			subiu = true;
			if (extra <= lista.size()) {
				// fase 1: os itens mais fortes do jogo, um por nível
				MerchantOffer o = lista.get(extra - 1).criar(v, v.getRandom());
				if (o != null) v.getOffers().add(o);
			} else {
				// fase 2: alterna otimizar uma troca forte e inovar com uma troca nova
				int k = extra - lista.size();
				if (k % 2 == 1) otimizar(v, lista, (k - 1) / 2);
				else {
					MerchantOffer o = inovar(v);
					if (o != null) v.getOffers().add(o);
				}
			}
		}
		if (!subiu) return;
		v.setAttached(ModRegistro.NIVEL_EXTRA, extra);
		v.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
		v.playSound(SoundEvents.VILLAGER_CELEBRATE, 1.0f, 1.0f);
		if (v.getTradingPlayer() instanceof ServerPlayer p && p.containerMenu != null) {
			p.sendMerchantOffers(p.containerMenu.containerId, v.getOffers(), 5, v.getVillagerXp(), true, true);
			enviar(p, v);
		}
	}

	public static void enviar(ServerPlayer p, Villager v) {
		if (ServerPlayNetworking.canSend(p, NivelPayload.TYPE)) ServerPlayNetworking.send(p, new NivelPayload(nivelExtra(v)));
	}

	/** Otimizar: uma das trocas fortes fica 15% mais barata e ganha 1 uso (uma de cada vez, em rodízio). */
	private static void otimizar(Villager v, List<Fabrica> lista, int vez) {
		MerchantOffer modelo = lista.get(vez % lista.size()).criar(v, v.getRandom());
		if (modelo == null) return;
		MerchantOffers ofertas = v.getOffers();
		for (int i = 0; i < ofertas.size(); i++) {
			MerchantOffer o = ofertas.get(i);
			if (!ItemStack.isSameItemSameComponents(o.getResult(), modelo.getResult())
				|| !o.getBaseCostA().is(modelo.getBaseCostA().getItem())) continue;
			ofertas.set(i, barato(o, 0.85, 1));
			return;
		}
	}

	private static MerchantOffer barato(MerchantOffer o, double f, int usosExtras) {
		ItemCost ca = o.getItemCostA();
		ItemCost na = new ItemCost(ca.item(), Math.max(1, (int) Math.floor(ca.count() * f)), ca.components());
		Optional<ItemCost> nb = o.getItemCostB().map(b -> new ItemCost(b.item(), Math.max(1, (int) Math.floor(b.count() * f)), b.components()));
		return new MerchantOffer(na, nb, o.getResult(), o.getMaxUses() + usosExtras, o.getXp(), o.getPriceMultiplier());
	}

	private static final Item[] NETHERITE = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
		Items.NETHERITE_BOOTS, Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL,
		Items.NETHERITE_HOE, Items.MACE, Items.TRIDENT, Items.BOW, Items.CROSSBOW, Items.ELYTRA, Items.FISHING_ROD};

	/** Inovar: uma troca nova sorteada (livro no máximo, equipamento encantado ou um item forte em dobro). */
	private static MerchantOffer inovar(Villager v) {
		RandomSource r = v.getRandom();
		List<Holder<Enchantment>> encs = new ArrayList<>();
		v.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
			.filter(e -> !e.is(EnchantmentTags.CURSE)).forEach(encs::add);
		switch (r.nextInt(3)) {
			case 0 -> {
				Holder<Enchantment> e = encs.get(r.nextInt(encs.size()));
				ItemStack livro = EnchantmentHelper.createBook(new EnchantmentInstance(e, e.value().getMaxLevel()));
				return new MerchantOffer(new ItemCost(Items.EMERALD, 20 + r.nextInt(30)), Optional.of(new ItemCost(Items.BOOK, 1)), livro, 3, 30, 0.2f);
			}
			case 1 -> {
				ItemStack item = new ItemStack(NETHERITE[r.nextInt(NETHERITE.length)]);
				List<Holder<Enchantment>> cabem = new ArrayList<>();
				for (Holder<Enchantment> e : encs) if (e.value().canEnchant(item)) cabem.add(e);
				for (int i = 0; i < 4 && !cabem.isEmpty(); i++) {
					Holder<Enchantment> e = cabem.remove(r.nextInt(cabem.size()));
					if (EnchantmentHelper.isEnchantmentCompatible(EnchantmentHelper.getEnchantmentsForCrafting(item).keySet(), e)) {
						item.enchant(e, e.value().getMaxLevel());
					}
				}
				return new MerchantOffer(new ItemCost(Items.EMERALD, 64), Optional.of(new ItemCost(Items.EMERALD_BLOCK, 8 + r.nextInt(9))), item, 1, 30, 0.05f);
			}
			default -> {
				List<Fabrica> todas = new ArrayList<>();
				ELITE.values().forEach(todas::addAll);
				MerchantOffer base = todas.get(r.nextInt(todas.size())).criar(v, r);
				if (base == null) return null;
				ItemStack venda = base.getResult().copy();
				if (venda.getMaxStackSize() < venda.getCount() * 2) return base;
				venda.setCount(venda.getCount() * 2);
				ItemStack a = base.getBaseCostA();
				int preco = (int) Math.ceil(a.getCount() * 1.6);
				Optional<ItemCost> b = base.getItemCostB().map(x -> new ItemCost(x.item(), Math.min(64, x.count() * 2), x.components()));
				if (preco > 64) {
					b = Optional.of(new ItemCost(Items.EMERALD_BLOCK, Math.min(64, (preco - 64 + 8) / 9 + b.map(ItemCost::count).orElse(0))));
					preco = 64;
				}
				return new MerchantOffer(new ItemCost(a.getItem(), preco), b, venda, base.getMaxUses(), 30, 0.05f);
			}
		}
	}

	private static void add(ResourceKey<VillagerProfession> p, Fabrica... f) {
		ELITE.computeIfAbsent(p, k -> new ArrayList<>()).addAll(List.of(f));
	}

	public static void registrar() {
		add(VillagerProfession.ARMORER,
			vendeEncantado(Items.DIAMOND_HELMET, 40, 2, 30, en(Enchantments.PROTECTION, 4), en(Enchantments.RESPIRATION, 3), en(Enchantments.AQUA_AFFINITY, 1)),
			vendeEncantado(Items.DIAMOND_LEGGINGS, 48, 2, 30, en(Enchantments.PROTECTION, 4), en(Enchantments.UNBREAKING, 3)),
			vendeEncantado(Items.DIAMOND_BOOTS, 44, 2, 30, en(Enchantments.FEATHER_FALLING, 4), en(Enchantments.DEPTH_STRIDER, 3)),
			vendeCom(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 32, Items.DIAMOND, 2, 2, 30),
			vendeEncantado(Items.DIAMOND_CHESTPLATE, 64, 1, 30, en(Enchantments.PROTECTION, 4), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1)),
			vendeCom(Items.NETHERITE_INGOT, 1, 64, Items.EMERALD_BLOCK, 8, 1, 30),
			vendeCom(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 48, Items.DIAMOND, 4, 1, 30),
			vendeCom(Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 40, Items.DIAMOND, 4, 1, 30),
			vendeEncantadoCom(Items.NETHERITE_HELMET, 64, Items.EMERALD_BLOCK, 12, 1, 30, en(Enchantments.PROTECTION, 4), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1), en(Enchantments.RESPIRATION, 3), en(Enchantments.AQUA_AFFINITY, 1)),
			vendeEncantadoCom(Items.NETHERITE_CHESTPLATE, 64, Items.EMERALD_BLOCK, 16, 1, 30, en(Enchantments.PROTECTION, 4), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1), en(Enchantments.THORNS, 3)),
			vendeEncantadoCom(Items.NETHERITE_LEGGINGS, 64, Items.EMERALD_BLOCK, 14, 1, 30, en(Enchantments.PROTECTION, 4), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1), en(Enchantments.SWIFT_SNEAK, 3)),
			vendeEncantadoCom(Items.NETHERITE_BOOTS, 64, Items.EMERALD_BLOCK, 12, 1, 30, en(Enchantments.PROTECTION, 4), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1), en(Enchantments.FEATHER_FALLING, 4), en(Enchantments.DEPTH_STRIDER, 3), en(Enchantments.SOUL_SPEED, 3)));
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
			vendeCom(Items.NETHER_STAR, 1, 64, Items.WITHER_SKELETON_SKULL, 3, 1, 30),
			vende(Items.WITHER_SKELETON_SKULL, 1, 48, 2, 30),
			vendeCom(Items.END_CRYSTAL, 4, 48, Items.GHAST_TEAR, 4, 2, 30),
			vendeCom(Items.DRAGON_EGG, 1, 64, Items.NETHER_STAR, 1, 1, 30));
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
			vendeCom(Items.CONDUIT, 1, 64, Items.HEART_OF_THE_SEA, 1, 1, 30),
			vendeEncantadoCom(Items.TRIDENT, 64, Items.HEART_OF_THE_SEA, 1, 1, 30, en(Enchantments.RIPTIDE, 3), en(Enchantments.IMPALING, 5), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1)));
		add(VillagerProfession.FLETCHER,
			vendeFlechas(Potions.STRONG_HARMING, 16, 12, 30),
			vendeEncantado(Items.BOW, 40, 2, 30, en(Enchantments.POWER, 5), en(Enchantments.INFINITY, 1), en(Enchantments.FLAME, 1)),
			vendeEncantado(Items.CROSSBOW, 40, 2, 30, en(Enchantments.MULTISHOT, 1), en(Enchantments.QUICK_CHARGE, 3), en(Enchantments.MENDING, 1)),
			vende(Items.BREEZE_ROD, 4, 24, 4, 30),
			vendeEncantado(Items.BOW, 64, 1, 30, en(Enchantments.POWER, 5), en(Enchantments.INFINITY, 1), en(Enchantments.UNBREAKING, 3), en(Enchantments.PUNCH, 2)),
			vendeEncantado(Items.CROSSBOW, 48, 1, 30, en(Enchantments.PIERCING, 4), en(Enchantments.QUICK_CHARGE, 3), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1)));
		add(VillagerProfession.LEATHERWORKER,
			vende(Items.SADDLE, 2, 12, 4, 30),
			vendeEncantado(Items.TURTLE_HELMET, 32, 2, 30, en(Enchantments.RESPIRATION, 3), en(Enchantments.UNBREAKING, 3)),
			vende(Items.SHULKER_SHELL, 2, 32, 2, 30),
			vendeCom(Items.ELYTRA, 1, 64, Items.LEATHER, 32, 1, 30),
			vendeEncantadoCom(Items.ELYTRA, 64, Items.EMERALD_BLOCK, 16, 1, 30, en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1)));
		add(VillagerProfession.LIBRARIAN,
			vendeLivro(Enchantments.SHARPNESS, 5, 32, 30),
			vendeLivro(Enchantments.EFFICIENCY, 5, 32, 30),
			vendeLivro(Enchantments.PROTECTION, 4, 24, 30),
			vendeLivro(Enchantments.SWIFT_SNEAK, 3, 48, 30),
			vendeLivro(Enchantments.SOUL_SPEED, 3, 40, 30),
			vendeLivro(Enchantments.FROST_WALKER, 2, 32, 30),
			vendeLivro(Enchantments.MENDING, 1, 24, 30),
			vendeLivro(Enchantments.UNBREAKING, 3, 20, 30),
			vendeLivro(Enchantments.FORTUNE, 3, 32, 30),
			vendeLivro(Enchantments.LOOTING, 3, 32, 30),
			vendeLivro(Enchantments.SILK_TOUCH, 1, 24, 30),
			vendeLivro(Enchantments.POWER, 5, 32, 30),
			vendeLivro(Enchantments.INFINITY, 1, 32, 30),
			vendeLivro(Enchantments.THORNS, 3, 32, 30),
			vendeLivro(Enchantments.DEPTH_STRIDER, 3, 24, 30),
			vendeLivro(Enchantments.RESPIRATION, 3, 24, 30),
			vendeLivro(Enchantments.FEATHER_FALLING, 4, 24, 30),
			vendeLivro(Enchantments.WIND_BURST, 3, 48, 30),
			vendeLivro(Enchantments.DENSITY, 5, 40, 30),
			vendeLivro(Enchantments.BREACH, 4, 40, 30),
			vendeLivro(Enchantments.RIPTIDE, 3, 32, 30),
			vendeLivro(Enchantments.CHANNELING, 1, 32, 30));
		add(VillagerProfession.MASON,
			vende(Items.CRYING_OBSIDIAN, 16, 12, 8, 30),
			vende(Items.SCULK_CATALYST, 1, 20, 4, 30),
			vende(Items.ANCIENT_DEBRIS, 1, 48, 2, 30),
			vendeCom(Items.BEACON, 1, 64, Items.DIAMOND_BLOCK, 2, 1, 30),
			vendeCom(Items.SHULKER_BOX, 1, 48, Items.ENDER_PEARL, 4, 2, 30),
			vende(Items.OMINOUS_TRIAL_KEY, 1, 32, 2, 30));
		add(VillagerProfession.SHEPHERD,
			vende(Items.MUSIC_DISC_PIGSTEP, 1, 32, 1, 30),
			vende(Items.MUSIC_DISC_OTHERSIDE, 1, 32, 1, 30),
			vende(Items.MUSIC_DISC_RELIC, 1, 32, 1, 30),
			vende(Items.DRAGON_HEAD, 1, 64, 1, 30),
			vende(Items.MUSIC_DISC_CREATOR, 1, 32, 1, 30),
			vende(Items.MUSIC_DISC_PRECIPICE, 1, 32, 1, 30),
			vende(Items.FLOW_BANNER_PATTERN, 1, 24, 2, 30));
		add(VillagerProfession.TOOLSMITH,
			vendeEncantado(Items.DIAMOND_PICKAXE, 48, 2, 30, en(Enchantments.EFFICIENCY, 5), en(Enchantments.FORTUNE, 3), en(Enchantments.UNBREAKING, 3)),
			vendeEncantado(Items.DIAMOND_PICKAXE, 48, 2, 30, en(Enchantments.EFFICIENCY, 5), en(Enchantments.SILK_TOUCH, 1), en(Enchantments.UNBREAKING, 3)),
			vendeCom(Items.NETHERITE_SCRAP, 2, 48, Items.GOLD_INGOT, 4, 2, 30),
			vendeCom(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 32, Items.DIAMOND, 2, 2, 30),
			vendeEncantado(Items.DIAMOND_PICKAXE, 64, 1, 30, en(Enchantments.EFFICIENCY, 5), en(Enchantments.FORTUNE, 3), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1)),
			vendeEncantadoCom(Items.NETHERITE_PICKAXE, 64, Items.EMERALD_BLOCK, 14, 1, 30, en(Enchantments.EFFICIENCY, 5), en(Enchantments.FORTUNE, 3), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1)),
			vendeEncantadoCom(Items.NETHERITE_AXE, 64, Items.EMERALD_BLOCK, 12, 1, 30, en(Enchantments.EFFICIENCY, 5), en(Enchantments.SILK_TOUCH, 1), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1)),
			vendeEncantadoCom(Items.NETHERITE_SHOVEL, 64, Items.EMERALD_BLOCK, 10, 1, 30, en(Enchantments.EFFICIENCY, 5), en(Enchantments.SILK_TOUCH, 1), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1)));
		add(VillagerProfession.WEAPONSMITH,
			vendeEncantado(Items.DIAMOND_SWORD, 48, 2, 30, en(Enchantments.SHARPNESS, 5), en(Enchantments.LOOTING, 3), en(Enchantments.UNBREAKING, 3)),
			vende(Items.HEAVY_CORE, 1, 40, 1, 30),
			vendeEncantado(Items.DIAMOND_AXE, 48, 2, 30, en(Enchantments.SHARPNESS, 5), en(Enchantments.UNBREAKING, 3)),
			vendeCom(Items.MACE, 1, 64, Items.BREEZE_ROD, 8, 1, 30),
			vendeEncantado(Items.DIAMOND_SWORD, 64, 1, 30, en(Enchantments.SHARPNESS, 5), en(Enchantments.FIRE_ASPECT, 2), en(Enchantments.LOOTING, 3), en(Enchantments.MENDING, 1)),
			vendeEncantadoCom(Items.MACE, 64, Items.HEAVY_CORE, 1, 1, 30, en(Enchantments.DENSITY, 5), en(Enchantments.WIND_BURST, 3), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1)),
			vendeEncantadoCom(Items.NETHERITE_SWORD, 64, Items.EMERALD_BLOCK, 16, 1, 30, en(Enchantments.SHARPNESS, 5), en(Enchantments.LOOTING, 3), en(Enchantments.FIRE_ASPECT, 2), en(Enchantments.SWEEPING_EDGE, 3), en(Enchantments.UNBREAKING, 3), en(Enchantments.MENDING, 1)));
	}
}
