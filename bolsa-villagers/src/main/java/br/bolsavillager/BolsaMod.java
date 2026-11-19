package br.bolsavillager;

import br.bolsavillager.market.MarketEngine;
import br.bolsavillager.market.StockDef;
import br.bolsavillager.net.CancelOrderPayload;
import br.bolsavillager.net.MarketSyncPayload;
import br.bolsavillager.net.NotifyPayload;
import br.bolsavillager.net.PlaceOrderPayload;
import br.bolsavillager.net.SellItemsPayload;
import br.bolsavillager.net.TradeSharesPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BolsaMod implements ModInitializer {
	public static final String MOD_ID = "bolsavillager";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BolsaConfig.load();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> BolsaCommand.register(dispatcher));

		// Rede
		PayloadTypeRegistry.clientboundPlay().register(MarketSyncPayload.TYPE, MarketSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(NotifyPayload.TYPE, NotifyPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TradeSharesPayload.TYPE, TradeSharesPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SellItemsPayload.TYPE, SellItemsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PlaceOrderPayload.TYPE, PlaceOrderPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CancelOrderPayload.TYPE, CancelOrderPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(TradeSharesPayload.TYPE, (payload, context) -> {
			StockDef def = StockDef.byId(payload.stockId());
			if (def != null) {
				MarketEngine.executeTrade(context.player(), def, payload.quantity());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SellItemsPayload.TYPE, (payload, context) -> {
			StockDef guild = StockDef.byId(payload.guildId());
			if (guild != null) {
				MarketEngine.sellItems(context.player(), payload.item(), guild, payload.quantity());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(PlaceOrderPayload.TYPE, (payload, context) -> {
			StockDef def = StockDef.byId(payload.stockId());
			if (def != null) {
				MarketEngine.placeOrder(context.player(), payload.orderType(), def, payload.quantity(), payload.target());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(CancelOrderPayload.TYPE,
				(payload, context) -> MarketEngine.cancelOrder(context.player(), payload.index()));

		// Eventos do mundo que mexem no mercado (as trocas e reposições entram pelos mixins)
		ServerTickEvents.END_SERVER_TICK.register(MarketEngine::onServerTick);
		ServerLivingEntityEvents.AFTER_DEATH.register(MarketEngine::onDeath);
		ServerEntityEvents.ENTITY_LOAD.register(MarketEngine::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(MarketEngine::onEntityUnload);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MarketEngine.onJoin(handler.player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MarketEngine.reset());

		LOGGER.info("Bolsa dos Villagers aberta para negociação!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
