package com.martins.trocas.mercador;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.rede.CatalogoPayload;
import com.martins.trocas.rede.OfertasPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Tela do Mercador, no formato da tela de trocas dos aldeões: 2 espaços de pagamento e 1 de resultado.
 * - Vender: coloque qualquer item no pagamento e ele mostra quantas esmeraldas paga (ou quantos faltam).
 * - Comprar: escolha uma troca da loja; o pagamento são esmeraldas e o resultado é o item.
 */
public class MercadorScreenHandler extends ScreenHandler {
	public static final int PAGAMENTO_1 = 0, PAGAMENTO_2 = 1, SAIDA = 2;
	public static final int INV_INICIO = 3;
	public static final int INV_FIM = 39;
	public static final int MAX_OFERTAS = 64;

	public static final int P_STATUS = 0, P_ESMERALDAS = 1, P_USADOS = 2, P_TOTAL = 3, P_FALTAM = 4,
		P_ITENS_POR_ESM = 5, P_ESM_POR_ITEM = 6, P_NIVEL = 7, P_XP = 8, P_COMPRA = 9, P_TAMANHO = 10;

	private final SimpleInventory pagamento;
	private final SimpleInventory saida = new SimpleInventory(1);
	private final PropertyDelegate props;
	private final PlayerEntity jogador;
	@Nullable
	private final VillagerEntity mercador; // só no servidor
	private Cotacao cotacao = Cotacao.VAZIA;
	private boolean ocupado;
	private boolean catalogoEnviado;
	/** Troca da loja escolhida (comprar), ou -1 para o modo vender. */
	private int compra = -1;

	private List<OfertasPayload.Oferta> ofertas = List.of();
	private List<OfertasPayload.Oferta> catalogoCompra = List.of();
	private List<LojaMercador.Oferta> loja = List.of();
	private int assinaturaOfertas = Integer.MIN_VALUE;

	/** Cliente. */
	public MercadorScreenHandler(int syncId, PlayerInventory inv, Integer idDoAldeao) {
		this(syncId, inv, (VillagerEntity) null);
	}

