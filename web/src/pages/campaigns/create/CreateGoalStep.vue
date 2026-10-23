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
    <template v-if="!embedded"><section class="ad-banner" aria-label="投放计划概览">
      <div class="container-page ad-banner__inner">
        <div class="mt-[4px] min-w-0 banner-copy">
          <div class="ad-banner__head"><h1 class="ad-banner__title">灵投广告投放</h1><span class="ad-banner__tag"><svg class="h-[13px] w-[13px]" viewBox="0 0 24 24" aria-hidden="true"><path d="M2.6 19.4 9.4 7.6l4 6.5 2.1-3.2 5.9 8.5H2.6Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" /></svg>抖音巨量千川 · 商品全域投放</span></div>
          <p class="ad-banner__sub">一次设置，广告自动出现在抖音商城、搜索、推荐等多个流量场景</p>
          <ul class="ad-banner__chips"><li v-for="text in bannerChips" :key="text" class="ad-chip"><svg class="ad-chip__ic" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8.9" fill="none" stroke="currentColor" stroke-width="1.7" /><path d="m8.2 12.3 2.5 2.5 5.1-5.3" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" /></svg>{{ text }}</li></ul>
        </div>
        <div v-if="account" class="ad-account" @keydown.esc="accountOpen = false">
          <button class="ad-account__card w-full text-left" type="button" aria-haspopup="listbox" :disabled="saving || readOnly || settingsOnly" :aria-expanded="accountOpen" @click="accountOpen = !accountOpen">
            <AwemeAccountSummary :account="account" :chips="accountChips" />
            <span class="ad-account__chevron" aria-hidden="true"><svg class="h-4 w-4" viewBox="0 0 24 24"><use href="#goal-ic-caret" /></svg></span>
          </button>
          <div v-if="accountOpen" class="account-menu" role="listbox" aria-label="选择抖音号"><button v-for="opt in accounts" :key="opt.key" type="button" role="option" :aria-selected="opt.key === account.key" @click="accountOpen = false; emit('account', opt.key)">{{ opt.awemeName }}<small>{{ opt.awemeId }}</small></button></div>
          <p class="ad-account__hint">下拉切换需要投放广告的抖音号</p>
        </div>
        <select v-if="account" class="account-mobile" :disabled="saving || readOnly || settingsOnly" :value="account.key" aria-label="选择抖音号" @change="emit('account', ($event.target as HTMLSelectElement).value)"><option v-for="opt in accounts" :key="opt.key" :value="opt.key">{{ opt.awemeName }} · {{ opt.awemeId }}</option></select>
      </div>
    </section>
    <nav class="ad-stepper" aria-label="投放计划进度"><template v-for="(label, i) in steps" :key="label"><button type="button" :disabled="saving || i === 3 || i+1 > (maxStep ?? 2)" @click="emit('navigate', i+1)" :aria-label="label" class="step" :class="{ 'step--done': i === 0 }" :aria-current="i === 1 ? 'step' : undefined"><span class="step__dot"><svg v-if="i === 3" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-check" /></svg><template v-else>{{ i + 1 }}</template></span><span class="step__label">{{ label }}</span></button><span v-if="i < 3" class="step__line" aria-hidden="true" /></template></nav>
