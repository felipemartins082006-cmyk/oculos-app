package com.martins.trocas.mercador;

import com.martins.trocas.TrocasMartins;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula quanto o Mercador paga por cada item.
 * Valores internos em milésimos de esmeralda (1000 = 1 esmeralda).
 */
public final class PrecoMercador {
	public static final TagKey<Item> NAO_COMPRA = TagKey.of(RegistryKeys.ITEM, TrocasMartins.id("nao_compra"));
	public static final long UMA_ESMERALDA = 1000;
	/** Itens criados valem 90% da soma dos ingredientes (o Mercador prefere matéria-prima). */
	public static final double FATOR_RECEITA = 0.9;
	/** Guardado em itens únicos comprados de aldeões: esmeraldas pagas. */
	public static final String CHAVE_PAGO = "trocasmartins_pago";

	private static volatile Map<Item, Long> valores = new HashMap<>();

	private PrecoMercador() {
	}

	// ------------------------------------------------------------------ cálculo da tabela

	public static void recalcular(MinecraftServer server) {
		long inicio = System.currentTimeMillis();
		Map<Item, Long> base = new HashMap<>();
		aplicar(base, TabelaPrecos.padrao());
		Map<String, Long> doConfig = new HashMap<>();
		MercadorConfig.atual.precos.forEach((k, v) -> {
			if (v != null) doConfig.put(k, Math.max(0, Math.round(v * UMA_ESMERALDA)));
		});
		aplicar(base, doConfig);

		Map<Item, Long> v = new HashMap<>(base);
		Map<Item, Double> exatos = new HashMap<>();
		List<RecipeEntry<?>> receitas = new ArrayList<>(server.getRecipeManager().values());

		// Valor de um item fabricado = soma dos ingredientes (o menor entre as receitas possíveis).
		// Repete até estabilizar; como não há perda, ciclos (lingote -> bloco -> lingote) não mudam o valor.
		for (int passo = 0; passo < 64; passo++) {
			boolean mudou = false;
			for (RecipeEntry<?> entrada : receitas) {
				Recipe<?> receita = entrada.value();
				ItemStack saida;
				List<Ingredient> ingredientes;
				try {
					saida = receita.getResult(server.getRegistryManager());
					ingredientes = receita.getIngredients();
				} catch (Exception e) {
					continue;
				}
				if (saida == null || saida.isEmpty() || base.containsKey(saida.getItem())) continue;

				long soma = 0;
				int usados = 0;
				boolean completo = true;
				for (Ingredient ing : ingredientes) {
					if (ing.isEmpty()) continue;
					long menor = menorValor(ing, v);
					if (menor < 0) {
						completo = false;
						break;
					}
					soma += menor;
					usados++;
				}
				if (!completo || usados == 0) continue;
				if (receita instanceof AbstractCookingRecipe) soma += 15; // combustível e trabalho

				double candidato = Math.max(1.0, (double) soma / saida.getCount());
				Double atual = exatos.get(saida.getItem());
				if (atual == null || candidato < atual - 1e-9) {
					exatos.put(saida.getItem(), candidato);
					v.put(saida.getItem(), (long) Math.ceil(candidato));
					mudou = true;
				}
			}
			if (!mudou) break;
		}

		// Itens de netherite (feitos na mesa de ferraria): versão de diamante + lingote de netherite.
		Long lingote = v.get(Items.NETHERITE_INGOT);
		if (lingote != null) {
			for (Item item : Registries.ITEM) {
				Identifier id = Registries.ITEM.getId(item);
				if (v.containsKey(item) || !id.getPath().startsWith("netherite_")) continue;
				Identifier diamante = Identifier.of(id.getNamespace(), "diamond_" + id.getPath().substring("netherite_".length()));
				Registries.ITEM.getOrEmpty(diamante).map(v::get).ifPresent(valorDiamante -> v.put(item, valorDiamante + lingote));
			}
		}

		// itens fabricados valem um pouco menos que a matéria-prima
		Map<Item, Long> finais = new HashMap<>(v);
		for (Item item : finais.keySet()) {
			if (!base.containsKey(item)) finais.put(item, Math.max(1, Math.round(v.get(item) * FATOR_RECEITA)));
		}
		valores = finais;
		TrocasMartins.LOGGER.info("Mercador: {} preços calculados em {} ms", finais.size(), System.currentTimeMillis() - inicio);
	}

