<template>
  <div class="campaign-reference">
    <main class="ad-main mg-main">
      <div class="container-page">
        <router-link class="vback" to="/campaigns"
          ><ReferenceIcon name="arrow-left" />返回投放计划列表</router-link
        >
        <div v-if="loading && !plan" class="nodata">正在加载投放数据…</div>
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
                ><span
                  >投放周期：<b
                    >{{ plan.startDate || "—" }} ~
                    {{ plan.endDate || "不限" }}</b
                  ></span
                >
              </p>
            </div>
            <div class="vhead__ops">
              <button
                class="btn-prev"
                :disabled="!hasData"
                @click="exportReport"
              >
                <ReferenceIcon name="download" />导出分析报表</button
              ><router-link
                class="btn-next-primary"
                :to="`/campaigns/${plan.planId}`"
                ><ReferenceIcon name="forward" />查看计划完整信息</router-link
              >
            </div>
          </div>
          <section v-if="!hasData" class="mg-panel">
            <div class="mg-panel__pad">
              <div class="nodata">
                <span class="nodata__ic"
                  ><ReferenceIcon name="chart-up"
                /></span>
                <p class="nodata__t">{{ noDataTitle }}</p>
                <p class="nodata__d">
                  平台审核通过并产生投放快照后，这里会展示消耗、成交、转化与素材表现数据。
                </p>
                <router-link
                  class="btn-prev"
                  :to="`/campaigns/${plan.planId}`"
                  style="margin-top: 16px"
                  >查看计划完整信息</router-link
                >
              </div>
            </div>
          </section>
          <template v-else
            ><section class="mg-panel">
              <div class="mg-panel__pad">
                <div class="anbar">
                  <h2 class="mg-panel__t">核心指标</h2>
                  <div class="anrange" role="group" aria-label="统计区间">
                    <button
                      v-for="r in ranges"
                      :key="r[0]"
                      class="anrange__i"
                      :aria-pressed="range === r[0]"
                      @click="pickRange(r[0]!)"
                    >
                      {{ r[1] }}
                    </button>
                  </div>
                  <button
                    class="btn-refresh ml-auto"
                    :disabled="loading"
                    @click="load"
                  >
                    <ReferenceIcon name="refresh" />刷新
                  </button>
                </div>
                <p class="pcard__hint mt-[10px]">
                  最新快照：{{
                    dateText(plan.snapshotTs || report?.snapshotTs)
                  }}
                  · 当前展示最新同步数据，暂无历史区间汇总
                </p>
                <PlanKpis :metrics="metrics" extended />
              </div>
            </section>
            <section class="mg-panel">
              <div class="mg-panel__pad">
                <div class="anbar">
                  <h2 class="mg-panel__t">消耗与成交趋势</h2>
                  <span class="pcard__hint">{{
                    ranges.find((r) => r[0] === range)?.[1]
                  }}</span>
                </div>
                <div class="chart">
                  <div class="chart__hd">
                    <span class="chart__t">消耗 / 成交金额（元）</span
                    ><span class="chart__lg"
                      ><span><i style="background: #575cf6" />消耗（元）</span
                      ><span
                        ><i style="background: #00b365" />成交金额（元）</span
                      ></span
                    >
                  </div>
                  <div class="trend-empty">
                    <ReferenceIcon name="chart-up" /><span
                      >暂无所选区间的历史趋势数据</span
                    ><small
                      >最新快照不代表每日数据，取得历史报表后将展示趋势</small
                    >
                  </div>
                </div>
              </div>
            </section>
            <section class="mg-panel">
              <div class="mg-panel__pad">
                <h2 class="mg-panel__t">转化漏斗</h2>
                <div class="funnel">
                  <div
                    v-for="(f, i) in funnel"
                    :key="f.label"
                    class="funnel__i"
                  >
                    <div
                      class="funnel__bar"
                      :style="{
                        width: `${100 - i * 20}%`,
                        background: ['#575cf6', '#7d81f8', '#a2a5fa'][i],
                      }"
                    >
                      <span>{{ f.label }}</span
                      ><b>{{
                        f.value == null ? "—" : f.value.toLocaleString("zh-CN")
                      }}</b>
                    </div>
                  </div>
                </div>
                <p class="pcard__hint">
                  曝光 → 点击 → 成交 ·
                  按平台已有指标展示，下单与秒退数据尚未提供
                </p>
              </div>
            </section>
            <section class="mg-panel">
              <div class="mg-panel__pad">
                <div class="anbar">
                  <h2 class="mg-panel__t">素材与商品表现</h2>
                  <div class="anrange">
                    <button
                      class="anrange__i"
                      :aria-pressed="rank === 'mats'"
                      @click="pickRank('mats')"
                    >
                      素材榜</button
                    ><button
                      class="anrange__i"
                      :aria-pressed="rank === 'goods'"
                      @click="pickRank('goods')"
                    >
                      商品榜
                    </button>
                  </div>
                </div>
                <div class="rtbl">
                  <table class="rtbl__t">
                    <thead>
                      <tr>
                        <th>排名</th>
                        <th>{{ rank === "goods" ? "商品" : "素材" }}</th>
                        <th>消耗</th>
                        <th>成交金额</th>
                        <th>支付ROI</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td colspan="5" class="table-empty">
                          暂无{{
                            rank === "goods" ? "商品" : "素材"
                          }}维度的效果报表
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <p class="pcard__hint mt-[12px]">
                  取得分项报表后按实际表现排名，分项数据与计划合计对账。
                </p>
              </div>
            </section>
            <section class="mg-panel">
              <div class="mg-panel__pad">
                <h2 class="mg-panel__t">灵投优化动作记录</h2>
                <div v-if="actions.length" class="vtl">
                  <div
                    v-for="a in actions"
                    :key="a.id"
                    class="vtl__item vtl__item--done"
                  >
                    <span class="vtl__rail"><span class="vtl__dot" /></span
                    ><span class="vtl__body"
                      ><span class="vtl__row"
                        ><span class="vtl__name">{{ ruleLabel(a.type) }}</span
                        ><span class="vtl__time">{{
                          dateText(a.snapshotTs)
                        }}</span></span
                      ><span class="vtl__desc">{{ a.summary }}</span></span
                    >
                  </div>
                </div>
                <p v-else class="table-empty">暂无已记录的优化或人工操作</p>
              </div>
            </section></template
          ></template
        >
      </div>
    </main>
    <ReferenceFooter />
  </div>
