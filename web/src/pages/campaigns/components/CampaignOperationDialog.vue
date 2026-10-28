<template>
  <CampaignToolsDialog v-if="isTool" v-model="open" :kind="kind" :plan="plan" />
  <el-dialog v-else v-model="open" :title="title" :width="kind === '修改计划' ? '560px' : '460px'" class="campaign-dialog" modal-class="campaign-overlay" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" align-center destroy-on-close>
    <div v-if="plan" class="operation-plan">
      <span class="operation-plan-icon"><el-icon><DataLine /></el-icon></span>
      <div><strong :title="plan.name">{{ baseline?.name || plan.name }}</strong><span>ID：{{ plan.qcPlanId || plan.planId }}</span></div>
      <span class="operation-plan-status">{{ PLAN_STATUS[plan.status] || plan.status }}</span>
    </div>
    <div class="operation-notice"><el-icon><InfoFilled /></el-icon><span>保存后更新当前计划。投放方式和素材保持原设置。</span></div>

    <el-form :disabled="saving || loading || !editableBaseline" ref="formRef" :model="form" :rules="rules" :validate-on-rule-change="false" label-position="top" class="operation-form" @submit.prevent>
      <template v-if="kind === '修改计划'">
        <el-form-item label="计划名称" prop="name"><el-input v-model="form.name" maxlength="30" show-word-limit placeholder="请输入自定义计划名称" aria-label="计划名称" /><p class="field-hint">可自定义 1–30 字的名称，用于区分不同投放计划。</p></el-form-item>
      </template>
      <template v-if="kind === '调整预算' || kind === '修改计划'">
        <el-form-item label="日预算" prop="budget"><el-input v-model="form.budget" inputmode="decimal" placeholder="请输入日预算" aria-label="日预算金额（元）"><template #suffix><span class="input-unit">元</span></template></el-input><p class="field-hint">参考范围 {{ budgetMinimum }}–999,999,999.99 元，最多两位小数。</p></el-form-item>
      </template>
      <template v-if="kind === '调整 ROI 目标' || (kind === '修改计划' && form.bidType === 'SMART_BID_CUSTOM')">
        <el-form-item prop="roi"><template #label><span>净成交ROI目标</span><el-tooltip content="计划设置的净成交ROI目标，用于投放优化。" popper-class="campaign-tooltip"><el-icon class="label-help" tabindex="0" aria-label="净成交ROI目标说明"><QuestionFilled /></el-icon></el-tooltip></template><el-input v-model="form.roi" inputmode="decimal" placeholder="请输入净成交ROI目标" aria-label="净成交ROI目标" /><p class="field-hint">参考范围 0.01–100。目标过高可能影响获取流量。</p></el-form-item>
      </template>

    </el-form>
    <section v-if="editError || loading || comparison || blocked" class="operation-recovery" aria-live="polite">
      <p v-if="loading">正在读取当前计划版本…</p><p v-if="editError" role="alert">{{ editError }}</p>
      <div class="operation-recovery-actions"><button type="button" :disabled="loading || saving" @click="baseline ? readLatest() : loadBaseline()">读取最新值并核对</button><router-link v-if="blocked && baseline?.qcPlanId" :to="`/campaigns/${baseline.planId}/edit?step=2`" target="_blank">在新窗口核查修改结果</router-link></div>
      <template v-if="comparison"><p>当前输入已保留。请核对后选择，修改不会自动重发。</p><table><thead><tr><th>字段</th><th>原值</th><th>我的输入</th><th>最新值</th></tr></thead><tbody><tr v-for="row in compareRows" :key="row.key"><th>{{ row.label }}</th><td>{{ row.before }}</td><td>{{ row.mine }}</td><td>{{ row.latest }}</td></tr></tbody></table><div class="operation-recovery-actions"><button type="button" :disabled="comparison.settingsEdit?.status !== 'IDLE' || !comparison.settingsEdit?.version" @click="adopt(false)">采用最新值</button><button v-if="canKeep" type="button" @click="adopt(true)">保留我的修改，采用最新版本</button></div></template>
    </section>
    <template #footer><el-button :disabled="saving" @click="open = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="loading || blocked || !!comparison || !baseline?.settingsEdit?.version || !editableBaseline" @click="save">保存修改</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue';
