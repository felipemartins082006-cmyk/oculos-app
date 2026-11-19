package br.bolsavillager.market;

import br.bolsavillager.BolsaConfig;
import br.bolsavillager.mixin.RaidsAccessor;
import br.bolsavillager.net.MarketSyncPayload;
import br.bolsavillager.net.NotifyPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * O "pregão": junta tudo o que os villagers fazem (trocas, reposições, mortes, censo, raids) e
 * transforma em variação de preço a cada pregão ({@link BolsaConfig#sessionIntervalTicks}).
 *
 * <p>Modelo de preço (por ação, em log-retorno a cada pregão):
 * <pre>
 *   r = força_dos_eventos * pressão + reversão * ln(valor_justo / preço) + ruído(σ = volatilidade)
 *   preço = preço * e^r
 * </pre>
 * Também cuida das ordens programadas, do balcão onde jogadores vendem itens às guildas,
 * dos dividendos diários e dos avisos (que aparecem como notificação, nunca no chat).
 */
public final class MarketEngine {
	public static final long DAY_LENGTH = 24000L;
	public static final double MIN_PRICE = 0.25;
	public static final int MAX_ORDER = 64;
	/** Distância do centro do raid em que os villagers contam como "da vila atacada". */
	private static final double RAID_RADIUS = 96;

	private static final Map<StockDef, Double> pressure = new EnumMap<>(StockDef.class);
	private static final RandomSource random = RandomSource.create();
	/** Raids acompanhados: fase (1 = em andamento, 2 = encerrado) e guildas afetadas. */
	private static final Map<Raid, RaidWatch> raids = new WeakHashMap<>();
	private static int tickCounter;
	private static int raidCheckCounter;

	private static final class RaidWatch {
		int phase;
		Set<StockDef> guilds = EnumSet.noneOf(StockDef.class);
	}

	private MarketEngine() {
	}

	/** Limpa o estado em memória quando o servidor fecha (ex.: sair de um mundo e entrar em outro). */
	public static void reset() {
		pressure.clear();
		raids.clear();
		tickCounter = 0;
		raidCheckCounter = 0;
	}

	public static MarketData data(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(MarketData.TYPE);
	}

	/** Pressão acumulada desde o último pregão (para testes e diagnóstico). */
	public static double pendingPressure(StockDef def) {
		return pressure.getOrDefault(def, 0.0);
	}

	private static void addPressure(StockDef def, double amount) {
		pressure.merge(def, amount, Double::sum);
	}

	/** Nível do efeito Herói da Vila do jogador (0 = nível I), ou -1 se não tiver. */
	public static int heroLevel(@Nullable Player player) {
		if (player == null) {
			return -1;
		}
		MobEffectInstance effect = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
		return effect == null ? -1 : effect.getAmplifier();
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

		MarketData data = data(level.getServer());
		StockState state = data.stock(def);
		state.volumeDay += value;
		state.tradesDay++;
		data.setDirty();
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
	// Raids
	// ---------------------------------------------------------------------------------------------

	/** Acompanha os raids: começo derruba as guildas da vila, vitória dá alta, derrota derruba forte. */
	private static void checkRaids(MinecraftServer server) {
		BolsaConfig config = BolsaConfig.get();
		for (ServerLevel level : server.getAllLevels()) {
			for (Raid raid : ((RaidsAccessor) level.getRaids()).bolsavillager$getRaidMap().values()) {
				if (raid.isStopped()) {
					continue;
				}
				RaidWatch watch = raids.computeIfAbsent(raid, r -> new RaidWatch());
				if (watch.phase == 0 && raid.hasFirstWaveSpawned()) {
					watch.phase = 1;
					watch.guilds = guildsNear(level, raid);
					for (StockDef def : watch.guilds) {
						addPressure(def, -config.raidStartShock);
					}
					notifyLevel(level, NotifyPayload.RAID, Component.translatable("bolsavillager.raid.start.title"),
							Component.translatable("bolsavillager.raid.start.message"));
					syncEveryone(server);
				}
				if (watch.phase < 2 && raid.isOver()) {
					if (watch.phase == 0) {
						watch.guilds = guildsNear(level, raid);
					}
					watch.phase = 2;
					boolean victory = raid.isVictory();
					for (StockDef def : watch.guilds) {
						addPressure(def, victory ? config.raidVictoryRally : -config.raidLossCrash);
					}
					notifyLevel(level, NotifyPayload.RAID,
							Component.translatable(victory ? "bolsavillager.raid.victory.title" : "bolsavillager.raid.loss.title"),
							Component.translatable(victory ? "bolsavillager.raid.victory.message" : "bolsavillager.raid.loss.message"));
					syncEveryone(server);
				}
			}
		}
	}

	private static Set<StockDef> guildsNear(ServerLevel level, Raid raid) {
		Set<StockDef> guilds = EnumSet.noneOf(StockDef.class);
		AABB area = new AABB(raid.getCenter()).inflate(RAID_RADIUS);
		for (Villager v : level.getEntitiesOfClass(Villager.class, area, Villager::isAlive)) {
			StockDef def = StockDef.forEntity(v);
			if (def != null) {
				guilds.add(def);
			}
		}
		if (guilds.isEmpty()) {
			guilds.addAll(EnumSet.complementOf(EnumSet.of(StockDef.WANDERING_TRADER)));
		}
		return guilds;
	}

	public static boolean raidOngoing() {
		for (Map.Entry<Raid, RaidWatch> e : raids.entrySet()) {
			if (e.getValue().phase == 1 && !e.getKey().isOver() && !e.getKey().isStopped()) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// Pregão, ordens programadas e dividendos
	// ---------------------------------------------------------------------------------------------

	public static void onServerTick(MinecraftServer server) {
		if (++raidCheckCounter >= 20) {
			raidCheckCounter = 0;
			checkRaids(server);
		}
		if (++tickCounter < BolsaConfig.get().sessionIntervalTicks) {
			return;
		}
		tickCounter = 0;
		runSession(server);
	}

	/** Roda um pregão imediatamente (normalmente acontece sozinho a cada {@link BolsaConfig#sessionIntervalTicks}). */
	public static void runSession(MinecraftServer server) {
		BolsaConfig config = BolsaConfig.get();
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

		// Raid em andamento: medo contínuo nas guildas da vila atacada.
		for (Map.Entry<Raid, RaidWatch> e : raids.entrySet()) {
			if (e.getValue().phase == 1 && !e.getKey().isOver()) {
				for (StockDef def : e.getValue().guilds) {
					addPressure(def, -config.raidOngoingPressure);
				}
			}
		}

		// O dia que acabou paga dividendos com base no movimento dele (antes de zerar as estatísticas).
		if (newDay && data.day >= 0) {
			payDividends(server, data, data.day);
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

			double fair = MarketRules.fairValue(def, state.population);
			double r = config.eventStrength * pressure.getOrDefault(def, 0.0)
					+ config.meanReversion * Math.log(fair / state.price)
					+ random.nextGaussian() * config.volatility;
			r = Mth.clamp(r, -config.maxMovePerSession, config.maxMovePerSession);
			state.price = Math.max(MIN_PRICE, state.price * Math.exp(r));
			state.pushHistory(state.price);

			if (newDay) {
				state.dayOpen = state.price;
				state.volumeDay = 0;
				state.tradesDay = 0;
			}

			// Os villagers "sentem" o mercado: comemoram altas e reclamam de quedas.
			if (config.villagerParticles && Math.abs(r) >= 0.03) {
				for (Villager villager : loaded.getOrDefault(def, List.of())) {
					((ServerLevel) villager.level()).sendParticles(
							r > 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ANGRY_VILLAGER,
							villager.getX(), villager.getY() + villager.getBbHeight() + 0.3, villager.getZ(),
							r > 0 ? 6 : 2, 0.3, 0.2, 0.3, 0.0);
				}
			}
		}
		if (newDay) {
			data.soldToday().clear();
		}
		pressure.clear();
		data.day = day;
		processOrders(server, data);
		data.setDirty();
		syncEveryone(server);
	}

	/** Executa as ordens programadas cujo preço-alvo foi atingido (jogador online ou não). */
	private static void processOrders(MinecraftServer server, MarketData data) {
		MarketRules rules = MarketRules.current();
		for (Map.Entry<UUID, Account> entry : data.accounts().entrySet()) {
			Account account = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			int hero = heroLevel(player);
			Iterator<LimitOrder> it = account.orders.iterator();
			while (it.hasNext()) {
				LimitOrder order = it.next();
				StockDef def = order.stock();
				if (def == null) {
					it.remove();
					continue;
				}
				StockState state = data.stock(def);
				if (!order.type().triggered(state.price, order.target())) {
					continue;
				}
				double price = state.price;
				if (order.type().isBuy()) {
					double cost = rules.buyCost(price, order.quantity(), hero);
					if (account.balance + 1e-9 < cost) {
						continue; // sem saldo: a ordem espera
					}
					buy(account, state, def, order.quantity(), cost, rules);
					it.remove();
					notify(player, NotifyPayload.ORDER, Component.translatable("bolsavillager.order.done.title"),
							Component.translatable("bolsavillager.order.done.buy", order.quantity(), def.ticker, fmt(cost)));
				} else {
					int qty = Math.min(order.quantity(), account.sharesOf(def));
					it.remove();
					if (qty <= 0) {
						notify(player, NotifyPayload.ORDER, Component.translatable("bolsavillager.order.cancelled.title"),
								Component.translatable("bolsavillager.order.cancelled.no_shares", def.ticker));
						continue;
					}
					double proceeds = sell(account, state, def, qty, rules, hero);
					notify(player, NotifyPayload.ORDER, Component.translatable("bolsavillager.order.done.title"),
							Component.translatable("bolsavillager.order.done.sell", qty, def.ticker, fmt(proceeds)));
				}
			}
		}
	}

	/**
	 * Paga os dividendos do dia {@code day} a todos os acionistas (online ou não) e avisa quem está online
	 * com uma notificação. Retorna o total distribuído.
	 */
	public static double payDividends(MinecraftServer server, MarketData data, long day) {
		MarketRules rules = MarketRules.current();
		if (!rules.dividendsEnabled()) {
			return 0;
		}
		Map<StockDef, Double> perShare = new EnumMap<>(StockDef.class);
		for (StockDef def : StockDef.values()) {
			StockState s = data.stock(def);
			perShare.put(def, rules.dividendPerShare(def, s.price, s.population, s.tradesDay));
		}

		double distributed = 0;
		for (Map.Entry<UUID, Account> entry : data.accounts().entrySet()) {
			Account account = entry.getValue();
			double total = 0;
			StockDef best = null;
			double bestAmount = 0;
			for (StockDef def : StockDef.values()) {
				double amount = account.sharesOf(def) * perShare.get(def);
				total += amount;
				if (amount > bestAmount) {
					bestAmount = amount;
					best = def;
				}
			}
			if (total <= 0) {
				continue;
			}
			account.balance += total;
			account.lastDividend = total;
			account.lastDividendDay = day;
			account.totalDividends += total;
			distributed += total;

			notify(server.getPlayerList().getPlayer(entry.getKey()), NotifyPayload.DIVIDENDS,
					Component.translatable("bolsavillager.dividends.title", fmt(total)),
					Component.translatable("bolsavillager.dividends.message", best.ticker, fmt(bestAmount)));
		}
		data.setDirty();
		return distributed;
	}

	// ---------------------------------------------------------------------------------------------
	// Jogadores
	// ---------------------------------------------------------------------------------------------

	public static void sync(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, MarketSyncPayload.TYPE)) {
			MarketData data = data(player.level().getServer());
			ServerPlayNetworking.send(player, MarketSyncPayload.of(data, data.account(player.getUUID()),
					MarketRules.current(), raidOngoing()));
		}
	}

	public static void syncEveryone(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			sync(p);
		}
	}

	private static void notify(@Nullable ServerPlayer player, int kind, Component title, Component message) {
		if (player != null && ServerPlayNetworking.canSend(player, NotifyPayload.TYPE)) {
			ServerPlayNetworking.send(player, new NotifyPayload(kind, title, message));
		}
	}

	private static void notifyLevel(ServerLevel level, int kind, Component title, Component message) {
		for (ServerPlayer p : level.players()) {
			notify(p, kind, title, message);
		}
	}

	/** Mensagem curta na barra acima da hotbar (nada de chat). */
	private static void feedback(ServerPlayer player, Component message) {
		player.sendSystemMessage(message, true);
	}

	private static void buy(Account account, StockState state, StockDef def, int qty, double cost, MarketRules rules) {
		account.balance -= cost;
		account.shares.merge(def.id, qty, Integer::sum);
		account.costBasis.merge(def.id, cost, Double::sum);
		state.price *= Math.exp(rules.impactPerShare() * qty);
	}

	private static double sell(Account account, StockState state, StockDef def, int qty, MarketRules rules, int hero) {
		int owned = account.sharesOf(def);
		double proceeds = rules.sellProceeds(state.price, qty, hero);
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
		state.price = Math.max(MIN_PRICE, state.price * Math.exp(-rules.impactPerShare() * qty));
		return proceeds;
	}

	/**
	 * Compra/venda imediata de ações: quantity > 0 compra, quantity < 0 vende.
	 * Na compra, se o saldo não bastar, as esmeraldas saem da mochila. Retorna se executou.
	 */
	public static boolean executeTrade(ServerPlayer player, StockDef def, int quantity) {
		if (quantity == 0 || Math.abs(quantity) > MAX_ORDER) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		MarketRules rules = MarketRules.current();
		MarketData data = data(server);
		StockState state = data.stock(def);
		Account account = data.account(player.getUUID());
		int hero = heroLevel(player);
		int qty = Math.abs(quantity);

		if (quantity > 0) {
			double cost = rules.buyCost(state.price, qty, hero);
			if (account.balance < cost) {
				int needed = (int) Math.ceil(cost - account.balance - 1e-9);
				if (countEmeralds(player) < needed) {
					feedback(player, Component.translatable("bolsavillager.msg.no_funds", fmt(cost), def.ticker)
							.withStyle(ChatFormatting.RED));
					return false;
				}
				removeEmeralds(player, needed);
				account.balance += needed;
			}
			buy(account, state, def, qty, cost, rules);
			feedback(player, Component.translatable("bolsavillager.msg.bought", qty, def.ticker, fmt(cost))
					.withStyle(ChatFormatting.GREEN));
		} else {
			int owned = account.sharesOf(def);
			if (owned < qty) {
				feedback(player, Component.translatable("bolsavillager.msg.no_shares", def.ticker).withStyle(ChatFormatting.RED));
				return false;
			}
			double basisSold = account.costBasis.getOrDefault(def.id, 0.0) * qty / owned;
			double proceeds = sell(account, state, def, qty, rules, hero);
			double profit = proceeds - basisSold;
			feedback(player, Component.translatable("bolsavillager.msg.sold", qty, def.ticker, fmt(proceeds),
					(profit >= 0 ? "+" : "") + fmt(profit)).withStyle(profit >= 0 ? ChatFormatting.GREEN : ChatFormatting.GOLD));
		}
		data.setDirty();
		syncEveryone(server);
		return true;
	}

	public static void placeOrder(ServerPlayer player, LimitOrder order) {
		StockDef def = order.stock();
		if (def == null || order.quantity() < 1 || order.quantity() > MAX_ORDER
				|| !Double.isFinite(order.target()) || order.target() <= 0 || order.target() > 1e6) {
			return;
		}
		MarketData data = data(player.level().getServer());
		Account account = data.account(player.getUUID());
		if (account.orders.size() >= LimitOrder.MAX_PER_PLAYER) {
			feedback(player, Component.translatable("bolsavillager.order.too_many", LimitOrder.MAX_PER_PLAYER)
					.withStyle(ChatFormatting.RED));
			return;
		}
		account.orders.add(order);
		data.setDirty();
		feedback(player, Component.translatable("bolsavillager.order.created",
				Component.translatable(order.type().translationKey()), order.quantity(), def.ticker, fmt(order.target()))
				.withStyle(ChatFormatting.AQUA));
		sync(player);
	}

	public static void cancelOrder(ServerPlayer player, int index) {
		MarketData data = data(player.level().getServer());
		Account account = data.account(player.getUUID());
		if (index >= 0 && index < account.orders.size()) {
			account.orders.remove(index);
			data.setDirty();
			sync(player);
		}
	}

	/** Deposita esmeraldas da mochila no saldo (0 = todas). */
	public static int deposit(ServerPlayer player, int amount) {
		int available = countEmeralds(player);
		int n = amount <= 0 ? available : Math.min(amount, available);
		if (n <= 0) {
			return 0;
		}
		removeEmeralds(player, n);
		MarketData data = data(player.level().getServer());
		data.account(player.getUUID()).balance += n;
		data.setDirty();
		feedback(player, Component.translatable("bolsavillager.msg.deposited", n).withStyle(ChatFormatting.GREEN));
		sync(player);
		return n;
	}

	/** Saca o saldo inteiro da corretora como esmeraldas. Retorna quantas esmeraldas saíram. */
	public static int withdraw(ServerPlayer player) {
		MarketData data = data(player.level().getServer());
		Account account = data.account(player.getUUID());
		int amount = (int) Math.floor(account.balance + 1e-9);
		if (amount <= 0) {
			return 0;
		}
		account.balance -= amount;
		data.setDirty();
		int left = amount;
		while (left > 0) {
			int n = Math.min(left, Items.EMERALD.getDefaultMaxStackSize());
			ItemStack stack = new ItemStack(Items.EMERALD, n);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			left -= n;
		}
		player.containerMenu.broadcastChanges();
		feedback(player, Component.translatable("bolsavillager.msg.withdrew", amount).withStyle(ChatFormatting.GREEN));
		sync(player);
		return amount;
	}

	/**
	 * Balcão: o jogador vende itens da mochila para uma guilda, que decide quanto paga
	 * (prosperidade, tamanho da guilda, saturação do dia e Herói da Vila). Retorna o valor recebido.
	 */
	public static double sellItems(ServerPlayer player, Identifier itemId, StockDef guild, int quantity) {
		Item item = BuiltInRegistries.ITEM.getValue(itemId);
		if (item == null || item == Items.AIR || quantity <= 0) {
			return 0;
		}
		ItemDemand.Buyer buyer = ItemDemand.buyer(new ItemStack(item), guild);
		if (buyer == null) {
			return 0;
		}
		int owned = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item) && ItemDemand.buyer(stack, guild) != null) {
				owned += stack.getCount();
			}
		}
		int qty = Math.min(quantity, owned);
		if (qty <= 0) {
			return 0;
		}
		MinecraftServer server = player.level().getServer();
		MarketData data = data(server);
		StockState state = data.stock(guild);
		MarketRules rules = MarketRules.current();
		int sold = data.soldToday().getOrDefault(buyer.saturationKey(), 0);
		double total = rules.saleTotal(buyer, state.price, state.population, sold, qty, heroLevel(player));
		if (total <= 0) {
			feedback(player, Component.translatable("bolsavillager.sale.no_villagers",
					Component.translatable(guild.translationKey())).withStyle(ChatFormatting.RED));
			return 0;
		}

		int left = qty;
		for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item) && ItemDemand.buyer(stack, guild) != null) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		player.containerMenu.broadcastChanges();

		Account account = data.account(player.getUUID());
		account.balance += total;
		data.soldToday().merge(buyer.saturationKey(), qty, Integer::sum);
		state.volumeDay += Math.max(1, (int) Math.round(total));
		state.tradesDay++;
		addPressure(guild, Math.min(0.0015 * Math.max(1, total), 0.05));
		data.setDirty();

		feedback(player, Component.translatable("bolsavillager.sale.done", qty, new ItemStack(item).getHoverName(),
				Component.translatable(guild.translationKey()), fmt(total)).withStyle(ChatFormatting.GREEN));
		syncEveryone(server);
		return total;
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

	/** Número no formato brasileiro: vírgula como separador decimal. */
	public static String fmt(double value) {
		return String.format(Locale.ROOT, "%.2f", value).replace('.', ',');
	}
}
