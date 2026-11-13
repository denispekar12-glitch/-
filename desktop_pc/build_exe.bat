@echo off
chcp 65001 >nul
title Збірка Омастротривога для Windows (.exe)
echo ========================================================
echo   Збірка «Омастротривога» у standalone-файл .exe
echo ========================================================

cd /d "%~dp0"

where python >nul 2>nul
if errorlevel 1 (
    echo [!] Python не знайдено. Спроба автоматичного встановлення через winget...
    winget install --id Python.Python.3.12 -e --source winget --accept-package-agreements --accept-source-agreements
    if errorlevel 1 (
        echo [X] Будь ласка, встановіть Python 3.10+ з https://www.python.org/downloads/ та позначте "Add Python to PATH".
        pause
        exit /b 1
    )
)

echo [1/3] Оновлення pip та встановлення PyInstaller...
python -m pip install --upgrade pip pyinstaller
if errorlevel 1 (
    echo [X] Не вдалося встановити PyInstaller. Перевірте інтернет-з'єднання.
    pause
    exit /b 1
)

echo [2/3] Збірка OmastroTrivoga.exe з вбудованими голосовими сигналами...
python -m PyInstaller --noconfirm --clean --onefile --windowed ^
    --name "OmastroTrivoga" ^
    --add-data "..\app\src\main\res\raw;assets\audio" ^
    omastro_trivoga_pc.py

if errorlevel 1 (
    echo [X] Помилка збірки .exe!
    pause
    exit /b 1
)

echo [3/3] Готово!
echo Ваш файл створено: %~dp0dist\OmastroTrivoga.exe
start "" "%~dp0dist"
pause
