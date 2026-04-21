#!/bin/bash
# DRS Agent 停止脚本 (Linux/Mac)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$SCRIPT_DIR/drs-agent.pid"

echo "========================================"
echo "DRS Agent 停止脚本"
echo "========================================"

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null 2>&1; then
        echo "停止服务 (PID: $PID)..."
        kill $PID
        sleep 2

        # 强制杀死如果还在运行
        if ps -p $PID > /dev/null 2>&1; then
            echo "强制停止..."
            kill -9 $PID
        fi

        rm -f "$PID_FILE"
        echo "服务已停止"
        exit 0
    else
        echo "PID文件存在但进程已不存在"
        rm -f "$PID_FILE"
    fi
fi

# 查找Java进程
echo "查找运行中的Java进程..."
PIDS=$(pgrep -f "drs-agent-backend")
if [ -n "$PIDS" ]; then
    echo "找到进程: $PIDS"
    kill $PIDS
    sleep 2
    echo "服务已停止"
else
    echo "没有找到运行中的服务"
fi