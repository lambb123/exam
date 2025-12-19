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

        <el-form-item label="题目描述" required>
          <el-input
            v-model="stem"
            type="textarea"
            :rows="3"
            placeholder="请输入主要的问题描述（不要在这里手写选项 A. B. C. ...）"
          />
        </el-form-item>

        <div v-if="isChoiceQuestion" class="options-panel">
          <el-form-item
            v-for="(opt, index) in options"
            :key="opt.label"
            :label="'选项 ' + opt.label"
            label-width="120px"
          >
            <div style="display: flex; width: 100%;">
              <el-input v-model="opt.value" :placeholder="'请输入选项 ' + opt.label + ' 的内容'" />
              <el-button
                v-if="options.length > 2"
                type="danger"
                icon="Delete"
                circle
                plain
                style="margin-left: 10px;"
                @click="removeOption(index)"
              />
            </div>
          </el-form-item>

          <div style="margin-left: 120px; margin-bottom: 20px;">
            <el-button type="primary" plain size="small" @click="addOption" icon="Plus">添加选项</el-button>
            <span style="font-size: 12px; color: #999; margin-left: 10px;">(最多支持 6 个选项)</span>
          </div>
        </div>

        <el-form-item label="正确答案" required>

          <el-radio-group v-model="form.answer" v-if="form.type === '单选'">
            <el-radio
              v-for="opt in options"
              :key="opt.label"
              :label="opt.label"
              border
            >
              {{ opt.label }}
            </el-radio>
          </el-radio-group>

          <el-radio-group v-model="form.answer" v-else-if="form.type === '判断'">
            <el-radio label="对" border>对 (正确)</el-radio>
            <el-radio label="错" border>错 (错误)</el-radio>
          </el-radio-group>

          <el-checkbox-group v-model="multiAnswer" v-else-if="form.type === '多选'">
            <el-checkbox
              v-for="opt in options"
              :key="opt.label"
              :label="opt.label"
              border
            >
              {{ opt.label }}
            </el-checkbox>
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
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { addQuestion } from '@/api/question'
import { ElMessage } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'

const router = useRouter()

// 1. 定义独立的数据状态
const stem = ref('') // 题干
const multiAnswer = ref<string[]>([]) // 多选答案数组
const options = ref([ // 默认4个选项
  { label: 'A', value: '' },
  { label: 'B', value: '' },
  { label: 'C', value: '' },
  { label: 'D', value: '' }
])
const OPTION_LABELS = ['A', 'B', 'C', 'D', 'E', 'F']

const form = reactive({
  content: '',
  type: '单选',
  difficulty: '简单',
  knowledgePoint: '',
  answer: ''
})

// 计算属性：是否为选择题
const isChoiceQuestion = computed(() => {
  return form.type === '单选' || form.type === '多选'
})

// 监听类型变化，重置状态
const handleTypeChange = () => {
  form.answer = ''
  multiAnswer.value = []
  // 如果切回选择题且选项列表意外为空，恢复默认
  if (isChoiceQuestion.value && options.value.length === 0) {
    options.value = [
      { label: 'A', value: '' }, { label: 'B', value: '' },
      { label: 'C', value: '' }, { label: 'D', value: '' }
    ]
  }
}

// ✅ 修复点：添加选项时处理 undefined
const addOption = () => {
  if (options.value.length >= 6) return ElMessage.warning('最多支持 6 个选项')

  // 使用 || '' 防止 TS 报错 Type undefined is not assignable to type string
  const nextLabel = OPTION_LABELS[options.value.length] || ''
  if (nextLabel) {
    options.value.push({ label: nextLabel, value: '' })
  }
}

// ✅ 修复点：删除选项时处理 undefined
const removeOption = (index: number) => {
  options.value.splice(index, 1)
  // 重置后续选项的 label
  options.value.forEach((opt, idx) => {
    // 使用 || OPTION_LABELS[idx]! 或 || '' 来消除 TS 报错
    opt.label = OPTION_LABELS[idx] || ''
  })
}

const onSubmit = async () => {
  // 1. 基础校验
  if (!stem.value) return ElMessage.warning('请填写题目描述')
  if (!form.knowledgePoint) return ElMessage.warning('请填写知识点')

  // 2. 组装内容 (Stem + Options)
  let finalContent = stem.value
  if (isChoiceQuestion.value) {
    // 校验选项是否都填了
    for (const opt of options.value) {
      if (!opt.value.trim()) {
        return ElMessage.warning(`选项 ${opt.label} 内容不能为空`)
      }
    }
    // 拼接成字符串
    const optionsStr = options.value
      .map(opt => `${opt.label}. ${opt.value}`)
      .join('\n')
    finalContent = `${stem.value}\n\n${optionsStr}`
  }
  form.content = finalContent

  // 3. 处理多选题答案
  if (form.type === '多选') {
    if (multiAnswer.value.length === 0) {
      return ElMessage.warning('多选题请至少选择一个选项')
    }
    form.answer = multiAnswer.value.sort().join(',')
  } else if (!form.answer) {
    return ElMessage.warning('请设置正确答案')
  }

  // 4. 提交
  try {
    const res: any = await addQuestion(form)
    if (res.code === 200) {
      ElMessage.success('添加成功')
      router.push('/question/list')
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
.options-panel {
  background-color: #fafafa;
  padding: 15px 15px 5px 15px;
  border-radius: 4px;
  margin-bottom: 22px;
  border: 1px dashed #dcdfe6;
}
</style>
