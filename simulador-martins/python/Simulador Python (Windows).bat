@echo off
chcp 65001 >nul
title Simulador Martins - motor Python
cd /d "%~dp0"

rem Procura o Python (instale em https://www.python.org/downloads/ marcando "Add python.exe to PATH")
where py >nul 2>nul && (set "PY=py -3") || (where python >nul 2>nul && (set "PY=python") || (
  echo.
  echo Python nao encontrado. Instale em https://www.python.org/downloads/
  echo e marque a opcao "Add python.exe to PATH". Depois abra este arquivo de novo.
  pause & exit /b 1))

rem Ambiente proprio na primeira vez (fica na pasta .venv)
if not exist ".venv\Scripts\python.exe" (
  echo Preparando o ambiente na primeira execucao, aguarde...
  %PY% -m venv .venv || (echo Falha ao criar o ambiente. & pause & exit /b 1)
  ".venv\Scripts\python.exe" -m pip install --upgrade pip >nul
  ".venv\Scripts\python.exe" -m pip install -r requirements.txt || (echo Falha ao instalar as bibliotecas. & pause & exit /b 1)
)
set "VPY=.venv\Scripts\python.exe"

:menu
cls
echo ================================================================
echo   SIMULADOR MARTINS - motor Python (usa todos os nucleos do PC)
echo ================================================================
echo   1 - Estudo: melhor carteira de FIIs de papel
echo   2 - Estudo: melhor carteira para chegar a R$ 3 milhoes
echo   3 - Rodar um estudo meu (arquivo .json)
echo   4 - Simulacao gigante (escolher numero de cenarios)
echo   5 - Atualizar todos os dados (internet, ~15 min)
echo   6 - Ver a base de dados
echo   7 - Superficies 3D + predicao intradiaria (escolher ativos)
echo   8 - Superficies 3D: melhor ativo e melhor configuracao (automatico)
echo   0 - Sair
echo.
set /p OP="Escolha: "
if "%OP%"=="1" ( "%VPY%" -m simulador estudo exemplos\fiis_papel.json && start "" "exemplos\saida\fiis_papel.html" & pause & goto menu )
if "%OP%"=="2" ( "%VPY%" -m simulador estudo exemplos\melhor_carteira_global.json && start "" "exemplos\saida\melhor_carteira_global.html" & pause & goto menu )
if "%OP%"=="3" (
  set /p ARQ="Caminho do arquivo .json: "
  call "%VPY%" -m simulador estudo "%%ARQ%%"
  pause & goto menu )
if "%OP%"=="4" (
  set /p N="Quantos cenarios (ex.: 100000000): "
  call "%VPY%" -m simulador simular exemplos\fiis_papel.json --cenarios %%N%%
  pause & goto menu )
if "%OP%"=="5" ( "%VPY%" -m pip install websocket-client >nul & "%VPY%" -m simulador atualizar & pause & goto menu )
if "%OP%"=="6" ( "%VPY%" -m simulador info & pause & goto menu )
if "%OP%"=="7" (
  set /p AT="Ativos separados por virgula (ex.: BTCUSDT,ETHUSDT,WIN): "
  set /p AL="Alvo em %% (ex.: 0.5): "
  set /p HZ="Horizonte em minutos (ex.: 30): "
  set /p SP="Stop em %% (ex.: 0.3): "
  call "%VPY%" -m superficie --ativos "%%AT%%" --alvo %%AL%% --horizonte %%HZ%% --stop %%SP%% --abrir
  pause & goto menu )
if "%OP%"=="8" ( "%VPY%" -m superficie --ativos auto --alvo 0.3,0.5,0.8 --stop 0.3,0.5 --horizonte 15,30,60 --abrir & pause & goto menu )
if "%OP%"=="0" exit /b 0
goto menu
