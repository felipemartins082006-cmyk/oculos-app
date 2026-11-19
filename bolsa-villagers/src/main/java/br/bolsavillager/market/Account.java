package br.bolsavillager.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conta de um jogador na bolsa. Não existe saldo virtual: compras saem direto das esmeraldas da mochila
 * e vendas/dividendos voltam como esmeraldas. Só o que chega com o jogador offline (ordem executada,
 * dividendo) fica guardado em {@link #pending} e é entregue assim que ele entra no mundo.
 */
public final class Account {
	public static final Codec<Account> CODEC = RecordCodecBuilder.create(i -> i.group(
			// "balance" é de versões antigas (saldo com centavos): vira esmeraldas a receber.
			Codec.DOUBLE.optionalFieldOf("balance", 0.0).forGetter(a -> 0.0),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("shares", Map.of()).forGetter(a -> a.shares),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("cost_basis", Map.of()).forGetter(Account::costBasisAsDouble),
			Codec.INT.optionalFieldOf("pending", 0).forGetter(a -> a.pending),
			Codec.DOUBLE.optionalFieldOf("dividend_carry", 0.0).forGetter(a -> a.dividendCarry),
			Codec.DOUBLE.optionalFieldOf("last_dividend", 0.0).forGetter(a -> (double) a.lastDividend),
			Codec.LONG.optionalFieldOf("last_dividend_day", -1L).forGetter(a -> a.lastDividendDay),
			Codec.DOUBLE.optionalFieldOf("total_dividends", 0.0).forGetter(a -> (double) a.totalDividends),
			LimitOrder.CODEC.listOf().optionalFieldOf("orders", List.of()).forGetter(a -> a.orders)
	).apply(i, Account::new));

	/** Ações por id de empresa. */
	public final Map<String, Integer> shares;
	/** Esmeraldas pagas pelas ações que ainda estão na carteira (para preço médio e lucro). */
	public final Map<String, Integer> costBasis;
	/** Esmeraldas esperando para serem entregues (chegaram com o jogador offline ou com a mochila cheia). */
	public int pending;
	/**
	 * Fração de dividendo acumulada que ainda não fechou 1 esmeralda. Fica escondida: o jogador só
	 * vê e recebe esmeraldas inteiras, mas poucas ações também acabam rendendo com o tempo.
	 */
	public double dividendCarry;
	/** Esmeraldas de dividendo recebidas na última virada de dia com pagamento. */
	public int lastDividend;
	public long lastDividendDay;
	/** Soma de todos os dividendos já recebidos. */
	public int totalDividends;
	/** Ordens programadas ainda não executadas. */
	public final List<LimitOrder> orders;

	private Account(double legacyBalance, Map<String, Integer> shares, Map<String, Double> costBasis, int pending,
			double dividendCarry, double lastDividend, long lastDividendDay, double totalDividends, List<LimitOrder> orders) {
		this.shares = new HashMap<>(shares);
		this.costBasis = new HashMap<>();
		costBasis.forEach((k, v) -> this.costBasis.put(k, (int) Math.round(v)));
		this.pending = pending + (int) Math.floor(legacyBalance + 1e-9);
		this.dividendCarry = dividendCarry;
		this.lastDividend = (int) Math.round(lastDividend);
		this.lastDividendDay = lastDividendDay;
		this.totalDividends = (int) Math.round(totalDividends);
		this.orders = new ArrayList<>(orders);
	}

	public Account() {
		this(0.0, Map.of(), Map.of(), 0, 0.0, 0.0, -1L, 0.0, List.of());
	}

	private Map<String, Double> costBasisAsDouble() {
		Map<String, Double> map = new HashMap<>();
		this.costBasis.forEach((k, v) -> map.put(k, v.doubleValue()));
		return map;
	}

	public int sharesOf(StockDef def) {
		return this.shares.getOrDefault(def.id, 0);
	}

	/** Esmeraldas presas em ordens de compra programadas. */
	public int reserved() {
		int total = 0;
		for (LimitOrder o : this.orders) {
			total += o.reserved();
		}
		return total;
	}
}
