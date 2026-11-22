package com.martins.trocas.mercador;

/**
 * Resultado da avaliação do Mercador para os itens colocados no balcão.
 *
 * @param status             situação da proposta
 * @param esmeraldas         esmeraldas oferecidas (0 se não há proposta)
 * @param usados             quantos itens serão usados para pagar essas esmeraldas
 * @param total              quantos itens foram colocados
 * @param faltam             INSUFICIENTE: itens que faltam para 1 esmeralda; OK: itens que faltam para a próxima esmeralda
 * @param itensPorEsmeralda  quantos itens valem 1 esmeralda (0 se 1 item vale 1 esmeralda ou mais)
 * @param esmeraldasPorItemX10 quanto 1 item vale, em décimos de esmeralda (só quando 1 item vale 1 esmeralda ou mais)
 */
public record Cotacao(Status status, int esmeraldas, int usados, int total, int faltam, int itensPorEsmeralda,
                      int esmeraldasPorItemX10) {

	public static final Cotacao VAZIA = new Cotacao(Status.VAZIO, 0, 0, 0, 0, 0, 0);

	public enum Status {
		VAZIO, MISTURADO, NAO_COMPRA, INSUFICIENTE, OK;

		public static Status of(int i) {
			Status[] v = values();
			return i >= 0 && i < v.length ? v[i] : VAZIO;
		}
	}

	public static Cotacao simples(Status status, int total) {
		return new Cotacao(status, 0, 0, total, 0, 0, 0);
	}

	public boolean ok() {
		return status == Status.OK && esmeraldas > 0;
	}
}
