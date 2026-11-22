package com.martins.trocas.mercador;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Preços-base em MILÉSIMOS de esmeralda por item (1000 = 1 esmeralda).
 * Tudo o que não está aqui é calculado a partir das receitas (soma dos ingredientes),
 * e o que não tem receita usa um valor pela raridade do item.
 * Linhas começando com # são tags de itens.
 */
final class TabelaPrecos {
	private static final String TABELA = """
		# --- terra, pedra e blocos naturais ---
		#minecraft:dirt 8
		minecraft:cobblestone 8
		minecraft:cobbled_deepslate 8
		minecraft:stone 12
		minecraft:deepslate 12
		minecraft:andesite 8
		minecraft:diorite 8
		minecraft:granite 8
		minecraft:tuff 10
		minecraft:calcite 15
		minecraft:netherrack 6
		minecraft:basalt 10
		minecraft:blackstone 10
		minecraft:end_stone 20
		#minecraft:sand 10
		minecraft:gravel 10
		minecraft:soul_sand 15
		minecraft:soul_soil 15
		minecraft:clay_ball 25
		minecraft:mud 10
		minecraft:snowball 5
		minecraft:ice 30
		minecraft:flint 30
		minecraft:obsidian 300
		minecraft:crying_obsidian 400
		minecraft:magma_block 60
		minecraft:dripstone_block 30
		minecraft:pointed_dripstone 20
		minecraft:moss_block 20
		minecraft:sculk 40
		minecraft:sculk_catalyst 1500
		minecraft:sculk_sensor 600
		minecraft:sculk_shrieker 1000
		minecraft:mycelium 40
		minecraft:podzol 20
		minecraft:prismarine 150
		minecraft:sponge 500
		minecraft:wet_sponge 500
		minecraft:glow_lichen 10
		minecraft:amethyst_cluster 200
		minecraft:ochre_froglight 200
		minecraft:verdant_froglight 200
		minecraft:pearlescent_froglight 200
		minecraft:shroomlight 80
		minecraft:cobweb 60
		#minecraft:terracotta 30
		# --- madeira e plantas ---
		#minecraft:logs 40
		minecraft:bamboo 5
		#minecraft:saplings 15
		#minecraft:leaves 3
		#minecraft:small_flowers 20
		#minecraft:tall_flowers 30
		minecraft:short_grass 2
		minecraft:tall_grass 3
		minecraft:fern 2
		minecraft:large_fern 3
		minecraft:dead_bush 5
		minecraft:vine 5
		minecraft:lily_pad 30
		minecraft:seagrass 3
		minecraft:kelp 5
		minecraft:sea_pickle 30
		minecraft:cactus 30
		minecraft:sugar_cane 30
		minecraft:wheat 50
		minecraft:wheat_seeds 5
		minecraft:beetroot_seeds 5
		minecraft:melon_seeds 10
		minecraft:pumpkin_seeds 10
		minecraft:torchflower_seeds 300
		minecraft:pitcher_pod 300
		minecraft:carrot 45
		minecraft:potato 40
		minecraft:poisonous_potato 10
		minecraft:beetroot 60
		minecraft:melon_slice 12
		minecraft:pumpkin 150
		minecraft:apple 60
		minecraft:sweet_berries 20
		minecraft:glow_berries 30
		minecraft:cocoa_beans 40
		minecraft:nether_wart 50
		minecraft:chorus_fruit 50
		minecraft:brown_mushroom 30
		minecraft:red_mushroom 30
		minecraft:crimson_fungus 30
		minecraft:warped_fungus 30
		minecraft:crimson_roots 5
		minecraft:warped_roots 5
		minecraft:nether_sprouts 5
		minecraft:twisting_vines 10
		minecraft:weeping_vines 10
		minecraft:spore_blossom 300
		minecraft:hanging_roots 5
		minecraft:big_dripleaf 30
		minecraft:small_dripleaf 30
		minecraft:azalea 30
		minecraft:flowering_azalea 40
		minecraft:pink_petals 10
		minecraft:cherry_leaves 3
		minecraft:mangrove_roots 10
		minecraft:muddy_mangrove_roots 12
		minecraft:mangrove_propagule 15
		# --- alimentos de animais ---
		minecraft:beef 70
		minecraft:porkchop 70
		minecraft:chicken 60
		minecraft:mutton 60
		minecraft:rabbit 60
		minecraft:cod 50
		minecraft:salmon 60
		minecraft:tropical_fish 80
		minecraft:pufferfish 100
		minecraft:egg 30
		minecraft:milk_bucket 350
		minecraft:honey_bottle 150
		minecraft:honeycomb 80
		# --- drops de criaturas ---
		minecraft:rotten_flesh 30
		minecraft:bone 60
		minecraft:string 60
		minecraft:spider_eye 80
		minecraft:gunpowder 100
		minecraft:slime_ball 120
		minecraft:ender_pearl 600
		minecraft:blaze_rod 500
		minecraft:breeze_rod 600
		minecraft:ghast_tear 1000
		minecraft:leather 120
		minecraft:feather 40
		minecraft:ink_sac 60
		minecraft:glow_ink_sac 150
		minecraft:phantom_membrane 300
		minecraft:rabbit_hide 50
		minecraft:rabbit_foot 400
		minecraft:prismarine_shard 80
		minecraft:prismarine_crystals 100
		minecraft:nautilus_shell 1500
		minecraft:heart_of_the_sea 8000
		minecraft:shulker_shell 3000
		minecraft:nether_star 40000
		minecraft:dragon_breath 500
		minecraft:dragon_egg 64000
		minecraft:turtle_scute 800
		minecraft:armadillo_scute 300
		minecraft:turtle_egg 500
		minecraft:sniffer_egg 3000
		minecraft:echo_shard 2000
		minecraft:goat_horn 1000
		minecraft:totem_of_undying 10000
		minecraft:trident 12000
		minecraft:heavy_core 20000
		minecraft:wither_skeleton_skull 6000
		minecraft:skeleton_skull 2000
		minecraft:zombie_head 2000
		minecraft:creeper_head 2000
		minecraft:piglin_head 2000
		minecraft:dragon_head 20000
		minecraft:experience_bottle 300
		#minecraft:wool 50
		# --- minérios e minerais ---
		minecraft:coal 50
		minecraft:charcoal 40
		minecraft:raw_iron 250
		minecraft:iron_ingot 300
		minecraft:raw_copper 50
		minecraft:copper_ingot 60
		minecraft:raw_gold 450
		minecraft:gold_ingot 500
		minecraft:redstone 40
		minecraft:lapis_lazuli 60
		minecraft:quartz 60
		minecraft:amethyst_shard 80
		minecraft:glowstone_dust 50
		minecraft:diamond 3000
		minecraft:netherite_scrap 6000
		minecraft:ancient_debris 6000
		#minecraft:coal_ores 80
		#minecraft:iron_ores 350
		#minecraft:copper_ores 80
		#minecraft:gold_ores 600
		#minecraft:redstone_ores 200
		#minecraft:lapis_ores 300
		#minecraft:diamond_ores 3500
		#minecraft:emerald_ores 900
		minecraft:nether_quartz_ore 100
		minecraft:nether_gold_ore 150
		minecraft:gilded_blackstone 200
		minecraft:budding_amethyst 0
		# --- tesouros e itens raros ---
		minecraft:name_tag 2000
		minecraft:saddle 1500
		minecraft:enchanted_golden_apple 20000
		minecraft:elytra 15000
		minecraft:iron_horse_armor 800
		minecraft:golden_horse_armor 1000
		minecraft:diamond_horse_armor 3000
		#minecraft:creeper_drop_music_discs 1500
		minecraft:music_disc_pigstep 4000
		minecraft:music_disc_otherside 4000
		minecraft:music_disc_relic 3000
		minecraft:music_disc_5 3000
		minecraft:music_disc_creator 3000
		minecraft:music_disc_creator_music_box 3000
		minecraft:music_disc_precipice 3000
		minecraft:disc_fragment_5 300
		#minecraft:decorated_pot_sherds 400
		#minecraft:trim_templates 1500
		minecraft:netherite_upgrade_smithing_template 5000
		minecraft:enchanted_book 300
		minecraft:potion 150
		minecraft:splash_potion 200
		minecraft:lingering_potion 350
		minecraft:tipped_arrow 30
		minecraft:bell 2000
		minecraft:trial_key 800
		minecraft:ominous_trial_key 2500
		minecraft:ominous_bottle 1500
		minecraft:wind_charge 150
		minecraft:mace 25000
		minecraft:chainmail_helmet 600
		minecraft:chainmail_chestplate 1000
		minecraft:chainmail_leggings 900
		minecraft:chainmail_boots 500
		minecraft:written_book 200
		minecraft:filled_map 300
		minecraft:firework_rocket 40
		minecraft:firework_star 40
		minecraft:suspicious_stew 80
		minecraft:bee_nest 800
		minecraft:beehive 800
		""";

	static Map<String, Long> padrao() {
		Map<String, Long> mapa = new LinkedHashMap<>();
		for (String linha : TABELA.split("\n")) {
			String l = linha.strip();
			if (l.isEmpty() || l.startsWith("# ")) continue;
			String[] partes = l.split("\\s+");
			if (partes.length != 2) continue;
			mapa.put(partes[0], Long.parseLong(partes[1]));
		}
		return mapa;
	}

	private TabelaPrecos() {
	}
}
