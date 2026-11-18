package br.bolsavillager.client;

import br.bolsavillager.client.screen.TerminalScreen;
import br.bolsavillager.client.screen.VillagerPanel;
import br.bolsavillager.market.StockDef;
import br.bolsavillager.net.MarketSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;

public class BolsaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(MarketSyncPayload.TYPE, (payload, context) -> {
			ClientMarket.accept(payload);
			if (payload.openTerminal()) {
				context.client().gui.setScreen(new TerminalScreen());
			}
		});

		// Lembra de qual guilda é o villager clicado, para o painel na tela de trocas.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide()) {
				StockDef def = StockDef.forEntity(entity);
				if (def != null) {
					ClientMarket.setLastMerchantStock(def);
				}
			}
			return InteractionResult.PASS;
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientMarket.clear());

		VillagerPanel.register();
	}
}
