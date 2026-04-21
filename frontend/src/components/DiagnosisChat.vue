<template>
  <div class="diagnosis-chat">
    <!-- 输入区域 -->
    <el-card class="input-card">
      <template #header>
        <div class="card-header">
          <el-icon><Edit /></el-icon>
          <span>问题描述</span>
        </div>
      </template>
      <el-form :model="inputForm" label-position="top">
        <el-form-item label="请描述您的问题">
          <el-input
            v-model="inputForm.question"
            type="textarea"
            :rows="4"
            placeholder="例如：系统登录失败，提示权限不足..."
            :disabled="isDiagnosing"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="isDiagnosing"
            @click="handleSubmit"
          >
            <el-icon><Search /></el-icon>
            {{ isDiagnosing ? '诊断中...' : '提交诊断' }}
          </el-button>
          <el-button @click="handleReset" :disabled="isDiagnosing">
            重置
          </el-button>
          <el-button @click="handleStop" v-if="isDiagnosing" type="danger">
            <el-icon><Close /></el-icon>
            停止
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 实时思考过程 -->
    <el-card class="thinking-card" v-if="thinkingContent || isDiagnosing">
      <template #header>
        <div class="card-header">
          <el-icon><Cpu /></el-icon>
          <span>AI思考过程</span>
          <el-tag type="info" size="small" style="margin-left: auto">
            {{ currentStep }}
          </el-tag>
        </div>
      </template>
      <div class="thinking-content">
        <div class="thinking-animation" v-if="isDiagnosing && !thinkingContent">
          <span class="dot"></span>
          <span class="dot"></span>
          <span class="dot"></span>
          正在分析问题...
        </div>
        <div class="thinking-text" v-html="formattedThinkingContent"></div>
      </div>
    </el-card>

    <!-- 诊断过程时间线 -->
    <el-card class="process-card" v-if="diagnosisSteps.length > 0">
      <template #header>
        <div class="card-header">
          <el-icon><Operation /></el-icon>
          <span>诊断过程</span>
          <el-tag v-if="totalExecutionTime" size="small" style="margin-left: auto">
            耗时: {{ totalExecutionTime }}ms
          </el-tag>
        </div>
      </template>
      <el-timeline>
        <el-timeline-item
          v-for="(step, index) in diagnosisSteps"
          :key="index"
          :type="getStepType(step.status)"
          :hollow="step.status !== 'SUCCESS'"
          :timestamp="step.executionTimeMs ? `${step.executionTimeMs}ms` : ''"
        >
          <div class="step-card">
            <div class="step-header">
              <span class="step-name">{{ step.stepName }}</span>
              <el-tag :type="getTagType(step.status)" size="small">
                {{ step.status }}
              </el-tag>
            </div>
            <div class="step-desc">{{ step.description }}</div>
            <div class="step-thinking" v-if="step.thinking">
              <el-collapse>
                <el-collapse-item title="查看思考内容">
                  <pre>{{ step.thinking }}</pre>
                </el-collapse-item>
              </el-collapse>
            </div>
            <div class="step-result" v-if="step.result">
              <span class="result-label">执行结果:</span>
              <div class="result-text">{{ truncate(step.result, 100) }}</div>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 诊断结果 -->
    <el-card class="result-card" v-if="diagnosisResult">
      <template #header>
        <div class="card-header">
          <el-icon><DocumentChecked /></el-icon>
          <span>诊断结果</span>
          <el-tag :type="diagnosisResult.status === 'COMPLETED' ? 'success' : 'danger'" size="small" style="margin-left: auto">
            {{ diagnosisResult.status }}
          </el-tag>
        </div>
      </template>

      <div class="result-content">
        <div class="result-item" v-if="diagnosisResult.problemType">
          <span class="label">问题类型:</span>
          <el-tag type="info" size="large">{{ diagnosisResult.problemType }}</el-tag>
        </div>

        <div class="result-item" v-if="diagnosisResult.rootCause">
          <span class="label">根因分析:</span>
          <div class="root-cause-box">
            <el-icon class="root-icon"><Warning /></el-icon>
            <span>{{ diagnosisResult.rootCause }}</span>
          </div>
        </div>

        <div class="result-item" v-if="diagnosisResult.solution">
          <span class="label">解决方案:</span>
          <div class="solution-content">
            <pre>{{ diagnosisResult.solution }}</pre>
          </div>
        </div>

        <div class="result-item" v-if="diagnosisResult.confidence">
          <span class="label">置信度:</span>
          <el-progress
            :percentage="Math.round(diagnosisResult.confidence * 100)"
            :color="getConfidenceColor(diagnosisResult.confidence * 100)"
            :stroke-width="20"
            :text-inside="true"
          />
        </div>

        <!-- 相关经验 -->
        <div class="result-item" v-if="similarExperiences.length > 0">
          <span class="label">相似经验:</span>
          <div class="experience-links">
            <el-link
              v-for="exp in similarExperiences"
              :key="exp.experienceId"
              type="primary"
              @click="handleViewExperience(exp)"
              class="experience-link"
            >
              <el-icon><Link /></el-icon>
              {{ exp.problemType }} (相似度: {{ Math.round(exp.similarity * 100) }}%)
            </el-link>
          </div>
        </div>

        <!-- 反馈按钮 -->
        <el-divider />
        <div class="feedback-section">
          <span class="feedback-label">诊断结果是否正确?</span>
          <el-button type="success" @click="handleFeedback(true)">
            <el-icon><Check /></el-icon>
            正确
          </el-button>
          <el-button type="danger" @click="handleFeedback(false)">
            <el-icon><Close /></el-icon>
            错误
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Edit, Search, Operation, Check, Close,
  DocumentChecked, Link, Cpu, Warning
} from '@element-plus/icons-vue'
import { request } from '@/services/request'
import { getToken } from '@/services/auth'