	private static void aplicar(Map<Item, Long> destino, Map<String, Long> origem) {
		// primeiro as tags, depois os itens específicos (que têm prioridade)
		origem.forEach((chave, valor) -> {
			if (!chave.startsWith("#")) return;
			Identifier id = Identifier.tryParse(chave.substring(1));
			if (id == null) return;
			for (RegistryEntry<Item> e : Registries.ITEM.iterateEntries(TagKey.of(RegistryKeys.ITEM, id))) {
				destino.put(e.value(), valor);
			}
		});
		origem.forEach((chave, valor) -> {
			if (chave.startsWith("#")) return;
			Identifier id = Identifier.tryParse(chave);
			if (id == null) return;
			Registries.ITEM.getOrEmpty(id).ifPresent(item -> destino.put(item, valor));
		});
	}

	private static long menorValor(Ingredient ing, Map<Item, Long> v) {
		long menor = -1;
		for (ItemStack s : ing.getMatchingStacks()) {
			Long valor = v.get(s.getItem());
			if (valor == null) continue;
			// ex.: balde de leite devolve o balde, então só conta o leite
			Item sobra = s.getItem().getRecipeRemainder();
			if (sobra != null && sobra != Items.AIR) {
				Long vs = v.get(sobra);
				if (vs != null) valor = Math.max(0, valor - vs);
			}
			if (menor < 0 || valor < menor) menor = valor;
		}
		return menor;
	}

	// ------------------------------------------------------------------ valor de um item

	/** Valor-base de um item (milésimos de esmeralda). 0 = o Mercador não compra. */
	public static long valorBase(Item item) {
		if (item == Items.AIR || item == Items.EMERALD || item == Items.EMERALD_BLOCK) return 0;
		if (item instanceof SpawnEggItem || Registries.ITEM.getEntry(item).isIn(NAO_COMPRA)) return 0;
		Long v = valores.get(item);
		if (v != null) return v;
		ItemStack s = item.getDefaultStack();
		Rarity raridade = s.getRarity();
		long porRaridade = switch (raridade) {
			case COMMON -> s.getMaxCount() == 1 ? 200 : 20;
			case UNCOMMON -> 500;
			case RARE -> 2000;
			case EPIC -> 8000;
		};
		return porRaridade;
	}

	/** Valor de UM item desta pilha, considerando durabilidade, encantamentos e conteúdo. */
	public static long valorUnitario(ItemStack stack) {
		return valorUnitario(stack, 0);
	}

