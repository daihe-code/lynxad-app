<template>
  <el-dialog :model-value="true" title="爆款复刻" width="min(1080px, calc(100vw - 32px))" append-to-body
    class="remake-dialog" align-center :close-on-click-modal="false" :close-on-press-escape="!busy && !rightsAgreementOpen" :show-close="!busy"
    @close="emit('close')">
    <div class="remake-workspace">
      <div class="remake-toolbar">
        <div class="remake-nav" role="group" aria-label="复刻工作台">
          <button type="button" :aria-pressed="panel === 'new'" :disabled="busy" @click="newTask">
            <AssetIcon name="plus" /> 新建复刻
          </button>
          <button type="button" :aria-pressed="panel !== 'new'" :disabled="busy" @click="history">
            <AssetIcon name="clock" /> 任务记录
          </button>
        </div>
        <router-link v-if="capability?.availablePoints != null && !busy" class="remake-balance" :to="pointsRoute" @click="emit('close')">
          <AssetIcon name="sparkle" />
          剩余 {{ formatPoints(capability.availablePoints) }} 积分
        </router-link>
      </div>

      <div v-if="panel === 'new'" class="remake-grid">
        <div class="remake-main">
          <div v-if="capabilityLoading" class="remake-empty" role="status">正在检查套餐与复刻服务…</div>

          <section v-else-if="!sourceMatchingReady" class="remake-access">
            <span class="remake-access-icon"><AssetIcon :name="capability?.subscribed === false ? 'video' : 'info-line'" /></span>
            <h3>{{ capability?.subscribed === false ? '开通套餐后即可开始复刻' : '复刻服务暂不可用' }}</h3>
            <p v-if="capabilityError" role="alert">{{ capabilityError }}</p>
            <p v-else-if="capability?.subscribed === false">参考原视频的镜头节奏，替换目标商品，生成可保存到素材库的新视频。</p>
            <p v-else>服务恢复后可以继续新建复刻，已有任务可在任务记录中查看。</p>
            <router-link v-if="capability?.subscribed === false" class="el-button el-button--primary remake-cta"
              to="/subscription" @click="emit('close')">查看套餐</router-link>
            <el-button v-else type="primary" @click="loadCapability">重新检查</el-button>
          </section>

          <form v-else class="remake-form" @submit.prevent="continueSetup">
            <ol class="remake-steps" aria-label="准备素材的步骤">
              <li v-for="(item, index) in setupSteps" :key="item" :class="{ current: setupStep === index }">
                <button type="button" :aria-current="setupStep === index ? 'step' : undefined"
                  :disabled="busy || index > furthestStep" @click="goToStep(index)">
                  <span>{{ index + 1 }}</span>{{ item }}
                </button>
              </li>
            </ol>

            <div v-if="setupStep === 0" class="remake-step-body">
              <h3>参考视频</h3>
              <p class="remake-muted">选择要参考镜头、构图和节奏的视频。</p>
              <div class="remake-source-switch" role="group" aria-label="参考视频来源">
                <button type="button" :aria-pressed="referenceSource === 'upload'" :disabled="busy" @click="switchReferenceSource('upload')">本地上传</button>
                <button type="button" :aria-pressed="referenceSource === 'link'" :disabled="busy" @click="switchReferenceSource('link')">粘贴链接</button>
              </div>
              <template v-if="referenceSource === 'upload'">
                <RemakeFilePicker kind="video" label="上传或拖入参考视频" :file="reference?.file" :disabled="busy"
                  :approval-required="!rights" @blocked="promptReferenceRights" @change="selectReference"
                  @metadata="referenceMetadata = $event" />
                <p v-if="referenceError || fieldError('reference')" class="remake-error" role="alert">{{ referenceError || fieldError('reference') }}</p>
                <p v-else-if="referenceMetadata === null" class="remake-muted">当前浏览器无法预览，上传后会校验视频规格。</p>
                <details class="remake-video-requirements">
                  <summary>查看视频要求</summary>
                  <ul>
                    <li>横屏 16:9 · 1280 × 720 至 2560 × 1440</li>
                    <li>竖屏 9:16 · 720 × 1280 至 1440 × 2560</li>
                  </ul>
                </details>
              </template>
              <div v-else class="remake-link-import">
                <label>抖音视频链接
                  <input v-model.trim="referenceLinkUrl" type="text" inputmode="url" :disabled="busy"
                    placeholder="粘贴抖音分享内容或视频链接，例如 v.douyin.com/… 或 douyin.com/video/…" @paste="handleReferenceLinkPaste">
                </label>
                <p class="remake-muted">{{ extractedReferenceLinkUrl ? '已识别链接：' + extractedReferenceLinkUrl : '支持抖音分享短链或视频页链接，也可直接粘贴整段抖音分享文案，会自动提取其中的视频链接；仅支持公开视频。提交时由服务端下载并校验视频规格；链接无法访问、视频私密或已删除时会在此提示，请更换链接或改用本地上传。' }}</p>
                <p v-if="referenceError || fieldError('reference')" class="remake-error" role="alert">{{ referenceError || fieldError('reference') }}</p>
                <p v-else-if="referenceLinkAssetId && referenceMetadata" class="remake-muted" role="status">已获取参考视频：{{ referenceMetadata.width }} × {{ referenceMetadata.height }} · {{ Math.round(referenceMetadata.duration) }} 秒（来源：抖音）</p>
              </div>
              <div class="remake-rights-row">
                <label class="remake-check"><input ref="referenceRights" v-model="rights" type="checkbox" :disabled="busy"
                  :aria-describedby="fieldError('rights') ? 'remake-video-rights-error' : undefined">我已阅读并同意协议，确认有权使用此参考视频<template v-if="productPeopleSupported">及本次使用的商品图</template></label>
                <button type="button" class="remake-agreement-link" aria-haspopup="dialog" :disabled="busy"
                  @click="rightsAgreementOpen = true">《素材授权与责任协议》</button>
              </div>
              <p v-if="fieldError('rights')" id="remake-video-rights-error" class="remake-error" role="alert">{{ fieldError('rights') }}</p>
            </div>

            <div v-else-if="setupStep === 1" class="remake-step-body">
              <h3>替换目标商品</h3>
              <div class="remake-row">
                <label>千川账户
                  <select v-model="bindingId" :disabled="busy || accountsLoading">
                    <option value="">请选择</option>
                    <option v-for="account in activeAccounts" :key="account.accountId" :value="account.accountId">{{ account.accountName }}</option>
                  </select>
                </label>
                <label>抖音账号
                  <select v-model="awemeId" :disabled="busy || !bindingId">
                    <option value="">请选择</option>
                    <option v-for="aweme in awemes" :key="aweme.awemeId" :value="aweme.awemeId">{{ aweme.name }}</option>
                  </select>
                </label>
              </div>
              <p v-if="fieldError('account')" class="remake-error" role="alert">{{ fieldError('account') }}</p>
              <div v-if="accountsError" class="remake-inline-error" role="alert">
                {{ accountsError }}<el-button link type="primary" @click="loadAccounts">重新加载</el-button>
              </div>
              <router-link v-else-if="!accountsLoading && !activeAccounts.length" class="remake-link" to="/accounts?tab=oauth"
                @click="emit('close')">去绑定千川账户</router-link>
              <div v-else-if="bindingId && !awemes.length" class="remake-muted">该千川账户暂无可用抖音账号。</div>

              <div v-if="awemeId" class="remake-catalog">
                <div class="remake-card-title">
                  <strong>目标商品</strong>
                  <span v-if="selectedProduct">已选 {{ selectedProduct.name }}</span>
                </div>
                <div class="remake-search">
                  <input v-model="keyword" type="search" aria-label="搜索目标商品" placeholder="商品名称 / ID" :disabled="busy"
                    @keydown.enter.prevent="searchProducts">
                  <el-button :loading="productsLoading" :disabled="busy" @click="searchProducts">搜索</el-button>
                </div>
                <p v-if="productsError" class="remake-error" role="alert">{{ productsError }}</p>
                <div v-if="products.length" class="remake-products" :aria-busy="productsLoading">
                  <button v-for="product in products" :key="product.qcProductId" type="button" class="remake-product"
                    :aria-pressed="selectedProduct?.qcProductId === product.qcProductId"
                    :disabled="busy || productsLoading" @click="selectedProduct = product">
                    <span class="remake-product-image"><img v-if="product.imageUrl" :src="product.imageUrl" alt="" loading="lazy"><AssetIcon v-else name="image" /></span>
                    <span class="remake-product-meta"><strong :title="product.name">{{ product.name }}</strong><small>{{ product.qcProductId }}</small></span>
                    <AssetIcon v-if="selectedProduct?.qcProductId === product.qcProductId" name="check" />
                  </button>
                </div>
                <p v-else-if="productsLoading" class="remake-muted" role="status">正在加载商品…</p>
                <p v-else-if="!productsError" class="remake-muted" role="status">没有符合条件的商品。</p>
                <p v-if="fieldError('product')" class="remake-error" role="alert">{{ fieldError('product') }}</p>
                <nav v-if="productTotalPages > 0" class="remake-pager" aria-label="商品分页">
                  <div class="remake-pager-pages">
                    <el-button link class="remake-pager-arrow remake-pager-prev" aria-label="上一页"
                      :disabled="busy || productPage <= 1 || productsLoading" @click="loadProducts(productPage - 1)">
                      <AssetIcon name="chevron-right" />
                    </el-button>
                    <span aria-live="polite">{{ productPage }}/{{ productTotalPages }}</span>
                    <el-button link class="remake-pager-arrow" aria-label="下一页"
                      :disabled="busy || productPage >= productTotalPages || productsLoading" @click="loadProducts(productPage + 1)">
                      <AssetIcon name="chevron-right" />
                    </el-button>
                  </div>
                  <div v-if="productTotalPages > 1" class="remake-pager-jump">
                    <input v-model="productPageInput" type="text" inputmode="numeric" placeholder="页码" aria-label="跳转到第几页"
                      :disabled="busy || productsLoading" :aria-invalid="!!productPageError"
                      :aria-describedby="productPageError ? 'remake-page-error' : undefined"
                      @input="productPageError = ''" @keydown.enter.prevent="jumpToProductPage">
                    <el-button native-type="button" :disabled="busy || productsLoading || !productPageInput.trim()" @click="jumpToProductPage">跳转</el-button>
                  </div>
                  <p v-if="productPageError" id="remake-page-error" class="remake-error remake-pager-error" role="alert">{{ productPageError }}</p>
                </nav>
              </div>
            </div>

            <div v-else class="remake-step-body">
              <h3>替换人物<template v-if="productPeopleSupported">（可选）</template></h3>
      <div v-if="productPeopleSupported" class="remake-person-options">
        <label class="remake-person-option">
          <input v-model="useCustomPortrait" type="checkbox" :disabled="busy">
          <span>使用自选人像</span>
        </label>
        <p class="remake-person-hint">不上传时，默认使用商品图中的模特，没有模特时使用参考视频中的表演者。</p>
      </div>
      <template v-if="!productPeopleMode">
                <RemakeFilePicker v-if="useKling" kind="image" label="上传已授权的成年人像" :file="portraitImage?.file"
                  :disabled="busy" @change="selectPortrait" />
                <template v-else>
                  <div class="remake-row">
                    <label>方舟预置虚拟人像 ID<input v-model.trim="portraitId" placeholder="asset-…" maxlength="136" :disabled="busy"></label>
                    <label>人物名称<input v-model.trim="portraitName" placeholder="输入人像名称" maxlength="80" :disabled="busy"></label>
                  </div>
                  <a class="remake-link" href="https://ark.volcengine.com/region:cn-beijing/docs/avatar-library"
                    target="_blank" rel="noopener noreferrer">查看方舟虚拟人像库 <AssetIcon name="chevron-right" /></a>
                </template>
                <p v-if="fieldError('portrait')" class="remake-error" role="alert">{{ fieldError('portrait') }}</p>
              </template>
              <template v-if="portraitTermsRequired">
                <div class="remake-rights-row">
                  <label class="remake-check"><input v-model="portraitTerms" type="checkbox" :disabled="busy"
                    :aria-describedby="fieldError('portraitTerms') ? 'remake-portrait-rights-error' : undefined">我已阅读并同意协议，{{ portraitTermsLabel }}</label>
                  <button type="button" class="remake-agreement-link" aria-haspopup="dialog" :disabled="busy"
                    @click="rightsAgreementOpen = true">《素材授权与责任协议》</button>
                </div>
                <p v-if="fieldError('portraitTerms')" id="remake-portrait-rights-error" class="remake-error" role="alert">{{ fieldError('portraitTerms') }}</p>
              </template>
            </div>

            <p v-if="setupError" class="remake-inline-error" role="alert">{{ setupError }}</p>
            <div v-if="uploadProgress" class="remake-upload-progress" role="status">
              <span>{{ uploadProgress.label }}</span>
              <el-progress :percentage="uploadProgress.percent" :aria-label="uploadProgress.label" />
            </div>
            <div class="remake-actions">
              <el-button v-if="setupStep > 0" :disabled="busy" @click="goToStep(setupStep - 1)">上一步</el-button>
              <el-button native-type="submit" type="primary" class="remake-submit" :loading="busy"
                :disabled="setupStep === 2 && portraitTermsRequired && !portraitTerms">{{ setupStep < 2 ? '下一步' : '开始复刻' }}</el-button>
            </div>
          </form>
        </div>

        <aside class="remake-sidebar" aria-label="复刻流程与成片设置">
          <section class="remake-side-card">
            <h3>复刻流程</h3>
            <ol class="remake-workflow">
              <li><span><AssetIcon name="video" /></span><div><strong>准备素材</strong><small>参考视频、目标商品与可选人像</small></div></li>
              <li><span><AssetIcon name="note" /></span><div><strong>后台生成</strong><small>自动生成分镜并核对成片规格</small></div></li>
              <li><span><AssetIcon name="sparkle" /></span><div><strong>生成与入库</strong><small>生成后预览，合适即可保存</small></div></li>
            </ol>
          </section>
          <section class="remake-side-card remake-summary">
            <h3>成片设置</h3>
            <dl>
              <div><dt>画幅</dt><dd>{{ expectedOutput ? orientationLabel(expectedOutput) : '跟随原视频' }}</dd></div>
              <div><dt>时长</dt><dd>{{ expectedOutput ? expectedOutput.duration_seconds + ' 秒' : '跟随原视频' }}</dd></div>
              <div><dt>分辨率</dt><dd>{{ expectedOutput ? resolutionLabel(expectedOutput) : capability?.sourceMatchingHd === false ? '720p' : '匹配原视频' }}</dd></div>
              <div v-if="selectedProduct"><dt>目标商品</dt><dd>{{ selectedProduct.name }}</dd></div>
            </dl>
            <p v-if="estimatedShortfall" class="remake-error" role="status">{{ estimatedShortfall }}</p>
            <p v-if="sourceMatchingReady" class="remake-muted">分析后自动生成，按成片规格预占积分。</p>
          </section>
        </aside>
      </div>

      <section v-else-if="panel === 'history'" class="remake-history">
        <div class="remake-history-bar">
          <label><span class="remake-sr-only">搜索任务</span><input v-model="historyQuery" type="search" placeholder="搜索商品名称或任务 ID"></label>
          <label><span class="remake-sr-only">任务状态</span><select v-model="historyFilter"><option value="all">全部状态</option><option value="running">处理中</option><option value="completed">成片已就绪</option><option value="attention">失败或待核对</option></select></label>
          <el-button :loading="historyLoading" @click="history"><AssetIcon name="refresh" />刷新</el-button>
        </div>
        <p v-if="historyError" class="remake-error" role="alert">{{ historyError }}</p>
        <div v-if="historyLoading && !tasks.length" class="remake-empty" role="status">正在加载任务…</div>
        <div v-else-if="!filteredTasks.length && !historyError" class="remake-empty">
          <AssetIcon name="folder" /><strong>{{ tasks.length ? '没有符合条件的任务' : '暂无复刻任务' }}</strong>
          <el-button v-if="!tasks.length" type="primary" plain @click="newTask">新建复刻</el-button>
        </div>
        <ul v-else-if="filteredTasks.length" class="remake-history-list">
          <li v-for="item in filteredTasks" :key="item.id">
            <span class="remake-task-icon"><AssetIcon name="video" /></span>
            <div class="remake-history-name"><strong>{{ item.product }}</strong><small>{{ formatDate(item.createdAt) }} <template v-if="item.assetId">· 已入库</template></small></div>
            <span class="remake-status" :data-tone="taskTone(item.phase)">{{ phaseLabel(item.phase) }}</span>
            <el-button link type="primary" @click="openTask(item.id, item)">{{ taskAction(item.phase) }} <AssetIcon name="chevron-right" /></el-button>
          </li>
        </ul>
      </section>

      <section v-else class="remake-task" :class="{ 'remake-task--with-gallery': progressStage && task?.productImageUrls?.length }">
        <div class="remake-task-header">
          <el-button link :disabled="busy" @click="history"><AssetIcon name="arrow-left" />返回任务列表</el-button>
          <span v-if="task" class="remake-muted">任务 ID {{ task.id }}</span>
        </div>
        <div v-if="taskLoading && !task" class="remake-empty" role="status">正在加载任务详情…</div>
        <template v-if="task">
          <div class="remake-task-title"><h3>{{ task.product }}</h3>
            <span class="remake-status" :data-tone="taskTone(task.phase)" role="status">{{ phaseLabel(task.phase) }}</span>
          </div>
          <p v-if="task.error" class="remake-error remake-task-error" role="alert">{{ task.error }}</p>
          <details v-if="task.consistencyIssues?.length || task.auditIssues?.length || task.generationWarnings?.length || task.shotRetries?.length"
            class="remake-details">
            <summary>查看详情</summary>
            <section v-if="task.consistencyIssues?.length" class="remake-ad-notices">
              <h3>成片一致性检查未通过</h3>
              <ul><li v-for="issue in task.consistencyIssues" :key="issue">{{ issue }}</li></ul>
            </section>
            <section v-if="task.auditIssues?.length" class="remake-ad-notices">
              <h3>与商品事实矛盾（未通过）</h3>
              <ul><li v-for="issue in task.auditIssues" :key="issue">{{ issue }}</li></ul>
            </section>
            <section v-if="task.generationWarnings?.length" class="remake-ad-notices">
              <h3>生成提示</h3>
              <ul><li v-for="warning in task.generationWarnings" :key="warning">{{ warning }}</li></ul>
            </section>
            <section v-if="task.shotRetries?.length" class="remake-ad-notices">
              <h3>分镜重试</h3>
              <ul><li v-for="retry in task.shotRetries" :key="retry.shot">
                第 {{ retry.shotIndex }} 分镜（{{ retryReasonLabel(retry.reason) }}），{{ retryStatusLabel(retry.status) }}。
              </li></ul>
            </section>
          </details>

          <div v-if="task.phase === 'SUCCEEDED'" class="remake-result">
            <div class="remake-result-screen">
              <video v-if="task.videoUrl" :key="previewVersion" class="remake-preview" :src="task.videoUrl" :poster="task.coverUrl" controls
                playsinline preload="metadata" aria-label="成片预览" @error="previewFailed = true" />
              <span v-else class="remake-muted">预览暂不可用</span>
            </div>
            <div class="remake-result-info">
              <h3>{{ task.plan?.title || '复刻成片' }}</h3>
              <p class="remake-muted">{{ formatDate(task.createdAt) }}<template v-if="task.outputSpec"> · {{ orientationLabel(task.outputSpec) }} · {{ task.outputSpec.duration_seconds }} 秒 · {{ resolutionLabel(task.outputSpec) }}</template></p>
              <p v-if="task.assetId" class="remake-saved"><AssetIcon name="check-circle" />已保存到素材库</p>
              <p v-if="previewFailed" class="remake-error" role="alert">预览加载失败，请刷新成片。</p>
              <div class="remake-result-actions">
                <el-button type="primary" :disabled="!task.videoUrl || !!task.assetId" :loading="busy" @click="save">
                  保存到素材库
                </el-button>
                <el-button :loading="taskLoading" :disabled="busy" @click="refreshTask">刷新成片</el-button>
              </div>
              <p v-if="taskError" class="remake-error" role="alert">{{ taskError }}</p>
            </div>
          </div>

          <section v-else class="remake-progress-card" aria-label="任务进度">
            <div v-if="progressStage" class="remake-progress-ring" :data-stage="progressStage" role="progressbar"
              aria-label="复刻阶段进度" :aria-valuemin="1" :aria-valuemax="progressStages.length" :aria-valuenow="progressStage"
              :aria-valuetext="`第 ${progressStage} 阶段，共 ${progressStages.length} 个阶段`">
              <strong>{{ progressStage }}<span>/{{ progressStages.length }}</span></strong>
              <small>阶段</small>
            </div>
            <span v-else class="remake-task-icon"><AssetIcon name="info-line" /></span>
            <div class="remake-progress-content">
              <h3 aria-live="polite">{{ taskProgress }}</h3>
              <p v-if="task.quote" class="remake-muted">本次生成 {{ formatPoints(task.quote.points) }} 积分</p>
              <p v-if="progressStage" class="remake-muted">可关闭弹窗，稍后从任务记录查看进度。</p>
              <p v-if="taskError" class="remake-error" role="alert">{{ taskError }}</p>
              <div class="remake-progress-actions">
                <el-button v-if="task.phase !== 'FAILED'" :loading="taskLoading" :disabled="busy" @click="refreshTask">刷新状态</el-button>
                <el-button v-else :disabled="busy" @click="newTask">新建复刻</el-button>
              </div>
            </div>
          </section>

          <section v-if="task.productImageUrls?.length" class="remake-task-gallery" aria-label="商品参考图">
            <h3>商品参考图</h3>
            <div class="remake-task-gallery-images">
              <el-image v-for="(url, index) in task.productImageUrls" :key="url" :src="url"
                :alt="`${task.product} · 商品图 ${index + 1}`" :preview-src-list="task.productImageUrls"
                :initial-index="index" fit="contain" referrerpolicy="no-referrer" preview-teleported>
                <template #error><span class="remake-task-gallery-placeholder" role="img" aria-label="图片暂不可用"><AssetIcon name="image" /></span></template>
              </el-image>
            </div>
          </section>
        </template>
        <div v-else-if="taskError" class="remake-inline-error" role="alert">
          {{ taskError }}<el-button link type="primary" @click="refreshTask">重试</el-button>
        </div>
      </section>
    </div>

    <el-dialog v-model="rightsAgreementOpen" title="素材授权与责任协议" width="min(560px, calc(100vw - 32px))"
      class="remake-agreement-dialog" append-to-body align-center>
      <div class="remake-agreement-content">
        <section>
          <h3>参考视频授权</h3>
          <p>商家须对参考视频及其中使用的音乐、画面等素材享有合法权利，或事先取得权利人的有效授权。授权范围须涵盖本次上传、AI 分析、编辑、复刻及成片的商业使用和广告投放。</p>
        </section>
        <section>
          <h3>人物肖像与声音授权</h3>
          <p>使用可识别真人的肖像或声音前，商家须向本人告知 AI 生成、编辑及广告投放等用途，并取得相应授权。涉及编辑人脸、人声等生物识别信息的，须依法取得本人的单独同意。使用预置虚拟人像的，须遵守相应许可条款。</p>
          <p v-if="productPeopleSupported">商品图中的模特亦须取得用于 AI 生成和商业广告投放的肖像授权。</p>
        </section>
        <section>
          <h3>责任承担</h3>
          <p><strong>因商家未经授权、超出授权范围或违法使用素材及成片引发的纠纷，由商家依法处理并承担相应法律责任。</strong>坤境智营提供视频生成与复刻技术服务，依法履行平台义务并承担相应责任。</p>
        </section>
      </div>
      <template #footer><el-button type="primary" @click="rightsAgreementOpen = false">返回确认</el-button></template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useAdvertisingAccountsStore } from '@/stores/advertisingAccounts';
