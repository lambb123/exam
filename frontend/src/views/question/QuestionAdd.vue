<template>
  <div class="page-container">
    <div class="header">
      <el-page-header @back="$router.back()" title="返回列表">
        <template #content>
          <span class="text-large font-600 mr-3"> 添加新试题 </span>
        </template>
      </el-page-header>
    </div>

    <el-card style="max-width: 900px; margin-top: 20px;">
      <el-form :model="form" label-width="120px" status-icon>

        <el-form-item label="题目类型" required>
          <el-radio-group v-model="form.type" @change="handleTypeChange">
            <el-radio-button label="单选">单选题</el-radio-button>
            <el-radio-button label="多选">多选题</el-radio-button>
            <el-radio-button label="判断">判断题</el-radio-button>
            <el-radio-button label="填空">填空题</el-radio-button>
            <el-radio-button label="简答">简答题</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="难度等级">
              <el-select v-model="form.difficulty" placeholder="请选择" style="width: 100%">
                <el-option label="简单" value="简单" />
                <el-option label="中等" value="中等" />
                <el-option label="困难" value="困难" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="知识点" required>
              <el-input v-model="form.knowledgePoint" placeholder="例如：Java基础, Spring Boot" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="题目内容" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            placeholder="请输入题目描述。如果是选择题，请将选项也写在这里，例如：
1. Java是哪年诞生的？
A. 1990
B. 1995
C. 2000
D. 2005"
          />
        </el-form-item>

        <el-form-item label="正确答案" required>

          <el-radio-group v-model="form.answer" v-if="form.type === '单选'">
            <el-radio label="A" border>A</el-radio>
            <el-radio label="B" border>B</el-radio>
            <el-radio label="C" border>C</el-radio>
            <el-radio label="D" border>D</el-radio>
          </el-radio-group>

          <el-radio-group v-model="form.answer" v-else-if="form.type === '判断'">
            <el-radio label="对" border>对 (正确)</el-radio>
            <el-radio label="错" border>错 (错误)</el-radio>
          </el-radio-group>

          <el-checkbox-group v-model="multiAnswer" v-else-if="form.type === '多选'">
            <el-checkbox label="A" border>A</el-checkbox>
            <el-checkbox label="B" border>B</el-checkbox>
            <el-checkbox label="C" border>C</el-checkbox>
            <el-checkbox label="D" border>D</el-checkbox>
          </el-checkbox-group>

          <el-input
            v-else
            v-model="form.answer"
            :type="form.type === '简答' ? 'textarea' : 'text'"
            :rows="3"
            placeholder="请输入参考答案"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" @click="onSubmit" style="width: 150px;">立即创建</el-button>
          <el-button size="large" @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { addQuestion } from '@/api/question'
import { ElMessage } from 'element-plus'

const router = useRouter()

// 多选的答案需要单独用数组存，提交时转字符串
const multiAnswer = ref<string[]>([])

const form = reactive({
  content: '',
  type: '单选', // 默认单选
  difficulty: '简单',
  knowledgePoint: '',
  answer: ''
})

// 监听类型变化，重置答案，防止脏数据
const handleTypeChange = () => {
  form.answer = ''
  multiAnswer.value = []
}

const onSubmit = async () => {
  // 1. 处理多选题答案：数组转字符串 (例如 ['A','B'] -> "A,B")
  if (form.type === '多选') {
    if (multiAnswer.value.length === 0) {
      return ElMessage.warning('多选题请至少选择一个选项')
    }
    form.answer = multiAnswer.value.sort().join(',')
  }

  // 2. 基础校验
  if (!form.content) return ElMessage.warning('请填写题目内容')
  if (!form.knowledgePoint) return ElMessage.warning('请填写知识点')
  if (!form.answer) return ElMessage.warning('请设置正确答案')

  // 3. 提交
  try {
    const res: any = await addQuestion(form)
    if (res.code === 200) {
      ElMessage.success('添加成功')
      router.push('/question/list') // 跳回列表
    } else {
      ElMessage.error(res.msg || '添加失败')
    }
  } catch (e) {
    ElMessage.error('网络请求错误')
  }
}
</script>

<style scoped>
.page-container { padding: 20px; }
.header { margin-bottom: 20px; }
</style>
