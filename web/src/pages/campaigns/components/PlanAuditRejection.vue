<template>
  <details :key="plan.planId" class="audit-rejection" open aria-label="审核未通过原因">
    <summary>
      <h3>审核未通过原因</h3>
      <span class="audit-rejection__brief">{{ loading ? '正在读取审核意见…' : `${issues.length} 项需核对处理` }}</span>
      <ReferenceIcon class="audit-rejection__caret" name="caret" />
    </summary>
    <div class="audit-rejection__body">
      <p class="audit-rejection__intro">根据平台审核意见逐项处理，处理后请确认本计划的最新审核结果。</p>
      <ol class="rejection-issues">
        <li v-for="(issue, index) in issues" :key="issue.key" :class="{ 'rejection-issues__item--unknown': issue.category === 'PLATFORM' }">
          <span class="rejection-issues__number">{{ index + 1 }}</span>
          <div class="rejection-issues__content">
            <header><h4>{{ issue.title }}</h4><span class="rejection-issues__badge"><ReferenceIcon name="warn-fill" />需你处理 · {{ issue.categoryLabel }}</span></header>
            <p v-for="(reason, i) in issue.reasons" :key="reason"><strong v-if="i === 0">未通过点：</strong>{{ reason }}</p>
            <p v-if="issue.materialId" class="rejection-issues__id">素材 ID：{{ issue.materialId }}</p>
            <div class="rejection-issues__action"><strong><ReferenceIcon name="forward" />处理方式</strong><span>{{ issue.guidance }}</span></div>
            <router-link v-if="issue.category === 'MATERIAL' && plan.qcPlanId" :to="{ path: '/campaigns', query: { plan: plan.planId, tab: 'materials' } }">查看本计划素材 <ReferenceIcon name="forward" /></router-link>
            <router-link v-if="issue.category === 'AUTHORIZATION'" to="/accounts?tab=oauth">查看广告账户授权 <ReferenceIcon name="forward" /></router-link>
          </div>
        </li>
      </ol>
      <p v-if="loadError" class="audit-rejection__load-note" role="status">{{ loadError }} <button type="button" :disabled="loading" @click="loadMaterials">重新读取</button></p>
      <section v-if="hasQualifications" class="qualification-guide" aria-label="资质问题处理指引">
        <h4><ReferenceIcon name="check" />资质问题处理指引</h4>
        <ul><li>根据上方审核意见，确认需要补充资质的商品及资质类型。</li><li>在巨量千川资质管理中补齐对应资料，核对有效期及主体信息。</li><li>以平台返回的审核结果为准，完成处理后核对计划状态，再提交审核。</li></ul>
        <a href="https://qianchuan.jinritemai.com/" target="_blank" rel="noopener noreferrer">前往千川处理资质 <ReferenceIcon name="forward" /></a>
      </section>
      <p class="audit-rejection__prevention"><ReferenceIcon name="info" /><span><strong>提交前核对：</strong>检查素材文案、画面、商品信息及相关资质，确认已按本次审核意见完成修改。</span></p>
    </div>
  </details>
</template>

<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue';
import { auditRejectionIssues, type AuditMaterialReason } from '@/features/delivery/planAuditRejection';
import { insightMaterials } from '@/features/delivery/planInsightsApi';
import { recentRange } from '@/features/delivery/statisticsRange';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
const props = defineProps<{ plan: { planId: string; qcPlanId?: string; rejectReason?: string | null } }>();
const materials = ref<AuditMaterialReason[]>([]), loading = ref(false), loadError = ref('');
const issues = computed(() => auditRejectionIssues(props.plan, materials.value));
const hasQualifications = computed(() => issues.value.some(issue => issue.category === 'QUALIFICATION'));
let serial = 0, controller: AbortController | undefined;
const byType = new Map<string, AuditMaterialReason[]>();
const materialTypes = ['VIDEO', 'IMAGE', 'CAROUSEL', 'TITLE', 'OTHER'];
async function loadMaterials() {
  controller?.abort(); controller = new AbortController();
  const request = ++serial, signal = controller.signal;
  if (!props.plan.qcPlanId) { materials.value = []; loading.value = false; return; }
  loading.value = true; loadError.value = '';
  const range = recentRange(7);
  const results = await Promise.allSettled(materialTypes.map(type => insightMaterials(props.plan.planId, range, type, signal)));
  if (signal.aborted || serial !== request) return;
  results.forEach((result, index) => {
    if (result.status !== 'fulfilled' || !Array.isArray(result.value.records)) return;
    const type = materialTypes[index]!;
    const rows = result.value.warning ? [...(byType.get(type) || []), ...result.value.records] : result.value.records;
    byType.set(type, [...new Map(rows.map(row => [`${row.type}:${row.id}`, row])).values()]);
  });
  materials.value = [...byType.values()].flat();
  if (results.some(result => result.status === 'rejected' || !Array.isArray(result.value.records) || !!result.value.warning)) loadError.value = '部分素材审核详情暂未取得，当前保留已读取的审核意见。';
  loading.value = false;
}
watch(() => props.plan.planId, () => { byType.clear(); materials.value = []; loadError.value = ''; void loadMaterials(); }, { immediate: true });
onUnmounted(() => { serial++; controller?.abort(); });
</script>