import { creationProducts, type CreationProduct } from '@/features/delivery/creationApi';
import {
  createRemake, getRemake, getRemakeCapability, importRemakeLink, listRemakes, saveRemake, uploadRemakeMedia,
  type RemakeCapability, type RemakeTask, type RemakeUpload, type RemakeVideoSpec,
} from '@/features/creative/videoRemakeApi';
import AssetIcon from './AssetIcon.vue';
import RemakeFilePicker from './RemakeFilePicker.vue';

const emit = defineEmits<{ close: []; saved: [] }>();
const advertising = useAdvertisingAccountsStore();
const { accounts, accountId, loading: accountsLoading, error: accountsError } = storeToRefs(advertising);
const activeAccounts = computed(() => accounts.value.filter(account => account.status === 'ACTIVE'));
const bindingId = ref(''), awemeId = ref('');
const selectedAccount = computed(() => activeAccounts.value.find(account => account.accountId === bindingId.value));
const awemes = computed(() => selectedAccount.value?.awemes ?? []);
const products = ref<CreationProduct[]>([]), selectedProduct = ref<CreationProduct>();
const keyword = ref(''), productPage = ref(1), productTotalPages = ref(0), productsLoading = ref(false), productsError = ref('');
const productPageInput = ref(''), productPageError = ref('');
let productQuery = '';

