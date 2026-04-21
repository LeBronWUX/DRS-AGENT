#!/bin/bash
# DRS Agent 一键启动脚本 (Linux/Mac)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="$SCRIPT_DIR/drs-agent-backend-1.0.0-SNAPSHOT.jar"
LOG_FILE="$SCRIPT_DIR/drs-agent.log"
PID_FILE="$SCRIPT_DIR/drs-agent.pid"

# 默认端口
PORT=${PORT:-8080}

echo "========================================"
echo "DRS Agent 启动脚本"
echo "========================================"

# 检查是否已运行
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null 2>&1; then
        echo "服务已在运行中 (PID: $PID)"
        echo "如需重启，请先运行: ./stop.sh"
        exit 1
    fi
fi

# 检查端口是否被占用
if lsof -i:$PORT > /dev/null 2>&1; then
    echo "端口 $PORT 已被占用!"
    echo "请修改端口: PORT=其他端口 ./start.sh"
    exit 1
fi

# 启动服务
echo ""
echo "启动参数:"
echo "  端口: $PORT"
echo "  日志: $LOG_FILE"
echo ""

nohup java -jar "$JAR_FILE" \
    --spring.config.location=classpath:/application-standalone.yml \
    --server.port=$PORT \
    > "$LOG_FILE" 2>&1 &

PID=$!
echo $PID > "$PID_FILE"

echo "服务启动中... (PID: $PID)"
echo ""

# 等待服务启动
echo "等待服务就绪..."
for i in {1..30}; do
    sleep 1
    if curl -s http://localhost:$PORT/api/actuator/health > /dev/null 2>&1; then
        echo ""
        echo "========================================"
        echo "服务启动成功!"
        echo "========================================"
        echo ""
        echo "访问地址:"
        echo "  前端页面: http://localhost:$PORT"
        echo "  API接口:  http://localhost:$PORT/api"
        echo "  健康检查: http://localhost:$PORT/api/actuator/health"
        echo ""
        echo "登录账号:"
        echo "  用户名: admin"
        echo "  密码:   admin123"
        echo ""
        echo "停止服务: ./stop.sh"
        echo "查看日志: tail -f $LOG_FILE"
        exit 0
    fi
    printf "."
done

echo ""
echo "服务启动超时，请检查日志:"
echo "  $LOG_FILE"
exit 1