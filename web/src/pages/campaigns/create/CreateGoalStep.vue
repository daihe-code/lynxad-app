<template>
  <div class="create-goal" @keydown.esc="accountOpen = false">
<svg xmlns="http://www.w3.org/2000/svg" style="position:absolute;width:0;height:0;overflow:hidden" aria-hidden="true">
  <symbol id="goal-lynx-mark" viewBox="0 0 1300 935">
    <g fill="none" stroke="currentColor" stroke-linecap="round">
      <path d="M1223.8 855.9C1219.3 837.9 1208.2 783 1196.8 747.6C1185.4 712.3 1170.6 677.8 1155.6 643.8C1140.7 609.7 1124.4 576.1 1107.2 543.1C1090 510.1 1072 477.5 1052.5 445.8C1033 414.1 1012.4 382.9 990.4 353C968.3 323 945.2 293.5 920.4 265.8C895.7 238.1 870.2 210.1 841.8 186.6C813.4 163.1 781.9 143.6 750 125C718 106.4 683.3 75 650 75C616.7 75 582 106.4 550 125C518.1 143.6 486.6 163.1 458.2 186.6C429.8 210.1 404.3 238.1 379.6 265.8C354.8 293.5 331.7 323 309.6 353C287.6 382.9 267 414.1 247.5 445.8C228 477.5 210 510.1 192.8 543.1C175.6 576.1 159.3 609.7 144.4 643.8C129.4 677.8 114.6 712.3 103.2 747.6C91.8 783 80.7 837.9 76.2 855.9" stroke-width="150"/>
      <path d="M82.8 175.9L285 455" stroke-width="58"/>
      <path d="M1217.2 175.9L1015 455" stroke-width="58"/>
    </g>
    <circle cx="440" cy="559" r="82.5" fill="currentColor"/>
    <circle cx="860" cy="559" r="82.5" fill="currentColor"/>
  </symbol>
  <symbol id="goal-ic-user" viewBox="0 0 24 24" fill="currentColor">
    <circle cx="12" cy="8.4" r="4.1"/>
    <path d="M4.4 20.4c0-3.6 3.4-6.2 7.6-6.2s7.6 2.6 7.6 6.2v.4H4.4v-.4Z"/>
  </symbol>
  <symbol id="goal-ic-bell" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
    <path d="M18.2 8.8a6.2 6.2 0 1 0-12.4 0c0 5.3-2.1 6.9-2.1 6.9h16.6s-2.1-1.6-2.1-6.9Z"/>
    <path d="M13.7 19.2a1.9 1.9 0 0 1-3.4 0"/>
  </symbol>
  <symbol id="goal-ic-caret" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
    <path d="m6 9.6 6 6 6-6"/>
  </symbol>
  <symbol id="goal-ic-menu" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
    <path d="M3.5 7h17M3.5 12h17M3.5 17h17"/>
  </symbol>
  <symbol id="goal-ic-mountain" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
    <path d="M2.6 19.4 9.4 7.6l4 6.5 2.1-3.2 5.9 8.5H2.6Z"/>
  </symbol>
  <symbol id="goal-ic-check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
    <path d="m5 12.6 4.6 4.4L19 7.4"/>
  </symbol>
  <symbol id="goal-ic-alert-fill" viewBox="0 0 24 24" fill="currentColor">
    <circle cx="12" cy="12" r="10"/>
    <path d="M12 6.4v7.2M12 17.1h.01" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round"/>
  </symbol>
  <symbol id="goal-ic-help-fill" viewBox="0 0 24 24" fill="currentColor">
    <circle cx="12" cy="12" r="10"/>
    <path d="M9.5 9.4a2.6 2.6 0 0 1 5 .9c0 1.7-2.4 2.1-2.4 3.6" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"/>
    <path d="M12.1 17.2h.01" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round"/>
  </symbol>
  <symbol id="goal-ic-bulb" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
    <path d="M12 3.6a5.6 5.6 0 0 0-3.3 10.1c.5.4.9 1 .9 1.6v1.1h4.8v-1.1c0-.6.4-1.2.9-1.6A5.6 5.6 0 0 0 12 3.6Z"/>
    <path d="M10.2 19.4h3.6"/>
    <path d="M13.2 7.4 10.5 11.3h2.9l-2.6 3.9"/>
  </symbol>
  <symbol id="goal-ic-shield-check" viewBox="0 0 24 24">
    <path fill="currentColor" d="M12 2.3c1.9 1.2 4.1 1.9 6.3 1.9.4 0 .8.3.8.8v6.3c0 4.6-3 8.8-7.1 10.4C7.9 20.1 4.9 15.9 4.9 11.3V5c0-.5.3-.8.8-.8 2.2 0 4.4-.7 6.3-1.9Z"/>
    <path fill="#fff" d="m8.1 11.8 1.5-1.5 2.1 2.1 4.1-4.2 1.5 1.5-5.6 5.7-3.6-3.6Z"/>
  </symbol>
  <symbol id="goal-ic-calendar" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
    <rect x="3.4" y="5" width="17.2" height="15.6" rx="2.6"/>
    <path d="M3.4 9.8h17.2M8 3.4v3.2M16 3.4v3.2"/>
  </symbol>
  <symbol id="goal-ic-arrow-right" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
    <path d="M4.4 12h15.2M13.4 5.8 19.6 12l-6.2 6.2"/>
  </symbol>
  <symbol id="goal-ic-arrow-left" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
    <path d="M19.6 12H4.4M10.6 5.8 4.4 12l6.2 6.2"/>
  </symbol>
  <!-- 圆圈对勾：横幅四特性（描边圆 + 对勾） -->
  <symbol id="goal-ic-check-circle" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
    <circle cx="12" cy="12" r="8.9"/>
    <path d="m8.2 12.3 2.5 2.5 5.1-5.3"/>
  </symbol>
  <symbol id="goal-ic-close" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
    <path d="M6.4 6.4l11.2 11.2M17.6 6.4 6.4 17.6"/>
  </symbol>