interface DiagnosisStep {
  stepId: string
  stepNumber?: number
  stepName: string
  description: string
  thinking?: string
  action?: string
  result?: string
  status: string
  executionTimeMs?: number
}

interface DiagnosisResult {
  sessionId: string
  problemType?: string
  rootCause?: string
  solution?: string
  confidence?: number
  status: string
  errorMessage?: string
}

interface SimilarExperience {
  experienceId: string
  problemType: string
  similarity: number
  summary?: string
}

interface InputForm {
  question: string
}

const emit = defineEmits<{
  (e: 'feedback', correct: boolean): void
  (e: 'viewExperience', experience: SimilarExperience): void
}>()

const inputForm = reactive<InputForm>({ question: '' })
const isDiagnosing = ref(false)
const diagnosisSteps = ref<DiagnosisStep[]>([])
const diagnosisResult = ref<DiagnosisResult | null>(null)
const similarExperiences = ref<SimilarExperience[]>([])
const thinkingContent = ref('')
const currentStep = ref('')
const totalExecutionTime = ref(0)
const sessionId = ref('')
const abortFn = ref<(() => void) | null>(null)

const formattedThinkingContent = computed(() => {
  if (!thinkingContent.value) return ''
  return thinkingContent.value
    .replace(/\n/g, '<br>')
    .replace(/思考:/g, '<strong>💭 思考:</strong>')
    .replace(/分析:/g, '<strong>🔍 分析:</strong>')
    .replace(/结论:/g, '<strong>✅ 结论:</strong>')
})

