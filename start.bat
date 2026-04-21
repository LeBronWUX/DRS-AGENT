@echo off
chcp 65001 >nul
REM DRS Agent 一键启动脚本 (Windows)

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set JAR_FILE=%SCRIPT_DIR%drs-agent-backend-1.0.0-SNAPSHOT.jar
set LOG_FILE=%SCRIPT_DIR%drs-agent.log

REM 默认端口
if not defined PORT set PORT=8080

echo ========================================
echo DRS Agent 启动脚本
echo ========================================

REM 检查jar文件是否存在
if not exist "%JAR_FILE%" (
    echo 错误: 找不到jar文件!
    echo 请先运行 build.bat 进行打包
    exit /b 1
)

REM 检查端口是否被占用
netstat -ano | findstr ":%PORT%" | findstr "LISTENING" >nul
if errorlevel 0 (
    if not errorlevel 1 (
        echo 端口 %PORT% 已被占用!
        echo 请修改端口: set PORT=其他端口 && start.bat
        exit /b 1
    )
)

echo.
echo 启动参数:
echo   端口: %PORT%
echo   日志: %LOG_FILE%
echo.

REM 启动服务
start "DRS Agent" java -jar "%JAR_FILE%" ^
    --spring.config.location=classpath:/application-standalone.yml ^
    --server.port=%PORT% ^
    > "%LOG_FILE%" 2>&1

echo 服务启动中...
echo.

REM 等待服务启动
echo 等待服务就绪...
set /a COUNT=0
:wait_loop
set /a COUNT+=1
if %COUNT% gtr 30 (
    echo.
    echo 服务启动超时，请检查日志:
    echo   %LOG_FILE%
    exit /b 1
)

timeout /t 1 /nobreak >nul
curl -s http://localhost:%PORT%/api/actuator/health >nul 2>&1
if errorlevel 1 (
    goto wait_loop
)

echo.
echo ========================================
echo 服务启动成功!
echo ========================================
echo.
echo 访问地址:
echo   前端页面: http://localhost:%PORT%
echo   API接口:  http://localhost:%PORT%/api
echo   健康检查: http://localhost:%PORT%/api/actuator/health
echo.
echo 登录账号:
echo   用户名: admin
echo   密码:   admin123
echo.
echo 停止服务: stop.bat
echo 查看日志: type %LOG_FILE%
echo.

endlocal