</template>
<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  usePlanPresentation,
  statusInfo,
  dateText,
  amount,
} from "./planPresentation";
import { ruleLabel } from "@/features/agentops/agentopsApi";
import { csvDownload } from "@/pages/accounts/accountFormat";
import ReferenceIcon from "./ReferenceIcon.vue";
import ReferenceFooter from "./ReferenceFooter.vue";
import PlanKpis from "./PlanKpis.vue";
import "./reference.css";
const route = useRoute(),
  router = useRouter();
const props = defineProps<{ planId?: string }>();
const { plan, shop, actions, report, metrics, hasData, loading, failed, load } =
  usePlanPresentation(() => props.planId || String(route.params.id || ""));
const ranges = [
  ["today", "当日"],
  ["7d", "近 7 日"],
  ["30d", "近 30 日"],
];
const range = computed(() =>
  ["today", "7d", "30d"].includes(String(route.query.range))
    ? String(route.query.range)
    : "7d",
);
const rank = computed(() => (route.query.rank === "goods" ? "goods" : "mats"));
const noDataTitle = computed(() =>
  ["DRAFT", "SUBMITTING", "PENDING_AUDIT", "REJECTED", "SCHEDULED"].includes(
    plan.value?.status || "",
  )
    ? "计划尚未开始投放"
    : "暂无投放数据快照",
);
const funnel = computed(() => [
  { label: "曝光", value: metrics.value.impressions },
  { label: "点击", value: metrics.value.clicks },
  { label: "成交", value: metrics.value.orders },
]);
function pickRange(value: string) {
  void router.replace({ query: { ...route.query, range: value } });
}
function pickRank(value: string) {
  void router.replace({ query: { ...route.query, rank: value } });
}
function exportReport() {
  if (!hasData.value || !plan.value) return;
  const m = metrics.value;
  csvDownload("投放效果分析.csv", [
    [
      "计划名称",
      "计划ID",
      "统计快照",
      "消耗（元）",
      "成交订单",
      "成交金额（元）",
      "支付ROI",
      "曝光",
      "点击",
    ],
    [
      plan.value.name,
      plan.value.planId,
      dateText(plan.value.snapshotTs || report.value?.snapshotTs),
      amount(m.cost),
      m.orders,
      amount(m.gmv),
      amount(m.roi),
      m.impressions,
      m.clicks,
    ],
  ]);
}
</script>
<style scoped>
.trend-empty {
  height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  color: #7c7f99;
}
.trend-empty svg {
  width: 40px;
  height: 40px;
  color: #b6b8ce;
}
.trend-empty small {
  font-size: 12px;
  text-align: center;
}
.table-empty {
  padding: 36px;
  text-align: center;
  color: #7c7f99;
}
.funnel__i {
  display: flex;
  justify-content: center;
  min-width: 0;
}
.funnel__bar {
  min-height: 58px;
  margin: 6px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-radius: 8px;
  color: #fff;
}
.vhead__ops svg,
.btn-refresh svg {
  width: 16px;
  height: 16px;
}
.vhead__t {
  overflow-wrap: anywhere;
}
@media (max-width: 639px) {
  .funnel {
    display: block;
  }
  .funnel__bar {
    max-width: 100%;
  }
  .pcard__hint {
    overflow-wrap: anywhere;
  }
}
</style>
