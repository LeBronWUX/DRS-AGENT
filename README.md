# DRS智能运维平台

AI驱动的数据复制服务(DRS)智能故障诊断系统

## 项目简介

DRS智能运维平台是一个基于AI的故障诊断系统，支持:
- 实时流式诊断输出(展示AI思考过程)
- 动态MCP工具配置(Web UI/CLI)
- AI模型可视化配置(GLM 4.x/5.0, Claude, OpenAI)
- 权限管理(admin账号, 游客仅可诊断)
- 一键部署(Windows/Linux)

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端 | Java 21, Spring Boot 3.2, Spring WebFlux |
| 前端 | Vue 3, TypeScript, Element Plus, Vite |
| 数据库 | MySQL 8.0 / H2 (内存模式) |
| 向量库 | Milvus (可选) |
| 缓存 | Redis (可选) |
| AI模型 | GLM, Claude, OpenAI |

## 快速启动

### 方式一: 部署包启动

```bash
# Windows
start.bat

# Linux/Mac
./start.sh

# 自定义端口
PORT=9000 ./start.sh
```

### 方式二: 源码构建

```bash
# 后端
cd drs-agent-backend
mvn clean package -DskipTests
java -jar target/drs-agent-backend-1.0.0-SNAPSHOT.jar

# 前端开发
cd drs-agent-frontend
npm install
npm run dev
```

## 访问地址

启动后访问:
- 前端页面: http://localhost:8080
- API接口: http://localhost:8080/v1
- 健康检查: http://localhost:8080/actuator/health

## 默认账号

| 用户 | 密码 | 角色 |
|------|------|------|
| admin | admin123 | 管理员 |

生产环境请修改默认密码。

## 环境配置

### 向量数据库 (Milvus)

```bash
export MILVUS_HOST=192.168.1.100
export MILVUS_PORT=19530
```

### MySQL数据库

```bash
export MYSQL_HOST=192.168.1.100
export MYSQL_PORT=3306
export MYSQL_DATABASE=drs_agent
export MYSQL_USERNAME=drs_user
export MYSQL_PASSWORD=password
```

### Redis缓存

```bash
export REDIS_HOST=192.168.1.100
export REDIS_PORT=6379
```

### AI模型

登录后在"模型配置"页面配置API Key:
- GLM: 智谱AI API Key
- Claude: Anthropic API Key
- OpenAI: OpenAI API Key

## 功能模块

### 1. 故障诊断
输入问题描述，实时展示AI分析过程:
- 问题分类
- 经验检索
- 诊断链执行
- 根因分析
- 方案生成

### 2. 模型配置
可视化配置AI模型:
- 支持多提供商
- 测试连接
- 默认模型设置

### 3. 工具管理
动态配置HTTP工具:
- 添加自定义API工具
- 配置认证方式
- 测试工具执行

### 4. 权限管理
- 管理员: 全功能访问
- 游客: 仅诊断功能

## 项目结构

```
DRS-AGENT/
├── src/main/java/          # 后端源码
│   ├── controller/         # API控制器
│   ├── service/            # 业务服务
│   ├── mcp/                # MCP工具
│   ├── config/             # 配置类
│   └── entity/             # 数据实体
├── frontend/               # 前端源码
│   ├── src/views/          # 页面组件
│   ├── src/components/     # 通用组件
│   ├── src/services/       # API服务
│   └── src/router/         # 路由配置
├── start.sh / start.bat    # 启动脚本
├── stop.sh / stop.bat      # 停止脚本
└── README.md               # 本文档
```

## API接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /v1/auth/login | POST | 登录 |
| /v1/auth/check | GET | 检查认证状态 |
| /v1/diagnose/stream | POST | 流式诊断 |
| /v1/models | GET/POST | 模型配置 |
| /v1/tools | GET/POST | 工具配置 |
| /v1/experiences | GET | 经验列表 |

## Docker部署

```bash
# 构建镜像
docker build -t drs-agent .

# 运行容器
docker run -d -p 8080:8080 \
  -e MILVUS_HOST=host \
  -e MYSQL_HOST=host \
  drs-agent
```

## 开发指南

### 后端开发
```bash
cd drs-agent-backend
mvn spring-boot:run
```

### 前端开发
```bash
cd frontend
npm run dev
```

## 许可证

MIT License

## 作者

DRS运维团队

---

**GitHub**: https://github.com/LeBronWUX/DRS-AGENT