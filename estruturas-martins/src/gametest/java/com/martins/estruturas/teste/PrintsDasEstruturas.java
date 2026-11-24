package com.martins.estruturas.teste;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Cria um mundo, coloca cada estrutura e tira prints de fora e de dentro. */
public class PrintsDasEstruturas implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext mundo = ctx.worldBuilder().create()) {
            var s = mundo.getServer();
            s.runCommand("gamemode spectator @a");
            s.runCommand("time set 1000");
            s.runCommand("weather clear");
            s.runCommand("gamerule advance_time false");

            ctx.waitTicks(220); // deixa a mensagem do chat sumir

            // Torre do Mago no chunk (0,0), Ruínas no (10,0), Cabana no (20,0).
            // Teleporta antes de colocar, para os chunks estarem carregados.
            colocar(ctx, s, "torre_do_mago", 8, 8);
            foto(ctx, s, "1_torre_do_mago", "-13 -44 -13 facing 4 -50 4");
            foto(ctx, s, "2_torre_do_mago_topo", "6 -44.4 6 facing 2 -44 2");
            colocar(ctx, s, "ruinas_antigas", 168, 8);
            foto(ctx, s, "3_ruinas_antigas", "152 -50 -6 facing 166 -60 6");
            colocar(ctx, s, "cabana_do_cacador", 328, 8);
            foto(ctx, s, "4_cabana_do_cacador", "318 -52 -9 facing 324 -57 5");
            foto(ctx, s, "5_cabana_do_cacador_fundos", "334 -52 22 facing 324 -57 5");
        }
    }

    private static void colocar(ClientGameTestContext ctx,
                                net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext s,
                                String estrutura, int x, int z) {
        s.runCommand("tp @a " + x + " -40 " + z);
        ctx.waitTicks(60);
        s.runCommand("place structure estruturas:" + estrutura + " " + x + " 0 " + z);
    }

    private static void foto(ClientGameTestContext ctx,
                             net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext s,
                             String nome, String posicao) {
        s.runCommand("tp @a " + posicao);
        ctx.waitTicks(120); // espera os chunks carregarem e renderizarem
        ctx.takeScreenshot(nome);
    }
}
