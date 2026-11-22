package com.martins.trocas.mercador;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.rede.CatalogoPayload;
import com.martins.trocas.rede.OfertasPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Tela do Mercador, no formato da tela de trocas dos aldeões: 2 espaços de pagamento e 1 de resultado.
 * - Vender: coloque qualquer item no pagamento e ele mostra quantas esmeraldas paga (ou quantos faltam).
 * - Comprar: escolha uma troca da loja; o pagamento são esmeraldas e o resultado é o item.
 */
public class MercadorMenu extends AbstractContainerMenu {
	public static final int PAGAMENTO_1 = 0, PAGAMENTO_2 = 1, SAIDA = 2;
	public static final int INV_INICIO = 3;
	public static final int INV_FIM = 39;
	public static final int MAX_OFERTAS = 64;

	public static final int P_STATUS = 0, P_ESMERALDAS = 1, P_USADOS = 2, P_TOTAL = 3, P_FALTAM = 4,
		P_ITENS_POR_ESM = 5, P_ESM_POR_ITEM = 6, P_NIVEL = 7, P_XP = 8, P_COMPRA = 9, P_TAMANHO = 10;

	private final SimpleContainer pagamento;
	private final SimpleContainer saida = new SimpleContainer(1);
	private final ContainerData props;
	private final Player jogador;
	@Nullable
	private final Villager mercador; // só no servidor
	private Cotacao cotacao = Cotacao.VAZIA;
	private boolean ocupado;
	private boolean catalogoEnviado;
	/** Troca da loja escolhida (comprar), ou -1 para o modo vender. */
	private int compra = -1;

	private List<OfertasPayload.Oferta> ofertas = List.of();
	private List<OfertasPayload.Oferta> catalogoCompra = List.of();
	private List<LojaMercador.Oferta> loja = List.of();
	private int assinaturaOfertas = Integer.MIN_VALUE;
	/** Usos de cada troca (no cliente, a cópia que o servidor mandou). */
	private java.util.HashMap<String, Integer> usosCliente = new java.util.HashMap<>();

	/** Cliente. */
	public MercadorMenu(int syncId, Inventory inv, Integer idDoAldeao) {
		this(syncId, inv, (Villager) null);
	}

