# Estruturas Martins — mod para Minecraft 26.2 (Fabric)

Adiciona 3 estruturas novas que aparecem sozinhas quando você explora mundos novos:

| Estrutura | Onde aparece | O que tem |
|---|---|---|
| **Torre do Mago** | planícies, prados e florestas | 3 andares, biblioteca, mesa de encantamento e baú de tesouro no topo |
| **Ruínas Antigas** | florestas, savanas, selvas, planícies e deserto | colunas quebradas, teias e um **baú escondido embaixo do altar** |
| **Cabana do Caçador** | taigas, planícies nevadas e bosques | varanda com lanternas, fornalha, bancada e baú |

## Como instalar

1. Instale o **Fabric Loader** para a 26.2: https://fabricmc.net/use/installer/
2. Baixe o **Fabric API** para a 26.2: https://modrinth.com/mod/fabric-api
3. Coloque `estruturas-martins-1.0.0.jar` e o Fabric API na pasta `.minecraft/mods`.
4. Abra o jogo com o perfil do Fabric e **crie um mundo novo** (as estruturas só aparecem em terreno ainda não gerado).

## Testar rápido (modo criativo com cheats)

```
/place structure estruturas:torre_do_mago
/place structure estruturas:ruinas_antigas
/place structure estruturas:cabana_do_cacador
/locate structure estruturas:torre_do_mago
```

## Compilar você mesmo

Precisa de Java 25.

```
./gradlew build        # o .jar fica em build/libs/
./gradlew runClient    # abre o Minecraft com o mod para testar
```

## Como criar mais estruturas

- `src/main/java/com/martins/estruturas/PecaMartins.java` — cada estrutura é um método que coloca os blocos (x = largura, y = altura, z = profundidade, z=0 é a frente).
- `TipoEstrutura.java` — adicione o novo tipo com o tamanho.
- `src/main/resources/data/estruturas/` — JSONs que dizem em quais biomas aparece (`tags/...`) e a frequência (`structure_set`: `spacing` maior = mais rara).
