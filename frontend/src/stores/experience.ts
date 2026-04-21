import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ExperienceRecord {
  id: number
  title: string
  category: string
  problem: string
  solution: string
  tags: string[]
  createTime: string
  updateTime: string
}

export const useExperienceStore = defineStore('experience', () => {
  const experienceList = ref<ExperienceRecord[]>([])
  const currentExperience = ref<ExperienceRecord | null>(null)
  const loading = ref(false)

  const setExperienceList = (list: ExperienceRecord[]) => {
    experienceList.value = list
  }

  const addExperience = (experience: ExperienceRecord) => {
    experienceList.value.unshift(experience)
  }

  const updateExperience = (id: number, experience: Partial<ExperienceRecord>) => {
    const index = experienceList.value.findIndex(item => item.id === id)
    if (index > -1) {
      experienceList.value[index] = { ...experienceList.value[index], ...experience }
    }
  }

  const deleteExperience = (id: number) => {
    const index = experienceList.value.findIndex(item => item.id === id)
    if (index > -1) {
      experienceList.value.splice(index, 1)
    }
  }

  const setLoading = (value: boolean) => {
    loading.value = value
  }

  return {
    experienceList,
    currentExperience,
    loading,
    setExperienceList,
    addExperience,
    updateExperience,
    deleteExperience,
    setLoading
  }
})