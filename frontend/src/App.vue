<template>
  <el-config-provider :locale="zhCn">
    <div class="app-container">
      <el-container>
        <el-aside width="200px" class="app-aside">
          <div class="logo">
            <h2>DRS智能运维</h2>
          </div>
          <el-menu
            :default-active="currentRoute"
            router
            class="app-menu"
          >
            <el-menu-item index="/diagnosis">
              <el-icon><Monitor /></el-icon>
              <span>故障诊断</span>
            </el-menu-item>
            <el-menu-item index="/experience" v-if="isAuthenticated()">
              <el-icon><Document /></el-icon>
              <span>经验管理</span>
            </el-menu-item>
            <el-menu-item index="/history" v-if="isAuthenticated()">
              <el-icon><Clock /></el-icon>
              <span>历史记录</span>
            </el-menu-item>
            <el-menu-item index="/tools" v-if="isAuthenticated()">
              <el-icon><Tools /></el-icon>
              <span>工具配置</span>
            </el-menu-item>
            <el-menu-item index="/models" v-if="isAuthenticated()">
              <el-icon><Cpu /></el-icon>
              <span>模型配置</span>
            </el-menu-item>
          </el-menu>
          <div class="user-section" v-if="isAuthenticated()">
            <el-dropdown @command="handleUserCommand">
              <span class="user-info">
                <el-icon><User /></el-icon>
                {{ currentUser?.displayName || currentUser?.username }}
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="logout">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="login-link" v-else>
            <el-button type="primary" size="small" @click="goLogin">
              <el-icon><User /></el-icon>
              登录
            </el-button>
          </div>
        </el-aside>
        <el-container>
          <el-header class="app-header">
            <div class="header-title">
              {{ pageTitle }}
            </div>
          </el-header>
          <el-main class="app-main">
            <router-view />
          </el-main>
        </el-container>
      </el-container>
    </div>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, Document, Clock, Tools, Cpu, User, SwitchButton } from '@element-plus/icons-vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import { isAuthenticated, getUser, clearToken, authApi } from '@/services/auth'

const route = useRoute()
const router = useRouter()

const currentUser = ref(getUser())

onMounted(async () => {
  // Check auth status on mount
  currentUser.value = getUser()
})

const currentRoute = computed(() => route.path)

const pageTitle = computed(() => {
  const titles: Record<string, string> = {
    '/diagnosis': '故障诊断',
    '/experience': '经验管理',
    '/history': '历史记录',
    '/tools': '工具配置',
    '/models': '模型配置',
    '/login': '登录'
  }
  return titles[route.path] || 'DRS智能运维平台'
})

const goLogin = () => {
  router.push('/login')
}

const handleUserCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出登录?', '退出确认', { type: 'warning' })
      await authApi.logout()
      clearToken()
      currentUser.value = null
      ElMessage.success('已退出登录')
      router.push('/diagnosis')
    } catch (e) {
      if (e !== 'cancel') {
        clearToken()
        currentUser.value = null
        router.push('/diagnosis')
      }
    }
  }
}
</script>

<style scoped>
.app-container {
  height: 100vh;
  width: 100vw;
}

.app-aside {
  background-color: #304156;
  color: #fff;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #263445;
}

.logo h2 {
  color: #fff;
  font-size: 16px;
  margin: 0;
}

.app-menu {
  border-right: none;
  background-color: #304156;
  flex: 1;
}

.app-menu :deep(.el-menu-item) {
  color: #bfcbd9;
}

.app-menu :deep(.el-menu-item:hover) {
  background-color: #263445;
}

.app-menu :deep(.el-menu-item.is-active) {
  color: #409eff;
  background-color: #263445;
}

.user-section {
  padding: 15px;
  border-top: 1px solid #263445;
}

.user-info {
  color: #bfcbd9;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 5px;
}

.user-info:hover {
  color: #409eff;
}

.login-link {
  padding: 15px;
  border-top: 1px solid #263445;
  display: flex;
  justify-content: center;
}

.app-header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  padding: 0 20px;
}

.header-title {
  font-size: 18px;
  font-weight: 500;
  color: #303133;
}

.app-main {
  background-color: #f5f7fa;
  padding: 20px;
}
</style>