const handleSubmit = async () => {
  if (!inputForm.question.trim()) {
    ElMessage.warning('请输入问题描述')
    return
  }

  isDiagnosing.value = true
  diagnosisSteps.value = []
  diagnosisResult.value = null
  similarExperiences.value = []
  thinkingContent.value = ''
  currentStep.value = '开始诊断'
  totalExecutionTime.value = 0

  const token = getToken()
  const startTime = Date.now()

  try {
    // 使用 fetch 进行流式请求
    const response = await fetch('/api/v1/diagnose/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : '',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({
        problem: inputForm.question,
        context: ''
      })
    })

    if (!response.ok) {
      if (response.status === 401) {
        ElMessage.warning('请先登录')
        return
      }
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error('No reader available')

    const decoder = new TextDecoder()
    let buffer = ''
    const abortController = new AbortController()
    abortFn.value = () => abortController.abort()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // Parse SSE events
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.substring(5).trim()
          if (data) {
            try {
              const event = JSON.parse(data)
              handleEvent(event)
            } catch (e) {
              console.error('Failed to parse SSE:', data)
            }
          }
        }
      }
    }

    totalExecutionTime.value = Date.now() - startTime
    ElMessage.success('诊断完成')

  } catch (error: any) {
    if (error.name !== 'AbortError') {
      ElMessage.error('诊断请求失败: ' + error.message)
    }
  } finally {
    isDiagnosing.value = false
    abortFn.value = null
  }
}

const handleEvent = (event: any) => {
  const {
    eventType, sessionId: sid, stepNumber, stepName, description,
    thinking, action, result, status, executionTimeMs,
    confidence, rootCause, solution, finalResult
  } = event

  if (sid) sessionId.value = sid

  // Update current step
  if (stepName) {
    currentStep.value = stepName
  }

  // Handle thinking content
  if (thinking) {
    thinkingContent.value += `\n${thinking}`
  }

  // Handle different event types
  switch (eventType) {
    case 'START':
      thinkingContent.value = '🚀 开始诊断流程...\n'
      break

    case 'THINKING':
      thinkingContent.value += `\n💭 ${description}\n`
      break

    case 'STEP_START':
      thinkingContent.value += `\n📍 步骤开始: ${stepName}\n`
      diagnosisSteps.value.push({
        stepId: `step_${stepNumber || diagnosisSteps.value.length}`,
        stepNumber,
        stepName,
        description: description || '',
        thinking,
        action,
        result,
        status: 'RUNNING'
      })
      break

    case 'STEP_COMPLETE':
      thinkingContent.value += `\n✅ 步骤完成: ${stepName}\n`
      if (thinking) thinkingContent.value += `${thinking}\n`
      updateStepStatus(stepNumber || diagnosisSteps.value.length, 'SUCCESS', result, executionTimeMs, thinking)
      break

    case 'CHAIN_STEP':
      diagnosisSteps.value.push({
        stepId: `step_${stepNumber || diagnosisSteps.value.length}`,
        stepNumber,
        stepName,
        description: description || '',
        thinking,
        action,
        result,
        status: status || 'SUCCESS',
        executionTimeMs
      })
      break

    case 'ANALYSIS':
      thinkingContent.value += `\n🔍 根因分析: ${stepName}\n${thinking || description}\n`
      break

    case 'RESULT':
      thinkingContent.value += `\n📋 诊断结果已生成\n`
      if (finalResult) {
        try {
          const parsed = JSON.parse(finalResult)
          diagnosisResult.value = parsed
          if (parsed.similarExperiences) {
            similarExperiences.value = parsed.similarExperiences
          }
        } catch (e) {
          diagnosisResult.value = {
            sessionId: sid,
            confidence,
            rootCause,
            solution,
            status: 'COMPLETED'
          }
        }
      } else {
        diagnosisResult.value = {
          sessionId: sid,
          confidence,
          rootCause,
          solution,
          status: 'COMPLETED'
        }
      }
      break

    case 'ERROR':
      thinkingContent.value += `\n❌ 错误: ${description}\n`
      ElMessage.error(description || '诊断失败')
      break
  }
}

const updateStepStatus = (stepNumber: number, status: string, result?: string, time?: number, thinking?: string) => {
  const step = diagnosisSteps.value.find(s => s.stepNumber === stepNumber || s.stepId === `step_${stepNumber}`)
  if (step) {
    step.status = status
    if (result) step.result = result
    if (time) step.executionTimeMs = time
    if (thinking) step.thinking = thinking
  }
}

