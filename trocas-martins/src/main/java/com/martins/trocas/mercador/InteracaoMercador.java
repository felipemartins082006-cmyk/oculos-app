package com.martins.trocas.mercador;

import com.martins.trocas.ModRegistro;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/** Clique direito num aldeão Mercador abre a tela de trocas do Mercador no lugar das trocas normais. */
public final class InteracaoMercador {
	private InteracaoMercador() {
	}

	public static InteractionResult aoInteragir(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (!(entity instanceof Villager aldeao) || !ModRegistro.ehMercador(aldeao)) return InteractionResult.PASS;
		if (player.isSpectator() || !aldeao.isAlive() || aldeao.isBaby() || aldeao.isSleeping()) return InteractionResult.PASS;
		ItemStack naMao = player.getItemInHand(hand);
		if (naMao.is(Items.NAME_TAG) || naMao.is(Items.LEAD) || naMao.is(Items.VILLAGER_SPAWN_EGG)) return InteractionResult.PASS;
		if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

		if (level.isClientSide()) return InteractionResult.SUCCESS;

		if (aldeao.getTradingPlayer() == player) return InteractionResult.SUCCESS;
		if (aldeao.getTradingPlayer() != null) {
			aldeao.setUnhappyCounter(40);
			aldeao.playSound(SoundEvents.VILLAGER_NO, 1.0f, aldeao.getVoicePitch());
			return InteractionResult.SUCCESS;
		}

		player.awardStat(Stats.TALKED_TO_VILLAGER);
		aldeao.setTradingPlayer(player);
		player.openMenu(new ExtendedMenuProvider<Integer>() {
			@Override
			public Integer getScreenOpeningData(ServerPlayer p) {
				return aldeao.getId();
			}

			@Override
			public Component getDisplayName() {
				return aldeao.getDisplayName();
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player p) {
				return new MercadorMenu(containerId, inv, aldeao);
			}
		});
		if (!(player.containerMenu instanceof MercadorMenu)) aldeao.setTradingPlayer(null);
		return InteractionResult.SUCCESS;
	}
}