	/** Servidor. */
	public MercadorMenu(int syncId, Inventory inv, @Nullable Villager mercador) {
		super(ModRegistro.MERCADOR_MENU, syncId);
		this.jogador = inv.player;
		this.mercador = mercador;
		this.props = new SimpleContainerData(P_TAMANHO);
		this.pagamento = new SimpleContainer(2) {
			@Override
			public void setChanged() {
				super.setChanged();
				MercadorMenu.this.slotsChanged(this);
			}
		};

		// mesmas posições da tela de trocas do Minecraft
		addSlot(new Slot(pagamento, 0, 136, 37));
		addSlot(new Slot(pagamento, 1, 162, 37));
		addSlot(new SlotSaida(saida, 0, 220, 37));
		for (int linha = 0; linha < 3; linha++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inv, col + linha * 9 + 9, 108 + col * 18, 84 + linha * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inv, col, 108 + col * 18, 142));
		}
		addDataSlots(props);
		if (mercador != null) {
			loja = LojaMercador.ofertas();
			atualizarNivel();
		}
	}

	// ------------------------------------------------------------------ dados para a tela

	public ContainerData getProps() {
		return props;
	}

	public Cotacao cotacaoCliente() {
		return new Cotacao(Cotacao.Status.of(props.get(P_STATUS)), props.get(P_ESMERALDAS), props.get(P_USADOS),
			props.get(P_TOTAL), props.get(P_FALTAM), props.get(P_ITENS_POR_ESM), props.get(P_ESM_POR_ITEM));
	}

	/** Troca da loja escolhida (-1 = vendendo). */
	public int compraEscolhida() {
		return props.get(P_COMPRA) - 1;
	}

	/** Itens do inventário que o Mercador compra. */
	public List<OfertasPayload.Oferta> getOfertas() {
		return ofertas;
	}

	/** Tudo o que o Mercador compra (para pesquisar). */
	public List<OfertasPayload.Oferta> getCatalogoCompra() {
		return catalogoCompra;
	}

	/** O que o Mercador vende. */
	public List<LojaMercador.Oferta> getLoja() {
		return loja;
	}

	public void setOfertas(List<OfertasPayload.Oferta> ofertas, java.util.HashMap<String, Integer> usos) {
		this.ofertas = ofertas;
		this.usosCliente = usos;
	}

	// ------------------------------------------------------------------ limite de trocas

	public static String chaveVenda(ItemStack s) {
		return "v:" + BuiltInRegistries.ITEM.getKey(s.getItem());
	}

	private java.util.Map<String, Integer> usos() {
		return mercador != null ? mercador.getAttachedOrCreate(ModRegistro.USOS) : usosCliente;
	}

	/** Quantas trocas ainda dá para fazer com {@code chave} até o Mercador repor. */
	public int restantes(String chave, int max) {
		return Math.max(0, max - usos().getOrDefault(chave, 0));
	}

	private void usar(String chave, int quantas) {
		java.util.Map<String, Integer> novo = new java.util.HashMap<>(usos());
		novo.merge(chave, quantas, Integer::sum);
		mercador.setAttached(ModRegistro.USOS, novo);
	}

	public void setCatalogo(List<OfertasPayload.Oferta> compra, List<LojaMercador.Oferta> venda) {
		this.catalogoCompra = compra;
		this.loja = venda;
	}

	// ------------------------------------------------------------------ cálculo

	@Override
	public void slotsChanged(Container inventory) {
		if (inventory == pagamento && !ocupado) recalcular();
		super.slotsChanged(inventory);
	}

	private double multiplicador() {
		return mercador == null ? 1.0 : PrecoMercador.multiplicador(mercador, jogador);
	}

	private void atualizarNivel() {
		props.set(P_NIVEL, mercador.getVillagerData().level());
		props.set(P_XP, mercador.getVillagerXp());
	}

	private static boolean ehEsmeralda(ItemStack s) {
		return s.is(Items.EMERALD) || s.is(Items.EMERALD_BLOCK);
	}

	private void recalcular() {
		if (mercador == null) return;
		ItemStack a = pagamento.getItem(0), b = pagamento.getItem(1);
		// colocou algo que não é esmeralda: volta a ser venda
		if (compra >= 0 && ((!a.isEmpty() && !ehEsmeralda(a)) || (!b.isEmpty() && !ehEsmeralda(b)))) compra = -1;

		if (compra >= 0 && compra < loja.size()) {
			LojaMercador.Oferta o = loja.get(compra);
			int falta = falta(o.custoA(), a) + falta(o.custoB(), b);
			if (restantes(o.chave(), o.maxUsos()) <= 0) {
				cotacao = Cotacao.simples(Cotacao.Status.ESGOTADO, 0);
				saida.setItem(0, ItemStack.EMPTY);
			} else cotacao = falta == 0
				? new Cotacao(Cotacao.Status.COMPRA_OK, 0, 0, 0, 0, 0, 0)
				: new Cotacao(Cotacao.Status.COMPRA_FALTA, 0, 0, 0, falta, 0, 0);
			if (cotacao.status() != Cotacao.Status.ESGOTADO) saida.setItem(0, falta == 0 ? o.resultado().copy() : ItemStack.EMPTY);
		} else {
			compra = -1;
			ItemStack ref = !a.isEmpty() ? a : b;
			int limite = ref.isEmpty() ? Integer.MAX_VALUE : restantes(chaveVenda(ref), LojaMercador.USOS_VENDA);
			cotacao = PrecoMercador.cotar(List.of(a, b), multiplicador(), limite);
			saida.setItem(0, cotacao.ok() ? new ItemStack(Items.EMERALD, cotacao.esmeraldas()) : ItemStack.EMPTY);
		}
		props.set(P_STATUS, cotacao.status().ordinal());
		props.set(P_ESMERALDAS, cotacao.esmeraldas());
		props.set(P_USADOS, cotacao.usados());
		props.set(P_TOTAL, cotacao.total());
		props.set(P_FALTAM, cotacao.faltam());
		props.set(P_ITENS_POR_ESM, cotacao.itensPorEsmeralda());
		props.set(P_ESM_POR_ITEM, cotacao.esmeraldasPorItemX10());
		props.set(P_COMPRA, compra + 1);
		atualizarNivel();
	}

	/** Esmeraldas que faltam no espaço para pagar {@code custo}. */
	private static int falta(ItemStack custo, ItemStack noEspaco) {
		if (custo.isEmpty()) return 0;
		int tem = noEspaco.is(custo.getItem()) ? noEspaco.getCount() : 0;
		int peso = custo.is(Items.EMERALD_BLOCK) ? 9 : 1;
		return Math.max(0, custo.getCount() - tem) * peso;
	}

	/** Um item de cada tipo que o jogador tem no inventário e o Mercador compra. */
	private List<OfertasPayload.Oferta> calcularOfertas() {
		double mult = multiplicador();
		List<ItemStack> tipos = new ArrayList<>();
		List<Integer> totais = new ArrayList<>();
		List<ItemStack> fontes = new ArrayList<>();
		fontes.add(pagamento.getItem(0));
		fontes.add(pagamento.getItem(1));
		Inventory inv = jogador.getInventory();
		fontes.addAll(inv.getNonEquipmentItems());

		for (ItemStack s : fontes) {
			if (s.isEmpty()) continue;
			int achado = -1;
			for (int i = 0; i < tipos.size(); i++) {
				if (ItemStack.isSameItemSameComponents(tipos.get(i), s)) {
					achado = i;
					break;
				}
			}
			if (achado >= 0) {
				totais.set(achado, totais.get(achado) + s.getCount());
			} else if (tipos.size() < MAX_OFERTAS) {
				tipos.add(s);
				totais.add(s.getCount());
			}
		}

		List<OfertasPayload.Oferta> lista = new ArrayList<>();
		for (int i = 0; i < tipos.size(); i++) {
			OfertasPayload.Oferta o = oferta(tipos.get(i), mult, totais.get(i));
			if (o != null) lista.add(o);
		}
		lista.sort(Comparator.comparingInt(o -> BuiltInRegistries.ITEM.getId(o.item().getItem())));
		return lista;
	}

	private static OfertasPayload.Oferta oferta(ItemStack item, double mult, int tem) {
		double v = PrecoMercador.valorEfetivo(item, mult);
		if (v <= 0) return null;
		int qtd = v < PrecoMercador.UMA_ESMERALDA ? PrecoMercador.teto(PrecoMercador.UMA_ESMERALDA, v) : 1;
		int esm = v < PrecoMercador.UMA_ESMERALDA ? 1 : (int) Math.min(64, v / PrecoMercador.UMA_ESMERALDA);
		return new OfertasPayload.Oferta(item.copyWithCount(1), qtd, esm, tem);
	}

	/** Todos os itens do jogo que o Mercador compra, para a pesquisa. */
	private List<OfertasPayload.Oferta> calcularCatalogo() {
		double mult = multiplicador();
		List<OfertasPayload.Oferta> lista = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) continue;
			OfertasPayload.Oferta o = oferta(item.getDefaultInstance(), mult, 0);
			if (o != null) lista.add(o);
		}
		return lista;
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (mercador == null || !(jogador instanceof ServerPlayer sp)) return;
		atualizarNivel();
		if (!catalogoEnviado) {
			catalogoEnviado = true;
			catalogoCompra = calcularCatalogo();
			enviar(sp, new CatalogoPayload(containerId, catalogoCompra, loja));
		}
		List<OfertasPayload.Oferta> novas = calcularOfertas();
		int assinatura = usos().hashCode();
		for (OfertasPayload.Oferta o : novas) {
			assinatura = 31 * assinatura + ItemStack.hashItemAndComponents(o.item());
			assinatura = 31 * assinatura + o.quantidade() * 7 + o.esmeraldas() * 131 + o.temJogador() * 1031;
		}
		if (assinatura != assinaturaOfertas) {
			assinaturaOfertas = assinatura;
			ofertas = novas;
			enviar(sp, new OfertasPayload(containerId, novas, new java.util.HashMap<>(usos())));
		}
	}

	private static void enviar(ServerPlayer sp, CustomPacketPayload payload) {
		try {
			if (ServerPlayNetworking.canSend(sp, payload.type())) ServerPlayNetworking.send(sp, payload);
		} catch (Exception ignorado) {
			// jogador falso (testes) não tem conexão
		}
	}

	// ------------------------------------------------------------------ escolher uma troca da lista

	/** Vender: devolve o que está no pagamento e coloca o item escolhido (do inventário). */
	public void escolherVenda(ItemStack alvo) {
		if (mercador == null || alvo.isEmpty()) return;
		ocupado = true;
		try {
			compra = -1;
			devolverPagamento(alvo);
			preencher(alvo, alvo);
		} finally {
			ocupado = false;
		}
		recalcular();
		broadcastChanges();
	}

	/** Comprar: devolve o pagamento e coloca as esmeraldas da troca escolhida, como nas trocas normais. */
	public void escolherCompra(int indice) {
		if (mercador == null || indice < 0 || indice >= loja.size()) return;
		LojaMercador.Oferta o = loja.get(indice);
		ocupado = true;
		try {
			compra = indice;
			devolverPagamento(null);
			preencherCompra(o);
		} finally {
			ocupado = false;
		}
		recalcular();
		broadcastChanges();
	}

	private void devolverPagamento(@Nullable ItemStack manter) {
		for (int i = 0; i < 2; i++) {
			ItemStack s = pagamento.getItem(i);
			if (s.isEmpty() || (manter != null && ItemStack.isSameItemSameComponents(s, manter))) continue;
			pagamento.setItem(i, ItemStack.EMPTY);
			jogador.getInventory().placeItemBackInInventory(s);
		}
	}

	private void preencherCompra(LojaMercador.Oferta o) {
		if (!o.custoA().isEmpty()) puxar(0, o.custoA());
		if (!o.custoB().isEmpty()) puxar(1, o.custoB());
		pagamento.setChanged();
	}

	/** Puxa do inventário o item indicado para os espaços de pagamento (até encher). */
	private void preencher(ItemStack alvo, ItemStack ignorado) {
		puxar(0, alvo);
		puxar(1, alvo);
		pagamento.setChanged();
	}

	private void puxar(int espaco, ItemStack alvo) {
		Inventory inv = jogador.getInventory();
		ItemStack atual = pagamento.getItem(espaco);
		if (!atual.isEmpty() && !ItemStack.isSameItemSameComponents(atual, alvo)) return;
		for (int i = 0; i < inv.getNonEquipmentItems().size(); i++) {
			atual = pagamento.getItem(espaco);
			int lugar = alvo.getMaxStackSize() - atual.getCount();
			if (lugar <= 0) break;
			ItemStack s = inv.getNonEquipmentItems().get(i);
			if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, alvo)) continue;
			int mover = Math.min(lugar, s.getCount());
			if (atual.isEmpty()) pagamento.setItem(espaco, s.copyWithCount(mover));
			else atual.grow(mover);
			s.shrink(mover);
		}
	}

	// ------------------------------------------------------------------ fechar a troca

	private void concluir(Player player, ItemStack recebido) {
		if (mercador == null) return;
		MerchantOffer troca;
		ocupado = true;
		try {
			if (cotacao.status() == Cotacao.Status.COMPRA_OK && compra >= 0) {
				LojaMercador.Oferta o = loja.get(compra);
				if (!o.custoA().isEmpty()) pagamento.removeItem(0, o.custoA().getCount());
				if (!o.custoB().isEmpty()) pagamento.removeItem(1, o.custoB().getCount());
				troca = new MerchantOffer(new ItemCost(o.custoA().getItem(), o.custoA().getCount()),
					o.custoB().isEmpty() ? Optional.empty() : Optional.of(new ItemCost(o.custoB().getItem(), o.custoB().getCount())),
					recebido.copy(), Integer.MAX_VALUE, Math.max(1, Math.min(30, o.esmeraldas())), 0f);
				usar(o.chave(), 1);
			} else if (cotacao.ok()) {
				ItemStack vendido = ItemStack.EMPTY;
				int restante = cotacao.usados();
				for (int i = 0; i < 2 && restante > 0; i++) {
					ItemStack s = pagamento.getItem(i);
					if (s.isEmpty()) continue;
					if (vendido.isEmpty()) vendido = s.copyWithCount(1);
					int tirar = Math.min(restante, s.getCount());
					pagamento.removeItem(i, tirar);
					restante -= tirar;
				}
				usar(chaveVenda(vendido), cotacao.itensPorEsmeralda() > 0 ? cotacao.esmeraldas() : cotacao.usados());
				troca = new MerchantOffer(new ItemCost(vendido.getItem(), Math.min(64, cotacao.usados())),
					recebido.copy(), Integer.MAX_VALUE, Math.max(1, cotacao.esmeraldas()), 0f);
			} else {
				return;
			}
		} finally {
			ocupado = false;
		}
		mercador.notifyTrade(troca);
		mercador.notifyTradeUpdated(recebido);
		player.awardStat(Stats.TRADED_WITH_VILLAGER);
		recalcular();
	}

	@Override
	public ItemStack quickMoveStack(Player player, int indice) {
		Slot slot = slots.get(indice);
		if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
		ItemStack pilha = slot.getItem();
		ItemStack resultado = pilha.copy();

		if (indice == SAIDA) {
			if (!slot.mayPickup(player) || espacoNoInventario(pilha) < pilha.getCount()) return ItemStack.EMPTY;
			ItemStack vendido = !pagamento.getItem(0).isEmpty() ? pagamento.getItem(0).copyWithCount(1) : pagamento.getItem(1).copyWithCount(1);
			int compraAntes = compra;
			moveItemStackTo(pilha, INV_INICIO, INV_FIM, true);
			slot.set(ItemStack.EMPTY);
			slot.onTake(player, resultado);
			// shift-clique repete a troca enquanto houver itens/esmeraldas no inventário
			if (mercador != null) {
				ocupado = true;
				try {
					if (compraAntes >= 0) preencherCompra(loja.get(compraAntes));
					else if (!vendido.isEmpty()) preencher(vendido, vendido);
				} finally {
					ocupado = false;
				}
				recalcular();
			}
			return resultado;
		} else if (indice < SAIDA) {
			if (!moveItemStackTo(pilha, INV_INICIO, INV_FIM, true)) return ItemStack.EMPTY;
		} else {
			if (!moveItemStackTo(pilha, PAGAMENTO_1, SAIDA, false)) {
				if (indice < INV_FIM - 9) {
					if (!moveItemStackTo(pilha, INV_FIM - 9, INV_FIM, false)) return ItemStack.EMPTY;
				} else if (!moveItemStackTo(pilha, INV_INICIO, INV_FIM - 9, false)) {
					return ItemStack.EMPTY;
				}
			}
		}

		if (pilha.isEmpty()) slot.set(ItemStack.EMPTY);
		else slot.setChanged();
		if (pilha.getCount() == resultado.getCount()) return ItemStack.EMPTY;
		slot.onTake(player, pilha);
		return resultado;
	}

	private int espacoNoInventario(ItemStack pilha) {
		int espaco = 0;
		for (int i = INV_INICIO; i < INV_FIM; i++) {
			ItemStack s = slots.get(i).getItem();
			if (s.isEmpty()) espaco += pilha.getMaxStackSize();
			else if (ItemStack.isSameItemSameComponents(s, pilha)) espaco += Math.max(0, s.getMaxStackSize() - s.getCount());
		}
		return espaco;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != saida && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public boolean stillValid(Player player) {
		if (mercador == null) return true;
		return mercador.isAlive()
			&& mercador.getTradingPlayer() == player
			&& ModRegistro.ehMercador(mercador)
			&& player.distanceToSqr(mercador) <= 64.0;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		saida.setItem(0, ItemStack.EMPTY);
		if (mercador != null) {
			ocupado = true;
			clearContainer(player, pagamento);
			if (mercador.getTradingPlayer() == player) mercador.setTradingPlayer(null);
		}
	}

	private class SlotSaida extends Slot {
		SlotSaida(Container inv, int index, int x, int y) {
			super(inv, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			Cotacao.Status s = mercador == null ? Cotacao.Status.of(props.get(P_STATUS)) : cotacao.status();
			return mercador == null ? s == Cotacao.Status.OK || s == Cotacao.Status.COMPRA_OK
				: cotacao.ok() || s == Cotacao.Status.COMPRA_OK;
		}

		@Override
		public ItemStack remove(int amount) {
			return super.remove(getItem().getCount());
		}

		@Override
		public void onTake(Player player, ItemStack stack) {
			if (!player.level().isClientSide() && !stack.isEmpty()) concluir(player, stack);
			super.onTake(player, stack);
		}
	}
}