const handleStop = () => {
  if (abortFn.value) {
    abortFn.value()
    ElMessage.info('诊断已停止')
    isDiagnosing.value = false
  }
}

const handleReset = () => {
  inputForm.question = ''
  diagnosisSteps.value = []
  diagnosisResult.value = null
  similarExperiences.value = []
  thinkingContent.value = ''
  currentStep.value = ''
}

const handleFeedback = async (correct: boolean) => {
  if (!sessionId.value) {
    emit('feedback', correct)
    return
  }

  try {
    await request.post(`/v1/diagnose/${sessionId.value}/feedback`, {
      rating: correct ? 5 : 2,
      isCorrect: correct
    })
    emit('feedback', correct)
    ElMessage.success(correct ? '感谢反馈，我们会持续优化' : '感谢反馈，已记录改进建议')
  } catch (e) {
    emit('feedback', correct)
  }
}

const handleViewExperience = (experience: SimilarExperience) => {
  emit('viewExperience', experience)
}

const getStepType = (status: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'primary'
    default: return 'info'
  }
}

const getTagType = (status: string): 'success' | 'warning' | 'danger' | 'info' => {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'warning'
    default: return 'info'
  }
}

const getConfidenceColor = (percentage: number): string => {
  if (percentage >= 90) return '#67c23a'
  if (percentage >= 70) return '#e6a23c'
  return '#f56c6c'
}

const truncate = (str: string, max: number): string => {
  if (!str) return ''
  return str.length > max ? str.substring(0, max) + '...' : str
}
</script>

<style scoped>
.diagnosis-chat {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.input-card, .thinking-card, .process-card, .result-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #303133;
}

.thinking-content {
  background: #1a1a2e;
  color: #eee;
  padding: 15px;
  border-radius: 8px;
  font-family: 'Consolas', monospace;
  font-size: 14px;
  min-height: 100px;
  max-height: 300px;
  overflow-y: auto;
}

.thinking-animation {
  display: flex;
  align-items: center;
  gap: 5px;
}

.dot {
  width: 8px;
  height: 8px;
  background: #409eff;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) { animation-delay: -0.32s; }
.dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.thinking-text {
  line-height: 1.6;
}

.step-card {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.step-name {
  font-weight: 600;
  color: #303133;
}

.step-desc {
  color: #606266;
  margin-top: 5px;
  font-size: 13px;
}

.step-thinking {
  margin-top: 10px;
}

.step-thinking pre {
  background: #1a1a2e;
  color: #eee;
  padding: 10px;
  border-radius: 6px;
  font-size: 12px;
  overflow-x: auto;
}

.step-result {
  margin-top: 10px;
}

.result-label {
  font-size: 12px;
  color: #909399;
}

.result-text {
  background: #fff;
  padding: 8px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  margin-top: 5px;
  font-size: 13px;
}

.result-content {
  padding: 10px 0;
}

.result-item {
  margin-bottom: 20px;
}

.result-item .label {
  display: block;
  font-weight: 600;
  color: #606266;
  margin-bottom: 10px;
  font-size: 14px;
}

.root-cause-box {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fef0f0;
  padding: 15px;
  border-radius: 6px;
  border: 1px solid #fbc4c4;
}

.root-icon {
  color: #f56c6c;
  font-size: 20px;
}

.solution-content {
  background: #f0f9eb;
  padding: 15px;
  border-radius: 6px;
  border: 1px solid #c2e7b0;
}

.solution-content pre {
  white-space: pre-wrap;
  font-family: inherit;
  color: #67c23a;
}

.experience-links {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.experience-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.feedback-section {
  display: flex;
  align-items: center;
  gap: 15px;
  flex-wrap: wrap;
}

.feedback-label {
  font-weight: 600;
  color: #606266;
}
</style>