import { DataLine, InfoFilled, QuestionFilled } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormItemRule, FormRules } from 'element-plus';
import { updatePlanSettings, getPlan, refreshPlanSettings, type PlanListVo, type PlanVo } from '@/features/delivery/deliveryApi';
import { budgetMinimum as minimumBudget, budgetInputError, roiInputError, planNameError } from '@/features/delivery/inputValidation';
import { canRebasePlan, rebasePlanInputs, planEditComparison } from '@/features/delivery/planEditConflict';
import { requestFailure } from '@/api/apiErrors';
import { PLAN_STATUS } from '@/utils/labels';
import CampaignToolsDialog from './CampaignToolsDialog.vue';

const props = defineProps<{ modelValue: boolean; kind: string; plan: PlanListVo | null }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>();
const open = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) });
const isTool = computed(() => ['一键起量', '素材追投'].includes(props.kind));
const title = computed(() => props.kind === '调整 ROI 目标' ? '调整净成交ROI目标' : props.kind);
const formRef = ref<FormInstance>();
const saving = ref(false), loading=ref(false), blocked=ref(false), editError=ref('');
const baseline=ref<PlanVo>(),comparison=ref<PlanVo>();let loadVersion=0;
const editableBaseline=computed(()=>!!baseline.value?.qcPlanId && ['DELIVERING','PAUSED'].includes(baseline.value.status) && (props.kind!=='调整 ROI 目标'||baseline.value.smartBidType==='SMART_BID_CUSTOM'));
const compareRows=computed(()=>baseline.value && comparison.value ? planEditComparison(baseline.value,comparison.value,form) : []);
const canKeep=computed(()=>!!baseline.value && !!comparison.value && canRebasePlan(baseline.value,comparison.value));
async function save() {
  if (!props.plan || !baseline.value?.settingsEdit?.version || !editableBaseline.value || blocked.value || comparison.value || loading.value || saving.value || !(await formRef.value?.validate().catch(() => false))) return;
  const body: { name?: string; budget?: number; roi2Goal?: number } = {};
  if (props.kind === '修改计划' && form.name.trim() !== baseline.value.name) body.name = form.name.trim();
  if (['调整预算', '修改计划'].includes(props.kind) && Number(form.budget) !== baseline.value.budget) body.budget = Number(form.budget);
  if ((props.kind === '调整 ROI 目标' || (props.kind === '修改计划' && form.bidType === 'SMART_BID_CUSTOM')) && Number(form.roi) !== baseline.value.roi2Goal) body.roi2Goal = Number(form.roi);
  if (!Object.keys(body).length) { ElMessage.info('参数没有变化'); return; }
  editError.value='';
  saving.value = true;
  try { await updatePlanSettings(props.plan.planId, {...body,expectedVersion:baseline.value.settingsEdit.version}); ElMessage.success('计划已更新'); open.value = false; emit('saved'); }
  catch(error) {const failure=requestFailure(error,{method:'PUT'});editError.value=failure.message;blocked.value=['CONFLICT','UNCERTAIN'].includes(failure.kind);}
  finally { saving.value = false; }
}
const form = reactive({ name: '', budget: '', roi: '', bidType: '' });
const budgetMinimum = computed(() => minimumBudget(form.bidType));
const unchanged=(key:'name'|'budget'|'roi')=>baseline.value && (key==='name'?form.name.trim()===baseline.value.name:Number(form[key])===(key==='budget'?baseline.value.budget:baseline.value.roi2Goal));
const rule=(error:()=>string):FormItemRule=>({trigger:'blur',validator:(_rule,_value,callback)=>{const message=error();message?callback(new Error(message)):callback();}});
const rules = computed<FormRules>(() => ({
  name:[rule(()=>unchanged('name')?'':planNameError(form.name))],
  budget:[rule(()=>unchanged('budget')?'':budgetInputError(form.budget,form.bidType,'update'))],
  roi:[rule(()=>unchanged('roi')?'':roiInputError(form.roi,form.bidType,'update'))],
}));
function fill(plan:PlanVo){Object.assign(form,{name:plan.name,budget:String(plan.budget??''),roi:String(plan.roi2Goal??''),bidType:plan.smartBidType||''});void nextTick(()=>formRef.value?.clearValidate());}
async function loadBaseline(){
  const id=props.plan?.planId,version=++loadVersion;if(!id || isTool.value)return;
  loading.value=true;editError.value='';
  try{const result=await getPlan(id);if(version!==loadVersion)return;baseline.value=result;fill(result);blocked.value=!result.settingsEdit?.version || result.settingsEdit.status!=='IDLE' || !editableBaseline.value;if(blocked.value)editError.value=result.settingsEdit?.message || (!editableBaseline.value?'当前计划状态或投放方式不支持此修改，请核对最新设置':'请读取最新计划版本后再修改');}
  catch(error){if(version===loadVersion){editError.value=requestFailure(error,{method:'GET'}).message;blocked.value=true;}}
  finally{if(version===loadVersion)loading.value=false;}
}
async function readLatest(){
  const id=props.plan?.planId,version=loadVersion;if(!id||loading.value||saving.value)return;loading.value=true;
  try{const current=await getPlan(id);const latest=current.settingsEdit?.status==='IDLE'?await refreshPlanSettings(id):current;if(version!==loadVersion)return;comparison.value=latest;blocked.value=true;editError.value=latest.settingsEdit?.message || '已读取最新设置，当前输入未被覆盖。';}
  catch(error){if(version===loadVersion)editError.value=requestFailure(error,{method:'GET'}).message;}
  finally{if(version===loadVersion)loading.value=false;}
}
function adopt(keepMine:boolean){
  const before=baseline.value,latest=comparison.value;if(!before||!latest?.settingsEdit?.version||latest.settingsEdit.status!=='IDLE')return;
  const values=keepMine?rebasePlanInputs(before,latest,form):undefined;baseline.value=latest;fill(latest);if(values)Object.assign(form,values);comparison.value=undefined;blocked.value=!editableBaseline.value;editError.value=editableBaseline.value?'':'当前计划状态或投放方式不支持此修改，请核对最新设置';
}
watch(() => [props.modelValue,props.plan?.planId,props.kind], () => {
  ++loadVersion;loading.value=false;baseline.value=undefined;comparison.value=undefined;blocked.value=!editableBaseline.value;editError.value=editableBaseline.value?'':'当前计划状态或投放方式不支持此修改，请核对最新设置';
  if(props.modelValue&&!isTool.value)void loadBaseline();
},{immediate:true});

