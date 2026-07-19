<template>
  <el-menu-item v-if="isLeaf" :index="menuIndex">
    <MenuIcon v-if="item.icon" :name="item.icon" />
    <span>{{ item.menuName }}</span>
  </el-menu-item>

  <el-sub-menu v-else :index="String(item.id)">
    <template #title>
      <MenuIcon v-if="item.icon" :name="item.icon" />
      <span>{{ item.menuName }}</span>
    </template>

    <SidebarMenuItem
      v-for="child in visibleChildren"
      :key="child.id"
      :item="child"
    />
  </el-sub-menu>
</template>

<script setup>
import { computed } from 'vue'
import MenuIcon from '../MenuIcon.vue'

defineOptions({
  name: 'SidebarMenuItem',
})

const props = defineProps({
  item: {
    type: Object,
    required: true,
  },
})

const visibleChildren = computed(() => (props.item.children || []).filter((child) => child.menuType !== 2))
const isLeaf = computed(() => visibleChildren.value.length === 0)
const menuIndex = computed(() => props.item.fullPath || `/${props.item.path}`)
</script>
