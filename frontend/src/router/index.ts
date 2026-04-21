import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { isAuthenticated } from '@/services/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/diagnosis'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: {
      title: '登录',
      public: true
    }
  },
  {
    path: '/diagnosis',
    name: 'Diagnosis',
    component: () => import('@/views/DiagnosisView.vue'),
    meta: {
      title: '故障诊断',
      public: true
    }
  },
  {
    path: '/experience',
    name: 'Experience',
    component: () => import('@/views/ExperienceView.vue'),
    meta: {
      title: '经验管理',
      requiresAuth: true
    }
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('@/views/HistoryView.vue'),
    meta: {
      title: '历史记录',
      requiresAuth: true
    }
  },
  {
    path: '/tools',
    name: 'Tools',
    component: () => import('@/views/ToolsView.vue'),
    meta: {
      title: '工具配置',
      requiresAuth: true
    }
  },
  {
    path: '/models',
    name: 'Models',
    component: () => import('@/views/ModelView.vue'),
    meta: {
      title: '模型配置',
      requiresAuth: true
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || 'DRS智能运维'} - DRS智能运维平台`

  const isPublic = to.meta.public
  const requiresAuth = to.meta.requiresAuth

  if (isPublic) {
    next()
  } else if (requiresAuth && !isAuthenticated()) {
    next('/login')
  } else {
    next()
  }
})

export default router