<template>
  <div class="campaign-overview ad-step3 ad-step4 ad-overview">
    <CreationFrame :step="4" :account="frameAccount" />
    <main class="ad-main">
      <div class="container-page">
        <p v-if="loading && !plan" class="overview-empty">正在加载投放计划…</p>
        <div v-else-if="failed" class="overview-empty">
          <p>计划数据加载失败</p>
          <button class="btn-res" @click="load">重试</button>
        </div>
        <section v-else-if="plan" class="st" :data-st="tone">
          <div class="panel pcard">
            <div class="pcard__pad">
              <div class="ovtop">
                <div class="min-w-0">
                  <h2 class="ovtitle">投放计划状态总览</h2>
                  <p class="ovsub">
                    计划名称 <b>{{ plan.name }}</b> · 计划 ID
                    <b>{{ plan.qcPlanId || plan.planId }}</b> · 投放抖音号
                    <b>{{ shop }}</b>
                  </p>
                </div>
                <span class="rjcount">当前状态 · {{ info[0] }}</span>
              </div>
              <div class="ovstate mt-[26px]">
                <div class="ovstate__l">
                  <span class="ovstate__ico" :class="`ico-tone--${tone}`"
                    ><ReferenceIcon :name="info[2] || 'doc'"
                  /></span>
                  <div>
                    <div class="ovstate__label">{{ info[0] }}</div>
                    <div class="ovstate__sub">{{ stateDescription }}</div>
                  </div>
                </div>
                <div class="ovstate__ops">
                  <router-link
                    v-if="!['PENDING_AUDIT','SUBMITTING'].includes(plan.status)"
                    class="btn-res"
                    :to="`/campaigns/manage/create?copy=${plan.planId}`"
                    >重建一条计划</router-link
                  ><router-link class="btn-res btn-res--primary" to="/campaigns"
                    >查看所有投放计划</router-link
                  >
                </div>
              </div>
              <PlanReviewNotice :plan="plan" @updated="plan = $event" />
              <div
                v-if="['pending', 'reviewing', 'passed'].includes(tone)"
                class="ovsec"
              >
                <div class="ovsec__head">
                  <h3 class="ovsec__title">
                    <ReferenceIcon name="hourglass" />{{
                      tone === "pending"
                        ? "提交进度"
                        : tone === "passed"
                          ? "投放安排"
                          : "审核进度"
                    }}
                  </h3>
                  <button
                    class="btn-refresh ml-auto"
                    :disabled="loading"
                    @click="refresh"
                  >
                    <ReferenceIcon name="refresh" />{{
                      loading ? "刷新中…" : "手动刷新"
                    }}
                  </button>
                </div>
                <div class="ovlist">
                  <div
                    v-for="pair in progressRows"
                    :key="pair[0]"
                    class="ovrow"
                  >
                    <span class="ovrow__k">{{ pair[0] }}</span
                    ><span class="ovrow__v">{{ pair[1] }}</span>
                  </div>
                </div>
                <p class="ovmuted">
                  状态由巨量千川返回，页面会定时同步；可返回列表继续其他操作。
                </p>
              </div>
              <div v-if="tone === 'rejected'" class="ovsec">
                <div class="ovsec__head">
                  <h3 class="ovsec__title">
                    <ReferenceIcon name="warn-tri" />审核拒绝原因
                  </h3>
                </div>
                <div class="ovnotice ovnotice--danger">
                  {{
                    plan.rejectReason ||
                    "平台暂未返回具体原因，请在千川查看审核详情。"
                  }}
                </div>
                <router-link
                  v-if="plan.source !== 'EXTERNAL'"
                  class="btn-res btn-res--primary"
                  :to="`/campaigns/${plan.planId}/edit`"
                  >修改并重新提交</router-link
                >
              </div>
              <div
                v-if="['running', 'paused', 'stopped', 'done'].includes(tone)"
                class="ovsec"
              >
                <div class="ovsec__head">
                  <h3 class="ovsec__title">
                    <ReferenceIcon name="chart-up" />{{
                      terminal ? "最终投放数据" : "核心投放数据"
                    }}
                  </h3>
                  <span class="ovsec__hint"
                    >最新快照：{{
                      dateText(plan.snapshotTs || report?.snapshotTs)
                    }}</span
                  >
                </div>
                <div v-if="hasData" class="ovgrid">
                  <div v-for="m in cards" :key="m.label" class="m4card">
                    <div class="m4card__k">
                      <ReferenceIcon :name="m.icon" />{{ m.label }}
                    </div>
                    <div class="m4card__v">{{ m.value }}</div>
                    <p class="m4card__f">{{ m.note }}</p>
                  </div>
                </div>
                <p v-else class="ovmuted">暂无此计划的投放数据快照。</p>
              </div>
              <div v-if="tone === 'paused' || terminal" class="ovsec">
                <div class="ovsec__head">
                  <h3 class="ovsec__title">
                    <ReferenceIcon :name="terminal ? 'stop' : 'pause'" />{{
                      terminal ? "结束信息" : "暂停信息"
                    }}
                  </h3>
                </div>
                <div class="ovlist">
                  <div class="ovrow">
                    <span class="ovrow__k">最近操作</span
                    ><span class="ovrow__v">{{
                      latestAction?.summary ||
                      "暂无对应操作记录，请以平台状态为准"
                    }}</span>
                  </div>
                  <div class="ovrow">
                    <span class="ovrow__k">记录时间</span
                    ><span class="ovrow__v">{{
                      dateText(latestAction?.snapshotTs)
                    }}</span>
                  </div>
                </div>
              </div>
              <div class="ovsec">
                <div class="ovsec__head">
                  <h3 class="ovsec__title">
                    <ReferenceIcon name="list" />计划设置
                  </h3>
                </div>
                <div class="ovlist">
                  <div v-for="pair in settingRows" :key="pair[0]" class="ovrow">
                    <span class="ovrow__k">{{ pair[0] }}</span
                    ><span class="ovrow__v">{{ pair[1] }}</span>
                  </div>
                </div>
                <div class="overview-links">
                  <router-link :to="`/campaigns/${plan.planId}/result`"
                    >查看投放结果</router-link
                  ><router-link :to="`/campaigns/${plan.planId}/analysis`"
                    >查看效果分析</router-link
                  ><router-link :to="`/campaigns/${plan.planId}`"
                    >查看完整计划</router-link
                  >
                </div>
              </div>
            </div>
          </div>
          <div class="panel pcard">
            <div class="pcard__pad">
              <div class="pcard__head">
                <h3 class="pcard__title">状态流转记录</h3>
                <span class="pcard__hint">已记录的操作与当前平台状态</span>
              </div>
              <div class="vtl">
                <div v-if="plan.createdAt" class="vtl__item vtl__item--done">
                  <span class="vtl__rail"><span class="vtl__dot" /></span>
                  <div class="vtl__body">
                    <div class="vtl__row">
                      <span class="vtl__name">计划创建</span
                      ><span class="vtl__time">{{
                        dateText(plan.createdAt)
                      }}</span>
                    </div>
                    <p class="vtl__desc">{{ plan.name }}</p>
                  </div>
                </div>
                <div
                  v-for="a in actions"
                  :key="a.id"
                  class="vtl__item vtl__item--done"
                >
                  <span class="vtl__rail"><span class="vtl__dot" /></span>
                  <div class="vtl__body">
                    <div class="vtl__row">
                      <span class="vtl__name">{{ ruleLabel(a.type) }}</span
                      ><span class="vtl__time">{{
                        dateText(a.snapshotTs)
                      }}</span>
                    </div>
                    <p class="vtl__desc">{{ a.summary }}</p>
                  </div>
                </div>
                <div class="vtl__item vtl__item--now">
                  <span class="vtl__rail"><span class="vtl__dot" /></span>
                  <div class="vtl__body">
                    <span class="vtl__name">当前状态：{{ info[0] }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted } from "vue";
