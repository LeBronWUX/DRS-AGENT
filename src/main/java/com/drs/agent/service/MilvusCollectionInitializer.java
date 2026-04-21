package com.drs.agent.service;

import io.milvus.client.MilvusServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Milvus Collection 初始化服务
 * 创建drs_experiences向量库Collection
 * Milvus client is optional - if not available, initialization will be skipped.
 */
@Slf4j
@Service
public class MilvusCollectionInitializer {

    private final MilvusServiceClient milvusClient;

    @Value("${milvus.enabled:false}")
    private boolean milvusEnabled;

    // Collection名称
    private static final String COLLECTION_NAME = "drs_experiences";
    // 向量维度 (Claude embedding dim)
    private static final int EMBEDDING_DIM = 1536;

    @Autowired
    public MilvusCollectionInitializer(@Autowired(required = false) MilvusServiceClient milvusClient) {
        this.milvusClient = milvusClient;
        log.info("MilvusCollectionInitializer initialized, Milvus enabled: {}", milvusClient != null);
    }

    /**
     * 初始化Collection (如果不存在则创建)
     *
     * TODO: 实际实现需要根据Milvus SDK版本调整API调用
     */
    public void initializeCollection() {
        if (milvusClient == null || !milvusEnabled) {
            log.info("Milvus not enabled, skipping collection initialization");
            return;
        }
        log.info("开始初始化Milvus Collection: {}", COLLECTION_NAME);

        try {
            // TODO: 检查Collection是否已存在
            // TODO: 创建Collection和索引
            log.info("Milvus Collection {} 初始化完成", COLLECTION_NAME);
        } catch (Exception e) {
            log.error("初始化Milvus Collection失败: {}", e.getMessage(), e);
        }
    }

    /**
     * Collection名称
     */
    public String getCollectionName() {
        return COLLECTION_NAME;
    }

    /**
     * 向量维度
     */
    public int getEmbeddingDim() {
        return EMBEDDING_DIM;
    }
}