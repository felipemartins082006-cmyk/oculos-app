"""Gera as texturas do mod Trocas Martins (pixel art procedural). Uso: python3 tools/gerar_texturas.py"""
import os, random
from PIL import Image

RAIZ = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'trocasmartins', 'textures')
random.seed(42)

def hexc(h, a=255):
    h = h.lstrip('#'); return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

def salvar(img, *caminho):
    p = os.path.join(RAIZ, *caminho); os.makedirs(os.path.dirname(p), exist_ok=True); img.save(p)

# ------------------------------------------------------------------ GUI
def gui():
    img = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    W, H = 176, 192
    px = img.load()
    PRETO, BRANCO, ESC, FUNDO = hexc('000000'), hexc('FFFFFF'), hexc('555555'), hexc('C6C6C6')
    for y in range(H):
        for x in range(W):
            px[x, y] = FUNDO
    # contorno preto com cantos arredondados
    for x in range(2, W - 2): px[x, 0] = PRETO; px[x, H - 1] = PRETO
    for y in range(2, H - 2): px[0, y] = PRETO; px[W - 1, y] = PRETO
    for (x, y) in [(1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)]: px[x, y] = PRETO
    for (x, y) in [(0, 0), (1, 0), (0, 1), (W - 1, 0), (W - 2, 0), (W - 1, 1), (0, H - 1), (1, H - 1), (0, H - 2),
                   (W - 1, H - 1), (W - 2, H - 1), (W - 1, H - 2)]: px[x, y] = (0, 0, 0, 0)
    # brilho (cima/esquerda) e sombra (baixo/direita)
    for x in range(2, W - 3):
        px[x, 1] = BRANCO; px[x, 2] = BRANCO
    for y in range(2, H - 3):
        px[1, y] = BRANCO; px[2, y] = BRANCO
    for x in range(3, W - 2):
        px[x, H - 2] = ESC; px[x, H - 3] = ESC
    for y in range(3, H - 2):
        px[W - 2, y] = ESC; px[W - 3, y] = ESC
    px[W - 3, 2] = FUNDO; px[2, H - 3] = FUNDO

    def slot(x0, y0, w=18, h=18):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                px[x, y] = hexc('8B8B8B')
        for x in range(x0, x0 + w - 1): px[x, y0] = hexc('373737')
        for y in range(y0, y0 + h - 1): px[x0, y] = hexc('373737')
        for x in range(x0 + 1, x0 + w): px[x, y0 + h - 1] = BRANCO
        for y in range(y0 + 1, y0 + h): px[x0 + w - 1, y] = BRANCO

    def painel(x0, y0, w, h, fundo='2E2A24'):
        slot(x0, y0, w, h)
        for y in range(y0 + 1, y0 + h - 1):
            for x in range(x0 + 1, x0 + w - 1):
                px[x, y] = hexc(fundo)

    for l in range(3):
        for c in range(3):
            slot(29 + c * 18, 16 + l * 18)
    slot(119, 30, 26, 26)
    for l in range(3):
        for c in range(9):
            slot(7 + c * 18, 109 + l * 18)
    for c in range(9):
        slot(7 + c * 18, 167)
    painel(89, 59, 81, 13)         # campo "Faltam"
    painel(7, 74, 162, 24)         # linha de informações

    def seta(ox, oy, cor):
        # seta 22x15 no estilo da mesa de trabalho
        for y in range(15):
            for x in range(22):
                dentro = (5 <= y <= 9 and x <= 14) or (x >= 14 and abs(y - 7) <= (21 - x))
                if dentro: img.putpixel((ox + x, oy + y), cor)
    seta(90, 35, hexc('8B8B8B'))
    seta(176, 0, hexc('3FCF5A'))   # seta verde usada quando a proposta é válida
    # pequena esmeralda decorativa no título da área de venda
    salvar(img, 'gui', 'mercador.png')

# ------------------------------------------------------------------ bloco
MAD = ['6B4A2B', '7A5532', '8A6139', '9C6E41', 'A97B4A']
def madeira(img, x0, y0, w, h, base=1):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            i = base + random.choice([0, 0, 0, 1, -1])
            if (y - y0) % 4 == 3: i = max(0, base - 1)
            img.putpixel((x, y), hexc(MAD[max(0, min(4, i))]))