	/** Servidor. */
	public MercadorScreenHandler(int syncId, PlayerInventory inv, @Nullable VillagerEntity mercador) {
		super(ModRegistro.MERCADOR_SCREEN, syncId);
		this.jogador = inv.player;
		this.mercador = mercador;
		this.props = new ArrayPropertyDelegate(P_TAMANHO);
		this.pagamento = new SimpleInventory(2) {
			@Override
			public void markDirty() {
				super.markDirty();
				MercadorScreenHandler.this.onContentChanged(this);
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
		addProperties(props);
		if (mercador != null) {
			loja = LojaMercador.ofertas();
			atualizarNivel();
		}
	}

	// ------------------------------------------------------------------ dados para a tela

	public PropertyDelegate getProps() {
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

	public void setOfertas(List<OfertasPayload.Oferta> ofertas) {
		this.ofertas = ofertas;
	}

	public void setCatalogo(List<OfertasPayload.Oferta> compra, List<LojaMercador.Oferta> venda) {
		this.catalogoCompra = compra;
		this.loja = venda;
	}

	// ------------------------------------------------------------------ cálculo

	@Override
	public void onContentChanged(Inventory inventory) {
		if (inventory == pagamento && !ocupado) recalcular();
		super.onContentChanged(inventory);
	}

	private double multiplicador() {
		return mercador == null ? 1.0 : PrecoMercador.multiplicador(mercador, jogador);
	}

	private void atualizarNivel() {
		props.set(P_NIVEL, mercador.getVillagerData().getLevel());
		props.set(P_XP, mercador.getExperience());
	}

	private static boolean ehEsmeralda(ItemStack s) {
		return s.isOf(Items.EMERALD) || s.isOf(Items.EMERALD_BLOCK);
	}

	private void recalcular() {
		if (mercador == null) return;
		ItemStack a = pagamento.getStack(0), b = pagamento.getStack(1);
		// colocou algo que não é esmeralda: volta a ser venda
		if (compra >= 0 && ((!a.isEmpty() && !ehEsmeralda(a)) || (!b.isEmpty() && !ehEsmeralda(b)))) compra = -1;

		if (compra >= 0 && compra < loja.size()) {
			LojaMercador.Oferta o = loja.get(compra);
			int falta = falta(o.custoA(), a) + falta(o.custoB(), b);
			cotacao = falta == 0
				? new Cotacao(Cotacao.Status.COMPRA_OK, 0, 0, 0, 0, 0, 0)
				: new Cotacao(Cotacao.Status.COMPRA_FALTA, 0, 0, 0, falta, 0, 0);
			saida.setStack(0, falta == 0 ? o.resultado().copy() : ItemStack.EMPTY);
		} else {
			compra = -1;
			cotacao = PrecoMercador.cotar(List.of(a, b), multiplicador());
			saida.setStack(0, cotacao.ok() ? new ItemStack(Items.EMERALD, cotacao.esmeraldas()) : ItemStack.EMPTY);
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
		int tem = noEspaco.isOf(custo.getItem()) ? noEspaco.getCount() : 0;
		int peso = custo.isOf(Items.EMERALD_BLOCK) ? 9 : 1;
		return Math.max(0, custo.getCount() - tem) * peso;
	}

	/** Um item de cada tipo que o jogador tem no inventário e o Mercador compra. */
	private List<OfertasPayload.Oferta> calcularOfertas() {
		double mult = multiplicador();
		List<ItemStack> tipos = new ArrayList<>();
		List<Integer> totais = new ArrayList<>();
		List<ItemStack> fontes = new ArrayList<>();
		fontes.add(pagamento.getStack(0));
		fontes.add(pagamento.getStack(1));
		PlayerInventory inv = jogador.getInventory();
		fontes.addAll(inv.main);

		for (ItemStack s : fontes) {
			if (s.isEmpty()) continue;
			int achado = -1;
			for (int i = 0; i < tipos.size(); i++) {
				if (ItemStack.areItemsAndComponentsEqual(tipos.get(i), s)) {
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
		lista.sort(Comparator.comparingInt(o -> Registries.ITEM.getRawId(o.item().getItem())));
		return lista;
	}

	@Nullable
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
		for (Item item : Registries.ITEM) {
			if (item == Items.AIR) continue;
			OfertasPayload.Oferta o = oferta(item.getDefaultStack(), mult, 0);
			if (o != null) lista.add(o);
		}
		return lista;
	}

	@Override
	public void sendContentUpdates() {
		super.sendContentUpdates();
		if (mercador == null || !(jogador instanceof ServerPlayerEntity sp)) return;
		atualizarNivel();
		if (!catalogoEnviado) {
			catalogoEnviado = true;
			catalogoCompra = calcularCatalogo();
			enviar(sp, new CatalogoPayload(syncId, catalogoCompra, loja));
		}
		List<OfertasPayload.Oferta> novas = calcularOfertas();
		int assinatura = 1;
		for (OfertasPayload.Oferta o : novas) {
			assinatura = 31 * assinatura + ItemStack.hashCode(o.item());
			assinatura = 31 * assinatura + o.quantidade() * 7 + o.esmeraldas() * 131 + o.temJogador() * 1031;
		}
		if (assinatura != assinaturaOfertas) {
			assinaturaOfertas = assinatura;
			ofertas = novas;
			enviar(sp, new OfertasPayload(syncId, novas));
		}
	}

	private static void enviar(ServerPlayerEntity sp, net.minecraft.network.packet.CustomPayload payload) {
		try {
			if (ServerPlayNetworking.canSend(sp, payload.getId())) ServerPlayNetworking.send(sp, payload);
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
		sendContentUpdates();
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
		sendContentUpdates();
	}

	private void devolverPagamento(@Nullable ItemStack manter) {
		for (int i = 0; i < 2; i++) {
			ItemStack s = pagamento.getStack(i);
			if (s.isEmpty() || (manter != null && ItemStack.areItemsAndComponentsEqual(s, manter))) continue;
			pagamento.setStack(i, ItemStack.EMPTY);
			jogador.getInventory().offerOrDrop(s);
		}
	}

	private void preencherCompra(LojaMercador.Oferta o) {
		if (!o.custoA().isEmpty()) puxar(0, o.custoA());
		if (!o.custoB().isEmpty()) puxar(1, o.custoB());
		pagamento.markDirty();
	}

	/** Puxa do inventário o item indicado para os espaços de pagamento (até encher). */
	private void preencher(ItemStack alvo, ItemStack ignorado) {
		puxar(0, alvo);
		puxar(1, alvo);
		pagamento.markDirty();
	}

	private void puxar(int espaco, ItemStack alvo) {
		PlayerInventory inv = jogador.getInventory();
		ItemStack atual = pagamento.getStack(espaco);
		if (!atual.isEmpty() && !ItemStack.areItemsAndComponentsEqual(atual, alvo)) return;
		for (int i = 0; i < inv.main.size(); i++) {
			atual = pagamento.getStack(espaco);
			int lugar = alvo.getMaxCount() - atual.getCount();
			if (lugar <= 0) break;
			ItemStack s = inv.main.get(i);
			if (s.isEmpty() || !ItemStack.areItemsAndComponentsEqual(s, alvo)) continue;
			int mover = Math.min(lugar, s.getCount());
			if (atual.isEmpty()) pagamento.setStack(espaco, s.copyWithCount(mover));
			else atual.increment(mover);
			s.decrement(mover);
		}
	}

	// ------------------------------------------------------------------ fechar a troca

	private void concluir(PlayerEntity player, ItemStack recebido) {
		if (mercador == null) return;
		TradeOffer troca;
		ocupado = true;
		try {
			if (cotacao.status() == Cotacao.Status.COMPRA_OK && compra >= 0) {
				LojaMercador.Oferta o = loja.get(compra);
				if (!o.custoA().isEmpty()) pagamento.removeStack(0, o.custoA().getCount());
				if (!o.custoB().isEmpty()) pagamento.removeStack(1, o.custoB().getCount());
				troca = new TradeOffer(new TradedItem(o.custoA().getItem(), o.custoA().getCount()),
					o.custoB().isEmpty() ? Optional.empty() : Optional.of(new TradedItem(o.custoB().getItem(), o.custoB().getCount())),
					recebido.copy(), Integer.MAX_VALUE, Math.max(1, Math.min(30, o.esmeraldas())), 0f);
			} else if (cotacao.ok()) {
				ItemStack vendido = ItemStack.EMPTY;
				int restante = cotacao.usados();
				for (int i = 0; i < 2 && restante > 0; i++) {
					ItemStack s = pagamento.getStack(i);
					if (s.isEmpty()) continue;
					if (vendido.isEmpty()) vendido = s.copyWithCount(1);
					int tirar = Math.min(restante, s.getCount());
					pagamento.removeStack(i, tirar);
					restante -= tirar;
				}
				troca = new TradeOffer(new TradedItem(vendido.getItem(), Math.min(64, cotacao.usados())),
					recebido.copy(), Integer.MAX_VALUE, Math.max(1, cotacao.esmeraldas()), 0f);
			} else {
				return;
			}
		} finally {
			ocupado = false;
		}
		mercador.trade(troca);
		mercador.onSellingItem(recebido);
		player.incrementStat(Stats.TRADED_WITH_VILLAGER);
		recalcular();
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int indice) {
		Slot slot = slots.get(indice);
		if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;
		ItemStack pilha = slot.getStack();
		ItemStack resultado = pilha.copy();

		if (indice == SAIDA) {
			if (!slot.canTakeItems(player) || espacoNoInventario(pilha) < pilha.getCount()) return ItemStack.EMPTY;
			ItemStack vendido = !pagamento.getStack(0).isEmpty() ? pagamento.getStack(0).copyWithCount(1) : pagamento.getStack(1).copyWithCount(1);
			int compraAntes = compra;
			insertItem(pilha, INV_INICIO, INV_FIM, true);
			slot.setStack(ItemStack.EMPTY);
			slot.onTakeItem(player, resultado);
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
			if (!insertItem(pilha, INV_INICIO, INV_FIM, true)) return ItemStack.EMPTY;
		} else {
			if (!insertItem(pilha, PAGAMENTO_1, SAIDA, false)) {
				if (indice < INV_FIM - 9) {
					if (!insertItem(pilha, INV_FIM - 9, INV_FIM, false)) return ItemStack.EMPTY;
				} else if (!insertItem(pilha, INV_INICIO, INV_FIM - 9, false)) {
					return ItemStack.EMPTY;
				}
			}
		}

		if (pilha.isEmpty()) slot.setStack(ItemStack.EMPTY);
		else slot.markDirty();
		if (pilha.getCount() == resultado.getCount()) return ItemStack.EMPTY;
		slot.onTakeItem(player, pilha);
		return resultado;
	}

	private int espacoNoInventario(ItemStack pilha) {
		int espaco = 0;
		for (int i = INV_INICIO; i < INV_FIM; i++) {
			ItemStack s = slots.get(i).getStack();
			if (s.isEmpty()) espaco += pilha.getMaxCount();
			else if (ItemStack.areItemsAndComponentsEqual(s, pilha)) espaco += Math.max(0, s.getMaxCount() - s.getCount());
		}
		return espaco;
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return slot.inventory != saida && super.canInsertIntoSlot(stack, slot);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		if (mercador == null) return true;
		return mercador.isAlive()
			&& mercador.getCustomer() == player
			&& mercador.getVillagerData().getProfession() == ModRegistro.MERCADOR
			&& player.squaredDistanceTo(mercador) <= 64.0;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		saida.setStack(0, ItemStack.EMPTY);
		if (mercador != null) {
			ocupado = true;
			dropInventory(player, pagamento);
			if (mercador.getCustomer() == player) mercador.setCustomer(null);
		}
	}

	private class SlotSaida extends Slot {
		SlotSaida(Inventory inv, int index, int x, int y) {
			super(inv, index, x, y);
		}

		@Override
		public boolean canInsert(ItemStack stack) {
			return false;
		}

		@Override
		public boolean canTakeItems(PlayerEntity player) {
			Cotacao.Status s = mercador == null ? Cotacao.Status.of(props.get(P_STATUS)) : cotacao.status();
			return mercador == null ? s == Cotacao.Status.OK || s == Cotacao.Status.COMPRA_OK
				: cotacao.ok() || s == Cotacao.Status.COMPRA_OK;
		}

		@Override
		public ItemStack takeStack(int amount) {
			return super.takeStack(getStack().getCount());
		}

		@Override
		public void onTakeItem(PlayerEntity player, ItemStack stack) {
			if (!player.getWorld().isClient && !stack.isEmpty()) concluir(player, stack);
			super.onTakeItem(player, stack);
		}
	}
}
