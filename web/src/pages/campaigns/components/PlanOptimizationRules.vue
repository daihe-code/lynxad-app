<template>
  <div v-loading="loading" class="rules-editor" :class="{ 'paused-editor': paused }">
    <header>
      <div class="heading"><h2>{{ configured ? '修改盯盘规则' : '设置盯盘优化规则' }}</h2><span v-if="view" class="tag" :class="{ 'paused-tag': paused }">{{ paused ? '盯盘优化：已暂停' : view.policy.enabled ? '已开启' : '尚未开启' }}</span><button class="close" aria-label="关闭盯盘规则" :disabled="saving" @click="emit('close')">×</button></div>
      <div class="plan"><div><strong>{{ plan?.name || '当前投放计划' }}</strong><small>广告账户：{{ accountName || plan?.accountId || '—' }}</small></div><el-tag size="small">{{ planStatus }}</el-tag></div>
      <nav><button v-for="(title, i) in sections" :key="title" :class="{ active: active === i }" @click="jump(i)">{{ title }}</button></nav>
    </header>
    <main ref="scrollArea" @scroll="onScroll">
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-alert v-if="view && !view.available" :title="view.unavailableReason || '当前计划暂不可开启盯盘'" type="warning" :closable="false" />
      <template v-if="form">
        <el-alert v-if="paused" title="保存规则后，盯盘优化仍为暂停状态" type="warning" :closable="false" show-icon />
        <div v-else class="base"><el-icon><CircleCheckFilled /></el-icon><div><strong>基础状态检查</strong><small>{{ view?.policy.enabled ? '随盯盘检查投放状态，数据异常时暂停受影响的判断。' : '保存并开启后，持续检查投放状态与可用数据。' }}</small></div></div>
        <section ref="roiSection">
          <h3><b>01</b>目标ROI保护</h3>
          <el-form-item label="期望净成交ROI不低于" required :error="errors.roiFloor" class="roi-input"><el-input v-model="form.roiFloor" inputmode="decimal" aria-label="期望净成交ROI不低于" /></el-form-item>
          <p>用于盯盘判断，不修改平台的出价或ROI目标。</p>
          <template v-if="!paused">
            <div class="panel"><h4>小灵会关注这些情况 <span class="tag">了解即可</span></h4><div class="concerns"><span>ROI低于要求，或持续走低</span><span>持续产生消耗，但成交没有跟上</span><span>点击、转化表现或流量成本异常</span><span>退款增加、素材衰减，或商品与素材的成交贡献变化</span></div></div>
            <div class="panel"><h4>效果变差后怎样处理？</h4><p>先核对数据更新、成交回传、商品与素材，再分析原因并给出建议。调整预算、更新素材或暂停计划，均需你确认。</p><small>数据不足时继续观察，避免因短时波动贸然调整。</small></div>
          </template>
        </section>
        <section ref="spendSection">
          <h3><b>02</b>消耗保护</h3>
          <div class="pair"><el-form-item label="当天消耗提醒金额" required :error="errors.spendReminder"><el-input v-model="form.spendReminder" inputmode="decimal" aria-label="当天消耗提醒金额"><template #append>元</template></el-input></el-form-item><el-form-item label="当天消耗保护金额" required :error="errors.spendProtection"><el-input v-model="form.spendProtection" inputmode="decimal" aria-label="当天消耗保护金额"><template #append>元</template></el-input></el-form-item></div>
          <p>达到提醒金额时通知你；达到保护金额时，按以下方式处理。</p>
          <el-form-item label="达到保护金额后的处理方式" required :error="errors.protectionAction"><el-radio-group v-model="form.protectionAction"><el-radio value="CONFIRM">提醒我确认暂停</el-radio><el-radio value="AUTO_PAUSE">允许小灵自动暂停本计划</el-radio></el-radio-group></el-form-item>
          <p>选择自动暂停即授予本计划消耗保护权限；独立调控任务另行处理。恢复投放始终需你确认。</p>
          <div class="panel runaway"><h4>跑飞提醒 <span class="tag">每分钟检查</span></h4><p>消耗异常快、成交没有跟上时，及时提醒。</p><div class="runaway-fields"><span>最近</span><el-form-item :error="errors.runawayMinutes"><el-input v-model="form.runawayMinutes" inputmode="numeric" aria-label="跑飞观察分钟数" /></el-form-item><span>分钟，消耗达到</span><el-form-item :error="errors.runawaySpend"><el-input v-model="form.runawaySpend" inputmode="decimal" aria-label="跑飞提醒消耗" /></el-form-item><span>元</span></div><p>且成交表现未达到本计划要求时提醒。</p><small>核对成交回传与投放变化；数据不足时继续观察。跑飞暂停仍需你确认。</small></div>
          <p class="note">暂停生效和数据更新存在延迟，最终消耗可能超过设置金额。</p>
        </section>
        <section ref="growthSection">
          <h3><b>03</b>增长机会洞察</h3><p>表现稳健时，寻找合适的增长机会；没有合适机会时保持当前设置。</p>
          <div class="summary">沿用本计划要求：<strong>净成交ROI不低于 {{ format(form.roiFloor) }}</strong><strong>消耗保护金额 ¥{{ format(form.spendProtection) }}</strong></div>
          <div class="pair"><el-form-item label="每次最多追加预算" required :error="errors.maxAddition"><el-input v-model="form.maxAddition" inputmode="decimal" aria-label="每次最多追加预算"><template #append>元</template></el-input></el-form-item><el-form-item label="每天累计最多追加预算" required :error="errors.dailyAddition"><el-input v-model="form.dailyAddition" inputmode="decimal" aria-label="每天累计最多追加预算"><template #append>元</template></el-input></el-form-item></div>
          <p>预算增加、素材追投和一键起量的追加预算合并核对，避免连续多次超出设置。</p><p class="note">这些金额只约束建议范围，不代表授权自动花费。每次追加预算仍需你确认。</p>
        </section>
        <section class="capabilities"><button class="expand" :aria-expanded="expanded" @click="expanded = !expanded"><h3>能力与处理说明 <span class="tag">了解即可</span></h3><span>{{ expanded ? '收起' : '展开' }}⌄</span></button><template v-if="expanded"><h4>小灵会寻找哪些机会？</h4><table><thead><tr><th>发现的表现</th><th>对应处理建议</th></tr></thead><tbody><tr v-for="row in opportunities" :key="row[0]"><td>{{ row[0] }}</td><td>{{ row[1] }}</td></tr></tbody></table><h4>发现机会后怎样处理？</h4><p>先核对数据、投放资格、当前消耗和运行任务，再给出具体建议。每次说明原因、预算和观察安排，由你确认后执行，并继续跟踪结果。</p><p>有跑飞或其他消耗风险时，先处理风险，暂缓追加投入建议；不会自动提高消耗保护金额。</p></template></section>
      </template>
    </main>
    <footer><small>{{ dirty ? '修改尚未保存' : '' }}</small><div><el-button :disabled="saving" @click="emit('close')">取消</el-button><el-button v-if="view?.available" type="primary" :loading="saving" :disabled="!form" @click="save">{{ paused ? '保存规则，不恢复' : configured ? '保存修改' : '保存并开启' }}</el-button></div></footer>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { CircleCheckFilled } from '@element-plus/icons-vue';
