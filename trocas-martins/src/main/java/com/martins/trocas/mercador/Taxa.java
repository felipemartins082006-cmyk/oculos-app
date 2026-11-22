package com.martins.trocas.mercador;

/** Texto curto que descreve a taxa de compra do Mercador. */
public final class Taxa {
	private Taxa() {
	}

	public static String descrever(Cotacao c) {
		if (c.itensPorEsmeralda() > 0) {
			return c.itensPorEsmeralda() == 1 ? "1 item = 1 esmeralda" : c.itensPorEsmeralda() + " itens = 1 esmeralda";
		}
		if (c.esmeraldasPorItemX10() > 0) {
			int x = c.esmeraldasPorItemX10();
			String valor = x % 10 == 0 ? String.valueOf(x / 10) : (x / 10) + "," + (x % 10);
			return "1 item = " + valor + " esmeralda(s)";
		}
		return "";
	}
}