const reference = ref<RemakeUpload>(), portraitImage = ref<RemakeUpload>();
const referenceSource = ref<'upload' | 'link'>('upload');
const referenceLinkUrl = ref('');
const douyinHosts = new Set(['v.douyin.com', 'www.douyin.com', 'douyin.com', 'www.iesdouyin.com', 'iesdouyin.com']);
function extractDouyinShareUrl(text: string) {
  const links = text.matchAll(/https?:\/\/[^\s"'<>，。；、！？]+|(?<![\w./@:-])(?:v\.douyin\.com|(?:www\.)?(?:douyin|iesdouyin)\.com)\/[^\s"'<>，。；、！？]+/gi);
  for (const [link] of links) {
    const candidate = link.replace(/[)\]}.,;:!?）】》]+$/, '');
    try {
      const url = new URL(/^https?:\/\//i.test(candidate) ? candidate : `https://${candidate}`);
      if (!douyinHosts.has(url.hostname) || url.username || url.password || url.port || url.pathname === '/') continue;
      url.protocol = 'https:';
      return url.toString();
    } catch { /* Continue with the next URL in the share text. */ }
  }
  return '';
}
const extractedReferenceLinkUrl = computed(() => extractDouyinShareUrl(referenceLinkUrl.value));
function handleReferenceLinkPaste(event: ClipboardEvent) {
  const extracted = extractDouyinShareUrl(event.clipboardData?.getData('text') ?? '');
  if (!extracted) return;
  event.preventDefault();
  referenceLinkUrl.value = extracted;
}
const referenceLinkAssetId = ref('');
const referenceMetadata = ref<{ duration: number; width: number; height: number } | null>();
const referenceError = computed(() => {
  const media = referenceMetadata.value;
  if (!media) return '';
  if (media.duration < 3.98 || media.duration > 120.02) return '参考视频时长须为 4 至 120 秒。';
  const shortEdge = Math.min(media.width, media.height), longEdge = Math.max(media.width, media.height);
  if (shortEdge < 720 || shortEdge > 1440 || longEdge > 2560 || Math.abs(longEdge / shortEdge - 16 / 9) > .005) {
    return '参考视频须为横屏 16:9 或竖屏 9:16，分辨率须在 720p 至 1440p 内。';
  }
  return '';
});
const invalidShareLink = '请粘贴包含抖音视频链接的分享内容。';
const linkFormatError = computed(() => referenceLinkUrl.value.trim() && !extractedReferenceLinkUrl.value
  ? invalidShareLink : '');
