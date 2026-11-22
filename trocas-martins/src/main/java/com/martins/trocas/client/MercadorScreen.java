package com.martins.trocas.client;

import com.martins.trocas.TrocasMartins;
import com.martins.trocas.mercador.Cotacao;
import com.martins.trocas.mercador.MercadorScreenHandler;
import com.martins.trocas.mercador.Taxa;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public class MercadorScreen extends HandledScreen<MercadorScreenHandler> {
	private static final Identifier FUNDO = TrocasMartins.id("textures/gui/mercador.png");
	private static final Identifier X_VERMELHO = Identifier.ofVanilla("container/villager/out_of_stock");
	private static final Identifier SETA = TrocasMartins.id("textures/gui/mercador.png");

	private static final int VERDE = 0x55FF55, VERMELHO = 0xFF5555, CINZA = 0xAAAAAA, AMARELO = 0xFFFF55, BRANCO = 0xFFFFFF;

	public MercadorScreen(MercadorScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 192;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		int x = this.x, y = this.y;
		ctx.drawTexture(FUNDO, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);

		Cotacao c = handler.cotacaoCliente();
		if (c.status() == Cotacao.Status.OK) {
			// seta verde cheia
			ctx.drawTexture(SETA, x + 90, y + 35, 176, 0, 22, 15, 256, 256);
		} else if (c.status() != Cotacao.Status.VAZIO) {
			// o "X" das trocas esgotadas: compra não é possível
			ctx.drawGuiTexture(X_VERMELHO, x + 87, y + 32, 28, 21);
		}
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		int nivel = Math.max(1, Math.min(5, handler.getProps().get(MercadorScreenHandler.P_NIVEL)));
		Text titulo = this.title.copy().append(" - ").append(Text.translatable("merchant.level." + nivel));
		ctx.drawText(textRenderer, titulo, titleX, titleY, 0x404040, false);
		ctx.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0x404040, false);

		Cotacao c = handler.cotacaoCliente();

		// campo "Faltam"
		if (c.status() == Cotacao.Status.INSUFICIENTE) {
			centro(ctx, Text.translatable("gui.trocasmartins.faltam", c.faltam()), 130, 62, VERMELHO, 76);
		} else if (c.status() == Cotacao.Status.OK && c.faltam() > 0) {
			centro(ctx, Text.translatable("gui.trocasmartins.proxima", c.faltam()), 130, 62, AMARELO, 76);
		} else {
			centro(ctx, Text.literal("-"), 130, 62, CINZA, 76);
		}

		Text linha1;
		Text linha2 = Text.empty();
		int cor1;
		switch (c.status()) {
			case MISTURADO -> {
				linha1 = Text.translatable("gui.trocasmartins.misturado");
				linha2 = Text.translatable("gui.trocasmartins.misturado2");
				cor1 = VERMELHO;
			}
			case NAO_COMPRA -> {
				linha1 = Text.translatable("gui.trocasmartins.nao_compra");
				linha2 = Text.translatable("gui.trocasmartins.nao_compra2");
				cor1 = VERMELHO;
			}
			case INSUFICIENTE -> {
				linha1 = Text.translatable("gui.trocasmartins.insuficiente", c.total(), c.faltam());
				linha2 = Text.literal(Taxa.descrever(c));
				cor1 = VERMELHO;
			}
			case OK -> {
				linha1 = Text.translatable("gui.trocasmartins.oferta", c.usados(), c.esmeraldas());
				linha2 = Text.literal(Taxa.descrever(c));
				cor1 = VERDE;
			}
			default -> {
				linha1 = Text.translatable("gui.trocasmartins.vazio");
				linha2 = Text.translatable("gui.trocasmartins.vazio2");
				cor1 = BRANCO;
			}
		}
		centro(ctx, linha1, 88, 77, cor1, 158);
		centro(ctx, linha2, 88, 88, CINZA, 158);
	}

	private void centro(DrawContext ctx, Text texto, int cx, int y, int cor, int max) {
		int largura = textRenderer.getWidth(texto);
		if (largura > max) {
			// encolhe um pouco textos muito longos
			ctx.getMatrices().push();
			float escala = (float) max / largura;
			ctx.getMatrices().translate(cx - max / 2f, y + (1 - escala) * 4, 0);
			ctx.getMatrices().scale(escala, escala, 1);
			ctx.drawText(textRenderer, texto, 0, 0, cor, true);
			ctx.getMatrices().pop();
		} else {
			ctx.drawText(textRenderer, texto, cx - largura / 2, y, cor, true);
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		Cotacao c = handler.cotacaoCliente();
		if (c.status() != Cotacao.Status.VAZIO && c.status() != Cotacao.Status.OK
			&& mouseX >= x + 87 && mouseX < x + 115 && mouseY >= y + 32 && mouseY < y + 53) {
			Text motivo = switch (c.status()) {
				case MISTURADO -> Text.translatable("gui.trocasmartins.misturado");
				case NAO_COMPRA -> Text.translatable("gui.trocasmartins.nao_compra");
				default -> Text.translatable("gui.trocasmartins.faltam_longo", c.faltam());
			};
			ctx.drawTooltip(textRenderer, List.of(
				Text.translatable("gui.trocasmartins.impossivel").formatted(Formatting.RED),
				motivo.copy().formatted(Formatting.GRAY)), mouseX, mouseY);
		}
		drawMouseoverTooltip(ctx, mouseX, mouseY);
	}
}