<style scoped>
.audit-rejection { border: 1px solid #e9eaf4; border-radius: 22px; background: #fff; box-shadow: 0 4px 16px #262c6b05; }
.audit-rejection summary { display: flex; align-items: center; gap: 14px; min-height: 68px; box-sizing: border-box; padding: 20px 32px; cursor: pointer; list-style: none; }
.audit-rejection summary::-webkit-details-marker { display: none; }
.audit-rejection summary:focus-visible { outline: 2px solid #575cf6; outline-offset: 3px; border-radius: 22px; }
.audit-rejection h3 { margin: 0; padding-left: 13px; border-left: 4px solid #5b55ff; font-size: 17px; font-weight: 650; line-height: 22px; }
.audit-rejection:not([open]) h3 { border-color: #c9c9d6; }
.audit-rejection__brief { margin-left: auto; text-align: right; color: #81849f; font-size: 12px; line-height: 1.7; }
.audit-rejection__caret { width: 17px; height: 17px; flex: none; color: #81849f; }
.audit-rejection[open] .audit-rejection__caret { transform: rotate(180deg); }
.audit-rejection__body { padding: 0 32px 32px; }
.audit-rejection__intro { margin: 0 0 18px; color: #81849f; font-size: 12px; line-height: 1.8; }
.rejection-issues { margin: 0; padding: 0; list-style: none; display: grid; gap: 14px; }
.rejection-issues li { display: flex; gap: 12px; padding: 20px; border: 1px solid #f8dfe1; border-radius: 15px; background: #fffafa; }
.rejection-issues li.rejection-issues__item--unknown { border-color: #e6e5f5; background: #fafaff; }
.rejection-issues__number { flex: none; width: 23px; height: 23px; display: grid; place-items: center; background: #ffebeb; color: #d24c50; border-radius: 50%; font-size: 12px; }
.rejection-issues__content { min-width: 0; flex: 1; }
.rejection-issues header { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; flex-wrap: wrap; }
.rejection-issues h4 { margin: 0; font-size: 14px; line-height: 24px; font-weight: 650; overflow-wrap: anywhere; }
.rejection-issues__badge { display: inline-flex; align-items: center; gap: 4px; padding: 3px 9px; background: #ffeded; color: #d24c50; border-radius: 20px; font-size: 11px; white-space: nowrap; }
.rejection-issues__badge svg { width: 12px; height: 12px; }
.rejection-issues p { margin: 8px 0 0; color: #5c617b; font-size: 13px; line-height: 1.9; white-space: pre-wrap; overflow-wrap: anywhere; }
.rejection-issues p strong { color: #35384d; font-weight: 600; }
.rejection-issues p.rejection-issues__id { color: #81849f; font-size: 12px; }
.rejection-issues__action { display: flex; align-items: flex-start; gap: 8px; margin-top: 12px; padding: 12px 14px; border-radius: 10px; background: #fff2f3; font-size: 13px; line-height: 1.9; color: #5c617b; }
.rejection-issues__action strong { display: inline-flex; align-items: center; gap: 4px; flex: none; color: #c84a50; font-weight: 600; }
.rejection-issues__action svg { width: 13px; height: 13px; }
.rejection-issues a { display: inline-flex; align-items: center; gap: 5px; margin-top: 12px; color: #5755ee; font-size: 12px; text-decoration: none; }
.rejection-issues a svg,.qualification-guide a svg { width: 14px; height: 14px; }
.qualification-guide { margin-top: 18px; padding: 18px 20px; border: 1px solid #d9d6ff; border-radius: 15px; background: #f8f7ff; }
.qualification-guide h4 { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 14px; font-weight: 600; }
.qualification-guide h4 svg { width: 16px; height: 16px; color: #5b55ff; }
.qualification-guide ul { margin: 12px 0 14px; padding-left: 20px; color: #5c617b; font-size: 13px; line-height: 2; }
.qualification-guide li::marker { color: #655bff; }
.qualification-guide a { display: inline-flex; align-items: center; gap: 7px; padding: 8px 12px; border: 1px solid #e5e4f4; border-radius: 8px; background: #fff; color: #53566f; font-size: 12px; text-decoration: none; }
.audit-rejection__prevention { display: flex; align-items: flex-start; gap: 9px; margin: 16px 0 0; padding: 14px 18px; border-radius: 12px; background: #f4f3ff; color: #656a85; font-size: 12px; line-height: 1.9; }
.audit-rejection__prevention svg { width: 16px; height: 16px; flex: none; margin-top: 4px; color: #ba8a36; }
.audit-rejection__load-note { color: #81849f; font-size: 12px; line-height: 1.8; margin: 14px 0 0; }
.audit-rejection__load-note button { padding: 0; border: 0; background: transparent; color: #5755ee; font: inherit; cursor: pointer; }
.audit-rejection a:focus-visible,.audit-rejection button:focus-visible { outline: 2px solid #575cf6; outline-offset: 3px; }
@media (max-width: 767px) {
  .audit-rejection summary { padding: 18px; gap: 10px; flex-wrap: wrap; }
  .audit-rejection h3 { flex: 1; }
  .audit-rejection__brief { order: 3; width: 100%; text-align: left; margin-left: 17px; }
  .audit-rejection__body { padding: 0 18px 24px; }
  .rejection-issues li { padding: 15px 12px; gap: 9px; }
  .rejection-issues__action { flex-direction: column; gap: 3px; padding: 10px; }
  .qualification-guide { padding: 16px; }
}
</style>
