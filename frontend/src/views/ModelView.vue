<template>
  <div class="model-view">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 模型列表 Tab -->
      <el-tab-pane label="模型配置" name="list">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><Cpu /></el-icon>
              <span>AI模型管理</span>
              <el-button type="primary" size="small" @click="showCreateDialog" style="margin-left: auto">
                <el-icon><Plus /></el-icon>
                新增模型
              </el-button>
            </div>
          </template>

          <el-table :data="models" v-loading="loading" style="width: 100%">
            <el-table-column prop="modelName" label="配置名称" width="150">
              <template #default="{ row }">
                <el-tag :type="row.isDefault ? 'success' : 'info'">
                  {{ row.modelName }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="provider" label="提供商" width="100">
              <template #default="{ row }">
                {{ getProviderDisplayName(row.provider) }}
              </template>
            </el-table-column>
            <el-table-column prop="modelId" label="模型ID" width="150" />
            <el-table-column prop="apiEndpoint" label="API地址" min-width="200" show-overflow-tooltip />
            <el-table-column prop="maxTokens" label="最大Token" width="100" />
            <el-table-column prop="temperature" label="温度" width="80" />
            <el-table-column prop="isDefault" label="默认" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.isDefault" type="success" effect="dark">默认</el-tag>
                <el-button v-else size="small" text @click="setDefault(row.modelName)">设为默认</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" @change="handleEnableChange(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button-group>
                  <el-button size="small" @click="testModelConnection(row)">
                    <el-icon><VideoPlay /></el-icon>
                    测试
                  </el-button>
                  <el-button size="small" @click="showEditDialog(row)">
                    <el-icon><Edit /></el-icon>
                    编辑
                  </el-button>
                  <el-button size="small" type="danger" @click="handleDelete(row)">
                    <el-icon><Delete /></el-icon>
                    删除
                  </el-button>
                </el-button-group>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 提供商信息 Tab -->
      <el-tab-pane label="支持的提供商" name="providers">
        <el-row :gutter="20">
          <el-col :span="8" v-for="provider in providers" :key="provider.name">
            <el-card shadow="hover" class="provider-card">
              <template #header>
                <div class="provider-header">
                  <span class="provider-name">{{ provider.displayName }}</span>
                  <el-tag size="small">{{ provider.name }}</el-tag>
                </div>
              </template>
              <div class="provider-content">
                <p><strong>默认API地址:</strong></p>
                <el-input :value="provider.defaultEndpoint" size="small" readonly />
                <p style="margin-top: 15px"><strong>可用模型:</strong></p>
                <div class="model-tags">
                  <el-tag v-for="model in provider.models" :key="model" size="small" class="model-tag">
                    {{ model }}
                  </el-tag>
                </div>
                <el-button type="primary" size="small" style="margin-top: 15px; width: 100%"
                           @click="quickCreate(provider)">
                  快速配置
                </el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/编辑模型对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑模型' : '新增模型'"
      width="600px"
      destroy-on-close
    >
      <el-form :model="formData" label-width="100px" ref="formRef">
        <el-form-item label="配置名称" prop="modelName" :rules="[{ required: true, message: '请输入配置名称' }]">
          <el-input v-model="formData.modelName" :disabled="isEdit" placeholder="如: glm-4-flash-config" />
        </el-form-item>

        <el-form-item label="提供商" prop="provider" :rules="[{ required: true, message: '请选择提供商' }]">
          <el-select v-model="formData.provider" @change="onProviderChange" style="width: 100%">
            <el-option v-for="p in providers" :key="p.name" :label="p.displayName" :value="p.name" />
          </el-select>
        </el-form-item>

        <el-form-item label="模型ID" prop="modelId" :rules="[{ required: true, message: '请选择或输入模型ID' }]">
          <el-select v-model="formData.modelId" filterable allow-create style="width: 100%">
            <el-option v-for="m in currentProviderModels" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>

        <el-form-item label="API地址" prop="apiEndpoint" :rules="[{ required: true, message: '请输入API地址' }]">
          <el-input v-model="formData.apiEndpoint" placeholder="https://api.example.com/v1/chat" />
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="formData.apiKey" type="password" show-password placeholder="API密钥" />
        </el-form-item>

        <el-divider content-position="left">参数配置</el-divider>

        <el-form-item label="最大Token">
          <el-input-number v-model="formData.maxTokens" :min="100" :max="32000" />
        </el-form-item>

        <el-form-item label="温度">
          <el-slider v-model="formData.temperature" :min="0" :max="2" :step="0.1" show-input />
        </el-form-item>

        <el-form-item label="Top P">
          <el-slider v-model="formData.topP" :min="0" :max="1" :step="0.1" show-input />
        </el-form-item>

        <el-form-item label="超时时间(秒)">
          <el-input-number v-model="formData.timeoutSeconds" :min="10" :max="300" />
        </el-form-item>

        <el-form-item label="最大重试">
          <el-input-number v-model="formData.maxRetries" :min="0" :max="10" />
        </el-form-item>

        <el-form-item label="设为默认">
          <el-switch v-model="formData.isDefault" />
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="formData.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ isEdit ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 测试结果对话框 -->
    <el-dialog v-model="testDialogVisible" title="模型连接测试" width="500px">
      <div v-if="testResult" class="test-result">
        <el-result
          :icon="testResult.success ? 'success' : 'error'"
          :title="testResult.success ? '连接成功' : '连接失败'"
        >
          <template #sub-title>
            <p>模型: {{ testResult.modelName }}</p>
            <p>提供商: {{ testResult.provider }}</p>
          </template>
          <template #extra>
            <div v-if="testResult.success && testResult.responsePreview" class="response-preview">
              <p><strong>响应预览:</strong></p>
              <pre>{{ testResult.responsePreview }}</pre>
            </div>
            <div v-if="!testResult.success" class="error-info">
              <p><strong>错误信息:</strong></p>
              <el-alert type="error" :title="testResult.error" show-icon />
            </div>
          </template>
        </el-result>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Cpu, Plus, VideoPlay, Edit, Delete } from '@element-plus/icons-vue'
import { modelApi, ModelConfig, ProviderInfo } from '@/services/models'

const activeTab = ref('list')
const loading = ref(false)
const models = ref<ModelConfig[]>([])
const providers = ref<ProviderInfo[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()

const formData = reactive<ModelConfig>({
  modelName: '',
  provider: '',
  apiEndpoint: '',
  apiKey: '',
  modelId: '',
  maxTokens: 4096,
  temperature: 0.7,
  topP: 0.9,
  timeoutSeconds: 120,
  maxRetries: 3,
  isDefault: false,
  enabled: true
})

const testDialogVisible = ref(false)
const testResult = ref<any>(null)

const currentProviderModels = computed(() => {
  const p = providers.value.find(p => p.name === formData.provider)
  return p?.models || []
})

onMounted(() => {
  loadModels()
  loadProviders()
})

const loadModels = async () => {
  loading.value = true
  try {
    models.value = await modelApi.getModels()
  } catch (e) {
    ElMessage.error('加载模型列表失败')
  } finally {
    loading.value = false
  }
}

const loadProviders = async () => {
  try {
    providers.value = await modelApi.getProviders()
  } catch (e) {
    ElMessage.error('加载提供商信息失败')
  }
}

const getProviderDisplayName = (name: string): string => {
  const p = providers.value.find(p => p.name === name)
  return p?.displayName || name
}

const onProviderChange = (provider: string) => {
  const p = providers.value.find(p => p.name === provider)
  if (p) {
    formData.apiEndpoint = p.defaultEndpoint
    formData.modelId = p.models[0] || ''
  }
}

const showCreateDialog = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const showEditDialog = (model: ModelConfig) => {
  isEdit.value = true
  Object.assign(formData, model)
  dialogVisible.value = true
}

const quickCreate = (provider: ProviderInfo) => {
  isEdit.value = false
  resetForm()
  formData.provider = provider.name
  formData.apiEndpoint = provider.defaultEndpoint
  formData.modelId = provider.models[0] || ''
  formData.modelName = `${provider.name.toLowerCase()}-config`
  dialogVisible.value = true
}

const resetForm = () => {
  formData.modelName = ''
  formData.provider = ''
  formData.apiEndpoint = ''
  formData.apiKey = ''
  formData.modelId = ''
  formData.maxTokens = 4096
  formData.temperature = 0.7
  formData.topP = 0.9
  formData.timeoutSeconds = 120
  formData.maxRetries = 3
  formData.isDefault = false
  formData.enabled = true
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    if (isEdit.value) {
      await modelApi.updateModel(formData.modelName, formData)
      ElMessage.success('模型已更新')
    } else {
      await modelApi.createModel(formData)
      ElMessage.success('模型已创建')
    }
    dialogVisible.value = false
    loadModels()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const setDefault = async (modelName: string) => {
  try {
    await modelApi.setDefaultModel(modelName)
    ElMessage.success('已设为默认模型')
    loadModels()
  } catch (e) {
    ElMessage.error('设置失败')
  }
}

const handleEnableChange = async (model: ModelConfig) => {
  try {
    await modelApi.updateModel(model.modelName, { enabled: model.enabled })
    ElMessage.success(`模型已${model.enabled ? '启用' : '禁用'}`)
  } catch (e) {
    ElMessage.error('更新失败')
    model.enabled = !model.enabled
  }
}

const testModelConnection = async (model: ModelConfig) => {
  testResult.value = null
  testDialogVisible.value = true
  try {
    testResult.value = await modelApi.testModel(model.modelName)
  } catch (e: any) {
    testResult.value = { success: false, modelName: model.modelName, error: e.message }
  }
}

const handleDelete = async (model: ModelConfig) => {
  try {
    await ElMessageBox.confirm(`确认删除模型配置 ${model.modelName}?`, '删除确认', { type: 'warning' })
    await modelApi.deleteModel(model.modelName)
    ElMessage.success('模型已删除')
    loadModels()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}
</script>

<style scoped>
.model-view {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.provider-card {
  height: 100%;
}

.provider-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.provider-name {
  font-weight: 600;
  font-size: 16px;
}

.provider-content p {
  margin: 5px 0;
  color: #606266;
}

.model-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.model-tag {
  margin: 2px;
}

.test-result {
  padding: 20px;
}

.response-preview pre {
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow: auto;
}

.error-info {
  margin-top: 10px;
}
</style>