const referenceRights = ref<HTMLInputElement>();
const portraitId = ref(''), portraitName = ref('');
  const portraitMode = ref<'auto' | 'replace'>('auto');
  const useCustomPortrait = computed({
    get: () => portraitMode.value === 'replace',
    set: (value: boolean) => { portraitMode.value = value ? 'replace' : 'auto'; },
  });
const rights = ref(false), portraitTerms = ref(false);
const rightsAgreementOpen = ref(false);
const setupSteps = ['参考视频', '目标商品', '替换人物'];
const setupStep = ref(0), furthestStep = ref(0);
const setupIssue = ref<{ field: string; message: string }>();
const setupError = ref('');
const uploadProgress = ref<{ label: string; percent: number }>();
let requestId = crypto.randomUUID();

const capability = ref<RemakeCapability>(), capabilityLoading = ref(true), capabilityError = ref('');
const useKling = computed(() => capability.value?.kling4Available === true);
const productPeopleSupported = computed(() => capability.value?.productPeopleSupported === true);
const productPeopleMode = computed(() => productPeopleSupported.value && portraitMode.value !== 'replace' ? portraitMode.value : undefined);
const portraitTermsRequired = computed(() => true); // 仅展示商品已移除，自动与自选均需确认肖像授权。
const portraitTermsLabel = computed(() => {
  if (productPeopleMode.value === 'auto') return '确认商品图中的模特为成年人，且已取得用于 AI 生成视频和广告投放的肖像授权';
  if (useKling.value) return '确认图中人物为成年人，且有权将图片交由可灵生成视频，并将成片用于广告投放';
  return '确认所选人像为成年人，且有权用于生成和广告投放';
});
const sourceMatchingReady = computed(() => capability.value?.ready === true && capability.value.sourceMatchingAvailable === true);
const formatPoints = (value: number) => value.toLocaleString('zh-CN', { maximumFractionDigits: 6 });
const expectedOutput = computed<RemakeVideoSpec | undefined>(() => {
  const media = referenceMetadata.value;
  if (!media || referenceError.value) return;
  const shortEdge = capability.value?.sourceMatchingHd && Math.min(media.width, media.height) >= 900 ? 1080 : 720;
  const longEdge = shortEdge * 16 / 9;
  const portrait = media.height > media.width;
  return { width: portrait ? shortEdge : longEdge, height: portrait ? longEdge : shortEdge,
    duration_seconds: Math.min(120, Math.max(4, Math.round(media.duration))) };
});
const estimatedPoints = computed(() => {
  const spec = expectedOutput.value;
  if (!spec) return;
  const resolution = Math.min(spec.width, spec.height) === 1080 ? '1080p' : '720p';
  const rate = capability.value?.pointsPerSecond?.[resolution];
  if (rate != null) return rate * spec.duration_seconds;
});
function pointShortfall(points: number | undefined, estimated = false) {
  const available = capability.value?.availablePoints;
  if (points == null || available == null || available >= points) return '';
  return `本次${estimated ? '至少' : '生成'}需 ${formatPoints(points)} 积分，还差 ${formatPoints(points - available)} 积分。`;
}
const estimatedShortfall = computed(() => pointShortfall(estimatedPoints.value, true));
const orientationLabel = (spec: RemakeVideoSpec) => spec.height > spec.width ? '竖屏 9:16' : '横屏 16:9';
const resolutionLabel = (spec: RemakeVideoSpec) => `${spec.width} × ${spec.height}`;
const pointsRoute = { path: '/accounts', query: { tab: 'workspace' } };