def bloco():
    OURO, OURO2 = hexc('F2C94C'), hexc('C99A2E')
    VERDE = [hexc('0E6B2E'), hexc('17A345'), hexc('41D46B'), hexc('9BF0B0')]
    ESC = hexc('3E2A16')
    # lado: tábuas + faixa dourada
    lado = Image.new('RGBA', (16, 16)); madeira(lado, 0, 0, 16, 16, 2)
    for x in range(16): lado.putpixel((x, 0), ESC); lado.putpixel((x, 15), ESC); lado.putpixel((x, 3), OURO2); lado.putpixel((x, 2), OURO)
    for y in range(16): lado.putpixel((0, y), ESC); lado.putpixel((15, y), ESC)
    salvar(lado, 'block', 'balcao_do_mercador_side.png')
    # frente: painel com uma esmeralda
    frente = lado.copy()
    for y in range(5, 14):
        for x in range(3, 13): frente.putpixel((x, y), hexc(MAD[0]))
    for x in range(3, 13): frente.putpixel((x, 5), ESC); frente.putpixel((x, 13), hexc(MAD[3]))
    gema = ["...##...", "..#++#..", ".#++oo#.", "#+++o++#", ".#++++#.", "..#++#..", "...##..."]
    cores = {'#': VERDE[0], '+': VERDE[1], 'o': VERDE[3]}
    for j, linha in enumerate(gema):
        for i, ch in enumerate(linha):
            if ch in cores: frente.putpixel((4 + i, 6 + j), cores[ch])
    salvar(frente, 'block', 'balcao_do_mercador_front.png')
    # topo: balcão com pano verde e moedas
    topo = Image.new('RGBA', (16, 16)); madeira(topo, 0, 0, 16, 16, 3)
    for y in range(3, 13):
        for x in range(3, 13):
            topo.putpixel((x, y), VERDE[0] if (x + y) % 5 == 0 else VERDE[1])
    for x in range(3, 13): topo.putpixel((x, 3), OURO2); topo.putpixel((x, 12), OURO2)
    for y in range(3, 13): topo.putpixel((3, y), OURO2); topo.putpixel((12, y), OURO2)
    for (x, y) in [(5, 5), (6, 5), (5, 6), (9, 8), (10, 8), (9, 9), (10, 9), (6, 9)]: topo.putpixel((x, y), OURO)
    topo.putpixel((7, 6), VERDE[3]); topo.putpixel((7, 7), VERDE[2])
    for x in range(16): topo.putpixel((x, 0), ESC); topo.putpixel((x, 15), ESC)
    for y in range(16): topo.putpixel((0, y), ESC); topo.putpixel((15, y), ESC)
    salvar(topo, 'block', 'balcao_do_mercador_top.png')
    icone = frente.resize((128, 128), Image.NEAREST)
    p = os.path.join(RAIZ, '..', 'icon.png'); icone.save(p)

# ------------------------------------------------------------------ roupa do aldeão (camada da profissão, 64x64)
def aldeao():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    VERDE, VERDE2, VERDE3 = hexc('1F7A3A'), hexc('2E9E4F'), hexc('145226')
    OURO, OURO2 = hexc('F2C94C'), hexc('B8862B')
    MARROM, MARROM2 = hexc('5A3A1E'), hexc('74502B')
    def ret(x0, y0, w, h, cor):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w): img.putpixel((x, y), cor)
    # chapéu (camada externa da cabeça: deslocamento 32,0, caixa 8x10x8)
    ret(40, 0, 8, 8, MARROM)                     # topo
    for x0 in (32, 40, 48, 56):                  # 4 lados, 3 linhas de cima
        ret(x0, 8, 8, 3, MARROM2)
        ret(x0, 11, 8, 1, VERDE)                 # faixa verde
    img.putpixel((43, 11), OURO); img.putpixel((44, 11), OURO)  # fivela
    # aba do chapéu (deslocamento 30,47, 16x16x1)
    ret(31, 47, 16, 16, MARROM)
    ret(33, 49, 12, 12, (0, 0, 0, 0))
    # colete por cima do corpo (corpo: 16,20 caixa 8x12x6)
    ret(22, 26, 8, 12, VERDE)                    # frente
    ret(25, 26, 2, 12, hexc('D9CBA3'))           # camisa aparecendo no meio
    for y in (28, 31, 34): img.putpixel((24, y), OURO)
    ret(16, 26, 6, 12, VERDE3); ret(30, 26, 6, 12, VERDE3); ret(36, 26, 8, 12, VERDE)
    # túnica (jaqueta: 0,38 caixa 8x20x6)
    ret(6, 44, 8, 20, VERDE2)                    # frente
    ret(6, 44, 8, 1, OURO2)
    ret(9, 45, 2, 19, VERDE3)                    # abertura
    ret(6, 56, 8, 1, OURO); ret(6, 63, 8, 1, OURO2)
    ret(0, 44, 6, 20, VERDE); ret(14, 44, 6, 20, VERDE); ret(20, 44, 8, 20, VERDE2)
    ret(6, 38, 8, 6, VERDE3); ret(14, 38, 8, 6, VERDE3)
    # bolsinha de moedas na cintura
    ret(11, 57, 2, 3, MARROM); img.putpixel((11, 57), OURO)
    # mangas (braços cruzados: 44,22 e 40,38)
    ret(44, 26, 16, 4, VERDE); ret(44, 29, 16, 1, OURO2)
    ret(40, 42, 16, 1, OURO2)
    salvar(img, 'entity', 'villager', 'profession', 'mercador.png')
    salvar(img, 'entity', 'zombie_villager', 'profession', 'mercador.png')

gui(); bloco(); aldeao()
print('texturas geradas')
