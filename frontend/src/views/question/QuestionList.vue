<template>
  <div class="page-container">
    <div class="toolbar">
      <div class="left-section">
        <h2>试题库管理</h2>

        <div class="db-switcher">
          <span class="label">当前主写入库：</span>
          <el-radio-group v-model="currentDbMode" @change="handleDbSwitch" size="small" fill="#67c23a">
            <el-radio-button label="MySQL">MySQL (默认)</el-radio-button>
            <el-radio-button label="Oracle">Oracle (容灾)</el-radio-button>
            <el-radio-button label="SQLServer">SQL Server (备用)</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <div class="actions">
        <el-button type="primary" @click="$router.push('/question/add')">
          <el-icon style="margin-right: 5px"><Plus /></el-icon>添加试题
        </el-button>

        <el-button type="warning" @click="openDebugDialog('Oracle')">
          <el-icon style="margin-right: 5px"><Coin /></el-icon> 模拟Oracle插入
        </el-button>
        <el-button type="success" @click="openDebugDialog('SQLServer')">
          <el-icon style="margin-right: 5px"><Monitor /></el-icon> 模拟SQLServer插入
        </el-button>
      </div>
    </div>

    <el-card class="search-box">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="题型">
          <el-select v-model="searchForm.type" placeholder="全部题型" clearable style="width: 140px;">
            <el-option label="单选" value="单选题" />
            <el-option label="多选" value="多选题" />
            <el-option label="填空" value="填空题" />
            <el-option label="判断" value="判断题" />
            <el-option label="简答" value="简答题" />
          </el-select>
        </el-form-item>

        <el-form-item label="难度">
          <el-select v-model="searchForm.difficulty" placeholder="全部难度" clearable style="width: 120px;">
            <el-option label="简单" value="简单" />
            <el-option label="中等" value="中等" />
            <el-option label="困难" value="困难" />
          </el-select>
        </el-form-item>

        <el-form-item label="知识点">
          <el-input v-model="searchForm.knowledgePoint" placeholder="输入知识点" clearable />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon style="margin-right: 5px"><Search /></el-icon>查询
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="tableData" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" align="center" />

        <el-table-column prop="content" label="题目内容" show-overflow-tooltip min-width="200" />

        <el-table-column prop="type" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getTypeTagColor(row.type)">{{ row.type }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="difficulty" label="难度" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getDifficultyType(row.difficulty)" effect="plain">
              {{ row.difficulty }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="knowledgePoint" label="知识点" width="150" show-overflow-tooltip />

        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button type="danger" size="small" @click="handleDelete(row.id)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="debugDialogVisible" :title="'[演示] 向 ' + targetDb + ' 插入数据'" width="500px">
      <el-form :model="debugForm" label-width="80px">
        <el-form-item label="题目内容">
          <el-input v-model="debugForm.content" placeholder="例如：这是来自 Oracle 的题目" />
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="debugForm.type">
            <el-option label="单选题" value="单选题" />
            <el-option label="多选题" value="多选题" />
            <el-option label="判断题" value="判断题" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="debugForm.difficulty">
            <el-option label="简单" value="简单" />
            <el-option label="中等" value="中等" />
            <el-option label="困难" value="困难" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="debugDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitDebugData" :loading="debugLoading">
            确认写入 {{ targetDb }}
          </el-button>
        </span>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { getQuestionList, deleteQuestion } from '@/api/question'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Delete, Coin, Monitor } from '@element-plus/icons-vue' // 引入新图标

// === 基础数据 ===
const tableData = ref([])
const searchForm = reactive({
  type: '',
  difficulty: '',
  knowledgePoint: ''
})

// === 加载全部数据 ===
const loadData = async () => {
  try {
    const res: any = await getQuestionList()
    if (res.code === 200) {
      // 倒序排列，让新插入的数据显示在最前面
      tableData.value = res.data.sort((a: any, b: any) => b.id - a.id)
    }
  } catch (err) {
    console.error(err)
  }
}

