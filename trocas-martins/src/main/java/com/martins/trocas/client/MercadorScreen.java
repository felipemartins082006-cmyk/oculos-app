package com.martins.trocas.client;

import com.martins.trocas.mercador.Cotacao;
import com.martins.trocas.mercador.LojaMercador;
import com.martins.trocas.mercador.MercadorScreenHandler;
import com.martins.trocas.rede.EscolherOfertaPayload;
import com.martins.trocas.rede.OfertasPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.VillagerData;
import org.lwjgl.glfw.GLFW;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tela do Mercador feita com as mesmas texturas e medidas da tela de trocas dos aldeões.
 * Duas abas: "Vender" (tudo o que ele compra, com pesquisa; os seus itens aparecem primeiro)
 * e "Comprar" (o que ele vende).
 */
public class MercadorScreen extends HandledScreen<MercadorScreenHandler> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/villager.png");
	private static final Identifier OUT_OF_STOCK = Identifier.ofVanilla("container/villager/out_of_stock");
	private static final Identifier XP_FUNDO = Identifier.ofVanilla("container/villager/experience_bar_background");
	private static final Identifier XP_ATUAL = Identifier.ofVanilla("container/villager/experience_bar_current");
	private static final Identifier XP_RESULTADO = Identifier.ofVanilla("container/villager/experience_bar_result");
	private static final Identifier SCROLLER = Identifier.ofVanilla("container/villager/scroller");
	private static final Identifier SCROLLER_DESATIVADO = Identifier.ofVanilla("container/villager/scroller_disabled");
	private static final Identifier SETA_ESGOTADA = Identifier.ofVanilla("container/villager/trade_arrow_out_of_stock");
	private static final Identifier SETA = Identifier.ofVanilla("container/villager/trade_arrow");
	private static final int VISIVEIS = 7;

	/** Uma linha da lista: o que se paga (a, b) e o que se recebe (c). */
	private record Linha(ItemStack a, ItemStack b, ItemStack c, boolean disponivel, int faltam,
	                     boolean comprar, int indiceLoja, ItemStack chave) {
	}

	private final BotaoOferta[] botoes = new BotaoOferta[VISIVEIS];
	private ButtonWidget abaVender;
	private ButtonWidget abaComprar;
	private TextFieldWidget pesquisa;
	private boolean modoComprar;
	private int inicio;
	private boolean rolando;
	private List<Linha> linhas = List.of();
	private String ultimaPesquisa = "";

	public MercadorScreen(MercadorScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 276;
		this.playerInventoryTitleX = 107;
	}

	@Override
	protected void init() {
		super.init();
		int i = this.x;
		int j = this.y;

		abaVender = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.trocasmartins.vender"), b -> trocarModo(false))
			.dimensions(i + 4, j - 21, 49, 20).build());
		abaComprar = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.trocasmartins.comprar"), b -> trocarModo(true))
			.dimensions(i + 54, j - 21, 49, 20).build());

		pesquisa = new TextFieldWidget(textRenderer, i + 6, j + 4, 87, 12, Text.translatable("gui.trocasmartins.pesquisar"));
		pesquisa.setMaxLength(50);
		pesquisa.setPlaceholder(Text.translatable("gui.trocasmartins.pesquisar").formatted(Formatting.DARK_GRAY));
		pesquisa.setChangedListener(t -> montarLinhas());
		addDrawableChild(pesquisa);

		int k = j + 16 + 2;
		for (int l = 0; l < VISIVEIS; l++) {
			botoes[l] = addDrawableChild(new BotaoOferta(i + 5, k, l, b -> clicar(((BotaoOferta) b).indice + inicio)));
			k += 20;
		}
		atualizarAbas();
		montarLinhas();
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		montarLinhas();
	}

	private void trocarModo(boolean comprar) {
		modoComprar = comprar;
		inicio = 0;
		atualizarAbas();
		montarLinhas();
	}

	private void atualizarAbas() {
		abaVender.active = modoComprar;
		abaComprar.active = !modoComprar;
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

	private final java.util.Map<Item, String> nomes = new java.util.HashMap<>();

	private boolean combina(ItemStack s, String busca) {
		if (busca.isEmpty()) return true;
		String nome = s.getComponentChanges().isEmpty()
			? nomes.computeIfAbsent(s.getItem(), it -> normalizar(s.getName().getString()))
			: normalizar(s.getName().getString());
		return nome.contains(busca) || Registries.ITEM.getId(s.getItem()).getPath().contains(busca);
	}

	private int quantosTem(Item item) {
		int n = 0;
		for (ItemStack s : client.player.getInventory().main) if (s.isOf(item)) n += s.getCount();
		for (int i = 0; i < 2; i++) {
			ItemStack s = handler.getSlot(i).getStack();
			if (s.isOf(item)) n += s.getCount();
		}
		return n;
	}

	private void montarLinhas() {
		String busca = normalizar(pesquisa.getText().trim());
		List<Linha> nova = new ArrayList<>();
		if (modoComprar) {
			List<LojaMercador.Oferta> loja = handler.getLoja();
			for (int i = 0; i < loja.size(); i++) {
				LojaMercador.Oferta o = loja.get(i);
				if (!combina(o.resultado(), busca)) continue;
				int falta = Math.max(0, o.custoA().getCount() - quantosTem(o.custoA().getItem()));
				if (!o.custoB().isEmpty()) falta += Math.max(0, o.custoB().getCount() - quantosTem(o.custoB().getItem()));
				int esmFalta = Math.max(0, o.esmeraldas() - (quantosTem(Items.EMERALD) + 9 * quantosTem(Items.EMERALD_BLOCK)));
				nova.add(new Linha(o.custoA(), o.custoB(), o.resultado(), falta == 0, esmFalta, true, i, ItemStack.EMPTY));
			}
		} else {
			// primeiro o que você tem, depois todo o resto que ele compra
			Set<Item> jaListados = new HashSet<>();
			for (OfertasPayload.Oferta o : handler.getOfertas()) {
				jaListados.add(o.item().getItem());
				if (combina(o.item(), busca)) nova.add(linhaVenda(o, o.temJogador()));
			}
			for (OfertasPayload.Oferta o : handler.getCatalogoCompra()) {
				if (jaListados.contains(o.item().getItem()) || !combina(o.item(), busca)) continue;
				nova.add(linhaVenda(o, 0));
			}
		}
		linhas = nova;
		if (!busca.equals(ultimaPesquisa)) {
			ultimaPesquisa = busca;
			inicio = 0;
		}
		inicio = MathHelper.clamp(inicio, 0, Math.max(0, linhas.size() - VISIVEIS));
	}

	private static Linha linhaVenda(OfertasPayload.Oferta o, int tem) {
		ItemStack a = o.item().copyWithCount(Math.min(64, o.quantidade()));
		ItemStack b = o.quantidade() > 64 ? o.item().copyWithCount(o.quantidade() - 64) : ItemStack.EMPTY;
		return new Linha(a, b, new ItemStack(Items.EMERALD, o.esmeraldas()), tem >= o.quantidade(),
			Math.max(0, o.quantidade() - tem), false, -1, o.item());
	}

	// ------------------------------------------------------------------ desenho

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		int nivel = handler.getProps().get(MercadorScreenHandler.P_NIVEL);
		Text titulo = nivel > 0 && nivel <= 5
			? Text.translatable("merchant.title", this.title, Text.translatable("merchant.level." + nivel))
			: this.title;
		ctx.drawText(textRenderer, titulo, 49 + backgroundWidth / 2 - textRenderer.getWidth(titulo) / 2, 6, 0x404040, false);
		ctx.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0x404040, false);

		// aviso no estilo do "Caro demais!" da bigorna
		Text aviso = aviso(handler.cotacaoCliente());
		if (aviso != null) {
			int largura = textRenderer.getWidth(aviso);
			int x = backgroundWidth - 8 - largura - 2;
			ctx.fill(x - 2, 60, backgroundWidth - 8, 72, 0x4F000000);
			ctx.drawTextWithShadow(textRenderer, aviso, x, 62, 0xFF6060);
		}
	}

	private static Text aviso(Cotacao c) {
		return switch (c.status()) {
			case INSUFICIENTE -> Text.translatable("gui.trocasmartins.faltam_itens", c.faltam());
			case COMPRA_FALTA -> Text.translatable("gui.trocasmartins.faltam_esmeraldas", c.faltam());
			case MISTURADO -> Text.translatable("gui.trocasmartins.misturado");
			case NAO_COMPRA -> Text.translatable("gui.trocasmartins.nao_compra");
			default -> null;
		};
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		ctx.drawTexture(TEXTURE, this.x, this.y, 0, 0.0F, 0.0F, backgroundWidth, backgroundHeight, 512, 256);
		if (handler.cotacaoCliente().impossivel()) {
			ctx.drawGuiTexture(OUT_OF_STOCK, this.x + 83 + 99, this.y + 35, 0, 28, 21);
		}
	}

	private void desenharXp(DrawContext ctx, int x, int y) {
		int nivel = handler.getProps().get(MercadorScreenHandler.P_NIVEL);
		int xp = handler.getProps().get(MercadorScreenHandler.P_XP);
		if (nivel < 1 || nivel >= 5) return;
		ctx.drawGuiTexture(XP_FUNDO, x + 136, y + 16, 0, 102, 5);
		int min = VillagerData.getLowerLevelExperience(nivel);
		if (xp >= min && VillagerData.canLevelUp(nivel)) {
			float f = 102.0F / (VillagerData.getUpperLevelExperience(nivel) - min);
			int atual = Math.min(MathHelper.floor(f * (xp - min)), 102);
			ctx.drawGuiTexture(XP_ATUAL, 102, 5, 0, 0, x + 136, y + 16, 0, atual, 5);
			Cotacao c = handler.cotacaoCliente();
			if (c.ok()) {
				int ganho = Math.min(MathHelper.floor(Math.max(1, c.esmeraldas()) * f), 102 - atual);
				ctx.drawGuiTexture(XP_RESULTADO, 102, 5, atual, 0, x + 136 + atual, y + 16, 0, ganho, 5);
			}
		}
	}

	private void desenharBarra(DrawContext ctx, int x, int y) {
		int n = linhas.size() + 1 - VISIVEIS;
		if (n > 1) {
			int j = 139 - (27 + (n - 1) * 139 / n);
			int passo = 1 + j / n + 139 / n;
			int m = Math.min(113, inicio * passo);
			if (inicio == n - 1) m = 113;
			ctx.drawGuiTexture(SCROLLER, x + 94, y + 18 + m, 0, 6, 27);
		} else {
			ctx.drawGuiTexture(SCROLLER_DESATIVADO, x + 94, y + 18, 0, 6, 27);
		}
	}

	private void item(DrawContext ctx, ItemStack s, int x, int y) {
		if (s.isEmpty()) return;
		ctx.drawItemWithoutEntity(s, x, y);
		ctx.drawItemInSlot(textRenderer, s, x, y);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int i = this.x;
		int j = this.y;
		desenharXp(ctx, i, j);
		desenharBarra(ctx, i, j);
		int k = j + 16 + 1;
		for (int idx = inicio; idx < Math.min(linhas.size(), inicio + VISIVEIS); idx++) {
			Linha l = linhas.get(idx);
			int y = k + 2;
			ctx.getMatrices().push();
			ctx.getMatrices().translate(0.0F, 0.0F, 100.0F);
			item(ctx, l.a(), i + 5 + 5, y);
			item(ctx, l.b(), i + 5 + 35, y);
			RenderSystem.enableBlend();
			ctx.drawGuiTexture(l.disponivel() ? SETA : SETA_ESGOTADA, i + 5 + 35 + 20, y + 3, 0, 10, 9);
			item(ctx, l.c(), i + 5 + 68, y);
			ctx.getMatrices().pop();
			k += 20;
		}
		if (linhas.isEmpty()) {
			Text vazio = Text.translatable(modoComprar || !pesquisa.getText().isEmpty()
				? "gui.trocasmartins.nada_encontrado" : "gui.trocasmartins.nada");
			ctx.drawText(textRenderer, vazio, i + 49 - textRenderer.getWidth(vazio) / 2, j + 80, 0x404040, false);
		}

		for (BotaoOferta b : botoes) {
			b.visible = b.indice + inicio < linhas.size();
			if (b.isSelected()) b.renderTooltip(ctx, mouseX, mouseY);
		}

		Text aviso = aviso(handler.cotacaoCliente());
		if (aviso != null && isPointWithinBounds(186, 35, 22, 21, mouseX, mouseY)) {
			ctx.drawTooltip(textRenderer, List.of(Text.translatable("gui.trocasmartins.impossivel"), aviso), mouseX, mouseY);
		}
		RenderSystem.enableDepthTest();
		drawMouseoverTooltip(ctx, mouseX, mouseY);
	}

	// ------------------------------------------------------------------ teclado e mouse

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (pesquisa.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				pesquisa.setFocused(false);
				setFocused(null);
				return true;
			}
			return pesquisa.keyPressed(keyCode, scanCode, modifiers) || true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int n = linhas.size();
		if (n > VISIVEIS) inicio = MathHelper.clamp((int) (inicio - verticalAmount), 0, n - VISIVEIS);
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		int n = linhas.size();
		if (rolando) {
			int topo = this.y + 18;
			int fundo = topo + 139;
			float f = ((float) mouseY - topo - 13.5F) / (fundo - topo - 27.0F);
			f = f * (n - VISIVEIS) + 0.5F;
			inicio = MathHelper.clamp((int) f, 0, Math.max(0, n - VISIVEIS));
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		rolando = false;
		if (linhas.size() > VISIVEIS && mouseX > x + 94 && mouseX < x + 100 && mouseY > y + 18 && mouseY <= y + 158) {
			rolando = true;
		}
		if (!pesquisa.isMouseOver(mouseX, mouseY)) pesquisa.setFocused(false);
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private class BotaoOferta extends ButtonWidget {
		final int indice;

		BotaoOferta(int x, int y, int indice, PressAction acao) {
			super(x, y, 88, 20, ScreenTexts.EMPTY, acao, DEFAULT_NARRATION_SUPPLIER);
			this.indice = indice;
			this.visible = false;
		}

		void renderTooltip(DrawContext ctx, int x, int y) {
			if (!hovered || indice + inicio >= linhas.size()) return;
			Linha l = linhas.get(indice + inicio);
			if (x < getX() + 20) {
				ctx.drawItemTooltip(textRenderer, l.a(), x, y);
			} else if (x < getX() + 50 && x > getX() + 30 && !l.b().isEmpty()) {
				ctx.drawItemTooltip(textRenderer, l.b(), x, y);
			} else if (x >= getX() + 50 && x <= getX() + 65 && !l.disponivel()) {
				ctx.drawTooltip(textRenderer, Text.translatable(l.comprar()
					? "gui.trocasmartins.faltam_esmeraldas" : "gui.trocasmartins.faltam_itens", l.faltam()), x, y);
			} else if (x > getX() + 65) {
				ctx.drawItemTooltip(textRenderer, l.c(), x, y);
			}
		}
	}
}