</svg>
    <section class="ad-banner" aria-label="投放计划概览">
      <div class="container-page ad-banner__inner">
        <div class="mt-[4px] min-w-0 banner-copy">
          <div class="ad-banner__head"><h1 class="ad-banner__title">灵投广告投放</h1><span class="ad-banner__tag"><svg class="h-[13px] w-[13px]" viewBox="0 0 24 24" aria-hidden="true"><path d="M2.6 19.4 9.4 7.6l4 6.5 2.1-3.2 5.9 8.5H2.6Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /></svg>抖音巨量千川 · 商品全域投放</span></div>
          <p class="ad-banner__sub">一次设置，广告自动出现在抖音商城、搜索、推荐等多个流量场景</p>
          <ul class="ad-banner__chips"><li v-for="text in bannerChips" :key="text" class="ad-chip"><svg class="ad-chip__ic" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8.9" fill="none" stroke="currentColor" stroke-width="1.7" /><path d="m8.2 12.3 2.5 2.5 5.1-5.3" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>{{ text }}</li></ul>
        </div>
        <div v-if="account" class="ad-account" @keydown.esc="accountOpen = false">
          <button class="ad-account__card w-full text-left" type="button" aria-haspopup="listbox" :disabled="saving" :aria-expanded="accountOpen" @click="accountOpen = !accountOpen">
            <span class="ad-account__name" :title="account.awemeName">{{ account.awemeName }}</span><span class="ad-account__id" :title="account.awemeId">抖音号ID：{{ account.awemeId }}</span>
            <span class="ad-account__btns"><span v-for="(chip, i) in accountChips" :key="chip" :class="i ? 'ad-account__btn-warn' : 'ad-account__btn-solid'">{{ chip }}</span></span>
            <span class="ad-account__chevron" aria-hidden="true"><svg class="h-4 w-4" viewBox="0 0 24 24"><use href="#goal-ic-caret" /></svg></span>
          </button>
          <div v-if="accountOpen" class="account-menu" role="listbox" aria-label="选择抖音号"><button v-for="opt in accounts" :key="opt.key" type="button" role="option" :aria-selected="opt.key === account.key" @click="accountOpen = false; emit('account', opt.key)">{{ opt.awemeName }}<small>{{ opt.awemeId }}</small></button></div>
          <p class="ad-account__hint">下拉切换需要投放广告的抖音号</p>
        </div>
        <select v-if="account" class="account-mobile" :disabled="saving" :value="account.key" aria-label="选择抖音号" @change="emit('account', ($event.target as HTMLSelectElement).value)"><option v-for="opt in accounts" :key="opt.key" :value="opt.key">{{ opt.awemeName }} · {{ opt.awemeId }}</option></select>
      </div>
    </section>
    <nav class="ad-stepper" aria-label="投放计划进度"><template v-for="(label, i) in steps" :key="label"><span class="step" :class="{ 'step--done': i === 0 }" :aria-current="i === 1 ? 'step' : undefined"><span class="step__dot"><svg v-if="i === 3" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-check" /></svg><template v-else>{{ i + 1 }}</template></span><span class="step__label">{{ label }}</span></span><span v-if="i < 3" class="step__line" aria-hidden="true" /></template></nav>
