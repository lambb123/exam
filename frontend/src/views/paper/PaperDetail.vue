<template>
  <div class="page-container" v-loading="loading">
    <div class="header">
      <el-page-header @back="$router.back()" title="返回列表">
        <template #content>
          <span class="text-large font-600 mr-3"> 试卷详情 </span>
        </template>
      </el-page-header>
    </div>

    <div v-if="paperInfo" class="content-box">
      <el-card class="paper-header-card" shadow="never">
        <div class="paper-title">{{ paperInfo.paperName }}</div>
        <el-descriptions :column="4" border>
          <el-descriptions-item label="总分">
            <span style="font-weight: bold; color: #f56c6c">{{ paperInfo.totalScore }} 分</span>
          </el-descriptions-item>
          <el-descriptions-item label="出卷人">{{ paperInfo.teacher ? paperInfo.teacher.realName : '未知' }}</el-descriptions-item>
          <el-descriptions-item label="题目数量">{{ questionList.length }} 题</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(paperInfo.createTime) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="question-list-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>📝 题目内容</span>
          </div>
        </template>

        <el-empty v-if="questionList.length === 0" description="暂无题目" />

        <div v-for="(item, index) in questionList" :key="item.question.id" class="question-item">
          <div class="q-header">
            <el-tag size="small" effect="dark" :type="getTypeColor(item.question.type)">{{ item.question.type }}</el-tag>
            <span class="q-score">({{ item.score }}分)</span>
            <span class="q-index">第 {{ index + 1 }} 题</span>
          </div>
          <div class="q-content">{{ item.question.content }}</div>
          <div class="q-footer">
            <span class="label">难度：</span>
            <span>{{ item.question.difficulty }}</span>
            <el-divider direction="vertical" />
            <span class="label">参考答案：</span>
            <span class="answer">{{ item.question.answer }}</span>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getPaperDetail } from '@/api/paper'
import { ElMessage } from 'element-plus'

const route = useRoute()
const loading = ref(false)
const paperInfo = ref<any>(null)
const questionList = ref<any[]>([])

const loadDetail = async () => {
  const id = Number(route.params.id)
  if (!id) return

  loading.value = true
  try {
    const res: any = await getPaperDetail(id)
    if (res.code === 200) {
      paperInfo.value = res.data.paperInfo
      // 这里获取到的 questionList 是 [{score:10, question:{...}}, ...]
      questionList.value = res.data.questionList || res.data.questions // 兼容后端可能的字段名差异
    } else {
      ElMessage.error(res.msg || '获取详情失败')
    }
  } catch (e) {
    ElMessage.error('无法连接服务器')
  } finally {
    loading.value = false
  }
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

const getTypeColor = (type: string) => {
  if (type === '单选') return ''
  if (type === '多选') return 'success'
  if (type === '判断') return 'warning'
  return 'info'
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
/* 样式保持不变 */
.page-container { padding: 20px; }
.header { margin-bottom: 20px; }
.paper-header-card { margin-bottom: 20px; }
.paper-title { font-size: 24px; font-weight: bold; text-align: center; margin-bottom: 20px; color: #303133; }

.question-item { padding: 20px; border-bottom: 1px dashed #eee; transition: background 0.3s; }
.question-item:hover { background-color: #f9f9f9; }
.question-item:last-child { border-bottom: none; }

.q-header { margin-bottom: 12px; font-size: 14px; color: #606266; display: flex; align-items: center; }
.q-score { color: #F56C6C; font-weight: bold; margin-left: 8px; font-size: 13px; }
.q-index { font-weight: bold; margin-left: 12px; color: #303133; font-size: 15px; }

.q-content { font-size: 16px; margin-bottom: 15px; line-height: 1.6; color: #303133; white-space: pre-wrap; padding-left: 5px; }

.q-footer { font-size: 13px; color: #909399; background: #f4f4f5; padding: 8px 15px; border-radius: 4px; display: inline-block; }
.q-footer .label { font-weight: bold; }
.q-footer .answer { color: #67C23A; font-weight: bold; }
</style>
