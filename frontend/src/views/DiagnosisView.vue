<template>
  <div class="diagnosis-view">
    <el-row :gutter="20">
      <!-- 左侧：诊断对话组件 -->
      <el-col :span="16">
        <DiagnosisChat
          @feedback="handleFeedback"
          @view-experience="handleViewExperience"
        />
      </el-col>

      <!-- 右侧：快速入口 -->
      <el-col :span="8">
        <el-card class="quick-entry-card">
          <template #header>
            <div class="card-header">
              <el-icon><Star /></el-icon>
              <span>快速入口</span>
            </div>
          </template>

          <!-- 常见问题 -->
          <div class="section">
            <h4>常见问题</h4>
            <div class="quick-items">
              <el-button
                v-for="(item, index) in commonProblems"
                :key="index"
                text
                @click="handleQuickInput(item)"
                class="quick-item"
              >
                <el-icon><QuestionFilled /></el-icon>
                {{ item }}
              </el-button>
            </div>
          </div>

          <!-- 诊断历史 -->
          <div class="section">
            <h4>最近诊断</h4>
            <el-timeline>
              <el-timeline-item
                v-for="record in recentDiagnosis"
                :key="record.id"
                :timestamp="record.time"
                placement="top"
              >
                <div class="history-item" @click="handleViewHistory(record)">
                  <el-tag :type="record.status === 'success' ? 'success' : 'danger'" size="small">
                    {{ record.status === 'success' ? '成功' : '失败' }}
                  </el-tag>
                  <span class="history-title">{{ record.title }}</span>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>

          <!-- 统计信息 -->
          <div class="section">
            <h4>统计信息</h4>
            <el-row :gutter="10">
              <el-col :span="12">
                <el-statistic title="今日诊断" :value="statistics.todayCount" />
              </el-col>
              <el-col :span="12">
                <el-statistic title="成功率" :value="statistics.successRate" suffix="%" />
              </el-col>
            </el-row>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 经验详情对话框 -->
    <el-dialog
      v-model="experienceDialogVisible"
      title="相关经验详情"
      width="600px"
      destroy-on-close
    >
      <el-descriptions :column="1" border v-if="selectedExperience">
        <el-descriptions-item label="经验标题">
          {{ selectedExperience.title }}
        </el-descriptions-item>
        <el-descriptions-item label="问题描述">
          {{ selectedExperience.problem }}
        </el-descriptions-item>
        <el-descriptions-item label="解决方案">
          {{ selectedExperience.solution }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Star, QuestionFilled } from '@element-plus/icons-vue'
import DiagnosisChat from '@/components/DiagnosisChat.vue'

interface RecentDiagnosis {
  id: number
  title: string
  time: string
  status: 'success' | 'failed'
}

interface SimilarExperience {
  experienceId: string
  problemType: string
  similarity: number
  summary?: string
}

interface SelectedExperience {
  id: string
  title: string
  problem: string
  solution: string
}

const commonProblems = [
  '系统登录失败',
  '数据库连接超时',
  '服务响应缓慢',
  '权限不足错误',
  '网络连接异常',
  '内存溢出问题'
]

const recentDiagnosis = ref<RecentDiagnosis[]>([
  { id: 1, title: '用户登录失败排查', time: '2024-01-20 14:30', status: 'success' },
  { id: 2, title: 'MySQL连接超时分析', time: '2024-01-20 11:20', status: 'success' },
  { id: 3, title: 'API响应慢诊断', time: '2024-01-20 09:15', status: 'failed' },
  { id: 4, title: 'Redis连接异常', time: '2024-01-19 16:45', status: 'success' }
])

const statistics = reactive({
  todayCount: 42,
  successRate: 89.5
})

const experienceDialogVisible = ref(false)
const selectedExperience = ref<SelectedExperience | null>(null)

const handleFeedback = (correct: boolean) => {
  if (correct) {
    ElMessage.success('感谢反馈，您的反馈将帮助我们提升诊断准确度')
  } else {
    ElMessage.info('感谢反馈，我们会记录并改进诊断逻辑')
  }
  // 可以调用API记录反馈
}

const handleViewExperience = (experience: SimilarExperience) => {
  // 模拟获取经验详情
  selectedExperience.value = {
    id: experience.experienceId,
    title: experience.problemType,
    problem: experience.summary || '暂无详细问题描述',
    solution: '1. 检查相关配置\n2. 验证服务状态\n3. 确认依赖服务\n4. 必要时重启服务'
  }
  experienceDialogVisible.value = true
}

const handleQuickInput = (problem: string) => {
  ElMessage.info(`快速输入: ${problem}`)
  // 可以通过事件总线或状态管理传递给DiagnosisChat组件
}

const handleViewHistory = (record: RecentDiagnosis) => {
  ElMessage.info(`查看诊断历史: ${record.title}`)
  // 跳转到历史详情页面或打开对话框
}
</script>

<style scoped>
.diagnosis-view {
  height: 100%;
  padding: 0;
}

.quick-entry-card {
  border-radius: 8px;
  min-height: calc(100vh - 140px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #303133;
}

.section {
  margin-bottom: 24px;
}

.section h4 {
  margin-bottom: 12px;
  color: #606266;
  font-size: 14px;
  font-weight: 600;
}

.quick-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.quick-item {
  justify-content: flex-start;
  text-align: left;
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-radius: 6px;
  transition: background-color 0.2s;
}

.quick-item:hover {
  background-color: #ecf5ff;
}

.history-item {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

.history-item:hover .history-title {
  color: #409eff;
}

.history-title {
  font-size: 13px;
  transition: color 0.2s;
}
</style>