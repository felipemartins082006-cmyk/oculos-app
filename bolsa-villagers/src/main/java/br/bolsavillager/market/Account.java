package br.bolsavillager.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/** Conta de um jogador na corretora: saldo em esmeraldas (com centavos) e carteira de ações. */
public final class Account {
	public static final Codec<Account> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("balance", 0.0).forGetter(a -> a.balance),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("shares", Map.of()).forGetter(a -> a.shares),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("cost_basis", Map.of()).forGetter(a -> a.costBasis)
	).apply(i, Account::new));

	public double balance;
	/** Ações por id de empresa. */
	public final Map<String, Integer> shares;
	/** Total pago pelas ações que ainda estão na carteira (para calcular preço médio e lucro). */
	public final Map<String, Double> costBasis;

	public Account(double balance, Map<String, Integer> shares, Map<String, Double> costBasis) {
		this.balance = balance;
		this.shares = new HashMap<>(shares);
		this.costBasis = new HashMap<>(costBasis);
	}

	public Account() {
		this(0.0, Map.of(), Map.of());
	}

	public int sharesOf(StockDef def) {
		return this.shares.getOrDefault(def.id, 0);
	}
}