</script>

<style scoped>
.operation-plan { display: flex; align-items: center; gap: 12px; border: 1px solid #e6e8f2; background: #fafaff; border-radius: 10px; padding: 12px; margin-bottom: 16px; }
.operation-plan-icon { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 8px; background: #eeeefe; color: #575cf6; flex-shrink: 0; font-size: 18px; }
.operation-plan > div { flex: 1; min-width: 0; }.operation-plan strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #4e5069; font-size: 13px; font-weight: 500; }.operation-plan div span { display: block; color: #7c7f99; font-size: 12px; overflow-wrap: anywhere; margin-top: 4px; }.operation-plan-status { font-size: 12px; color: #7c7f99; flex-shrink: 0; }
.operation-notice { display: flex; gap: 8px; color: #676b94; background: #f3f4ff; padding: 10px 12px; font-size: 12px; line-height: 20px; border-radius: 8px; margin-bottom: 24px; }.operation-notice .el-icon { margin-top: 3px; flex-shrink: 0; }
.operation-form :deep(.el-form-item) { margin-bottom: 24px; }.field-hint { color: #7c7f99; font-size: 12px; line-height: 20px; margin: 8px 0 0; }.input-unit { color: #7c7f99; padding-left: 8px; }.label-help { color: #9da0b5; margin-left: 6px; vertical-align: -2px; }
</style>

<style scoped>
.operation-recovery{padding:12px;border:1px solid #e1e2f3;border-radius:10px;font-size:12px;line-height:1.6;color:#555875;overflow-wrap:anywhere}.operation-recovery p{margin:6px 0}.operation-recovery-actions{display:flex;gap:12px;flex-wrap:wrap;margin-top:8px}.operation-recovery-actions button,.operation-recovery-actions a{font:inherit;color:#575cf6;text-decoration:underline}.operation-recovery-actions button:disabled{opacity:.45}.operation-recovery table{width:100%;table-layout:fixed;border-collapse:collapse;margin-top:10px}.operation-recovery th,.operation-recovery td{padding:6px;border:1px solid #e1e2f3;overflow-wrap:anywhere;text-align:left;vertical-align:top}
</style>