</template><main class="ad-main">
  <div class="container-page">

    <section class="panel panel--goal" aria-labelledby="panel-goal">
      <div class="panel__pad">
        <div class="panel__head">
          <div class="min-w-0">
            <h2 class="panel__title" id="panel-goal">设置投放目标</h2>
            <p class="panel__sub">确认预算、目标和时间，我会据此准备投放方案</p>
          </div>
        </div>
      </div>

      <div class="panel__body pb-10"><p v-if="selectionWarning" class="selection-warning">{{ selectionWarning }}</p>

        <div class="form-row mt-[21px]">
          <p class="form-row__label" id="row-budget">投放方式和预算</p>
          <div class="form-row__body" role="group" aria-labelledby="row-budget">

            <p class="field__label" id="lbl-mode">投放方式</p>

            <div class="form-grid mt-2" role="radiogroup" aria-labelledby="lbl-mode">
              <button type="button" class="opt" role="radio" tabindex="0" :aria-checked="mode === 'SMART_BID_CUSTOM'" :disabled="saving || readOnly || (settingsOnly && !allowModeChange)" @click="emit('mode', 'SMART_BID_CUSTOM')" data-mode="cost">
                <span class="opt__row">
                  <span class="opt__radio" aria-hidden="true"></span>
                  <span class="opt__title">控成本投放</span>
                  <span class="opt__badge opt__badge--rec">推荐</span>
                </span>
                <span class="opt__sub">按你设置的 ROI 目标争取成交，目标设置过高可能影响起量</span>
              </button>

              <button type="button" class="opt" role="radio" tabindex="0" :aria-checked="mode === 'SMART_BID_CONSERVATIVE'" :disabled="saving || readOnly || (settingsOnly && !allowModeChange)" @click="emit('mode', 'SMART_BID_CONSERVATIVE')" data-mode="volume">
                <span class="opt__row">
                  <span class="opt__radio" aria-hidden="true"></span>
                  <span class="opt__title">放量投放</span>
                  <span class="opt__badge opt__badge--fast">起量快</span>
                </span>
                <span class="opt__sub">在预算范围内争取更多成交，实际成本可能出现波动</span>
              </button>
            </div>

            <p v-if="settingsOnly && !allowModeChange" class="hint-stack published-bid-hint" role="status">{{ PUBLISHED_BID_MODE_HINT }}</p>

            <div class="form-grid mt-6">

              <div class="field field--prefixed" data-field="budget">
                <p class="field__label">
                  <label for="in-budget">日预算</label>
                  <el-popover trigger="click" placement="bottom-start" :width="300"><template #reference><button class="field__label-btn" type="button"
                          aria-label="日预算说明：系统根据商品与类目给出推荐日预算，可自行调整">
                    <svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-help-fill"/></svg>
                  </button></template><p style="font-size:13px;line-height:1.7">日预算：系统按商品与类目给出推荐值，可在推荐区间内自行调整</p></el-popover>
                </p>

                <div class="field-input mt-[14px]">
                  <span class="field-input__slot" aria-hidden="true">¥</span>
                  <el-popover v-model:visible="budgetOpen" trigger="click" placement="bottom-start" :width="438" :offset="8" :show-arrow="false" :disabled="mode !== 'SMART_BID_CONSERVATIVE' || saving || readOnly || (settingsOnly && !allowModeChange)" popper-class="budget-tiers-popover">
                    <template #reference>
                      <input class="field-input__box" id="in-budget" type="text" inputmode="decimal" v-model="budget" :disabled="saving || readOnly"
                             autocomplete="off" @input="budgetTouched = true" @blur="budgetTouched = true" @keydown.down.prevent="focusBudgetOptions" @keydown.esc.stop="budgetOpen = false"
                             :aria-haspopup="mode === 'SMART_BID_CONSERVATIVE' ? 'dialog' : undefined" :aria-expanded="mode === 'SMART_BID_CONSERVATIVE' ? budgetOpen : undefined"
                             aria-describedby="hint-budget-minimum msg-budget" :aria-invalid="budgetTouched && !!budgetError">
                    </template>
                    <div ref="budgetList" class="budget-tiers" role="group" aria-label="推荐日预算档位" @keydown.esc.stop="budgetOpen = false">
                      <button v-for="option in budgetOptions" :key="option.budget" class="budget-tier" type="button" :aria-pressed="Number(budget) === option.budget" :disabled="saving || readOnly || (settingsOnly && !allowModeChange)" @click="chooseBudget(option.budget)">
                        <span class="budget-tier__radio" aria-hidden="true"></span><b class="budget-tier__amount">{{ option.budget }}元</b>
                        <span class="budget-tier__estimates"><span>预计ROI <strong>{{ option.roi }}</strong></span><span>订单量 <strong>{{ option.orders }}</strong></span></span>
                        <span v-if="option.badge" class="budget-tier__badge">{{ option.badge }}</span>
                      </button>
                      <p v-if="!budgetOptions?.length" class="budget-tiers__empty">千川暂未提供推荐档位，可手动输入日预算</p>
                      <p class="budget-tiers__note">平台预算预估，仅供参考，以实际投放结果为准</p>
                    </div>
                  </el-popover>
                  <span class="field-input__unit">元/天</span>
                </div>

                <p v-if="mode === 'SMART_BID_CUSTOM' && !(budgetTouched && budgetError)" class="hint-stack mt-[9px]" id="hint-budget-minimum"><span class="hint">{{ COST_DAILY_BUDGET_HINT }}</span></p>
                <p class="hint-stack mt-[9px]" id="hint-budget" v-if="mode === 'SMART_BID_CONSERVATIVE' && (!budgetTouched || !(budgetError || budgetWarn))">
                  <span class="hint">
                    <svg class="hint__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-bulb"/></svg>
                    <span>{{ budgetHint || '请根据实际经营情况填写日预算' }}</span>
                  </span>
                </p>

                <p :class="['field-msg', budgetError ? 'field-msg--error' : 'field-msg--warn']" id="msg-budget" role="alert" v-if="budgetTouched && (budgetError || budgetWarn)">
                  <svg class="field-msg__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-alert-fill"/></svg>
                  <span>{{ budgetError || budgetWarn }}</span>
                </p>
              </div>


              <div class="field" data-field="roi" v-if="mode === 'SMART_BID_CUSTOM'">
                <p class="field__label">
                  <label for="in-roi">净成交ROI目标</label>
                  <el-popover trigger="click" placement="bottom-start" :width="300"><template #reference><button class="field__label-btn" type="button"
                          aria-label="净成交ROI目标说明：净成交ROI=（整体成交金额-秒退订单成交额）/ 消耗">
                    <svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-help-fill"/></svg>
                  </button></template><p style="font-size:13px;line-height:1.7">净成交ROI =（整体成交金额 - 秒退订单成交额）/ 消耗</p></el-popover>
                </p>

                <div class="field-input mt-[14px]">
                  <input class="field-input__box" id="in-roi" type="text" inputmode="decimal" v-model="roi" :disabled="saving || readOnly"
                         autocomplete="off" @blur="roiTouched = true"
                         aria-describedby="msg-roi" :aria-invalid="roiTouched && !!roiError">
                  <span class="guar ml-2">
                    <svg class="guar__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-shield-check"/></svg>
                    平台保障以规则为准
                  </span>
                </div>

                <div class="hint-stack mt-[9px]">
                  <span class="hint">
                    <svg class="hint__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-bulb"/></svg>
                    <span>{{ roiHint || '请参考实际经营目标设置 ROI：' }}</span>
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
                <div class="est__header">
                  <p class="est__title">所选日预算的平台预估：<span v-if="demoMode" class="estimate-demo">演示预估</span></p>
                  <span v-if="!estimateHint" class="guar">
                    <svg class="guar__icon" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-shield-check"/></svg>
                    以平台结果为准
                  </span>
                </div>
                <div class="est__rows">
                  <div class="est__row">
                    <span class="est__k">预估ROI</span>
                    <span class="est__vwrap">
                      <b class="est__v">{{ estimateRoiHint || '待平台评估' }}</b>
                    </span>
                  </div>
                  <div class="est__row">
                    <span class="est__k">预估成交订单量</span>
                    <span class="est__vwrap"><b class="est__v" data-role="est-orders">{{ estimateOrdersHint || '待平台评估' }}</b></span>
                  </div>
                </div>
                <p v-if="estimateHint" class="est__message" role="status">{{ estimateHint }}</p>
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
                        :disabled="saving || readOnly || settingsOnly" @click="duration = 'long'">
                  <span class="dur__btn-title">长期投放</span>
                  <span class="dur__btn-sub">随时暂停</span>
                </button>
              </div>
              <div class="dur__item">
                <button class="dur__btn" type="button" role="radio" :aria-checked="duration === 'custom'"
                        :disabled="saving || readOnly || settingsOnly" @click="duration = 'custom'">
                  <span class="dur__btn-title">自定日期</span>
                </button>
              </div>
            </div>

            <div class="daterange" id="daterange" v-if="duration === 'custom'">
              <span class="daterange__sep">
                <svg class="h-[17px] w-[17px] text-ink-3" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-calendar"/></svg>
              </span>
              <label class="sr-only" for="in-start">投放开始日期</label>
              <input class="daterange__box" id="in-start" type="date" v-model="start" :disabled="saving || readOnly || settingsOnly">
              <span class="daterange__sep" aria-hidden="true">至</span>
              <label class="sr-only" for="in-end">投放结束日期</label>
              <input class="daterange__box" id="in-end" type="date" v-model="end" :disabled="saving || readOnly || settingsOnly">
            </div>

            <p v-if="dateError" class="schedule-unavailable" role="status">{{ dateError }}</p>
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
<div v-if="!embedded" class="actionbar">
  <div class="container-page actionbar__inner">
    <button class="btn-prev" type="button" :disabled="saving || readOnly || settingsOnly" @click="emit('prev')">
      <svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-arrow-left"/></svg>
      上一步
    </button>
    <button class="btn-next-primary" type="button" :disabled="saving || !!budgetError || !!roiError || !!dateError" @click="nextStep">
      下一步
      <svg class="h-[15px] w-[15px]" viewBox="0 0 24 24" aria-hidden="true"><use href="#goal-ic-arrow-right"/></svg>
    </button>
    <button class="btn-cancel" type="button" :disabled="saving || readOnly || settingsOnly" @click="emit('cancel')">取消投放</button>
  </div>
