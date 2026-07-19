import { useAuthStore } from '../stores/auth'

export default {
  mounted(el, binding) {
    const authStore = useAuthStore()
    const permission = binding.value
    if (!permission) {
      return
    }
    if (!authStore.permissions.includes(permission)) {
      el.parentNode?.removeChild(el)
    }
  },
}