	private static long valorUnitario(ItemStack stack, int profundidade) {
		if (stack.isEmpty()) return 0;
		long v = valorBase(stack.getItem());
		if (v <= 0) return 0;

		if (stack.isDamageable() && stack.getMaxDamage() > 0) {
			double restante = 1.0 - (double) stack.getDamage() / stack.getMaxDamage();
			v = Math.max(1, Math.round(v * Math.max(0.1, restante)));
		}

		v += valorEncantamentos(stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT));
		v += valorEncantamentos(stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT));

		// comprado de um aldeão: no máximo metade do que foi pago
		NbtComponent dados = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (dados != null && dados.contains(CHAVE_PAGO)) {
			int pago = dados.copyNbt().getInt(CHAVE_PAGO);
			v = Math.max(1, Math.min(v, pago * UMA_ESMERALDA / 2));
		}

		ContainerComponent conteudo = stack.get(DataComponentTypes.CONTAINER);
		if (conteudo != null && profundidade < 2) {
			for (ItemStack dentro : conteudo.iterateNonEmpty()) {
				v += valorUnitario(dentro, profundidade + 1) * dentro.getCount();
			}
		}
		return v;
	}

	private static long valorEncantamentos(ItemEnchantmentsComponent encantamentos) {
		long soma = 0;
		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : encantamentos.getEnchantmentEntries()) {
			RegistryEntry<Enchantment> ench = e.getKey();
			if (ench.isIn(EnchantmentTags.CURSE)) continue;
			long add = 400L * e.getIntValue() * Math.max(1, ench.value().getAnvilCost());
			if (ench.isIn(EnchantmentTags.TREASURE)) add *= 2;
			soma += add;
		}
		return soma;
	}

	// ------------------------------------------------------------------ proposta do Mercador

	/** Multiplicador de preço do Mercador: nível, reputação do jogador e Herói da Vila. */
	public static double multiplicador(VillagerEntity mercador, PlayerEntity jogador) {
		MercadorConfig.Dados cfg = MercadorConfig.atual;
		int nivel = mercador.getVillagerData().getLevel();
		double bonus = 1.0 + cfg.bonus_por_nivel * Math.max(0, nivel - 1);
		if (cfg.bonus_por_reputacao) {
			int rep = Math.max(-100, Math.min(100, mercador.getReputation(jogador)));
			bonus *= 1.0 + rep / 1000.0;
			if (jogador.hasStatusEffect(StatusEffects.HERO_OF_THE_VILLAGE)) bonus *= 1.05;
		}
		bonus = Math.min(bonus, 1.0 + Math.max(0, cfg.bonus_maximo));
		return Math.max(0.01, cfg.multiplicador_global * bonus);
	}

	public static Cotacao cotar(List<ItemStack> pilhas, double multiplicador) {
		ItemStack referencia = ItemStack.EMPTY;
		int total = 0;
		for (ItemStack s : pilhas) {
			if (s.isEmpty()) continue;
			if (referencia.isEmpty()) {
				referencia = s;
			} else if (!ItemStack.areItemsAndComponentsEqual(referencia, s)) {
				return Cotacao.simples(Cotacao.Status.MISTURADO, 0);
			}
			total += s.getCount();
		}
		if (referencia.isEmpty()) return Cotacao.VAZIA;

		long valor = valorUnitario(referencia);
		if (valor <= 0) return Cotacao.simples(Cotacao.Status.NAO_COMPRA, total);
		valor = Math.max(1, Math.round(valor * multiplicador));
		int maxPorItem = Math.max(1, Math.min(64, MercadorConfig.atual.max_esmeraldas_por_item));
		valor = Math.min(valor, maxPorItem * UMA_ESMERALDA);

		int itensPorEsmeralda = valor < UMA_ESMERALDA ? (int) teto(UMA_ESMERALDA, valor) : 0;
		int esmPorItemX10 = valor >= UMA_ESMERALDA ? (int) (valor / 100) : 0;

		long esmeraldas = Math.min(64, total * valor / UMA_ESMERALDA);
		if (esmeraldas <= 0) {
			int faltam = (int) (teto(UMA_ESMERALDA, valor) - total);
			return new Cotacao(Cotacao.Status.INSUFICIENTE, 0, 0, total, Math.max(1, faltam), itensPorEsmeralda, esmPorItemX10);
		}
		int usados = (int) Math.min(total, teto(esmeraldas * UMA_ESMERALDA, valor));
		int faltamProxima = esmeraldas < 64 ? (int) Math.max(0, teto((esmeraldas + 1) * UMA_ESMERALDA, valor) - total) : 0;
		return new Cotacao(Cotacao.Status.OK, (int) esmeraldas, usados, total, faltamProxima, itensPorEsmeralda, esmPorItemX10);
	}

	private static long teto(long a, long b) {
		return (a + b - 1) / b;
	}
}
