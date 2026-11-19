package br.bolsavillager.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Dá acesso à lista de raids de um mundo, para a bolsa reagir a invasões. */
@Mixin(Raids.class)
public interface RaidsAccessor {
	@Accessor("raidMap")
	Int2ObjectMap<Raid> bolsavillager$getRaidMap();
}
