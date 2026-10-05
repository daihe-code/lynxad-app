<template>
  <div class="creative-workspace ad-step3">
    <CreationFrame :step="3" :account="account" :account-chips="accountChips" />
    <main class="ad-main">
      <div class="container-page">
        <section class="panel">
          <div class="panel__pad">
            <div class="panel__head">
              <div class="min-w-0">
                <h2 class="panel__title">确定投放素材</h2>
                <p class="panel__sub">
                  系统根据您选中的商品智能生成优质投放素材，保障投放效果
                </p>
              </div>
            </div>
            <p class="tip">
              <ReferenceIcon name="tipmark" /><span class="tip__text">{{
                ready
                  ? "素材已生成，点击任意素材可预览投放效果。"
                  : task?.status === "FAILED"
                    ? "素材生成未完成，可重试或返回修改投放目标。"
                    : "素材生成中，请耐心等待，生成结束将有消息提醒。"
              }}</span>
            </p>
          </div>
          <div v-if="!ready" class="panel__body">
            <div class="gen">
              <div
                class="gauge"
                :aria-label="
                  progress == null
                    ? '正在生成素材'
                    : `素材生成进度 ${progress}%`
                "
                role="img"
              >
                <svg class="gauge__svg" viewBox="0 0 104 104">
                  <circle class="gauge__track" cx="52" cy="52" r="46" />
                  <circle
                    class="gauge__arc"
                    cx="52"
                    cy="52"
                    r="46"
                    stroke-dasharray="289"
                    :stroke-dashoffset="289 - (progress || 0) * 2.89"
                  /></svg
                ><span class="gauge__mid"
                  ><span class="gauge__pct"
                    >{{ progress == null ? "…" : progress
                    }}<span v-if="progress != null" class="gauge__unit"
                      >%</span
                    ></span
                  ><span class="gauge__cap">{{
                    task?.status === "FAILED" ? "生成失败" : "生成中"
                  }}</span></span
                >
              </div>
              <p class="gen__title">
                {{ task?.errorMsg || "正在生成投放素材，素材越多耗时越长" }}
              </p>
              <p class="gen__desc">
                生成任务会在后台继续执行。完成后可在当前页面预览，也可前往素材中心查看。
              </p>
              <div class="gencards">
                <article
                  v-for="card in generationCards"
                  :key="card.key"
                  class="gencard"
                  :class="{ 'gencard--done': card.done }"
                >
                  <div class="gencard__top">
                    <span
                      class="gencard__icon"
                      :class="`gencard__icon--${card.key}`"
                      ><ReferenceIcon :name="card.icon" /></span
                    ><span
                      ><span class="gencard__name">{{ card.name }}</span
                      ><span class="gencard__count">{{
                        card.label
                      }}</span></span
                    >
                  </div>
                  <div class="gencard__bar">
                    <span
                      class="gencard__fill"
                      :class="`gencard__fill--${card.key}`"
                      :style="{ width: card.done ? '100%' : '0%' }"
                    />
                  </div>
                  <p class="gencard__foot">
                    <span
                      v-if="!card.done && card.key === 'video'"
                      class="spinner"
                    />{{ card.description }}
                  </p>
                </article>
              </div>
              <p v-if="task?.steps?.length" class="gen__hint">
                {{ "正在准备选中商品的素材，请稍候" }}
              </p>
              <button
                v-if="task?.status === 'FAILED' || !task"
                class="btn-next-primary"
                :disabled="saving"
                @click="emit('retry')"
              >
                重新生成素材
              </button>
              <p class="gen__hint">
                <button class="gen__link" @click="emit('prev')">
                  返回上一步调整投放目标</button
                >，也可前往<router-link class="gen__link" to="/creatives"
                  >素材中心</router-link
                >管理历史素材
              </p>
            </div>
          </div>
          <div v-else class="panel__pad">
            <div class="srow">
              <p class="srow__label">素材可见性</p>
              <div class="srow__body">
                <div class="seg seg--lg">
                  <button class="seg__item" disabled>
                    抖音主页频次可见<span class="seg__badge">推荐</span></button
                  ><button class="seg__item" disabled>抖音主页始终可见</button>
                </div>
                <p class="srow__hint">
                  素材可见性采用平台默认设置，暂不支持单独修改
                </p>
              </div>
            </div>
            <div class="srow">
              <p class="srow__label">素材对应商品</p>
              <div class="srow__body">
                <select
                  v-model="productId"
                  class="pick"
                  aria-label="素材对应商品"
                >
                  <option
                    v-for="p in selected"
                    :key="p.qcProductId"
                    :value="p.qcProductId"
                  >
                    {{ p.name }}
                  </option>
                </select>
                <p class="srow__hint">下拉可切换查询各商品对应素材情况</p>
              </div>
            </div>
            <div class="mblock">
              <div class="mblock__head">
                <p class="mblock__label">素材和标题</p>
                <p class="mblock__count">
                  已添加：素材 <b>{{ videoItems.length + imageItems.length }}</b
                  >/100，标题 <b>{{ titles.length }}</b
                  >/30
                </p>
              </div>
              <p class="mblock__hint">
                点击素材可预览投放效果，也可上传文件替换对应商品的素材
              </p>
              <div class="tabs" role="tablist" aria-label="素材类型">
                <button
                  v-for="tab in tabs"
                  :key="tab.key"
                  class="tabs__item"
                  role="tab"
                  :aria-selected="activeTab === tab.key"
                  @click="
                    activeTab = tab.key;
                    activeIndex = 0;
                  "
                >
                  {{ tab.name }}
                  <span class="tabs__count">{{ tab.count }}</span>
                </button>
              </div>
              <div class="work">
                <div class="work__main">
                  <div v-if="activeTab !== 'title'" class="matlist" role="list">
                    <article
                      v-for="(item, i) in visibleItems"
                      :key="item.productId"
                      class="matrow"
                      :class="{ 'matrow--active': activeIndex === i }"
                      role="listitem"
                      tabindex="0"
                      @click="activeIndex = i"
                      @keydown.enter="activeIndex = i"
                    >
                      <span class="matrow__thumb"
                        ><img
                          v-if="item.posterUrl"
                          :src="mediaUrl(item.posterUrl)"
                          alt="" /><ReferenceIcon
                          v-else
                          :name="
                            activeTab === 'video' ? 'video' : 'image'
                          " /></span
                      ><span class="matrow__body"
                        ><span class="matrow__title"
                          >{{ productName(item.productId) }} ·
                          {{
                            activeTab === "video" ? "视频" : "商品卡图片"
                          }}</span
                        ><span class="matrow__meta"
                          >对应商品：{{ item.productId }}</span
                        ><span class="matrow__tag">智能生成</span></span
                      ><span class="matrow__ops"
                        ><button
                          :disabled="saving"
                          aria-label="替换素材"
                          @click.stop="openReplacement(item.productId)"
                        >
                          <ReferenceIcon name="edit" /></button
                      ></span>
                    </article>
                    <p v-if="!visibleItems.length" class="workspace-empty">
                      {{
                        activeTab === "article"
                          ? "当前素材包暂无图文素材"
                          : "此商品暂无该类型素材"
                      }}
                    </p>
                    <button class="addrow" @click="openAdd">
                      <ReferenceIcon name="plus" />新增素材
                    </button>
                  </div>
                  <div v-else>
                    <section
                      v-for="name in ['视频标题', '商品卡标题']"
                      :key="name"
                      class="tgroup"
                    >
                      <div class="tgroup__head">
                        <span class="tgroup__name">{{ name }}</span
                        ><span class="tgroup__added"
                          >已添加：{{
                            name === "视频标题" ? titles.length : 0
                          }}</span
                        >
                      </div>
                      <p class="tgroup__sub">
                        {{
                          name === "视频标题"
                            ? "配视频素材，在短视频信息流场景展示"
                            : "配商品卡素材，在抖音商城、搜索等场景展示"
                        }}
                      </p>
                      <div class="trows">
                        <div
                          v-for="(title, i) in name === '视频标题'
                            ? titles.slice(0, 3)
                            : []"
                          :key="i"
                          class="trow"
                        >
                          <span class="trow__text">{{ title }}</span
                          ><span class="trow__count">{{ title.length }}/55</span
                          ><span class="trow__ops"
                            ><button
                              disabled
                              aria-label="编辑标题"
                              title="暂不支持单独编辑标题"
                            >
                              <ReferenceIcon name="edit" /></button
                          ></span>
                        </div>
                        <div class="trow">
                          <input
                            class="trow__box"
                            disabled
                            :placeholder="
                              name === '视频标题'
                                ? '标题暂不支持单独修改'
                                : '暂无商品卡标题'
                            "
                          /><span class="trow__count">0/55</span>
                        </div>
                      </div>
                    </section>
                  </div>
                </div>
                <aside class="work__side" aria-label="投放效果预览">
                  <div class="preview">
                    <p class="preview__title">投放效果预览</p>
                    <div class="plats" role="tablist" aria-label="预览平台">
                      <button
                        v-for="(name, i) in platforms"
                        :key="name"
                        class="plat"
                        role="tab"
                        :aria-label="name"
                        :aria-selected="platform === i"
                        :title="name"
                        @click="platform = i"
                      >
                        <span
                          class="plat__i"
                          :class="`plat--${platformKeys[i]}`"
                          ><ReferenceIcon
                            v-if="i !== 1"
                            :name="`plat-${platformKeys[i]}`"
                          /><span v-else class="plat__txt">头条</span></span
                        >
                      </button>
                    </div>
                    <div class="phone">
                      <div class="phone__screen">
                        <div class="phone__media">
                          <video
                            v-if="activeTab === 'video' && previewItem?.playUrl"
                            :key="previewItem.playUrl"
                            :src="mediaUrl(previewItem.playUrl)"
                            :poster="mediaUrl(previewItem.posterUrl)"
                            controls
                            playsinline
                          /><img
                            v-else-if="previewItem?.posterUrl"
                            :src="mediaUrl(previewItem.posterUrl)"
                            alt="素材预览"
                          /><span v-else>暂无预览素材</span>
                        </div>
                        <div class="phone__hots">
                          <p class="phone__name">
                            {{ currentProduct?.name || "请选择素材" }}
                          </p>
                          <p class="phone__price">
                            {{ amount(currentProduct?.price, "¥") }}
                          </p>
                          <p class="phone__buy">立即购买</p>
                        </div>
                      </div>
                    </div>
                    <p class="preview__note">
                      *仅供预览，最终效果以{{ platforms[platform] }}平台为准
                    </p>
                  </div>
                </aside>
              </div>
            </div>
          </div>
        </section>
        <section v-if="ready" class="panel plan">
          <div class="plan__pad">
            <div class="plan__row">
              <label class="plan__label" for="creation-name"
                >投放计划名称</label
              >
              <div class="plan__body">
                <input
                  id="creation-name"
                  class="plan__input"
                  :value="planName"
                  maxlength="30"
                  placeholder="请输入自定义计划名称"
                  @input="
                    emit(
                      'update:planName',
                      ($event.target as HTMLInputElement).value,
                    )
                  "
                />
                <p class="plan__hint">
                  系统自动生成，主要用于后续跟踪查看，可修改 ·
                  {{ planName.length }}/30
                </p>
              </div>
            </div>
          </div>
        </section>
      </div>
    </main>
    <div class="actionbar">
      <div class="container-page actionbar__inner">
        <span class="actionbar__tip">{{
          ready
            ? confirmed
              ? "素材已确认，可发布投放"
              : "发布时将确认当前素材包"
            : "生成期间可以继续其他操作"
        }}</span
        ><button class="btn-prev" :disabled="saving" @click="emit('prev')">
          上一步</button
        ><button
          class="btn-next-primary"
          :disabled="saving || !ready || !planName.trim()"
          @click="emit('publish')"
        >
          {{ saving ? "发布中…" : "发布投放" }}
        </button>
      </div>
    </div>
    <ReferenceModal
      v-if="modalOpen"
      :title="replacementId ? '替换素材' : '新增素材'"
      :busy="saving"
      @close="modalOpen = false"
      ><div class="tabs">
        <button
          v-for="t in ['video', 'image', 'article'] as const"
          :key="t"
          class="tabs__item"
          :aria-selected="modalTab === t"
          :disabled="!!replacementId"
          @click="modalTab = t"
        >
          {{
            t === "video"
              ? "新增视频"
              : t === "image"
                ? "新增商品卡图片"
                : "新增图文"
          }}
        </button>
      </div>
      <div class="source-tabs">
        <button
          v-for="s in sources"
          :key="s.key"
          :aria-pressed="source === s.key"
          @click="source = s.key"
        >
          {{ s.name }}
        </button>
      </div>
      <template v-if="source === 'local' && modalTab !== 'article'"
        ><p>选择要替换的商品素材，上传后需重新确认素材包。</p>
        <select v-model="replacementId" aria-label="替换素材对应商品">
          <option value="">请选择商品</option>
          <option
            v-for="p in selected"
            :key="p.qcProductId"
            :value="p.qcProductId"
          >
            {{ p.name }}
          </option></select
        ><label class="upload-drop"
          ><ReferenceIcon name="upload" /><span
            >点击选择本地{{ modalTab === "video" ? "视频" : "图片" }}文件</span
          ><input
            type="file"
            :accept="
              modalTab === 'video' ? 'video/mp4' : 'image/jpeg,image/png'
            "
            @change="pickFile"
        /></label>
        <p>
          {{ file?.name || "支持 MP4 视频、JPG / PNG 图片，文件不超过 100MB" }}
        </p></template
      ><template v-else
        ><p v-if="modalTab === 'article'">图文名称</p>
        <input
          v-if="modalTab === 'article'"
          class="finput"
          maxlength="50"
          disabled
          placeholder="图文素材暂未支持生成或上传"
        />
        <p class="workspace-empty">
          {{
            source === "smart"
              ? "当前支持整包智能生成，暂不支持追加单条素材"
              : source === "lib"
                ? "素材库内容可在素材中心预览，暂不支持绑定到此素材包"
                : "图文素材暂不支持追加上传"
          }}
        </p>
        <router-link
          v-if="source === 'lib'"
          class="panel__toggle"
          to="/creatives"
          >前往素材中心</router-link
        ></template
      ><template #footer
        ><button class="btn-prev" :disabled="saving" @click="modalOpen = false">
          取消</button
        ><button
          class="btn-next-primary"
          :disabled="
            saving ||
            source !== 'local' ||
            modalTab === 'article' ||
            !file ||
            !replacementId
          "
          @click="replace"
        >
          {{ saving ? "上传中…" : "提交" }}
        </button></template
      ></ReferenceModal
    >
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import type { TaskVo } from "@/features/creative/creativeApi";
import type { ProductVo } from "@/features/catalog/catalogApi";
import { amount, mediaUrl } from "../reference/planPresentation";
import CreationFrame from "../reference/CreationFrame.vue";
import ReferenceIcon from "../reference/ReferenceIcon.vue";
import ReferenceModal from "../reference/ReferenceModal.vue";
import "../reference/creative-workspace.css";
const props = defineProps<{
  task: TaskVo | null;
  saving: boolean;
  confirmed: boolean;
  replacementVersion: number;
  planName: string;
  selected: ProductVo[];
  account?: { awemeName: string; awemeId: string } | null;
  accountChips: string[];
}>();
const emit = defineEmits<{
  "update:planName": [value: string];
  prev: [];
  retry: [];
  confirm: [];
  publish: [];
  replace: [productId: string, file: File];
}>();
const activeTab = ref<"video" | "image" | "article" | "title">("video"),
  productId = ref(""),
  activeIndex = ref(0),
  platform = ref(0),
  modalOpen = ref(false),
  modalTab = ref<"video" | "image" | "article">("video"),
  source = ref("smart"),
  replacementId = ref(""),
  file = ref<File>();
