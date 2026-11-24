package com.martins.estruturas;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Cada estrutura do mod, com o tamanho (largura x altura x profundidade) que ocupa. */
public enum TipoEstrutura implements StringRepresentable {
    TORRE_DO_MAGO("torre_do_mago", 9, 24, 9),
    RUINAS_ANTIGAS("ruinas_antigas", 13, 7, 13),
    CABANA_DO_CACADOR("cabana_do_cacador", 9, 8, 11);

    public static final Codec<TipoEstrutura> CODEC = StringRepresentable.fromEnum(TipoEstrutura::values);

    public final String nome;
    public final int largura, altura, profundidade;

    TipoEstrutura(String nome, int largura, int altura, int profundidade) {
        this.nome = nome;
        this.largura = largura;
        this.altura = altura;
        this.profundidade = profundidade;
    }

    @Override
    public String getSerializedName() {
        return nome;
    }

    public static TipoEstrutura porNome(String nome) {
        for (TipoEstrutura t : values()) if (t.nome.equals(nome)) return t;
        return TORRE_DO_MAGO;
    }
}
