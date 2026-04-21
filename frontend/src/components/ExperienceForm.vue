<template>
  <div class="experience-form">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="120px"
      label-position="top"
    >
      <!-- 问题类型 -->
      <el-form-item label="问题类型" prop="problemType">
        <el-select
          v-model="formData.problemType"
          placeholder="请选择问题类型"
          style="width: 100%"
        >
          <el-option
            v-for="type in problemTypes"
            :key="type.value"
            :label="type.label"
            :value="type.value"
          />
        </el-select>
      </el-form-item>

      <!-- 关键词 -->
      <el-form-item label="关键词" prop="keywords">
        <el-tag
          v-for="tag in formData.keywords"
          :key="tag"
          closable
          @close="handleRemoveKeyword(tag)"
          class="keyword-tag"
        >
          {{ tag }}
        </el-tag>
        <el-input
          v-model="keywordInput"
          class="keyword-input"
          placeholder="输入关键词后按回车添加"
          @keyup.enter="handleAddKeyword"
        />
      </el-form-item>

      <!-- 诊断链路 -->
      <el-form-item label="诊断链路" prop="diagnosisSteps">
        <div class="steps-container">
          <div
            v-for="(step, index) in formData.diagnosisSteps"
            :key="index"
            class="step-item"
          >
            <div class="step-header">
              <el-tag type="info" size="small">步骤 {{ index + 1 }}</el-tag>
              <el-button
                type="danger"
                link
                size="small"
                @click="handleRemoveStep(index)"
                v-if="formData.diagnosisSteps.length > 1"
              >
                删除
              </el-button>
            </div>
            <el-input
              v-model="step.action"
              placeholder="步骤动作"
              class="step-input"
            />
            <el-input
              v-model="step.target"
              placeholder="目标/系统"
              class="step-input"
            />
          </div>
          <el-button type="primary" link @click="handleAddStep">
            <el-icon><Plus /></el-icon>
            添加步骤
          </el-button>
        </div>
      </el-form-item>

      <!-- 根因模式 -->
      <el-form-item label="根因模式" prop="rootCausePattern">
        <div class="pattern-container">
          <el-card class="pattern-card" shadow="never">
            <template #header>
              <div class="pattern-header">
                <span>Pattern - Cause - Solution</span>
              </div>
            </template>
            <el-form-item label="模式特征 (Pattern)">
              <el-input
                v-model="formData.rootCausePattern.pattern"
                type="textarea"
                :rows="2"
                placeholder="描述问题的特征模式..."
              />
            </el-form-item>
            <el-form-item label="根因描述 (Cause)">
              <el-input
                v-model="formData.rootCausePattern.cause"
                type="textarea"
                :rows="2"
                placeholder="描述问题的根本原因..."
              />
            </el-form-item>
            <el-form-item label="解决方案 (Solution)">
              <el-input
                v-model="formData.rootCausePattern.solution"
                type="textarea"
                :rows="2"
                placeholder="描述解决方案..."
              />
            </el-form-item>
          </el-card>
        </div>
      </el-form-item>

      <!-- 备注 -->
      <el-form-item label="备注">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          placeholder="其他补充说明..."
        />
      </el-form-item>

      <!-- 操作按钮 -->
      <el-form-item>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          <el-icon><Check /></el-icon>
          提交经验
        </el-button>
        <el-button @click="handlePreview">
          <el-icon><View /></el-icon>
          预览
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 预览对话框 -->
    <el-dialog
      v-model="previewVisible"
      title="经验预览"
      width="600px"
      destroy-on-close
    >
      <div class="preview-content">
        <div class="preview-item">
          <span class="preview-label">问题类型:</span>
          <el-tag>{{ getProblemTypeLabel(formData.problemType) }}</el-tag>
        </div>
        <div class="preview-item">
          <span class="preview-label">关键词:</span>
          <div class="preview-tags">
            <el-tag
              v-for="keyword in formData.keywords"
              :key="keyword"
              class="preview-tag"
            >
              {{ keyword }}
            </el-tag>
          </div>
        </div>
        <div class="preview-item">
          <span class="preview-label">诊断链路:</span>
          <el-steps direction="vertical" :active="formData.diagnosisSteps.length">
            <el-step
              v-for="(step, index) in formData.diagnosisSteps"
              :key="index"
              :title="step.action"
              :description="step.target"
            />
          </el-steps>
        </div>
        <div class="preview-item">
          <span class="preview-label">根因模式:</span>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="Pattern">
              {{ formData.rootCausePattern.pattern || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Cause">
              {{ formData.rootCausePattern.cause || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Solution">
              {{ formData.rootCausePattern.solution || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
        <div class="preview-item" v-if="formData.remark">
          <span class="preview-label">备注:</span>
          <p>{{ formData.remark }}</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Check, View, Refresh } from '@element-plus/icons-vue'

interface DiagnosisStep {
  action: string
  target: string
}

interface RootCausePattern {
  pattern: string
  cause: string
  solution: string
}

interface ExperienceFormData {
  problemType: string
  keywords: string[]
  diagnosisSteps: DiagnosisStep[]
  rootCausePattern: RootCausePattern
  remark: string
}

const emit = defineEmits<{
  (e: 'submit', data: ExperienceFormData): void
}>()

const formRef = ref<FormInstance>()
const keywordInput = ref('')
const submitting = ref(false)
const previewVisible = ref(false)

const problemTypes = [
  { label: '系统故障', value: 'system' },
  { label: '网络问题', value: 'network' },
  { label: '数据库异常', value: 'database' },
  { label: '应用错误', value: 'application' },
  { label: '安全事件', value: 'security' },
  { label: '性能问题', value: 'performance' }
]

const formData = reactive<ExperienceFormData>({
  problemType: '',
  keywords: [],
  diagnosisSteps: [
    { action: '', target: '' }
  ],
  rootCausePattern: {
    pattern: '',
    cause: '',
    solution: ''
  },
  remark: ''
})

const formRules: FormRules = {
  problemType: [
    { required: true, message: '请选择问题类型', trigger: 'change' }
  ],
  keywords: [
    { type: 'array', min: 1, message: '请至少添加一个关键词', trigger: 'change' }
  ],
  diagnosisSteps: [
    {
      validator: (_rule, value: DiagnosisStep[], callback) => {
        const valid = value.some(step => step.action && step.target)
        if (!valid) {
          callback(new Error('请至少完整填写一个诊断步骤'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  rootCausePattern: [
    {
      validator: (_rule, value: RootCausePattern, callback) => {
        if (!value.pattern || !value.cause || !value.solution) {
          callback(new Error('请完整填写根因模式'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

const getProblemTypeLabel = (value: string): string => {
  const type = problemTypes.find(t => t.value === value)
  return type ? type.label : value
}

const handleAddKeyword = () => {
  const keyword = keywordInput.value.trim()
  if (keyword && !formData.keywords.includes(keyword)) {
    formData.keywords.push(keyword)
    keywordInput.value = ''
  }
}

const handleRemoveKeyword = (keyword: string) => {
  const index = formData.keywords.indexOf(keyword)
  if (index > -1) {
    formData.keywords.splice(index, 1)
  }
}

const handleAddStep = () => {
  formData.diagnosisSteps.push({ action: '', target: '' })
}

const handleRemoveStep = (index: number) => {
  formData.diagnosisSteps.splice(index, 1)
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate((valid) => {
    if (valid) {
      submitting.value = true
      // 模拟提交
      setTimeout(() => {
        emit('submit', { ...formData })
        ElMessage.success('经验提交成功')
        submitting.value = false
        handleReset()
      }, 1000)
    }
  })
}

const handlePreview = () => {
  previewVisible.value = true
}

const handleReset = () => {
  formRef.value?.resetFields()
  formData.keywords = []
  formData.diagnosisSteps = [{ action: '', target: '' }]
  formData.rootCausePattern = { pattern: '', cause: '', solution: '' }
  formData.remark = ''
}
</script>

<style scoped>
.experience-form {
  padding: 10px;
}

.keyword-tag {
  margin-right: 10px;
  margin-bottom: 10px;
}

.keyword-input {
  width: 200px;
  vertical-align: bottom;
}

.steps-container {
  width: 100%;
}

.step-item {
  padding: 15px;
  margin-bottom: 10px;
  background-color: #f5f7fa;
  border-radius: 6px;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.step-input {
  margin-bottom: 8px;
}

.pattern-container {
  width: 100%;
}

.pattern-card {
  width: 100%;
}

.pattern-header {
  font-weight: 600;
  color: #303133;
}

.preview-content {
  padding: 10px;
}

.preview-item {
  margin-bottom: 20px;
}

.preview-label {
  display: block;
  font-weight: 600;
  color: #606266;
  margin-bottom: 10px;
}

.preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-tag {
  margin: 0;
}
</style>