@echo off
chcp 65001 >nul
REM DRS Agent 一键打包脚本
REM 构建前端并打包到后端jar中

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set BACKEND_DIR=%SCRIPT_DIR%
set FRONTEND_DIR=%SCRIPT_DIR%\..\drs-agent-frontend
set DEPLOY_DIR=%SCRIPT_DIR%\..\drs-agent-deploy

echo ========================================
echo DRS Agent 一键打包
echo ========================================

REM 1. 构建前端
echo.
echo [1/4] 构建前端...
cd /d "%FRONTEND_DIR%"
call npm run build
if errorlevel 1 (
    echo 前端构建失败!
    exit /b 1
)

REM 2. 复制前端到后端resources
echo.
echo [2/4] 复制前端文件到后端...
if exist "%BACKEND_DIR%\src\main\resources\frontend" rd /s /q "%BACKEND_DIR%\src\main\resources\frontend"
mkdir "%BACKEND_DIR%\src\main\resources\frontend"
xcopy /e /i /y dist "%BACKEND_DIR%\src\main\resources\frontend\"

REM 3. 构建后端
echo.
echo [3/4] 构建后端...
cd /d "%BACKEND_DIR%"
call mvn clean package -DskipTests
if errorlevel 1 (
    echo 后端构建失败!
    exit /b 1
)

REM 4. 创建部署包
echo.
echo [4/4] 创建部署包...
if not exist "%DEPLOY_DIR%" mkdir "%DEPLOY_DIR%"
copy /y target\drs-agent-backend-1.0.0-SNAPSHOT.jar "%DEPLOY_DIR%\"
copy /y "%SCRIPT_DIR%\start.sh" "%DEPLOY_DIR%\"
copy /y "%SCRIPT_DIR%\start.bat" "%DEPLOY_DIR%\"
copy /y "%SCRIPT_DIR%\stop.sh" "%DEPLOY_DIR%\"
copy /y "%SCRIPT_DIR%\stop.bat" "%DEPLOY_DIR%\"

echo.
echo ========================================
echo 打包完成!
echo ========================================
echo 部署包位置: %DEPLOY_DIR%
echo.
echo 文件列表:
dir "%DEPLOY_DIR%"
echo.
echo 启动方式:
echo   Linux/Mac: ./start.sh
echo   Windows:   start.bat
echo.

endlocal