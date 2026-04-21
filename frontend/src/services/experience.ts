import { request } from './request'
import type { ExperienceRecord } from '@/stores/experience'

export interface ExperienceCreateRequest {
  title: string
  category: string
  problem: string
  solution: string
  tags?: string[]
}

export interface ExperienceUpdateRequest extends ExperienceCreateRequest {
  id: number
}

export const experienceApi = {
  getExperienceList(params: {
    page: number
    pageSize: number
    category?: string
    keyword?: string
  }): Promise<{
    list: ExperienceRecord[]
    total: number
  }> {
    return request.get('/experience/list', { params })
  },

  getExperienceById(id: number): Promise<ExperienceRecord> {
    return request.get(`/experience/${id}`)
  },

  createExperience(data: ExperienceCreateRequest): Promise<ExperienceRecord> {
    return request.post('/experience', data)
  },

  updateExperience(data: ExperienceUpdateRequest): Promise<ExperienceRecord> {
    return request.put('/experience', data)
  },

  deleteExperience(id: number): Promise<void> {
    return request.delete(`/experience/${id}`)
  },

  searchExperience(keyword: string): Promise<ExperienceRecord[]> {
    return request.get('/experience/search', { params: { keyword } })
  }
}