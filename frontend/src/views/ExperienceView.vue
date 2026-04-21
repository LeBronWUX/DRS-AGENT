<template>
  <div class="experience-view">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 经验列表 Tab -->
      <el-tab-pane label="经验库" name="list">
        <ExperienceList
          @view="handleView"
          @edit="handleEdit"
          @delete="handleDelete"
        />
      </el-tab-pane>

      <!-- 经验录入 Tab -->
      <el-tab-pane label="录入经验" name="form">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><Edit /></el-icon>
              <span>新建经验</span>
            </div>
          </template>
          <ExperienceForm @submit="handleSubmit" />
        </el-card>
      </el-tab-pane>

      <!-- 统计分析 Tab -->
      <el-tab-pane label="统计分析" name="stats">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-card shadow="hover" class="stat-card">
              <el-statistic title="总经验数" :value="stats.totalCount">
                <template #suffix>
                  <el-icon><Document /></el-icon>
                </template>
              </el-statistic>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover" class="stat-card">
              <el-statistic title="本月新增" :value="stats.monthlyNew">
                <template #suffix>
                  <el-icon><Plus /></el-icon>
                </template>
              </el-statistic>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover" class="stat-card">
              <el-statistic title="总命中次数" :value="stats.totalHits">
                <template #suffix>
                  <el-icon><Aim /></el-icon>
                </template>
              </el-statistic>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="20" style="margin-top: 20px">
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header>
                <span>问题类型分布</span>
              </template>
              <div class="chart-placeholder">
                <el-progress
                  v-for="(item, index) in typeDistribution"
                  :key="index"
                  :percentage="item.percentage"
                  :format="() => item.label"
                  :stroke-width="20"
                  :color="item.color"
                  class="type-progress"
                />
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header>
                <span>热门关键词</span>
              </template>
              <div class="hot-keywords">
                <el-tag
                  v-for="(keyword, index) in hotKeywords"
                  :key="index"
                  :type="getTagType(index)"
                  :size="index < 3 ? 'large' : 'default'"
                  class="hot-tag"
                >
                  {{ keyword }}
                </el-tag>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="20" style="margin-top: 20px">
          <el-col :span="24">
            <el-card shadow="hover">
              <template #header>
                <span>Top 5 高命中经验</span>
              </template>
              <el-table :data="topExperiences" style="width: 100%">
                <el-table-column prop="rank" label="排名" width="80">
                  <template #default="{ row }">
                    <el-tag :type="row.rank <= 3 ? 'danger' : 'info'" effect="dark">
                      {{ row.rank }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="title" label="经验标题" min-width="200" />
                <el-table-column prop="type" label="问题类型" width="120">
                  <template #default="{ row }">
                    <el-tag>{{ row.type }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="hitCount" label="命中次数" width="120" sortable />
                <el-table-column prop="lastHit" label="最近命中" width="180" />
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Edit, Document, Plus, Aim } from '@element-plus/icons-vue'
import ExperienceForm from '@/components/ExperienceForm.vue'
import ExperienceList from '@/components/ExperienceList.vue'

interface Experience {
  id: number
  problemType: string
  keywords: string[]
  diagnosisSteps: Array<{ action: string; target: string }>
  rootCausePattern: {
    pattern: string
    cause: string
    solution: string
  }
  hitCount: number
  createTime: string
  updateTime: string
}

interface TopExperience {
  rank: number
  title: string
  type: string
  hitCount: number
  lastHit: string
}

const activeTab = ref('list')

const stats = reactive({
  totalCount: 156,
  monthlyNew: 23,
  totalHits: 3847
})

const typeDistribution = [
  { label: '系统故障', percentage: 35, color: '#f56c6c' },
  { label: '网络问题', percentage: 25, color: '#e6a23c' },
  { label: '数据库异常', percentage: 20, color: '#409eff' },
  { label: '应用错误', percentage: 12, color: '#67c23a' },
  { label: '安全事件', percentage: 5, color: '#909399' },
  { label: '性能问题', percentage: 3, color: '#b37feb' }
]

const hotKeywords = [
  '权限不足', '连接超时', '内存溢出', 'CPU飙升',
  '网络延迟', '数据库死锁', '配置错误', '服务崩溃',
  '日志分析', '性能优化'
]

const topExperiences = ref<TopExperience[]>([
  { rank: 1, title: 'MySQL连接超时问题排查', type: '数据库异常', hitCount: 256, lastHit: '2024-01-20 15:30' },
  { rank: 2, title: '用户权限不足排查指南', type: '系统故障', hitCount: 198, lastHit: '2024-01-20 14:20' },
  { rank: 3, title: 'JVM内存溢出处理方案', type: '应用错误', hitCount: 156, lastHit: '2024-01-20 11:45' },
  { rank: 4, title: 'Redis连接异常诊断', type: '系统故障', hitCount: 134, lastHit: '2024-01-20 10:15' },
  { rank: 5, title: '网络延迟问题排查', type: '网络问题', hitCount: 89, lastHit: '2024-01-19 16:30' }
])

const getTagType = (index: number): '' | 'success' | 'warning' | 'info' | 'danger' => {
  const types: Array<'' | 'success' | 'warning' | 'info' | 'danger'> = ['danger', 'warning', 'success', 'info', 'info']
  return types[index % types.length]
}

const handleView = (experience: Experience) => {
  ElMessage.info(`查看经验: ${experience.keywords.join(', ')}`)
}

const handleEdit = (experience: Experience) => {
  ElMessage.info(`编辑经验 ID: ${experience.id}`)
}

const handleDelete = (id: number) => {
  stats.totalCount--
  ElMessage.success(`已删除经验 ID: ${id}`)
}

const handleSubmit = () => {
  stats.totalCount++
  stats.monthlyNew++
  ElMessage.success('经验录入成功')
  // 可以切换到列表tab查看
  activeTab.value = 'list'
}
</script>

<style scoped>
.experience-view {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #303133;
}

.stat-card {
  text-align: center;
  padding: 20px 0;
}

.chart-placeholder {
  padding: 10px;
}

.type-progress {
  margin-bottom: 16px;
}

.type-progress:last-child {
  margin-bottom: 0;
}

.hot-keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px;
}

.hot-tag {
  cursor: pointer;
  transition: transform 0.2s;
}

.hot-tag:hover {
  transform: scale(1.1);
}
</style>