<template>
  <div class="campaign-reference">
    <main class="ad-main mg-main">
      <div class="container-page">
        <router-link class="vback" to="/campaigns"
          ><ReferenceIcon name="arrow-left" />返回投放计划列表</router-link
        >
        <div v-if="loading && !plan" class="nodata">正在加载计划…</div>
        <div v-else-if="failed" class="nodata">
          <p class="nodata__t">计划数据加载失败</p>
          <button class="btn-prev" @click="load">重新加载</button>
        </div>
        <template v-else-if="plan"
          ><div class="vhead">
            <div class="vhead__l">
              <h1 class="vhead__t">
                {{ plan.name
                }}<span
                  class="stpill"
                  :class="`stpill--${statusInfo(plan.status)[1]}`"
                  >{{ statusInfo(plan.status)[0] }}</span
                >
              </h1>
              <p class="vhead__meta">
                <span
                  >计划ID：<b>{{ plan.qcPlanId || plan.planId }}</b></span
                ><span
                  >抖音号：<b>{{ shop }}</b></span
                ><span>投放平台：<b>抖音 · 巨量千川</b></span>
              </p>
            </div>
            <div class="vhead__ops">
              <button v-if="plan.qcPlanId" class="btn-prev" :disabled="busy" @click="syncCurrent"><ReferenceIcon name="refresh" />同步平台详情</button>
              <router-link
                class="btn-prev"
                :to="`/campaigns/manage/create?copy=${plan.planId}`"
                ><ReferenceIcon name="copy" />复制计划</router-link
              ><button
                v-if="canAct"
                class="btn-prev"
                :disabled="busy"
                @click="
                  ask(plan.status === 'DELIVERING' ? 'DISABLE' : 'ENABLE')
                "
              >
                <ReferenceIcon
                  :name="plan.status === 'DELIVERING' ? 'pause' : 'play'"
                />{{
                  plan.status === "DELIVERING" ? "暂停投放" : "恢复投放"
                }}</button
              ><button v-if="canAct" class="btn-next-primary" @click="operationOpen = true"><ReferenceIcon name="edit" />修改计划</button><router-link
                v-else-if="plan.source !== 'EXTERNAL' && ['DRAFT', 'REJECTED'].includes(plan.status)"
                class="btn-next-primary"
                :to="`/campaigns/${plan.planId}/edit`"
                ><ReferenceIcon name="edit" />修改计划</router-link
              >
            </div>
          </div>
          <section class="mg-panel">
            <div class="mg-panel__pad">
              <div
                class="flex flex-wrap items-center justify-between gap-x-4 gap-y-3"
              >
                <h2 class="mg-panel__t">投放数据概览</h2>
                <span class="pcard__hint"
                  >最新快照：{{
                    dateText(plan.snapshotTs || report?.snapshotTs)
                  }}</span
                >
              </div>
              <PlanKpis v-if="hasData" :metrics="metrics" />
              <div v-else class="nodata">
                <span class="nodata__ic"
                  ><ReferenceIcon name="hourglass"
                /></span>
                <p class="nodata__t">
                  {{
                    [
                      "DRAFT",
                      "SUBMITTING",
                      "PENDING_AUDIT",
                      "REJECTED",
                    ].includes(plan.status)
                      ? "计划尚未开始投放"
                      : "暂无投放数据快照"
                  }}
                </p>
                <p class="nodata__d">
                  计划通过平台审核并产生投放数据后，这里会展示消耗、成交与 ROI。
                </p>
              </div>
            </div>
          </section>
          <section class="mg-panel">
            <div class="mg-panel__pad">
              <h2 class="mg-panel__t">计划完整信息</h2>
              <div v-for="group in infoGroups" :key="group.title" class="vsec">
                <h3 class="vsec__t">
                  <ReferenceIcon :name="group.icon" />{{ group.title }}
                </h3>
                <div class="vgrid">
                  <div v-for="pair in group.rows" :key="pair[0]" class="vkv">
                    <span class="vkv__k">{{ pair[0] }}</span
                    ><span class="vkv__v">{{ pair[1] }}</span>
                  </div>
                </div>
              </div>
              <div id="goods" class="vsec">
                <h3 class="vsec__t">
                  <ReferenceIcon name="cart" />投放商品<span class="vsec__hint"
                    >共 {{ plan.products?.length || 0 }} 款</span
                  >
                </h3>
                <div class="vgoods">
                  <article
                    v-for="p in plan.products || []"
                    :key="p.id"
                    class="vgood"
                  >
                    <span class="vgood__thumb"
                      ><img
                        v-if="p.imageUrl"
                        :src="mediaUrl(p.imageUrl)"
                        :alt="p.name" /><ReferenceIcon
                        v-else
                        name="cart" /></span
                    ><span class="vgood__b"
                      ><span class="vgood__n">{{ p.name }}</span
                      ><span class="vgood__p"
                        >商品ID：{{ p.id }} ·
                        {{
                          hasData ? "暂无商品分项效果数据" : "待投放"
                        }}</span
                      ></span
                    >
                  </article>
                  <p v-if="!plan.products?.length" class="empty-note">
                    暂无绑定商品记录
                  </p>
                </div>
              </div>
              <div class="vsec">
                <h3 class="vsec__t"><ReferenceIcon name="video" />投放素材</h3>
                <p class="empty-note">
                  暂无此计划的素材明细，可前往素材中心查看已入库素材。
                </p>
                <router-link class="panel__toggle" to="/creatives"
                  >前往素材中心 <ReferenceIcon name="forward"
                /></router-link>
              </div>
            </div>
          </section>
          <section class="mg-panel">
            <div class="mg-panel__pad">
              <div
                class="flex flex-wrap items-center justify-between gap-x-4 gap-y-3"
              >
                <h2 class="mg-panel__t">计划状态流转</h2>
                <router-link
                  class="panel__toggle"
                  :to="`/campaigns/${plan.planId}/analysis`"
                  >查看投放效果数据分析 <ReferenceIcon name="forward"
                /></router-link>
              </div>
              <div class="vtl">
                <div v-if="plan.createdAt" class="vtl__item vtl__item--done">
                  <span class="vtl__rail"><span class="vtl__dot" /></span
                  ><span class="vtl__body"
                    ><span class="vtl__row"
                      ><span class="vtl__name">计划创建</span
                      ><span class="vtl__time">{{
                        dateText(plan.createdAt)
                      }}</span></span
                    ><span class="vtl__desc">{{ plan.name }}</span></span
                  >
                </div>
                <div
                  v-for="a in actions"
                  :key="a.id"
                  class="vtl__item vtl__item--done"
                >
                  <span class="vtl__rail"><span class="vtl__dot" /></span
                  ><span class="vtl__body"
                    ><span class="vtl__row"
                      ><span class="vtl__name">{{ actionName(a.type) }}</span
                      ><span class="vtl__time">{{
                        dateText(a.snapshotTs)
                      }}</span></span
                    ><span class="vtl__desc">{{ a.summary }}</span></span
                  >
                </div>
                <div class="vtl__item vtl__item--now">
                  <span class="vtl__rail"><span class="vtl__dot" /></span
                  ><span class="vtl__body"
                    ><span class="vtl__name"
                      >当前状态：{{ statusInfo(plan.status)[0] }}</span
                    ><span class="vtl__desc">{{
                      plan.rejectReason || "以平台同步状态为准"
                    }}</span></span
                  >
                </div>
              </div>
              <router-link
                class="panel__toggle"
                :to="`/campaigns/${plan.planId}/result`"
                >查看投放结果 <ReferenceIcon name="forward" /></router-link
              ><router-link
                class="panel__toggle"
                :to="`/campaigns/${plan.planId}/overview`"
                style="margin-left: 20px"
                >查看状态总览 <ReferenceIcon name="forward"
              /></router-link>
            </div></section
        ></template>
      </div>
    </main>
    <CampaignOperationDialog v-model="operationOpen" kind="修改计划" :plan="plan" @saved="load" /><ReferenceFooter /><ReferenceModal
      v-if="pendingAction"
      :title="pendingAction === 'DISABLE' ? '暂停计划提醒' : '恢复投放确认'"
      :width="460"
      :busy="busy"
      @close="pendingAction = null"
      ><p>
        {{
          pendingAction === "DISABLE"
            ? "暂停后计划将停止消耗，恢复后可继续投放。"
            : "确认恢复此计划的投放？"
        }}
      </p>
      <template #footer
        ><button
          class="btn-prev"
          :disabled="busy"
          @click="pendingAction = null"
        >
          取消</button
        ><button class="btn-next-primary" :disabled="busy" @click="act">
          {{ busy ? "提交中…" : "确定" }}
        </button></template
      ></ReferenceModal
    >
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from "vue";
import { actPlan, syncPlanDetail } from "@/features/delivery/deliveryApi";
import { GUARANTEE_STATUS } from '@/utils/labels';
import CampaignOperationDialog from './components/CampaignOperationDialog.vue';
import { useUserStore } from "@/stores/user";
import { ElMessage } from "element-plus";
import {
  usePlanPresentation,
  statusInfo,
  amount,
  dateText,
  mediaUrl,
} from "./reference/planPresentation";
import { ruleLabel } from "@/features/agentops/agentopsApi";
import ReferenceIcon from "./reference/ReferenceIcon.vue";
import ReferenceFooter from "./reference/ReferenceFooter.vue";
import ReferenceModal from "./reference/ReferenceModal.vue";
import PlanKpis from "./reference/PlanKpis.vue";
import "./reference/reference.css";
const { plan, shop, account, actions, report, metrics, hasData, loading, failed, load } =
  usePlanPresentation();
