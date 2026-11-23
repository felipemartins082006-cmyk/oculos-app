# Baixador de Vídeos

Programa para computador: cole o link de um vídeo, clique em **Baixar** e pronto.
Funciona com YouTube, Instagram, TikTok, X/Twitter, Facebook, Vimeo, Reddit e
[mais de mil sites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md).

## Como usar (Windows)

1. Instale o **Python**: https://www.python.org/downloads/
   (na instalação, marque **"Add Python to PATH"**).
2. (Recomendado) Instale o **FFmpeg** para qualidade máxima e MP3. Abra o
   Prompt de Comando e rode:
   ```
   winget install Gyan.FFmpeg
   ```
3. Dê dois cliques em **`iniciar-windows.bat`**.

Na janela: cole o link → escolha o formato → **Baixar**. O vídeo vai para a
pasta **Downloads** (dá para trocar em "Escolher…").

### Quer um `.exe`?
Dê dois cliques em **`gerar-exe-windows.bat`**. O programa fica em
`dist\BaixadorDeVideos.exe` — depois é só abrir esse arquivo direto.

## Mac / Linux

```
pip install -U yt-dlp
python3 baixador.py
```
(No Linux pode precisar de `sudo apt install python3-tk ffmpeg`.)

## Se um site parar de funcionar
Os sites mudam com frequência. Atualize o motor de download:
```
pip install -U yt-dlp
```

> Baixe apenas vídeos que você tem direito de baixar (seus, de domínio público
> ou com permissão do autor) e respeite os termos de uso de cada site.