</div>

  </div>
</template>

<script setup lang="ts">
import AwemeAccountSummary from '@/components/AwemeAccountSummary.vue';
import { nextTick, ref, watch } from 'vue';

import { PUBLISHED_BID_MODE_HINT } from '@/utils/labels';
import type { BudgetOption } from '@/features/delivery/creationRecommendations';
import { COST_DAILY_BUDGET_HINT } from '@/features/delivery/budgetValidation';
interface AccountOption { key: string; awemeName: string; awemeId: string; avatar?: string }
const props = defineProps<{ mode: string; saving: boolean; budgetError: string; budgetWarn: string; roiError: string; orderLo: number; orderHi: number; dateError?: string; estimateRoiHint?: string; estimateOrdersHint?: string; estimateHint?: string; budgetOptions?: BudgetOption[]; budgetHint?: string; roiHint?: string; selectionWarning: string; demoMode: boolean; account?: AccountOption; accounts: AccountOption[]; accountChips: string[]; readOnly?: boolean; embedded?: boolean; settingsOnly?: boolean; allowModeChange?: boolean; maxStep?: number }>();
const budget = defineModel<string>('budget', { required: true });
const roi = defineModel<string>('roi', { required: true });
const duration = defineModel<'long' | 'custom'>('duration', { required: true });
const start = defineModel<string>('start', { required: true });
const end = defineModel<string>('end', { required: true });
const emit = defineEmits<{ navigate: [step:number]; mode: [mode: 'SMART_BID_CUSTOM' | 'SMART_BID_CONSERVATIVE']; account: [key: string]; prev: []; next: []; cancel: [] }>();
const bannerChips = ['商城+搜索等全域投放', '智能生成素材', '智能出价', '实时监控+优化'];
const steps = ['选择商品', '设置投放目标', '定素材并投放', '查看投放结果'];
const accountOpen = ref(false), budgetTouched = ref(false), roiTouched = ref(false);
const budgetOpen = ref(false), budgetList = ref<HTMLElement>();
function chooseBudget(value: number) {
  if (props.readOnly || (props.settingsOnly && !props.allowModeChange) || props.saving || props.mode !== 'SMART_BID_CONSERVATIVE') return;
  budget.value = String(value);budgetTouched.value = true;budgetOpen.value = false;
}
async function focusBudgetOptions() {
  if (props.readOnly || (props.settingsOnly && !props.allowModeChange) || props.saving || props.mode !== 'SMART_BID_CONSERVATIVE') return;
  budgetOpen.value = true;await nextTick();
  (budgetList.value?.querySelector<HTMLButtonElement>('[aria-pressed="true"]') || budgetList.value?.querySelector<HTMLButtonElement>('button'))?.focus();
}
watch(() => props.saving, value => { if (value) budgetOpen.value = false; });



