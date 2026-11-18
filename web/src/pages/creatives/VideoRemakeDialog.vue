<template>
  <el-dialog :model-value="true" title="爆款复刻" width="min(760px, 94vw)" append-to-body
    :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="emit('close')">
    <div v-if="loading" role="status">正在加载…</div>
    <template v-else>
      <div class="remake-nav">
        <el-button link type="primary" :disabled="busy || panel === 'new'" @click="newTask">新建复刻</el-button>
        <el-button link type="primary" :disabled="busy || panel === 'history'" @click="history">任务记录</el-button>
      </div>
      <template v-if="panel === 'new'">
        <el-alert v-if="!capability?.ready" type="info" :closable="false"
          :title="capability?.subscribed === false ? '开通套餐后可使用爆款复刻' : '复刻服务暂不可用'" />
        <form v-else class="remake-form" @submit.prevent="start">
          <fieldset :disabled="busy">
            <label>参考视频
              <input type="file" accept=".mp4,.mov,.webm" @change="chooseFile($event, 'video')">
            </label>
            <span class="remake-hint">MP4、MOV、WebM，最长 120 秒，最大 200 MB</span>
            <label class="remake-check"><input v-model="rights" type="checkbox">我有权使用这个参考视频</label>
          </fieldset>
          <fieldset :disabled="busy">
            <div class="remake-row">
              <label>千川账户
                <select v-model="bindingId">
                  <option value="">请选择</option>
                  <option v-for="account in activeAccounts" :key="account.accountId" :value="account.accountId">{{ account.accountName }}</option>
                </select>
              </label>
              <label>抖音账号
                <select v-model="awemeId">
                  <option value="">请选择</option>
                  <option v-for="aweme in awemes" :key="aweme.awemeId" :value="aweme.awemeId">{{ aweme.name }}</option>
                </select>
              </label>
            </div>
            <label>目标商品
              <div class="remake-search">
                <input v-model="keyword" placeholder="搜索商品名称或 ID" @keydown.enter.prevent="loadProducts(1)">
                <el-button native-type="button" :loading="productsLoading" :disabled="!awemeId" @click="loadProducts(1)">搜索</el-button>
              </div>
              <select v-model="productId" :disabled="productsLoading">
                <option value="">请选择</option>
                <option v-for="product in products" :key="product.qcProductId" :value="product.qcProductId">{{ product.name }}</option>
              </select>
            </label>
            <div v-if="productPage > 1 || hasMore" class="remake-pager">
              <el-button link :disabled="productPage <= 1 || productsLoading" @click="loadProducts(productPage - 1)">上一页</el-button>
              <span>第 {{ productPage }} 页</span>
              <el-button link :disabled="!hasMore || productsLoading" @click="loadProducts(productPage + 1)">下一页</el-button>
            </div>
            <label>商品参考图
              <input type="file" accept=".jpg,.jpeg,.png,.webp" @change="chooseFile($event, 'image')">
            </label>
            <span v-if="selectedProduct?.imageUrl" class="remake-hint">未上传时使用所选商品的封面图</span>
            <label>商品事实
              <textarea v-model="facts" rows="3" maxlength="2412" placeholder="每行填写一条，例如材质、规格或实际使用方式"></textarea>
            </label>
          </fieldset>
          <fieldset :disabled="busy">
            <div class="remake-row">
              <label>方舟预置虚拟人像 ID
                <input v-model.trim="portraitId" maxlength="136" placeholder="asset-…">
              </label>
              <label>人物名称
                <input v-model.trim="portraitName" maxlength="80">
              </label>
            </div>
            <a class="remake-link" href="https://ark.volcengine.com/region:cn-beijing/docs/avatar-library" target="_blank" rel="noopener noreferrer">查看方舟虚拟人像库</a>
            <label class="remake-check"><input v-model="portraitTerms" type="checkbox">我有权使用所选预置人像，并已确认人像为成年人</label>
            <label>成片时长
              <select v-model.number="duration"><option :value="15">15 秒 · 竖屏</option><option :value="30">30 秒 · 竖屏</option></select>
            </label>
          </fieldset>
          <p v-if="uploadLabel" role="status">{{ uploadLabel }}</p>
          <div class="remake-actions"><el-button native-type="submit" type="primary" :loading="busy" :disabled="!ready">分析参考视频</el-button></div>
        </form>
      </template>
      <template v-else-if="panel === 'history'">
        <p v-if="!tasks.length" role="status">暂无复刻任务</p>
        <ul v-else class="remake-history">
          <li v-for="item in tasks" :key="item.id">
            <div><strong>{{ item.product }}</strong><small>{{ formatDate(item.createdAt) }}</small></div>
            <el-button link type="primary" @click="open(item.id)">{{ phaseLabel(item.phase) }} ›</el-button>
          </li>
        </ul>
      </template>
      <template v-else-if="task">
        <div class="remake-task"><strong>{{ task.product }}</strong><span role="status">{{ phaseLabel(task.phase) }}</span></div>
        <p v-if="task.error" class="remake-error" role="alert">{{ task.error }}</p>
        <form v-if="task.phase === 'REVIEW' && plan" class="remake-form" @submit.prevent="approve">
          <label>视频标题 <input v-model.trim="plan.title" maxlength="100" required></label>
          <fieldset v-for="(shot, index) in plan.shots" :key="index" :disabled="busy">
            <legend>镜头 {{ index + 1 }} · {{ shot.role }} · {{ shot.duration }} 秒</legend>
            <label>画面<textarea v-model.trim="shot.visual_prompt" rows="3" required minlength="10" maxlength="1800"></textarea></label>
            <label>旁白<textarea v-model.trim="shot.narration" :maxlength="Math.min(120, shot.duration * 5)" rows="2" required></textarea></label>
            <label>字幕<input v-model.trim="shot.caption" maxlength="24" required></label>
          </fieldset>
          <p v-if="capability?.balanceStatus === 'EMPTY'" class="remake-error">积分不足，充值后可继续生成。</p>
          <div class="remake-actions">
            <el-button native-type="submit" type="primary" :loading="busy"
              :disabled="!capability?.ready || capability.balanceStatus === 'EMPTY'">
              确认生成 · {{ capability?.points ?? '—' }} 积分
            </el-button>
          </div>
        </form>
        <template v-if="task.phase === 'SUCCEEDED'">
          <video v-if="task.videoUrl" class="remake-preview" :src="task.videoUrl" :poster="task.coverUrl" controls
            playsinline preload="metadata" @error="error = '视频暂时无法播放，可刷新成片后重试'"></video>
          <div class="remake-actions">
            <el-button v-if="error" @click="open(task.id)">刷新成片</el-button>
            <el-button type="primary" :loading="busy" :disabled="!task.videoUrl || !!task.assetId" @click="save">
              {{ task.assetId ? '已保存到素材库' : '保存到素材库' }}
            </el-button>
          </div>
        </template>
        <div v-if="task.phase === 'NEEDS_ATTENTION'" class="remake-actions"><el-button @click="open(task.id)">刷新状态</el-button></div>
      </template>
      <p v-if="error" class="remake-error" role="alert">{{ error }}</p>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useAdvertisingAccountsStore } from '@/stores/advertisingAccounts';
