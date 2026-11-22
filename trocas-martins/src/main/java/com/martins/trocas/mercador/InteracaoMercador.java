package com.martins.trocas.mercador;

import com.martins.trocas.ModRegistro;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Clique direito num aldeão Mercador abre o balcão de vendas no lugar das trocas normais. */
public final class InteracaoMercador {
	private InteracaoMercador() {
	}

	public static ActionResult aoInteragir(PlayerEntity player, World world, Hand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (!(entity instanceof VillagerEntity aldeao)) return ActionResult.PASS;
		if (aldeao.getVillagerData().getProfession() != ModRegistro.MERCADOR) return ActionResult.PASS;
		if (player.isSpectator() || !aldeao.isAlive() || aldeao.isBaby() || aldeao.isSleeping()) return ActionResult.PASS;
		ItemStack naMao = player.getStackInHand(hand);
		if (naMao.isOf(Items.NAME_TAG) || naMao.isOf(Items.LEAD) || naMao.isOf(Items.VILLAGER_SPAWN_EGG)) return ActionResult.PASS;
		if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

		if (world.isClient) return ActionResult.SUCCESS;

		if (aldeao.getCustomer() == player) return ActionResult.SUCCESS;
		if (aldeao.getCustomer() != null) {
			aldeao.setHeadRollingTimeLeft(40);
			aldeao.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, aldeao.getSoundPitch());
			return ActionResult.SUCCESS;
		}

		player.incrementStat(Stats.TALKED_TO_VILLAGER);
		aldeao.setCustomer(player);
		player.openHandledScreen(new ExtendedScreenHandlerFactory<Integer>() {
			@Override
			public Integer getScreenOpeningData(ServerPlayerEntity p) {
				return aldeao.getId();
			}

			@Override
			public Text getDisplayName() {
				return aldeao.getDisplayName();
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
				return new MercadorScreenHandler(syncId, inv, aldeao);
			}
		});
		if (!(player.currentScreenHandler instanceof MercadorScreenHandler)) {
			aldeao.setCustomer(null);
		}
		return ActionResult.SUCCESS;
	}
}
