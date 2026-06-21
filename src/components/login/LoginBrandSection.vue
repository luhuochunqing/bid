<template>
  <div class="brand-section">
    <div class="brand-content">
      <!-- Logo -->
      <div class="brand-logo">
        <div class="logo-icon">
          <img src="/favicon.ico" alt="西域 Logo">
        </div>
        <span class="logo-text">西域数智化投标管理平台</span>
      </div>

      <!-- 标语 -->
      <div class="brand-hero">
        <h1 class="hero-title">专业高效的<br>招投标全流程管理</h1>
        <p class="hero-desc">AI 驱动的智能投标分析，助您提升中标率</p>
      </div>

      <!-- 核心功能 -->
      <div class="features-grid">
        <div class="feature-item">
          <div class="feature-icon feature-blue">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 12l2 2 4-4"/>
              <circle cx="12" cy="12" r="10"/>
            </svg>
          </div>
          <span class="feature-text">智能标书分析</span>
        </div>
        <div class="feature-item">
          <div class="feature-icon feature-green">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"/>
            </svg>
          </div>
          <span class="feature-text">中标率预测</span>
        </div>
        <div class="feature-item">
          <div class="feature-icon feature-orange">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z"/>
            </svg>
          </div>
          <span class="feature-text">团队协作</span>
        </div>
        <div class="feature-item">
          <div class="feature-icon feature-purple">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/>
            </svg>
          </div>
          <span class="feature-text">数据报表</span>
        </div>
      </div>

      <!-- 底部版权 -->
      <div class="brand-footer">
        <span>© 西域数智化投标管理平台</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.brand-section {
  flex: 1;
  background: linear-gradient(135deg, var(--login-brand-bg-start) 0%, var(--login-brand-bg-mid) 50%, var(--login-brand-bg-start) 100%);
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.brand-section::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -20%;
  width: 80%;
  height: 200%;
  background: radial-gradient(circle, rgba(var(--login-brand-accent-rgb), 0.15) 0%, transparent 70%);
  pointer-events: none;
}

.brand-section::after {
  content: '';
  position: absolute;
  bottom: -30%;
  left: -10%;
  width: 60%;
  height: 160%;
  background: radial-gradient(circle, rgba(var(--login-brand-success-rgb), 0.08) 0%, transparent 70%);
  pointer-events: none;
}

.brand-content {
  position: relative;
  z-index: 1;
  max-width: 520px;
  width: 100%;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 48px;
}

.logo-icon {
  width: 48px;
  height: 48px;
  background: rgba(var(--bg-white-rgb), 0.96);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12px 28px rgba(var(--shadow-dark-rgb), 0.18);
}

.logo-icon img {
  width: 30px;
  height: 30px;
  object-fit: contain;
}

.logo-text {
  font-size: 20px;
  font-weight: 600;
  color: var(--bg-card);
  letter-spacing: 0.02em;
}

.brand-hero {
  margin-bottom: 56px;
}

.hero-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--bg-card);
  line-height: 1.2;
  margin-bottom: 16px;
  letter-spacing: -0.02em;
}

.hero-desc {
  font-size: 18px;
  color: rgba(var(--bg-white-rgb), 0.7);
  line-height: 1.6;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin-bottom: 48px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: rgba(var(--bg-white-rgb), 0.05);
  border: 1px solid rgba(var(--bg-white-rgb), 0.1);
  border-radius: 12px;
  transition: all 0.3s ease;
}

.feature-item:hover {
  background: rgba(var(--bg-white-rgb), 0.08);
  border-color: rgba(var(--bg-white-rgb), 0.15);
  transform: translateX(4px);
}

.feature-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feature-icon svg {
  width: 20px;
  height: 20px;
}

.feature-blue {
  background: linear-gradient(135deg, rgba(var(--login-brand-accent-rgb), 0.2) 0%, rgba(var(--login-brand-accent-rgb), 0.1) 100%);
  color: var(--login-brand-feature-blue);
}

.feature-green {
  background: linear-gradient(135deg, rgba(var(--login-brand-success-rgb), 0.2) 0%, rgba(var(--login-brand-success-rgb), 0.1) 100%);
  color: var(--login-brand-feature-green);
}

.feature-orange {
  background: linear-gradient(135deg, rgba(var(--login-brand-warning-rgb), 0.2) 0%, rgba(var(--login-brand-warning-rgb), 0.1) 100%);
  color: var(--login-brand-feature-orange);
}

.feature-purple {
  background: linear-gradient(135deg, rgba(var(--login-brand-purple-rgb), 0.2) 0%, rgba(var(--login-brand-purple-rgb), 0.1) 100%);
  color: var(--login-brand-feature-purple);
}

.feature-text {
  font-size: 14px;
  font-weight: 500;
  color: rgba(var(--bg-white-rgb), 0.9);
}

.brand-footer {
  text-align: center;
  color: rgba(var(--bg-white-rgb), 0.4);
  font-size: 13px;
}

@media (max-width: 1024px) {
  .brand-section {
    display: none;
  }
}

@media (max-width: 640px) {
  .hero-title {
    font-size: 36px;
  }
  .features-grid {
    grid-template-columns: 1fr;
  }
}
</style>
