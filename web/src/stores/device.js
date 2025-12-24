import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { deviceApi } from '@/api'

export const useDeviceStore = defineStore('device', () => {
  // 状态
  const devices = ref([])
  const loading = ref(false)
  const currentDevice = ref(null)
  
  // 计算属性
  const onlineDevices = computed(() => 
    devices.value.filter(d => d.status === 'online')
  )
  
  const busyDevices = computed(() => 
    devices.value.filter(d => d.status === 'busy')
  )
  
  const offlineDevices = computed(() => 
    devices.value.filter(d => d.status === 'offline')
  )
  
  const availableDevices = computed(() =>
    devices.value.filter(d => 
      d.status === 'online' && 
      d.frp_connected && 
      d.ws_connected && 
      !d.current_task
    )
  )
  
  // 获取设备列表
  async function fetchDevices(status = null) {
    loading.value = true
    try {
      devices.value = await deviceApi.list(status)
      return devices.value
    } catch (error) {
      console.error('Failed to fetch devices:', error)
      throw error
    } finally {
      loading.value = false
    }
  }
  
  // 获取设备详情
  async function fetchDevice(deviceId) {
    loading.value = true
    try {
      currentDevice.value = await deviceApi.get(deviceId)
      return currentDevice.value
    } catch (error) {
      console.error('Failed to fetch device:', error)
      throw error
    } finally {
      loading.value = false
    }
  }
  
  return {
    // 状态
    devices,
    loading,
    currentDevice,
    
    // 计算属性
    onlineDevices,
    busyDevices,
    offlineDevices,
    availableDevices,
    
    // 方法
    fetchDevices,
    fetchDevice
  }
})

