<template>
  <div class="exam-container">
    <div class="header">
      <h2>正在考试：{{ paperInfo.paperName }}</h2>
      <div class="timer">⏳ 请认真作答，答题结束后点击下方交卷按钮</div>
    </div>

    <el-card class="question-card" v-for="(item, index) in questionList" :key="item.question.id">
      <template #header>
        <div class="q-header-row">
          <el-tag size="small" effect="dark" :type="getTypeTagColor(item.question.type)">
            {{ item.question.type }}
          </el-tag>
          <span class="q-title">第 {{ index + 1 }} 题：{{ item.question.content }}</span>
          <el-tag size="small" type="info" style="margin-left: auto;">{{ item.score }} 分</el-tag>
        </div>
      </template>

      <div v-if="item.question.type === '单选'" class="options-box">
        <el-radio-group v-model="answers[item.question.id]">
          <el-radio label="A" size="large" border>A</el-radio>
          <el-radio label="B" size="large" border>B</el-radio>
          <el-radio label="C" size="large" border>C</el-radio>
          <el-radio label="D" size="large" border>D</el-radio>
        </el-radio-group>
      </div>

      <div v-else-if="item.question.type === '判断'" class="options-box">
        <el-radio-group v-model="answers[item.question.id]">
          <el-radio label="对" size="large" border>正确 (True)</el-radio>
          <el-radio label="错" size="large" border>错误 (False)</el-radio>
        </el-radio-group>
      </div>

      <div v-else-if="item.question.type === '多选'" class="options-box">
        <el-checkbox-group v-model="answers[item.question.id]">
          <el-checkbox label="A" size="large" border>A</el-checkbox>
          <el-checkbox label="B" size="large" border>B</el-checkbox>
          <el-checkbox label="C" size="large" border>C</el-checkbox>
          <el-checkbox label="D" size="large" border>D</el-checkbox>
        </el-checkbox-group>
      </div>

      <div v-else-if="item.question.type === '填空'" class="options-box">
        <el-input
          v-model="answers[item.question.id]"
          placeholder="请输入答案..."
          clearable
          style="max-width: 400px;"
        />
      </div>

      <div v-else class="options-box">
        <el-input
          v-model="answers[item.question.id]"
          type="textarea"
          :rows="5"
          placeholder="请输入详细的解题思路或答案..."
          resize="none"
        />
      </div>
    </el-card>

    <div class="footer-btn">
      <el-button type="primary" size="large" @click="handleSubmit" :loading="loading" style="width: 200px;">
        交卷并查看分数
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getExamDetail, submitExam } from '@/api/exam'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const paperId = route.params.id
const loading = ref(false)

const paperInfo = ref<any>({})
const questionList = ref<any[]>([])
const answers = reactive<any>({}) // 存放学生答案 {问题ID: 答案}

// 辅助：获取类型标签颜色
const getTypeTagColor = (type: string) => {
  if (type === '单选') return ''
  if (type === '多选') return 'success'
  if (type === '判断') return 'warning'
  if (type === '填空') return 'primary'
  return 'info' // 简答
}

onMounted(async () => {
  if (!paperId) {
    ElMessage.error('参数错误')
    return
  }

  try {
    const res: any = await getExamDetail(Number(paperId))
    if (res.code === 200) {
      paperInfo.value = res.data.paperInfo
      questionList.value = res.data.questionList || []

      // === 【关键】初始化答案数据结构 ===
      questionList.value.forEach((item: any) => {
        const qId = item.question.id
        const qType = item.question.type

        // 多选题必须初始化为空数组 []，否则 el-checkbox-group 会报错
        if (qType === '多选') {
          answers[qId] = []
        } else {
          // 其他题型（单选、填空、简答、判断）初始化为空字符串
          answers[qId] = ''
        }
      })
    }
  } catch (e) {
    ElMessage.error('加载试卷失败')
  }
})

const handleSubmit = () => {
  // 检查是否有未做的题（可选，这里暂不做强制限制）
  ElMessageBox.confirm('确定要提交试卷吗？提交后将自动判分。', '交卷确认', {
    confirmButtonText: '立即交卷',
    cancelButtonText: '再检查一下',
    type: 'warning'
  }).then(async () => {
    loading.value = true
    const userStr = localStorage.getItem('user')
    const user = JSON.parse(userStr || '{}')

    // === 【关键】数据预处理 ===
    // 后端通常接收字符串类型的答案，所以需要把多选题的数组转成字符串
    const finalAnswers: any = {}
    for (const key in answers) {
      const val = answers[key]
      if (Array.isArray(val)) {
        // 例如 ['A', 'C'] -> "A,C"
        finalAnswers[key] = val.sort().join(',')
      } else {
        finalAnswers[key] = val
      }
    }

    const submitData = {
      studentId: user.id,
      paperId: Number(paperId),
      answers: finalAnswers
    }

    try {
      const res: any = await submitExam(submitData)
      if (res.code === 200) {
        const score = res.data.score
        ElMessageBox.alert(`考试结束！你的得分是：${score} 分`, '成绩单', {
          confirmButtonText: '查看我的成绩',
          type: 'success',
          callback: () => {
            router.push('/score/my')
          }
        })
      } else {
        ElMessage.error(res.msg || '提交失败')
      }
    } catch(e) {
      ElMessage.error('提交异常，请稍后重试')
    } finally {
      loading.value = false
    }
  }).catch(() => {
    // 用户点击了取消，什么都不做
  })
}
</script>

<style scoped>
.exam-container { max-width: 900px; margin: 0 auto; padding-bottom: 80px; }
.header { text-align: center; margin-bottom: 30px; margin-top: 10px; }
.timer { color: #909399; font-size: 14px; margin-top: 8px; }

.question-card { margin-bottom: 24px; border-radius: 8px; }
.q-header-row { display: flex; align-items: center; gap: 12px; }
.q-title { font-weight: bold; font-size: 16px; line-height: 1.5; color: #303133; }

.options-box { margin-top: 15px; padding: 0 5px; }

/* 优化单选和多选框的样式：垂直排列，占满宽度 */
:deep(.el-radio-group), :deep(.el-checkbox-group) {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
  width: 100%;
}
:deep(.el-radio.is-bordered), :deep(.el-checkbox.is-bordered) {
  width: 100%;
  margin-left: 0 !important;
  justify-content: flex-start;
  padding: 12px 20px;
  height: auto;
}
:deep(.el-radio__label), :deep(.el-checkbox__label) {
  font-size: 15px;
}

.footer-btn { text-align: center; margin-top: 40px; }
</style>
