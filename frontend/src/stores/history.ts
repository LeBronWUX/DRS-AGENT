import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface HistoryRecord {
  id: number
  title: string
  type: 'database' | 'network' | 'system' | 'application'
  status: 'pending' | 'processing' | 'completed' | 'failed'
  problem: string
  result: string
  suggestion: string
  createTime: string
}

export const useHistoryStore = defineStore('history', () => {
  const historyList = ref<HistoryRecord[]>([])
  const currentRecord = ref<HistoryRecord | null>(null)
  const loading = ref(false)
  const total = ref(0)

  const setHistoryList = (list: HistoryRecord[]) => {
    historyList.value = list
  }

  const setCurrentRecord = (record: HistoryRecord | null) => {
    currentRecord.value = record
  }

  const deleteRecord = (id: number) => {
    const index = historyList.value.findIndex(item => item.id === id)
    if (index > -1) {
      historyList.value.splice(index, 1)
      total.value -= 1
    }
  }

  const clearAll = () => {
    historyList.value = []
    total.value = 0
  }

  const setLoading = (value: boolean) => {
    loading.value = value
  }

  const setTotal = (value: number) => {
    total.value = value
  }

  return {
    historyList,
    currentRecord,
    loading,
    total,
    setHistoryList,
    setCurrentRecord,
    deleteRecord,
    clearAll,
    setLoading,
    setTotal
  }
})