const panel = ref<'new' | 'history' | 'task'>('new');
const tasks = ref<RemakeTask[]>([]), historyError = ref(''), historyLoading = ref(false);
const historyFilter = ref('all'), historyQuery = ref('');
const task = ref<RemakeTask>(), taskId = ref(''), taskLoading = ref(false), taskError = ref('');
const previewFailed = ref(false), previewVersion = ref(0), busy = ref(false);

let productRequest = 0, taskRequest = 0, capabilityRequest = 0, alive = true;
let poll: ReturnType<typeof setTimeout> | undefined;
const inProgress = new Set<RemakeTask['phase']>(['CREATING', 'PLANNING', 'REVIEW', 'CONFIRMING', 'SENDING', 'GENERATING']);
const labels: Record<RemakeTask['phase'], string> = {
  CREATING: '等待分析', PLANNING: '正在分析视频', REVIEW: '正在准备生成', CONFIRMING: '正在确认生成',
  SENDING: '正在提交生成', GENERATING: '正在生成', SUCCEEDED: '成片已就绪', FAILED: '复刻失败', NEEDS_ATTENTION: '结果待核对',
};
const phaseLabel = (phase: RemakeTask['phase']) => labels[phase] ?? phase;
const formatDate = (date: string) => new Date(date).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
function message(cause: unknown) {
  const text = cause instanceof Error ? cause.message : '操作未完成，请稍后重试。';
  const reason = (cause as { reason?: unknown } | null)?.reason;
  return typeof reason === 'string' && reason ? `${text}（原因码：${reason}）` : text;
}