<main class="ad-main">
  <div class="container-page">

    <section class="panel panel--goal" aria-labelledby="panel-goal">
      <div class="panel__pad">
        <div class="panel__head">
          <div class="min-w-0">
            <h2 class="panel__title" id="panel-goal">设置投放目标</h2>
            <p class="panel__sub">系统智能托管模式，自动优化出价和投放节奏，你只需设定预算和时间</p>
          </div>
        </div>

        <p class="tip">
          <svg class="tip__icon h-[17px] w-[17px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-alert-fill"/></svg>
          <span class="tip__text">涉及投放成本和目标效果，需重点关注</span>
        </p>
      </div>

      <div class="panel__body pb-10"><p v-if="selectionWarning" class="selection-warning">{{ selectionWarning }}</p>

        <div class="form-row mt-[21px]">
          <p class="form-row__label" id="row-budget">投放方式和预算</p>
          <div class="form-row__body" role="group" aria-labelledby="row-budget">

            <p class="field__label" id="lbl-mode">投放方式</p>

            <div class="form-grid mt-2" role="radiogroup" aria-labelledby="lbl-mode">
              <button type="button" class="opt" role="radio" tabindex="0" :aria-checked="mode === 'SMART_BID_CUSTOM'" :disabled="saving" @click="emit('mode', 'SMART_BID_CUSTOM')" data-mode="cost">
                <span class="opt__row">
                  <span class="opt__radio" aria-hidden="true"></span>
                  <span class="opt__title">控成本投放</span>
                  <span class="opt__badge opt__badge--rec">推荐</span>
                </span>
                <span class="opt__sub">守住单价，少亏钱，适合测款</span>
              </button>

              <button type="button" class="opt" role="radio" tabindex="0" :aria-checked="mode === 'SMART_BID_CONSERVATIVE'" :disabled="saving" @click="emit('mode', 'SMART_BID_CONSERVATIVE')" data-mode="volume">
                <span class="opt__row">
                  <span class="opt__radio" aria-hidden="true"></span>
                  <span class="opt__title">放量投放</span>
                  <span class="opt__badge opt__badge--fast">起量快</span>
                </span>
                <span class="opt__sub">预算范围内，多推流量多接单，前期成本可能偏高</span>
              </button>
            </div>

            <div class="form-grid mt-6">

              <div class="field field--prefixed" data-field="budget">
                <p class="field__label">
                  <label for="in-budget">日预算</label>
                  <button class="field__label-btn" type="button" @click="hint('日预算：系统按商品与类目给出推荐值，可在推荐区间内自行调整')"
                          aria-label="日预算说明：系统根据商品与类目给出推荐日预算，可自行调整">
                    <svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-help-fill"/></svg>
                  </button>
                </p>

                <div class="field-input mt-[14px]">
                  <span class="field-input__slot" aria-hidden="true">¥</span>
                  <input class="field-input__box" id="in-budget" type="text" inputmode="decimal" v-model="budget" :disabled="saving"
                         autocomplete="off" @blur="budgetTouched = true"
                         aria-describedby="msg-budget" :aria-invalid="budgetTouched && !!budgetError">
                  <span class="field-input__unit">元/天</span>
                </div>

                <p class="hint-stack" id="hint-budget" v-if="!budgetTouched || !(budgetError || budgetWarn)">
                  <span class="hint">
                    <svg class="hint__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-bulb"/></svg>
                    <span>已填入系统推荐目标值，可调整</span>
                  </span>
                </p>

                <p :class="['field-msg', budgetError ? 'field-msg--error' : 'field-msg--warn']" id="msg-budget" v-if="budgetTouched && (budgetError || budgetWarn)">
                  <svg class="field-msg__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-alert-fill"/></svg>
                  <span>{{ budgetError || budgetWarn }}</span>
                </p>
              </div>


              <div class="field" data-field="roi" v-if="mode === 'SMART_BID_CUSTOM'">
                <p class="field__label">
                  <label for="in-roi">净成交ROI目标</label>
                  <button class="field__label-btn" type="button" @click="hint('净成交ROI =（整体成交金额 - 秒退订单成交额）/ 消耗')"
                          aria-label="净成交ROI目标说明：净成交ROI=（整体成交金额-秒退订单成交额）/ 消耗">
                    <svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-help-fill"/></svg>
                  </button>
                </p>

                <div class="field-input mt-[14px]">
                  <input class="field-input__box" id="in-roi" type="text" inputmode="decimal" v-model="roi" :disabled="saving"
                         autocomplete="off" @blur="roiTouched = true"
                         aria-describedby="msg-roi" :aria-invalid="roiTouched && !!roiError">
                  <span class="guar ml-2">
                    <svg class="guar__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-shield-check"/></svg>
                    享抖音平台保障
                  </span>
                </div>

                <div class="hint-stack mt-[9px]">
                  <span class="hint">
                    <svg class="hint__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-bulb"/></svg>
                    <span>已填入系统推荐目标值，可参考实际经营目标调整：</span>
                  </span>
                  <span class="hint hint--cont">可依据当前实际经营目标推算净成交ROI</span>
                  <span class="hint hint--cont">净成交ROI=（整体成交金额-秒退订单成交额）/ 消耗</span>
                </div>

                <p class="field-msg field-msg--error" id="msg-roi" v-if="roiTouched && roiError">
                  <svg class="field-msg__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-alert-fill"/></svg>
                  <span>{{ roiError }}</span>
                </p>
              </div>


              <div class="field est" data-field="estimate" v-else>
                <p class="est__title">连续投放7天预估带来：<span v-if="demoMode" class="estimate-demo">演示预估</span></p>
                <div class="est__rows">
                  <div class="est__row">
                    <span class="est__k">7日净成交ROI</span>
                    <span class="est__vwrap">
                      <b class="est__v">{{ demoMode ? '不低于 0.19' : '待评估' }}</b>
                      <span class="guar">
                        <svg class="guar__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-shield-check"/></svg>
                        保障福利
                      </span>
                    </span>
                  </div>
                  <div class="est__row">
                    <span class="est__k">7日净成交订单量</span>
                    <span class="est__vwrap"><b class="est__v" data-role="est-orders">{{ demoMode ? orderLo + ' ～ ' + orderHi + '单' : '待评估' }}</b></span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="divider-h mt-6"></div>


        <div class="form-row mt-[38px]">
          <p class="form-row__label" id="row-duration">投放时长</p>
          <div class="form-row__body" role="group" aria-labelledby="row-duration">

            <div class="dur" role="radiogroup" aria-label="投放时长">
              <div class="dur__item">
                <span class="dur__badge" aria-hidden="true">推荐</span>
                <button class="dur__btn" type="button" role="radio" :aria-checked="duration === 'long'"
                        :disabled="saving" @click="duration = 'long'">
                  <span class="dur__btn-title">长期投放</span>
                  <span class="dur__btn-sub">随时暂停</span>
                </button>
              </div>
              <div class="dur__item">
                <button class="dur__btn" type="button" role="radio" :aria-checked="duration === 'custom'"
                        :disabled="saving" @click="duration = 'custom'">
                  <span class="dur__btn-title">自定日期</span>
                </button>
              </div>
            </div>

            <div class="daterange" id="daterange" v-if="duration === 'custom'">
              <span class="daterange__sep">
                <svg class="h-[17px] w-[17px] text-ink-3" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-calendar"/></svg>
              </span>
              <label class="sr-only" for="in-start">投放开始日期</label>
              <input class="daterange__box" id="in-start" type="date" v-model="start" :disabled="saving">
              <span class="daterange__sep" aria-hidden="true">至</span>
              <label class="sr-only" for="in-end">投放结束日期</label>
              <input class="daterange__box" id="in-end" type="date" v-model="end" :disabled="saving">
            </div>

            <p v-if="duration === 'custom'" class="schedule-unavailable" role="status">自定日期暂未开放，请选择长期投放后继续。</p>
            <div class="hint-stack mt-[25px]">
              <span class="hint">
                <svg class="hint__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-bulb"/></svg>
                <span>投放期间可随时暂停或调整，到期后自动停止</span>
              </span>
              <span class="hint">
                <svg class="hint__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-bulb"/></svg>
                <span>建议至少投放 2 周及以上，覆盖完整的冷启动 → 稳定期 → 放量期</span>
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>

  </div>
