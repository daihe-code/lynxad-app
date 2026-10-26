<template>
  <div class="campaign-result ad-step3 ad-step4" :class="{ 'campaign-result--submission': submissionPhase }">
    <CreationFrame :step="4" :account="frameAccount" :account-chips="frameAccountChips" :result-state="submissionPhase === 'FAILED' ? 'warning' : undefined" />
    <main class="ad-main">
      <div class="container-page">
        <div v-if="loading && !plan" class="result-loading">
          正在同步计划状态…
        </div>
        <div v-else-if="failed" class="result-loading">
          <p>计划状态加载失败</p>
          <button class="btn-refresh" @click="load">重新加载</button>
        </div>
        <PlanSubmissionCard v-else-if="plan && submissionPhase" :phase="submissionPhase" :summary="submissionSummary" :error="failedPublication?.publishError" :recovery="failedPublication?.publishRecovery" @recover="recoverFailedPlan" @back="router.push('/campaigns')" />
        <PlanAuditResult v-else-if="plan && underReview" :plan="plan" :aweme-name="shop" @updated="plan = $event" />
        <section v-else-if="plan" class="st" :data-st="tone">
          <div class="reshead">
            <span class="reshead__ico" :class="`ico-tone--${tone}`"
              ><ReferenceIcon :name="info[2] || 'doc'"
            /></span>
            <h2 class="reshead__title">
              {{ overview ? "投放计划状态总览" : title }}
            </h2>
            <p class="reshead__sub">{{ description }}</p>
            <span
              v-if="overview"
              class="stbadge"
              :class="tone === 'rejected' ? 'stbadge--bad' : 'stbadge--good'"
              >{{ info[0] }}</span
            >
            <p
              v-if="['pending', 'reviewing', 'passed'].includes(tone)"
              class="resnote"
            >
              <ReferenceIcon name="refresh" />以平台返回状态为准，页面每60秒读取后台状态；审核等待较久时后台每3分钟查询
            </p>
          </div>
          <details
            class="fold"
            :open="['pending', 'reviewing', 'passed'].includes(tone)"
          >
            <summary>
              <span class="fold__t">投放计划摘要</span
              ><span class="fold__r"
                ><span class="fold__meta">{{ plan.name }}</span
                ><ReferenceIcon class="fold__caret" name="caret"
              /></span>
            </summary>
            <div class="fold__body">
              <div class="sumgrid">
                <div v-for="row in summary" :key="row[0]" class="sumkv">
                  <span>{{ row[0] }}</span
                  ><b>{{ row[1] }}</b>
                </div>
              </div>
              <router-link class="result-link" :to="`/campaigns/${plan.planId}`"
                >查看计划完整信息</router-link
              >
            </div>
          </details>
          <div
            v-if="['pending', 'reviewing'].includes(tone)"
            class="panel pcard"
          >
            <div class="pcard__pad">
              <div class="pcard__head">
                <h3 class="pcard__title">
                  {{ tone === "pending" ? "提交进度" : "审批进度" }}
                </h3>
                <div class="pcard__tools">
                  <span class="pcard__hint">最近刷新 {{ refreshedAt }}</span
                  ><button
                    class="btn-refresh"
                    :disabled="loading"
                    @click="refresh"
                  >
                    <ReferenceIcon name="refresh" />刷新审核进度
                  </button>
                </div>
              </div>
              <div class="vtl">
                <div
                  v-for="(node, i) in reviewNodes"
                  :key="node.name"
                  class="vtl__item"
                  :class="`vtl__item--${node.state}`"
                >
                  <span class="vtl__rail"><span class="vtl__dot" /></span>
                  <div class="vtl__body">
                    <div class="vtl__row">
                      <span class="vtl__name">{{ node.name }}</span
                      ><span class="vtl__time">{{
                        i === 0
                          ? dateText(plan.createdAt)
                          : node.state === "now"
                            ? "进行中"
                            : "待平台同步"
                      }}</span>
                    </div>
                    <p class="vtl__desc">{{ node.description }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div v-if="tone === 'rejected'" class="panel pcard">
            <div class="pcard__pad">
              <h3 class="pcard__title">审核未通过原因</h3>
              <div class="rejection-box">
                <ReferenceIcon name="warn-tri" />
                <div>
                  <b>需处理平台审核意见</b>
                  <p>
                    {{
                      plan.rejectReason ||
                      "平台暂未返回详细拒审原因，请稍后刷新或在巨量千川查看。"
                    }}
                  </p>
                </div>
              </div>
              <p class="pcard__sub">
                请根据平台审核意见检查素材、商品信息和广告资质，修改后重新提交。修复状态以实际操作结果为准。
              </p>
              <div class="result-links">
                <router-link :to="`/campaigns/${plan.planId}/edit`"
                  >修改计划</router-link
                ><router-link to="/accounts?tab=oauth"
                  >前往广告授权管理</router-link
                >
              </div>
            </div>
          </div>
          <div v-if="tone === 'passed'" class="panel pcard">
            <div class="pcard__pad">
              <h3 class="pcard__title">距离投放开始还有</h3>
              <div class="countdown">
                <div>
                  <b>{{ remaining.hours }}</b
                  ><span>小时</span>
                </div>
                <span>:</span>
                <div>
                  <b>{{ remaining.minutes }}</b
                  ><span>分</span>
                </div>
              </div>
              <p class="pcard__sub">
                计划开始日期：{{
                  plan.startDate || "待平台同步"
                }}。倒计时结束后继续同步平台状态，实际投放由平台确认。
              </p>
            </div>
          </div>
          <div
            v-if="['running', 'paused', 'stopped', 'done'].includes(tone)"
            class="panel pcard"
          >
            <div class="pcard__pad pcard__pad--snug">
              <div class="pcard__head">
                <h3 class="pcard__title">
                  {{
                    tone === "running"
                      ? "核心投放数据"
                      : tone === "paused"
                        ? "暂停前的投放表现"
                        : "最终投放数据汇总"
                  }}
                </h3>
                <span class="pcard__hint"
                  >最新快照：{{
                    dateText(plan.snapshotTs || report?.snapshotTs)
                  }}</span
                >
              </div>
              <div
                v-if="hasData"
                :class="tone === 'done' ? 'm3grid' : 'm4grid'"
              >
                <div v-for="m in metricCards" :key="m.label" class="m4card">
                  <div class="m4card__k">
                    <ReferenceIcon :name="m.icon" />{{ m.label }}
                  </div>
                  <div class="m4card__v">{{ m.value }}</div>
                  <p class="m4card__f">{{ m.note }}</p>
                </div>
              </div>
              <p v-else class="pcard__sub">暂无此计划的投放数据快照。</p>
            </div>
          </div>
          <div v-if="tone === 'paused'" class="panel pcard">
            <div class="pcard__pad pcard__pad--snug">
              <h3 class="pcard__title">暂停原因</h3>
              <template v-if="systemPause"
                ><div class="autostop">
                  <b>平台异常暂停</b>
                  <p>{{ systemPause.summary }}</p>
                </div>
                <p class="pcard__sub">
                  请先在巨量千川检查账户、余额和审核状态，再决定是否恢复投放。
                </p></template
              ><template v-else>
                <p class="pcard__sub">{{ manualPause?.summary && manualPause.summary !== 'MANUAL_DISABLE' ? manualPause.summary : '暂无暂停原因记录。' }}</p></template
              >
            </div>
          </div>
          <div v-if="tone === 'stopped'" class="panel pcard">
            <div class="pcard__pad pcard__pad--snug">
              <h3 class="pcard__title">终止信息</h3>
              <div class="stop-note">计划已结束，历史投放数据仍可查看。</div>
              <div class="sumgrid">
                <div class="sumkv">
                  <span>结束时间</span
                  ><b>{{ dateText(finishAction?.snapshotTs) }}</b>
                </div>
                <div class="sumkv">
                  <span>结束记录</span
                  ><b>{{ finishAction?.summary || "暂无结束原因记录" }}</b>
                </div>
                <div class="sumkv">
                  <span>累计消耗</span><b>{{ amount(metrics.cost, "¥") }}</b>
                </div>
                <div class="sumkv">
                  <span>累计成交</span><b>{{ metrics.orders ?? "—" }} 单</b>
                </div>
              </div>
            </div>
          </div>
          <div v-if="tone === 'done'" class="panel pcard">
            <div class="pcard__pad pcard__pad--snug">
              <h3 class="pcard__title">投放期间的操作记录</h3>
              <div v-if="autoActions.length" class="actgrid">
                <div v-for="a in autoActions" :key="a.id" class="actgrid__i">
                  <span class="actgrid__k">{{ ruleLabel(a.type) }}</span>
                  <p class="actgrid__d">{{ a.summary }}</p>
                </div>
              </div>
              <p v-else class="pcard__sub">暂无已记录的操作。</p>
            </div>
          </div>
          <details class="fold" :open="overview">
            <summary>
              <span class="fold__t">状态流转记录</span
              ><span class="fold__r"
                ><span class="fold__meta">{{ actions.length }} 条操作记录</span
                ><ReferenceIcon class="fold__caret" name="caret"
              /></span>
            </summary>
            <div class="fold__body">
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
          </details>
          <div class="result-links">
            <router-link
              :to="
                overview
                  ? `/campaigns/${plan.planId}/result`
                  : `/campaigns/${plan.planId}/overview`
              "
              >{{ overview ? "查看投放结果" : "查看状态总览" }}</router-link
            ><router-link :to="`/campaigns/${plan.planId}/analysis`"
              >查看投放效果分析</router-link
            ><router-link to="/campaigns">查看所有投放计划</router-link>
          </div>
        </section>
      </div>
    </main>
    <div v-if="plan && !terminal && !submissionPhase && !underReview" class="actionbar">
      <div class="container-page actionbar__inner">
        <router-link class="btn-res" to="/campaigns">返回投放管理</router-link
        ><template v-if="canAct"
          ><button
            class="btn-prev"
            :disabled="busy"
            @click="
              pendingAction =
                plan.status === 'DELIVERING' ? 'DISABLE' : 'FINISH'
            "
          >
            {{ plan.status === "DELIVERING" ? "暂停投放" : "终止计划" }}</button
          ><button
            v-if="plan.status === 'PAUSED'"
            class="btn-res btn-res--primary"
            :disabled="busy"
            @click="pendingAction = 'ENABLE'"
          >
            恢复投放
          </button></template
        ><router-link
          v-if="tone === 'rejected'"
          class="btn-next-primary"
          :to="`/campaigns/${plan.planId}/edit`"
          >修改并重新提交</router-link
        ><button
          v-if="['pending', 'reviewing', 'passed'].includes(tone)"
          class="btn-next-primary"
          :disabled="loading"
          @click="refresh"
        >
          刷新状态
        </button>
      </div>
    </div>
    <CampaignPlanActionDialog :model-value="pendingAction === 'DISABLE'" :plan="plan || null" action="pause" :busy="busy" @update:model-value="value => { if (!value) pendingAction = null; }" @confirm="act" />
    <ReferenceModal
      v-if="pendingAction && pendingAction !== 'DISABLE'"
      :title="
        pendingAction === 'FINISH'
          ? '终止计划确认'
          : '恢复投放确认'
      "
      :width="460"
      :busy="busy"
      @close="pendingAction = null"
      ><p>
        {{
          pendingAction === "FINISH"
            ? "终止后不可恢复投放，历史数据保留。确认结束此计划？"
            : "请确认更新此计划的投放状态。"
        }}
      </p>
      <template #footer
        ><button
          class="btn-prev"
          :disabled="busy"
          @click="pendingAction = null"
        >
          取消</button
        ><button class="btn-next-primary" :disabled="busy" @click="act()">
          {{ busy ? "提交中…" : "确定" }}
        </button></template
      ></ReferenceModal
    >
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { actPlan } from "@/features/delivery/deliveryApi";
import { ruleLabel } from "@/features/agentops/agentopsApi";
import { useUserStore } from "@/stores/user";
import {
  usePlanPresentation,
  statusInfo,
  amount,
  dateText,
} from "./planPresentation";
import ReferenceIcon from "./ReferenceIcon.vue";
import ReferenceModal from "./ReferenceModal.vue";
import CreationFrame from "./CreationFrame.vue";
import PlanAuditResult from '../components/PlanAuditResult.vue';
import { isPlanUnderReview } from '@/features/delivery/planReview';
import PlanSubmissionCard from '../components/PlanSubmissionCard.vue';
import CampaignPlanActionDialog from '../components/CampaignPlanActionDialog.vue';
import { isPlanPauseReason, type PlanPauseReason } from '@/features/delivery/planPause';
import { planSubmissionPhase, planSubmissionSummary } from '@/features/delivery/planSubmissionPresentation';
import { awemeLabels } from '@/features/identity/awemeLabels';
import { creationGet, type CreationState } from '@/features/delivery/creationApi';
import { publishFailureLocation, type PublishFailureTarget } from '@/features/delivery/planPublishFailure';
import "./campaign-result.css";
const props = defineProps<{ planId?: string }>(),
  route = useRoute(), router = useRouter();
const { plan, account: boundAccount, shop, actions, report, metrics, hasData, loading, failed, load } =
  usePlanPresentation(() => props.planId || String(route.params.id || ""));
const user = useUserStore(),
  busy = ref(false),
  now = ref(Date.now()),
  refreshedAt = ref("—"),
  pendingAction = ref<"ENABLE" | "DISABLE" | "FINISH" | null>(null);
const underReview = computed(() => isPlanUnderReview(plan.value));
let timer: number | undefined;
const overview = computed(() => route.path.endsWith("/overview"));
const failedPublication = ref<CreationState>();
function recoverFailedPlan(choice: PublishFailureTarget) {
  if (!plan.value) return;
  const location = publishFailureLocation(plan.value.planId, failedPublication.value?.publishRecovery, failedPublication.value?.publishError, choice);
  if (location) void router.push(location);
}
watch(() => plan.value?.status === 'DRAFT' && !plan.value.qcPlanId ? plan.value.planId : undefined, async (id, _previous, onCleanup) => {
  failedPublication.value = undefined;
  if (!id) return;
  const controller = new AbortController();
  onCleanup(() => controller.abort());
  try {
    const creation = await creationGet(id, controller.signal);
    if (!controller.signal.aborted && creation.publishStatus === 'FAILED') failedPublication.value = creation;
  } catch { /* Historical drafts may have no creation snapshot. */ }
});
const submissionPhase = computed(() => planSubmissionPhase(failedPublication.value || plan.value || {}));
const submissionSummary = computed(() => planSubmissionSummary(plan.value || {}, shop.value));
const info = computed(() => statusInfo(plan.value?.status));
const tone = computed(() =>
  info.value[1] === "bad" ? "rejected" : info.value[1] || "pending",
);
const title = computed(
  () =>
    (
      ({
        pending:
          plan.value?.status === "DRAFT" ? "计划草稿已保存" : "投放计划已提交",
        reviewing: "审核中",
        rejected: "审核未通过",
        passed: "审核通过，等待投放",
        running: "投放中",
        paused: "已暂停",
        stopped: "计划已结束",
        done: "投放已完成",
      }) as Record<string, string>
    )[tone.value],
);
const description = computed(
  () =>
    (
      ({
        pending:
          plan.value?.status === "DRAFT"
            ? "素材与计划设置仍可调整，完成后提交平台审核"
            : "正在提交千川审核，请稍候",
        reviewing: "广告计划已提交至抖音平台，等待审核结果",
        rejected: "请按平台返回的审核意见检查并修改计划",
        passed: "广告计划已通过审核，将于平台确认的开始时间投放",
        running: "广告计划正在抖音商城、搜索、推荐等场景投放中，数据随平台同步更新",
        paused: "广告计划已暂停投放，暂停期间不会产生新增消耗",
        stopped: "广告计划已结束，历史投放数据可继续查看",
        done: "广告计划已完成，以下为本次投放的数据汇总",
      }) as Record<string, string>
    )[tone.value],
);
const terminal = computed(() =>
  ["FINISHED", "COMPLETED", "TERMINATED"].includes(plan.value?.status || ""),
);
const canAct = computed(
  () =>
    user.trialActive &&
    plan.value?.source !== "EXTERNAL" &&
    ["DELIVERING", "PAUSED"].includes(plan.value?.status || ""),
);
const frameAccount = computed(() =>
  plan.value
    ? { awemeName: shop.value, awemeId: plan.value.awemeId || "—", avatar: boundAccount.value?.awemes.find(aweme => aweme.awemeId === plan.value?.awemeId)?.avatar }
    : null,
);
const frameAccountChips = computed(() => awemeLabels(boundAccount.value?.awemes.find(aweme => aweme.awemeId === plan.value?.awemeId)));
const summary = computed(() => {
  const p = plan.value;
  return p
    ? [
        ["计划名称", p.name],
        ["计划编号", p.qcPlanId || p.planId],
        [
          "投放模式",
          p.smartBidType === "SMART_BID_CUSTOM" ? "控成本投放" : "放量投放",
        ],
        ["日预算", amount(p.budget, "¥")],
        ["净成交ROI目标", amount(p.roi2Goal)],
        ["投放商品", `${p.products?.length || 0} 款`],
        ["抖音号", shop.value],
        ["投放时间", `${p.startDate || "—"} ~ ${p.endDate || "不限"}`],
      ]
    : [];
});
const reviewNodes = computed(() => [
  {
    name: "素材与计划校验",
    state: "done",
    description: "按当前素材包与计划设置提交",
  },
  {
    name: "提交至巨量千川",
    state: tone.value === "pending" ? "now" : "done",
    description: "建立广告计划与商品投放关系",
  },
  {
    name: "平台审核",
    state: tone.value === "reviewing" ? "now" : "todo",
    description: "审核结果由平台返回",
  },
]);
const metricCards = computed(() => {
  const m = metrics.value;
  let cards = [
    {
      label: "广告消耗",
      icon: "coins",
      value: amount(m.cost, "¥"),
      note: "最新统计快照",
    },
    {
      label: "整体支付ROI",
      icon: "target",
      value: amount(m.roi),
      note: "成交金额 / 广告消耗",
    },
    {
      label: "曝光量",
      icon: "eye",
      value:
        m.impressions == null ? "—" : m.impressions.toLocaleString("zh-CN"),
      note: "最新统计快照",
    },
    {
      label: "成交订单",
      icon: "cart",
      value: m.orders == null ? "—" : String(m.orders),
      note: "平台成交口径",
    },
  ];
  if (tone.value === "done")
    cards.push(
      {
        label: "成交金额",
        icon: "chart-up",
        value: amount(m.gmv, "¥"),
        note: "平台成交口径",
      },
      {
        label: "表现最好素材",
        icon: "bulb",
        value: "—",
        note: "素材分项效果待接入",
      },
    );
  return cards;
});
const systemPause = computed(() =>
    actions.value.find((a) => a.type === "QC_ABNORMAL_PAUSE" && a.open),
  ),
  finishAction = computed(() =>
    actions.value.find((a) => a.type === "MANUAL_FINISH"),
  ),
  autoActions = computed(() => actions.value);
const manualPause = computed(() => [...actions.value].filter(a => a.type === 'MANUAL_DISABLE').sort((a, b) => b.snapshotTs.localeCompare(a.snapshotTs))[0]);
const remaining = computed(() => {
  const start = plan.value?.startDate
    ? new Date(plan.value.startDate + "T00:00:00").getTime()
    : NaN;
  const minutes = Number.isFinite(start)
    ? Math.max(0, Math.ceil((start - now.value) / 60000))
    : null;
  return {
    hours: minutes == null ? "—" : Math.floor(minutes / 60),
    minutes: minutes == null ? "—" : minutes % 60,
  };
});
async function refresh() {
  await load(true);
  refreshedAt.value = new Date().toLocaleTimeString("zh-CN", { hour12: false });
  now.value = Date.now();
}
async function act(pauseReason?: PlanPauseReason) {
  if (!plan.value || !pendingAction.value || busy.value) return;
  if (pendingAction.value === 'DISABLE' && !isPlanPauseReason(pauseReason)) return;
  busy.value = true;
  try {
    await actPlan(plan.value.planId, pendingAction.value, pauseReason);
    pendingAction.value = null;
    await refresh();
    ElMessage.success("计划状态已更新");
  } catch {
    /* Retain the confirmation on failure. */
  } finally {
    busy.value = false;
  }
}
onMounted(() => {
  let lastReadAt = Date.now();
  timer = window.setInterval(() => {
    now.value = Date.now();
    if (
      document.visibilityState === "visible" &&
      !underReview.value &&
      ["pending", "reviewing", "passed", "running"].includes(tone.value) &&
      !loading.value && now.value - lastReadAt >= (plan.value?.status === 'SUBMITTING' ? 2500 : 60000)
    ) {
      lastReadAt = now.value;
      void load();
    }
  }, 2500);
});
onUnmounted(() => clearInterval(timer));
</script>
<style scoped>
.campaign-result--submission .ad-main { padding: 24px 0 32px; }
.result-loading {
  padding: 80px 24px;
  text-align: center;
  color: #7c7f99;
}
.sumgrid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px 32px;
}
.sumkv {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}
.sumkv span {
  color: #7c7f99;
}
.sumkv b {
  font-weight: 500;
  overflow-wrap: anywhere;
}
.result-links {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  margin: 24px 0;
  color: #575cf6;
  font-size: 14px;
}
.result-link {
  display: inline-block;
  margin-top: 24px;
  color: #575cf6;
  font-size: 13px;
}
.rejection-box {
  display: flex;
  gap: 16px;
  background: #fdeceb;
  border-radius: 12px;
  padding: 20px;
  color: #f54a45;
  margin: 20px 0;
}
.rejection-box > svg {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
}
.rejection-box p {
  margin-top: 8px;
  overflow-wrap: anywhere;
}
.countdown {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32px;
  margin: 28px 0;
  color: #575cf6;
}
.countdown > div {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.countdown b {
  font-size: 44px;
}
.stop-note {
  padding: 16px;
  background: #f1f2f7;
  border-radius: 10px;
  margin: 20px 0;
  color: #7c7f99;
}
.fold__meta {
  max-width: 50vw;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.btn-refresh svg,
.resnote svg {
  width: 16px;
  height: 16px;
}
.rchips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}
.rchip {
  padding: 8px 12px;
  background: #fff;
  border: 1px solid #e6e8f2;
  border-radius: 999px;
  color: #7c7f99;
  font-size: 13px;
}
.rchip[aria-checked="true"] {
  color: #575cf6;
  border-color: #575cf6;
  background: #f7f7ff;
}
.actionbar__inner {
  justify-content: flex-end;
  gap: 16px;
}
@media (max-width: 767px) {
  .sumgrid {
    grid-template-columns: 1fr;
  }
  .m4grid,
  .m3grid,
  .actgrid {
    grid-template-columns: 1fr 1fr;
  }
  .actionbar__inner {
    flex-wrap: wrap;
  }
  .pcard__head {
    flex-wrap: wrap;
    gap: 16px;
  }
  .reshead__sub {
    padding: 0 12px;
  }
  .reshead__title {
    font-size: 26px;
  }
}
@media (max-width: 390px) {
  .m4grid,
  .m3grid {
    grid-template-columns: 1fr;
  }
  .countdown {
    gap: 16px;
  }
}
</style>