function taskTone(phase: RemakeTask['phase']) {
  if (phase === 'SUCCEEDED') return 'success';
  if (phase === 'FAILED') return 'danger';
  if (phase === 'NEEDS_ATTENTION') return 'warning';
  return 'progress';
}
function taskAction(phase: RemakeTask['phase']) {
  if (phase === 'SUCCEEDED') return '查看成片';
  return '查看任务';
}
const filteredTasks = computed(() => {
  const query = historyQuery.value.trim().toLowerCase();
  return tasks.value.filter(item => {
    const group = inProgress.has(item.phase) ? 'running' : item.phase === 'SUCCEEDED' ? 'completed'
      : 'attention';
    return (historyFilter.value === 'all' || historyFilter.value === group)
      && (!query || item.product.toLowerCase().includes(query) || item.id.toLowerCase().includes(query));
  });
});
const progressStages = ['正在分析素材', '正在准备生成', '正在生成成片'];
const progressStage = computed(() => {
  const phase = task.value?.phase;
  if (phase === 'CREATING' || phase === 'PLANNING') return 1;
  if (phase === 'REVIEW' || phase === 'CONFIRMING') return 2;
  if (phase === 'SENDING' || phase === 'GENERATING') return 3;
  return 0;
});
const retryReasons: Record<string, string> = {
  product_mismatch: '商品与参考图不一致', fake_brand: '伪造或替换品牌', garbled_text: '乱码或伪文字',
};
const retryStatuses: Record<string, string> = {
  passed: '重试后通过', failed: '重试后仍未通过',
};
const retryReasonLabel = (reason: string) => retryReasons[reason] || '视频一致性检查未通过';
const retryStatusLabel = (status: string) => retryStatuses[status] || '重试中';
const taskProgress = computed(() => task.value
  ? progressStages[progressStage.value - 1] ?? phaseLabel(task.value.phase)
  : '');

