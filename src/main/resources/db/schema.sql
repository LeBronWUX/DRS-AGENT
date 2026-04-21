-- DRS智能运维智能体 - MySQL表结构
-- 版本: v1.0
-- 创建日期: 2026-04-20

-- =====================================================
-- 1. 经验库主表
-- =====================================================
CREATE TABLE IF NOT EXISTS experiences (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '经验ID',
    problem_type VARCHAR(50) NOT NULL COMMENT '问题类型',
    keywords JSON NOT NULL COMMENT '关键词列表',
    diagnosis_chain JSON NOT NULL COMMENT '诊断链路JSON',
    root_causes JSON NOT NULL COMMENT '根因模式列表JSON',
    source VARCHAR(100) COMMENT '经验来源(专家名称)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    hit_count INT DEFAULT 0 COMMENT '命中次数',
    feedback_score DECIMAL(3,2) DEFAULT 0.00 COMMENT '反馈评分(0-5)',
    status VARCHAR(20) DEFAULT 'active' COMMENT '状态(active/deprecated/pending)',
    embedding_id VARCHAR(100) COMMENT 'Milvus向量ID',
    INDEX idx_problem_type (problem_type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊断经验库';

-- =====================================================
-- 2. 诊断会话表
-- =====================================================
CREATE TABLE IF NOT EXISTS diagnosis_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',
    session_id VARCHAR(100) NOT NULL UNIQUE COMMENT '会话唯一标识',
    user_id VARCHAR(100) COMMENT '用户ID',
    problem TEXT NOT NULL COMMENT '用户问题描述',
    problem_type VARCHAR(50) COMMENT '识别的问题类型',
    confidence DECIMAL(4,3) COMMENT '问题类型置信度',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态(pending/in_progress/completed/failed)',
    result JSON COMMENT '诊断结果JSON',
    root_cause TEXT COMMENT '根因描述',
    solution TEXT COMMENT '处理建议',
    result_confidence DECIMAL(4,3) COMMENT '结果置信度',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    completed_at TIMESTAMP COMMENT '完成时间',
    source VARCHAR(20) DEFAULT 'web' COMMENT '来源(web/welink/alert)',
    feedback_rating INT COMMENT '用户反馈评分(1-5)',
    feedback_comment TEXT COMMENT '用户反馈评论',
    INDEX idx_session_id (session_id),
    INDEX idx_status (status),
    INDEX idx_problem_type (problem_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊断会话记录';

-- =====================================================
-- 3. 诊断步骤表
-- =====================================================
CREATE TABLE IF NOT EXISTS diagnosis_steps (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '步骤ID',
    session_id VARCHAR(100) NOT NULL COMMENT '关联会话ID',
    step_order INT NOT NULL COMMENT '步骤顺序',
    tool_name VARCHAR(50) NOT NULL COMMENT '工具名称',
    action VARCHAR(200) COMMENT '动作描述',
    params JSON COMMENT '输入参数JSON',
    result JSON COMMENT '执行结果JSON',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态(pending/running/success/failed/skipped)',
    error_message TEXT COMMENT '错误信息',
    executed_at TIMESTAMP COMMENT '执行时间',
    duration_ms INT COMMENT '执行耗时(毫秒)',
    INDEX idx_session_id (session_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊断步骤记录';

-- =====================================================
-- 4. 反馈记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS feedback_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '反馈ID',
    session_id VARCHAR(100) NOT NULL COMMENT '关联会话ID',
    user_id VARCHAR(100) COMMENT '用户ID',
    rating INT NOT NULL COMMENT '评分(1-5)',
    comment TEXT COMMENT '评论',
    is_correct BOOLEAN COMMENT '诊断是否正确',
    corrected_root_cause TEXT COMMENT '修正后的根因',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈记录';

-- =====================================================
-- 5. WeLink消息记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS welink_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    msg_id VARCHAR(100) NOT NULL UNIQUE COMMENT 'WeLink消息ID',
    sender_id VARCHAR(100) COMMENT '发送者ID',
    content TEXT COMMENT '消息内容',
    response TEXT COMMENT '响应内容',
    session_id VARCHAR(100) COMMENT '关联诊断会话ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_msg_id (msg_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WeLink消息记录';

-- =====================================================
-- 6. 告警记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS alert_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '告警ID',
    alert_id VARCHAR(100) NOT NULL UNIQUE COMMENT '告警唯一标识',
    alert_type VARCHAR(50) COMMENT '告警类型',
    alert_content TEXT COMMENT '告警内容',
    workflow_id VARCHAR(100) COMMENT '关联workflowId',
    session_id VARCHAR(100) COMMENT '关联诊断会话ID',
    auto_triggered BOOLEAN DEFAULT FALSE COMMENT '是否自动触发诊断',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_alert_id (alert_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录';

-- =====================================================
-- 7. Agent对话历史表 (Claude调用记录)
-- =====================================================
CREATE TABLE IF NOT EXISTS conversation_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT '角色(user/assistant)',
    content TEXT NOT NULL COMMENT '消息内容',
    tokens_used INT DEFAULT 0 COMMENT '使用的token数',
    model VARCHAR(50) COMMENT '使用的模型',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Claude对话历史';

-- =====================================================
-- 8. MCP工具执行日志表
-- =====================================================
CREATE TABLE IF NOT EXISTS mcp_tool_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(50) NOT NULL COMMENT '工具名称',
    status VARCHAR(20) NOT NULL COMMENT '执行状态',
    input_data TEXT COMMENT '输入参数',
    output_data TEXT COMMENT '输出结果',
    error_message TEXT COMMENT '错误信息',
    execution_time_ms BIGINT COMMENT '执行耗时',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_tool_name (tool_name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP工具执行日志';

-- =====================================================
-- 9. MCP工具动态配置表
-- =====================================================
CREATE TABLE IF NOT EXISTS mcp_tools_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_name VARCHAR(100) NOT NULL UNIQUE COMMENT '工具名称',
    description VARCHAR(500) COMMENT '工具描述',
    input_params JSON COMMENT '输入参数定义',
    output_format JSON COMMENT '输出格式定义',
    tool_type VARCHAR(50) NOT NULL COMMENT '工具类型(HTTP/MOCK)',
    endpoint_url VARCHAR(500) COMMENT 'HTTP接口地址',
    http_method VARCHAR(10) DEFAULT 'GET' COMMENT 'HTTP方法',
    auth_type VARCHAR(50) DEFAULT 'NONE' COMMENT '认证类型(NONE/API_KEY/BASIC)',
    auth_config JSON COMMENT '认证配置',
    request_template JSON COMMENT '请求模板',
    response_mapping VARCHAR(200) COMMENT '响应映射(JSONPath)',
    headers JSON COMMENT '自定义请求头',
    timeout_ms INT DEFAULT 30000 COMMENT '超时时间',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tool_name (tool_name),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP工具动态配置';

-- =====================================================
-- 10. AI模型配置表
-- =====================================================
CREATE TABLE IF NOT EXISTS ai_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL UNIQUE COMMENT '模型配置名称',
    provider VARCHAR(50) NOT NULL COMMENT '提供商(CLAUDE/GLM/OPENAI)',
    api_endpoint VARCHAR(500) NOT NULL COMMENT 'API地址',
    api_key VARCHAR(500) COMMENT 'API密钥(加密存储)',
    model_id VARCHAR(100) NOT NULL COMMENT '模型ID',
    max_tokens INT DEFAULT 4096 COMMENT '最大Token数',
    temperature DECIMAL(3,2) DEFAULT 0.7 COMMENT '温度参数',
    top_p DECIMAL(3,2) DEFAULT 0.9 COMMENT 'Top P参数',
    timeout_seconds INT DEFAULT 120 COMMENT '超时时间',
    max_retries INT DEFAULT 3 COMMENT '最大重试次数',
    is_default BOOLEAN DEFAULT FALSE COMMENT '是否为默认模型',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    extra_params JSON COMMENT '额外参数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_model_name (model_name),
    idx_provider (provider),
    INDEX idx_is_default (is_default),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置';

-- =====================================================
-- 11. 用户账号表
-- =====================================================
CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色(ADMIN/USER)',
    display_name VARCHAR(100) COMMENT '显示名称',
    email VARCHAR(100) COMMENT '邮箱',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    last_login TIMESTAMP COMMENT '最后登录时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号';

-- 默认admin账号 (密码: admin123, 需要在首次启动后修改)
INSERT INTO user_account (username, password_hash, role, display_name, enabled)
SELECT 'admin', 'bcrypt_placeholder', 'ADMIN', '系统管理员', TRUE
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE username = 'admin');