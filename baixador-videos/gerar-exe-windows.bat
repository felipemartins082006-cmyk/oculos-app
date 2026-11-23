@echo off
cd /d "%~dp0"
python -m pip install -U -r requirements.txt pyinstaller
python -m PyInstaller --onefile --windowed --name BaixadorDeVideos baixador.py
echo.
echo Pronto! O programa esta em: dist\BaixadorDeVideos.exe
pause
