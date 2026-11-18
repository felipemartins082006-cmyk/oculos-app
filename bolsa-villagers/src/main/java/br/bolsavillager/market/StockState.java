package br.bolsavillager.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/** Estado salvo de uma ação: cotação, abertura do dia, histórico e estatísticas do pregão. */
public final class StockState {
	/** Quantos pregões (pontos) o histórico guarda. Com um pregão a cada 30 s, são 60 minutos. */
	public static final int HISTORY_SIZE = 120;

	public static final Codec<StockState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.fieldOf("price").forGetter(s -> s.price),
			Codec.DOUBLE.fieldOf("day_open").forGetter(s -> s.dayOpen),
			Codec.DOUBLE.listOf().fieldOf("history").forGetter(s -> s.history),
			Codec.INT.optionalFieldOf("population", 0).forGetter(s -> s.population),
			Codec.INT.optionalFieldOf("volume_day", 0).forGetter(s -> s.volumeDay),
			Codec.INT.optionalFieldOf("trades_day", 0).forGetter(s -> s.tradesDay)
	).apply(i, StockState::new));

	public double price;
	public double dayOpen;
	public final List<Double> history;
	/** Villagers dessa profissão no censo. */
	public int population;
	/** Esmeraldas movimentadas em trocas com villagers hoje. */
	public int volumeDay;
	/** Número de trocas com villagers hoje. */
	public int tradesDay;

	public StockState(double price, double dayOpen, List<Double> history, int population, int volumeDay, int tradesDay) {
		this.price = price;
		this.dayOpen = dayOpen;
		this.history = new ArrayList<>(history);
		this.population = population;
		this.volumeDay = volumeDay;
		this.tradesDay = tradesDay;
	}

	public static StockState fresh(StockDef def) {
		List<Double> history = new ArrayList<>();
		history.add(def.basePrice);
		return new StockState(def.basePrice, def.basePrice, history, 0, 0, 0);
	}

	public void pushHistory(double value) {
		this.history.add(value);
		while (this.history.size() > HISTORY_SIZE) {
			this.history.removeFirst();
		}
	}
}