const user = useUserStore(),
  busy = ref(false),
  operationOpen = ref(false),
  pendingAction = ref<"ENABLE" | "DISABLE" | null>(null);
const canAct = computed(
  () =>
    !!plan.value?.qcPlanId && account.value?.status === 'ACTIVE' &&
    user.trialActive &&
    ["DELIVERING", "PAUSED"].includes(plan.value?.status || ""),
);
const infoGroups = computed(() => {
  const p = plan.value;
  if (!p) return [];
  return [
    {
      title: "基础信息",
      icon: "file",
      rows: [
        ["计划名称", p.name],
        ["计划ID", p.qcPlanId || p.planId],
        ["投放平台", "抖音 · 巨量千川"],
        [
          "投放方式",
          p.deliveryPlatform === "STANDARD" ? "标准投放" : "商品全域投放",
        ],
        ["抖音号", shop.value],
        ["抖音号ID", p.awemeId || "—"],
        ["创建时间", dateText(p.createdAt)],
        ["数据更新时间", dateText(p.snapshotTs)],
      ],
    },
    {
      title: "投放设置",
      icon: "sliders",
      rows: [
        ["日预算", amount(p.budget, "¥")],
        ["净成交ROI目标", amount(p.roi2Goal)],
        [
          "投放模式",
          p.smartBidType === "SMART_BID_CUSTOM" ? "控成本投放" : "放量投放",
        ],
        ["投放时段", "全时段"],
        [
          "成本保障",
          p.guarantee ? GUARANTEE_STATUS[p.guarantee] || p.guarantee : "—",
        ],
        ["当前状态", statusInfo(p.status)[0] || "—"],
      ],
    },
    {
      title: "投放时间",
      icon: "coins",
      rows: [
        ["投放周期", `${p.startDate || "—"} ~ ${p.endDate || "不限"}`],
        ["投放时段", "00:00 ~ 24:00"],
      ],
    },
  ];
});
async function syncCurrent() {
  if (!plan.value || busy.value) return;
  busy.value = true;
  try { await syncPlanDetail(plan.value.planId); await load(); ElMessage.success('平台详情已同步'); }
  catch { /* Keep the last successfully loaded details. */ }
  finally { busy.value = false; }
}
function ask(action: "ENABLE" | "DISABLE") {
  if (canAct.value) pendingAction.value = action;
}
function actionName(type: string) {
  return (
    (
      {
        MANUAL_ENABLE: "恢复投放",
        MANUAL_DISABLE: "暂停投放",
        MANUAL_FINISH: "结束计划",
      } as Record<string, string>
    )[type] || ruleLabel(type)
  );
}
async function act() {
  if (!plan.value || !pendingAction.value || busy.value) return;
  busy.value = true;
  try {
    await actPlan(plan.value.planId, pendingAction.value);
    pendingAction.value = null;
    await load();
    ElMessage.success("计划状态已更新");
  } catch {
    /* API displays failure; retain confirmation for retry. */
  } finally {
    busy.value = false;
  }
}
</script>
<style scoped>
.vhead__ops svg,
.panel__toggle svg {
  width: 16px;
  height: 16px;
}
.vhead__t,
.vkv__v {
  overflow-wrap: anywhere;
}
.vgood__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
}
.vgood__thumb > svg {
  width: 24px;
  height: 24px;
  color: #575cf6;
}
.empty-note {
  font-size: 13px;
  color: #7c7f99;
  padding: 16px 0;
}
.panel__toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
</style>
