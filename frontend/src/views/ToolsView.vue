<template>
  <div class="tools-view">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 工具列表 Tab -->
      <el-tab-pane label="工具列表" name="list">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><Tools /></el-icon>
              <span>MCP工具管理</span>
              <el-button type="primary" size="small" @click="showCreateDialog" style="margin-left: auto">
                <el-icon><Plus /></el-icon>
                新增工具
              </el-button>
              <el-button size="small" @click="refreshTools">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>

          <el-table :data="tools" v-loading="loading" style="width: 100%">
            <el-table-column prop="toolName" label="工具名称" width="180">
              <template #default="{ row }">
                <el-tag :type="row.isDynamic ? 'success' : 'info'" effect="plain">
                  {{ row.toolName }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column prop="toolType" label="类型" width="100">
              <template #default="{ row }">
                <el-tag :type="getToolTypeTag(row.toolType)">
                  {{ row.toolType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="isDynamic" label="动态配置" width="100">
              <template #default="{ row }">
                <el-tag :type="row.isDynamic ? 'success' : 'warning'" size="small">
                  {{ row.isDynamic ? '可编辑' : '静态' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{ row }">
                <el-switch
                  v-if="row.isDynamic"
                  v-model="row.enabled"
                  @change="handleEnableChange(row)"
                />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button-group>
                  <el-button size="small" @click="showTestDialog(row)">
                    <el-icon><VideoPlay /></el-icon>
                    测试
                  </el-button>
                  <el-button size="small" @click="showEditDialog(row)" v-if="row.isDynamic">
                    <el-icon><Edit /></el-icon>
                    编辑
                  </el-button>
                  <el-button size="small" type="danger" @click="handleDelete(row)" v-if="row.isDynamic">
                    <el-icon><Delete /></el-icon>
                    删除
                  </el-button>
                </el-button-group>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/编辑工具对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑工具' : '新增工具'"
      width="700px"
      destroy-on-close
    >
      <el-form :model="formData" label-width="120px" ref="formRef">
        <el-form-item label="工具名称" prop="toolName" :rules="[{ required: true, message: '请输入工具名称' }]">
          <el-input v-model="formData.toolName" :disabled="isEdit" placeholder="唯一标识符，如: search_knowledge" />
        </el-form-item>

        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="工具功能描述" />
        </el-form-item>

        <el-form-item label="工具类型" prop="toolType" :rules="[{ required: true, message: '请选择工具类型' }]">
          <el-select v-model="formData.toolType" style="width: 100%">
            <el-option label="HTTP API" value="HTTP" />
            <el-option label="Mock模拟" value="MOCK" />
          </el-select>
        </el-form-item>

        <!-- HTTP配置 -->
        <template v-if="formData.toolType === 'HTTP'">
          <el-form-item label="接口地址" prop="endpointUrl" :rules="[{ required: true, message: '请输入接口地址' }]">
            <el-input v-model="formData.endpointUrl" placeholder="http://api.example.com/search" />
          </el-form-item>

          <el-form-item label="HTTP方法">
            <el-select v-model="formData.httpMethod" style="width: 100%">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="DELETE" value="DELETE" />
            </el-select>
          </el-form-item>

          <el-form-item label="认证类型">
            <el-select v-model="formData.authType" style="width: 100%">
              <el-option label="无认证" value="NONE" />
              <el-option label="API Key" value="API_KEY" />
              <el-option label="Basic Auth" value="BASIC" />
            </el-select>
          </el-form-item>

          <!-- API Key配置 -->
          <template v-if="formData.authType === 'API_KEY'">
            <el-form-item label="Header名称">
              <el-input v-model="formData.authConfig!.header" placeholder="X-API-Key" />
            </el-form-item>
            <el-form-item label="API Key">
              <el-input v-model="formData.authConfig!.key" placeholder="your-api-key" />
            </el-form-item>
          </template>

          <!-- Basic Auth配置 -->
          <template v-if="formData.authType === 'BASIC'">
            <el-form-item label="用户名">
              <el-input v-model="formData.authConfig!.username" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="formData.authConfig!.password" type="password" show-password />
            </el-form-item>
          </template>

          <el-form-item label="请求模板">
            <el-input
              v-model="formData.requestTemplate"
              type="textarea"
              :rows="4"
              placeholder="JSON格式，支持 ${params.xxx} 参数替换"
            />
          </el-form-item>

          <el-form-item label="响应映射">
            <el-input v-model="formData.responseMapping" placeholder="JSONPath，如: data.result" />
          </el-form-item>

          <el-form-item label="超时时间(ms)">
            <el-input-number v-model="formData.timeoutMs" :min="1000" :max="60000" />
          </el-form-item>
        </template>

        <el-divider content-position="left">输入参数定义</el-divider>

        <el-form-item label="参数列表">
          <div class="params-editor">
            <div v-for="(param, index) in formData.inputParams" :key="index" class="param-row">
              <el-input v-model="param.name" placeholder="参数名" style="width: 120px" />
              <el-select v-model="param.type" style="width: 100px">
                <el-option label="string" value="string" />
                <el-option label="number" value="number" />
                <el-option label="boolean" value="boolean" />
              </el-select>
              <el-checkbox v-model="param.required">必填</el-checkbox>
              <el-input v-model="param.description" placeholder="描述" style="width: 150px" />
              <el-button type="danger" size="small" @click="removeParam(index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button type="primary" size="small" @click="addParam">
              <el-icon><Plus /></el-icon>
              添加参数
            </el-button>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ isEdit ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 测试工具对话框 -->
    <el-dialog v-model="testDialogVisible" title="测试工具" width="600px">
      <el-form label-width="80px">
        <el-form-item label="工具名称">
          <el-tag>{{ testTool?.toolName }}</el-tag>
        </el-form-item>

        <el-form-item label="测试参数">
          <el-input
            v-model="testParamsJson"
            type="textarea"
            :rows="6"
            placeholder="JSON格式的测试参数"
          />
        </el-form-item>

        <el-form-item label="执行结果">
          <div v-if="testResult" class="test-result">
            <el-tag :type="testResult.success ? 'success' : 'danger'" size="large">
              {{ testResult.success ? '成功' : '失败' }}
            </el-tag>
            <span v-if="testResult.executionTime">耗时: {{ testResult.executionTime }}ms</span>
            <pre class="result-data">{{ JSON.stringify(testResult.data || testResult.error, null, 2) }}</pre>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="testDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="executeTest" :loading="testing">
          执行测试
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tools, Plus, Refresh, VideoPlay, Edit, Delete } from '@element-plus/icons-vue'
import { toolsApi, ToolConfig, ToolTestResult } from '@/services/tools'

const activeTab = ref('list')
const loading = ref(false)
const tools = ref<ToolConfig[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()

const formData = reactive<ToolConfig>({
  toolName: '',
  description: '',
  inputParams: [],
  toolType: 'HTTP',
  endpointUrl: '',
  httpMethod: 'GET',
  authType: 'NONE',
  authConfig: { header: '', key: '', username: '', password: '' },
  requestTemplate: '',
  responseMapping: '',
  timeoutMs: 30000,
  enabled: true
})

const testDialogVisible = ref(false)
const testTool = ref<ToolConfig | null>(null)
const testParamsJson = ref('{\n  "param1": "value1"\n}')
const testResult = ref<ToolTestResult | null>(null)
const testing = ref(false)

onMounted(() => {
  loadTools()
})

const loadTools = async () => {
  loading.value = true
  try {
    tools.value = await toolsApi.getTools()
  } catch (e) {
    ElMessage.error('加载工具列表失败')
  } finally {
    loading.value = false
  }
}

const refreshTools = async () => {
  try {
    await toolsApi.refreshTools()
    ElMessage.success('工具已刷新')
    loadTools()
  } catch (e) {
    ElMessage.error('刷新失败')
  }
}

const getToolTypeTag = (type: string): 'success' | 'warning' | 'info' | 'danger' => {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
    'HTTP': 'success',
    'MOCK': 'warning',
    'STATIC': 'info'
  }
  return map[type] || 'info'
}

const showCreateDialog = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const showEditDialog = (tool: ToolConfig) => {
  isEdit.value = true
  Object.assign(formData, tool)
  if (!formData.authConfig) formData.authConfig = {}
  if (!formData.inputParams) formData.inputParams = []
  dialogVisible.value = true
}

const showTestDialog = (tool: ToolConfig) => {
  testTool.value = tool
  testParamsJson.value = '{\n  "param1": "value1"\n}'
  testResult.value = null
  testDialogVisible.value = true
}

const resetForm = () => {
  formData.toolName = ''
  formData.description = ''
  formData.inputParams = []
  formData.toolType = 'HTTP'
  formData.endpointUrl = ''
  formData.httpMethod = 'GET'
  formData.authType = 'NONE'
  formData.authConfig = { header: '', key: '', username: '', password: '' }
  formData.requestTemplate = ''
  formData.responseMapping = ''
  formData.timeoutMs = 30000
  formData.enabled = true
}

const addParam = () => {
  if (!formData.inputParams) formData.inputParams = []
  formData.inputParams.push({
    name: '',
    type: 'string',
    required: false,
    description: ''
  })
}

const removeParam = (index: number) => {
  if (formData.inputParams) formData.inputParams.splice(index, 1)
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    if (isEdit.value) {
      await toolsApi.updateTool(formData.toolName, formData)
      ElMessage.success('工具已更新')
    } else {
      await toolsApi.createTool(formData)
      ElMessage.success('工具已创建')
    }
    dialogVisible.value = false
    loadTools()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (tool: ToolConfig) => {
  try {
    await ElMessageBox.confirm(`确认删除工具 ${tool.toolName}?`, '删除确认', {
      type: 'warning'
    })
    await toolsApi.deleteTool(tool.toolName)
    ElMessage.success('工具已删除')
    loadTools()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleEnableChange = async (tool: ToolConfig) => {
  try {
    await toolsApi.updateTool(tool.toolName, { enabled: tool.enabled } as ToolConfig)
    ElMessage.success(`工具已${tool.enabled ? '启用' : '禁用'}`)
  } catch (e) {
    ElMessage.error('更新失败')
    tool.enabled = !tool.enabled
  }
}

const executeTest = async () => {
  testing.value = true
  testResult.value = null
  try {
    const params = JSON.parse(testParamsJson.value)
    testResult.value = await toolsApi.testTool(testTool.value!.toolName, params)
  } catch (e: any) {
    testResult.value = {
      success: false,
      error: e.message || '参数解析错误'
    }
  } finally {
    testing.value = false
  }
}
</script>

<style scoped>
.tools-view {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.params-editor {
  width: 100%;
}

.param-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}

.test-result {
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
}

.result-data {
  margin: 10px 0;
  padding: 10px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>