package br.bolsavillager.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Conta de um jogador na corretora: saldo em esmeraldas (com centavos) e carteira de ações. */
public final class Account {
	public static final Codec<Account> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("balance", 0.0).forGetter(a -> a.balance),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("shares", Map.of()).forGetter(a -> a.shares),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("cost_basis", Map.of()).forGetter(a -> a.costBasis),
			Codec.DOUBLE.optionalFieldOf("last_dividend", 0.0).forGetter(a -> a.lastDividend),
			Codec.LONG.optionalFieldOf("last_dividend_day", -1L).forGetter(a -> a.lastDividendDay),
			Codec.DOUBLE.optionalFieldOf("total_dividends", 0.0).forGetter(a -> a.totalDividends),
			LimitOrder.CODEC.listOf().optionalFieldOf("orders", List.of()).forGetter(a -> a.orders)
	).apply(i, Account::new));

	public double balance;
	/** Ações por id de empresa. */
	public final Map<String, Integer> shares;
	/** Total pago pelas ações que ainda estão na carteira (para calcular preço médio e lucro). */
	public final Map<String, Double> costBasis;
	/** Dividendos recebidos na última virada de dia em que houve pagamento. */
	public double lastDividend;
	public long lastDividendDay;
	/** Soma de todos os dividendos já recebidos. */
	public double totalDividends;
	/** Ordens programadas ainda não executadas. */
	public final List<LimitOrder> orders;

	public Account(double balance, Map<String, Integer> shares, Map<String, Double> costBasis,
			double lastDividend, long lastDividendDay, double totalDividends, List<LimitOrder> orders) {
		this.balance = balance;
		this.shares = new HashMap<>(shares);
		this.costBasis = new HashMap<>(costBasis);
		this.lastDividend = lastDividend;
		this.lastDividendDay = lastDividendDay;
		this.totalDividends = totalDividends;
		this.orders = new ArrayList<>(orders);
	}

	public Account() {
		this(0.0, Map.of(), Map.of(), 0.0, -1L, 0.0, List.of());
	}

	public int sharesOf(StockDef def) {
		return this.shares.getOrDefault(def.id, 0);
	}
}
