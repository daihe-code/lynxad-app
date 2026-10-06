<template>
  <el-dialog v-model="open" width="720px" class="campaign-dialog campaign-tools-dialog" modal-class="campaign-overlay" :close-on-click-modal="false" :close-on-press-escape="!submitting" :show-close="!submitting" align-center destroy-on-close>
    <template #header><div class="tools-header"><h2>调控工具</h2><span :title="plan?.name">· {{ plan?.name }}</span></div></template>
    <div class="tools-cards" role="tablist" aria-label="选择调控工具">
      <div v-for="tool in tools" :key="tool.key" class="tool-card" :class="{ active: activeTool === tool.key }">
        <button :id="`control-tab-${tool.key}`" class="tool-pick" :disabled="submitting" type="button" role="tab" :aria-selected="activeTool === tool.key" aria-controls="control-parameters" :tabindex="activeTool === tool.key ? 0 : -1" @click="activeTool = tool.key" @keydown="moveTab($event, tool.key)">
          <strong><svg v-if="tool.key === '一键起量'" class="bolt-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M13 2 5 14h6l-1 8 9-13h-6z" /></svg><svg v-else-if="tool.key === '素材追投'" class="tool-video-icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="3" /><path d="m10 9 5 3-5 3z" /></svg><el-icon v-else><component :is="tool.icon" /></el-icon>{{ tool.key }}</strong><span>· {{ tool.description }}</span><span>· {{ tool.condition }}</span>
        </button>
        <el-popover ref="helpPopovers" trigger="click" placement="bottom" :width="310" popper-class="campaign-popover tools-help-popover"><template #reference><button class="tool-help" type="button">{{ tool.helpLabel }}</button></template><strong>{{ tool.key }}原理</strong><p class="principle-text">{{ tool.description }}</p><p class="principle-text">{{ tool.condition }}</p></el-popover>
      </div>
    </div>
    <el-alert v-if="activeTool === '搜索抢首屏'" title="搜索抢首屏专用接口尚未核验，暂不可提交" type="info" :closable="false" show-icon /><section id="control-parameters" role="tabpanel" :aria-labelledby="`control-tab-${activeTool}`">
      <section v-if="activeTool === '素材追投'" class="material-section">
        <h3>追投素材（仅视频）</h3>
        <div class="material-filters">
          <el-input v-model="videoKeyword" clearable placeholder="输入视频名称/ID/后回车搜索" aria-label="搜索追投视频" @keyup.enter="searchVideos"><template #prefix><el-icon><Search /></el-icon></template></el-input>
          <div class="material-date-filter"><el-icon aria-hidden="true"><Calendar /></el-icon><span>范围：当前计划关联素材</span></div>
          <el-select v-model="videoTag" placeholder="投放状态：" aria-label="素材投放状态" clearable popper-class="campaign-dropdown"><el-option v-for="tag in tags" :key="tag" :value="tag" :label="tag" /></el-select>
        </div>
        <p class="material-spec"><el-icon><InfoFilled /></el-icon>视频需要为自建素材，且审核通过、全域在投；仅可选择平台确认正在投放且审核通过的自建视频。</p>
        <div class="material-table-wrap" v-loading="videosLoading">
          <div class="material-table-scroll"><table class="material-table"><colgroup><col style="width:40px"><col><col style="width:88px"><col style="width:94px"><col style="width:74px"><col style="width:110px"></colgroup><thead><tr><th></th><th>视频</th><th>视频类型</th><th>审核状态</th><th>投放状态</th><th>整体展示次数 <el-icon><Sort /></el-icon></th></tr></thead><tbody>
            <tr v-for="video in pagedVideos" :key="video.id"><td><el-tooltip :content="video.reason || '可追投'" popper-class="campaign-tooltip"><span><el-checkbox v-model="selectedVideos" :value="video.id" :disabled="!video.eligible || submitting" :aria-label="`选择视频 ${video.name}`" /></span></el-tooltip></td><td><div class="video-info"><span class="video-cover"><el-image v-if="video.coverUrl" :src="video.coverUrl" fit="cover"><template #error><el-icon><VideoCamera /></el-icon></template></el-image><el-icon v-else><VideoCamera /></el-icon></span><div><strong :title="video.name">{{ video.name }}</strong><span>素材ID：{{ video.id }}</span></div></div></td><td>视频</td><td><span class="video-review" :class="{ rejected: video.auditStatus === 'REJECT' }"><el-icon v-if="video.auditStatus === 'REJECT'"><WarningFilled /></el-icon>{{ reviewText(video.auditStatus) }}</span></td><td>{{ tagText(video.materialStatus) }}</td><td class="video-impressions">{{ video.impressions ?? '—' }}</td></tr>
            <tr v-if="!pagedVideos.length && !videosLoading"><td colspan="6" class="material-empty"><p>{{ videosFailed ? '素材加载失败' : '暂无符合条件的自建视频' }}</p><button v-if="videosFailed" class="tool-help" @click="loadVideos">重新加载</button></td></tr>
          </tbody></table></div>
          <div class="material-pager"><span>共 {{ filteredVideos.length }} 条记录</span><el-pagination v-model:current-page="videoPage" :total="filteredVideos.length" :page-size="10" layout="jumper, prev, pager, next" background /><span>10条/页</span></div>
        </div>
      </section>
      <el-form ref="formRef" :model="draft" :rules="rules" :disabled="submitting" :validate-on-rule-change="false" label-position="top" class="tools-form" @submit.prevent="submit">
        <el-form-item prop="budget" class="budget-field"><template #label><span>调控预算(元)</span><el-tooltip :content="budgetHint" popper-class="campaign-tooltip"><button class="label-help-button" type="button" aria-label="调控预算说明"><el-icon><QuestionFilled /></el-icon></button></el-tooltip><span class="required-star">*</span></template><el-input v-model="draft.budget" inputmode="decimal" placeholder="请输入" aria-label="调控预算（元）"><template #suffix><span class="input-unit">元</span></template></el-input><template #error="{ error }"><p class="tool-error"><el-icon><WarningFilled /></el-icon>{{ error }}</p></template></el-form-item>
        <p v-if="!budgetInvalid" class="tool-hint budget-hint">{{ budgetHint }}</p>
        <template v-if="activeTool !== '搜索抢首屏'">
          <el-form-item prop="hours" class="duration-field"><template #label><span>调控时长</span><el-tooltip content="范围为 0.5–24 小时，可通过加减按钮以 0.5 小时调整。" popper-class="campaign-tooltip"><button class="label-help-button" type="button" aria-label="调控时长说明"><el-icon><QuestionFilled /></el-icon></button></el-tooltip><span class="required-star">*</span></template><div class="duration-stepper"><button type="button" aria-label="减少调控时长" :disabled="Number(draft.hours) <= .5 && draft.hours !== ''" @click="stepHours(-.5)"><el-icon><Minus /></el-icon></button><el-input v-model="draft.hours" placeholder="请输入" inputmode="decimal" aria-label="调控时长（小时）" @keydown.up.prevent="stepHours(.5)" @keydown.down.prevent="stepHours(-.5)" /><button type="button" aria-label="增加调控时长" :disabled="Number(draft.hours) >= 24" @click="stepHours(.5)"><el-icon><Plus /></el-icon></button><span>小时</span></div><template #error="{ error }"><p class="tool-error"><el-icon><WarningFilled /></el-icon>{{ error }}</p></template></el-form-item>
          <p class="tool-hint duration-hint">可设范围 0.5-24 小时。注意：有起量需求时，建议提前 10 分钟创建任务</p>
        </template>
        <template v-else>
          <el-form-item prop="roi" class="screen-roi-field"><template #label><span>净成交ROI目标</span><el-tooltip content="支持范围为 0.01–100，最多两位小数。" popper-class="campaign-tooltip"><button class="label-help-button" type="button" aria-label="净成交ROI目标说明"><el-icon><QuestionFilled /></el-icon></button></el-tooltip><span class="required-star">*</span></template><el-input v-model="draft.roi" inputmode="decimal" placeholder="请输入" aria-label="搜索抢首屏净成交ROI目标" /><template #error="{ error }"><p class="tool-error"><el-icon><WarningFilled /></el-icon>{{ error }}</p></template></el-form-item>
          <div class="roi-note"><el-icon><Opportunity /></el-icon><div><strong>建议参考实际经营目标设置</strong><p>可依据当前实际经营目标推算净成交ROI</p><p>净成交ROI =（整体成交金额 - 秒退订单成交额）/ 消耗</p></div></div>
          <div class="guarantee-note"><svg class="shield-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3 5 6v6c0 4 3 7 7 9 4-2 7-5 7-9V6zM12 8v5m0 3v.1" /></svg><span>搜索抢首屏按单独的「保障规则」提供保障权益（不计入全域投放成本保障），请谨慎修改净成交ROI目标，以免失去保障资格</span></div>
        </template>
        <el-form-item label="任务名称" prop="taskName" class="task-name-field"><el-input v-model="draft.taskName" maxlength="50" show-word-limit placeholder="请输入任务名称" aria-label="任务名称" /></el-form-item>
      </el-form>
    </section>
    <section class="control-history"><h3>近期调控任务 <el-button text :loading="tasksLoading" @click="loadTasks">刷新</el-button></h3><p v-if="tasksFailed">任务加载失败，请刷新核查</p><p v-else-if="!tasks.length">暂无调控任务</p><div v-for="task in tasks" :key="task.id"><strong>{{ task.name || task.id }}</strong><span>{{ task.task_status || '已提交' }}</span><small>ID：{{ task.id }}</small></div></section><template #footer><span class="tool-submit-note">{{ submittedId ? '已提交：' + submittedId : '预算不得超过原计划日预算' }}</span><el-button :disabled="submitting" @click="open = false">关闭</el-button><el-button type="primary" :loading="submitting" :disabled="activeTool === '搜索抢首屏' || !!submittedId" @click="submit">提交</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { ElMessage, type FormInstance, type FormItemRule, type PopoverInstance } from 'element-plus';