import { creationProducts, type CreationProduct } from '@/features/delivery/creationApi';
import {
  confirmRemake, createRemake, getRemake, getRemakeCapability, listRemakes, saveRemake, uploadRemakeMedia,
  type RemakeCapability, type RemakeTask, type RemakeUpload,
} from '@/features/creative/videoRemakeApi';

const emit = defineEmits<{ close: []; saved: [] }>();
const advertising = useAdvertisingAccountsStore();
const { accounts, accountId } = storeToRefs(advertising);
const activeAccounts = computed(() => accounts.value.filter(account => account.status === 'ACTIVE'));
const bindingId = ref(''), awemeId = ref(''), keyword = ref(''), productId = ref('');
const awemes = computed(() => activeAccounts.value.find(account => account.accountId === bindingId.value)?.awemes ?? []);
const products = ref<CreationProduct[]>([]), productsLoading = ref(false), productPage = ref(1), hasMore = ref(false);
const selectedProduct = computed(() => products.value.find(product => product.qcProductId === productId.value));
const reference = ref<RemakeUpload>(), productImage = ref<RemakeUpload>();
const portraitId = ref(''), portraitName = ref(''), duration = ref(15), facts = ref('');
const rights = ref(false), portraitTerms = ref(false);
const cleanFacts = computed(() => facts.value.split(/\r?\n/).map(line => line.trim()).filter(Boolean));
const ready = computed(() => Boolean(reference.value && selectedProduct.value && (productImage.value || selectedProduct.value.imageUrl)
  && portraitId.value && portraitName.value && rights.value && portraitTerms.value && cleanFacts.value.length));
