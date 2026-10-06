<template>
  <CampaignToolsDialog v-if="isTool" v-model="open" :kind="kind" :plan="plan" />
  <el-dialog v-else v-model="open" :title="title" :width="kind === '修改计划' ? '560px' : '460px'" class="campaign-dialog" modal-class="campaign-overlay" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" align-center destroy-on-close>
    <div v-if="plan" class="operation-plan">
      <span class="operation-plan-icon"><el-icon><DataLine /></el-icon></span>
      <div><strong :title="plan.name">{{ plan.name }}</strong><span>ID：{{ plan.qcPlanId || plan.planId }}</span></div>
      <span class="operation-plan-status">{{ PLAN_STATUS[plan.status] || plan.status }}</span>
    </div>
    <div class="operation-notice"><el-icon><InfoFilled /></el-icon><span>保存后更新当前计划。投放方式和素材保持原设置。</span></div>

    <el-form ref="formRef" :model="form" :rules="rules" :validate-on-rule-change="false" label-position="top" class="operation-form" @submit.prevent>
      <template v-if="kind === '修改计划'">
        <el-form-item label="计划名称" prop="name"><el-input v-model="form.name" maxlength="30" show-word-limit placeholder="请输入自定义计划名称" aria-label="计划名称" /><p class="field-hint">可自定义 1–30 字的名称，用于区分不同投放计划。</p></el-form-item>
      </template>
      <template v-if="kind === '切换投放方式'">
        <el-form-item label="投放方式" prop="bidType">
          <el-radio-group v-model="form.bidType" class="bid-options" aria-label="投放方式">
            <el-radio value="SMART_BID_CONSERVATIVE" border><strong>放量投放</strong><span>优先探索流量，扩大投放规模</span></el-radio>
            <el-radio value="SMART_BID_CUSTOM" border><strong>控成本投放</strong><span>根据 ROI 目标控制投放成本</span></el-radio>
          </el-radio-group>
        </el-form-item>
      </template>
      <template v-if="kind === '调整预算' || kind === '修改计划'">
        <el-form-item label="日预算" prop="budget"><el-input v-model="form.budget" inputmode="decimal" placeholder="请输入日预算" aria-label="日预算金额（元）"><template #suffix><span class="input-unit">元</span></template></el-input><p class="field-hint">参考范围 300–999,999,999.99 元，最多两位小数。</p></el-form-item>
      </template>
      <template v-if="kind === '调整 ROI 目标' || (kind === '修改计划' && form.bidType === 'SMART_BID_CUSTOM')">
        <el-form-item prop="roi"><template #label><span>净成交ROI目标</span><el-tooltip content="计划设置的净成交ROI目标，用于投放优化。" popper-class="campaign-tooltip"><el-icon class="label-help" tabindex="0" aria-label="净成交ROI目标说明"><QuestionFilled /></el-icon></el-tooltip></template><el-input v-model="form.roi" inputmode="decimal" placeholder="请输入净成交ROI目标" aria-label="净成交ROI目标" /><p class="field-hint">参考范围 0.01–100。目标过高可能影响获取流量。</p></el-form-item>
      </template>

    </el-form>
    <template #footer><el-button :disabled="saving" @click="open = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="kind === '切换投放方式'" @click="save">保存修改</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue';
import { DataLine, InfoFilled, QuestionFilled } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormItemRule, FormRules } from 'element-plus';
import { updatePlanSettings, type PlanListVo } from '@/features/delivery/deliveryApi';
import { PLAN_STATUS } from '@/utils/labels';
import CampaignToolsDialog from './CampaignToolsDialog.vue';