// === 执行筛选查询 ===
const handleSearch = async () => {
  try {
    const res: any = await request.get('/api/question/search', {
      params: {
        type: searchForm.type,
        difficulty: searchForm.difficulty,
        knowledgePoint: searchForm.knowledgePoint
      }
    })
    if (res.code === 200) {
      tableData.value = res.data.sort((a: any, b: any) => b.id - a.id)
      ElMessage.success(`查询到 ${res.data.length} 条试题`)
    }
  } catch (err) {
    ElMessage.error('查询失败')
  }
}

// === 重置 ===
const resetSearch = () => {
  searchForm.type = ''
  searchForm.difficulty = ''
  searchForm.knowledgePoint = ''
  loadData()
}

// === 删除 ===
const handleDelete = (id: number) => {
  ElMessageBox.confirm('确定要删除这道题吗？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteQuestion(id)
    ElMessage.success('删除成功')
    if (searchForm.type || searchForm.difficulty || searchForm.knowledgePoint) {
      handleSearch()
    } else {
      loadData()
    }
  })
}

// ==========================================
// [新增功能] 1. 动态主库切换
// ==========================================
const currentDbMode = ref('MySQL')

// 获取当前主库状态
const fetchDbMode = async () => {
  try {
    const res: any = await request.get('/api/system/db-mode')
    if (res.code === 200) {
      currentDbMode.value = res.data
    }
  } catch (e) {
    console.error(e)
  }
}

// 切换主库
const handleDbSwitch = async (val: string) => {
  try {
    const res: any = await request.post(`/api/system/db-mode?mode=${val}`)
    if (res.code === 200) {
      ElMessage.success(`系统已切换！所有新数据将优先写入 ${val}`)
    }
  } catch (e) {
    ElMessage.error('切换失败')
    fetchDbMode() // 回滚显示
  }
}

// ==========================================
// [新增功能] 2. 模拟异构库插入 (反向同步演示)
// ==========================================
const debugDialogVisible = ref(false)
const targetDb = ref('')
const debugLoading = ref(false)
const debugForm = reactive({
  content: '',
  type: '单选题',
  difficulty: '简单',
  knowledgePoint: '测试知识点',
  answer: 'A'
})

const openDebugDialog = (db: string) => {
  targetDb.value = db
  debugForm.content = `【反向同步测试】来自 ${db} 的数据 ` + new Date().toLocaleTimeString()
  debugDialogVisible.value = true
}

const submitDebugData = async () => {
  if (!debugForm.content) return ElMessage.warning('请输入内容')

  debugLoading.value = true
  const url = targetDb.value === 'Oracle' ? '/api/debug/oracle/question' : '/api/debug/sqlserver/question'

  try {
    const res: any = await request.post(url, debugForm)
    if (res.code === 200) {
      debugDialogVisible.value = false
      ElMessage.success(`成功写入 ${targetDb.value} 并同步回 MySQL！`)
      // 【核心】立即刷新列表，展示"零延迟"同步效果
      await loadData()
    } else {
      ElMessage.error(res.msg || '写入失败')
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('请求异常')
  } finally {
    debugLoading.value = false
  }
}

// ==========================================
// 辅助函数
// ==========================================
const getDifficultyType = (diff: string) => {
  if (diff === '困难') return 'danger'
  if (diff === '中等') return 'warning'
  return 'success'
}

const getTypeTagColor = (type: string) => {
  if (type === '单选题' || type === '多选题') return ''
  if (type === '判断题') return 'warning'
  if (type === '填空题') return 'info'
  return 'success'
}

onMounted(() => {
  loadData()
  fetchDbMode() // 初始化加载主库状态
})
</script>

<style scoped>
.page-container { padding: 20px; }

/* 顶部工具栏布局优化 */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.left-section {
  display: flex;
  align-items: center;
  gap: 20px;
}

.actions {
  display: flex;
  gap: 10px;
}

/* 主库切换器样式 */
.db-switcher {
  display: flex;
  align-items: center;
  background-color: #f5f7fa;
  padding: 5px 15px;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
}
.label {
  font-weight: bold;
  margin-right: 10px;
  color: #606266;
  font-size: 14px;
}

.search-box {
  margin-bottom: 20px;
  background-color: #fcfcfc;
}
</style>
