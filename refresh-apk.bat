@echo off
chcp 65001 >nul
title 智趣笔记 · 刷新公网地址并重编 APK
cd /d "%~dp0"
setlocal enabledelayedexpansion

echo ============================================
echo   刷新公网地址 -^> 重新编译 APK
echo ============================================
echo.
echo 这会把最新的公网地址编译进 APK，装到任何手机上开箱即用。
echo 注意：需要先让隧道跑在一个单独窗口里（运行 start.bat）。
echo.

set "URL="
echo 请粘贴 start.bat 窗口里打印的地址（形如 https://xxx.trycloudflare.com）：
set /p URL="> "

if "%URL%"=="" (
  echo [!] 地址为空，已取消
  pause & exit /b 1
)

echo.
echo [1/2] 校验地址连通性 ...
powershell -NoProfile -Command "try { $r = Invoke-RestMethod '%URL%/api/health' -TimeoutSec 15; Write-Host ('   OK  users=' + $r.users + ' notes=' + $r.notes) -ForegroundColor Green } catch { Write-Host '   地址不可达，请确认隧道在运行' -ForegroundColor Red; exit 1 }"
if errorlevel 1 ( pause & exit /b 1 )

echo.
echo [2/2] 重新编译 APK ...
call gradlew.bat :app:assembleDebug -Psmartnotes.server=%URL% --console=plain
if errorlevel 1 (
  echo [!] 编译失败
  pause & exit /b 1
)

echo.
echo ============================================
echo   完成
echo   APK: app\build\outputs\apk\debug\app-debug.apk
echo   云服务地址: %URL%
echo.
echo   安装到手机：
echo     adb install -r app\build\outputs\apk\debug\app-debug.apk
echo ============================================
pause
