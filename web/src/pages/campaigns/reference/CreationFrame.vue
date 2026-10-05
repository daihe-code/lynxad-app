<template>
  <section class="ad-banner" aria-label="投放计划概览">
    <div class="container-page ad-banner__inner">
      <div class="mt-[4px] min-w-0">
        <div class="ad-banner__head">
          <h1 class="ad-banner__title">灵投广告投放</h1>
          <span class="ad-banner__tag"
            ><ReferenceIcon name="mountain" />抖音巨量千川 · 商品全域投放</span
          >
        </div>
        <p class="ad-banner__sub">
          一次设置，广告自动出现在抖音商城、搜索、推荐等多个流量场景
        </p>
        <ul class="ad-banner__chips">
          <li v-for="text in chips" :key="text" class="ad-chip">
            <ReferenceIcon name="check-circle" />{{ text }}
          </li>
        </ul>
      </div>
      <div v-if="account" class="ad-account">
        <div class="ad-account__card">
          <span class="ad-account__name">{{ account.awemeName }}</span
          ><span class="ad-account__id">抖音号ID：{{ account.awemeId }}</span
          ><span class="ad-account__btns"
            ><span
              v-for="(chip, i) in accountChips || []"
              :key="chip"
              :class="i ? 'ad-account__btn-warn' : 'ad-account__btn-solid'"
              >{{ chip }}</span
            ></span
          >
        </div>
        <p class="ad-account__hint">当前投放抖音号</p>
      </div>
    </div>
  </section>
  <nav class="ad-stepper" aria-label="投放计划进度">
    <template v-for="(label, i) in steps" :key="label"
      ><span
        class="step"
        :class="{ 'step--done': i + 1 < step }"
        :aria-current="i + 1 === step ? 'step' : undefined"
        ><span class="step__dot"
          ><ReferenceIcon v-if="i === 3" name="check" /><template v-else>{{
            i + 1
          }}</template></span
        ><span class="step__label">{{ label }}</span></span
      ><span v-if="i < 3" class="step__line"
    /></template>
  </nav>
</template>
<script setup lang="ts">
import ReferenceIcon from "./ReferenceIcon.vue";
defineProps<{
  step: number;
  account?: { awemeName: string; awemeId: string } | null;
  accountChips?: string[];
}>();
const steps = ["选择商品", "设置投放目标", "定素材并投放", "查看投放结果"],
  chips = ["商城+搜索等全域投放", "智能生成素材", "智能出价", "实时监控+优化"];
</script>
<style scoped>
.ad-banner__tag svg {
  width: 13px;
  height: 13px;
}
.ad-chip svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}
.step__dot svg {
  width: 16px;
  height: 16px;
}
.ad-account__name,
.ad-account__id {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ad-account__btns { min-height: 22px; }
@media (max-width: 639px) {
  .ad-banner__inner {
    flex-direction: column;
    gap: 20px;
  }
  .ad-banner__head {
    flex-wrap: wrap;
  }
  .ad-account {
    width: 100%;
    display: block;
  }
  .ad-account__card {
    width: 100%;
  }
}
</style>