import { Calendar, Flag, InfoFilled, Minus, Opportunity, Plus, QuestionFilled, Search, Sort, VideoCamera, WarningFilled } from '@element-plus/icons-vue';
import { listControlMaterials, listControlTasks, createControlTask, type ControlMaterial, type ControlTask, type PlanListVo } from '@/features/delivery/deliveryApi';
const props = defineProps<{ modelValue: boolean; kind: string; plan: PlanListVo | null }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();
const open = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) });
const tools = [
  { key: '一键起量', icon: null, description: '对指定计划额外付出预算追加投放，系统将在指定时长及预算内，额外探索流量。', condition: '仅在「投放中」状态下支持开启一键起量。', helpLabel: '查看一键起量原理' },
  { key: '素材追投', icon: null, description: '对指定视频类素材额外追加投放预算（支持多素材同时追投），系统将在指定时长及预算内，额外探索流量。', condition: '适用新素材快速探索效果或优质视频补量激活。', helpLabel: '查看素材追投原理' },
  { key: '搜索抢首屏', icon: Flag, description: '开启抢首屏工具后，系统将为你探索更多搜索首屏曝光，获取更多优质流量。', condition: '仅在「投放中」状态下支持抢首屏。', helpLabel: '查看抢首屏原理' },
];
type Draft = { budget: string; hours: string; roi: string; taskName: string };
const drafts = reactive<Record<string, Draft>>(Object.fromEntries(tools.map(t => [t.key, { budget: '', hours: '', roi: '', taskName: '' }])));
const activeTool = ref('一键起量'), submitting = ref(false), submittedId = ref(''), requestKey = ref('');
const draft = computed(() => drafts[activeTool.value]!);
const formRef = ref<FormInstance>();
const helpPopovers = ref<PopoverInstance[]>([]);
const budgetHint = computed(() => activeTool.value === '搜索抢首屏' ? '专用接口尚未核验，暂不可提交' : '调控预算为额外追加的独立预算，不得超过原计划日预算。调控预算越高 ROI 下降风险越大，请合理设置');
const budgetInvalid = computed(() => !!formRef.value?.fields.find(field => field.prop === 'budget')?.validateMessage);
function numberRule(validate: (value: string) => string): FormItemRule { return { trigger: 'blur', validator: (_rule, value, callback) => { const error = validate(String(value ?? '').trim()); callback(error ? new Error(error) : undefined); } }; }
const decimal = (value: string) => /^\d+(\.\d{1,2})?$/.test(value) && Number.isFinite(Number(value));
const rules = computed(() => ({
  budget: [numberRule(value => !decimal(value) ? '请输入正确的预算，最多两位小数' : Number(value) <= 100 ? '预算需大于 100 元' : Number(value) > Number(props.plan?.budget || 0) ? '调控预算不得超过原计划日预算' : '')],
  hours: [numberRule(value => !decimal(value) || Number(value) < .5 || Number(value) > 24 || Number(value) % .5 !== 0 ? '调控时长为 0.5–24 小时，步进 0.5 小时' : '')],
  roi: [numberRule(value => !decimal(value) || Number(value) < .01 || Number(value) > 100 ? '支持范围: 0.01-100，最多两位小数' : '')],
  taskName: [{ required: true, whitespace: true, max: 50, message: '请输入 1–50 字的任务名称', trigger: 'blur' }],
}));
function moveTab(event: KeyboardEvent, key: string) { if (submitting.value || !['ArrowRight', 'ArrowLeft', 'Home', 'End'].includes(event.key)) return; event.preventDefault(); const index = tools.findIndex(t => t.key === key); activeTool.value = tools[event.key === 'Home' ? 0 : event.key === 'End' ? 2 : (index + (event.key === 'ArrowRight' ? 1 : -1) + 3) % 3]!.key; nextTick(() => document.getElementById('control-tab-' + activeTool.value)?.focus()); }
function stepHours(amount: number) { const current = Number(draft.value.hours); draft.value.hours = String(Math.max(.5, Math.min(24, (Number.isFinite(current) ? current : 0) + amount))); nextTick(() => formRef.value?.validateField('hours').catch(() => {})); }
function taskName(tool: string) { const time = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).format(new Date()); return time + ' ' + tool; }
const localDay = (date: Date) => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(date);
const videoKeyword = ref(''), videoTag = ref(''), videoPage = ref(1), dateRange = ref<string[]>([]), selectedVideos = ref<string[]>([]);
const videos = ref<ControlMaterial[]>([]), videosLoading = ref(false), videosFailed = ref(false);
const tasks = ref<ControlTask[]>([]), tasksLoading = ref(false), tasksFailed = ref(false);
let requestVersion = 0;
const tags = computed(() => [...new Set(videos.value.map(v => tagText(v.materialStatus)))]);
const filteredVideos = computed(() => videos.value.filter(v => (!videoTag.value || tagText(v.materialStatus) === videoTag.value) && (!videoKeyword.value.trim() || (v.name + v.id).toLowerCase().includes(videoKeyword.value.trim().toLowerCase()))));
const pagedVideos = computed(() => filteredVideos.value.slice((videoPage.value - 1) * 10, videoPage.value * 10));
function tagText(status: string) { return ({ DELIVERY_OK: '投放中', DELIVERY_NOT: '未投放', EXCLUDE: '已排除', DELETED: '已删除' } as Record<string,string>)[status] || status; }
function reviewText(review: string) { return ({ PASS: '审核通过', REJECT: '审核不通过', IN_PROGRESS: '审核中' } as Record<string,string>)[review] || '暂无审核信息'; }
function futureDate(date: Date) { return localDay(date) > localDay(new Date()); }
function searchVideos() { videoPage.value = 1; }
async function loadVideos() {
  if (!props.plan) return;
  const version = ++requestVersion; videosLoading.value = true; videosFailed.value = false;
  try { const rows = await listControlMaterials(props.plan.planId); if (version === requestVersion) { videos.value = rows; selectedVideos.value = selectedVideos.value.filter(id => rows.some(v => v.id === id && v.eligible)); } }
  catch { if (version === requestVersion) { videosFailed.value = true; videos.value = []; } }
  finally { if (version === requestVersion) videosLoading.value = false; }
}
async function loadTasks() {
  if (!props.plan) return;
  tasksLoading.value = true; tasksFailed.value = false;
  try { tasks.value = await listControlTasks(props.plan.planId); } catch { tasksFailed.value = true; } finally { tasksLoading.value = false; }
}
async function submit() {
  if (!props.plan || submitting.value || submittedId.value || activeTool.value === '搜索抢首屏') return;
  const valid = await formRef.value?.validate().catch(() => false); if (!valid) return;
  if (activeTool.value === '素材追投' && !selectedVideos.value.length) { ElMessage.warning('请选择至少 1 条可追投视频'); return; }
  if (!requestKey.value) requestKey.value = crypto.randomUUID();
  submitting.value = true;
  try {
    const result = await createControlTask(props.plan.planId, { kind: activeTool.value, name: draft.value.taskName, budget: Number(draft.value.budget), hours: Number(draft.value.hours), materialIds: activeTool.value === '素材追投' ? [...selectedVideos.value] : [], requestKey: requestKey.value });
    submittedId.value = result.taskId;
    ElMessage.success(result.taskId.startsWith('mock_') ? '模拟任务已提交' : '调控任务已提交');
    await loadTasks();
  } catch { /* Retain the request key: an uncertain result must not create a second task. */ await loadTasks(); }
  finally { submitting.value = false; }
}
watch(videoTag, () => { videoPage.value = 1; });
watch(() => filteredVideos.value.length, count => { videoPage.value = Math.min(videoPage.value, Math.max(1, Math.ceil(count / 10))); });
watch(activeTool, () => { helpPopovers.value.forEach(popover => popover.hide()); submittedId.value = ''; requestKey.value = ''; nextTick(() => formRef.value?.clearValidate()); if (open.value && activeTool.value === '素材追投' && !videosLoading.value) void loadVideos(); });
watch(() => props.modelValue, value => {
  if (!value) { requestVersion++; videosLoading.value = false; return; }
  for (const tool of tools) Object.assign(drafts[tool.key]!, { budget: '', hours: '', roi: '', taskName: taskName(tool.key) });
  submittedId.value = ''; requestKey.value = ''; selectedVideos.value = []; videoKeyword.value = ''; videoTag.value = ''; videoPage.value = 1; videos.value = []; videosFailed.value = false;
  const today = new Date(); dateRange.value = [localDay(new Date(today.getTime() - 6 * 86400000)), localDay(today)];
  activeTool.value = tools.some(t => t.key === props.kind) ? props.kind : '一键起量';
  void loadTasks(); nextTick(() => { formRef.value?.clearValidate(); if (activeTool.value === '素材追投' && !videosLoading.value) void loadVideos(); });
}, { immediate: true });
onBeforeUnmount(() => { requestVersion++; });
</script>