// Switching modes changes the allowed budget range even when the amount is unchanged.
watch(() => props.mode, () => { budgetTouched.value = true; roiTouched.value = false; budgetOpen.value = false; });
function nextStep() {
  budgetTouched.value = true; roiTouched.value = true;
  if (props.saving || props.budgetError || props.roiError || props.dateError) return;
  accountOpen.value = false;
  emit('next');
}
</script>
<style scoped src="./create-goal.css"></style>

<style scoped>
</style>
<style scoped>
:global(.el-popover.el-popper.budget-tiers-popover){box-sizing:border-box;max-width:calc(100vw - 24px);padding:12px 14px;border:1px solid #e0e2ef;border-radius:12px;box-shadow:0 10px 30px #1a1b2e1a;font-family:inherit}
.budget-tiers{display:flex;flex-direction:column;gap:10px}
.budget-tier{position:relative;display:flex;align-items:center;gap:12px;width:100%;min-height:64px;padding:16px 18px;border:1px solid #dedee7;border-radius:10px;background:white;color:#1a1b2e;text-align:left;cursor:pointer}
.budget-tier:hover,.budget-tier:focus-visible{border-color:#575cf6;outline:none;box-shadow:0 0 0 2px #575cf61a}
.budget-tier[aria-pressed=true]{border-color:#575cf6;background:#fafaff}
.budget-tier__radio{box-sizing:border-box;width:20px;height:20px;flex-shrink:0;border:1px solid #c6c7d2;border-radius:50%}
.budget-tier[aria-pressed=true] .budget-tier__radio{border:6px solid #315cf5}
.budget-tier__amount{font-size:18px;line-height:24px;white-space:nowrap}
.budget-tier__estimates{display:flex;flex-wrap:wrap;gap:4px 8px;font-size:12px;line-height:20px;color:#60647a}
.budget-tier__estimates strong{font-weight:700;color:#315cf5;font-size:14px}
.budget-tier__badge{position:absolute;top:0;right:0;border-radius:0 9px 0 4px;padding:1px 4px;font-size:12px;line-height:16px;background:#edf0ff;color:#315cf5;font-weight:600}
.budget-tier[aria-pressed=true] .budget-tier__badge{background:#315cf5;color:white}
.budget-tiers__empty,.budget-tiers__note{margin:0;font-size:12px;line-height:20px;color:#8588a5}
@media(max-width:480px){.budget-tier{padding:18px 12px 12px;gap:8px}.budget-tier__estimates{flex-direction:column}.budget-tier__amount{font-size:16px}}
</style>
