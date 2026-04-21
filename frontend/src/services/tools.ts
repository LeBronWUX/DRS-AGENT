import { request } from './request'

export interface ToolConfig {
  id?: number
  toolName: string
  description?: string
  inputParams?: ToolParamDef[]
  outputFormat?: string
  toolType: string
  endpointUrl?: string
  httpMethod?: string
  authType?: string
  authConfig?: AuthConfig
  requestTemplate?: string
  responseMapping?: string
  headers?: HeaderDef[]
  timeoutMs?: number
  enabled?: boolean
  isDynamic?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ToolParamDef {
  name: string
  type: string
  required: boolean
  description?: string
  defaultValue?: any
}

export interface AuthConfig {
  header?: string
  key?: string
  username?: string
  password?: string
}

export interface HeaderDef {
  name: string
  value: string
}

export interface ToolTestRequest {
  params: Record<string, any>
}

export interface ToolTestResult {
  success: boolean
  data?: any
  error?: string
  executionTime?: number
}

export const toolsApi = {
  getTools(): Promise<ToolConfig[]> {
    return request.get('/v1/tools')
  },

  getTool(toolName: string): Promise<ToolConfig> {
    return request.get(`/v1/tools/${toolName}`)
  },

  createTool(config: ToolConfig): Promise<ToolConfig> {
    return request.post('/v1/tools', config)
  },

  updateTool(toolName: string, config: ToolConfig): Promise<ToolConfig> {
    return request.put(`/v1/tools/${toolName}`, config)
  },

  deleteTool(toolName: string): Promise<{ success: boolean; message: string }> {
    return request.delete(`/v1/tools/${toolName}`)
  },

  testTool(toolName: string, params: Record<string, any>): Promise<ToolTestResult> {
    return request.post(`/v1/tools/${toolName}/test`, { params })
  },

  refreshTools(): Promise<{ success: boolean; message: string }> {
    return request.post('/v1/tools/refresh')
  }
}