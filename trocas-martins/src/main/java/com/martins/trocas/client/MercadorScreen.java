package com.martins.trocas.client;

import com.martins.trocas.mercador.Cotacao;
import com.martins.trocas.mercador.LojaMercador;
import com.martins.trocas.mercador.MercadorMenu;
import com.martins.trocas.rede.EscolherOfertaPayload;
import com.martins.trocas.rede.OfertasPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tela do Mercador com as mesmas texturas e medidas da tela de trocas dos aldeões.
 * Uma lista só: primeiro o que você tem e ele compra, depois o que ele vende;
 * pesquisando, aparecem todos os itens do jogo que ele compra.
 */
public class MercadorScreen extends AbstractContainerScreen<MercadorMenu> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/villager.png");
	private static final Identifier OUT_OF_STOCK = Identifier.withDefaultNamespace("container/villager/out_of_stock");
	private static final Identifier XP_FUNDO = Identifier.withDefaultNamespace("container/villager/experience_bar_background");
	private static final Identifier XP_ATUAL = Identifier.withDefaultNamespace("container/villager/experience_bar_current");
	private static final Identifier XP_RESULTADO = Identifier.withDefaultNamespace("container/villager/experience_bar_result");
	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/villager/scroller");
	private static final Identifier SCROLLER_DESATIVADO = Identifier.withDefaultNamespace("container/villager/scroller_disabled");
	private static final Identifier SETA_ESGOTADA = Identifier.withDefaultNamespace("container/villager/trade_arrow_out_of_stock");
	private static final Identifier SETA = Identifier.withDefaultNamespace("container/villager/trade_arrow");
	private static final Component DICA_PESQUISA = Component.translatable("gui.recipebook.search_hint").withStyle(EditBox.SEARCH_HINT_STYLE);
	private static final int COR = 0xFF404040;
	private static final int VISIVEIS = 7;

	/** Uma linha da lista: o que se paga (a, b) e o que se recebe (c). */
	private record Linha(ItemStack a, ItemStack b, ItemStack c, boolean disponivel, int faltam,
	                     boolean comprar, int indiceLoja, ItemStack chave, boolean esgotado) {
	}

	private final BotaoOferta[] botoes = new BotaoOferta[VISIVEIS];
	private final Map<Item, String> nomes = new HashMap<>();
	private EditBox pesquisa;
	private int inicio;
	private boolean rolando;
	private List<Linha> linhas = List.of();
	private String ultimaPesquisa = "";

	public MercadorScreen(MercadorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 276, 166);
		this.inventoryLabelX = 107;
	}

	@Override
	protected void init() {
		super.init();
		int i = this.leftPos;
		int j = this.topPos;
		// igual à pesquisa do livro de receitas
		pesquisa = new EditBox(font, i + 6, j + 2, 87, 9 + 5, Component.translatable("itemGroup.search"));
		pesquisa.setMaxLength(50);
		pesquisa.setVisible(true);
		pesquisa.setTextColor(-1);
		pesquisa.setHint(DICA_PESQUISA);
		pesquisa.setResponder(t -> montarLinhas());
		addRenderableWidget(pesquisa);

		int k = j + 16 + 2;
		for (int l = 0; l < VISIVEIS; l++) {
			botoes[l] = addRenderableWidget(new BotaoOferta(i + 5, k, l, b -> clicar(((BotaoOferta) b).indice + inicio)));
			k += 20;
		}
		montarLinhas();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		montarLinhas();
	}

	private void clicar(int indice) {
		if (indice < 0 || indice >= linhas.size()) return;
		Linha l = linhas.get(indice);
		if (l.comprar()) ClientPlayNetworking.send(new EscolherOfertaPayload(true, l.indiceLoja(), ItemStack.EMPTY));
		else ClientPlayNetworking.send(new EscolherOfertaPayload(false, -1, l.chave()));
	}

	// ------------------------------------------------------------------ montar a lista

	private static String normalizar(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	private boolean combina(ItemStack s, String busca) {
		if (busca.isEmpty()) return true;
		String nome = s.getComponentsPatch().isEmpty()
			? nomes.computeIfAbsent(s.getItem(), it -> normalizar(s.getHoverName().getString()))
			: normalizar(s.getHoverName().getString());
		return nome.contains(busca) || BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().contains(busca);
	}

	private int quantosTem(Item item) {
		int n = 0;
		for (ItemStack s : minecraft.player.getInventory().getNonEquipmentItems()) if (s.is(item)) n += s.getCount();
		for (int i = 0; i < 2; i++) {
			ItemStack s = menu.getSlot(i).getItem();
			if (s.is(item)) n += s.getCount();
		}
		return n;
	}

	private void montarLinhas() {
		if (pesquisa == null) return;
		String busca = normalizar(pesquisa.getValue().trim());
		List<Linha> nova = new ArrayList<>();
		// 1) o que você tem e ele compra
		Set<Item> jaListados = new HashSet<>();
		for (OfertasPayload.Oferta o : menu.getOfertas()) {
			jaListados.add(o.item().getItem());
			if (combina(o.item(), busca)) nova.add(linhaVenda(o, o.temJogador()));
		}
		// 2) o que ele vende
		List<LojaMercador.Oferta> loja = menu.getLoja();
		for (int i = 0; i < loja.size(); i++) {
			LojaMercador.Oferta o = loja.get(i);
			if (!combina(o.resultado(), busca)) continue;
			int esmFalta = Math.max(0, o.esmeraldas() - (quantosTem(Items.EMERALD) + 9 * quantosTem(Items.EMERALD_BLOCK)));
			boolean temA = quantosTem(o.custoA().getItem()) >= o.custoA().getCount();
			boolean temB = o.custoB().isEmpty() || quantosTem(o.custoB().getItem()) >= o.custoB().getCount();
			boolean esgotado = menu.restantes(o.chave(), o.maxUsos()) <= 0;
			nova.add(new Linha(o.custoA(), o.custoB(), o.resultado(), temA && temB && !esgotado, esmFalta, true, i,
				ItemStack.EMPTY, esgotado));
		}
		// 3) pesquisando: todo o resto que ele compra
		if (!busca.isEmpty()) {
			for (OfertasPayload.Oferta o : menu.getCatalogoCompra()) {
				if (jaListados.contains(o.item().getItem()) || !combina(o.item(), busca)) continue;
				nova.add(linhaVenda(o, 0));
			}
		}
		linhas = nova;
		if (!busca.equals(ultimaPesquisa)) {
			ultimaPesquisa = busca;
			inicio = 0;
		}
		inicio = Mth.clamp(inicio, 0, Math.max(0, linhas.size() - VISIVEIS));
	}

	private Linha linhaVenda(OfertasPayload.Oferta o, int tem) {
		ItemStack a = o.item().copyWithCount(Math.min(64, o.quantidade()));
		ItemStack b = o.quantidade() > 64 ? o.item().copyWithCount(o.quantidade() - 64) : ItemStack.EMPTY;
		boolean esgotado = menu.restantes(MercadorMenu.chaveVenda(o.item()), LojaMercador.USOS_VENDA) <= 0;
		return new Linha(a, b, new ItemStack(Items.EMERALD, o.esmeraldas()), tem >= o.quantidade() && !esgotado,
			Math.max(0, o.quantidade() - tem), false, -1, o.item(), esgotado);
	}

	// ------------------------------------------------------------------ desenho

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		int nivel = menu.getProps().get(MercadorMenu.P_NIVEL);
		Component titulo = nivel > 0 && nivel <= 5
			? Component.translatable("merchant.title", this.title, Component.translatable("merchant.level." + nivel))
			: this.title;
		g.text(font, titulo, 49 + imageWidth / 2 - font.width(titulo) / 2, 6, COR, false);
		g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COR, false);

		// aviso no estilo do "Caro demais!" da bigorna
		Component aviso = aviso(menu.cotacaoCliente());
		if (aviso != null) {
			int largura = font.width(aviso);
			int x = imageWidth - 8 - largura - 2;
			g.fill(x - 2, 60, imageWidth - 8, 72, 0x4F000000);
			g.text(font, aviso, x, 62, 0xFFFF6060, true);
		}
	}

	private static Component aviso(Cotacao c) {
		return switch (c.status()) {
			case INSUFICIENTE -> Component.translatable("gui.trocasmartins.faltam_itens", c.faltam());
			case COMPRA_FALTA -> Component.translatable("gui.trocasmartins.faltam_esmeraldas", c.faltam());
			case MISTURADO -> Component.translatable("gui.trocasmartins.misturado");
			case NAO_COMPRA -> Component.translatable("gui.trocasmartins.nao_compra");
			case ESGOTADO -> Component.translatable("gui.trocasmartins.esgotado");
			default -> null;
		};
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 512, 256);
		if (menu.cotacaoCliente().impossivel()) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, OUT_OF_STOCK, leftPos + 83 + 99, topPos + 35, 28, 21);
		}
	}

	private void desenharXp(GuiGraphicsExtractor g, int x, int y) {
		int nivel = menu.getProps().get(MercadorMenu.P_NIVEL);
		int xp = menu.getProps().get(MercadorMenu.P_XP);
		if (nivel < 1 || nivel >= 5) return;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_FUNDO, x + 136, y + 16, 102, 5);
		int min = VillagerData.getMinXpPerLevel(nivel);
		if (xp >= min && VillagerData.canLevelUp(nivel)) {
			float f = 102.0F / (VillagerData.getMaxXpPerLevel(nivel) - min);
			int atual = Math.min(Mth.floor(f * (xp - min)), 102);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_ATUAL, 102, 5, 0, 0, x + 136, y + 16, atual, 5);
			Cotacao c = menu.cotacaoCliente();
			if (c.ok()) {
				int ganho = Math.min(Mth.floor(Math.max(1, c.esmeraldas()) * f), 102 - atual);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_RESULTADO, 102, 5, atual, 0, x + 136 + atual, y + 16, ganho, 5);
			}
		}
	}

	private void desenharBarra(GuiGraphicsExtractor g, int x, int y) {
		int n = linhas.size() + 1 - VISIVEIS;
		if (n > 1) {
			int j = 139 - (27 + (n - 1) * 139 / n);
			int passo = 1 + j / n + 139 / n;
			int m = Math.min(113, inicio * passo);
			if (inicio == n - 1) m = 113;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER, x + 94, y + 18 + m, 6, 27);
		} else {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_DESATIVADO, x + 94, y + 18, 6, 27);
		}
	}

	private void item(GuiGraphicsExtractor g, ItemStack s, int x, int y) {
		if (s.isEmpty()) return;
		g.fakeItem(s, x, y);
		g.itemDecorations(font, s, x, y);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractContents(g, mouseX, mouseY, a);
		int i = leftPos;
		int j = topPos;
		desenharXp(g, i, j);
		desenharBarra(g, i, j);
		int k = j + 16 + 1;
		for (int idx = inicio; idx < Math.min(linhas.size(), inicio + VISIVEIS); idx++) {
			Linha l = linhas.get(idx);
			int y = k + 2;
			item(g, l.a(), i + 5 + 5, y);
			item(g, l.b(), i + 5 + 35, y);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, l.disponivel() ? SETA : SETA_ESGOTADA, i + 5 + 35 + 20, y + 3, 10, 9);
			item(g, l.c(), i + 5 + 68, y);
			k += 20;
		}
		if (linhas.isEmpty()) {
			Component vazio = Component.translatable("gui.trocasmartins.nada_encontrado");
			g.text(font, vazio, i + 49 - font.width(vazio) / 2, j + 80, COR, false);
		}

		for (BotaoOferta b : botoes) {
			b.visible = b.indice + inicio < linhas.size();
			if (b.isHoveredOrFocused()) b.dica(g, mouseX, mouseY);
		}

		Cotacao c = menu.cotacaoCliente();
		Component aviso = aviso(c);
		if (aviso != null && isHovering(186, 35, 22, 21, mouseX, mouseY)) {
			g.setTooltipForNextFrame(font, c.status() == Cotacao.Status.ESGOTADO
				? List.of(aviso, Component.translatable("merchant.deprecated").copy().withStyle(ChatFormatting.GRAY))
				: List.of(Component.translatable("gui.trocasmartins.impossivel"), aviso), java.util.Optional.empty(), mouseX, mouseY);
		}
	}

	// ------------------------------------------------------------------ teclado e mouse

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (pesquisa.isFocused()) {
			if (event.isEscape()) {
				pesquisa.setFocused(false);
				setFocused(null);
				return true;
			}
			pesquisa.keyPressed(event);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int n = linhas.size();
		if (n > VISIVEIS) inicio = Mth.clamp((int) (inicio - verticalAmount), 0, n - VISIVEIS);
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		int n = linhas.size();
		if (rolando) {
			int topo = topPos + 18;
			int fundo = topo + 139;
			float f = ((float) event.y() - topo - 13.5F) / (fundo - topo - 27.0F);
			f = f * (n - VISIVEIS) + 0.5F;
			inicio = Mth.clamp((int) f, 0, Math.max(0, n - VISIVEIS));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		rolando = linhas.size() > VISIVEIS && event.x() > leftPos + 94 && event.x() < leftPos + 100
			&& event.y() > topPos + 18 && event.y() <= topPos + 158;
		if (!pesquisa.isMouseOver(event.x(), event.y())) pesquisa.setFocused(false);
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		rolando = false;
		return super.mouseReleased(event);
	}

	private class BotaoOferta extends Button.Plain {
		final int indice;

		BotaoOferta(int x, int y, int indice, OnPress acao) {
			super(x, y, 88, 20, CommonComponents.EMPTY, acao, DEFAULT_NARRATION);
			this.indice = indice;
			this.visible = false;
		}

		void dica(GuiGraphicsExtractor g, int x, int y) {
			if (!isHovered || indice + inicio >= linhas.size()) return;
			Linha l = linhas.get(indice + inicio);
			if (x < getX() + 20) {
				g.setTooltipForNextFrame(font, l.a(), x, y);
			} else if (x < getX() + 50 && x > getX() + 30 && !l.b().isEmpty()) {
				g.setTooltipForNextFrame(font, l.b(), x, y);
			} else if (x >= getX() + 50 && x <= getX() + 65 && l.esgotado()) {
				g.setTooltipForNextFrame(font, List.of(Component.translatable("gui.trocasmartins.esgotado"),
					Component.translatable("merchant.deprecated").copy().withStyle(ChatFormatting.GRAY)), java.util.Optional.empty(), x, y);
			} else if (x >= getX() + 50 && x <= getX() + 65 && !l.disponivel()) {
				g.setTooltipForNextFrame(Component.translatable(l.comprar()
					? "gui.trocasmartins.faltam_esmeraldas" : "gui.trocasmartins.faltam_itens", l.faltam()), x, y);
			} else if (x > getX() + 65) {
				g.setTooltipForNextFrame(font, l.c(), x, y);
			}
		}
	}
}
