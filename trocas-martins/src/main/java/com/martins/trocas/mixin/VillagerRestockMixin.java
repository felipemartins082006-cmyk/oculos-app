package com.martins.trocas.mixin;

import com.martins.trocas.ModRegistro;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Quando o aldeão repõe as trocas (trabalhando no bloco), o Mercador também zera os limites. */
@Mixin(VillagerEntity.class)
public abstract class VillagerRestockMixin {
	@Inject(method = {"restock", "restockAndUpdateDemandBonus"}, at = @At("TAIL"))
	private void trocasmartins$repor(CallbackInfo ci) {
		VillagerEntity self = (VillagerEntity) (Object) this;
		if (self.getVillagerData().getProfession() == ModRegistro.MERCADOR) self.removeAttached(ModRegistro.USOS);
	}

	/** O Mercador não tem trocas normais: ele precisa repor quando alguma troca dele foi usada. */
	@Inject(method = "needsRestock", at = @At("HEAD"), cancellable = true)
	private void trocasmartins$precisaRepor(CallbackInfoReturnable<Boolean> cir) {
		VillagerEntity self = (VillagerEntity) (Object) this;
		if (self.getVillagerData().getProfession() == ModRegistro.MERCADOR) {
			var usos = self.getAttached(ModRegistro.USOS);
			cir.setReturnValue(usos != null && !usos.isEmpty());
		}
	}
}
