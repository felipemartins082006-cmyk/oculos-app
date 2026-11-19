package br.bolsavillager.mixin;

import br.bolsavillager.market.TradeLimits;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trocas nunca esgotam (some o X vermelho). No lugar do bloqueio, cada troca além do estoque
 * normal fica mais cara (ágio de escassez). Roda igual no servidor e no cliente, então o preço
 * mostrado na tela de trocas é o preço cobrado.
 */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin {
	@Shadow
	private int uses;
	@Shadow
	private int maxUses;

	@Inject(method = "isOutOfStock", at = @At("HEAD"), cancellable = true)
	private void bolsavillager$neverOutOfStock(CallbackInfoReturnable<Boolean> cir) {
		if (TradeLimits.unlimited) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getModifiedCostCount", at = @At("RETURN"), cancellable = true)
	private void bolsavillager$scarcitySurcharge(ItemCost cost, CallbackInfoReturnable<Integer> cir) {
		int extra = TradeLimits.surcharge(cost.count(), this.uses, this.maxUses);
		if (extra > 0) {
			cir.setReturnValue(Math.min(cir.getReturnValue() + extra, cost.itemStack().getMaxStackSize()));
		}
	}
}
