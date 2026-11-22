package com.martins.trocas.mixin;

import com.martins.trocas.client.TrocasMartinsClient;
import com.martins.trocas.trocas.NiveisExtras;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tela de trocas dos aldeões: título "Mestre II, III..." e barra de experiência depois de Mestre. */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {
	private static final Identifier XP_FUNDO = Identifier.withDefaultNamespace("container/villager/experience_bar_background");
	private static final Identifier XP_ATUAL = Identifier.withDefaultNamespace("container/villager/experience_bar_current");
	private static final Identifier XP_RESULTADO = Identifier.withDefaultNamespace("container/villager/experience_bar_result");
	private static final int COR = 0xFF404040;

	private MerchantScreenMixin(MerchantMenu m, Inventory i, Component t) {
		super(m, i, t, 276, 166);
	}

	private static String romano(int n) {
		String[] r = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < v.length; i++) while (n >= v[i]) {
			sb.append(r[i]);
			n -= v[i];
		}
		return sb.toString();
	}

	@Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
	private void trocasmartins$titulo(GuiGraphicsExtractor g, int xm, int ym, CallbackInfo ci) {
		int extra = TrocasMartinsClient.nivelExtra;
		if (menu.getTraderLevel() != 5 || extra <= 0) return;
		Component nivel = Component.translatable("merchant.level.5").copy().append(" " + romano(extra + 1));
		Component titulo = Component.translatable("merchant.title", this.title, nivel);
		g.text(font, titulo, 49 + imageWidth / 2 - font.width(titulo) / 2, 6, COR, false);
		g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COR, false);
		Component trocas = Component.translatable("merchant.trades");
		g.text(font, trocas, 5 - font.width(trocas) / 2 + 48, 6, COR, false);
		ci.cancel();
	}

	@Inject(method = "extractContents", at = @At("TAIL"))
	private void trocasmartins$barraXp(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (menu.getTraderLevel() != 5 || !menu.showProgressBar() || menu.getOffers().isEmpty()) return;
		int extra = TrocasMartinsClient.nivelExtra;
		int min = NiveisExtras.xpPara(extra), max = NiveisExtras.xpPara(extra + 1);
		int x = (this.width - this.imageWidth) / 2, y = (this.height - this.imageHeight) / 2;
		float f = 102.0F / (max - min);
		int atual = Mth.clamp(Mth.floor(f * (menu.getTraderXp() - min)), 0, 102);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_FUNDO, x + 136, y + 16, 102, 5);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_ATUAL, 102, 5, 0, 0, x + 136, y + 16, atual, 5);
		int ganho = menu.getFutureTraderXp();
		if (ganho > 0) {
			int o = Math.min(Mth.floor(ganho * f), 102 - atual);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_RESULTADO, 102, 5, atual, 0, x + 136 + atual, y + 16, o, 5);
		}
	}
}
