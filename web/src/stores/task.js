import { defineStore } from 'pinia'
import { ref } from 'vue'
import { taskApi } from '@/api'

export const useTaskStore = defineStore('task', () => {
  // 状态
  const tasks = ref([])
  const loading = ref(false)
  const currentTask = ref(null)
  
  // 获取任务列表
  async function fetchTasks(params = {}) {
    loading.value = true
    try {
      tasks.value = await taskApi.list(params)
      return tasks.value
    } catch (error) {
      console.error('Failed to fetch tasks:', error)
      throw error
    } finally {
      loading.value = false
    }
  }
  
  // 获取任务详情
  async function fetchTask(taskId) {
    loading.value = true
    try {
      currentTask.value = await taskApi.get(taskId)
      return currentTask.value
    } catch (error) {
      console.error('Failed to fetch task:', error)
      throw error
    } finally {
      loading.value = false
    }
  }
  
  // 创建任务
  async function createTask(data) {
    loading.value = true
    try {
      const task = await taskApi.create(data)
      tasks.value.unshift(task)
      return task
    } catch (error) {
      console.error('Failed to create task:', error)
      throw error
    } finally {
      loading.value = false
    }
  }
  
  // 取消任务
  async function cancelTask(taskId) {
    loading.value = true
    try {
      await taskApi.cancel(taskId)
      // 更新本地状态
      const task = tasks.value.find(t => t.task_id === taskId)
      if (task) {
        task.status = 'cancelled'
      }
    } catch (error) {
      console.error('Failed to cancel task:', error)
      throw error
    } finally {
      loading.value = false
    }
  }
  
  // 删除任务
  async function deleteTask(taskId) {
    loading.value = true
    try {
      await taskApi.delete(taskId)
      // 从列表中移除
      const index = tasks.value.findIndex(t => t.task_id === taskId)
      if (index !== -1) {
        tasks.value.splice(index, 1)
      }
    } catch (error) {
      console.error('Failed to delete task:', error)
      throw error
    } finally {
      loading.value = false
    }
  }
  
  // 批量删除任务
  async function deleteBatchTasks(taskIds) {
    loading.value = true
    try {
      await taskApi.deleteBatch(taskIds)
      // 从列表中移除
      tasks.value = tasks.value.filter(t => !taskIds.includes(t.task_id))
    } catch (error) {
      console.error('Failed to batch delete tasks:', error)
      throw error
    } finally {
      loading.value = false
    }
  }
  
  return {
    // 状态
    tasks,
    loading,
    currentTask,
    
    // 方法
    fetchTasks,
    fetchTask,
    createTask,
    cancelTask,
    deleteTask,
    deleteBatchTasks
  }
})

