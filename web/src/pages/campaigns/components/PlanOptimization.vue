<template>
  <div class="monitor" v-loading="loading">
    <div class="heading"><div><h3>盯盘优化</h3><p>每5分钟查看真实投放数据，结合商品成本判断赚亏。后台服务持续运行时，关闭页面仍继续盯盘。</p></div><router-link :to="`/monitoring?planId=${planId}&tab=history`">全店中心与历史 →</router-link></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="notice" />
    <template v-if="view && form">
      <el-alert v-if="!view.available" title="当前计划暂无法开启真实盯盘" :description="view.unavailableReason" type="warning" :closable="false" show-icon class="notice" />
      <el-alert v-if="view.error" title="本轮采集失败，系统将自动重试" :description="view.error" type="warning" :closable="false" show-icon class="notice" />
      <el-alert v-if="view.holdReason" title="自动执行已暂停，仍继续盯盘" :description="view.holdReason" type="warning" :closable="false" show-icon class="notice" />
      <div class="status-line"><el-switch v-model="form.enabled" active-text="开启盯盘优化" :disabled="!view.available && !form.enabled" /><span>保存后生效</span><el-button :loading="checking" :disabled="saving || !view.policy.enabled || !view.available || dirty" @click="check">立即检查</el-button><el-button :icon="Refresh" aria-label="刷新盯盘状态" @click="load(false)">刷新状态</el-button></div>
      <p class="status-description">{{ statusText }} · 最近检查：{{ time(view.lastCheck) }} · 最近成功：{{ time(view.lastSuccess) }}</p>
      <el-alert v-if="view.result && view.stale" title="以下为历史判断，数据已过期；恢复采集后会更新" type="warning" :closable="false" class="notice" />
      <div class="metrics">
        <div><span>预估利润（扣成本和广告费，元）</span><strong :class="{ loss: Number(view.result?.estimated_contribution_profit) < 0 }">{{ money(view.result?.estimated_contribution_profit) }}</strong></div>
        <div><span>保本所需回报（ROI）</span><strong>{{ money(view.result?.break_even_net_roi) }}</strong></div>
        <div><span>用于本次判断的广告费（元）</span><strong>{{ money(view.result?.spend) }}</strong></div>
        <div><span>今日消耗（元）</span><strong>{{ money(view.result?.daily_spend) }}</strong></div>
      </div>
      <p class="caption">{{ view.result?.profit_note || '商品成本资料与订单结果齐全后，显示扣除成本和广告费的预估利润。' }}<span v-if="view.result?.window_start"> 依据：{{ time(view.result.window_start) }} ～ {{ time(view.result.window_end) }}</span></p>
      <h4>判断与建议</h4>
      <ul v-if="view.result?.reasons.length" class="reasons"><li v-for="reason in view.result.reasons" :key="reason">{{ reason }}</li></ul>
      <ul v-if="view.result?.tactics_issues?.length" class="reasons"><li v-for="reason in view.result.tactics_issues" :key="reason">{{ reason }}</li></ul>
      <div v-for="(item, index) in view.result?.recommendations || []" :key="index" class="recommendation"><strong>{{ item.title }}</strong><p>{{ item.evidence }}</p><el-button link type="primary" @click="emit('data')">查看运营数据</el-button><router-link :to="`/campaigns/${planId}/edit`">进入计划修改 →</router-link></div>
      <el-empty v-if="!view.result?.recommendations.length" :description="view.result ? '当前没有调控建议' : '开启并保存后，系统开始采集与判断'" :image-size="55" />
      <p class="caption">{{ view.policy.mode === 'AUTO_LIMITED' ? '自动操作前会检查数据是否最新、调整后的观察时间、每日次数和预算限制。执行结果可在全店中心查看。' : '“只看建议”会持续采集数据并给出建议。切换为自动调控并保存后，系统执行您勾选允许的操作。' }}</p>
      <h4>素材生成与试投记录</h4>
      <details v-if="view.result?.creative_direction_assessments?.length" class="creative-directions"><summary>素材方向与效果（{{ view.result.creative_direction_assessments.length }}）</summary><div class="direction-scroll"><table><thead><tr><th>商品 / 素材</th><th>场景</th><th>风格</th><th>展示角度</th><th>判断</th><th>成熟贡献利润</th></tr></thead><tbody><tr v-for="item in view.result.creative_direction_assessments" :key="`${item.product_id}:${item.entity_id}`"><td>{{ item.product_id }} / {{ item.entity_id }}</td><td>{{ item.creative_profile.scenes?.join('、') || '待分析' }}</td><td>{{ item.creative_profile.visual_style || '待分析' }}</td><td>{{ item.creative_profile.creative_angle || '待分析' }}</td><td>{{ { LOSS: item.creative_profile.source === 'VISUAL_ANALYSIS' && (item.creative_profile.confidence || 0) >= .6 ? '亏损素材，规避已确认方向' : '素材亏损，方向待分析', QUALIFIED: '表现良好，保留方向', OBSERVING: '样本不足，继续观察' }[item.status as 'LOSS' | 'QUALIFIED' | 'OBSERVING'] || item.status }}</td><td>{{ money(item.estimated_contribution_profit) }}<br><small>{{ item.mature_days }} 个成熟日</small></td></tr></tbody></table></div><p class="caption">效果按素材的成熟数据判断；场景、风格和角度用于规划下一次素材，不代表已证明单因素因果关系。</p></details>
      <el-alert v-if="creativeError" :title="creativeError" type="warning" :closable="false" />
      <el-empty v-if="!creativeJobs.length" description="暂无生成记录；启用素材生成并保存授权后，按诊断创建任务" :image-size="45" />
      <article v-for="job in creativeJobs" :key="job.id" class="creative-job">
        <div class="status-line"><strong>{{ formats[job.body.strategy.target] || job.body.strategy.target }} · {{ jobStatuses[job.status] || job.status }}</strong><span>{{ time(job.createdAt) }} · 商品 {{ job.productId }}</span></div>
        <p>{{ job.body.strategy.hypothesis }}</p><p class="caption">{{ job.body.strategy.evidence_summary }}<br>表达方向：{{ job.body.strategy.visual_style || '依据商品事实规划' }} · 生成预算预留 ¥{{ job.reservedCost }}（非实际服务账单）</p>
        <p v-if="job.body.directionPlan" class="caption">生成前选定方向：场景 {{ job.body.directionPlan.creative_profile.scenes.join('、') || '尚未选定' }}；风格 {{ job.body.directionPlan.creative_profile.visual_style || '尚未选定' }}；商品展示角度 {{ job.body.directionPlan.creative_profile.creative_angle || '尚未选定' }}。<br>{{ job.body.directionPlan.rationale }}</p>
        <p v-if="job.body.directionPlan?.warnings?.length" class="caption">{{ job.body.directionPlan.preflight_status === 'ADVISORY' ? '已采用相对更合适的方案继续生成' : '已采用备用方向继续生成' }}：{{ job.body.directionPlan.warnings.join('；') }}。</p>
        <p v-if="job.body.strategy.scene_strategy" class="caption">追加时规避：场景 {{ job.body.strategy.scene_strategy.avoid_scenes.join('、') || '暂无已证实失败场景' }}；风格 {{ job.body.strategy.scene_strategy.avoid_styles.join('、') || '暂无' }}；角度 {{ job.body.strategy.scene_strategy.avoid_angles.join('、') || '暂无' }}。</p>
        <p v-if="job.body.strategy.baseline_material_id" class="caption">参考素材 {{ job.body.strategy.baseline_material_id }}；重点验证 {{ job.body.strategy.test_dimension || '新的需求表达' }}，不表示严格单变量实验。</p>
        <div v-if="job.body.material" class="creative-preview">
          <video v-if="job.body.material.type === 'VIDEO' && job.body.material.previewUrl" :src="job.body.material.previewUrl" :poster="job.body.material.posterUrl" controls preload="none" />
          <el-image v-for="url in job.body.material.imageUrls || []" :key="url" :src="url" :preview-src-list="job.body.material.imageUrls" fit="contain" preview-teleported />
          <div><strong>{{ job.body.material.title }}</strong><p>{{ job.body.material.text }}</p></div>
        </div>
        <p v-if="job.body.trial_summary" class="caption">完整成熟日 {{ job.body.trial_summary.days }} 天 · 曝光 {{ job.body.trial_summary.impressions }} · 点击 {{ job.body.trial_summary.clicks }} · 成交 {{ job.body.trial_summary.orders }} · 消耗 ¥{{ job.body.trial_summary.spend }} · 预估贡献利润 ¥{{ job.body.trial_summary.profit }}（扣除预留生成费用）</p>
        <p v-if="job.status === 'TRIAL'" class="caption">平台自主分配流量；样本不足继续观察。没有曝光不代表素材无效。</p>
        <el-alert v-if="job.body.error" :title="job.body.error" type="warning" :closable="false" />
        <div v-if="job.status === 'REVIEW'" class="status-line"><el-button type="primary" :loading="reviewing === job.id" :disabled="dirty || reviewing !== ''" @click="review(job, true)">确认发布并试投</el-button><el-button :disabled="reviewing !== ''" @click="review(job, false)">拒绝发布</el-button><span>仅补充到当前商品，并同步关联转投计划</span></div>
        <el-button v-else-if="['QUEUED','GENERATING','UNKNOWN','SYNCING','PLATFORM_REVIEW','TRIAL'].includes(job.status)" :disabled="reviewing !== ''" @click="review(job, false)">停止本批后续发布与试投管理</el-button>
      </article>
      <h4>花费和亏损限制</h4>
      <el-radio-group v-model="form.mode"><el-radio-button value="OBSERVE">只看建议</el-radio-button><el-radio-button value="AUTO_LIMITED">自动调控</el-radio-button></el-radio-group>
      <p class="caption">{{ form.mode === 'OBSERVE' ? '只看建议：持续盯盘并记录建议，操作由您决定。' : '自动调控：保存后，按下方勾选的范围和金额限制执行。' }}</p>
      <el-form label-position="top" class="policy-form">
        <OptimizationField field="dailySpendLimit"><el-input-number v-model="form.dailySpendLimit" :min="0.01" :max="999999999" :precision="2" /></OptimizationField>
        <OptimizationField field="maxLoss"><el-input-number v-model="form.maxLoss" :min="0.01" :max="999999999" :precision="2" /></OptimizationField>
      </el-form>
      <details class="advanced-settings">
        <summary>进阶设置：用多少数据判断投放效果</summary>
        <p class="caption">至少花 ¥{{ money(form.minSpend) }} · 加大投放至少成交 {{ form.minOrders }} 单 · 参考最近 {{ form.persistenceWindows }} 个完整小时 · 等订单更新 {{ form.conversionDelayMinutes }} 分钟</p>
        <el-form label-position="top" class="policy-form">
        <OptimizationField field="minSpend"><el-input-number v-model="form.minSpend" :min="10" :max="999999999" :precision="2" /></OptimizationField>
        <OptimizationField field="minOrders"><el-input-number v-model="form.minOrders" :min="1" :max="10000" :precision="0" /></OptimizationField>
        <OptimizationField field="persistenceWindows"><el-input-number v-model="form.persistenceWindows" :min="3" :max="8" :precision="0" /></OptimizationField>
        <OptimizationField field="conversionDelayMinutes"><el-input-number v-model="form.conversionDelayMinutes" :min="60" :max="1440" :precision="0" /></OptimizationField>
        </el-form>
      </details>
      <template v-if="form.mode === 'AUTO_LIMITED'">
        <h4>允许系统自动做什么</h4>
        <p class="caption">首次自动增加投放前，请在全店中心设置该广告账户下所有计划的日预算总上限。您手动修改计划后，系统会暂停自动操作；核对后可在全店中心重新授权。</p>
        <div class="permissions"><el-checkbox v-model="form.automation.budget">自动增减日预算</el-checkbox><el-checkbox v-model="form.automation.roi" :disabled="view.bidType !== 'CUSTOM'">自动调整目标回报（ROI）</el-checkbox><el-checkbox v-model="form.automation.pause">达到花费或亏损限额时自动暂停</el-checkbox><el-checkbox v-model="form.automation.resumeNextDay">因每天花费达限暂停，第二天自动恢复</el-checkbox></div>
        <p v-if="form.automation.resumeNextDay" class="caption">只恢复由灵投每日限额规则暂停的计划。人工暂停及亏损止损不会自动恢复；转投关联计划运行时也不恢复。</p>
        <el-form label-position="top" class="policy-form">
          <OptimizationField field="minBudget"><el-input-number v-model="form.automation.minBudget" :min="view.bidType === 'CUSTOM' ? COST_DAILY_BUDGET_MINIMUM : 50" :precision="2" /></OptimizationField>
          <OptimizationField field="maxBudget"><el-input-number v-model="form.automation.maxBudget" :min="form.automation.minBudget" :max="999999999" :precision="2" /></OptimizationField>
          <OptimizationField field="maxStepPercent"><el-input-number v-model="form.automation.maxStepPercent" :min="1" :max="20" :precision="2" /></OptimizationField>
          <OptimizationField v-if="view.bidType === 'CUSTOM'" field="minRoi"><el-input-number v-model="form.automation.minRoi" :min=".01" :max="100" :precision="2" /></OptimizationField>
          <OptimizationField v-if="view.bidType === 'CUSTOM'" field="maxRoi"><el-input-number v-model="form.automation.maxRoi" :min="form.automation.minRoi" :max="100" :precision="2" /></OptimizationField>
          <OptimizationField field="cooldownMinutes"><el-input-number v-model="form.automation.cooldownMinutes" :min="60" :max="1440" :precision="0" /></OptimizationField>
          <OptimizationField field="maxActionsPerDay"><el-input-number v-model="form.automation.maxActionsPerDay" :min="1" :max="10" :precision="0" /></OptimizationField>
          <OptimizationField v-if="form.automation.resumeNextDay" field="resumeHour"><el-input-number v-model="form.automation.resumeHour" :min="0" :max="23" :precision="0" /> 时</OptimizationField>
        </el-form>
      </template>
      <h4>素材与商品怎么管理</h4>
      <p class="caption">勾选允许的操作并保存后，在自动调控模式下执行。商品只支持自动移除；系统会保留设定数量的商品和素材，并同步处理转投关联计划。移除的是计划中的投放关联，店铺商品和素材中心文件保留。</p>
      <div class="permissions"><el-checkbox v-model="form.tactics.boost" :disabled="view.bidType !== 'CUSTOM'">追加预算给计划起量（一键起量）</el-checkbox><el-checkbox v-model="form.tactics.materialFollow" :disabled="view.bidType !== 'CUSTOM'">追加预算给优质素材（素材追投）</el-checkbox><el-checkbox v-model="form.creativeGeneration.enabled">素材不足或效果变差时生成新素材</el-checkbox><el-checkbox v-model="form.tactics.materialRemove">从计划移除亏损素材</el-checkbox><el-checkbox v-model="form.tactics.productRemove">从计划移除亏损商品</el-checkbox></div>
      <el-form label-position="top" class="policy-form">
        <OptimizationField v-if="form.tactics.boost || form.tactics.materialFollow" field="taskBudget"><el-input-number v-model="form.tactics.taskBudget" :min="100" :precision="2" /></OptimizationField>
        <OptimizationField v-if="form.tactics.boost || form.tactics.materialFollow" field="taskHours"><el-input-number v-model="form.tactics.taskHours" :min=".5" :max="24" :step=".5" /></OptimizationField>
        <OptimizationField v-if="form.tactics.boost || form.tactics.materialFollow" field="extraBudgetPerDay"><el-input-number v-model="form.tactics.extraBudgetPerDay" :min="form.tactics.taskBudget" :precision="2" /></OptimizationField>
        <OptimizationField field="entityDays"><el-input-number v-model="form.tactics.entityDays" :min="2" :max="7" :precision="0" /></OptimizationField>
        <OptimizationField field="retainProducts"><el-input-number v-model="form.tactics.retainProducts" :min="1" :max="10" :precision="0" /></OptimizationField>
        <OptimizationField field="retainMaterials"><el-input-number v-model="form.tactics.retainMaterials" :min="1" :max="100" :precision="0" /></OptimizationField>
      </el-form>
      <template v-if="form.creativeGeneration.enabled">
        <p class="caption">系统根据素材数量、展示、点击和成交表现选择视频、图文或图片，并尝试更合适的场景和展示角度。每个商品24小时最多生成1条；多条素材都卖不动时，先检查商品价格、规格和购买页面。生成费用与追加广告预算分别限额。</p>
        <el-checkbox v-model="form.creativeGeneration.autoPublish">新素材生成后自动加入当前计划试投</el-checkbox>
        <p class="caption">{{ form.creativeGeneration.autoPublish ? '保存后授权自动发布。发布后先等待平台审核，再单独跟踪试投，不将新素材视为已有盈利素材。' : '生成完成后，需要在上方记录中审核并确认发布。' }}</p>
        <el-checkbox-group v-model="form.creativeGeneration.formats"><el-checkbox value="video">视频</el-checkbox><el-checkbox value="image_text">图文</el-checkbox><el-checkbox value="product_card">商品卡图片</el-checkbox></el-checkbox-group>
        <el-form label-position="top" class="policy-form">
          <OptimizationField field="minMaterials"><el-input-number v-model="form.creativeGeneration.minMaterials" :min="1" :max="20" :precision="0" /></OptimizationField>
          <OptimizationField field="maxPending"><el-input-number v-model="form.creativeGeneration.maxPending" :min="1" :max="2" :precision="0" /></OptimizationField>
          <OptimizationField field="dailyCostLimit"><el-input-number v-model="form.creativeGeneration.dailyCostLimit" :min=".01" :precision="2" /></OptimizationField>
          <OptimizationField field="perAssetCostLimit"><el-input-number v-model="form.creativeGeneration.perAssetCostLimit" :min=".01" :precision="2" /></OptimizationField>
          <OptimizationField field="trialLossLimit"><el-input-number v-model="form.creativeGeneration.trialLossLimit" :min=".01" :precision="2" /></OptimizationField>
          <OptimizationField field="minImpressions"><el-input-number v-model="form.creativeGeneration.minImpressions" :min="100" :max="10000000" :precision="0" /></OptimizationField>
          <OptimizationField field="minClicks"><el-input-number v-model="form.creativeGeneration.minClicks" :min="10" :max="100000" :precision="0" /></OptimizationField>
        </el-form>
        <el-form label-position="top"><OptimizationField field="brandStyle"><el-input v-model="form.creativeGeneration.brandStyle" type="textarea" maxlength="300" show-word-limit placeholder="例如：自然生活场景、克制表达、强调真实细节" /></OptimizationField><OptimizationField field="forbiddenClaims"><el-input v-model="form.creativeGeneration.forbiddenClaims" type="textarea" maxlength="500" show-word-limit placeholder="例如：未经核实的功效、销量、规格和价格承诺" /></OptimizationField></el-form>
      </template>
      <p class="caption">计划起量参考最近完整小时的利润；素材追投和移除素材、商品参考最近完整日的表现，并等待订单数据更新。数据不足、已有调控任务、预算超限或还在调整后观察期时继续盯盘，原因可在建议与历史中查看。</p>
      <h4>商品卖出后能留下多少钱</h4>
      <p class="caption">先填写商品收入扣除货品、物流、平台佣金等经营成本后剩余的比例，广告费由系统另外扣除。例如：收入100元、经营成本70元，填写30%。再根据历史情况预留后续退款。多商品计划按最低有效比例保守估算，转投关联计划共用这些资料。</p>
      <el-table :data="economics" empty-text="尚未获得该计划的商品绑定，请先同步计划信息">
        <el-table-column label="商品" min-width="200"><template #default="{ row }"><div>{{ productName(row.productId) }}</div><small>ID：{{ row.productId }}</small></template></el-table-column>
        <el-table-column label="扣除经营成本后剩余比例（%）" min-width="240"><template #header><OptimizationLabel field="contributionMarginRate" /></template><template #default="{ row }"><el-input-number v-model="row.marginPct" :min="0" :max="100" :precision="2" :step="1" /></template></el-table-column>
        <el-table-column label="后续退款预留比例（%）" min-width="210"><template #header><OptimizationLabel field="refundReserveRate" /></template><template #default="{ row }"><el-input-number v-model="row.reservePct" :min="0" :max="99.99" :precision="2" :step="1" /></template></el-table-column>
        <el-table-column label="这些成本和退款比例已核对" min-width="210"><template #header><OptimizationLabel field="verified" /></template><template #default="{ row }"><el-checkbox v-model="row.verified">已核实</el-checkbox></template></el-table-column>
      </el-table>
      <div class="save-line"><el-button type="primary" :loading="saving" :disabled="checking" @click="save">保存盯盘设置</el-button><el-button :disabled="saving || checking" @click="reloadSettings">重新加载设置</el-button><span v-if="dirty">存在未保存的设置</span></div>
    </template>
    <el-button v-else-if="!loading" @click="load(true)">重新加载</el-button>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import OptimizationField from './OptimizationField.vue';