import { ruleLabel } from "@/features/agentops/agentopsApi";
import {
  usePlanPresentation,
  statusInfo,
  amount,
  dateText,
} from "./planPresentation";
import ReferenceIcon from "./ReferenceIcon.vue";
import CreationFrame from "./CreationFrame.vue";
import PlanReviewNotice from '../components/PlanReviewNotice.vue';
import "./campaign-overview.css";
const { plan, shop, actions, report, metrics, hasData, loading, failed, load } =
  usePlanPresentation();
const info = computed(() => statusInfo(plan.value?.status));
const tone = computed(() =>
  info.value[1] === "bad" ? "rejected" : info.value[1] || "pending",
);
const terminal = computed(() => ["stopped", "done"].includes(tone.value));
const frameAccount = computed(() =>
  plan.value
    ? { awemeName: shop.value, awemeId: plan.value.awemeId || "—" }
    : null,
);
const stateDescription = computed(
  () =>
    (
      ({
        pending: "正在准备或提交计划，请以平台同步结果为准",
        reviewing: "平台审核中，可正常进行其他操作",
        rejected: "审核未通过，请查看原因并调整计划",
        passed: "计划已通过审核，等待开始投放",
        running: "广告计划投放中，以下为最新统计快照",
        paused: "广告计划已暂停投放",
        stopped: "计划已结束，历史数据保留",
        done: "广告计划投放已完成",
      }) as Record<string, string>
    )[tone.value],
);
const progressRows = computed(() => [
  ["当前阶段", info.value[0] || "—"],
  ["平台计划ID", plan.value?.qcPlanId || "待平台返回"],
  ["计划创建时间", dateText(plan.value?.createdAt)],
  ["投放开始日期", plan.value?.startDate || "—"],
]);
const settingRows = computed(() => [
  [
    "投放模式",
    plan.value?.smartBidType === "SMART_BID_CUSTOM" ? "控成本投放" : "放量投放",
  ],
  ["日预算", amount(plan.value?.budget, "¥")],
  ["净成交ROI目标", amount(plan.value?.roi2Goal)],
  ["投放商品", `${plan.value?.products?.length || 0} 款`],
  [
    "投放时间",
    `${plan.value?.startDate || "—"} ~ ${plan.value?.endDate || "不限"}`,
  ],
]);
const latestAction = computed(() =>
  actions.value.find((a) =>
    terminal.value
      ? a.type === "MANUAL_FINISH"
      : a.type === "MANUAL_DISABLE" || a.type === "QC_ABNORMAL_PAUSE",
  ),
);
const cards = computed(() => [
  {
    label: "广告消耗",
    icon: "coins",
    value: amount(metrics.value.cost, "¥"),
    note: "最新统计快照",
  },
  {
    label: "整体支付ROI",
    icon: "target",
    value: amount(metrics.value.roi),
    note: "成交金额 / 广告消耗",
  },
  {
    label: "成交订单",
    icon: "cart",
    value: metrics.value.orders == null ? "—" : String(metrics.value.orders),
    note: "平台成交口径",
  },
  {
    label: "成交金额",
    icon: "chart-up",
    value: amount(metrics.value.gmv, "¥"),
    note: "平台成交口径",
  },
]);
let timer: number | undefined;
async function refresh() {
  await load(true);
}
onMounted(() => {
  timer = window.setInterval(() => {
    if (
      document.visibilityState === "visible" &&
      !terminal.value &&
      !loading.value
    )
      void load();
  }, 60000);
});
onUnmounted(() => clearInterval(timer));
</script>
<style scoped>
.overview-empty {
  padding: 80px 24px;
  text-align: center;
  color: #7c7f99;
}
.overview-links {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-top: 24px;
  color: #575cf6;
  font-size: 14px;
}
.ovnotice {
  padding: 20px;
  border-radius: 12px;
  margin: 20px 0;
}
.ovnotice--danger {
  background: #fdeceb;
  color: #f54a45;
}
.ovsub,
.ovrow__v {
  overflow-wrap: anywhere;
}
.ovstate__ico svg {
  width: 26px;
  height: 26px;
}
.ovsec__title svg,
.btn-refresh svg {
  width: 16px;
  height: 16px;
}
.ovrow__v {
  text-align: right;
}
.ovlist { display: grid; gap: 12px; margin-top: 20px; }
.ovrow { display: flex; justify-content: space-between; align-items: baseline; gap: 24px; padding: 10px 0; border-bottom: 1px solid #eeeff4; font-size: 13px; }
.ovrow__k { color: #7c7f99; flex-shrink: 0; }
.ovrow__v { color: #4e5069; }
.st[data-st="running"] .rjcount, .st[data-st="done"] .rjcount, .st[data-st="passed"] .rjcount { color: #0ba05f; background: #eaf8ef; }
.st[data-st="paused"] .rjcount { color: #cc8b19; background: #fff5e5; }
.st[data-st="pending"] .rjcount, .st[data-st="reviewing"] .rjcount, .st[data-st="stopped"] .rjcount { color: #7c7f99; background: #f1f2f7; }
.pcard__pad {
  padding: 40px;
}
.ovgrid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
@media (max-width: 767px) {
  .pcard__pad {
    padding: 24px;
  }
  .ovtop,
  .ovstate {
    flex-wrap: wrap;
    gap: 20px;
  }
  .ovgrid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .ovstate__ops {
    flex-wrap: wrap;
  }
  .ovrow {
    gap: 16px;
  }
  .ad-stepper {
    overflow: auto;
    justify-content: flex-start;
  }
  .ovstate__l {
    min-width: 0;
  }
}
@media (max-width: 390px) {
  .ovgrid {
    grid-template-columns: 1fr;
  }
}
</style>
