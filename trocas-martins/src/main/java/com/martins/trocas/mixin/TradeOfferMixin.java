package com.martins.trocas.mixin;

import com.martins.trocas.mercador.PrecoMercador;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Itens únicos comprados de aldeões guardam quantas esmeraldas custaram, para o Mercador
 * nunca pagar mais que a metade disso (sem "fabricar" esmeraldas com descontos).
 */
@Mixin(MerchantOffer.class)
public abstract class TradeOfferMixin {
	@Inject(method = "assemble", at = @At("RETURN"))
	private void trocasmartins$marcarPrecoPago(CallbackInfoReturnable<ItemStack> cir) {
		ItemStack item = cir.getReturnValue();
		if (item.isEmpty() || item.getMaxStackSize() > 1) return;
		MerchantOffer self = (MerchantOffer) (Object) this;
		int pago = esmeraldas(self.getCostA()) + esmeraldas(self.getCostB());
		if (pago <= 0) return;
		CustomData.update(DataComponents.CUSTOM_DATA, item, tag -> tag.putInt(PrecoMercador.CHAVE_PAGO, pago));
	}

	private static int esmeraldas(ItemStack s) {
		if (s.is(Items.EMERALD)) return s.getCount();
		if (s.is(Items.EMERALD_BLOCK)) return s.getCount() * 9;
		return 0;
	}
}
