<template>
  <div class="authorization-page" :data-page="state">
    <main v-if="state === 'accounts' && bindingSession" class="page-main">
      <QianchuanAccountSelection :session="bindingSession" @completed="verify" />
    </main>
    <main
      v-if="state === 'verifying'"
      class="authorization-checking"
      role="status"
    >
      <h1>正在核验授权结果</h1>
      <p>正在同步账户信息，请稍候…</p>
    </main>
    <main v-if="state === 'select'" class="page-main" id="main">
      <div class="container page-main__inner">
        <section class="hero">
          <h1 class="hero__title">授权抖音广告投放账号</h1>

          <div class="hero__checks">
            <p class="hero__check">
              <svg
                class="hero__check-icon"
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M2.6 7.4L5.4 10.2L11.4 3.8"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              授权后，灵投即可为你管理广告投放。
            </p>
            <p class="hero__check">
              <svg
                class="hero__check-icon"
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M2.6 7.4L5.4 10.2L11.4 3.8"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              完全基于抖音官方授权规范，不会获取你的账号密码等非可授权范围内信息。
            </p>
          </div>

          <ul class="hero__pills">
            <li class="pill">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M8 1.5L13 3.5V7.4C13 10.6 10.9 13.3 8 14.4C5.1 13.3 3 10.6 3 7.4V3.5L8 1.5Z"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linejoin="round"
                />
                <path
                  d="M5.9 8.1L7.4 9.6L10.2 6.5"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              抖音官方API安全接入
            </li>
            <li class="pill">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <circle
                  cx="8"
                  cy="8"
                  r="6.2"
                  stroke="currentColor"
                  stroke-width="1.3"
                />
                <path
                  d="M8 4.6V8L10.4 9.6"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              10秒完成授权
            </li>
            <li class="pill">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M2.4 7.2A5.8 5.8 0 1 1 3.6 11"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                />
                <path
                  d="M2 3.4V7.4H6"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              随时可取消
            </li>
          </ul>
        </section>

        <section class="panel" aria-labelledby="platform-title">
          <div class="panel__inner">
            <header class="section-head">
              <h2 class="section-title" id="platform-title">选择投放平台</h2>
              <p class="section-desc">根据业务类型选择需要授权的广告投放平台</p>
            </header>

            <div class="platform-grid">
              <article class="platform-card platform-card--recommended">
                <span class="badge-recommend">推荐</span>

                <div class="platform-card__head">
                  <svg
                    class="platform-card__icon"
                    viewBox="0 0 48 48"
                    role="img"
                    aria-label="巨量千川"
                  >
                    <rect width="48" height="48" rx="12" fill="#F0F6FE" />
                    <path
                      d="M37.65 14.67L37.7 29.05L32.94 30.79L32.25 30.19L32.37 16.55L37.64 14.67ZM29.94 19.73L24.56 21.63L24.86 33.36L30.12 31.48L29.99 19.74ZM22.24 24.87L16.93 26.77L17.1 35.9L22.48 34.04L22.29 24.88Z"
                      fill="#3554DD"
                    />
                    <path
                      d="M23.09 16.96L29.78 19.39L29.14 20.14L24.43 21.44L18.01 19.1L17.95 18.53L23.06 16.97ZM15.01 22.23L22.23 24.63L17.53 26.58L10.3 24.25L10.69 23.66L14.96 22.24Z"
                      fill="#78E4DA"
                    />
                    <path
                      d="M29.99 12.1L37.27 14.43L37.27 14.96L33.37 16.26L32.08 16.49L25.42 13.98L29.96 12.1Z"
                      fill="#F1E067"
                    />
                  </svg>
                  <div class="platform-card__meta">
                    <h3 class="platform-card__name">巨量千川</h3>
                    <p class="platform-card__tagline">电商推广 · 全域投放</p>
                  </div>
                </div>

                <p class="platform-card__desc">
                  商品下单、直播间引流、短视频带货、商品卡等电商全场景投放
                </p>

                <div class="permissions">
                  <p class="permissions__label">将获取的权限</p>
                  <div class="permissions__rows">
                    <div class="permissions__row">
                      <span class="tag">广告账户管理</span>
                      <span class="tag">投放计划管理</span>
                      <span class="tag">素材管理</span>
                    </div>
                    <div class="permissions__row">
                      <span class="tag">数据报表</span>
                      <span class="tag">全域投放</span>
                    </div>
                  </div>
                </div>

                <button
                  class="btn btn--primary btn--lg btn--block"
                  type="button"
                  data-action="authorize-qianchuan"
                  :disabled="busy"
                  @click.prevent="action('authorize-qianchuan')"
                >
                  点击授权巨量千川
                </button>
              </article>

              <article class="platform-card">
                <div class="platform-card__head">
                  <svg
                    class="platform-card__icon"
                    viewBox="0 0 48 48"
                    role="img"
                    aria-label="巨量本地推"
                  >
                    <rect width="48" height="48" rx="12" fill="#F0F6FE" />
                    <path
                      d="M24.6 10.53L25.15 10.75L26.44 11.71L29.06 14.2L30.46 15.85L30.65 16.21L30.6 16.64L28.81 16.66L28.13 16.83L20.51 24.17L18.95 25.59L17.42 26.74L16.05 27.19L15.12 27.23L14.21 26.97L13.24 26.38L12.25 25.3L11.71 23.94L11.64 23.1L11.77 22.26L12.35 21.32L16.47 16.71L22.1 11.2L22.88 10.6L23.73 10.3Z"
                      fill="#79E4DA"
                    />
                    <path
                      d="M28.74 16.79L27.8 17.29L26.78 18.11L23.83 20.96L22.18 22.84L21.93 23.24L21.9 23.69L23.89 23.55L25.55 23.75L27.12 24.51L29.38 26.11L30.81 26.91L32.48 27.27L33.19 27.21L33.78 26.98L35.31 25.89L35.77 25.34L36.15 24.57L36.36 23L35.89 21.37L34.21 19.27L32.36 17.72L31.27 17.15L29.61 16.56Z"
                      fill="#5187F5"
                    />
                    <path
                      d="M24.66 25.86L26.32 26.34L27.5 27.19L28.61 28.72L28.96 29.64L29.03 30.42L28.49 32.7L27.47 34.49L25.38 36.67L24.36 37.46L23.74 37.7L23.05 37.26L21.81 36.12L20.13 34.11L19.22 32.11L18.93 30.22L19.13 29.26L19.51 28.36L20.09 27.54L20.88 26.81L21.79 26.25L22.7 25.92L23.74 25.73ZM23.15 28.89L22.39 29.41L21.92 30.24L21.85 31.05L22.09 31.95L22.58 32.61L23.29 32.97L24.12 33.03L25.01 32.78L25.67 32.28L26.04 31.55L26.09 30.71L25.82 29.82L25.41 29.3L24.83 28.96L23.78 28.61Z"
                      fill="#58BAF9"
                    />
                  </svg>
                  <div class="platform-card__meta">
                    <h3 class="platform-card__name">巨量本地推</h3>
                    <p class="platform-card__tagline">本地生活 · 到店服务</p>
                  </div>
                </div>

                <p class="platform-card__desc">
                  到店成交、团购核销、门店引流、POI 打卡等本地生活场景投放
                </p>

                <div class="permissions">
                  <p class="permissions__label">将获取的权限</p>
                  <div class="permissions__rows">
                    <div class="permissions__row">
                      <span class="tag">广告账户管理</span>
                      <span class="tag">项目管理</span>
                      <span class="tag">素材管理</span>
                    </div>
                    <div class="permissions__row">
                      <span class="tag">数据报表</span>
                      <span class="tag">线索管理</span>
                    </div>
                  </div>
                </div>

                <button
                  class="btn btn--muted btn--lg btn--block"
                  type="button"
                  disabled
                  aria-disabled="true"
                >
                  建设中
                </button>
              </article>
            </div>
          </div>
        </section>

        <section class="accordion">
          <h2>
            <button
              class="accordion__trigger"
              type="button"
              :aria-expanded="expanded"
              aria-controls="safe-panel"
              @click="expanded = !expanded"
            >
              <span class="accordion__title">
                <svg
                  width="20"
                  height="20"
                  viewBox="0 0 20 20"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true"
                >
                  <path
                    d="M10 2.2L16.4 4.7V9.3C16.4 13.2 13.8 16.5 10 17.8C6.2 16.5 3.6 13.2 3.6 9.3V4.7L10 2.2Z"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linejoin="round"
                  />
                  <path
                    d="M7.4 10L9.2 11.8L12.6 8.2"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                授权安全说明
              </span>
              <svg
                class="accordion__chevron"
                width="20"
                height="20"
                viewBox="0 0 20 20"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M5.2 7.8L10 12.6L14.8 7.8"
                  stroke="currentColor"
                  stroke-width="1.7"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>
          </h2>
          <div class="accordion__panel" id="safe-panel" v-show="expanded">
            <ul class="accordion__list">
              <li>
                本次授权完全基于抖音官方授权规范，灵投不会获取你的账号密码等非授权范围内的信息。
              </li>
              <li>授权全程约 10 秒完成，无需手动填写任何账号信息。</li>
              <li>
                你可以随时在「账户管理」中取消授权，取消后灵投将立即停止对该账号的投放管理。
              </li>
            </ul>
          </div>
        </section>

        <section class="cta-banner">
          <div class="cta-banner__content">
            <h2 class="cta-banner__title">暂无抖音广告投放账号？</h2>
            <p class="cta-banner__body">
              如果你是首次投放抖音广告，尚未开通巨量千川或巨量本地推账户，需先至抖音平台进行账户开通。
            </p>
            <p class="cta-banner__body">
              如有需要，可联系客服协助开通，全程免费，通常 1 个工作日内完成。
            </p>
          </div>
          <div class="cta-banner__actions">
            <button
              class="btn btn--cta-primary"
              type="button"
              data-action="open-account"
              @click.prevent="action('open-account')"
            >
              免费开通
            </button>
            <button
              class="btn btn--cta-ghost"
              type="button"
              data-action="contact-service"
              @click.prevent="action('contact-service')"
            >
              联系客服
            </button>
          </div>
        </section>
      </div>
    </main>
    <main v-if="state === 'authorizing'" class="page-main" id="main">
      <div class="container page-main__inner">
        <section class="hero">
          <h1 class="hero__title">授权抖音广告投放账号</h1>

          <div class="hero__checks">
            <p class="hero__check">
              <svg
                class="hero__check-icon"
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M2.6 7.4L5.4 10.2L11.4 3.8"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              授权后，灵投即可为你管理广告投放。
            </p>
            <p class="hero__check">
              <svg
                class="hero__check-icon"
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M2.6 7.4L5.4 10.2L11.4 3.8"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              完全基于抖音官方授权规范，不会获取你的账号密码等非可授权范围内信息。
            </p>
          </div>

          <ul class="hero__pills">
            <li class="pill">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M8 1.5L13 3.5V7.4C13 10.6 10.9 13.3 8 14.4C5.1 13.3 3 10.6 3 7.4V3.5L8 1.5Z"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linejoin="round"
                />
                <path
                  d="M5.9 8.1L7.4 9.6L10.2 6.5"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              抖音官方API安全接入
            </li>
            <li class="pill">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <circle
                  cx="8"
                  cy="8"
                  r="6.2"
                  stroke="currentColor"
                  stroke-width="1.3"
                />
                <path
                  d="M8 4.6V8L10.4 9.6"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              10秒完成授权
            </li>
            <li class="pill">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M2.4 7.2A5.8 5.8 0 1 1 3.6 11"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                />
                <path
                  d="M2 3.4V7.4H6"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              随时可取消
            </li>
          </ul>
        </section>

        <section
          class="panel panel--loading"
          role="status"
          aria-live="polite"
          aria-labelledby="loading-title"
        >
          <div class="panel__inner">
            <div class="loading-state">
              <svg
                class="spinner"
                viewBox="0 0 64 64"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <circle
                  cx="32"
                  cy="32"
                  r="28"
                  stroke="#E9EAF4"
                  stroke-width="5"
                />
                <circle
                  cx="32"
                  cy="32"
                  r="28"
                  stroke="url(#spin-grad)"
                  stroke-width="5"
                  stroke-linecap="round"
                  stroke-dasharray="123 53"
                  transform="rotate(-90 32 32)"
                />
                <defs>
                  <linearGradient
                    id="spin-grad"
                    x1="10"
                    y1="10"
                    x2="54"
                    y2="54"
                    gradientUnits="userSpaceOnUse"
                  >
                    <stop stop-color="#8B8CFF" />
                    <stop offset="1" stop-color="#575CF6" />
                  </linearGradient>
                </defs>
              </svg>

              <h2 class="loading-state__title" id="loading-title">
                正在跳转至抖音巨量平台进行授权...
              </h2>
              <p class="loading-state__desc">
                请在巨量平台登录你的广告账号，并勾选同意授权
              </p>

              <ol class="steps" aria-label="授权流程">
                <li class="steps__item">
                  <span class="steps__num">1</span>跳转授权页
                </li>
                <li aria-hidden="true">
                  <svg
                    class="steps__arrow"
                    width="16"
                    height="16"
                    viewBox="0 0 16 16"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                      d="M2.6 8H12.6M9.4 4.8L12.8 8L9.4 11.2"
                      stroke="currentColor"
                      stroke-width="1.3"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </li>
                <li class="steps__item">
                  <span class="steps__num">2</span>登录账号
                </li>
                <li aria-hidden="true">
                  <svg
                    class="steps__arrow"
                    width="16"
                    height="16"
                    viewBox="0 0 16 16"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                      d="M2.6 8H12.6M9.4 4.8L12.8 8L9.4 11.2"
                      stroke="currentColor"
                      stroke-width="1.3"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </li>
                <li class="steps__item">
                  <span class="steps__num">3</span>确认授权
                </li>
                <li aria-hidden="true">
                  <svg
                    class="steps__arrow"
                    width="16"
                    height="16"
                    viewBox="0 0 16 16"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                      d="M2.6 8H12.6M9.4 4.8L12.8 8L9.4 11.2"
                      stroke="currentColor"
                      stroke-width="1.3"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </li>
                <li class="steps__item">
                  <span class="steps__num">4</span>返回灵投
                </li>
              </ol>
            </div>
          </div>
        </section>

        <section class="accordion">
          <h2>
            <button
              class="accordion__trigger"
              type="button"
              :aria-expanded="expanded"
              aria-controls="safe-panel"
              @click="expanded = !expanded"
            >
              <span class="accordion__title">
                <svg
                  width="20"
                  height="20"
                  viewBox="0 0 20 20"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true"
                >
                  <path
                    d="M10 2.2L16.4 4.7V9.3C16.4 13.2 13.8 16.5 10 17.8C6.2 16.5 3.6 13.2 3.6 9.3V4.7L10 2.2Z"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linejoin="round"
                  />
                  <path
                    d="M7.4 10L9.2 11.8L12.6 8.2"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                授权安全说明
              </span>
              <svg
                class="accordion__chevron"
                width="20"
                height="20"
                viewBox="0 0 20 20"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M5.2 7.8L10 12.6L14.8 7.8"
                  stroke="currentColor"
                  stroke-width="1.7"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </button>
          </h2>
          <div class="accordion__panel" id="safe-panel" v-show="expanded">
            <ul class="accordion__list">
              <li>
                本次授权完全基于抖音官方授权规范，灵投不会获取你的账号密码等非授权范围内的信息。
              </li>
              <li>授权全程约 10 秒完成，无需手动填写任何账号信息。</li>
              <li>
                你可以随时在「账户管理」中取消授权，取消后灵投将立即停止对该账号的投放管理。
              </li>
            </ul>
          </div>
        </section>

        <section class="cta-banner">
          <div class="cta-banner__content">
            <h2 class="cta-banner__title">暂无抖音广告投放账号？</h2>
            <p class="cta-banner__body">
              如果你是首次投放抖音广告，尚未开通巨量千川或巨量本地推账户，需先至抖音平台进行账户开通。
            </p>
            <p class="cta-banner__body">
              如有需要，可联系客服协助开通，全程免费，通常 1 个工作日内完成。
            </p>
          </div>
          <div class="cta-banner__actions">
            <button
              class="btn btn--cta-primary"
              type="button"
              data-action="open-account"
              @click.prevent="action('open-account')"
            >
              免费开通
            </button>
            <button
              class="btn btn--cta-ghost"
              type="button"
              data-action="contact-service"
              @click.prevent="action('contact-service')"
            >
              联系客服
            </button>
          </div>
        </section>
      </div>
      <div class="authorization-retry">
        <button class="btn btn--secondary" :disabled="busy" @click="begin">
          重新打开授权页</button
        ><router-link class="btn btn--secondary" to="/authorization"
          >返回选择平台</router-link
        >
      </div>
    </main>
    <main v-if="state === 'success'" class="page-main" id="main">
      <div class="container page-main__inner">
        <section class="hero">
          <h1 class="hero__title">授权抖音广告投放账号</h1>

          <div class="hero__checks">
            <p class="hero__check">
              <svg
                class="hero__check-icon"
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M2.6 7.4L5.4 10.2L11.4 3.8"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              授权后，灵投即可为你管理广告投放。
            </p>
            <p class="hero__check">
              <svg
                class="hero__check-icon"
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M2.6 7.4L5.4 10.2L11.4 3.8"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              完全基于抖音官方授权规范，不会获取你的账号密码等非可授权范围内信息。
            </p>
          </div>

          <ul class="hero__pills">
            <li class="pill">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M8 1.5L13 3.5V7.4C13 10.6 10.9 13.3 8 14.4C5.1 13.3 3 10.6 3 7.4V3.5L8 1.5Z"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linejoin="round"
                />
                <path
                  d="M5.9 8.1L7.4 9.6L10.2 6.5"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              抖音官方API安全接入
            </li>
            <li class="pill">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <circle
                  cx="8"
                  cy="8"
                  r="6.2"
                  stroke="currentColor"
                  stroke-width="1.3"
                />
                <path
                  d="M8 4.6V8L10.4 9.6"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              10秒完成授权
            </li>
            <li class="pill">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M2.4 7.2A5.8 5.8 0 1 1 3.6 11"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                />
                <path
                  d="M2 3.4V7.4H6"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              随时可取消
            </li>
          </ul>
        </section>

        <section class="panel panel--result" aria-labelledby="result-title">
          <div class="panel__inner">
            <header class="result-head">
              <span class="result-icon-slot">
                <span class="result-icon result-icon--success">
                  <svg
                    width="36"
                    height="36"
                    viewBox="0 0 36 36"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                    aria-hidden="true"
                  >
                    <path
                      d="M8.4 18.6L14.6 24.8L27.6 11.4"
                      stroke="#FFFFFF"
                      stroke-width="3"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </span>
              </span>
              <h2 class="result-head__title" id="result-title">授权成功！</h2>
              <p class="result-head__desc">
                灵投已获得您的广告投放授权，后续将开启智能广告投放
              </p>
            </header>

            <ul class="capability-grid">
              <li class="capability-card">
                <svg
                  class="capability-card__icon"
                  viewBox="0 0 28 28"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true"
                >
                  <path
                    d="M2.6 4.6H5.4L8 17.4H20.6L23.4 8.2H6.3"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                  <circle
                    cx="10.6"
                    cy="21.8"
                    r="1.7"
                    stroke="currentColor"
                    stroke-width="1.8"
                  />
                  <circle
                    cx="18.6"
                    cy="21.8"
                    r="1.7"
                    stroke="currentColor"
                    stroke-width="1.8"
                  />
                </svg>
                <h3 class="capability-card__title">智能选品</h3>
                <p class="capability-card__desc">智能推荐最佳投放商品组合</p>
              </li>

              <li class="capability-card">
                <svg
                  class="capability-card__icon"
                  viewBox="0 0 28 28"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true"
                >
                  <circle
                    cx="14"
                    cy="14"
                    r="11"
                    stroke="currentColor"
                    stroke-width="1.8"
                  />
                  <circle
                    cx="14"
                    cy="14"
                    r="6.2"
                    stroke="currentColor"
                    stroke-width="1.8"
                  />
                  <circle cx="14" cy="14" r="1.9" fill="currentColor" />
                </svg>
                <h3 class="capability-card__title">智能盯盘和优化</h3>
                <p class="capability-card__desc">
                  7*24小时持续盯盘和优化，<br />及时止损、保障增益
                </p>
              </li>

              <li class="capability-card">
                <svg
                  class="capability-card__icon"
                  viewBox="0 0 28 28"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true"
                >
                  <path
                    d="M3.4 24H24.6"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                  />
                  <rect
                    x="5.4"
                    y="14.2"
                    width="4.6"
                    height="7.4"
                    rx="1"
                    stroke="currentColor"
                    stroke-width="1.8"
                  />
                  <rect
                    x="11.7"
                    y="9.4"
                    width="4.6"
                    height="12.2"
                    rx="1"
                    stroke="currentColor"
                    stroke-width="1.8"
                  />
                  <rect
                    x="18"
                    y="4.6"
                    width="4.6"
                    height="17"
                    rx="1"
                    stroke="currentColor"
                    stroke-width="1.8"
                  />
                </svg>
                <h3 class="capability-card__title">数据驱动</h3>
                <p class="capability-card__desc">实时归因 ROI 可视</p>
              </li>
            </ul>

            <dl class="info-list">
              <div class="info-list__row">
                <dt class="info-list__label">授权账号</dt>
                <dd
                  class="info-list__value info-list__value--strong"
                  data-field="account"
                >
                  {{ accountName }}
                </dd>
              </div>
              <div class="info-list__row">
                <dt class="info-list__label">授权平台</dt>
                <dd class="info-list__value" data-field="platform">巨量千川</dd>
              </div>
              <div class="info-list__row">
                <dt class="info-list__label">授权权限</dt>
                <dd class="info-list__value" data-field="scopes">
                  广告管理 / 数据读取 / 素材管理 / 全域投放
                </dd>
              </div>
              <div class="info-list__row">
                <dt class="info-list__label">授权时间</dt>
                <dd class="info-list__value" data-field="time">
                  {{ accountTime }}
                </dd>
              </div>
            </dl>

            <div class="result-actions">
              <button
                class="btn btn--primary btn--lg btn--block"
                type="button"
                data-action="start-campaign"
                @click.prevent="action('start-campaign')"
              >
                <svg
                  class="btn__icon"
                  width="18"
                  height="18"
                  viewBox="0 0 18 18"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true"
                >
                  <path
                    d="M10.4 1.6C13.4 2.4 15.6 4.6 16.4 7.6L10.6 13.4L4.6 7.4L10.4 1.6Z"
                    fill="#FFFFFF"
                  />
                  <path
                    d="M4.6 7.4L2 8.1L4.4 10.5M10.6 13.4L9.9 16L7.5 13.6"
                    stroke="#FFFFFF"
                    stroke-width="1.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                  <path
                    d="M2.6 15.4L5.9 12.1"
                    stroke="#FFFFFF"
                    stroke-width="1.6"
                    stroke-linecap="round"
                  />
                </svg>
                立即开始投放
              </button>
              <button
                class="btn btn--secondary btn--lg btn--block"
                type="button"
                data-action="continue-authorize"
                @click.prevent="action('continue-authorize')"
              >
                继续授权
              </button>
            </div>
          </div>
        </section>
      </div>
    </main>
    <main v-if="state === 'failure'" class="page-main" id="main">
      <div class="container page-main__inner">
        <section class="hero">
          <h1 class="hero__title">授权抖音广告投放账号</h1>

          <div class="hero__checks">
            <p class="hero__check">
              <svg
                class="hero__check-icon"
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M2.6 7.4L5.4 10.2L11.4 3.8"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              授权后，灵投即可为你管理广告投放。
            </p>
            <p class="hero__check">
              <svg
                class="hero__check-icon"
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M2.6 7.4L5.4 10.2L11.4 3.8"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              完全基于抖音官方授权规范，不会获取你的账号密码等非可授权范围内信息。
            </p>
          </div>

          <ul class="hero__pills">
            <li class="pill">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M8 1.5L13 3.5V7.4C13 10.6 10.9 13.3 8 14.4C5.1 13.3 3 10.6 3 7.4V3.5L8 1.5Z"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linejoin="round"
                />
                <path
                  d="M5.9 8.1L7.4 9.6L10.2 6.5"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              抖音官方API安全接入
            </li>
            <li class="pill">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <circle
                  cx="8"
                  cy="8"
                  r="6.2"
                  stroke="currentColor"
                  stroke-width="1.3"
                />
                <path
                  d="M8 4.6V8L10.4 9.6"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              10秒完成授权
            </li>
            <li class="pill">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
              >
                <path
                  d="M2.4 7.2A5.8 5.8 0 1 1 3.6 11"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                />
                <path
                  d="M2 3.4V7.4H6"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              随时可取消
            </li>
          </ul>
        </section>

        <section class="panel panel--result" aria-labelledby="result-title">
          <div class="panel__inner">
            <header class="result-head">
              <span class="result-icon-slot">
                <span class="result-icon-ring">
                  <svg
                    class="result-icon"
                    viewBox="0 0 48 48"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                    aria-hidden="true"
                  >
                    <circle
                      cx="24"
                      cy="24"
                      r="20.4"
                      stroke="currentColor"
                      stroke-width="3.2"
                    />
                    <path
                      d="M17.4 17.4L30.6 30.6M30.6 17.4L17.4 30.6"
                      stroke="currentColor"
                      stroke-width="3.2"
                      stroke-linecap="round"
                    />
                  </svg>
                </span>
              </span>
              <h2 class="result-head__title" id="result-title">授权失败</h2>
              <p class="result-head__desc">
                无法完成广告账号授权，请检查后<strong>重新尝试</strong>或联系客服协助处理。
              </p>
            </header>

            <div class="alert alert--error" role="alert">
              <p class="alert__title">
                <svg
                  width="15"
                  height="15"
                  viewBox="0 0 16 16"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true"
                >
                  <path
                    d="M8 2.2L14.4 13.2H1.6L8 2.2Z"
                    stroke="currentColor"
                    stroke-width="1.4"
                    stroke-linejoin="round"
                  />
                  <path
                    d="M8 6.6V9.4"
                    stroke="currentColor"
                    stroke-width="1.4"
                    stroke-linecap="round"
                  />
                  <circle cx="8" cy="11.4" r="0.85" fill="currentColor" />
                </svg>
                失败原因
              </p>
              <p class="alert__body" data-field="fail-reason">{{ message }}</p>
            </div>

            <div class="result-actions">
              <button
                class="btn btn--primary btn--lg btn--block"
                type="button"
                data-action="retry-authorize"
                @click.prevent="action('retry-authorize')"
              >
                <svg
                  class="btn__icon"
                  width="17"
                  height="17"
                  viewBox="0 0 18 18"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true"
                >
                  <path
                    d="M15.2 9A6.2 6.2 0 1 1 13 4.3"
                    stroke="#FFFFFF"
                    stroke-width="1.7"
                    stroke-linecap="round"
                  />
                  <path
                    d="M15.6 1.6V5.4H11.8"
                    stroke="#FFFFFF"
                    stroke-width="1.7"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                重新授权
              </button>
              <button
                class="btn btn--secondary btn--lg btn--block"
                type="button"
                data-action="change-account"
                @click.prevent="action('change-account')"
              >
                更换其他账号
              </button>
            </div>

            <div class="alert alert--info">
              <p class="alert__title">
                <svg
                  width="15"
                  height="15"
                  viewBox="0 0 16 16"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true"
                >
                  <circle
                    cx="8"
                    cy="8"
                    r="6.4"
                    stroke="currentColor"
                    stroke-width="1.4"
                  />
                  <path
                    d="M6.2 6.3a1.85 1.85 0 0 1 3.6.5c0 1.2-1.8 1.5-1.8 2.6"
                    stroke="currentColor"
                    stroke-width="1.4"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                  <circle cx="8" cy="11.7" r="0.85" fill="currentColor" />
                </svg>
                需要帮助？
              </p>
              <p class="alert__body">
                如果多次尝试仍无法成功授权，请联系客服获取人工协助，或查阅授权常见问题了解解决方案。
              </p>
            </div>
          </div>
        </section>

        <section class="panel panel--reasons">
          <div class="panel__inner">
            <div class="reasons__head">
              <h2 class="reasons__title">
                <svg
                  width="20"
                  height="20"
                  viewBox="0 0 20 20"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true"
                >
                  <rect
                    x="3.6"
                    y="2.4"
                    width="12.8"
                    height="15.2"
                    rx="2.4"
                    fill="currentColor"
                  />
                  <path
                    d="M6.9 7.3H13.1M6.9 10.3H13.1M6.9 13.3H10.3"
                    stroke="#FFFFFF"
                    stroke-width="1.4"
                    stroke-linecap="round"
                  />
                </svg>
                常见失败原因
              </h2>
              <button
                class="reasons__toggle"
                type="button"
                aria-expanded="true"
                aria-controls="reasons-body"
                aria-label="收起常见失败原因"
                data-accordion-trigger
              >
                <svg
                  class="reasons__chevron"
                  width="20"
                  height="20"
                  viewBox="0 0 20 20"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true"
                >
                  <path
                    d="M5.2 7.8L10 12.6L14.8 7.8"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
            </div>

            <div class="reasons__body" id="reasons-body">
              <ul class="reasons__list">
                <li class="reasons__item">
                  <span class="reasons__label">授权超时</span>
                  <span class="reasons__text"
                    >：网络不稳定或页面停留时间过长，请刷新后重新授权</span
                  >
                </li>
                <li class="reasons__item">
                  <span class="reasons__label">权限不足</span>
                  <span class="reasons__text"
                    >：当前账号无广告管理权限，请联系账号管理员开通</span
                  >
                </li>
                <li class="reasons__item">
                  <span class="reasons__label">账号异常</span>
                  <span class="reasons__text"
                    >：账号存在风险或被限制，请先在平台解除限制</span
                  >
                </li>
                <li class="reasons__item">
                  <span class="reasons__label">已授权其他应用</span>
                  <span class="reasons__text"
                    >：同一账号同时只能授权一个第三方应用，需先取消原有授权</span
                  >
                </li>
                <li class="reasons__item">
                  <span class="reasons__label">浏览器拦截</span>
                  <span class="reasons__text"
                    >：弹窗被浏览器拦截，请允许弹窗后重试</span
                  >
                </li>
              </ul>
            </div>
          </div>
        </section>
      </div>
    </main>
    <footer class="app-footer">
      <div class="app-footer__inner">
        <p class="app-footer__copy">© 2026 灵投智营 · 广告智能投放平台</p>
        <nav class="app-footer__links">
          <router-link class="app-footer__link" to="/#faq">帮助中心</router-link
          ><router-link class="app-footer__link" to="/legal/terms"
            >使用指南</router-link
          ><router-link class="app-footer__link" to="/legal/privacy"
            >隐私政策</router-link
          >
        </nav>
      </div>
    </footer>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  oauthUrl,
  listAccounts,
  type AccountVo,
  authorizationSession,
  hasQianchuanAuthorization,
  type AuthorizationSession,
} from "@/features/identity/oauthApi";
import QianchuanAccountSelection from './QianchuanAccountSelection.vue';
import { useUserStore } from "@/stores/user";
import { useWizardStore } from "@/stores/wizard";
import { dateText } from "@/pages/campaigns/reference/planPresentation";
import "./authorization.css";
const route = useRoute(),
  router = useRouter(),
  user = useUserStore(),
  expanded = ref(false),
  busy = ref(false),
  accounts = ref<AccountVo[]>([]),
  verified = ref(false),
  checking = ref(false);
