package com.drs.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * DRS Agent Backend Application
 *
 * Main entry point for the DRS Intelligent Operations Agent Backend Service.
 * This service provides AI-powered operations capabilities including:
 * - MCP (Model Context Protocol) tool integration
 * - Vector database operations with Milvus
 * - Integration with Claude AI for intelligent analysis
 * - Operations platform connectivity
 *
 * @author DRS Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
public class DrsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DrsAgentApplication.class, args);
    }
}