# Mundo Martins

Simulador de mundo **para celular**, original: você é um deus que molda o terreno e cria povos,
acompanha a evolução deles da **Idade da Pedra até a Era Contemporânea** e pode, a qualquer
momento, **viver como uma dessas pessoas em primeira pessoa**.

É um app web instalável (igual ao Finanças): abra o endereço no celular e toque em **Instalar**
(Android/Chrome) ou em **Compartilhar → Adicionar à Tela de Início** (iPhone/Safari).
Depois da primeira abertura ele funciona sem internet.

Endereço (depois que esta pasta estiver na branch publicada do GitHub Pages):
https://felipemartins082006-cmyk.github.io/oculos-app/mundo-martins/

## Como jogar

**Modo deus** (barra de baixo):

| Ferramenta | O que faz |
|---|---|
| ✋ Olhar | Arraste para mover, pinça para zoom, gire com dois dedos. Toque numa cidade ou pessoa para ver detalhes. |
| 👁️ 1ª pessoa | Toque numa pessoa para viver como ela. |
| 🧑 Povo | Toque na terra para criar um povo novo (perto de uma cidade, chegam moradores). |
| ⛰️ Erguer / 🌊 Afundar | Arraste para criar montanhas, ilhas, lagos e mares. |
| 🌳 Floresta | Arraste para plantar árvores. |
| 💡 Inspirar | Dá um empurrão na ciência do povo da cidade tocada. |
| ⚡ Raio / ☄️ Meteoro | Destruição: incêndios, crateras e mortes. |

No topo: velocidade (pausa, 1×, 3×, 10×), 📜 povos e eras (com a linha do tempo) e ⚙️ ajustes.

**Primeira pessoa**: arraste do lado esquerdo da tela para andar (empurrar até o fim corre) e do
lado direito para olhar. O botão amarelo muda conforme o que está à sua frente:
cortar árvore, colher, entregar recursos no centro da cidade, construir uma casa ou atacar um inimigo
em guerra. ⤒ pula, 🔄 troca para outro morador, 🌍 volta a ser deus.
No computador: W A S D para andar, Shift corre, espaço pula, E faz a ação, Esc sai.

## Evolução dos povos

Cada povo junta ciência pela população, escolas e fábricas e passa por 8 eras. Cada era muda as
construções, as roupas, as armas e o dia a dia:

1. 🪨 **Idade da Pedra**: cabanas, fogueira com totem, lanças.
2. 🏺 **Idade do Bronze**: fazendas com plantações, casas de barro.
3. 🏛️ **Antiguidade**: casas de pedra com telhas, templos com colunas, palácio, estradas de terra.
4. 🏰 **Idade Média**: castelos com torres, igrejas, casas de enxaimel, espadas e elmos.
5. 📜 **Renascimento**: sobrados de tijolo, cúpulas, bibliotecas, ruas de pedra.
6. 🏭 **Era Industrial**: fábricas com chaminés soltando fumaça, prefeitura com torre do relógio.
7. 🚗 **Era Moderna**: prédios, asfalto, postes, carros, luz elétrica (janelas acesas à noite).
8. 📱 **Era Contemporânea**: arranha-céus de vidro, painéis solares, turbinas eólicas, gente no celular.

As cidades crescem, mandam colonos fundar cidades novas, reformam os prédios antigos para o estilo
da era nova, entram em guerra com vizinhos, conquistam cidades e às vezes uma cidade distante
declara independência e vira um povo novo. Na velocidade 1×, um povo leva cerca de 20 a 25 minutos
para chegar à era atual (no 10×, uns 2 a 3 minutos).

## Desempenho (feito para celular)

- Gráficos em WebGL2 com sombreamento facetado calculado na placa de vídeo, sem guardar normais:
  cada vértice ocupa só 16 bytes.
- O mapa é dividido em 64 pedaços; só o pedaço que mudou é remontado (no máximo alguns por quadro).
- Pessoas distantes viram um único bloco (4× menos geometria); na 1ª pessoa só se desenha o que
  está perto e à frente, com névoa escondendo o limite.
- A resolução cai sozinha quando o celular não aguenta a taxa de quadros e sobe de novo quando sobra fôlego.
- A simulação usa uma grade espacial para achar vizinhos, então custa menos de 1 ms por passo
  mesmo com centenas de pessoas.
- Em ⚙️ Ajustes: **Leve** (30 fps, menos gente, névoa mais perto), **Normal** e **Bonito**.
  Dá para desligar o ciclo de dia e noite e mostrar o contador de FPS.

O mundo é salvo sozinho no próprio celular a cada minuto e quando o app vai para segundo plano.

## Arquivos

- `js/world.js`: simulação (terreno, povos, cidades, eras, guerras, fogo, salvar/carregar). Roda também no Node.
- `js/render.js`: motor WebGL2 e montagem de malhas.
- `js/meshes.js`: modelos do terreno, das árvores, dos prédios de cada era e das pessoas.
- `js/main.js`: câmera, toque, poderes, primeira pessoa, interface e laço principal.
