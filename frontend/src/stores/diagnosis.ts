import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface DiagnosisRecord {
  id: number
  title: string
  description: string
  logs: string
  result: string
  status: 'pending' | 'processing' | 'completed' | 'failed'
  createTime: string
}

export const useDiagnosisStore = defineStore('diagnosis', () => {
  const currentDiagnosis = ref<DiagnosisRecord | null>(null)
  const diagnosisHistory = ref<DiagnosisRecord[]>([])
  const loading = ref(false)

  const setCurrentDiagnosis = (diagnosis: DiagnosisRecord | null) => {
    currentDiagnosis.value = diagnosis
  }

  const addToHistory = (diagnosis: DiagnosisRecord) => {
    diagnosisHistory.value.unshift(diagnosis)
  }

  const setLoading = (value: boolean) => {
    loading.value = value
  }

  return {
    currentDiagnosis,
    diagnosisHistory,
    loading,
    setCurrentDiagnosis,
    addToHistory,
    setLoading
  }
})