import { getOptimizationRules, saveOptimizationRules, type OptimizationRules, type OptimizationView } from '@/features/delivery/optimizationApi';
import { defaultOptimizationRules, numericOptimizationRules, validateOptimizationRules } from '@/features/delivery/optimizationRules';
import { planStatusDisplay } from '@/features/delivery/planStatus';
import type { PlanListVo } from '@/features/delivery/deliveryApi';
const props = defineProps<{ planId: string; plan?: PlanListVo; accountName?: string }>();
const emit = defineEmits<{ saved: []; close: [] }>();
const sections = ['目标ROI保护', '消耗保护', '增长机会洞察'];
const opportunities = [['ROI与成交持续稳健', '判断是否适合增长，也可保持现状'], ['某条素材持续带来较好成交', '建议素材追投，说明预算与时长'], ['效果较好，但素材数量或更新不足', '建议补充新素材或制作不同版本'], ['表现较好，但预算限制继续投放', '核对消耗后，建议是否增加预算'], ['起量或追投任务表现较好', '评估续投机会，重新请你确认'], ['商品或新素材值得进一步验证', '建议小范围试投，并安排后续观察']];
const view = ref<OptimizationView>(), form = ref<OptimizationRules>(), errors = ref<ReturnType<typeof validateOptimizationRules>>({});
const planStatus = computed(() => planStatusDisplay(view.value?.planStatus || props.plan?.status, view.value?.qcStatus ?? props.plan?.qcStatus).label);
const configured = computed(() => !!(view.value?.policy.rules || view.value?.policy.enabled));
const paused = computed(() => configured.value && !view.value?.policy.enabled);
const loading = ref(true), saving = ref(false), error = ref(''), expanded = ref(false), active = ref(0);
const scrollArea = ref<HTMLElement>(), roiSection = ref<HTMLElement>(), spendSection = ref<HTMLElement>(), growthSection = ref<HTMLElement>();
let disposed = false, saved = '';
const dirty = computed(() => !!form.value && JSON.stringify(form.value) !== saved);
const format = (value: unknown) => value == null || value === '' || !Number.isFinite(Number(value)) ? '—' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 });
function jump(index: number) { active.value = index; const element = [roiSection.value, spendSection.value, growthSection.value][index]; if (element && scrollArea.value) scrollArea.value.scrollTo({ top: element.offsetTop - 8, behavior: 'smooth' }); }
function onScroll() { if (!scrollArea.value) return; const top = scrollArea.value.getBoundingClientRect().top + 65; let index = 0; [roiSection.value, spendSection.value, growthSection.value].forEach((element, i) => { if (element && element.getBoundingClientRect().top <= top) index = i; }); active.value = index; }
async function save() {
  if (!form.value || !view.value?.available || saving.value) return;
  errors.value = validateOptimizationRules(form.value);
  if (Object.keys(errors.value).length) { const key = Object.keys(errors.value)[0]; jump(key === 'roiFloor' ? 0 : ['maxAddition', 'dailyAddition'].includes(key!) ? 2 : 1); return; }
  if (!paused.value && form.value.protectionAction === 'AUTO_PAUSE') { try { await ElMessageBox.confirm(`当天消耗达到 ¥${format(form.value.spendProtection)} 时，允许自动暂停本计划。独立调控任务不在此次授权内，恢复投放需再次确认。`, '确认消耗保护授权', { confirmButtonText: '确认并保存', cancelButtonText: '取消' }); } catch { return; } }
  saving.value = true; error.value = '';
  try { const value = await saveOptimizationRules(props.planId, numericOptimizationRules(form.value), !paused.value, view.value.version); if (!disposed) { view.value = value; saved = JSON.stringify(form.value); ElMessage.success({ message: '盯盘规则已保存', duration: 3000 }); emit('saved'); } }
  catch (err: any) { if (!disposed) error.value = err?.response?.data?.message || err?.message || '保存失败，请重试'; }
  finally { saving.value = false; }
}
onMounted(async () => { try { const value = await getOptimizationRules(props.planId); if (!disposed) { view.value = value; form.value = { ...(value.policy.rules || defaultOptimizationRules(props.plan?.roi2Goal, props.plan?.budget)) }; saved = JSON.stringify(form.value); } } catch (err: any) { if (!disposed) error.value = err?.response?.data?.message || err?.message || '暂时无法加载规则，请重试'; } finally { if (!disposed) loading.value = false; } });
watch(form, () => { if (form.value && Object.keys(errors.value).length) errors.value = validateOptimizationRules(form.value); }, { deep: true });
onBeforeUnmount(() => { disposed = true; });
</script>
<style scoped>
.tag.paused-tag{background:#fff0db;color:#a45c10}
.paused-editor section{margin-top:8px}
.rules-editor{height:100%;display:flex;flex-direction:column;background:#fff;color:#202444}header{padding:18px 22px 0;flex:none}.heading{display:flex;align-items:center;gap:12px}h2{font-size:18px;margin:0}.tag{font-size:11px;padding:3px 10px;border-radius:16px;background:#efefff;color:#6f7399}.close{margin-left:auto;border:0;background:none;font-size:27px;color:#72779a;cursor:pointer}.plan{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:13px 15px;margin:16px 0 8px;background:linear-gradient(100deg,#f6f5ff,#f2f6ff);border:1px solid #e3e5f7;border-radius:7px}.plan strong{font-size:14px;overflow-wrap:anywhere}small{display:block;font-size:12px;color:#7d83a6;line-height:1.7}.plan small{margin-top:5px}nav{display:flex;border-bottom:1px solid #dedff1}nav button{width:33.333%;padding:16px 3px 12px;border:0;border-bottom:2px solid transparent;background:white;color:#666d90;cursor:pointer;font-size:14px;font-weight:600}nav button.active{color:#5546ff;border-bottom-color:#5546ff}main{flex:1;min-height:0;overflow:auto;position:relative;padding:16px 22px 28px}section{padding:18px 16px;border:1px solid #e1e5f4;border-radius:8px;margin-bottom:14px}h3{display:flex;align-items:center;gap:10px;font-size:16px;margin:0 0 18px}h3>b{padding:6px;background:#eeecff;color:#5849ff;border-radius:50%;font-size:14px}.base{display:flex;gap:12px;align-items:center;background:#efedff;border-radius:7px;padding:14px;margin-bottom:14px;color:#5947fb}.base>.el-icon{font-size:25px}.base strong{font-size:14px}.base small{margin-top:5px}p{font-size:12px;color:#7d83a6;line-height:1.8;margin:8px 0 12px}:deep(.el-form-item){margin-bottom:18px}:deep(.el-form-item__label){font-size:13px;font-weight:600;line-height:1.6;margin-bottom:7px}:deep(.el-input__wrapper){box-shadow:0 0 0 1px #d7dcef inset;background:#fff}:deep(.el-input__inner){font-weight:600;color:#23264d}:deep(.el-form-item.is-error .el-input__wrapper){box-shadow:0 0 0 1px var(--el-color-danger) inset}.roi-input{align-items:center;gap:20px}.roi-input :deep(.el-form-item__content){max-width:190px}.pair{display:grid;grid-template-columns:1fr 1fr;gap:28px}.pair :deep(.el-form-item){display:block}.panel{padding:12px;margin-top:12px;background:#f5f4ff;border-radius:7px}h4{font-size:13px;margin:0 0 8px}.concerns{display:grid;grid-template-columns:1fr 1fr;gap:8px}.concerns span{background:#fff;padding:8px;font-size:11px;color:#7d83a6;border-radius:5px}.runaway{border:1px solid #dad5ff;background:#f4f1ff;padding:14px}.runaway h4{color:#5947fb}.runaway-fields{display:flex;align-items:center;gap:10px;flex-wrap:wrap;font-size:12px;font-weight:600}.runaway-fields :deep(.el-form-item){max-width:100px;margin:0 0 5px}.note{background:#f1efff;color:#737aa0;border-radius:6px;padding:10px;font-size:12px;line-height:1.7}.summary{background:#f2f3ff;border-radius:6px;padding:12px;margin-bottom:18px;display:flex;flex-wrap:wrap;gap:12px;font-size:12px}.expand{display:flex;justify-content:space-between;align-items:center;gap:12px;width:100%;padding:0;border:0;background:none;cursor:pointer;color:inherit}.expand h3{margin:0;font-size:14px;flex-wrap:wrap}.expand>span{font-size:12px;color:#5947fb;white-space:nowrap}.capabilities h4{margin-top:16px}table{border-collapse:collapse;width:100%;font-size:12px;color:#737aa0;margin:12px 0 18px}th,td{border:1px solid #e1e4f7;padding:6px;text-align:left}th{background:#efefff;color:#31365d}footer{flex:none;display:flex;justify-content:space-between;align-items:center;gap:12px;border-top:1px solid #e6e7f4;padding:12px 22px}footer>div{display:flex;gap:10px}footer :deep(.el-button + .el-button){margin-left:0}@media(max-width:600px){header{padding:14px 14px 0}main{padding:14px}footer{padding:12px 14px}.pair,.concerns{grid-template-columns:1fr;gap:4px}.roi-input{display:block}nav button{font-size:12px}.plan strong{font-size:12px}section{padding:14px}}
</style>