const bindingSession = ref<AuthorizationSession | null>(null);
const state = computed(() =>
  route.path === "/oauth/callback"
    ? checking.value
      ? "verifying"
      : bindingSession.value && bindingSession.value.status !== 'COMPLETED'
        ? 'accounts'
      : route.query.ok === "1" && verified.value
        ? "success"
        : "failure"
    : route.path.endsWith("/authorizing")
      ? "authorizing"
      : "select",
);
const active = computed(
  () =>
    accounts.value
      .filter((a) => hasQianchuanAuthorization([a]))
      .sort((a, b) =>
        String(b.authorizedAt || "").localeCompare(
          String(a.authorizedAt || ""),
        ),
      )[0],
);
const accountName = computed(() => active.value?.accountName || "—");
const accountTime = computed(() => dateText(active.value?.authorizedAt));
const message = computed(() =>
  String(
    route.query.message ||
      route.query.error ||
      (route.query.ok === "1"
        ? checking.value ? "正在核验授权结果，请稍候" : "未找到有效授权记录，请重新授权"
        : "授权未完成，请重新授权"),
  ),
);
async function verify() {
  verified.value = false;
  bindingSession.value = null;
  if (route.path !== "/oauth/callback" || route.query.ok !== "1") return;
  checking.value = true;
  try {
    const sessionId = String(route.query.authorization_id || '');
    if (sessionId) {
      bindingSession.value = await authorizationSession(sessionId);
      if (bindingSession.value.status !== 'COMPLETED') return;
    }
    accounts.value = await listAccounts();
    verified.value = accounts.value.some((a) => hasQianchuanAuthorization([a])
      && (!bindingSession.value || bindingSession.value.boundAccountIds.includes(a.accountId)));
    if (verified.value && bindingSession.value) {
      const destination = bindingSession.value.returnPath === '/accounts?tab=oauth' ? '/accounts?tab=oauth' : '/campaigns';
      await router.replace(destination);
    }
  } catch {
    /* Failed verification remains a failure view. */
  } finally {
    checking.value = false;
  }
}
async function begin() {
  if (busy.value) return;
  busy.value = true;
  try {
    const result = await oauthUrl("QIANCHUAN");
    const url = new URL(result.url, location.origin);
    if (url.protocol !== 'https:' || url.hostname !== 'qianchuan.jinritemai.com')
      throw Error("授权链接无效");
    window.location.assign(url.href);
  } catch (e) {
    await router.replace({
      path: "/oauth/callback",
      query: {
        ok: "0",
        status: "fail",
        message: String(
          (e as { message?: string })?.message || "无法打开授权页，请稍后重试",
        ),
      },
    });
  } finally {
    busy.value = false;
  }
}
function action(name: string) {
  if (name === "authorize-qianchuan" || name === "retry-authorize") {
    if (route.path.endsWith("/authorizing")) void begin();
    else void router.push("/authorization/authorizing?start=1");
  } else if (name === "start-campaign") {
    useWizardStore().reset();
    void router.push("/campaigns/create");
  }
  else if (["continue-authorize", "change-account"].includes(name))
    void router.push("/authorization");
  else if (name === "back-home") void router.push("/");
  else if (["open-account", "contact-service"].includes(name))
    void router.push("/?modal=contact");
}
watch(
  () => route.fullPath,
  () => {
    expanded.value = false;
    void verify();
    if (route.path.endsWith("/authorizing") && route.query.start === "1")
      void begin();
  },
);
onMounted(() => {
  void verify();
  if (route.path.endsWith("/authorizing") && route.query.start === "1")
    void begin();
});
</script>
<style scoped>
.authorization-checking {
  text-align: center;
  min-height: 450px;
  padding: 100px 24px;
}
.authorization-checking h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 20px;
}
.authorization-page {
  font-family:
    system-ui,
    -apple-system,
    "Segoe UI",
    Roboto,
    "PingFang SC",
    "Microsoft YaHei",
    sans-serif;
  min-height: calc(100vh - 60px);
}
.authorization-retry {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
  padding: 0 24px;
  flex-wrap: wrap;
}
</style>
