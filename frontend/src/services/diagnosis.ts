import { request } from './request'
import type { DiagnosisRecord } from '@/stores/diagnosis'

export interface DiagnosisRequest {
  description: string
  logs?: string
}

export interface DiagnosisResponse {
  id: number
  title: string
  result: string
  solutions: Array<{
    step: string
    content: string
  }>
  confidence: number
}

export const diagnosisApi = {
  startDiagnosis(data: DiagnosisRequest): Promise<DiagnosisResponse> {
    return request.post('/diagnosis/start', data)
  },

  getDiagnosisResult(id: number): Promise<DiagnosisRecord> {
    return request.get(`/diagnosis/${id}`)
  },

  getDiagnosisHistory(params: {
    page: number
    pageSize: number
    type?: string
    startDate?: string
    endDate?: string
  }): Promise<{
    list: DiagnosisRecord[]
    total: number
  }> {
    return request.get('/diagnosis/history', { params })
  }
}