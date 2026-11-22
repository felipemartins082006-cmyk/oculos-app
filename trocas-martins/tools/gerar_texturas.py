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
    """Roupa do Mercador: capuz e manto no estilo do mascate, mas verde-azulado com acabamento creme,
    cinto de couro e uma esmeralda na fivela. Sombreado pixel a pixel como as texturas do jogo."""
    random.seed(7)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    MANTO = [hexc('1E4D4A'), hexc('24605B'), hexc('2A6E68'), hexc('327B74')]
    MANTO_ESC = [hexc('163A38'), hexc('1B4643'), hexc('205350')]
    CREME = [hexc('C9B98F'), hexc('D8C9A0'), hexc('E3D6B0')]
    COURO = [hexc('4A3020'), hexc('5A3B27'), hexc('6B482F')]
    def tom(paleta, x, y):
        r = random.random()
        i = 0 if r < 0.2 else (len(paleta) - 1 if r > 0.85 else 1 + int(random.random() * (len(paleta) - 2)) if len(paleta) > 2 else 1)
        return paleta[min(i, len(paleta) - 1)]
    def ret(x0, y0, w, h, paleta):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                img.putpixel((x, y), tom(paleta, x, y))
    def px(x, y, paleta):
        img.putpixel((x, y), tom(paleta, x, y))

    # capuz (camada externa da cabeça, 32,0): cobre tudo, menos o rosto
    ret(40, 0, 8, 8, MANTO)           # topo
    ret(48, 0, 8, 8, MANTO_ESC)       # baixo
    ret(32, 8, 8, 10, MANTO)          # lado direito
    ret(48, 8, 8, 10, MANTO)          # lado esquerdo
    ret(56, 8, 8, 10, MANTO_ESC)      # nuca
    ret(40, 8, 8, 2, MANTO)           # testa
    for y in range(10, 18):           # moldura do rosto
        px(40, y, CREME); px(47, y, CREME)
    for x in range(40, 48):
        px(x, 10, CREME)
    # corpo (16,20) e manto (0,38)
    ret(16, 20, 28, 18, MANTO)
    ret(22, 20, 8, 6, MANTO_ESC)
    ret(0, 38, 28, 26, MANTO)
    ret(6, 38, 8, 6, MANTO_ESC); ret(14, 38, 8, 6, MANTO_ESC)
    ret(20, 44, 8, 20, MANTO_ESC)     # costas do manto mais escuras
    for y in range(26, 38):           # gola/abertura na frente do corpo
        px(25, y, CREME); px(26, y, CREME)
    for x in range(22, 30):
        px(x, 26, CREME)
    for y in range(44, 64):           # abertura do manto
        px(9, y, CREME); px(10, y, CREME)
    for x in list(range(0, 28)):      # barra creme
        px(x, 63, CREME); px(x, 62, CREME)
    # cinto de couro com fivela de esmeralda
    for x in range(0, 28):
        px(x, 50, COURO); px(x, 51, COURO)
    img.putpixel((9, 50), hexc('3FCF6A')); img.putpixel((10, 50), hexc('17A345'))
    img.putpixel((9, 51), hexc('17A345')); img.putpixel((10, 51), hexc('0E6B2E'))
    # bolsinha no quadril
    ret(12, 52, 2, 3, COURO)
    # mangas (braços cruzados) com punhos creme
    ret(44, 22, 16, 12, MANTO)
    ret(40, 38, 24, 8, MANTO)
    for x in range(44, 60):
        px(x, 33, CREME)
    for x in range(40, 64):
        px(x, 45, CREME)
    salvar(img, 'entity', 'villager', 'profession', 'mercador.png')
    salvar(img, 'entity', 'zombie_villager', 'profession', 'mercador.png')

aldeao()
print('texturas geradas')