const capability = ref<RemakeCapability>(), task = ref<RemakeTask>(), tasks = ref<RemakeTask[]>([]);
const plan = ref<RemakeTask['plan']>();
const panel = ref<'new' | 'history' | 'task'>('new');
const busy = ref(false), loading = ref(true), error = ref(''), uploadLabel = ref('');
let requestId = crypto.randomUUID();
let poll: ReturnType<typeof setTimeout> | undefined;
let productRequest = 0, taskRequest = 0, alive = true;
const inProgress = new Set(['CREATING', 'PLANNING', 'CONFIRMING', 'SENDING', 'GENERATING']);
const labels: Record<RemakeTask['phase'], string> = {
  CREATING: '等待分析', PLANNING: '正在分析和编排分镜', REVIEW: '待确认分镜',
  CONFIRMING: '正在确认生成', SENDING: '正在提交生成', GENERATING: '正在生成视频',
  SUCCEEDED: '成片已就绪', FAILED: '复刻失败', NEEDS_ATTENTION: '结果待核对',
};
const phaseLabel = (phase: RemakeTask['phase']) => labels[phase] ?? phase;
const formatDate = (date: string) => new Date(date).toLocaleString('zh-CN', { hour12: false });
const message = (cause: unknown) => cause instanceof Error ? cause.message : '操作未完成，请稍后重试';

async function loadProducts(page = 1) {
  const revision = ++productRequest;
  if (!bindingId.value || !awemeId.value) return;
  productsLoading.value = true;
  error.value = '';
  try {
    const pageSize = 10;
    const result = await creationProducts(bindingId.value, awemeId.value, {
      page, pageSize, keyword: keyword.value, tab: 'ALL', unpromoted: false,
    });
    if (revision !== productRequest || !alive) return;
    products.value = result.items;
    productPage.value = page;
    hasMore.value = result.hasMore || page * pageSize < result.total;
    if (!selectedProduct.value) productId.value = '';
  } catch (cause) {
    if (revision === productRequest && alive) error.value = message(cause);
  } finally {
    if (revision === productRequest && alive) productsLoading.value = false;
  }
}

watch(bindingId, () => {
  awemeId.value = awemes.value.find(aweme => aweme.isPrimary)?.awemeId ?? awemes.value[0]?.awemeId ?? '';
  // Uploads and selected products belong to the account active at submission.
  for (const upload of [reference.value, productImage.value]) {
    if (upload) { upload.requestId = crypto.randomUUID(); upload.uploadId = undefined; upload.assetId = undefined; upload.nextChunk = 0; }
  }
});
watch([bindingId, awemeId], () => {
  ++productRequest;
  products.value = [];
  productId.value = '';
  productsLoading.value = false;
  void loadProducts();
});

function chooseFile(event: Event, kind: 'video' | 'image') {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  const state = kind === 'video' ? reference : productImage;
  state.value = undefined;
  error.value = '';
  if (!file) return;
  if (!file.size || file.size > (kind === 'video' ? 200 : 10) * 1024 * 1024) {
    error.value = kind === 'video' ? '参考视频最大 200 MB' : '商品图最大 10 MB';
    input.value = '';
    return;
  }
  state.value = { file, requestId: crypto.randomUUID(), nextChunk: 0 };
}

function changePanel(next: typeof panel.value) {
  ++taskRequest;
  clearTimeout(poll);
  panel.value = next;
  error.value = '';
}

async function newTask() {
  changePanel('new');
  try { capability.value = await getRemakeCapability(); }
  catch (cause) { error.value = message(cause); }
}

async function history() {
  changePanel('history');
  const revision = taskRequest;
  try {
    const result = await listRemakes();
    if (alive && revision === taskRequest) tasks.value = result;
  } catch (cause) {
    if (alive && revision === taskRequest) error.value = message(cause);
  }
}

function show(result: RemakeTask) {
  task.value = result;
  if (result.phase === 'REVIEW' && result.plan) {
    plan.value = { title: result.plan.title, shots: result.plan.shots.map(shot => ({ ...shot })) };
    void getRemakeCapability().then(value => { if (alive) capability.value = value; }).catch(() => {});
  } else plan.value = undefined;
}

async function fetchTask(id: string, revision: number, keepPolling: boolean) {
  let again = keepPolling;
  try {
    const result = await getRemake(id);
    if (!alive || revision !== taskRequest) return;
    error.value = '';
    show(result);
    again = inProgress.has(result.phase);
  } catch (cause) {
    if (alive && revision === taskRequest) error.value = message(cause);
  }
  if (again && alive && revision === taskRequest) poll = setTimeout(() => void fetchTask(id, revision, true), 5000);
}

