<template>
  <div class="page-card dashboard-page">
    <section class="hero-panel">
      <div class="hero-copy">
        <div class="hero-kicker">System Overview</div>
        <h2>统一的后台工作台</h2>
        <p>
          这里保留了当前系统的核心状态、待处理事项和基础能力入口，方便管理员快速判断运行情况并继续操作。
        </p>

        <div class="hero-actions">
          <el-button type="primary">查看用户</el-button>
          <el-button plain>打开系统配置</el-button>
        </div>
      </div>

      <div class="hero-side">
        <div class="hero-stat">
          <span>在线模块</span>
          <strong>5</strong>
          <p>用户、角色、菜单、配置、日志</p>
        </div>
        <div class="hero-stat">
          <span>当前主题</span>
          <strong>2563EB</strong>
          <p>科技蓝主色调，自动同步全局组件</p>
        </div>
      </div>
    </section>

    <section class="overview-grid">
      <div v-for="item in overview" :key="item.title" class="metric-card">
        <div class="metric-top">
          <span class="metric-title">{{ item.title }}</span>
          <span class="metric-dot"></span>
        </div>
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-desc">{{ item.desc }}</div>
      </div>
    </section>

    <section class="detail-grid">
      <div class="detail-panel subtle-surface">
        <div class="panel-heading">
          <div>
            <div class="panel-kicker">Workspace</div>
            <h3 class="panel-title">最近状态</h3>
            <p class="panel-copy">关注当前系统的基础运行信息和最近的管理动作，方便快速定位入口。</p>
          </div>
          <el-tag effect="light" type="success">稳定</el-tag>
        </div>

        <div class="status-list">
          <div v-for="item in statusList" :key="item.label" class="status-row">
            <div class="status-label">{{ item.label }}</div>
            <div class="status-value">{{ item.value }}</div>
            <div class="status-bar">
              <span :style="{ width: item.width }"></span>
            </div>
          </div>
        </div>
      </div>

      <div class="detail-panel subtle-surface">
        <div class="panel-heading">
          <div>
            <div class="panel-kicker">Actions</div>
            <h3 class="panel-title">快捷入口</h3>
            <p class="panel-copy">按工作台常用路径组织，尽量减少层级跳转。</p>
          </div>
        </div>

        <div class="shortcut-list">
          <button v-for="item in shortcuts" :key="item.label" type="button" class="shortcut-item">
            <div>
              <div class="shortcut-title">{{ item.label }}</div>
              <div class="shortcut-desc">{{ item.desc }}</div>
            </div>
            <span class="shortcut-arrow">→</span>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
const overview = [
  { title: '技术栈', value: 'Vue 3 + Element Plus', desc: '当前界面基于 Vite 构建，适合继续做模块化扩展。' },
  { title: '权限粒度', value: '按钮级', desc: '菜单、页面与操作权限都可通过统一体系控制。' },
  { title: '运行环境', value: 'Java 17', desc: '后端约束清晰，适配 MySQL 和标准管理场景。' },
]

const statusList = [
  { label: '系统主页', value: '可用', width: '92%' },
  { label: '权限模型', value: '已启用', width: '84%' },
  { label: '布局体验', value: '已优化', width: '76%' },
]

const shortcuts = [
  { label: '用户管理', desc: '维护账号、角色和岗位关系' },
  { label: '系统配置', desc: '调整站点基础信息和默认参数' },
  { label: '操作日志', desc: '查看最近的系统操作记录' },
]
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(280px, 0.75fr);
  gap: 18px;
  padding: 28px;
  border-radius: 26px;
  color: #fff;
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.16), transparent 24%),
    linear-gradient(135deg, #102033, #183152 58%, #23467b 100%);
  box-shadow: 0 24px 60px rgba(16, 24, 40, 0.18);
}

.hero-copy h2 {
  margin: 0;
  font-size: clamp(30px, 4vw, 46px);
  line-height: 1.06;
  letter-spacing: -0.03em;
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  margin-bottom: 16px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  background: rgba(255, 255, 255, 0.12);
}

.hero-copy p {
  max-width: 660px;
  margin: 16px 0 0;
  color: rgba(255, 255, 255, 0.76);
  font-size: 15px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 22px;
  flex-wrap: wrap;
}

.hero-side {
  display: grid;
  gap: 14px;
}

.hero-stat {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 132px;
  padding: 20px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.09);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.hero-stat span {
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-stat strong {
  font-size: 30px;
  letter-spacing: 0.02em;
}

.hero-stat p {
  margin: 0;
  color: rgba(255, 255, 255, 0.76);
  line-height: 1.6;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  padding: 22px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 249, 253, 0.96));
  border: 1px solid rgba(186, 198, 214, 0.34);
  box-shadow: 0 16px 34px rgba(16, 24, 40, 0.05);
}

.metric-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.metric-title {
  color: var(--muted);
  font-weight: 700;
}

.metric-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary), #60a5fa);
}

.metric-value {
  margin: 18px 0 10px;
  font-size: 28px;
  font-weight: 800;
}

.metric-desc {
  color: var(--muted);
  line-height: 1.7;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.detail-panel {
  padding: 22px;
}

.status-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 18px;
}

.status-row {
  display: grid;
  grid-template-columns: 110px 70px 1fr;
  gap: 12px;
  align-items: center;
}

.status-label {
  color: var(--muted);
  font-weight: 700;
}

.status-value {
  color: var(--text);
  font-weight: 700;
}

.status-bar {
  height: 10px;
  padding: 2px;
  border-radius: 999px;
  background: rgba(186, 198, 214, 0.28);
}

.status-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--primary), #60a5fa);
}

.shortcut-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 18px;
}

.shortcut-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding: 16px 18px;
  border: 1px solid rgba(186, 198, 214, 0.3);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.shortcut-item:hover {
  transform: translateY(-1px);
  border-color: rgba(37, 99, 235, 0.24);
  box-shadow: 0 14px 28px rgba(16, 24, 40, 0.08);
}

.shortcut-title {
  font-weight: 800;
}

.shortcut-desc {
  margin-top: 4px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}

.shortcut-arrow {
  color: var(--muted);
  font-size: 18px;
}

@media (max-width: 960px) {
  .hero-panel,
  .overview-grid,
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .hero-panel {
    padding: 22px;
  }

  .status-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }
}
</style>
