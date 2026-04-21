<template>
  <div class="history-view">
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>历史记录</span>
          <el-button type="danger" @click="handleClearAll">
            清空历史
          </el-button>
        </div>
      </template>

      <el-form :inline="true" class="search-form">
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item label="诊断类型">
          <el-select v-model="diagnosisType" placeholder="全部类型" clearable>
            <el-option label="数据库" value="database" />
            <el-option label="网络" value="network" />
            <el-option label="系统" value="system" />
            <el-option label="应用" value="application" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="historyList" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="诊断标题" min-width="180" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getTypeTag(row.type)">
              {{ getTypeName(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="诊断时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">
              查看详情
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="诊断详情" width="700px">
      <el-descriptions :column="1" border v-if="currentDetail">
        <el-descriptions-item label="诊断标题">
          {{ currentDetail.title }}
        </el-descriptions-item>
        <el-descriptions-item label="诊断类型">
          {{ getTypeName(currentDetail.type) }}
        </el-descriptions-item>
        <el-descriptions-item label="诊断状态">
          <el-tag :type="getStatusTag(currentDetail.status)">
            {{ currentDetail.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="问题描述">
          {{ currentDetail.problem }}
        </el-descriptions-item>
        <el-descriptions-item label="诊断结果">
          {{ currentDetail.result }}
        </el-descriptions-item>
        <el-descriptions-item label="建议方案">
          {{ currentDetail.suggestion }}
        </el-descriptions-item>
        <el-descriptions-item label="诊断时间">
          {{ currentDetail.createTime }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleExport">导出报告</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

interface HistoryRecord {
  id: number
  title: string
  type: string
  status: string
  problem: string
  result: string
  suggestion: string
  createTime: string
}

const dateRange = ref<[Date, Date] | null>(null)
const diagnosisType = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const detailVisible = ref(false)
const currentDetail = ref<HistoryRecord | null>(null)

const historyList = ref<HistoryRecord[]>([
  {
    id: 1,
    title: '数据库连接超时诊断',
    type: 'database',
    status: '已完成',
    problem: '应用无法连接数据库，提示连接超时',
    result: '检测到数据库连接池配置过小，导致连接等待超时',
    suggestion: '建议增加连接池最大连接数至200，并优化慢查询SQL',
    createTime: '2024-01-15 10:30:00'
  },
  {
    id: 2,
    title: '服务响应缓慢分析',
    type: 'application',
    status: '已完成',
    problem: 'API接口响应时间超过5秒',
    result: '发现存在慢查询和内存泄漏问题',
    suggestion: '优化SQL查询语句，修复内存泄漏点',
    createTime: '2024-01-14 14:20:00'
  },
  {
    id: 3,
    title: '网络延迟问题排查',
    type: 'network',
    status: '处理中',
    problem: '跨机房访问延迟过高',
    result: '正在分析网络拓扑和路由配置',
    suggestion: '待分析完成后给出建议',
    createTime: '2024-01-13 09:15:00'
  }
])

total.value = historyList.value.length

const getTypeTag = (type: string) => {
  const types: Record<string, string> = {
    database: 'primary',
    network: 'success',
    system: 'warning',
    application: 'info'
  }
  return types[type] || ''
}

const getTypeName = (type: string) => {
  const names: Record<string, string> = {
    database: '数据库',
    network: '网络',
    system: '系统',
    application: '应用'
  }
  return names[type] || type
}

const getStatusTag = (status: string) => {
  return status === '已完成' ? 'success' : 'warning'
}

const handleSearch = () => {
  ElMessage.info('查询功能开发中...')
}

const handleReset = () => {
  dateRange.value = null
  diagnosisType.value = ''
}

const handleViewDetail = (row: HistoryRecord) => {
  currentDetail.value = row
  detailVisible.value = true
}

const handleDelete = (row: HistoryRecord) => {
  ElMessageBox.confirm(`确定删除该诊断记录?`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    const index = historyList.value.findIndex(item => item.id === row.id)
    if (index > -1) {
      historyList.value.splice(index, 1)
      total.value = historyList.value.length
    }
    ElMessage.success('删除成功')
  })
}

const handleClearAll = () => {
  ElMessageBox.confirm('确定清空所有历史记录?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    historyList.value = []
    total.value = 0
    ElMessage.success('已清空历史记录')
  })
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
}

const handleExport = () => {
  ElMessage.success('导出功能开发中...')
}
</script>

<style scoped>
.history-view {
  height: 100%;
}

.page-card {
  min-height: calc(100vh - 140px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>