</main>
<div class="actionbar">
  <div class="container-page actionbar__inner">
    <button class="btn-prev" type="button" :disabled="saving" @click="emit('prev')">
      <svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-arrow-left"/></svg>
      上一步
    </button>
    <button class="btn-next-primary" type="button" :disabled="saving || !!budgetError || !!roiError || duration === 'custom'" @click="openConfirm">
      下一步
      <svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-arrow-right"/></svg>
    </button>
  </div>
</div>

<div class="modal" v-if="dialogOpen" id="gen-confirm" role="dialog" aria-modal="true" aria-labelledby="gen-confirm-title" @keydown.esc.prevent="closeConfirm" @keydown.tab="trapFocus">
  <div class="modal__mask" @click="closeConfirm"></div>

  <div ref="dialogBox" class="modal__box modal__box--lg" tabindex="-1">
    <div class="modal__head">
      <h2 class="modal__title" id="gen-confirm-title">素材智能生成确认</h2>
      <button class="modal__close" type="button" :disabled="saving" @click="closeConfirm" aria-label="关闭">
        <svg viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-close"/></svg>
      </button>
    </div>

    <p class="modal__intro">
      系统将进入素材智能生成环节，会针对已选商品匹配生成最佳投放效果素材！<br>
      如部分商品已有可用素材（视频、图片、图文）无需系统生成，可选出：
    </p>

    <div class="mfield mt-[18px]">
      <p class="mfield__label"><label for="gen-prod">选择商品</label></p>
      <div class="mfield__ctrl">
        <div class="msel">
          <button class="msel__box" id="gen-prod" type="button" @click="productMenu = !productMenu"
                  :aria-expanded="productMenu" aria-haspopup="listbox">
            <span class="msel__ph" data-role="prod-text">请选择不需生成素材的商品，不选代表均需生成</span>
            <svg class="msel__caret" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-caret"/></svg>
          </button>
          <div class="msel__menu" role="listbox" aria-multiselectable="true" data-role="prod-menu" v-show="productMenu"
               aria-label="选择不需生成素材的商品">
            <button v-for="p in selected" :key="p.productId" class="msel__opt" type="button" role="option" aria-selected="false" disabled title="已有素材复用暂未开放"><span class="msel__box2" aria-hidden="true" /><span class="msel__name">{{ p.name }}</span></button>
          </div>
        </div>
        <div class="spec spec--plain spec--tip">
          <svg class="spec__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-bulb"/></svg>
          <span>已有素材复用暂未开放，本次将为全部已选商品生成素材</span>
        </div>
      </div>
    </div>

    <div class="modal__foot">
      <button class="btn-modal-ghost" type="button" :disabled="saving" @click="closeConfirm">取消</button>
      <button class="btn-modal-primary" type="button" :disabled="saving" @click="emit('next')">{{ saving ? '处理中…' : '确定' }}</button>
    </div>
  </div>
