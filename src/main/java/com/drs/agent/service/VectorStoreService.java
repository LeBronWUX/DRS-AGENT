package com.drs.agent.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.collection.ReleaseCollectionParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Vector Store Service
 *
 * Handles vector database operations using Milvus for semantic search and RAG.
 * Milvus client is optional - if not available, operations will return default values.
 */
@Slf4j
@Service
public class VectorStoreService {

    private final MilvusServiceClient milvusServiceClient;

    @Value("${milvus.enabled:false}")
    private boolean milvusEnabled;

    @Autowired
    public VectorStoreService(@Autowired(required = false) MilvusServiceClient milvusServiceClient) {
        this.milvusServiceClient = milvusServiceClient;
        log.info("VectorStoreService initialized, Milvus enabled: {}", milvusServiceClient != null);
    }

    public boolean collectionExists(String collectionName) {
        if (milvusServiceClient == null || !milvusEnabled) {
            log.info("Milvus not enabled, returning false for collection existence");
            return false;
        }
        try {
            HasCollectionParam param = HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();
            var response = milvusServiceClient.hasCollection(param);
            return response.getData();
        } catch (Exception e) {
            log.error("Error checking collection existence: {}", e.getMessage());
            return false;
        }
    }

    public void loadCollection(String collectionName) {
        if (milvusServiceClient == null || !milvusEnabled) {
            log.info("Milvus not enabled, skipping collection load");
            return;
        }
        try {
            LoadCollectionParam param = LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();
            milvusServiceClient.loadCollection(param);
            log.info("Collection {} loaded successfully", collectionName);
        } catch (Exception e) {
            log.error("Error loading collection {}: {}", collectionName, e.getMessage());
        }
    }

    public void releaseCollection(String collectionName) {
        if (milvusServiceClient == null || !milvusEnabled) {
            log.info("Milvus not enabled, skipping collection release");
            return;
        }
        try {
            ReleaseCollectionParam param = ReleaseCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();
            milvusServiceClient.releaseCollection(param);
            log.info("Collection {} released successfully", collectionName);
        } catch (Exception e) {
            log.error("Error releasing collection {}: {}", collectionName, e.getMessage());
        }
    }
}