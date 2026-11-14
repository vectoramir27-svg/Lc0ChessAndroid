@echo off
chcp 65001 >nul
title Отправка Lc0ChessAndroid на GitHub
echo =======================================================
echo    Отправка проекта на GitHub (vectoramir27-svg)
echo =======================================================
echo.
echo 1. Убедитесь, что вы создали репозиторий на:
echo    https://github.com/new
echo    (Рекомендуемое имя: Lc0ChessAndroid)
echo.
set /p REPO_NAME="Введите имя репозитория [по умолчанию Lc0ChessAndroid]: "
if "%REPO_NAME%"=="" set REPO_NAME=Lc0ChessAndroid

echo.
echo Подключаем https://github.com/vectoramir27-svg/%REPO_NAME%.git ...
git remote remove origin 2>nul
git remote add origin https://github.com/vectoramir27-svg/%REPO_NAME%.git
git branch -M master

echo.
echo Отправляем файлы на GitHub...
git push -u origin master

if %ERRORLEVEL% EQU 0 (
    echo.
    echo =======================================================
    echo [УСПЕХ] Проект отправлен на GitHub!
    echo.
    echo Сборка APK началась автоматически во вкладке Actions:
    echo https://github.com/vectoramir27-svg/%REPO_NAME%/actions
    echo =======================================================
) else (
    echo.
    echo [ВНИМАНИЕ] Если репозиторий еще не создан на GitHub,
    echo сначала создайте его на https://github.com/new
)
echo.
pause
