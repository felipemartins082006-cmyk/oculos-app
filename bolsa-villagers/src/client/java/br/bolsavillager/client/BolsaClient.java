package br.bolsavillager.client;

import br.bolsavillager.BolsaMod;
import br.bolsavillager.client.screen.BolsaScreen;
import br.bolsavillager.client.screen.VillagerPanel;
import br.bolsavillager.market.StockDef;
import br.bolsavillager.net.MarketSyncPayload;
import br.bolsavillager.net.NotifyPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;

public class BolsaClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(BolsaMod.id("bolsa"));
	/** Tecla que abre a bolsa em qualquer lugar (padrão: B). Pode ser trocada em Opções > Controles. */
	public static final KeyMapping OPEN_KEY = new KeyMapping("key.bolsavillager.open", InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_B, CATEGORY);

	/** Um tipo de notificação por assunto, para um aviso não apagar o outro. */
	private static final SystemToast.SystemToastId[] TOASTS = {
			new SystemToast.SystemToastId(5000L),
			new SystemToast.SystemToastId(7000L),
			new SystemToast.SystemToastId(6000L),
			new SystemToast.SystemToastId(8000L)
	};

	@Override
	public void onInitializeClient() {
		KeyMappingHelper.registerKeyMapping(OPEN_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_KEY.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new BolsaScreen());
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(MarketSyncPayload.TYPE, (payload, context) -> ClientMarket.accept(payload));
		// Avisos da bolsa aparecem como notificação no canto da tela, nunca no chat.
		ClientPlayNetworking.registerGlobalReceiver(NotifyPayload.TYPE, (payload, context) -> {
			int kind = Math.floorMod(payload.kind(), TOASTS.length);
			SystemToast.addOrUpdate(context.client().gui.toastManager(), TOASTS[kind], payload.title(), payload.message());
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

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
