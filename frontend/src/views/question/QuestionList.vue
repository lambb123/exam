<template>
  <div class="page-container">
    <div class="toolbar">
      <h2>试题库管理</h2>
      <el-button type="primary" @click="$router.push('/question/add')">
        <el-icon style="margin-right: 5px"><Plus /></el-icon>添加试题
      </el-button>
    </div>

    <el-card class="search-box">
      <el-form :inline="true" :model="searchForm">

        <el-form-item label="题型">
          <el-select v-model="searchForm.type" placeholder="全部题型" clearable style="width: 140px;">
            <el-option label="单选" value="单选" />
            <el-option label="多选" value="多选" />
            <el-option label="填空" value="填空" />
            <el-option label="判断" value="判断" />
            <el-option label="简答" value="简答" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { getQuestionList, deleteQuestion } from '@/api/question'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Delete } from '@element-plus/icons-vue'

const tableData = ref([])

// 搜索条件
const searchForm = reactive({
  type: '',
  difficulty: '',
  knowledgePoint: ''
})

// 加载全部数据
const loadData = async () => {
  try {
    const res: any = await getQuestionList()
    if (res.code === 200) {
      tableData.value = res.data
    }
  } catch (err) {
    console.error(err)
  }
}

// 执行筛选查询
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
      tableData.value = res.data
      ElMessage.success(`查询到 ${res.data.length} 条试题`)
    }
  } catch (err) {
    ElMessage.error('查询失败')
  }
}

// 重置
const resetSearch = () => {
  searchForm.type = ''
  searchForm.difficulty = ''
  searchForm.knowledgePoint = ''
  loadData()
}

// 删除
const handleDelete = (id: number) => {
  ElMessageBox.confirm('确定要删除这道题吗？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteQuestion(id)
    ElMessage.success('删除成功')
    // 刷新列表
    if (searchForm.type || searchForm.difficulty || searchForm.knowledgePoint) {
      handleSearch()
    } else {
      loadData()
    }
  })
}

// 辅助函数：难度标签颜色
const getDifficultyType = (diff: string) => {
  if (diff === '困难') return 'danger'
  if (diff === '中等') return 'warning'
  return 'success'
}

// 辅助函数：题型标签颜色 (可选)
const getTypeTagColor = (type: string) => {
  if (type === '单选题' || type === '多选题') return ''
  if (type === '判断题') return 'warning'
  if (type === '填空题') return 'info'
  return 'success' // 简答题
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page-container { padding: 20px; }
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.search-box {
  margin-bottom: 20px;
  background-color: #fcfcfc;
}
</style>