const platforms = ["抖音", "今日头条", "西瓜视频", "抖音火山版", "抖音极速版"],
  platformKeys = ["douyin", "toutiao", "xigua", "huoshan", "jisu"],
  sources = [
    { key: "smart", name: "智能生成" },
    { key: "local", name: "本地上传" },
    { key: "lib", name: "素材库选择" },
  ];
const ready = computed(() => props.task?.status === "SUCCEEDED");
const videoItems = computed(
    () => props.task?.items?.filter((i) => !!i.playUrl) || [],
  ),
  imageItems = computed(
    () => props.task?.items?.filter((i) => !!i.posterUrl) || [],
  ),
  titles = computed(
    () => props.task?.items?.flatMap((i) => i.titles || []) || [],
  );
const tabs = computed(() => [
  { key: "video" as const, name: "视频", count: videoItems.value.length },
  { key: "image" as const, name: "图片", count: imageItems.value.length },
  { key: "article" as const, name: "图文", count: 0 },
  { key: "title" as const, name: "标题", count: titles.value.length },
]);
const visibleItems = computed(() =>
  (activeTab.value === "video"
    ? videoItems.value
    : activeTab.value === "image"
      ? imageItems.value
      : []
  ).filter((i) => !productId.value || i.productId === productId.value),
);
const previewItem = computed(() => visibleItems.value[activeIndex.value]);
const currentProduct = computed(() =>
  props.selected.find(
    (p) => p.qcProductId === (previewItem.value?.productId || productId.value),
  ),
);
const progress = computed(() => {
  const steps = props.task?.steps || [];
  if (!steps.length) return null;
  return Math.round(
    (steps.filter((s) => /:SUCCEEDED$/.test(s)).length / steps.length) * 100,
  );
});
const generationCards = computed(() => [
  {
    key: "video",
    name: "短视频",
    icon: "video",
    label: `${videoItems.value.length}/${props.selected.length} 已生成`,
    done: ready.value,
    description:
      props.task?.status === "FAILED" ? "生成失败" : "正在生成视频素材…",
  },
  {
    key: "image",
    name: "图片",
    icon: "image",
    label: `${imageItems.value.length} 张已生成`,
    done: ready.value && imageItems.value.length > 0,
    description: "视频封面及商品卡图片",
  },
  {
    key: "article",
    name: "图文",
    icon: "article",
    label: "暂无生成任务",
    done: false,
    description: "当前标准素材包不包含图文",
  },
]);
function productName(id: string) {
  return props.selected.find((p) => p.qcProductId === id)?.name || id;
}
watch(
  () => props.selected,
  () => {
    if (!props.selected.some((p) => p.qcProductId === productId.value))
      productId.value = props.selected[0]?.qcProductId || "";
  },
  { immediate: true },
);
watch(productId, () => (activeIndex.value = 0));
watch(
  () => props.replacementVersion,
  () => {
    modalOpen.value = false;
    file.value = undefined;
  },
);
function openAdd() {
  replacementId.value = "";
  file.value = undefined;
  modalTab.value =
    activeTab.value === "image"
      ? "image"
      : activeTab.value === "article"
        ? "article"
        : "video";
  source.value = "smart";
  modalOpen.value = true;
}
function openReplacement(id: string) {
  replacementId.value = id;
  file.value = undefined;
  modalTab.value = activeTab.value === "image" ? "image" : "video";
  source.value = "local";
  modalOpen.value = true;
}
function pickFile(event: Event) {
  const picked = (event.target as HTMLInputElement).files?.[0];
  if (!picked) return;
  const valid =
    modalTab.value === "video"
      ? picked.type === "video/mp4" && /\.mp4$/i.test(picked.name)
      : ["image/jpeg", "image/png"].includes(picked.type) &&
        /\.(jpe?g|png)$/i.test(picked.name);
  if (!valid || picked.size <= 0 || picked.size > 100 * 1024 * 1024) {
    ElMessage.warning("请选择正确类型的文件，大小需在 0–100MB 之间");
    file.value = undefined;
    return;
  }
  file.value = picked;
}
function replace() {
  if (file.value && replacementId.value)
    emit("replace", replacementId.value, file.value);
}
</script>
<style scoped>
.tip > svg {
  width: 17px;
  height: 17px;
  flex-shrink: 0;
}
.matrow__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
}
.matrow__thumb svg {
  width: 28px;
  height: 28px;
}
.matrow__ops svg,
.addrow svg {
  width: 16px;
  height: 16px;
}
.workspace-empty {
  padding: 24px 0;
  color: #7c7f99;
  font-size: 13px;
}
.phone__media {
  overflow: hidden;
}
.phone__media video,
.phone__media img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.source-tabs {
  display: flex;
  gap: 12px;
  margin: 24px 0;
}
.source-tabs button {
  padding: 10px 14px;
  border: 1px solid #e6e8f2;
  border-radius: 8px;
  background: #fff;
  color: #4e5069;
}
.source-tabs button[aria-pressed="true"] {
  color: #575cf6;
  border-color: #575cf6;
  background: #f7f7ff;
}
.upload-drop {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  border: 1px dashed #b6b8ce;
  border-radius: 10px;
  padding: 30px;
  margin-top: 16px;
  color: #7c7f99;
}
.upload-drop > svg {
  width: 32px;
  height: 32px;
}
.upload-drop input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}
.reference-modal select {
  display: block;
  width: 100%;
  border: 1px solid #e6e8f2;
  border-radius: 8px;
  padding: 12px;
}
.gencard__top > span {
  min-width: 0;
}
.gencard__count {
  display: block;
}
.gen__link {
  border: 0;
  background: none;
  font: inherit;
  cursor: pointer;
}
.matrow__title,
.phone__name {
  overflow-wrap: anywhere;
}
@media (max-width: 767px) {
  .work {
    display: flex;
    flex-direction: column;
  }
  .work__side {
    width: 100%;
  }
  .gencards {
    grid-template-columns: 1fr;
  }
  .srow,
  .plan__row {
    display: block;
  }
  .srow__label,
  .plan__label {
    display: block;
    margin-bottom: 12px;
  }
  .plan__body {
    width: 100%;
  }
  .source-tabs {
    flex-wrap: wrap;
  }
  .actionbar__inner {
    flex-wrap: wrap;
    gap: 12px;
  }
  .actionbar__tip {
    width: 100%;
  }
}
</style>
