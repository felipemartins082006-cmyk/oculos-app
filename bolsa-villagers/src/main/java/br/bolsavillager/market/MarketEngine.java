package br.bolsavillager.market;

import br.bolsavillager.BolsaMod;
import br.bolsavillager.net.MarketSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * O "pregão": junta tudo o que os villagers fazem (trocas, reposições, mortes, censo) e
 * transforma em variação de preço a cada {@link #TICK_INTERVAL} ticks.
 *
 * <p>Modelo de preço (por ação, em log-retorno a cada pregão):
 * <pre>
 *   r = pressão_dos_eventos + 0,08 * ln(valor_justo / preço) + ruído(σ = 0,6%)
 *   preço = preço * e^r
 * </pre>
 * O valor justo cresce com a população da profissão. A pressão vem das trocas
 * (mais esmeraldas movimentadas = alta), reposições, estoque esgotado, mortes e compras/vendas de jogadores.
 */
public final class MarketEngine {
	/** Um pregão a cada 30 segundos. */
	public static final int TICK_INTERVAL = 600;
	public static final long DAY_LENGTH = 24000L;
	/** Taxa de corretagem cobrada em cada compra/venda de ações. */
	public static final double FEE = 0.01;
	public static final double MIN_PRICE = 0.25;
	public static final int MAX_ORDER = 64;
	/** Distância máxima (em blocos) do terminal para operar. */
	private static final double TERMINAL_RANGE = 8.0;

	private static final Map<StockDef, Double> pressure = new EnumMap<>(StockDef.class);
	/** Último terminal usado por cada jogador (para validar ordens vindas da tela do terminal). */
	private static final Map<UUID, BlockPos> lastTerminal = new HashMap<>();
	/** Ação do villager com quem cada jogador está negociando agora. */
	private static final Map<UUID, StockDef> merchantStock = new HashMap<>();
	private static final RandomSource random = RandomSource.create();
	private static int tickCounter;

	private MarketEngine() {
	}

	/** Limpa o estado em memória quando o servidor fecha (ex.: sair de um mundo e entrar em outro). */
	public static void reset() {
		pressure.clear();
		lastTerminal.clear();
		merchantStock.clear();
		tickCounter = 0;
	}

	public static MarketData data(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(MarketData.TYPE);
	}

	private static void addPressure(StockDef def, double amount) {
		pressure.merge(def, amount, Double::sum);
	}

	// ---------------------------------------------------------------------------------------------
	// Eventos vindos dos villagers
	// ---------------------------------------------------------------------------------------------

	/** Chamado (via mixin) a cada troca concluída com um villager ou mercador ambulante. */
	public static void onTrade(AbstractVillager merchant, MerchantOffer offer) {
		if (!(merchant.level() instanceof ServerLevel level)) {
			return;
		}
		StockDef def = StockDef.forEntity(merchant);
		if (def == null) {
			return;
		}
		int paidByPlayer = emeralds(offer.getCostA()) + emeralds(offer.getCostB());
		int paidByVillager = emeralds(offer.getResult());
		int value = Math.max(1, paidByPlayer + paidByVillager);

		// Jogador comprando da guilda = demanda pelos produtos dela (alta forte).
		// Jogador vendendo matéria-prima para a guilda = a guilda gasta caixa, mas produz (alta leve).
		double impulse = paidByPlayer > 0 ? 0.004 * paidByPlayer : 0.0015 * Math.max(1, paidByVillager);
		if (offer.isOutOfStock()) {
			impulse += 0.01; // escassez: a oferta esgotou
		}
		addPressure(def, Math.min(impulse, 0.05));

		StockState state = data(level.getServer()).stock(def);
		state.volumeDay += value;
		state.tradesDay++;
		data(level.getServer()).setDirty();
	}

	/** Chamado (via mixin) quando um villager repõe o estoque no posto de trabalho. */
	public static void onRestock(Villager villager) {
		StockDef def = StockDef.forEntity(villager);
		if (def != null && !villager.level().isClientSide()) {
			addPressure(def, 0.003);
		}
	}

	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		StockDef def = StockDef.forEntity(entity);
		if (def != null) {
			// Morte por jogador vira escândalo: queda maior.
			addPressure(def, source.getEntity() instanceof Player ? -0.07 : -0.045);
		}
		if (entity instanceof Villager) {
			MarketData data = data(level.getServer());
			if (data.census().remove(entity.getUUID()) != null) {
				data.setDirty();
			}
		}
	}

	public static void onEntityLoad(Entity entity, ServerLevel level) {
		if (entity instanceof Villager villager) {
			updateCensus(data(level.getServer()), villager);
		}
	}

	public static void onEntityUnload(Entity entity, ServerLevel level) {
		if (!(entity instanceof Villager)) {
			return;
		}
		Entity.RemovalReason reason = entity.getRemovalReason();
		// Descarregar o chunk não tira o villager do censo; morrer, sumir ou virar zumbi tira.
		if (reason != null && reason.shouldDestroy()) {
			MarketData data = data(level.getServer());
			if (data.census().remove(entity.getUUID()) != null) {
				data.setDirty();
			}
		}
	}

	private static void updateCensus(MarketData data, Villager villager) {
		StockDef def = StockDef.forEntity(villager);
		UUID uuid = villager.getUUID();
		String previous = def == null ? data.census().remove(uuid) : data.census().put(uuid, def.id);
		String now = def == null ? null : def.id;
		if (previous == null ? now != null : !previous.equals(now)) {
			data.setDirty();
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Pregão
	// ---------------------------------------------------------------------------------------------

	public static void onServerTick(MinecraftServer server) {
		if (++tickCounter < TICK_INTERVAL) {
			return;
		}
		tickCounter = 0;
		runSession(server);
	}

	/** Roda um pregão imediatamente (normalmente acontece sozinho a cada {@link #TICK_INTERVAL} ticks). */
	public static void runSession(MinecraftServer server) {
		MarketData data = data(server);
		long day = server.overworld().getGameTime() / DAY_LENGTH;
		boolean newDay = day != data.day;

		// Atualiza o censo com os villagers carregados (pega mudanças de profissão).
		Map<StockDef, List<Villager>> loaded = new EnumMap<>(StockDef.class);
		for (ServerLevel level : server.getAllLevels()) {
			for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class), Villager::isAlive)) {
				updateCensus(data, villager);
				StockDef def = StockDef.forEntity(villager);
				if (def != null) {
					loaded.computeIfAbsent(def, d -> new ArrayList<>()).add(villager);
				}
			}
		}
		Map<StockDef, Integer> population = new EnumMap<>(StockDef.class);
		for (String id : data.census().values()) {
			StockDef def = StockDef.byId(id);
			if (def != null) {
				population.merge(def, 1, Integer::sum);
			}
		}

		for (StockDef def : StockDef.values()) {
			StockState state = data.stock(def);
			int pop = population.getOrDefault(def, 0);
			if (def != StockDef.WANDERING_TRADER) {
				// Nova contratação/nascimento puxa para cima, perda de villagers puxa para baixo.
				int delta = pop - state.population;
				if (delta != 0 && state.population + pop > 0) {
					addPressure(def, Mth.clamp(0.025 * delta, -0.1, 0.1));
				}
				state.population = pop;
			}

			double fair = fairValue(def, state.population);
			double r = pressure.getOrDefault(def, 0.0)
					+ 0.08 * Math.log(fair / state.price)
					+ random.nextGaussian() * 0.006;
			r = Mth.clamp(r, -0.15, 0.15);
			state.price = Math.max(MIN_PRICE, state.price * Math.exp(r));
			state.pushHistory(state.price);

			if (newDay) {
				state.dayOpen = state.price;
				state.volumeDay = 0;
				state.tradesDay = 0;
			}

			// Os villagers "sentem" o mercado: comemoram altas e reclamam de quedas.
			if (Math.abs(r) >= 0.03) {
				for (Villager villager : loaded.getOrDefault(def, List.of())) {
					((ServerLevel) villager.level()).sendParticles(
							r > 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ANGRY_VILLAGER,
							villager.getX(), villager.getY() + villager.getBbHeight() + 0.3, villager.getZ(),
							r > 0 ? 6 : 2, 0.3, 0.2, 0.3, 0.0);
				}
			}
		}
		pressure.clear();
		data.day = day;
		data.setDirty();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sync(player, false);
		}
	}

	/** Valor "justo" da ação: cresce com a raiz da população da profissão. */
	public static double fairValue(StockDef def, int population) {
		return def.basePrice * (0.5 + 0.5 * Math.sqrt(1 + population));
	}

	/**
	 * Multiplicador de preço das trocas de um villager conforme a cotação da guilda dele:
	 * ação valorizada = produtos mais caros; ação em baixa = promoção. Limitado a ±30%.
	 */
	public static double tradePriceFactor(MinecraftServer server, StockDef def) {
		StockState state = data(server).stock(def);
		double ratio = state.price / fairValue(def, state.population);
		return Mth.clamp((ratio - 1.0) * 0.3, -0.3, 0.3);
	}

	// ---------------------------------------------------------------------------------------------
	// Jogadores
	// ---------------------------------------------------------------------------------------------

	public static void sync(ServerPlayer player, boolean openTerminal) {
		if (ServerPlayNetworking.canSend(player, MarketSyncPayload.TYPE)) {
			MarketData data = data(player.level().getServer());
			ServerPlayNetworking.send(player, MarketSyncPayload.of(data, data.account(player.getUUID()), openTerminal));
		}
	}

	public static void onTerminalUsed(ServerPlayer player, BlockPos pos) {
		lastTerminal.put(player.getUUID(), pos.immutable());
		sync(player, true);
	}

	public static void onMerchantInteract(Player player, Entity entity) {
		StockDef def = StockDef.forEntity(entity);
		if (def != null) {
			merchantStock.put(player.getUUID(), def);
		} else {
			merchantStock.remove(player.getUUID());
		}
	}

	public static void onDisconnect(ServerPlayer player) {
		lastTerminal.remove(player.getUUID());
		merchantStock.remove(player.getUUID());
	}

	private static boolean canOperate(ServerPlayer player, StockDef def) {
		if (player.containerMenu instanceof MerchantMenu) {
			return merchantStock.get(player.getUUID()) == def;
		}
		BlockPos pos = lastTerminal.get(player.getUUID());
		return pos != null
				&& player.level().getBlockState(pos).is(BolsaMod.TERMINAL)
				&& player.position().closerThan(Vec3.atCenterOf(pos), TERMINAL_RANGE);
	}

	/** Quanto cada ação negociada move o preço (em log): 64 ações = ~29%. */
	public static final double IMPACT_PER_SHARE = 0.004;

	/**
	 * Valor total de uma ordem (sem corretagem), pagando o preço médio ao longo do impacto que a própria
	 * ordem causa. Assim comprar e vender em seguida nunca dá lucro: a ida e a volta se anulam e a
	 * corretagem vira prejuízo. quantity > 0 compra, quantity < 0 vende.
	 */
	public static double orderValue(double price, int quantity) {
		double x = IMPACT_PER_SHARE * Math.abs(quantity);
		double average = quantity > 0 ? price * Math.expm1(x) / x : price * -Math.expm1(-x) / x;
		return average * Math.abs(quantity);
	}

	/** Executa uma ordem: quantity > 0 compra, quantity < 0 vende. */
	public static void trade(ServerPlayer player, StockDef def, int quantity) {
		if (quantity == 0 || Math.abs(quantity) > MAX_ORDER || !canOperate(player, def)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		MarketData data = data(server);
		StockState state = data.stock(def);
		Account account = data.account(player.getUUID());
		int qty = Math.abs(quantity);

		if (quantity > 0) {
			double cost = orderValue(state.price, qty) * (1 + FEE);
			if (account.balance < cost) {
				int needed = (int) Math.ceil(cost - account.balance - 1e-9);
				if (countEmeralds(player) < needed) {
					player.sendSystemMessage(Component.translatable("bolsavillager.msg.no_funds",
							fmt(cost), def.ticker).withStyle(ChatFormatting.RED), true);
					return;
				}
				removeEmeralds(player, needed);
				account.balance += needed;
			}
			account.balance -= cost;
			account.shares.merge(def.id, qty, Integer::sum);
			account.costBasis.merge(def.id, cost, Double::sum);
			// Impacto de mercado da ordem.
			state.price *= Math.exp(IMPACT_PER_SHARE * qty);
			player.sendSystemMessage(Component.translatable("bolsavillager.msg.bought",
					qty, def.ticker, fmt(cost)).withStyle(ChatFormatting.GREEN), true);
		} else {
			int owned = account.sharesOf(def);
			if (owned < qty) {
				player.sendSystemMessage(Component.translatable("bolsavillager.msg.no_shares", def.ticker)
						.withStyle(ChatFormatting.RED), true);
				return;
			}
			double proceeds = orderValue(state.price, -qty) * (1 - FEE);
			double basis = account.costBasis.getOrDefault(def.id, 0.0);
			double basisSold = basis * qty / owned;
			account.balance += proceeds;
			if (owned == qty) {
				account.shares.remove(def.id);
				account.costBasis.remove(def.id);
			} else {
				account.shares.put(def.id, owned - qty);
				account.costBasis.put(def.id, basis - basisSold);
			}
			state.price = Math.max(MIN_PRICE, state.price * Math.exp(-IMPACT_PER_SHARE * qty));
			double profit = proceeds - basisSold;
			player.sendSystemMessage(Component.translatable("bolsavillager.msg.sold",
					qty, def.ticker, fmt(proceeds), (profit >= 0 ? "+" : "") + fmt(profit))
					.withStyle(profit >= 0 ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
		}
		data.setDirty();
		syncEveryone(server);
	}

	/** Saca o saldo inteiro da corretora como esmeraldas. */
	public static void withdraw(ServerPlayer player) {
		MarketData data = data(player.level().getServer());
		Account account = data.account(player.getUUID());
		int amount = (int) Math.floor(account.balance + 1e-9);
		if (amount <= 0) {
			return;
		}
		account.balance -= amount;
		data.setDirty();
		while (amount > 0) {
			int n = Math.min(amount, Items.EMERALD.getDefaultMaxStackSize());
			ItemStack stack = new ItemStack(Items.EMERALD, n);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			amount -= n;
		}
		player.containerMenu.broadcastChanges();
		sync(player, false);
	}

	private static void syncEveryone(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			sync(p, false);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Utilidades
	// ---------------------------------------------------------------------------------------------

	private static int emeralds(@Nullable ItemStack stack) {
		return stack != null && stack.is(Items.EMERALD) ? stack.getCount()
				: stack != null && stack.is(Items.EMERALD_BLOCK) ? stack.getCount() * 9 : 0;
	}

	private static int countEmeralds(Player player) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(Items.EMERALD)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void removeEmeralds(Player player, int amount) {
		for (int i = 0; i < player.getInventory().getContainerSize() && amount > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(Items.EMERALD)) {
				int take = Math.min(amount, stack.getCount());
				stack.shrink(take);
				amount -= take;
			}
		}
		player.containerMenu.broadcastChanges();
	}

	public static String fmt(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
