<template>
  <el-dialog v-model="visible" :title="title" width="640px" append-to-body>
    <slot />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="$emit('submit')">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
const visible = defineModel({ type: Boolean, default: false })

defineProps({
  title: {
    type: String,
    default: '编辑',
  },
})

defineEmits(['submit'])
</script>
