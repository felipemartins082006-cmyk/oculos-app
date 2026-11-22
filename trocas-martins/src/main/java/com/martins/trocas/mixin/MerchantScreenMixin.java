package com.martins.trocas.mixin;

import com.martins.trocas.client.TrocasMartinsClient;
import com.martins.trocas.trocas.NiveisExtras;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tela de trocas dos aldeões: título "Mestre II, III..." e barra de experiência depois de Mestre. */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends HandledScreen<MerchantScreenHandler> {
	private static final Identifier XP_FUNDO = Identifier.ofVanilla("container/villager/experience_bar_background");
	private static final Identifier XP_ATUAL = Identifier.ofVanilla("container/villager/experience_bar_current");
	private static final Identifier XP_RESULTADO = Identifier.ofVanilla("container/villager/experience_bar_result");

	private MerchantScreenMixin(MerchantScreenHandler h, PlayerInventory i, Text t) {
		super(h, i, t);
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

	@Inject(method = "drawForeground", at = @At("HEAD"), cancellable = true)
	private void trocasmartins$titulo(DrawContext ctx, int mouseX, int mouseY, CallbackInfo ci) {
		int extra = TrocasMartinsClient.nivelExtra;
		if (handler.getLevelProgress() != 5 || extra <= 0) return;
		Text nivel = Text.translatable("merchant.level.5").copy().append(" " + romano(extra + 1));
		Text titulo = Text.translatable("merchant.title", this.title, nivel);
		ctx.drawText(textRenderer, titulo, 49 + backgroundWidth / 2 - textRenderer.getWidth(titulo) / 2, 6, 0x404040, false);
		ctx.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0x404040, false);
		Text trocas = Text.translatable("merchant.trades");
		ctx.drawText(textRenderer, trocas, 5 - textRenderer.getWidth(trocas) / 2 + 48, 6, 0x404040, false);
		ci.cancel();
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void trocasmartins$barraXp(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (handler.getLevelProgress() != 5 || !handler.isLeveled() || handler.getRecipes().isEmpty()) return;
		int extra = TrocasMartinsClient.nivelExtra;
		int min = NiveisExtras.xpPara(extra), max = NiveisExtras.xpPara(extra + 1);
		int xp = handler.getExperience();
		int x = (this.width - this.backgroundWidth) / 2, y = (this.height - this.backgroundHeight) / 2;
		float f = 102.0F / (max - min);
		int atual = MathHelper.clamp(MathHelper.floor(f * (xp - min)), 0, 102);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 200);
		ctx.drawGuiTexture(XP_FUNDO, x + 136, y + 16, 0, 102, 5);
		ctx.drawGuiTexture(XP_ATUAL, 102, 5, 0, 0, x + 136, y + 16, 0, atual, 5);
		int ganho = handler.getMerchantRewardedExperience();
		if (ganho > 0) {
			int o = Math.min(MathHelper.floor(ganho * f), 102 - atual);
			ctx.drawGuiTexture(XP_RESULTADO, 102, 5, atual, 0, x + 136 + atual, y + 16, 0, o, 5);
		}
		ctx.getMatrices().pop();
	}
}
