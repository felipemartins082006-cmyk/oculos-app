package com.martins.trocas.mercador;

import com.martins.trocas.TrocasMartins;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
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
	/** 8 milésimos = 125 itens por esmeralda: cabe nos 2 espaços de pagamento. */
	public static final long VALOR_MINIMO = 8;
	/** Cada passo de receita (para cima ou para baixo) vale 95%: um pouco de prejuízo, nunca lucro. */
	public static final double FATOR_RECEITA = 0.95;
	/** Guardado em itens únicos comprados de aldeões: esmeraldas pagas. */
	public static final String CHAVE_PAGO = "trocasmartins_pago";

	private static volatile Map<Item, Double> valores = new HashMap<>();
	private static volatile Map<Item, Double> custos = new HashMap<>();
	/** O Mercador nunca paga mais que 90% do que custa comprar o item dos aldeões. */
	public static final double TRAVA_LUCRO = 0.9;

	private PrecoMercador() {
	}

	// ------------------------------------------------------------------ cálculo da tabela

	/**
	 * Regras:
	 * 1. Item que algum aldeão compra: o Mercador paga exatamente o mesmo (ex.: 32 gravetos = 1 esmeralda).
	 * 2. Item feito numa receita: 95% da soma dos ingredientes ("para cima").
	 * 3. Item que vira outro numa receita só com ele (tronco -> tábuas -> gravetos): 95% do que
	 *    ele rende ("para baixo"). Assim vender o tronco ao Mercador dá um pouco menos do que
	 *    transformar em gravetos e vender ao flecheiro, mas nunca um prejuízo absurdo.
	 * 4. Sem nada disso: tabela de matérias-primas e, por último, a raridade.
	 * 5. Trava contra lucro: o Mercador nunca paga mais que 90% do que custa conseguir o item
	 *    com os aldeões (comprando o item ou os ingredientes dele).
	 */
	public static void recalcular(MinecraftServer server) {
		long inicio = System.currentTimeMillis();
		TrocasAldeoes.ler(server);

		Map<Item, Double> ancora = new HashMap<>();
		TrocasAldeoes.COMPRAM.forEach((item, t) -> ancora.put(item, t.porItem() * UMA_ESMERALDA));
		ancora.put(Items.EMERALD, (double) UMA_ESMERALDA);
		ancora.put(Items.EMERALD_BLOCK, 9.0 * UMA_ESMERALDA);
		Map<String, Long> doConfig = new HashMap<>();
		MercadorConfig.atual.precos.forEach((k, v) -> {
			if (v != null) doConfig.put(k, Math.max(0, Math.round(v * UMA_ESMERALDA)));
		});
		Map<Item, Long> config = new HashMap<>();
		aplicar(config, doConfig);
		config.forEach((item, v) -> ancora.put(item, (double) v));

		Map<Item, Long> tabelaL = new HashMap<>();
		aplicar(tabelaL, TabelaPrecos.padrao());
		Map<Item, Double> tabela = new HashMap<>();
		tabelaL.forEach((item, v) -> {
			if (!ancora.containsKey(item)) tabela.put(item, (double) v);
		});

		List<Receita> receitas = lerReceitas(server);
		Map<Item, Double> custo = custoComAldeoes(receitas);

		// fase 1, "para baixo": a partir do que os aldeões compram (tronco -> tábuas -> gravetos)
		Map<Item, Double> desce = new HashMap<>();
		for (int passo = 0; passo < 40; passo++) {
			Map<Item, Double> atual = new HashMap<>(tabela);
			atual.putAll(desce);
			atual.putAll(ancora);
			Map<Item, Double> d = new HashMap<>();
			for (Receita rc : receitas) {
				// só resultados empilháveis: um barco ou uma ferramenta não define o preço da madeira
				Double vSaida = atual.get(rc.saida);
				if (vSaida == null || rc.unicos == null || rc.saida.getMaxCount() <= 1) continue;
				double cand = vSaida * rc.qtdSaida / rc.ingredientes.size() * FATOR_RECEITA;
				for (Item x : rc.unicos) {
					if (!ancora.containsKey(x)) d.merge(x, cand, Math::max);
				}
			}
			d.replaceAll((item, valor) -> travar(item, valor, custo));
			boolean igual = d.size() == desce.size();
			if (igual) {
				for (Map.Entry<Item, Double> e : d.entrySet()) {
					Double antes = desce.get(e.getKey());
					if (antes == null || Math.abs(antes - e.getValue()) > 0.001) {
						igual = false;
						break;
					}
				}
			}
			desce = d;
			if (igual) break;
		}
		Map<Item, Double> fixos = new HashMap<>(tabela);
		fixos.putAll(desce);
		fixos.putAll(ancora);

		// fase 2, "para cima": item fabricado = a receita mais barata (soma dos ingredientes)
		Map<Item, Double> sobe = new HashMap<>();
		Map<Item, Double> consulta = new HashMap<>(fixos);
		for (int passo = 0; passo < 64; passo++) {
			boolean mudou = false;
			for (Receita rc : receitas) {
				if (ancora.containsKey(rc.saida)) continue;
				double soma = 0;
				boolean ok = true;
				for (List<Item> opcoes : rc.ingredientes) {
					double menor = menor(opcoes, consulta);
					if (menor < 0) {
						ok = false;
						break;
					}
					soma += menor;
				}
				if (!ok) continue;
				double cand = soma / rc.qtdSaida;
				Double antes = sobe.get(rc.saida);
				if (antes == null || cand < antes - 0.001) {
					sobe.put(rc.saida, cand);
					Double fixo = desce.get(rc.saida);
					consulta.put(rc.saida, fixo != null ? Math.max(fixo, cand) : cand);
					mudou = true;
				}
			}
			if (!mudou) break;
		}

		Map<Item, Double> v = new HashMap<>(tabela);
		sobe.forEach((item, valor) -> v.put(item, valor * FATOR_RECEITA));
		desce.forEach((item, valor) -> v.merge(item, valor, Math::max));
		v.putAll(ancora);
		v.replaceAll((item, valor) -> travar(item, valor, custo));

		// itens de netherite (mesa de ferraria): versão de diamante + lingote de netherite
		Double lingote = v.get(Items.NETHERITE_INGOT);
		if (lingote != null) {
			for (Item item : Registries.ITEM) {
				Identifier id = Registries.ITEM.getId(item);
				if (v.containsKey(item) || !id.getPath().startsWith("netherite_")) continue;
				Identifier diamante = Identifier.of(id.getNamespace(), "diamond_" + id.getPath().substring("netherite_".length()));
				Double vd = Registries.ITEM.getOrEmpty(diamante).map(v::get).orElse(null);
				if (vd != null) v.put(item, vd + lingote);
			}
		}

		Map<Item, Double> finais = new HashMap<>();
		v.forEach((item, valor) -> finais.put(item, Math.max(0, valor)));
		custos = custo;
		valores = finais;
		LojaMercador.montar();
		TrocasMartins.LOGGER.info("Mercador: {} preços calculados em {} ms", finais.size(), System.currentTimeMillis() - inicio);
	}

	private static double travar(Item item, double valor, Map<Item, Double> custo) {
		Double c = custo.get(item);
		return c != null ? Math.min(valor, c * TRAVA_LUCRO) : valor;
	}

	/** Receita simplificada: ingredientes (cada um com suas opções), resultado e quantidade. */
	private record Receita(Item saida, int qtdSaida, List<List<Item>> ingredientes, boolean forno, List<Item> unicos) {
	}

	private static List<Receita> lerReceitas(MinecraftServer server) {
		List<Receita> lista = new ArrayList<>();
		for (RecipeEntry<?> entrada : server.getRecipeManager().values()) {
			Recipe<?> receita = entrada.value();
			ItemStack saida;
			List<Ingredient> ings;
			try {
				saida = receita.getResult(server.getRegistryManager());
				ings = receita.getIngredients();
			} catch (Exception e) {
				continue;
			}
			if (saida == null || saida.isEmpty()) continue;
			List<List<Item>> ingredientes = new ArrayList<>();
			for (Ingredient ing : ings) {
				if (ing.isEmpty()) continue;
				List<Item> opcoes = new ArrayList<>();
				for (ItemStack s : ing.getMatchingStacks()) opcoes.add(s.getItem());
				if (!opcoes.isEmpty()) ingredientes.add(opcoes);
			}
			if (ingredientes.isEmpty()) continue;
			// itens que servem em TODOS os ingredientes (ex.: 2 tábuas -> 4 gravetos)
			List<Item> unicos = new ArrayList<>(ingredientes.get(0));
			for (List<Item> opcoes : ingredientes) unicos.retainAll(opcoes);
			unicos.remove(saida.getItem());
			lista.add(new Receita(saida.getItem(), saida.getCount(), ingredientes,
				receita instanceof AbstractCookingRecipe, unicos.isEmpty() ? null : unicos));
		}
		return lista;
	}

	/** Quanto custa, no mínimo, conseguir cada item comprando dos aldeões (o item ou os ingredientes). */
	private static Map<Item, Double> custoComAldeoes(List<Receita> receitas) {
		Map<Item, Double> c = new HashMap<>();
		TrocasAldeoes.VENDEM.forEach((item, t) -> c.put(item, t.porItem() * UMA_ESMERALDA));
		for (int passo = 0; passo < 40; passo++) {
			boolean mudou = false;
			for (Receita rc : receitas) {
				double soma = 0;
				boolean ok = true;
				for (List<Item> opcoes : rc.ingredientes) {
					double menor = menor(opcoes, c);
					if (menor < 0) {
						ok = false;
						break;
					}
					soma += menor;
				}
				if (!ok) continue;
				double cand = soma / rc.qtdSaida;
				Double atual = c.get(rc.saida);
				if (atual == null || cand < atual - 0.01) {
					c.put(rc.saida, cand);
					mudou = true;
				}
			}
			if (!mudou) break;
		}
		return c;
	}

	private static double menor(List<Item> opcoes, Map<Item, Double> v) {
		double menor = -1;
		for (Item item : opcoes) {
			Double valor = v.get(item);
			if (valor == null) continue;
			// ex.: balde de leite devolve o balde, então só conta o leite
			Item sobra = item.getRecipeRemainder();
			if (sobra != null && sobra != Items.AIR) {
				Double vs = v.get(sobra);
				if (vs != null) valor = Math.max(0, valor - vs);
			}
			if (menor < 0 || valor < menor) menor = valor;
		}
		return menor;
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

	/** Custo para conseguir o item com aldeões (milésimos), ou null se nenhum aldeão vende. */
	public static Double custoComAldeoes(Item item) {
		return custos.get(item);
	}

	// ------------------------------------------------------------------ valor de um item

	/** Valor-base de um item (milésimos de esmeralda). 0 = o Mercador não compra. */
	public static double valorBase(Item item) {
		if (item == Items.AIR || item == Items.EMERALD || item == Items.EMERALD_BLOCK) return 0;
		if (item instanceof SpawnEggItem || Registries.ITEM.getEntry(item).isIn(NAO_COMPRA)) return 0;
		Double v = valores.get(item);
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
	public static double valorUnitario(ItemStack stack) {
		return valorUnitario(stack, 0);
	}

	private static double valorUnitario(ItemStack stack, int profundidade) {
		if (stack.isEmpty()) return 0;
		double v = valorBase(stack.getItem());
		if (v <= 0) return 0;

		if (stack.isDamageable() && stack.getMaxDamage() > 0) {
			double restante = 1.0 - (double) stack.getDamage() / stack.getMaxDamage();
			v = Math.max(1, v * Math.max(0.1, restante));
		}

		v += valorEncantamentos(stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT));
		v += valorEncantamentos(stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT));

		// comprado de um aldeão: no máximo metade do que foi pago
		NbtComponent dados = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (dados != null && dados.contains(CHAVE_PAGO)) {
			int pago = dados.copyNbt().getInt(CHAVE_PAGO);
			v = Math.max(1, Math.min(v, pago * UMA_ESMERALDA / 2.0));
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

	/** Multiplicador de preço do Mercador (só o ajuste global da configuração). */
	public static double multiplicador(VillagerEntity mercador, PlayerEntity jogador) {
		return Math.max(0.01, MercadorConfig.atual.multiplicador_global);
	}

	/**
	 * Quanto o Mercador paga por UM item desta pilha (milésimos de esmeralda), já com o multiplicador.
	 * Nunca menos que {@link #VALOR_MINIMO}, para que os 2 espaços de pagamento (128 itens)
	 * sempre consigam fechar pelo menos 1 esmeralda. 0 = não compra.
	 */
	public static double valorEfetivo(ItemStack stack, double multiplicador) {
		double valor = valorUnitario(stack);
		if (valor <= 0) return 0;
		valor = valor * multiplicador;
		if (valor < VALOR_MINIMO) {
			// arredonda para 125 itens por esmeralda, a não ser que isso dê lucro com os aldeões
			Double c = custos.get(stack.getItem());
			if (c != null && c * TRAVA_LUCRO < VALOR_MINIMO) return 0;
			valor = VALOR_MINIMO;
		}
		int maxPorItem = Math.max(1, Math.min(64, MercadorConfig.atual.max_esmeraldas_por_item));
		return Math.min(valor, maxPorItem * UMA_ESMERALDA);
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

		double valor = valorEfetivo(referencia, multiplicador);
		if (valor <= 0) return Cotacao.simples(Cotacao.Status.NAO_COMPRA, total);

		int itensPorEsmeralda = valor < UMA_ESMERALDA ? teto(UMA_ESMERALDA, valor) : 0;
		int esmPorItemX10 = valor >= UMA_ESMERALDA ? (int) (valor / 100) : 0;

		int esmeraldas = (int) Math.min(64, Math.floor(total * valor / UMA_ESMERALDA + 1e-9));
		if (esmeraldas <= 0) {
			int faltam = teto(UMA_ESMERALDA, valor) - total;
			return new Cotacao(Cotacao.Status.INSUFICIENTE, 0, 0, total, Math.max(1, faltam), itensPorEsmeralda, esmPorItemX10);
		}
		int usados = Math.min(total, teto(esmeraldas * (double) UMA_ESMERALDA, valor));
		int faltamProxima = esmeraldas < 64 ? Math.max(0, teto((esmeraldas + 1) * (double) UMA_ESMERALDA, valor) - total) : 0;
		return new Cotacao(Cotacao.Status.OK, esmeraldas, usados, total, faltamProxima, itensPorEsmeralda, esmPorItemX10);
	}

	/** Quantos itens de valor {@code b} são precisos para somar {@code a}. */
	public static int teto(double a, double b) {
		return (int) Math.ceil(a / b - 1e-9);
	}
}
