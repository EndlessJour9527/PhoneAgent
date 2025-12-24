/*
 * Copyright (C) 2025 PhoneAgent Contributors
 * Licensed under AGPL-3.0
 */
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: { title: 'PhoneAgent - 首页' }
  },
  {
    path: '/tasks',
    name: 'Tasks',
    component: () => import('../views/Tasks.vue'),
    meta: { title: '任务列表' }
  },
  {
    path: '/devices',
    name: 'Devices',
    component: () => import('../views/Devices.vue'),
    meta: { title: '设备管理' }
  },
  {
    path: '/app-config',
    name: 'AppConfig',
    component: () => import('../views/AppConfig.vue'),
    meta: { title: '应用配置' }
  },
  {
    path: '/anti-detection',
    name: 'AntiDetection',
    component: () => import('../views/AntiDetection.vue'),
    meta: { title: '防风控配置' }
  },
  {
    path: '/logs',
    name: 'SystemLogs',
    component: () => import('../views/SystemLogs.vue'),
    meta: { title: '系统日志' }
  },
  {
    path: '/diagnostic',
    name: 'Diagnostic',
    component: () => import('../views/Diagnostic.vue'),
    meta: { title: '性能诊断' }
  },
  {
    path: '/model-stats',
    name: 'ModelStats',
    component: () => import('../views/ModelStats.vue'),
    meta: { title: '模型统计' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title || 'PhoneAgent'
  next()
})

export default router