async function loadCapability() {
  const revision = ++capabilityRequest;
  capabilityLoading.value = true;
  capabilityError.value = '';
  try {
    const value = await getRemakeCapability();
    if (alive && revision === capabilityRequest) capability.value = value;
  } catch (cause) {
    if (alive && revision === capabilityRequest) {
      capability.value = undefined;
      capabilityError.value = message(cause);
    }
  } finally {
    if (alive && revision === capabilityRequest) capabilityLoading.value = false;
  }
}
async function loadAccounts() {
  await advertising.load();
  if (!alive || selectedAccount.value) return;
  bindingId.value = activeAccounts.value.find(account => account.accountId === accountId.value)?.accountId
    ?? activeAccounts.value[0]?.accountId ?? '';
}
async function loadProducts(page: number, query = productQuery) {
  if (!bindingId.value || !awemeId.value || busy.value) return;
  const revision = ++productRequest;
  productsLoading.value = true;
  productsError.value = '';
  productPageError.value = '';
  try {
    const pageSize = 6;
    const result = await creationProducts(bindingId.value, awemeId.value, {
      page, pageSize, keyword: query, tab: 'ALL', unpromoted: false,
    });
    if (revision !== productRequest || !alive) return;
    const totalPages = Math.max(result.items.length ? 1 : 0, result.totalPages ?? Math.ceil(result.total / pageSize));
    if (totalPages > 0 && page > totalPages) {
      await loadProducts(totalPages, query);
      return;
    }
    products.value = result.items;
    productPage.value = totalPages > 0 ? page : 1;
    productTotalPages.value = totalPages;
    productPageInput.value = '';
    productQuery = query;
    const returnedSelection = result.items.find(item => item.qcProductId === selectedProduct.value?.qcProductId);
    if (returnedSelection) selectedProduct.value = returnedSelection;
  } catch (cause) {
    if (revision === productRequest && alive) productsError.value = message(cause);
  } finally {
    if (revision === productRequest && alive) productsLoading.value = false;
  }
}
function jumpToProductPage() {
  const input = productPageInput.value.trim();
  if (!input || busy.value || productsLoading.value) return;
  const page = Number(input);
  if (!/^\d+$/.test(input) || page < 1 || page > productTotalPages.value) {
    productPageError.value = `请输入 1 至 ${productTotalPages.value} 的整数页码。`;
    return;
  }
  if (page === productPage.value) {
    productPageInput.value = '';
    return;
  }
  void loadProducts(page);
}
function searchProducts() { void loadProducts(1, keyword.value.trim()); }
watch(bindingId, () => {
  awemeId.value = awemes.value.find(aweme => aweme.isPrimary)?.awemeId ?? awemes.value[0]?.awemeId ?? '';
  for (const item of [reference.value, portraitImage.value]) {
    if (!item) continue;
    item.requestId = crypto.randomUUID();
    item.uploadId = undefined;
    item.assetId = undefined;
    item.nextChunk = 0;
  }
  referenceLinkAssetId.value = '';
  if (referenceSource.value === 'link') referenceMetadata.value = undefined;
});
watch([bindingId, awemeId], () => {
  ++productRequest;
  keyword.value = '';
  productQuery = '';
  products.value = [];
  selectedProduct.value = undefined;
  productPage.value = 1;
  productTotalPages.value = 0;
  productPageInput.value = '';
  productPageError.value = '';
  productsLoading.value = false;
  productsError.value = '';
  furthestStep.value = Math.min(furthestStep.value, 1);
  void loadProducts(1);
});
watch([productPeopleMode, useKling, portraitId, () => selectedProduct.value?.qcProductId], () => {
  portraitTerms.value = false;
});
watch(referenceMetadata, () => {
  if (setupIssue.value?.field === 'reference') setupIssue.value = undefined;
});
watch([reference, portraitImage, productPeopleMode, bindingId, awemeId, () => selectedProduct.value?.qcProductId,
  portraitId, portraitName, rights, portraitTerms, referenceLinkUrl, referenceSource], () => {
  requestId = crypto.randomUUID();
  setupIssue.value = undefined;
  setupError.value = '';
});
function uploadSession(file?: File): RemakeUpload | undefined {
  return file ? { file, requestId: crypto.randomUUID(), nextChunk: 0 } : undefined;
}
function selectReference(file?: File) {
  if (reference.value) rights.value = false;
  referenceSource.value = 'upload';
  referenceLinkUrl.value = '';
  referenceLinkAssetId.value = '';
  reference.value = uploadSession(file);
  referenceMetadata.value = undefined;
  furthestStep.value = 0;
}
function switchReferenceSource(next: 'upload' | 'link') {
  if (busy.value || next === referenceSource.value) return;
  referenceSource.value = next;
  setupIssue.value = undefined;
  setupError.value = '';
  if (next === 'link') {
    reference.value = undefined;
    referenceMetadata.value = undefined;
  } else {
    referenceLinkUrl.value = '';
    referenceLinkAssetId.value = '';
    referenceMetadata.value = undefined;
  }
  furthestStep.value = 0;
}
function promptReferenceRights() {
  setupIssue.value = { field: 'rights', message: '请先阅读并勾选《素材授权与责任协议》，再选择视频。' };
  referenceRights.value?.focus();
}
function selectPortrait(file?: File) {
  portraitImage.value = uploadSession(file);
  portraitTerms.value = false;
}
const fieldError = (field: string) => setupIssue.value?.field === field ? setupIssue.value.message : '';
function validateStep(step: number) {
  const portraitMissing = useKling.value ? !portraitImage.value
    : !/^asset-[A-Za-z0-9_-]{4,120}$/.test(portraitId.value.replace(/^asset:\/\//, '')) || !portraitName.value;
  const issues: { when: boolean; field: string; message: string }[][] = [
    [
      { when: !rights.value, field: 'rights', message: '请阅读并同意协议，确认参考视频的使用授权。' },
      { when: referenceSource.value === 'upload' && !reference.value, field: 'reference', message: '请上传参考视频。' },
      { when: referenceSource.value === 'link' && !referenceLinkUrl.value.trim(), field: 'reference', message: '请粘贴抖音视频链接。' },
      { when: referenceSource.value === 'link' && !!linkFormatError.value, field: 'reference', message: linkFormatError.value },
      { when: !!referenceError.value, field: 'reference', message: referenceError.value },
      { when: referenceSource.value === 'upload' && !!reference.value && referenceMetadata.value === undefined, field: 'reference', message: '正在读取视频规格，请稍候。' },
    ],
    [
      { when: !bindingId.value || !awemeId.value, field: 'account', message: '请选择千川账户和抖音账号。' },
      { when: !selectedProduct.value, field: 'product', message: '请选择目标商品。' },
      { when: !!selectedProduct.value && !selectedProduct.value.imageUrls?.length && !selectedProduct.value.imageUrl,
        field: 'product', message: '所选商品没有可用的商品图，请重新选择。' },
    ],
    [
      { when: !productPeopleMode.value && portraitMissing,
        field: 'portrait', message: useKling.value ? '请上传已授权的成年人像图。' : '请填写有效的方舟人像 ID 和名称。' },
      { when: portraitTermsRequired.value && !portraitTerms.value, field: 'portraitTerms', message: '请确认人物素材的使用授权。' },
    ],
  ];
  setupIssue.value = issues[step]?.find(issue => issue.when);
  return !setupIssue.value;
}
function goToStep(next: number) {
  if (busy.value) return;
  if (next > setupStep.value) {
    for (let step = 0; step < next; step++) {
      if (!validateStep(step)) { setupStep.value = step; return; }
    }
  }
  setupIssue.value = undefined;
  setupError.value = '';
  setupStep.value = next;
  furthestStep.value = Math.max(furthestStep.value, next);
}
async function importReferenceLink() {
  uploadProgress.value = { label: '正在下载链接视频', percent: 50 };
  const extractedLinkUrl = extractedReferenceLinkUrl.value;
  if (!extractedLinkUrl) throw new Error(invalidShareLink);
  referenceLinkUrl.value = extractedLinkUrl;
  const result = await importRemakeLink(bindingId.value, crypto.randomUUID(), extractedLinkUrl);
  referenceLinkAssetId.value = result.assetId;
  referenceMetadata.value = { duration: result.durationSeconds, width: result.width, height: result.height };
  if (referenceError.value) throw new Error(referenceError.value);
  uploadProgress.value = { label: '正在校验链接视频', percent: 100 };
  return result.assetId;
}
async function continueSetup() {
  if (busy.value || !sourceMatchingReady.value) return;
  if (setupStep.value < 2) { goToStep(setupStep.value + 1); return; }
  for (let step = 0; step < setupSteps.length; step++) {
    if (!validateStep(step)) { setupStep.value = step; return; }
  }
  const product = selectedProduct.value, video = reference.value, kling = useKling.value, peopleMode = productPeopleMode.value;
  if (!product) return;
  const portraitTermsConfirmed = portraitTermsRequired.value && portraitTerms.value;
  busy.value = true;
  setupError.value = '';
  const upload = (session: RemakeUpload, kind: 'video' | 'image', label: string) => {
    uploadProgress.value = { label, percent: 0 };
    return uploadRemakeMedia(session, kind, bindingId.value, percent => {
      uploadProgress.value = { label: percent === 100 ? '正在校验' + label.replace(/^上传/, '') : label, percent };
    });
  };
  try {
    let referenceId: string;
    if (referenceSource.value === 'link') {
      referenceId = await importReferenceLink();
    } else {
      if (!video) return;
      referenceId = await upload(video, 'video', '上传参考视频');
    }
    const portraitImageId = !peopleMode && kling && portraitImage.value ? await upload(portraitImage.value, 'image', '上传人物图') : undefined;
    uploadProgress.value = undefined;
    const result = await createRemake({ requestId, bindingId: bindingId.value, awemeId: awemeId.value,
      productId: product.qcProductId, referenceId,
      portraitId: peopleMode || kling ? undefined : portraitId.value,
      portraitName: peopleMode || kling ? undefined : portraitName.value, portraitImageId,
      productPeopleMode: peopleMode, rightsConfirmed: rights.value,
      portraitTermsConfirmed, followSource: true });
    requestId = crypto.randomUUID();
    portraitMode.value = 'auto';
    portraitTerms.value = false;
    setupStep.value = 0;
    await openTask(result.id, result);
  } catch (cause) { setupError.value = message(cause); }
  finally { busy.value = false; uploadProgress.value = undefined; }
}

function changePanel(next: typeof panel.value) {
  ++taskRequest;
  clearTimeout(poll);
  panel.value = next;
  taskError.value = '';
  taskLoading.value = false;
  historyLoading.value = false;
}
async function newTask() {
  if (panel.value === 'new') return;
  changePanel('new');
  await loadCapability();
}
async function history() {
  changePanel('history');
  const revision = taskRequest;
  historyLoading.value = true;
  historyError.value = '';
  try {
    const value = await listRemakes();
    if (alive && revision === taskRequest) tasks.value = value;
  } catch (cause) {
    if (alive && revision === taskRequest) historyError.value = message(cause);
  } finally {
    if (alive && revision === taskRequest) historyLoading.value = false;
  }
}
function show(result: RemakeTask) {
  const previous = task.value;
  task.value = result;
  if (previous?.phase !== result.phase && ['CONFIRMING', 'GENERATING', 'SUCCEEDED', 'FAILED'].includes(result.phase)) {
    void loadCapability();
  }
}
async function fetchTask(id: string, revision: number, keepPolling: boolean) {
  let again = keepPolling;
  try {
    const result = await getRemake(id);
    if (!alive || revision !== taskRequest) return;
    taskError.value = '';
    show(result);
    again = inProgress.has(result.phase);
  } catch (cause) {
    if (alive && revision === taskRequest) taskError.value = message(cause);
  } finally {
    if (alive && revision === taskRequest) taskLoading.value = false;
  }
  if (again && alive && revision === taskRequest) poll = setTimeout(() => void fetchTask(id, revision, true), 5000);
}
async function openTask(id: string, summary?: RemakeTask) {
  changePanel('task');
  taskId.value = id;
  task.value = undefined;
  previewFailed.value = false;
  if (summary) show(summary);
  await refreshTask();
}
async function refreshTask() {
  if (!taskId.value || taskLoading.value) return;
  clearTimeout(poll);
  const revision = ++taskRequest;
  taskLoading.value = true;
  previewFailed.value = false;
  await fetchTask(taskId.value, revision, !!task.value && inProgress.has(task.value.phase));
  if (alive && revision === taskRequest && !taskError.value) previewVersion.value++;
}
async function save() {
  if (!task.value || task.value.assetId || busy.value) return;
  const current = task.value;
  busy.value = true;
  taskError.value = '';
  try {
    const { assetId } = await saveRemake(current.id);
    if (task.value?.id === current.id) task.value.assetId = assetId;
    emit('saved');
  } catch (cause) { taskError.value = message(cause); }
  finally { busy.value = false; }
}
onMounted(() => { void loadCapability(); void loadAccounts(); });
onUnmounted(() => { alive = false; clearTimeout(poll); });
</script>

<style src="./video-remake.css"></style>