async function open(id: string, retry = false) {
  changePanel('task');
  if (task.value?.id !== id) task.value = undefined;
  await fetchTask(id, taskRequest, retry);
}

async function start() {
  if (!ready.value || !reference.value || busy.value) return;
  error.value = '';
  if (cleanFacts.value.length > 12 || cleanFacts.value.some(fact => Array.from(fact).length > 200)) {
    error.value = '请填写 1 至 12 条商品事实，每条最多 200 字。';
    return;
  }
  busy.value = true;
  try {
    const referenceId = await uploadRemakeMedia(reference.value, 'video', bindingId.value,
      percent => { uploadLabel.value = `上传参考视频 ${percent}%`; });
    const productImageId = productImage.value
      ? await uploadRemakeMedia(productImage.value, 'image', bindingId.value,
        percent => { uploadLabel.value = `上传商品图 ${percent}%`; }) : undefined;
    const result = await createRemake({ requestId, bindingId: bindingId.value, awemeId: awemeId.value,
      productId: productId.value, facts: cleanFacts.value, productImageId, referenceId,
      portraitId: portraitId.value, portraitName: portraitName.value, rightsConfirmed: rights.value,
      portraitTermsConfirmed: portraitTerms.value, durationSeconds: duration.value });
    requestId = crypto.randomUUID();
    show(result);
    await open(result.id, true);
  } catch (cause) { error.value = message(cause); }
  finally { busy.value = false; uploadLabel.value = ''; }
}

async function approve() {
  if (!task.value || !plan.value || !capability.value || busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    const result = await confirmRemake(task.value.id, plan.value.title, plan.value.shots, capability.value.points);
    show(result);
    await open(result.id, true);
  } catch (cause) { error.value = message(cause); }
  finally { busy.value = false; }
}

async function save() {
  if (!task.value || busy.value) return;
  const current = task.value;
  busy.value = true;
  error.value = '';
  try {
    const { assetId } = await saveRemake(current.id);
    current.assetId = assetId;
    emit('saved');
  } catch (cause) { error.value = message(cause); }
  finally { busy.value = false; }
}

onMounted(async () => {
  try {
    await Promise.all([advertising.load(), getRemakeCapability().then(value => { capability.value = value; })]);
    bindingId.value = activeAccounts.value.find(account => account.accountId === accountId.value)?.accountId
      ?? activeAccounts.value[0]?.accountId ?? '';
  } catch (cause) { error.value = message(cause); }
  finally { loading.value = false; }
});
onUnmounted(() => { alive = false; clearTimeout(poll); });
</script>

<style scoped>
.remake-nav,.remake-actions,.remake-pager { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.remake-actions { justify-content: flex-end; margin: 8px 0 0; }
.remake-form,.remake-form fieldset { display: grid; gap: 10px; min-width: 0; }
.remake-form fieldset { border: 1px solid var(--el-border-color-light); border-radius: 10px; padding: 12px; margin: 0; }
.remake-form legend { padding: 0 5px; font-weight: 600; }
.remake-row { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; }
.remake-form label { display: grid; gap: 5px; }
.remake-form :is(input:not([type=checkbox]),select,textarea) {
  border: 1px solid var(--el-border-color); border-radius: 6px; padding: 7px 9px; min-width: 0; width: 100%;
  background: var(--el-bg-color); color: var(--el-text-color-primary); font: inherit;
}
.remake-form .remake-check,.remake-search { display: flex; align-items: center; gap: 8px; }
.remake-check input { accent-color: var(--el-color-primary); }
.remake-hint,.remake-history small { color: var(--el-text-color-secondary); }
.remake-link { color: var(--el-color-primary); width: fit-content; }
.remake-history { list-style: none; margin: 0; padding: 0; }
.remake-history li,.remake-task { display: flex; justify-content: space-between; gap: 12px; padding: 10px 0; }
.remake-history li { border-bottom: 1px solid var(--el-border-color-light); }
.remake-history small { display: block; margin-top: 4px; }
.remake-error { color: var(--el-color-danger); margin-top: 8px; }
.remake-preview { display: block; width: min(300px,100%); max-height: 52vh; margin: 0 auto; background: #111; border-radius: 8px; }
@media (max-width: 580px) { .remake-row { grid-template-columns: 1fr; } }
</style>
