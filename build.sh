#!/bin/bash
# DRS Agent 一键打包脚本
# 构建前端并打包到后端jar中

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/../drs-agent-backend"
FRONTEND_DIR="$SCRIPT_DIR/../drs-agent-frontend"
DEPLOY_DIR="$SCRIPT_DIR/../drs-agent-deploy"

echo "========================================"
echo "DRS Agent 一键打包"
echo "========================================"

# 1. 构建前端
echo ""
echo "[1/4] 构建前端..."
cd "$FRONTEND_DIR"
npm run build

# 2. 复制前端到后端resources
echo ""
echo "[2/4] 复制前端文件到后端..."
rm -rf "$BACKEND_DIR/src/main/resources/frontend"
mkdir -p "$BACKEND_DIR/src/main/resources/frontend"
cp -r dist/* "$BACKEND_DIR/src/main/resources/frontend/"

# 3. 构建后端
echo ""
echo "[3/4] 构建后端..."
cd "$BACKEND_DIR"
mvn clean package -DskipTests

# 4. 创建部署包
echo ""
echo "[4/4] 创建部署包..."
mkdir -p "$DEPLOY_DIR"
cp target/drs-agent-backend-1.0.0-SNAPSHOT.jar "$DEPLOY_DIR/"
cp "$SCRIPT_DIR/start.sh" "$DEPLOY_DIR/"
cp "$SCRIPT_DIR/start.bat" "$DEPLOY_DIR/"
cp "$SCRIPT_DIR/stop.sh" "$DEPLOY_DIR/"
cp "$SCRIPT_DIR/stop.bat" "$DEPLOY_DIR/"

echo ""
echo "========================================"
echo "打包完成!"
echo "========================================"
echo "部署包位置: $DEPLOY_DIR"
echo ""
echo "文件列表:"
ls -lh "$DEPLOY_DIR"
echo ""
echo "启动方式:"
echo "  Linux/Mac: ./start.sh"
echo "  Windows:   start.bat"