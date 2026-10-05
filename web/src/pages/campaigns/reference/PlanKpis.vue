<template>
  <div class="kpi-grid" :class="{ 'kpi-grid--3': extended }">
    <article
      v-for="(k, i) in entries"
      :key="k.label"
      class="kpi"
      :class="tones[i % 4]"
    >
      <span class="kpi__k">{{ k.label }}</span
      ><span class="kpi__v"
        >{{ k.value }}<span class="kpi__unit">{{ k.unit }}</span></span
      ><span class="kpi__n">{{ k.note }}</span>
    </article>
  </div>
</template>
<script setup lang="ts">
import { computed } from "vue";
import { amount } from "./planPresentation";
const props = defineProps<{
  metrics: {
    cost?: number | null;
    gmv?: number | null;
    roi?: number | null;
    orders?: number | null;
    impressions?: number | null;
    clicks?: number | null;
  };
  extended?: boolean;
}>();
const tones = ["kpi--spend", "kpi--orders", "kpi--gmv", "kpi--roi"];
const entries = computed(() => {
  const m = props.metrics;
  const rows = [
    {
      label: "消耗",
      value: amount(m.cost, "¥"),
      unit: "元",
      note: "最新统计快照",
    },
    {
      label: "成交订单",
      value: m.orders == null ? "—" : String(m.orders),
      unit: "单",
      note: "平台成交口径",
    },
    {
      label: "成交金额",
      value: amount(m.gmv, "¥"),
      unit: "元",
      note: "平台成交口径",
    },
    {
      label: "整体支付ROI",
      value: amount(m.roi),
      unit: "",
      note: "成交金额 / 广告消耗",
    },
  ];
  if (props.extended)
    rows.push(
      {
        label: "客单价",
        value: amount(m.orders && m.gmv != null ? m.gmv / m.orders : null, "¥"),
        unit: "元",
        note: "成交金额 / 成交订单",
      },
      {
        label: "曝光量",
        value:
          m.impressions == null ? "—" : m.impressions.toLocaleString("zh-CN"),
        unit: "次",
        note: "最新统计快照",
      },
    );
  return rows;
});
</script>
