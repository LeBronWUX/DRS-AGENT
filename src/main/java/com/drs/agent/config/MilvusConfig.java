package com.drs.agent.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus Vector Database Configuration
 *
 * Creates and configures the Milvus client for vector database operations.
 * Can be disabled by setting milvus.enabled=false.
 */
@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private int milvusPort;

    @Value("${milvus.connect-timeout:10000}")
    private long connectTimeout;

    @Value("${milvus.keep-alive-time:60000}")
    private long keepAliveTime;

    @Bean
    @ConditionalOnProperty(name = "milvus.enabled", havingValue = "true", matchIfMissing = false)
    public MilvusServiceClient milvusServiceClient() {
        log.info("Initializing Milvus client connection to {}:{}", milvusHost, milvusPort);

        return new MilvusServiceClient(
                ConnectParam.newBuilder()
                        .withHost(milvusHost)
                        .withPort(milvusPort)
                        .withConnectTimeout(connectTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .withKeepAliveTime(keepAliveTime, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .build()
        );
    }
}