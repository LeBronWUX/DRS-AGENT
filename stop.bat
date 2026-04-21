@echo off
chcp 65001 >nul
REM DRS Agent 停止脚本 (Windows)

echo ========================================
echo DRS Agent 停止脚本
echo ========================================

echo 正在查找Java进程...

REM 查找并杀死Java进程
for /f "tokens=5" %%a in ('tasklist /fi "imagename eq java.exe" /fo table ^| findstr "java.exe"') do (
    echo 找到进程 PID: %%a
    taskkill /f /pid %%a >nul 2>&1
)

echo.
echo 服务已停止
echo.

pause