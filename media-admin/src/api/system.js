import request from '../utils/request'

export const userApi = {
  list: (params) => request({ url: '/api/system/users', method: 'get', params }),
  add: (data) => request({ url: '/api/system/users', method: 'post', data }),
  update: (id, data) => request({ url: `/api/system/users/${id}`, method: 'put', data }),
  resetPassword: (id, password) => request({ url: `/api/system/users/${id}/reset-password`, method: 'put', data: password || null }),
  batchResetPassword: (ids, password) => request({ url: '/api/system/users/batch-reset-password', method: 'put', data: { ids, password: password || null } }),
  remove: (id) => request({ url: `/api/system/users/${id}`, method: 'delete' }),
}

export const roleApi = {
  list: (params) => request({ url: '/api/system/roles', method: 'get', params }),
  options: () => request({ url: '/api/system/business-dicts/roles/options', method: 'get' }),
  menuIds: (id) => request({ url: `/api/system/roles/${id}/menu-ids`, method: 'get' }),
  add: (data) => request({ url: '/api/system/roles', method: 'post', data }),
  update: (id, data) => request({ url: `/api/system/roles/${id}`, method: 'put', data }),
  remove: (id) => request({ url: `/api/system/roles/${id}`, method: 'delete' }),
}

export const postApi = {
  list: (params) => request({ url: '/api/system/posts', method: 'get', params }),
  options: () => request({ url: '/api/system/business-dicts/posts/options', method: 'get' }),
  add: (data) => request({ url: '/api/system/posts', method: 'post', data }),
  update: (id, data) => request({ url: `/api/system/posts/${id}`, method: 'put', data }),
  remove: (id) => request({ url: `/api/system/posts/${id}`, method: 'delete' }),
}


export const dictApi = {
  typeList: (params) => request({ url: '/api/system/dicts/types', method: 'get', params }),
  addType: (data) => request({ url: '/api/system/dicts/types', method: 'post', data }),
  updateType: (id, data) => request({ url: `/api/system/dicts/types/${id}`, method: 'put', data }),
  removeType: (id) => request({ url: `/api/system/dicts/types/${id}`, method: 'delete' }),
  dataList: (params) => request({ url: '/api/system/dicts/data', method: 'get', params }),
  addData: (data) => request({ url: '/api/system/dicts/data', method: 'post', data }),
  updateData: (id, data) => request({ url: `/api/system/dicts/data/${id}`, method: 'put', data }),
  removeData: (id) => request({ url: `/api/system/dicts/data/${id}`, method: 'delete' }),
  options: (typeCode) => request({ url: `/api/system/business-dicts/dicts/options/${typeCode}`, method: 'get' }),
}
export const deptApi = {
  tree: () => request({ url: '/api/system/business-dicts/depts/tree', method: 'get' }),
  add: (data) => request({ url: '/api/system/depts', method: 'post', data }),
  update: (id, data) => request({ url: `/api/system/depts/${id}`, method: 'put', data }),
  remove: (id) => request({ url: `/api/system/depts/${id}`, method: 'delete' }),
}

export const menuApi = {
  tree: () => request({ url: '/api/system/business-dicts/menus/tree', method: 'get' }),
  add: (data) => request({ url: '/api/system/menus', method: 'post', data }),
  update: (id, data) => request({ url: `/api/system/menus/${id}`, method: 'put', data }),
  remove: (id) => request({ url: `/api/system/menus/${id}`, method: 'delete' }),
}

export const operLogApi = {
  list: (params) => request({ url: '/api/system/oper-logs', method: 'get', params }),
}

export const systemConfigApi = {
  publicInfo: () => request({ url: '/api/system/business-dicts/site-config', method: 'get' }),
  detail: () => request({ url: '/api/system/configs', method: 'get' }),
  defaultPassword: () => request({ url: '/api/system/business-dicts/default-password', method: 'get' }),
  update: (data) => request({ url: '/api/system/configs', method: 'put', data }),
}

export const uploadFileApi = {
  list: (params) => request({ url: '/api/system/upload-files', method: 'get', params }),
  upload: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request({
      url: '/api/system/upload-files',
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

export const jobTaskApi = {
  list: (params) => request({ url: '/api/tool/job-tasks', method: 'get', params }),
  add: (data) => request({ url: '/api/tool/job-tasks', method: 'post', data }),
  update: (id, data) => request({ url: `/api/tool/job-tasks/${id}`, method: 'put', data }),
  remove: (id) => request({ url: `/api/tool/job-tasks/${id}`, method: 'delete' }),
  pause: (id) => request({ url: `/api/tool/job-tasks/${id}/pause`, method: 'put' }),
  resume: (id) => request({ url: `/api/tool/job-tasks/${id}/resume`, method: 'put' }),
  runOnce: (id) => request({ url: `/api/tool/job-tasks/${id}/run`, method: 'put' }),
}

export const jobTaskLogApi = {
  list: (params) => request({ url: '/api/tool/job-task-logs', method: 'get', params }),
}

export const districtApi = {
  sync: () => request({ url: '/api/system/districts/sync', method: 'post', timeout: 120000 }),
  tree: () => request({ url: '/api/system/districts/tree', method: 'get' }),
}

export const modelProviderApi = {
  list: (params) => request({ url: '/api/model/providers', method: 'get', params }),
  options: () => request({ url: '/api/model/providers/options', method: 'get' }),
  add: (data) => request({ url: '/api/model/providers', method: 'post', data }),
  update: (id, data) => request({ url: `/api/model/providers/${id}`, method: 'put', data }),
  remove: (id) => request({ url: `/api/model/providers/${id}`, method: 'delete' }),
}

export const imageModelApi = {
  list: (params) => request({ url: '/api/model/images', method: 'get', params }),
  add: (data) => request({ url: '/api/model/images', method: 'post', data }),
  update: (id, data) => request({ url: `/api/model/images/${id}`, method: 'put', data }),
  remove: (id) => request({ url: `/api/model/images/${id}`, method: 'delete' }),
}

export const videoModelApi = {
  list: (params) => request({ url: '/api/model/videos', method: 'get', params }),
  add: (data) => request({ url: '/api/model/videos', method: 'post', data }),
  update: (id, data) => request({ url: `/api/model/videos/${id}`, method: 'put', data }),
  remove: (id) => request({ url: `/api/model/videos/${id}`, method: 'delete' }),
}
