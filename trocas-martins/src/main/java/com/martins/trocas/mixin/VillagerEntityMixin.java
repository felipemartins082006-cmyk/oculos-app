package com.martins.trocas.mixin;

import com.martins.trocas.trocas.NovasTrocas;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {
	@Inject(method = "fillRecipes", at = @At("TAIL"))
	private void trocasmartins$novasTrocas(CallbackInfo ci) {
		VillagerEntity self = (VillagerEntity) (Object) this;
		NovasTrocas.adicionarTrocas(self, self.getOffers());
	}
}
