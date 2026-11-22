package com.martins.trocas.mercador;

import com.martins.trocas.ModRegistro;
import com.martins.trocas.rede.OfertasPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import java.util.List;

/**
 * Tela do Mercador, no mesmo formato da tela de trocas dos aldeões:
 * 2 espaços de pagamento (o que você vende), 1 de resultado (as esmeraldas)
 * e a lista de trocas à esquerda, montada com o que você tem no inventário.
 */
public class MercadorScreenHandler extends ScreenHandler {
	public static final int PAGAMENTO_1 = 0, PAGAMENTO_2 = 1, SAIDA = 2;
	public static final int INV_INICIO = 3;
	public static final int INV_FIM = 39;
	public static final int MAX_OFERTAS = 64;

	public static final int P_STATUS = 0, P_ESMERALDAS = 1, P_USADOS = 2, P_TOTAL = 3, P_FALTAM = 4,
		P_ITENS_POR_ESM = 5, P_ESM_POR_ITEM = 6, P_NIVEL = 7, P_XP = 8, P_TAMANHO = 9;

	private final SimpleInventory pagamento;
	private final SimpleInventory saida = new SimpleInventory(1);
	private final PropertyDelegate props;
	private final PlayerEntity jogador;
	@Nullable
	private final VillagerEntity mercador; // só no servidor
	private Cotacao cotacao = Cotacao.VAZIA;
	private boolean ocupado;

