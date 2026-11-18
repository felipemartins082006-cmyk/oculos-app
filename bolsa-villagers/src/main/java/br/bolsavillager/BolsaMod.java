package br.bolsavillager;

import br.bolsavillager.block.TerminalBlock;
import br.bolsavillager.market.MarketEngine;
import br.bolsavillager.market.StockDef;
import br.bolsavillager.net.MarketSyncPayload;
import br.bolsavillager.net.TradeSharesPayload;
import br.bolsavillager.net.WithdrawPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BolsaMod implements ModInitializer {
	public static final String MOD_ID = "bolsavillager";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final ResourceKey<Block> TERMINAL_KEY = ResourceKey.create(Registries.BLOCK, id("terminal"));
	public static final Block TERMINAL = Registry.register(BuiltInRegistries.BLOCK, TERMINAL_KEY,
			new TerminalBlock(BlockBehaviour.Properties.of()
					.setId(TERMINAL_KEY)
					.mapColor(MapColor.EMERALD)
					.strength(2.5F)
					.sound(SoundType.WOOD)
					.lightLevel(state -> 7)));

	private static final ResourceKey<Item> TERMINAL_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("terminal"));
	public static final Item TERMINAL_ITEM = Registry.register(BuiltInRegistries.ITEM, TERMINAL_ITEM_KEY,
			new BlockItem(TERMINAL, new Item.Properties().setId(TERMINAL_ITEM_KEY).useBlockDescriptionPrefix()));

	@Override
	public void onInitialize() {
		BolsaConfig.load();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> BolsaCommand.register(dispatcher));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(Items.LECTERN, TERMINAL_ITEM));

		// Rede
		PayloadTypeRegistry.clientboundPlay().register(MarketSyncPayload.TYPE, MarketSyncPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TradeSharesPayload.TYPE, TradeSharesPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WithdrawPayload.TYPE, WithdrawPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(TradeSharesPayload.TYPE, (payload, context) -> {
			StockDef def = StockDef.byId(payload.stockId());
			if (def != null) {
				MarketEngine.trade(context.player(), def, payload.quantity());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(WithdrawPayload.TYPE,
				(payload, context) -> MarketEngine.withdraw(context.player()));

		// Eventos do mundo que mexem no mercado (as trocas e reposições entram pelos mixins)
		ServerTickEvents.END_SERVER_TICK.register(MarketEngine::onServerTick);
		ServerLivingEntityEvents.AFTER_DEATH.register(MarketEngine::onDeath);
		ServerEntityEvents.ENTITY_LOAD.register(MarketEngine::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(MarketEngine::onEntityUnload);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!level.isClientSide()) {
				MarketEngine.onMerchantInteract(player, entity);
			}
			return InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MarketEngine.sync(handler.player, false));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MarketEngine.onDisconnect(handler.player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MarketEngine.reset());

		LOGGER.info("Bolsa dos Villagers aberta para negociação!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
