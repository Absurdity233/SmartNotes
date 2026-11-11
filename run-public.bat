@echo off
chcp 65001 >nul
title 智趣笔记 · 公网服务（Cloudflare Tunnel）
cd /d "%~dp0"

set "CF=%~dp0tools\bin\cloudflared.exe"
if not exist "%CF%" (
  echo [!] 未找到 tools\bin\cloudflared.exe
  echo     下载地址: https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe
  pause
  exit /b 1
)

echo.
echo  [1/2] 启动本地云服务 (端口 8787)...
start "SmartNotes Server" /min cmd /c "cd /d %~dp0server && node server.js"

echo  [2/2] 启动公网隧道...
echo.
echo  稍等片刻，下面 "https://xxxx.trycloudflare.com" 就是公网地址，
echo  把它填进 App 的「我的 → 云服务地址」，或构建时用 -Psmartnotes.server= 注入。
echo  按 Ctrl+C 可结束隧道。
echo.
"%CF%" tunnel --url http://127.0.0.1:8787 --no-autoupdate

pause