	private List<OfertasPayload.Oferta> ofertas = List.of();
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
		if (mercador != null) atualizarNivel();
	}

	// ------------------------------------------------------------------ dados para a tela

	public PropertyDelegate getProps() {
		return props;
	}

	public Cotacao cotacaoCliente() {
		return new Cotacao(Cotacao.Status.of(props.get(P_STATUS)), props.get(P_ESMERALDAS), props.get(P_USADOS),
			props.get(P_TOTAL), props.get(P_FALTAM), props.get(P_ITENS_POR_ESM), props.get(P_ESM_POR_ITEM));
	}

	public List<OfertasPayload.Oferta> getOfertas() {
		return ofertas;
	}

	/** Chamado no cliente quando chega a lista do servidor. */
	public void setOfertas(List<OfertasPayload.Oferta> ofertas) {
		this.ofertas = ofertas;
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

	private void recalcular() {
		if (mercador == null) return;
		cotacao = PrecoMercador.cotar(List.of(pagamento.getStack(0), pagamento.getStack(1)), multiplicador());
		props.set(P_STATUS, cotacao.status().ordinal());
		props.set(P_ESMERALDAS, cotacao.esmeraldas());
		props.set(P_USADOS, cotacao.usados());
		props.set(P_TOTAL, cotacao.total());
		props.set(P_FALTAM, cotacao.faltam());
		props.set(P_ITENS_POR_ESM, cotacao.itensPorEsmeralda());
		props.set(P_ESM_POR_ITEM, cotacao.esmeraldasPorItemX10());
		atualizarNivel();
		saida.setStack(0, cotacao.ok() ? new ItemStack(Items.EMERALD, cotacao.esmeraldas()) : ItemStack.EMPTY);
	}

	/** Monta a lista de trocas: um item de cada tipo que o jogador tem e o Mercador compra. */
	private List<OfertasPayload.Oferta> calcularOfertas() {
		double mult = multiplicador();
		List<ItemStack> tipos = new ArrayList<>();
		List<Integer> totais = new ArrayList<>();
		List<ItemStack> fontes = new ArrayList<>();
		fontes.add(pagamento.getStack(0));
		fontes.add(pagamento.getStack(1));
		PlayerInventory inv = jogador.getInventory();
		for (int i = 0; i < inv.main.size(); i++) fontes.add(inv.main.get(i));

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

		// ordem fixa (pela ordem dos itens no jogo), para a lista não pular quando você escolhe uma troca
		Integer[] ordem = new Integer[tipos.size()];
		for (int i = 0; i < ordem.length; i++) ordem[i] = i;
		java.util.Arrays.sort(ordem, java.util.Comparator.comparingInt(i -> net.minecraft.registry.Registries.ITEM.getRawId(tipos.get(i).getItem())));

		List<OfertasPayload.Oferta> lista = new ArrayList<>();
		for (int i : ordem) {
			long v = PrecoMercador.valorEfetivo(tipos.get(i), mult);
			if (v <= 0) continue;
			int qtd = v < PrecoMercador.UMA_ESMERALDA ? (int) PrecoMercador.teto(PrecoMercador.UMA_ESMERALDA, v) : 1;
			int esm = v < PrecoMercador.UMA_ESMERALDA ? 1 : (int) Math.min(64, v / PrecoMercador.UMA_ESMERALDA);
			lista.add(new OfertasPayload.Oferta(tipos.get(i).copyWithCount(1), qtd, esm, totais.get(i)));
		}
		return lista;
	}

	@Override
	public void sendContentUpdates() {
		super.sendContentUpdates();
		if (mercador == null || !(jogador instanceof ServerPlayerEntity sp)) return;
		atualizarNivel();
		List<OfertasPayload.Oferta> novas = calcularOfertas();
		int assinatura = 1;
		for (OfertasPayload.Oferta o : novas) {
			assinatura = 31 * assinatura + ItemStack.hashCode(o.item());
			assinatura = 31 * assinatura + o.quantidade() * 7 + o.esmeraldas() * 131 + o.temJogador() * 1031;
		}
		if (assinatura != assinaturaOfertas) {
			assinaturaOfertas = assinatura;
			ofertas = novas;
			try {
				if (ServerPlayNetworking.canSend(sp, OfertasPayload.ID)) {
					ServerPlayNetworking.send(sp, new OfertasPayload(syncId, novas));
				}
			} catch (Exception ignorado) {
				// jogador falso (testes) não tem conexão
			}
		}
	}

	// ------------------------------------------------------------------ escolher uma troca da lista

	/** Igual a clicar numa troca do aldeão: devolve o que está no pagamento e coloca o item escolhido. */
	public void escolherOferta(int indice) {
		if (mercador == null || indice < 0 || indice >= ofertas.size()) return;
		ItemStack alvo = ofertas.get(indice).item();
		ocupado = true;
		try {
			for (int i = 0; i < 2; i++) {
				ItemStack s = pagamento.getStack(i);
				if (!s.isEmpty() && !ItemStack.areItemsAndComponentsEqual(s, alvo)) {
					pagamento.setStack(i, ItemStack.EMPTY);
					jogador.getInventory().offerOrDrop(s);
				}
			}
			preencherPagamento(alvo);
		} finally {
			ocupado = false;
		}
		recalcular();
		sendContentUpdates();
	}

	/** Puxa do inventário o item indicado para os 2 espaços de pagamento. */
	private void preencherPagamento(ItemStack alvo) {
		PlayerInventory inv = jogador.getInventory();
		for (int p = 0; p < 2; p++) {
			ItemStack atual = pagamento.getStack(p);
			if (!atual.isEmpty() && !ItemStack.areItemsAndComponentsEqual(atual, alvo)) continue;
			for (int i = 0; i < inv.main.size(); i++) {
				atual = pagamento.getStack(p);
				int espaco = alvo.getMaxCount() - atual.getCount();
				if (espaco <= 0) break;
				ItemStack s = inv.main.get(i);
				if (s.isEmpty() || !ItemStack.areItemsAndComponentsEqual(s, alvo)) continue;
				int mover = Math.min(espaco, s.getCount());
				if (atual.isEmpty()) {
					pagamento.setStack(p, s.copyWithCount(mover));
				} else {
					atual.increment(mover);
				}
				s.decrement(mover);
			}
		}
		pagamento.markDirty();
	}

	// ------------------------------------------------------------------ fechar a venda

	private void concluirVenda(PlayerEntity player, ItemStack esmeraldas) {
		if (mercador == null) return;
		Cotacao c = cotacao;
		if (!c.ok()) return;
		ItemStack vendido = ItemStack.EMPTY;
		ocupado = true;
		try {
			int restante = c.usados();
			for (int i = 0; i < 2 && restante > 0; i++) {
				ItemStack s = pagamento.getStack(i);
				if (s.isEmpty()) continue;
				if (vendido.isEmpty()) vendido = s.copyWithCount(1);
				int tirar = Math.min(restante, s.getCount());
				pagamento.removeStack(i, tirar);
				restante -= tirar;
			}
		} finally {
			ocupado = false;
		}

		TradeOffer troca = new TradeOffer(new TradedItem(vendido.getItem(), Math.min(64, c.usados())),
			esmeraldas.copy(), Integer.MAX_VALUE, Math.max(1, c.esmeraldas()), 0f);
		mercador.trade(troca);
		mercador.onSellingItem(esmeraldas);
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
			insertItem(pilha, INV_INICIO, INV_FIM, true);
			slot.setStack(ItemStack.EMPTY);
			slot.onTakeItem(player, resultado);
			// shift-clique continua vendendo enquanto houver desse item no inventário
			if (mercador != null && !vendido.isEmpty()) {
				ocupado = true;
				try {
					preencherPagamento(vendido);
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
			if (mercador == null) return Cotacao.Status.of(props.get(P_STATUS)) == Cotacao.Status.OK;
			return cotacao.ok();
		}

		@Override
		public ItemStack takeStack(int amount) {
			return super.takeStack(getStack().getCount());
		}

		@Override
		public void onTakeItem(PlayerEntity player, ItemStack stack) {
			if (!player.getWorld().isClient && !stack.isEmpty()) {
				concluirVenda(player, stack);
			}
			super.onTakeItem(player, stack);
		}
	}
}