</div>


  </div>
</template>

<script setup lang="ts">
import { nextTick, onUnmounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { ProductVo } from '@/features/catalog/catalogApi';
interface AccountOption { key: string; awemeName: string; awemeId: string }
const props = defineProps<{ mode: string; saving: boolean; budgetError: string; budgetWarn: string; roiError: string; orderLo: number; orderHi: number; selectionWarning: string; demoMode: boolean; selected: ProductVo[]; account?: AccountOption; accounts: AccountOption[]; accountChips: string[] }>();
const budget = defineModel<string>('budget', { required: true });
const roi = defineModel<string>('roi', { required: true });
const duration = defineModel<'long' | 'custom'>('duration', { required: true });
const start = defineModel<string>('start', { required: true });
const end = defineModel<string>('end', { required: true });
const emit = defineEmits<{ mode: [mode: 'SMART_BID_CUSTOM' | 'SMART_BID_CONSERVATIVE']; account: [key: string]; prev: []; next: [] }>();
const bannerChips = ['商城+搜索等全域投放', '智能生成素材', '智能出价', '实时监控+优化'];
const steps = ['选择商品', '设置投放目标', '定素材并投放', '查看投放结果'];
const accountOpen = ref(false), budgetTouched = ref(false), roiTouched = ref(false);
const dialogOpen = ref(false), productMenu = ref(false), dialogBox = ref<HTMLElement>();
let previousOverflow = '';
const hint = (message: string) => ElMessage.info(message);
watch(() => props.mode, () => { budgetTouched.value = false; roiTouched.value = false; });
watch(dialogOpen, async open => {
  if (open) { previousOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; await nextTick(); dialogBox.value?.focus(); }
  else { document.body.style.overflow = previousOverflow; productMenu.value = false; }
});
function openConfirm() {
  budgetTouched.value = true; roiTouched.value = true;
  if (props.saving || props.budgetError || props.roiError || duration.value === 'custom') return;
  accountOpen.value = false; dialogOpen.value = true;
}
async function closeConfirm() {
  if (props.saving) return;
  dialogOpen.value = false;
  await nextTick(); document.querySelector<HTMLButtonElement>('.create-goal .btn-next-primary')?.focus();
}
function trapFocus(event: KeyboardEvent) {
  const buttons = dialogBox.value?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), [tabindex="0"]');
  if (!buttons?.length) { event.preventDefault(); return; }
  const first = buttons[0], last = buttons[buttons.length - 1];
  if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogBox.value)) { event.preventDefault(); last?.focus(); }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
}
onUnmounted(() => { if (dialogOpen.value) document.body.style.overflow = previousOverflow; });
</script>
<style scoped src="./create-goal.css"></style>
