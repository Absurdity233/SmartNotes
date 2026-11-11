@echo off
chcp 65001 >nul
title 智趣笔记 · 一键公网启动
cd /d "%~dp0"

echo ============================================
echo   智趣笔记 - 一键启动（本地服务 + 公网隧道）
echo ============================================
echo.

rem ---- 1. 本地云服务 ----
netstat -ano | findstr ":8787" | findstr LISTENING >nul
if errorlevel 1 (
  echo [1/3] 启动本地云服务 8787 ...
  start "SmartNotes Server" /min cmd /c "cd /d %~dp0server && node server.js"
  timeout /t 3 >nul
) else (
  echo [1/3] 云服务已在运行 ^(8787^)
)

rem ---- 2. 公网隧道 ----
set "CF=%~dp0tools\bin\cloudflared.exe"
if not exist "%CF%" (
  echo [!] 缺少 tools\bin\cloudflared.exe
  echo     下载: https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe
  pause & exit /b 1
)

echo [2/3] 建立公网隧道（约 10 秒）...
echo.
echo     请记下下面出现的 https://xxxx.trycloudflare.com 地址
echo     按 Ctrl+C 结束隧道
echo.
"%CF%" tunnel --url http://127.0.0.1:8787 --no-autoupdate --protocol http2
