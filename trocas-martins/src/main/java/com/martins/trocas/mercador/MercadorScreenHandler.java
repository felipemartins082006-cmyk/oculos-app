package com.martins.trocas.mercador;

import com.martins.trocas.ModRegistro;
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
import net.minecraft.stat.Stats;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Balcão do Mercador: 9 espaços para o jogador colocar o que quer vender,
 * e 1 espaço de saída com as esmeraldas oferecidas.
 */
public class MercadorScreenHandler extends ScreenHandler {
	public static final int SAIDA = 0;
	public static final int ENTRADA_INICIO = 1;
	public static final int ENTRADA_FIM = 10; // exclusivo
	public static final int INV_INICIO = 10;
	public static final int INV_FIM = 46;

	// índices do PropertyDelegate
	public static final int P_STATUS = 0, P_ESMERALDAS = 1, P_USADOS = 2, P_TOTAL = 3, P_FALTAM = 4,
		P_ITENS_POR_ESM = 5, P_ESM_POR_ITEM = 6, P_NIVEL = 7, P_TAMANHO = 8;

	private final SimpleInventory entrada;
	private final SimpleInventory saida = new SimpleInventory(1);
	private final PropertyDelegate props;
	private final PlayerEntity jogador;
	@Nullable
	private final VillagerEntity mercador; // só existe no servidor
	private Cotacao cotacao = Cotacao.VAZIA;
	private boolean ocupado;

	/** Construtor do cliente. */
	public MercadorScreenHandler(int syncId, PlayerInventory inv, Integer idDoAldeao) {
		this(syncId, inv, null, new ArrayPropertyDelegate(P_TAMANHO));
	}

	/** Construtor do servidor. */
	public MercadorScreenHandler(int syncId, PlayerInventory inv, VillagerEntity mercador) {
		this(syncId, inv, mercador, new ArrayPropertyDelegate(P_TAMANHO));
	}

	private MercadorScreenHandler(int syncId, PlayerInventory inv, @Nullable VillagerEntity mercador, PropertyDelegate props) {
		super(ModRegistro.MERCADOR_SCREEN, syncId);
		this.jogador = inv.player;
		this.mercador = mercador;
		this.props = props;
		this.entrada = new SimpleInventory(9) {
			@Override
			public void markDirty() {
				super.markDirty();
				MercadorScreenHandler.this.onContentChanged(this);
			}
		};

		addSlot(new SlotSaida(saida, 0, 124, 35));
		for (int linha = 0; linha < 3; linha++) {
			for (int col = 0; col < 3; col++) {
				addSlot(new Slot(entrada, col + linha * 3, 30 + col * 18, 17 + linha * 18));
			}
		}
		for (int linha = 0; linha < 3; linha++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inv, col + linha * 9 + 9, 8 + col * 18, 110 + linha * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inv, col, 8 + col * 18, 168));
		}
		addProperties(props);
		if (mercador != null) {
			props.set(P_NIVEL, mercador.getVillagerData().getLevel());
		}
	}

	public PropertyDelegate getProps() {
		return props;
	}

	public Cotacao cotacaoCliente() {
		return new Cotacao(Cotacao.Status.of(props.get(P_STATUS)), props.get(P_ESMERALDAS), props.get(P_USADOS),
			props.get(P_TOTAL), props.get(P_FALTAM), props.get(P_ITENS_POR_ESM), props.get(P_ESM_POR_ITEM));
	}

	@Override
	public void onContentChanged(Inventory inventory) {
		if (inventory == entrada && !ocupado) {
			recalcular();
		}
		super.onContentChanged(inventory);
	}

	private void recalcular() {
		if (mercador == null) return; // o cliente só exibe o que o servidor manda
		List<ItemStack> pilhas = new ArrayList<>(9);
		for (int i = 0; i < entrada.size(); i++) pilhas.add(entrada.getStack(i));
		cotacao = PrecoMercador.cotar(pilhas, PrecoMercador.multiplicador(mercador, jogador));
		props.set(P_STATUS, cotacao.status().ordinal());
		props.set(P_ESMERALDAS, cotacao.esmeraldas());
		props.set(P_USADOS, cotacao.usados());
		props.set(P_TOTAL, cotacao.total());
		props.set(P_FALTAM, cotacao.faltam());
		props.set(P_ITENS_POR_ESM, cotacao.itensPorEsmeralda());
		props.set(P_ESM_POR_ITEM, cotacao.esmeraldasPorItemX10());
		props.set(P_NIVEL, mercador.getVillagerData().getLevel());
		saida.setStack(0, cotacao.ok() ? new ItemStack(Items.EMERALD, cotacao.esmeraldas()) : ItemStack.EMPTY);
	}

	/** Chamado quando o jogador pega as esmeraldas: cobra os itens e registra a troca no aldeão. */
	private void concluirVenda(PlayerEntity player, ItemStack esmeraldas) {
		if (mercador == null) return;
		Cotacao c = cotacao;
		if (!c.ok()) return;
		ItemStack vendido = ItemStack.EMPTY;
		ocupado = true;
		try {
			int restante = c.usados();
			for (int i = 0; i < entrada.size() && restante > 0; i++) {
				ItemStack s = entrada.getStack(i);
				if (s.isEmpty()) continue;
				if (vendido.isEmpty()) vendido = s.copyWithCount(1);
				int tirar = Math.min(restante, s.getCount());
				entrada.removeStack(i, tirar);
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
		sendContentUpdates();
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int indice) {
		ItemStack resultado = ItemStack.EMPTY;
		Slot slot = slots.get(indice);
		if (slot == null || !slot.hasStack()) return resultado;
		ItemStack pilha = slot.getStack();
		resultado = pilha.copy();

		if (indice == SAIDA) {
			if (!slot.canTakeItems(player) || espacoNoInventario(pilha) < pilha.getCount()) return ItemStack.EMPTY;
			insertItem(pilha, INV_INICIO, INV_FIM, true);
			slot.setStack(ItemStack.EMPTY);
			slot.onTakeItem(player, resultado);
			return resultado;
		} else if (indice < ENTRADA_FIM) {
			if (!insertItem(pilha, INV_INICIO, INV_FIM, true)) return ItemStack.EMPTY;
		} else {
			if (!insertItem(pilha, ENTRADA_INICIO, ENTRADA_FIM, false)) return ItemStack.EMPTY;
		}

		if (pilha.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
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
			dropInventory(player, entrada);
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
			// sempre pega todas as esmeraldas da proposta (não dá para pegar só metade)
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
