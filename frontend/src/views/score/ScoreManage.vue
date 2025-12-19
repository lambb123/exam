<template>
  <div class="page-container">
    <div class="header">
      <h2>成绩管理与分析</h2>
    </div>

    <el-card class="box-card">
      <el-form :inline="true" :model="searchForm" class="demo-form-inline">
        <el-form-item label="学生姓名">
          <el-input v-model="searchForm.studentName" placeholder="输入姓名" clearable />
        </el-form-item>
        <el-form-item label="试卷名称">
          <el-input v-model="searchForm.paperName" placeholder="输入试卷名" clearable />
        </el-form-item>

        <el-form-item label="筛选模式">
          <el-switch
            v-model="isFilterOn"
            active-text="只看 ≥ 平均分 (学霸)"
            inactive-text="显示全部"
            @change="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon> 查询
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="tech-tip" v-if="isFilterOn">
        🔥 <b>已开启筛选：</b>正在执行复杂 SQL (Correlated Subquery)，仅显示成绩 <b>大于等于</b> 试卷平均分的学生。
      </div>
      <div class="tech-tip normal-tip" v-else>
        📋 <b>默认列表：</b>显示所有考生成绩。表格中依然会计算并展示“该卷平均分”以供参考。
      </div>
    </el-card>

    <el-table :data="tableData" border stripe style="width: 100%; margin-top: 20px">
      <el-table-column type="index" label="序号" width="60" align="center"/>

      <el-table-column prop="studentName" label="学生姓名" width="150">
        <template #default="{ row }">
          <span style="font-weight: bold">{{ row.studentName }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="paperName" label="考试试卷" show-overflow-tooltip />

      <el-table-column prop="score" label="学生得分" width="120" sortable>
        <template #default="{ row }">
          <span :style="{ color: getScoreColor(row.score, row.avgScore), fontWeight: 'bold' }">
            {{ row.score }}
          </span>
        </template>
      </el-table-column>

      <el-table-column prop="avgScore" label="该卷平均分" width="120">
        <template #default="{ row }">
          <el-tag type="info" effect="plain">{{ row.avgScore }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="与均分对比" width="160">
        <template #default="{ row }">
          <el-tag :type="getDiffTagType(row.score, row.avgScore)" effect="dark">
            {{ formatDiff(row.score, row.avgScore) }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import request from '@/utils/request'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const tableData = ref([])
const isFilterOn = ref(false) // 默认不开启筛选

const searchForm = reactive({
  studentName: '',
  paperName: ''
})

// 核心查询逻辑
const handleSearch = async () => {
  try {
    // 根据开关状态决定调用哪个接口
    const url = isFilterOn.value
      ? '/api/score/analysis/above-average' // 筛选接口 (>= 平均分)
      : '/api/score/list'                   // 普通全量接口

    const res: any = await request.get(url, {
      params: {
        studentName: searchForm.studentName,
        paperName: searchForm.paperName
      }
    })

    if (res.code === 200) {
      tableData.value = res.data
      if (isFilterOn.value) {
        ElMessage.success(`筛选完成，共找到 ${res.data.length} 名超均分学生`)
      }
    }
  } catch (e) {
    console.error(e)
  }
}

const resetSearch = () => {
  searchForm.studentName = ''
  searchForm.paperName = ''
  isFilterOn.value = false // 重置时关闭筛选
  handleSearch()
}

// 辅助样式函数
const getScoreColor = (score: number, avg: number) => {
  if (score >= avg) return '#67C23A' // 绿色
  return '#F56C6C' // 红色
}

const getDiffTagType = (score: number, avg: number) => {
  if (score >= avg) return 'success'
  return 'danger'
}

const formatDiff = (score: number, avg: number) => {
  const diff = score - avg
  return diff >= 0 ? `+${diff.toFixed(1)}` : `${diff.toFixed(1)}`
}

onMounted(() => {
  handleSearch() // 默认加载
})
</script>

<style scoped>
.page-container { padding: 20px; }
.header { margin-bottom: 20px; }
.box-card { margin-bottom: 20px; }

.tech-tip {
  margin-top: 10px;
  padding: 8px 15px;
  border-radius: 4px;
  font-size: 13px;
  border: 1px solid;
}
/* 筛选开启时的提示样式 */
.tech-tip:not(.normal-tip) {
  background: #fdf6ec;
  color: #e6a23c;
  border-color: #faecd8;
}
/* 普通列表的提示样式 */
.normal-tip {
  background: #f4f4f5;
  color: #909399;
  border-color: #e9e9eb;
}
</style>
