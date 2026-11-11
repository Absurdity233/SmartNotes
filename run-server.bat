@echo off
chcp 65001 >nul
title 智趣笔记 · 云服务
cd /d "%~dp0server"
echo.
echo  正在启动智趣笔记云服务...
echo  请确保已安装 Node.js 22.5 或更高版本
echo.
node server.js
pause