const props = defineProps<{ modelValue: boolean; kind: string; plan: PlanListVo | null; initialBidType?: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>();
const open = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) });
const isTool = computed(() => ['一键起量', '素材追投', '搜索抢首屏'].includes(props.kind));
const title = computed(() => props.kind === '调整 ROI 目标' ? '调整净成交ROI目标' : props.kind);
const formRef = ref<FormInstance>();
const saving = ref(false);
async function save() {
  if (!props.plan || saving.value || !(await formRef.value?.validate().catch(() => false))) return;
  const body: { name?: string; budget?: number; roi2Goal?: number } = {};
  if (props.kind === '修改计划' && form.name.trim() !== props.plan.name) body.name = form.name.trim();
  if (['调整预算', '修改计划'].includes(props.kind) && Number(form.budget) !== props.plan.budget) body.budget = Number(form.budget);
  if ((props.kind === '调整 ROI 目标' || (props.kind === '修改计划' && form.bidType === 'SMART_BID_CUSTOM')) && Number(form.roi) !== props.plan.roi2Goal) body.roi2Goal = Number(form.roi);
  if (!Object.keys(body).length) { ElMessage.info('参数没有变化'); return; }
  saving.value = true;
  try { await updatePlanSettings(props.plan.planId, body); ElMessage.success('计划已更新'); open.value = false; emit('saved'); }
  catch { /* Keep entered values; API interceptor presents the error. */ }
  finally { saving.value = false; }
}
const form = reactive({ name: '', budget: '', roi: '', bidType: '' });
function numericRule(min: number, max: number, label: string, exclusiveMin = false): FormItemRule {
  return { required: true, trigger: 'blur', validator: (_rule, value, callback) => {
    const s = String(value ?? '').trim();
    if (!/^\d+(\.\d{1,2})?$/.test(s) || !Number.isFinite(Number(s)) || (exclusiveMin ? Number(s) <= min : Number(s) < min) || Number(s) > max) callback(new Error(`${label}需${exclusiveMin ? '大于' : '不低于'} ${min}，不超过 ${max}，最多两位小数`));
    else callback();
  } };
}
const rules = computed<FormRules>(() => ({
  name: [{ required: true, whitespace: true, message: '请输入计划名称', trigger: 'blur' }],
  bidType: [{ required: true, message: '请选择投放方式', trigger: 'change' }],
  budget: [numericRule(300, 999999999.99, '日预算')],
  roi: [numericRule(0.01, 100, 'ROI 目标')],
}));
watch(() => props.modelValue, value => {
  if (!value) return;
  Object.assign(form, { name: props.plan?.name || '', budget: props.plan?.budget == null ? '' : String(props.plan.budget), roi: props.plan?.roi2Goal == null ? '' : String(props.plan.roi2Goal), bidType: props.initialBidType || props.plan?.smartBidType || '' });
  nextTick(() => formRef.value?.clearValidate());
});
</script>

<style scoped>
.operation-plan { display: flex; align-items: center; gap: 12px; border: 1px solid #e6e8f2; background: #fafaff; border-radius: 10px; padding: 12px; margin-bottom: 16px; }
.operation-plan-icon { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 8px; background: #eeeefe; color: #575cf6; flex-shrink: 0; font-size: 18px; }
.operation-plan > div { flex: 1; min-width: 0; }.operation-plan strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #4e5069; font-size: 13px; font-weight: 500; }.operation-plan div span { display: block; color: #7c7f99; font-size: 12px; overflow-wrap: anywhere; margin-top: 4px; }.operation-plan-status { font-size: 12px; color: #7c7f99; flex-shrink: 0; }
.operation-notice { display: flex; gap: 8px; color: #676b94; background: #f3f4ff; padding: 10px 12px; font-size: 12px; line-height: 20px; border-radius: 8px; margin-bottom: 24px; }.operation-notice .el-icon { margin-top: 3px; flex-shrink: 0; }
.operation-form :deep(.el-form-item) { margin-bottom: 24px; }.field-hint { color: #7c7f99; font-size: 12px; line-height: 20px; margin: 8px 0 0; }.input-unit { color: #7c7f99; padding-left: 8px; }.label-help { color: #9da0b5; margin-left: 6px; vertical-align: -2px; }
.bid-options { width: 100%; display: grid; gap: 10px; }.bid-options :deep(.el-radio) { margin: 0; height: auto; min-height: 70px; padding: 12px 16px; border-radius: 10px; }.bid-options :deep(.el-radio__label) { white-space: normal; }.bid-options strong { display: block; font-size: 13px; font-weight: 600; }.bid-options span { display: block; margin-top: 6px; font-size: 12px; color: #7c7f99; }.bid-options :deep(.is-checked) { background: #f5f5ff; }
</style>
