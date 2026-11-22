package com.martins.trocas.mixin;

import com.martins.trocas.mercador.PrecoMercador;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Itens únicos (ferramentas, armaduras...) comprados de aldeões guardam quantas esmeraldas custaram.
 * Assim o Mercador nunca paga mais que a metade disso e não dá para "fabricar" esmeraldas
 * comprando barato de um aldeão com desconto e vendendo caro ao Mercador.
 */
@Mixin(TradeOffer.class)
public abstract class TradeOfferMixin {
	@Inject(method = "copySellItem", at = @At("RETURN"))
	private void trocasmartins$marcarPrecoPago(CallbackInfoReturnable<ItemStack> cir) {
		ItemStack item = cir.getReturnValue();
		if (item.isEmpty() || item.getMaxCount() > 1) return;
		TradeOffer self = (TradeOffer) (Object) this;
		int pago = esmeraldas(self.getDisplayedFirstBuyItem()) + esmeraldas(self.getDisplayedSecondBuyItem());
		if (pago <= 0) return;
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, item, nbt -> nbt.putInt(PrecoMercador.CHAVE_PAGO, pago));
	}

	private static int esmeraldas(ItemStack s) {
		if (s.isOf(Items.EMERALD)) return s.getCount();
		if (s.isOf(Items.EMERALD_BLOCK)) return s.getCount() * 9;
		return 0;
	}
}
