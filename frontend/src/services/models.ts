import { request } from './request'

export interface ModelConfig {
  id?: number
  modelName: string
  provider: string
  apiEndpoint: string
  apiKey?: string
  modelId: string
  maxTokens?: number
  temperature?: number
  topP?: number
  timeoutSeconds?: number
  maxRetries?: number
  isDefault?: boolean
  enabled?: boolean
  extraParams?: any
  createdAt?: string
  updatedAt?: string
}

export interface ProviderInfo {
  name: string
  displayName: string
  models: string[]
  defaultEndpoint: string
}

export const modelApi = {
  getModels(): Promise<ModelConfig[]> {
    return request.get('/v1/models')
  },

  getEnabledModels(): Promise<ModelConfig[]> {
    return request.get('/v1/models/enabled')
  },

  getDefaultModel(): Promise<ModelConfig> {
    return request.get('/v1/models/default')
  },

  getProviders(): Promise<ProviderInfo[]> {
    return request.get('/v1/models/providers')
  },

  getModel(modelName: string): Promise<ModelConfig> {
    return request.get(`/v1/models/${modelName}`)
  },

  createModel(config: ModelConfig): Promise<ModelConfig> {
    return request.post('/v1/models', config)
  },

  updateModel(modelName: string, config: Partial<ModelConfig>): Promise<ModelConfig> {
    return request.put(`/v1/models/${modelName}`, config)
  },

  setDefaultModel(modelName: string): Promise<{ success: boolean; message: string }> {
    return request.post(`/v1/models/${modelName}/default`)
  },

  deleteModel(modelName: string): Promise<{ success: boolean; message: string }> {
    return request.delete(`/v1/models/${modelName}`)
  },

  testModel(modelName: string): Promise<{ success: boolean; error?: string; responsePreview?: string }> {
    return request.post(`/v1/models/${modelName}/test`)
  }
}