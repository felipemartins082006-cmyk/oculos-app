"""
Baixador de Vídeos — cole o link de um site e o vídeo é baixado automaticamente.

Usa o yt-dlp, que funciona com YouTube, Instagram, TikTok, X/Twitter,
Facebook, Vimeo, Reddit e mais de mil outros sites.
"""

import os
import queue
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

try:
    import yt_dlp
except ImportError:
    yt_dlp = None

PASTA_PADRAO = str(Path.home() / "Downloads")

FORMATOS = {
    "Vídeo (melhor qualidade)": "video",
    "Vídeo 720p": "720",
    "Somente áudio (MP3)": "mp3",
}


def ffmpeg_embutido():
    """Pasta do FFmpeg que vem dentro do .exe (quando existe)."""
    import sys
    base = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    nome = "ffmpeg.exe" if os.name == "nt" else "ffmpeg"
    return base if os.path.isfile(os.path.join(base, nome)) else None


def tem_ffmpeg() -> bool:
    from shutil import which
    return ffmpeg_embutido() is not None or which("ffmpeg") is not None


def opcoes_formato(escolha: str) -> dict:
    """Monta as opções do yt-dlp para o formato escolhido."""
    ffmpeg = tem_ffmpeg()
    if escolha == "mp3":
        opts = {"format": "bestaudio/best"}
        if ffmpeg:
            opts["postprocessors"] = [{
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "192",
            }]
        return opts
    if escolha == "720":
        if ffmpeg:
            return {"format": "bv*[height<=720]+ba/b[height<=720]/b",
                    "merge_output_format": "mp4"}
        return {"format": "b[height<=720]/b"}
    # melhor qualidade
    if ffmpeg:
        return {"format": "bv*+ba/b", "merge_output_format": "mp4"}
    # sem ffmpeg só dá pra baixar arquivos que já vêm com áudio+vídeo juntos
    return {"format": "b"}


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Baixador de Vídeos")
        self.geometry("620x360")
        self.minsize(520, 340)

        self.fila = queue.Queue()
        self.baixando = False

        self.var_link = tk.StringVar()
        self.var_pasta = tk.StringVar(value=PASTA_PADRAO)
        self.var_formato = tk.StringVar(value=list(FORMATOS)[0])
        self.var_status = tk.StringVar(value="Cole um link e clique em Baixar.")

        self._montar_tela()
        self.after(100, self._processar_fila)

        if yt_dlp is None:
            messagebox.showerror(
                "Falta o yt-dlp",
                "Instale a dependência com:\n\npip install -U yt-dlp",
            )

    # ---------- interface ----------
    def _montar_tela(self):
        pad = {"padx": 12, "pady": 6}
        frm = ttk.Frame(self)
        frm.pack(fill="both", expand=True, padx=8, pady=8)
        frm.columnconfigure(0, weight=1)

        ttk.Label(frm, text="Link do vídeo:", font=("", 11, "bold")).grid(
            row=0, column=0, sticky="w", **pad)

        linha = ttk.Frame(frm)
        linha.grid(row=1, column=0, sticky="ew", padx=12)
        linha.columnconfigure(0, weight=1)
        self.entrada = ttk.Entry(linha, textvariable=self.var_link, font=("", 11))
        self.entrada.grid(row=0, column=0, sticky="ew", ipady=4)
        self.entrada.bind("<Return>", lambda e: self.baixar())
        ttk.Button(linha, text="Colar", command=self.colar).grid(
            row=0, column=1, padx=(6, 0))

        opc = ttk.Frame(frm)
        opc.grid(row=2, column=0, sticky="ew", **pad)
        opc.columnconfigure(1, weight=1)
        ttk.Label(opc, text="Formato:").grid(row=0, column=0, sticky="w")
        ttk.Combobox(opc, textvariable=self.var_formato, values=list(FORMATOS),
                     state="readonly", width=26).grid(row=0, column=1, sticky="w", padx=6)

        ttk.Label(opc, text="Salvar em:").grid(row=1, column=0, sticky="w", pady=(8, 0))
        ttk.Entry(opc, textvariable=self.var_pasta).grid(
            row=1, column=1, sticky="ew", padx=6, pady=(8, 0))
        ttk.Button(opc, text="Escolher…", command=self.escolher_pasta).grid(
            row=1, column=2, pady=(8, 0))

        self.botao = ttk.Button(frm, text="⬇  Baixar", command=self.baixar)
        self.botao.grid(row=3, column=0, sticky="ew", padx=12, pady=(12, 6), ipady=6)

        self.barra = ttk.Progressbar(frm, maximum=100)
        self.barra.grid(row=4, column=0, sticky="ew", **pad)

        ttk.Label(frm, textvariable=self.var_status, wraplength=560,
                  justify="left").grid(row=5, column=0, sticky="w", **pad)

        ttk.Button(frm, text="Abrir pasta", command=self.abrir_pasta).grid(
            row=6, column=0, sticky="e", padx=12)

        if not tem_ffmpeg():
            ttk.Label(
                frm, foreground="#a15c00", wraplength=560,
                text="Dica: instale o FFmpeg para baixar na qualidade máxima e "
                     "converter para MP3.",
            ).grid(row=7, column=0, sticky="w", **pad)

        self.entrada.focus()

    # ---------- ações ----------
    def colar(self):
        try:
            self.var_link.set(self.clipboard_get().strip())
        except tk.TclError:
            pass

    def escolher_pasta(self):
        pasta = filedialog.askdirectory(initialdir=self.var_pasta.get())
        if pasta:
            self.var_pasta.set(pasta)

    def abrir_pasta(self):
        pasta = self.var_pasta.get()
        try:
            if os.name == "nt":
                os.startfile(pasta)
            elif os.uname().sysname == "Darwin":
                os.system(f'open "{pasta}"')
            else:
                os.system(f'xdg-open "{pasta}"')
        except Exception as e:
            messagebox.showerror("Erro", str(e))

    def baixar(self):
        if self.baixando or yt_dlp is None:
            return
        link = self.var_link.get().strip()
        if not link.startswith(("http://", "https://")):
            messagebox.showwarning("Link inválido", "Cole um link começando com http:// ou https://")
            return
        pasta = self.var_pasta.get().strip() or PASTA_PADRAO
        os.makedirs(pasta, exist_ok=True)

        self.baixando = True
        self.botao.config(state="disabled")
        self.barra["value"] = 0
        self.var_status.set("Buscando informações do vídeo…")
        formato = FORMATOS[self.var_formato.get()]
        threading.Thread(target=self._trabalho, args=(link, pasta, formato),
                         daemon=True).start()

    # ---------- download (em segundo plano) ----------
    def _trabalho(self, link, pasta, formato):
        def progresso(d):
            if d["status"] == "downloading":
                total = d.get("total_bytes") or d.get("total_bytes_estimate")
                feito = d.get("downloaded_bytes", 0)
                pct = (feito / total * 100) if total else 0
                vel = d.get("_speed_str", "").strip()
                eta = d.get("_eta_str", "").strip()
                self.fila.put(("progresso", pct, f"Baixando… {pct:.0f}%  {vel}  faltam {eta}"))
            elif d["status"] == "finished":
                self.fila.put(("progresso", 100, "Finalizando arquivo…"))

        opts = {
            "outtmpl": os.path.join(pasta, "%(title).150B [%(id)s].%(ext)s"),
            "noplaylist": True,
            "progress_hooks": [progresso],
            "quiet": True,
            "noprogress": True,
            "no_warnings": True,
            "windowsfilenames": True,
        }
        if ffmpeg_embutido():
            opts["ffmpeg_location"] = ffmpeg_embutido()
        opts.update(opcoes_formato(formato))

        try:
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(link, download=True)
            titulo = info.get("title", "vídeo") if info else "vídeo"
            self.fila.put(("fim", True, f"✅ Pronto! \"{titulo}\" salvo em {pasta}"))
        except Exception as e:
            msg = str(e).replace("ERROR: ", "")
            self.fila.put(("fim", False, f"❌ Não foi possível baixar: {msg}"))

    def _processar_fila(self):
        try:
            while True:
                item = self.fila.get_nowait()
                if item[0] == "progresso":
                    self.barra["value"] = item[1]
                    self.var_status.set(item[2])
                elif item[0] == "fim":
                    self.baixando = False
                    self.botao.config(state="normal")
                    self.var_status.set(item[2])
                    if item[1]:
                        self.barra["value"] = 100
                        self.var_link.set("")
        except queue.Empty:
            pass
        self.after(100, self._processar_fila)


if __name__ == "__main__":
    App().mainloop()