import OptimizationLabel from './OptimizationLabel.vue';
import { COST_DAILY_BUDGET_MINIMUM } from '@/features/delivery/budgetValidation';
import { checkOptimization, getOptimization, saveOptimization, defaultAutomation, defaultTactics, defaultCreativeGeneration, getCreativeJobs, reviewCreativeJob, type CreativeJob, type OptimizationPolicy, type OptimizationView } from '@/features/delivery/optimizationApi';
const props = defineProps<{ planId: string; products?: { id: string; name: string }[] }>();
const emit = defineEmits<{ data: []; saved: [] }>();
const view = ref<OptimizationView>(), form = ref<OptimizationPolicy>();
const creativeJobs = ref<CreativeJob[]>([]), creativeError = ref(''), reviewing = ref('');
const formats: Record<string,string> = { video: '视频', image_text: '图文', product_card: '商品卡图片' };
const jobStatuses: Record<string,string> = { QUEUED: '待生成', GENERATING: '生成中', UNKNOWN: '生成结果待核查', REVIEW: '待确认发布', SYNCING: '同步账户 / 等待发布条件', PUBLISHING: '发布核查中', PLATFORM_REVIEW: '等待平台审核', TRIAL: '试投观察中', QUALIFIED: '试投达标', FAILED: '未完成', CANCELLED: '已停止', REMOVING: '亏损素材移除中', REMOVED: '亏损素材已移除', LOSS_RETAINED: '达到亏损线，需人工处理', UNTESTED: '验证期结束未达标' };
const economics = ref<{ productId: string; marginPct: number; reservePct: number; verified: boolean }[]>([]);
const loading = ref(true), saving = ref(false), checking = ref(false), error = ref('');
let disposed = false, timer: ReturnType<typeof setTimeout> | undefined, inFlight = false;
let saved = '', savedVersion = 0, requestRevision = 0;
const body = () => ({ ...form.value!, tactics: { ...form.value!.tactics, materialAdd: form.value!.creativeGeneration.enabled, candidatePlanIds: [] }, economics: economics.value.map(e => ({ productId: e.productId, contributionMarginRate: Number((e.marginPct / 100).toFixed(8)), refundReserveRate: Number((e.reservePct / 100).toFixed(8)), verified: e.verified })) });
const dirty = computed(() => !!form.value && JSON.stringify(body()) !== saved);
const statusText = computed(() => !view.value?.policy.enabled ? '未开启' : view.value.error ? '采集异常，等待重试' : view.value.stale ? '等待最新判断' : view.value.result?.state === 'READY' ? '数据已满足判断条件' : view.value.result?.state === 'PAUSED' ? '计划未投放，持续观察' : '等待订单数据更新或补齐商品成本');
const time = (value?: string | null) => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '—';
const money = (value?: string | number | null) => value == null ? '—' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const productName = (id: string) => props.products?.find(p => p.id === id)?.name || '计划推广商品';
const message = (err: any) => err?.response?.data?.message || err?.message || '盯盘状态暂不可用';
function apply(value: OptimizationView, reset: boolean) {
  view.value = value;
  if (reset) {
    form.value = JSON.parse(JSON.stringify(value.policy));
    form.value!.automation ||= defaultAutomation();
    form.value!.tactics ||= defaultTactics();
    form.value!.creativeGeneration ||= defaultCreativeGeneration();
    form.value!.tactics.productAdd = false;
    form.value!.tactics.candidatePlanIds = [];
    if (value.bidType === 'CUSTOM' && form.value!.automation.minBudget < COST_DAILY_BUDGET_MINIMUM) form.value!.automation.minBudget = COST_DAILY_BUDGET_MINIMUM;
    economics.value = value.productIds.map(productId => { const e = value.policy.economics.find(p => p.productId === productId); return { productId, marginPct: Number(((e?.contributionMarginRate || 0) * 100).toFixed(4)), reservePct: Number(((e?.refundReserveRate || 0) * 100).toFixed(4)), verified: e?.verified || false }; });
    saved = JSON.stringify(body());
    savedVersion = value.version;
  }
}
async function load(reset: boolean) {
  if (inFlight || disposed || saving.value || checking.value) return;
  inFlight = true;
  const revision = requestRevision;
  try { const value = await getOptimization(props.planId); if (!disposed && revision === requestRevision) { apply(value, reset || (!dirty.value && value.version !== savedVersion)); error.value = ''; }
    try { const records = await getCreativeJobs(props.planId); if (!disposed) { creativeJobs.value = records; creativeError.value = ''; } } catch (err) { if (!disposed) creativeError.value = message(err); }
  }
  catch (err) { if (!disposed && revision === requestRevision) error.value = message(err); }
  finally { inFlight = false; if (!disposed) loading.value = false; }
}
async function save() {
  if (form.value?.enabled && form.value.mode === 'AUTO_LIMITED') {
    try { await ElMessageBox.confirm('保存后，灵投将执行勾选的调控操作。启用素材生成会按上限预留生成费用；自动发布开关决定生成后是否直接加入原计划试投。素材增删和商品删除同步转投关联计划。请确认成本、生成及试投亏损上限。', '授权自动调控', { confirmButtonText: '确认授权并保存', cancelButtonText: '取消' }); } catch { return; }
  }
  requestRevision++;
  saving.value = true;
  try { const value = await saveOptimization(props.planId, body(), savedVersion); if (!disposed) { apply(value, true); error.value = ''; ElMessage.success('盯盘设置已保存'); emit('saved'); } }
  catch (err) { if (!disposed) error.value = message(err); }
  finally { saving.value = false; }
}
async function check() {
  requestRevision++;
  checking.value = true;
  try { const value = await checkOptimization(props.planId); if (!disposed) { apply(value, false); error.value = ''; } }
  catch (err) { if (!disposed) error.value = message(err); }
  finally { checking.value = false; }
}
async function reloadSettings() {
  if (dirty.value) { try { await ElMessageBox.confirm('重新加载会舍弃未保存的设置，是否继续？', '重新加载盯盘设置', { confirmButtonText: '重新加载', cancelButtonText: '取消' }); } catch { return; } }
  await load(true);
}
async function review(job: CreativeJob, approved: boolean) {
  if (approved) { try { await ElMessageBox.confirm('将此生成素材补充到当前商品，并同步转投关联计划，发布后开始试投观察。是否确认？', '确认发布生成素材', { confirmButtonText: '确认发布', cancelButtonText: '取消' }); } catch { return; } }
  else if (job.status !== 'REVIEW') { try { await ElMessageBox.confirm('停止后续发布与自动试投管理。已生成文件和已发布素材绑定保留，正在执行的生成请求可能仍会计费；计划启停状态保持当前设置。', '停止本批素材管理', { confirmButtonText: '停止后续管理', cancelButtonText: '取消' }); } catch { return; } }
  reviewing.value = job.id;
  try { await reviewCreativeJob(props.planId, job.id, approved); creativeJobs.value = await getCreativeJobs(props.planId); ElMessage.success(approved ? '已确认，后台继续同步与发布检查' : job.status === 'REVIEW' ? '已拒绝发布' : '已停止本批后续管理'); } catch (err) { creativeError.value = message(err); } finally { reviewing.value = ''; }
}
async function poll() { if (disposed) return; if (!saving.value && !checking.value) await load(false); if (!disposed) timer = setTimeout(poll, 30000); }
onMounted(async () => { await load(true); if (!disposed) timer = setTimeout(poll, 30000); });
onBeforeUnmount(() => { disposed = true; if (timer) clearTimeout(timer); });
</script>
<style scoped>
.advanced-settings{padding:14px 16px;margin:2px 0 20px;border:1px solid var(--el-border-color);border-radius:9px}.advanced-settings summary{cursor:pointer;font-size:13px;color:var(--el-color-primary)}.advanced-settings .policy-form{margin-top:16px}.permissions{display:flex;flex-wrap:wrap;gap:10px 22px;margin:14px 0}.permissions :deep(.el-checkbox){height:auto;margin-right:0;align-items:flex-start}.permissions :deep(.el-checkbox__label){white-space:normal;line-height:1.6}.permissions :deep(.el-checkbox__input){margin-top:4px}
.creative-directions{margin:16px 0;font-size:12px}.creative-directions summary{cursor:pointer;color:var(--el-color-primary)}.direction-scroll{overflow:auto}.creative-directions table{width:100%;min-width:700px;border-collapse:collapse}.creative-directions th,.creative-directions td{padding:12px;text-align:left;border-bottom:1px solid var(--el-border-color);min-width:100px}
.creative-job{border:1px solid var(--el-border-color);border-radius:10px;padding:16px;margin:14px 0}.creative-job>p{font-size:13px;line-height:1.7}.creative-preview{display:flex;flex-wrap:wrap;gap:12px;margin:12px 0}.creative-preview video{max-width:100%;width:240px;max-height:320px;background:#111}.creative-preview .el-image{width:130px;height:170px}.creative-preview>div{flex:1;min-width:180px;overflow-wrap:anywhere}
.heading,.status-line,.save-line{display:flex;align-items:center;gap:14px;flex-wrap:wrap}.heading{justify-content:space-between}.heading h3{margin:0}.heading p,.caption,.status-description,.status-line>span,.save-line>span{font-size:12px;color:var(--el-text-color-secondary);line-height:1.8}.notice{margin:16px 0}.status-line{margin-top:22px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-top:20px}.metrics>div{padding:20px;background:var(--el-fill-color-light);border-radius:10px}.metrics span{display:block;font-size:12px;color:var(--el-text-color-secondary);margin-bottom:12px}.metrics strong{font-size:23px}.metrics .loss{color:var(--el-color-danger)}h4{margin:28px 0 14px}.policy-form{display:grid;grid-template-columns:repeat(3,1fr);gap:0 20px}.reasons{font-size:13px;line-height:1.9;color:var(--el-text-color-secondary);padding-left:22px}.recommendation{padding:16px;border:1px solid var(--el-border-color);border-radius:9px;margin:12px 0}.recommendation p{font-size:13px;color:var(--el-text-color-secondary)}.recommendation a{color:var(--el-color-primary);font-size:13px;text-decoration:none;margin-left:18px}.save-line{margin-top:20px}small{color:var(--el-text-color-secondary)}@media(max-width:850px){.metrics,.policy-form{grid-template-columns:repeat(2,1fr)}}@media(max-width:500px){.metrics,.policy-form{grid-template-columns:1fr}}
</style>
