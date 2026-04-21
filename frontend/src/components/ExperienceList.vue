<template>
  <div class="experience-list">
    <!-- 搜索筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-row :gutter="20">
        <el-col :span="8">
          <el-select
            v-model="filterType"
            placeholder="问题类型"
            clearable
            style="width: 100%"
            @change="handleFilter"
          >
            <el-option
              v-for="type in problemTypes"
              :key="type.value"
              :label="type.label"
              :value="type.value"
            />
          </el-select>
        </el-col>
        <el-col :span="8">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索关键词"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #append>
              <el-button :icon="Search" @click="handleSearch" />
            </template>
          </el-input>
        </el-col>
        <el-col :span="8">
          <el-button type="primary" @click="handleRefresh">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 经验列表表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        :data="tableData"
        style="width: 100%"
        v-loading="loading"
        @row-click="handleRowClick"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="problemType" label="问题类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getProblemTypeTag(row.problemType)">
              {{ getProblemTypeLabel(row.problemType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="keywords" label="关键词" min-width="200">
          <template #default="{ row }">
            <el-tag
              v-for="(keyword, index) in row.keywords.slice(0, 3)"
              :key="index"
              size="small"
              class="keyword-tag"
            >
              {{ keyword }}
            </el-tag>
            <el-tag v-if="row.keywords.length > 3" size="small" type="info">
              +{{ row.keywords.length - 3 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hitCount" label="命中次数" width="100" sortable>
          <template #default="{ row }">
            <el-badge :value="row.hitCount" type="primary" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleView(row)">
              <el-icon><View /></el-icon>
              查看
            </el-button>
            <el-button link type="primary" @click.stop="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button link type="danger" @click.stop="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="经验详情"
      width="700px"
      destroy-on-close
    >
      <el-descriptions :column="1" border v-if="currentExperience">
        <el-descriptions-item label="问题类型">
          <el-tag>{{ getProblemTypeLabel(currentExperience.problemType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="关键词">
          <el-tag
            v-for="(keyword, index) in currentExperience.keywords"
            :key="index"
            class="keyword-tag"
          >
            {{ keyword }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="诊断链路">
          <el-timeline>
            <el-timeline-item
              v-for="(step, index) in currentExperience.diagnosisSteps"
              :key="index"
              :type="index === 0 ? 'primary' : 'info'"
            >
              <strong>{{ step.action }}</strong>
              <br />
              <span class="step-target">{{ step.target }}</span>
            </el-timeline-item>
          </el-timeline>
        </el-descriptions-item>
        <el-descriptions-item label="根因模式">
          <el-card shadow="never" class="pattern-detail">
            <p><strong>Pattern:</strong> {{ currentExperience.rootCausePattern.pattern }}</p>
            <p><strong>Cause:</strong> {{ currentExperience.rootCausePattern.cause }}</p>
            <p><strong>Solution:</strong> {{ currentExperience.rootCausePattern.solution }}</p>
          </el-card>
        </el-descriptions-item>
        <el-descriptions-item label="命中次数">
          <el-badge :value="currentExperience.hitCount" type="success" />
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ currentExperience.createTime }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ currentExperience.updateTime }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="editVisible"
      title="编辑经验"
      width="700px"
      destroy-on-close
    >
      <el-form :model="editForm" label-width="100px" v-if="currentExperience">
        <el-form-item label="问题类型">
          <el-select v-model="editForm.problemType" style="width: 100%">
            <el-option
              v-for="type in problemTypes"
              :key="type.value"
              :label="type.label"
              :value="type.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-tag
            v-for="keyword in editForm.keywords"
            :key="keyword"
            closable
            @close="handleEditRemoveKeyword(keyword)"
            class="keyword-tag"
          >
            {{ keyword }}
          </el-tag>
          <el-input
            v-model="editKeywordInput"
            class="keyword-input"
            placeholder="回车添加"
            @keyup.enter="handleEditAddKeyword"
          />
        </el-form-item>
        <el-form-item label="根因模式">
          <el-input
            v-model="editForm.rootCausePattern.pattern"
            type="textarea"
            :rows="2"
            placeholder="Pattern"
          />
          <el-input
            v-model="editForm.rootCausePattern.cause"
            type="textarea"
            :rows="2"
            placeholder="Cause"
            style="margin-top: 10px"
          />
          <el-input
            v-model="editForm.rootCausePattern.solution"
            type="textarea"
            :rows="2"
            placeholder="Solution"
            style="margin-top: 10px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, View, Edit, Delete } from '@element-plus/icons-vue'

interface DiagnosisStep {
  action: string
  target: string
}

interface RootCausePattern {
  pattern: string
  cause: string
  solution: string
}

interface Experience {
  id: number
  problemType: string
  keywords: string[]
  diagnosisSteps: DiagnosisStep[]
  rootCausePattern: RootCausePattern
  hitCount: number
  createTime: string
  updateTime: string
}

const emit = defineEmits<{
  (e: 'edit', experience: Experience): void
  (e: 'delete', id: number): void
  (e: 'view', experience: Experience): void
}>()

const problemTypes = [
  { label: '系统故障', value: 'system' },
  { label: '网络问题', value: 'network' },
  { label: '数据库异常', value: 'database' },
  { label: '应用错误', value: 'application' },
  { label: '安全事件', value: 'security' },
  { label: '性能问题', value: 'performance' }
]

const loading = ref(false)
const filterType = ref('')
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const detailVisible = ref(false)
const editVisible = ref(false)
const currentExperience = ref<Experience | null>(null)
const editKeywordInput = ref('')

const editForm = reactive({
  problemType: '',
  keywords: [] as string[],
  rootCausePattern: {
    pattern: '',
    cause: '',
    solution: ''
  }
})

// 模拟数据
const tableData = ref<Experience[]>([
  {
    id: 1,
    problemType: 'system',
    keywords: ['权限不足', '登录失败', 'LDAP'],
    diagnosisSteps: [
      { action: '检查LDAP连接', target: 'LDAP服务器' },
      { action: '验证用户权限', target: '权限管理系统' },
      { action: '同步权限配置', target: '业务系统' }
    ],
    rootCausePattern: {
      pattern: '用户登录返回权限不足错误',
      cause: 'LDAP权限同步延迟，导致用户权限未及时更新',
      solution: '手动触发权限同步，或等待自动同步周期'
    },
    hitCount: 128,
    createTime: '2024-01-15 10:30:00',
    updateTime: '2024-01-20 14:20:00'
  },
  {
    id: 2,
    problemType: 'database',
    keywords: ['MySQL', '连接超时', '连接池'],
    diagnosisSteps: [
      { action: '检查数据库状态', target: 'MySQL实例' },
      { action: '查看连接数', target: '数据库监控' },
      { action: '分析慢查询', target: '慢查询日志' }
    ],
    rootCausePattern: {
      pattern: '应用连接数据库超时',
      cause: '数据库连接池已满，无法获取新连接',
      solution: '增加最大连接数配置，优化慢查询SQL'
    },
    hitCount: 256,
    createTime: '2024-01-14 09:15:00',
    updateTime: '2024-01-18 11:30:00'
  },
  {
    id: 3,
    problemType: 'network',
    keywords: ['网络延迟', '丢包', '防火墙'],
    diagnosisSteps: [
      { action: 'ping测试', target: '目标服务器' },
      { action: 'traceroute追踪', target: '网络路径' },
      { action: '检查防火墙规则', target: '防火墙配置' }
    ],
    rootCausePattern: {
      pattern: '服务间调用延迟高或超时',
      cause: '防火墙规则变更导致部分端口被阻断',
      solution: '检查并恢复防火墙规则，开放必要端口'
    },
    hitCount: 64,
    createTime: '2024-01-13 16:45:00',
    updateTime: '2024-01-16 10:00:00'
  },
  {
    id: 4,
    problemType: 'application',
    keywords: ['OOM', '内存溢出', 'JVM'],
    diagnosisSteps: [
      { action: '检查JVM内存使用', target: '应用服务器' },
      { action: '分析堆内存', target: 'Heap Dump' },
      { action: '定位内存泄漏', target: 'MAT分析工具' }
    ],
    rootCausePattern: {
      pattern: '应用服务OOM崩溃或频繁GC',
      cause: '代码存在内存泄漏，对象未正确释放',
      solution: '修复内存泄漏代码，调整JVM内存参数'
    },
    hitCount: 89,
    createTime: '2024-01-12 14:20:00',
    updateTime: '2024-01-15 09:30:00'
  },
  {
    id: 5,
    problemType: 'performance',
    keywords: ['CPU飙升', '负载高', '线程阻塞'],
    diagnosisSteps: [
      { action: '查看CPU使用率', target: '系统监控' },
      { action: '分析线程堆栈', target: 'Thread Dump' },
      { action: '定位热点代码', target: '性能分析工具' }
    ],
    rootCausePattern: {
      pattern: '服务器CPU使用率持续升高',
      cause: '存在死循环或频繁GC或线程阻塞',
      solution: '优化热点代码，调整线程池配置'
    },
    hitCount: 156,
    createTime: '2024-01-11 11:30:00',
    updateTime: '2024-01-14 15:45:00'
  }
])

total.value = tableData.value.length

const getProblemTypeLabel = (value: string): string => {
  const type = problemTypes.find(t => t.value === value)
  return type ? type.label : value
}

const getProblemTypeTag = (value: string): string => {
  const typeMap: Record<string, string> = {
    system: 'danger',
    network: 'warning',
    database: 'info',
    application: 'primary',
    security: 'danger',
    performance: 'success'
  }
  return typeMap[value] || 'info'
}

const handleFilter = () => {
  currentPage.value = 1
  // 实际应用中调用API进行筛选
  ElMessage.info(`筛选类型: ${filterType.value || '全部'}`)
}

const handleSearch = () => {
  currentPage.value = 1
  // 实际应用中调用API进行搜索
  ElMessage.info(`搜索关键词: ${searchKeyword.value}`)
}

const handleRefresh = () => {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    ElMessage.success('刷新成功')
  }, 500)
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
}

const handleRowClick = (row: Experience) => {
  currentExperience.value = row
  detailVisible.value = true
}

const handleView = (row: Experience) => {
  currentExperience.value = row
  detailVisible.value = true
  emit('view', row)
}

const handleEdit = (row: Experience) => {
  currentExperience.value = row
  editForm.problemType = row.problemType
  editForm.keywords = [...row.keywords]
  editForm.rootCausePattern = { ...row.rootCausePattern }
  editVisible.value = true
  emit('edit', row)
}

const handleDelete = (row: Experience) => {
  ElMessageBox.confirm(`确定删除该经验记录？命中次数: ${row.hitCount}`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    const index = tableData.value.findIndex(item => item.id === row.id)
    if (index > -1) {
      tableData.value.splice(index, 1)
      total.value = tableData.value.length
      emit('delete', row.id)
      ElMessage.success('删除成功')
    }
  })
}

const handleEditAddKeyword = () => {
  const keyword = editKeywordInput.value.trim()
  if (keyword && !editForm.keywords.includes(keyword)) {
    editForm.keywords.push(keyword)
    editKeywordInput.value = ''
  }
}

const handleEditRemoveKeyword = (keyword: string) => {
  const index = editForm.keywords.indexOf(keyword)
  if (index > -1) {
    editForm.keywords.splice(index, 1)
  }
}

const handleSaveEdit = () => {
  if (currentExperience.value) {
    const index = tableData.value.findIndex(item => item.id === currentExperience.value!.id)
    if (index > -1) {
      tableData.value[index].problemType = editForm.problemType
      tableData.value[index].keywords = [...editForm.keywords]
      tableData.value[index].rootCausePattern = { ...editForm.rootCausePattern }
      tableData.value[index].updateTime = new Date().toLocaleString()
    }
  }
  editVisible.value = false
  ElMessage.success('保存成功')
}

onMounted(() => {
  // 初始化加载数据
})
</script>

<style scoped>
.experience-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-card {
  border-radius: 8px;
}

.table-card {
  border-radius: 8px;
}

.keyword-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

.step-target {
  color: #909399;
  font-size: 12px;
}

.pattern-detail {
  background-color: #f5f7fa;
}

.pattern-detail p {
  margin: 8px 0;
}

.keyword-input {
  width: 150px;
  margin-left: 10px;
}
</style>