"""Geradores de cenários mensais.

Todos trabalham sobre X (L meses × D colunas). Há dois tipos:
- reamostragem (devolvem índices de meses históricos, B × N): bootstrap, blocos fixos, estacionário;
- paramétricos (devolvem valores, B × N × D): normal, t-Student, GARCH-FHS, regimes.
"""
from __future__ import annotations

import numpy as np
from scipy import optimize, stats


class Modelo:
    reamostragem = False
    nome = ""

    def ajustar(self, X: np.ndarray) -> "Modelo":
        self.X = np.asarray(X, float)
        self.L, self.D = self.X.shape
        return self

    def descricao(self) -> str:
        return self.nome


# ---------------------------------------------------------------- reamostragem
class Bootstrap(Modelo):
    """Sorteia meses históricos independentes (o mesmo mês para todas as colunas)."""
    reamostragem = True
    nome = "Bootstrap mensal"

    def indices(self, B, N, rng):
        return rng.integers(0, self.L, size=(B, N))


class Blocos(Modelo):
    """Blocos de tamanho fixo (circular): preserva sequências de crise e memória dos juros."""
    reamostragem = True

    def __init__(self, tamanho: int = 12):
        self.tamanho = tamanho
        self.nome = f"Bootstrap em blocos de {tamanho} meses"

    def indices(self, B, N, rng):
        nb = -(-N // self.tamanho)
        ini = rng.integers(0, self.L, size=(B, nb, 1))
        idx = (ini + np.arange(self.tamanho)) % self.L
        return idx.reshape(B, -1)[:, :N]


class Estacionario(Modelo):
    """Bootstrap estacionário (Politis-Romano): blocos de tamanho geométrico com média `media`."""
    reamostragem = True

    def __init__(self, media: float = 12):
        self.media = media
        self.nome = f"Bootstrap estacionário (blocos médios de {media:g} meses)"

    def indices(self, B, N, rng):
        idx = np.empty((B, N), dtype=np.int64)
        idx[:, 0] = rng.integers(0, self.L, B)
        novo = rng.random((B, N)) < 1 / self.media
        salto = rng.integers(0, self.L, (B, N))
        for t in range(1, N):
            idx[:, t] = np.where(novo[:, t], salto[:, t], (idx[:, t - 1] + 1) % self.L)
        return idx


# ---------------------------------------------------------------- paramétricos
class Normal(Modelo):
    nome = "Normal multivariada"

    def ajustar(self, X):
        super().ajustar(X)
        self.mu = self.X.mean(0)
        self.C = np.linalg.cholesky(np.cov(self.X.T) + 1e-12 * np.eye(self.D))
        return self

    def gerar(self, B, N, rng):
        z = rng.standard_normal((B, N, self.D))
        return np.maximum(self.mu + z @ self.C.T, -0.95)


class TStudent(Normal):
    """t-Student multivariada com a mesma covariância histórica (caudas gordas)."""

    def __init__(self, gl: float = 5):
        self.gl = gl
        self.nome = f"t-Student multivariada ({gl:g} graus de liberdade)"

    def gerar(self, B, N, rng):
        z = rng.standard_normal((B, N, self.D))
        w = np.sqrt((self.gl - 2) / rng.chisquare(self.gl, size=(B, N, 1)))
        return np.maximum(self.mu + (z * w) @ self.C.T, -0.95)


def _garch_ajuste(r):
    """GARCH(1,1) gaussiano com média constante, por máxima verossimilhança."""
    mu = r.mean(); e = r - mu; v0 = e.var()

    def nll(p):
        w, a, b = p
        if w <= 0 or a < 0 or b < 0 or a + b >= 0.999:
            return 1e10
        s2 = np.empty_like(e); s2[0] = v0
        for t in range(1, len(e)):
            s2[t] = w + a * e[t - 1] ** 2 + b * s2[t - 1]
        return 0.5 * np.sum(np.log(s2) + e ** 2 / s2)

    res = optimize.minimize(nll, [v0 * 0.1, 0.1, 0.8], method="Nelder-Mead", options={"maxiter": 2000, "xatol": 1e-8, "fatol": 1e-8})
    w, a, b = res.x
    if not (w > 0 and a >= 0 and b >= 0 and a + b < 0.999):
        w, a, b = v0, 0.0, 0.0
    s2 = np.empty_like(e); s2[0] = v0
    for t in range(1, len(e)):
        s2[t] = w + a * e[t - 1] ** 2 + b * s2[t - 1]
    return mu, w, a, b, s2, e / np.sqrt(s2)


class GarchFHS(Modelo):
    """GARCH(1,1) por coluna + simulação histórica filtrada: a volatilidade muda com o tempo
    (agrupamento de volatilidade) e os choques vêm dos resíduos padronizados reais, sorteados no
    mesmo mês para todas as colunas (mantém a correlação). Colunas `fixas` (CDI, IPCA) são reamostradas."""
    nome = "GARCH(1,1) + simulação histórica filtrada"

    def __init__(self, fixas: tuple[int, ...] = ()):
        self.fixas = set(fixas)

    def ajustar(self, X):
        super().ajustar(X)
        self.par, self.Z = [], np.zeros_like(self.X)
        for d in range(self.D):
            if d in self.fixas:
                self.par.append(None); continue
            mu, w, a, b, s2, z = _garch_ajuste(self.X[:, d])
            self.par.append((mu, w, a, b, s2[-1], self.X[-1, d] - mu))
            self.Z[:, d] = z
        return self

    def descricao(self):
        p = [f"α={x[2]:.2f} β={x[3]:.2f}" for x in self.par if x]
        return f"{self.nome} ({'; '.join(p)})"

    def gerar(self, B, N, rng):
        idx = rng.integers(0, self.L, size=(B, N))
        out = np.empty((B, N, self.D))
        for d in range(self.D):
            if self.par[d] is None:
                out[:, :, d] = self.X[idx, d]; continue
            mu, w, a, b, s2_ult, e_ult = self.par[d]
            s2 = np.full(B, w + a * e_ult ** 2 + b * s2_ult)
            z = self.Z[idx, d]
            for t in range(N):
                e = np.sqrt(s2) * z[:, t]
                out[:, t, d] = mu + e
                s2 = w + a * e ** 2 + b * s2
        return np.maximum(out, -0.95)


class Regimes(Modelo):
    """Mudança de regime (cadeia de Markov oculta com K estados gaussianos multivariados),
    estimada por EM (Baum-Welch). Tipicamente: um regime calmo e um de crise, com médias,
    volatilidades e correlações diferentes, e a chance de trocar de um para o outro a cada mês."""

    def __init__(self, k: int = 2, iteracoes: int = 200):
        self.k, self.it = k, iteracoes
        self.nome = f"Mudança de regime ({k} estados)"

    def ajustar(self, X):
        super().ajustar(X)
        L, D, K = self.L, self.D, self.k
        X = self.X
        # início: divide pela volatilidade da 1ª coluna
        ordem = np.argsort(np.abs(X[:, 0] - X[:, 0].mean()))
        grupos = np.array_split(ordem, K)
        mu = np.array([X[g].mean(0) for g in grupos])
        cov = np.array([np.cov(X[g].T) + 1e-6 * np.eye(D) for g in grupos])
        A = np.full((K, K), 0.1 / (K - 1)); np.fill_diagonal(A, 0.9)
        pi = np.full(K, 1 / K)
        ll_ant = -np.inf
        for _ in range(self.it):
            dens = np.column_stack([stats.multivariate_normal(mu[k], cov[k], allow_singular=True).pdf(X) for k in range(K)]) + 1e-300
            alfa = np.zeros((L, K)); c = np.zeros(L)
            alfa[0] = pi * dens[0]; c[0] = alfa[0].sum(); alfa[0] /= c[0]
            for t in range(1, L):
                alfa[t] = (alfa[t - 1] @ A) * dens[t]; c[t] = alfa[t].sum(); alfa[t] /= c[t]
            beta = np.ones((L, K))
            for t in range(L - 2, -1, -1):
                beta[t] = (A @ (dens[t + 1] * beta[t + 1])) / c[t + 1]
            gama = alfa * beta; gama /= gama.sum(1, keepdims=True)
            xi = np.zeros((K, K))
            for t in range(L - 1):
                m = np.outer(alfa[t], dens[t + 1] * beta[t + 1]) * A / c[t + 1]
                xi += m / m.sum()
            pi = gama[0]
            A = xi / xi.sum(1, keepdims=True)
            for k in range(K):
                g = gama[:, k]; sg = g.sum()
                mu[k] = g @ X / sg
                dx = X - mu[k]
                cov[k] = (g[:, None] * dx).T @ dx / sg + 1e-8 * np.eye(D)
            ll = np.log(c).sum()
            if abs(ll - ll_ant) < 1e-7:
                break
            ll_ant = ll
        o = np.argsort([np.sqrt(cov[k][0, 0]) for k in range(K)])  # estado 0 = mais calmo
        self.mu, self.cov, self.A = mu[o], cov[o], A[np.ix_(o, o)]
        self.p_final = alfa[-1][o]
        self.freq = gama.mean(0)[o]
        self.chol = np.array([np.linalg.cholesky(c_) for c_ in self.cov])
        return self

    def descricao(self):
        partes = []
        for k in range(self.k):
            dur = 1 / max(1e-9, 1 - self.A[k, k])
            partes.append(f"estado {k + 1}: {self.freq[k]:.0%} do tempo, dura ~{dur:.0f} meses, vol. mensal {np.sqrt(self.cov[k][0, 0]):.1%}")
        return f"{self.nome}: " + "; ".join(partes)

    def gerar(self, B, N, rng):
        K, D = self.k, self.D
        est = rng.choice(K, size=B, p=self.p_final / self.p_final.sum())
        cumA = np.cumsum(self.A, axis=1)
        out = np.empty((B, N, D))
        for t in range(N):
            u = rng.random(B)
            est = (u[:, None] > cumA[est]).sum(1)
            z = rng.standard_normal((B, D))
            for k in range(K):
                m = est == k
                if m.any():
                    out[m, t] = self.mu[k] + z[m] @ self.chol[k].T
        return np.maximum(out, -0.95)


def criar(nome: str, **kw) -> Modelo:
    nome = nome.lower()
    if nome in ("bootstrap", "boot"):
        return Bootstrap()
    if nome in ("blocos", "bloco"):
        return Blocos(kw.get("bloco", 12))
    if nome in ("estacionario", "estacionário"):
        return Estacionario(kw.get("bloco", 12))
    if nome == "normal":
        return Normal()
    if nome in ("t", "tstudent", "t-student"):
        return TStudent(kw.get("gl", 5))
    if nome in ("garch", "garch-fhs"):
        return GarchFHS(kw.get("fixas", ()))
    if nome in ("regimes", "markov", "hmm"):
        return Regimes(kw.get("estados", 2))
    raise ValueError(f"Modelo desconhecido: {nome}")


MODELOS = ["bootstrap", "blocos", "estacionario", "normal", "t", "garch", "regimes"]
