package com.drs.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Prompt模板服务
 * 负责加载和管理Prompt模板文件
 */
@Service
public class PromptTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateService.class);
    private static final String PROMPT_DIR = "prompts/";

    // Prompt模板名称常量
    public static final String PROBLEM_CLASSIFIER = "problem_classifier";
    public static final String DIAGNOSIS_ORCHESTRATOR = "diagnosis_orchestrator";
    public static final String ROOT_CAUSE_ANALYZER = "root_cause_analyzer";
    public static final String EXPERIENCE_GENERATOR = "experience_generator";

    // 模板缓存
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        logger.info("Initializing PromptTemplateService...");
        // 预加载所有模板
        loadTemplate(PROBLEM_CLASSIFIER);
        loadTemplate(DIAGNOSIS_ORCHESTRATOR);
        loadTemplate(ROOT_CAUSE_ANALYZER);
        loadTemplate(EXPERIENCE_GENERATOR);
        logger.info("PromptTemplateService initialized with {} templates", templateCache.size());
    }

    /**
     * 加载指定的Prompt模板
     *
     * @param templateName 模板名称(不含.txt后缀)
     * @return 模板内容
     */
    public String loadTemplate(String templateName) {
        return templateCache.computeIfAbsent(templateName, name -> {
            try {
                String content = loadTemplateFromFile(name + ".txt");
                logger.debug("Loaded prompt template: {}", name);
                return content;
            } catch (IOException e) {
                logger.error("Failed to load prompt template: {}", name, e);
                throw new PromptTemplateException("Failed to load prompt template: " + name, e);
            }
        });
    }

    /**
     * 获取模板并填充参数
     *
     * @param templateName 模板名称
     * @param params       参数映射
     * @return 填充后的模板内容
     */
    public String getTemplate(String templateName, Map<String, String> params) {
        String template = loadTemplate(templateName);
        return fillTemplate(template, params);
    }

    /**
     * 获取模板并填充单个参数
     *
     * @param templateName 模板名称
     * @param paramName    参数名称
     * @param paramValue   参数值
     * @return 填充后的模板内容
     */
    public String getTemplate(String templateName, String paramName, String paramValue) {
        String template = loadTemplate(templateName);
        return template.replace("{" + paramName + "}", paramValue);
    }

    /**
     * 从文件加载模板内容
     */
    private String loadTemplateFromFile(String fileName) throws IOException {
        ClassPathResource resource = new ClassPathResource(PROMPT_DIR + fileName);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * 填充模板参数
     */
    private String fillTemplate(String template, Map<String, String> params) {
        String result = template;
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    /**
     * 刷新模板缓存
     */
    public void refreshCache() {
        logger.info("Refreshing prompt template cache...");
        templateCache.clear();
        init();
    }

    /**
     * 刷新指定模板
     */
    public void refreshTemplate(String templateName) {
        templateCache.remove(templateName);
        loadTemplate(templateName);
        logger.info("Refreshed prompt template: {}", templateName);
    }

    /**
     * 获取所有已加载的模板名称
     */
    public java.util.Set<String> getLoadedTemplateNames() {
        return templateCache.keySet();
    }

    /**
     * 检查模板是否存在
     */
    public boolean hasTemplate(String templateName) {
        return templateCache.containsKey(templateName);
    }

    /**
     * Prompt模板异常
     */
    public static class PromptTemplateException extends RuntimeException {
        public PromptTemplateException(String message) {
            super(message);
        }

        public PromptTemplateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}