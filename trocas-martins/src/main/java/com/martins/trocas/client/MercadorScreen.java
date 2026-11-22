package com.martins.trocas.client;

import com.martins.trocas.mercador.Cotacao;
import com.martins.trocas.mercador.MercadorScreenHandler;
import com.martins.trocas.rede.EscolherOfertaPayload;
import com.martins.trocas.rede.OfertasPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.VillagerData;

import java.util.List;

/**
 * Tela do Mercador feita com as mesmas texturas e medidas da tela de trocas dos aldeões.
 * A lista à esquerda mostra o que o Mercador compra do seu inventário; clicar numa troca
 * coloca os itens nos espaços de pagamento, como numa troca normal.
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
	private static final Text TROCAS = Text.translatable("merchant.trades");
	private static final int VISIVEIS = 7;

	private final BotaoOferta[] botoes = new BotaoOferta[VISIVEIS];
	private int inicio;
	private int selecionado = -1;
	private boolean rolando;

	public MercadorScreen(MercadorScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 276;
		this.playerInventoryTitleX = 107;
	}

	private List<OfertasPayload.Oferta> ofertas() {
		return handler.getOfertas();
	}

	@Override
	protected void init() {
		super.init();
		int i = (this.width - this.backgroundWidth) / 2;
		int j = (this.height - this.backgroundHeight) / 2;
		int k = j + 16 + 2;
		for (int l = 0; l < VISIVEIS; l++) {
			botoes[l] = addDrawableChild(new BotaoOferta(i + 5, k, l, b -> {
				selecionado = ((BotaoOferta) b).indice + inicio;
				ClientPlayNetworking.send(new EscolherOfertaPayload(selecionado));
			}));
			k += 20;
		}
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		int nivel = handler.getProps().get(MercadorScreenHandler.P_NIVEL);
		Text titulo = nivel > 0 && nivel <= 5
			? Text.translatable("merchant.title", this.title, Text.translatable("merchant.level." + nivel))
			: this.title;
		ctx.drawText(textRenderer, titulo, 49 + backgroundWidth / 2 - textRenderer.getWidth(titulo) / 2, 6, 0x404040, false);
		ctx.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0x404040, false);
		ctx.drawText(textRenderer, TROCAS, 5 - textRenderer.getWidth(TROCAS) / 2 + 48, 6, 0x404040, false);

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
			case MISTURADO -> Text.translatable("gui.trocasmartins.misturado");
			case NAO_COMPRA -> Text.translatable("gui.trocasmartins.nao_compra");
			default -> null;
		};
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		int i = (this.width - this.backgroundWidth) / 2;
		int j = (this.height - this.backgroundHeight) / 2;
		ctx.drawTexture(TEXTURE, i, j, 0, 0.0F, 0.0F, backgroundWidth, backgroundHeight, 512, 256);
		Cotacao.Status s = handler.cotacaoCliente().status();
		if (s != Cotacao.Status.VAZIO && s != Cotacao.Status.OK) {
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
		int n = ofertas().size() + 1 - VISIVEIS;
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

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		List<OfertasPayload.Oferta> lista = ofertas();
		int i = (this.width - this.backgroundWidth) / 2;
		int j = (this.height - this.backgroundHeight) / 2;
		desenharXp(ctx, i, j);
		if (!lista.isEmpty()) {
			inicio = MathHelper.clamp(inicio, 0, Math.max(0, lista.size() - VISIVEIS));
			int k = j + 16 + 1;
			desenharBarra(ctx, i, j);
			for (int idx = inicio; idx < Math.min(lista.size(), inicio + VISIVEIS); idx++) {
				OfertasPayload.Oferta o = lista.get(idx);
				int y = k + 2;
				ctx.getMatrices().push();
				ctx.getMatrices().translate(0.0F, 0.0F, 100.0F);
				ItemStack primeiro = o.item().copyWithCount(Math.min(64, o.quantidade()));
				ctx.drawItemWithoutEntity(primeiro, i + 5 + 5, y);
				ctx.drawItemInSlot(textRenderer, primeiro, i + 5 + 5, y);
				if (o.quantidade() > 64) {
					ItemStack segundo = o.item().copyWithCount(o.quantidade() - 64);
					ctx.drawItemWithoutEntity(segundo, i + 5 + 35, y);
					ctx.drawItemInSlot(textRenderer, segundo, i + 5 + 35, y);
				}
				RenderSystem.enableBlend();
				ctx.drawGuiTexture(o.disponivel() ? SETA : SETA_ESGOTADA, i + 5 + 35 + 20, y + 3, 0, 10, 9);
				ItemStack esm = new ItemStack(Items.EMERALD, o.esmeraldas());
				ctx.drawItemWithoutEntity(esm, i + 5 + 68, y);
				ctx.drawItemInSlot(textRenderer, esm, i + 5 + 68, y);
				ctx.getMatrices().pop();
				k += 20;
			}
		}

		for (BotaoOferta b : botoes) {
			b.visible = b.indice + inicio < lista.size();
			if (b.isSelected()) b.renderTooltip(ctx, mouseX, mouseY);
		}

		Cotacao c = handler.cotacaoCliente();
		Text aviso = aviso(c);
		if (aviso != null && isPointWithinBounds(186, 35, 22, 21, mouseX, mouseY)) {
			ctx.drawTooltip(textRenderer, List.of(Text.translatable("gui.trocasmartins.impossivel"), aviso), mouseX, mouseY);
		}
		RenderSystem.enableDepthTest();
		drawMouseoverTooltip(ctx, mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int n = ofertas().size();
		if (n > VISIVEIS) inicio = MathHelper.clamp((int) (inicio - verticalAmount), 0, n - VISIVEIS);
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		int n = ofertas().size();
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
		int i = (this.width - this.backgroundWidth) / 2;
		int j = (this.height - this.backgroundHeight) / 2;
		if (ofertas().size() > VISIVEIS && mouseX > i + 94 && mouseX < i + 100 && mouseY > j + 18 && mouseY <= j + 158) {
			rolando = true;
		}
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
			if (!hovered || indice + inicio >= ofertas().size()) return;
			OfertasPayload.Oferta o = ofertas().get(indice + inicio);
			if (x < getX() + 20) {
				ctx.drawItemTooltip(textRenderer, o.item(), x, y);
			} else if (x > getX() + 50 && x < getX() + 65 && !o.disponivel()) {
				ctx.drawTooltip(textRenderer, Text.translatable("gui.trocasmartins.faltam_itens", o.quantidade() - o.temJogador()), x, y);
			} else if (x > getX() + 65) {
				ctx.drawItemTooltip(textRenderer, new ItemStack(Items.EMERALD, o.esmeraldas()), x, y);
			}
		}
	}
}