<style scoped>
.control-history { margin-top: 24px; color: #7c7f99; font-size: 12px; }.control-history h3 { color: #1a1b2e; font-size: 14px; display: flex; justify-content: space-between; align-items: center; }.control-history > div { border-top: 1px solid #eeeff4; padding: 10px 0; display: flex; gap: 12px; flex-wrap: wrap; }.control-history strong { color: #4e5069; font-weight: 500; flex: 1; }.control-history small { width: 100%; }
.bolt-icon, .shield-icon, .tool-video-icon { width: 14px; height: 14px; flex-shrink: 0; fill: none; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; }.bolt-icon, .tool-video-icon { color: #7c7f99; }.active .bolt-icon, .active .tool-video-icon { color: #575cf6; }.shield-icon { margin-top: 4px; }.task-name-field :deep(.el-input__count-inner) { background: transparent; }
.material-date-filter { display: flex; align-items: center; border: 1px solid #e6e8f2; border-radius: 10px; padding-left: 10px; width: 310px; flex: 0 1 310px; height: 36px; box-sizing: border-box; }.material-date-filter > .el-icon { color: #7c7f99; margin-right: 6px; font-size: 12px; flex-shrink: 0; }.material-date-filter > span { color: #4e5069; font-size: 12px; white-space: nowrap; }.material-filters .material-date-filter :deep(.el-date-editor) { flex: 1 1 0; min-width: 0; width: 230px; min-height: 34px; height: 34px; background: transparent; border: 0; box-shadow: none; }.material-filters .material-date-filter :deep(.el-range__icon) { display: none; }
.tools-header { display: flex; gap: 8px; align-items: baseline; padding-right: 30px; min-width: 0; }.tools-header h2 { color: #1a1b2e; font-size: 18px; font-weight: 600; margin: 0; white-space: nowrap; }.tools-header > span { color: #7c7f99; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tools-cards { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 12px; }.tool-card { border: 1px solid #e6e8f2; border-radius: 14px; padding: 14px; display: flex; flex-direction: column; align-items: flex-start; }.tool-card.active { background: #f7f7ff; border-color: #575cf6; }.tool-pick { display: block; width: 100%; border: 0; padding: 0; background: transparent; text-align: left; cursor: pointer; }.tool-pick strong { display: flex; gap: 8px; align-items: center; color: #1a1b2e; font-weight: 600; font-size: 14px; }.tool-pick .el-icon { color: #7c7f99; font-size: 14px; }.active .tool-pick strong, .active .tool-pick .el-icon { color: #575cf6; }.tool-pick > span { display: block; font-size: 13px; line-height: 19px; margin-top: 9px; color: #7c7f99; }.tool-help { color: #575cf6; font-size: 12px; padding: 0; margin-top: 6px; background: none; border: 0; text-decoration: underline; cursor: pointer; line-height: 20px; }
.tools-form :deep(.el-form-item) { margin-bottom: 0; }.tools-form :deep(.el-form-item__label) { display: flex; align-items: center; margin-bottom: 10px; color: #1a1b2e; font-size: 14px; line-height: 20px; font-weight: 600; }.tools-form :deep(.el-form-item__label::before) { display: none; }.required-star { color: #ff4757; margin-left: 6px; font-weight: 400; }.label-help-button { display: inline-flex; align-items: center; padding: 0; margin: 0 6px; background: transparent; color: #a0a3ba; border: 0; cursor: help; font-size: 12px; }.tools-form :deep(.el-input__wrapper) { min-height: 46px; border-radius: 10px; }.budget-field :deep(.el-input__inner) { text-align: right; }.input-unit { color: #7c7f99; margin-left: 8px; font-size: 15px; }.tool-hint { color: #7c7f99; font-size: 13px; line-height: 20px; margin: 10px 0 0; }.tool-error { display: flex; align-items: baseline; gap: 6px; margin: 8px 0 0; color: #f04452; font-size: 13px; line-height: 20px; }.tool-error .el-icon { flex-shrink: 0; font-size: 12px; }.tools-form :deep(.is-error .el-input__wrapper) { box-shadow: 0 0 0 1px #ff4757 inset; }.duration-field, .screen-roi-field { margin-top: 28px; }.duration-stepper { display: flex; align-items: center; gap: 10px; width: 100%; }.duration-stepper > button { display: grid; place-items: center; width: 34px; height: 34px; border: 1px solid #e6e8f2; border-radius: 8px; color: #4e5069; background: #fff; cursor: pointer; font-size: 16px; }.duration-stepper > button:disabled { color: #b2b5c8; cursor: default; }.duration-stepper :deep(.el-input) { width: 132px; }.duration-stepper :deep(.el-input__inner) { text-align: center; }.duration-stepper > span { color: #4e5069; font-size: 13px; margin-left: 2px; }.task-name-field { margin-top: 28px; margin-bottom: 8px; }.task-name-field :deep(.el-input__wrapper) { box-shadow: none; background: #f5f6fa; min-height: 42px; }.task-name-field :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px #575cf6 inset; }.task-name-field :deep(.el-input__inner) { font-size: 14px; }.task-name-field :deep(.el-input__count) { font-size: 12px; color: #7c7f99; }
.roi-note, .guarantee-note { display: flex; align-items: flex-start; gap: 10px; padding: 14px 16px; border-radius: 14px; margin-top: 16px; font-size: 13px; line-height: 21px; }.roi-note { background: #f3f3ff; color: #4e5069; }.roi-note > .el-icon { color: #575cf6; margin-top: 3px; flex-shrink: 0; }.roi-note strong { color: #1a1b2e; font-weight: 600; }.roi-note p { margin: 0; }.guarantee-note { color: #079455; background: #edf9f3; }.guarantee-note .el-icon { margin-top: 4px; flex-shrink: 0; }.tool-submit-note { font-size: 12px; color: #7c7f99; margin-right: auto; }
.material-section h3 { margin: 0 0 14px; font-size: 13px; font-weight: 600; color: #1a1b2e; }.material-filters { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }.material-filters > :deep(.el-input) { flex: 1 1 210px; width: 210px; }.material-filters :deep(.el-date-editor) { width: 276px; height: 36px; flex: 0 1 276px; padding: 0 10px; }.material-filters :deep(.el-input__wrapper), .material-filters :deep(.el-select__wrapper) { min-height: 36px; padding: 0 10px; border-radius: 10px; }.material-filters :deep(.el-input__inner), .material-filters :deep(.el-range-input) { font-size: 12px; }.material-filters :deep(.el-select) { width: 100px; }.material-spec { display: flex; align-items: flex-start; gap: 6px; margin: 10px 0 14px; font-size: 11px; line-height: 18px; color: #7c7f99; }.material-spec .el-icon { flex-shrink: 0; color: #575cf6; margin-top: 3px; }.material-table-wrap { border: 1px solid #e6e8f2; border-radius: 14px; overflow: hidden; margin-bottom: 28px; }.material-table-scroll { overflow-x: auto; }.material-table { width: 100%; min-width: 620px; border-collapse: collapse; text-align: left; table-layout: fixed; font-size: 12px; }.material-table th { height: 34px; background: #f7f8fc; color: #7c7f99; font-size: 11px; font-weight: 400; padding: 0 6px; }.material-table th .el-icon { font-size: 10px; vertical-align: -1px; }.material-table td { padding: 16px 6px; color: #1a1b2e; vertical-align: middle; }.material-table td:first-child { padding-left: 14px; }.video-info { display: flex; gap: 12px; align-items: flex-start; }.video-cover { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 8px; background: #1a1b2e; color: #fff; font-size: 20px; flex-shrink: 0; }.video-cover :deep(.el-image) { width: 40px; height: 40px; border-radius: 8px; }.video-info > div { min-width: 0; }.video-info strong { display: block; font-size: 12px; font-weight: 400; line-height: 18px; overflow-wrap: anywhere; }.video-info div > span { display: block; color: #7c7f99; font-size: 11px; line-height: 14px; overflow-wrap: anywhere; }.video-review { display: inline-flex; gap: 4px; align-items: center; font-size: 12px; color: #7c7f99; line-height: 16px; }.video-review.rejected { color: #f04452; }.material-empty { text-align: center; height: 68px; }.material-empty p { color: #7c7f99; margin: 0; }.material-pager { display: flex; gap: 10px; align-items: center; font-size: 11px; color: #7c7f99; padding: 10px 14px 12px; flex-wrap: wrap; }.material-pager > span:first-child { margin-right: auto; }.material-pager :deep(.el-pagination) { font-size: 11px; --el-pagination-button-width: 26px; --el-pagination-button-height: 28px; }.material-pager :deep(.el-pagination__jump) { margin-left: 0; font-size: 11px; }.material-pager :deep(.el-pagination__editor.el-input) { width: 46px; }.material-pager :deep(.el-input__wrapper) { min-height: 30px; padding: 0 6px; }.material-pager :deep(.el-pagination .el-input__inner) { font-size: 12px; }.material-pager :deep(.el-pagination__goto) { font-size: 0; }.material-pager :deep(.el-pagination__goto::after) { content: '跳转'; font-size: 11px; }
@media (max-width: 600px) { .tools-cards { grid-template-columns: 1fr; gap: 8px; }.tool-card { padding: 12px; }.tool-pick > span { margin-top: 6px; }.material-filters > :deep(.el-input), .material-filters :deep(.el-date-editor) { width: 100%; flex: 1 1 100%; }.duration-stepper { gap: 8px; }.duration-stepper :deep(.el-input) { width: 120px; }.tools-header > span { font-size: 11px; }.tool-submit-note { width: 100%; text-align: right; }.material-pager { gap: 6px; }.material-pager > span:first-child { width: 100%; } }
</style>
