import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

export const useDbStore = defineStore('db', () => {
  // 当前主库模式，默认为 MySQL
  const currentDbMode = ref('MySQL')

  // 初始化：从后端获取当前状态
  const fetchDbMode = async () => {
    try {
      const res: any = await request.get('/api/system/db-mode')
      if (res.code === 200) {
        currentDbMode.value = res.data
      }
    } catch (e) {
      console.error('获取主库状态失败', e)
    }
  }

  // 切换主库
  const switchDbMode = async (mode: string) => {
    try {
      const res: any = await request.post(`/api/system/db-mode?mode=${mode}`)
      if (res.code === 200) {
        currentDbMode.value = mode
        ElMessage.success(`系统主库已切换为: ${mode}`)
      }
    } catch (e) {
      ElMessage.error('切换失败')
      fetchDbMode() // 失败回滚
    }
  }

  return { currentDbMode, fetchDbMode, switchDbMode }
})
