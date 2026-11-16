@echo off
chcp 65001 >nul
title Збірка Омастротривога v2.0 для Windows (.exe)
echo ========================================================
echo   Збірка «Омастротривога ПК v2.0» у standalone .exe
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

echo [1/4] Перевірка логіки та всіх 12 аудіофайлів...
python -m unittest -v test_omastro_pc.py
if errorlevel 1 (
    echo [X] Помилка перевірки тестів!
    pause
    exit /b 1
)

echo [2/4] Оновлення pip та встановлення PyInstaller...
python -m pip install --upgrade pip pyinstaller
if errorlevel 1 (
    echo [X] Не вдалося встановити PyInstaller. Перевірте інтернет-з'єднання.
    pause
    exit /b 1
)

echo [3/4] Збірка OmastroTrivoga.exe з Google входом та вбудованими голосами...
python -m PyInstaller --noconfirm --clean --onefile --windowed ^
    --name "OmastroTrivoga" ^
    --add-data "..\app\src\main\res\raw;assets\audio" ^
    omastro_trivoga_pc.py

if errorlevel 1 (
    echo [X] Помилка збірки .exe!
    pause
    exit /b 1
)

echo [4/4] Готово!
echo Ваш файл створено: %~dp0dist\OmastroTrivoga.exe
start "" "%~dp0dist"
pause
