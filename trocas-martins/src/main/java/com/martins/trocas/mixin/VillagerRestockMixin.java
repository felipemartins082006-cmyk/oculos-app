package com.martins.trocas.mixin;

import com.martins.trocas.ModRegistro;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Quando o aldeão repõe as trocas (trabalhando no bloco), o Mercador também zera os limites. */
@Mixin(Villager.class)
public abstract class VillagerRestockMixin {
	@Inject(method = {"restock", "catchUpDemand"}, at = @At("TAIL"))
	private void trocasmartins$repor(CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (ModRegistro.ehMercador(self)) self.removeAttached(ModRegistro.USOS);
	}

	/** O Mercador não tem trocas normais: ele precisa repor quando alguma troca dele foi usada. */
	@Inject(method = "needsToRestock", at = @At("HEAD"), cancellable = true)
	private void trocasmartins$precisaRepor(CallbackInfoReturnable<Boolean> cir) {
		Villager self = (Villager) (Object) this;
		if (ModRegistro.ehMercador(self)) {
			var usos = self.getAttached(ModRegistro.USOS);
			cir.setReturnValue(usos != null && !usos.isEmpty());
		}
	}
}
