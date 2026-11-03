<template>
  <section class="placement" :class="`placement--${platform}`" :aria-label="`${name}投放效果示意`">
    <div class="placement-status" aria-hidden="true">9:41 <span v-if="platform !== 'toutiao'">● ▰</span></div>
    <header v-if="platform === 'toutiao'" class="toutiao-header" aria-hidden="true">
      <div class="toutiao-search"><div class="toutiao-search__field"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m15.5 15.5 4 4" /></svg></div><div class="toutiao-publish"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 5 9.5 3h5L16 5h4v15H4V5Z" /><circle cx="12" cy="12" r="4" /><path d="M19 2v5M16.5 4.5h5" /></svg><span>发布</span></div></div>
      <nav class="toutiao-channels"><span>关注</span><span class="is-selected">推荐</span><span>快讯</span><span>热点</span><span>视频</span><span>新时代</span><svg viewBox="0 0 18 18" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 4h12M3 9h12M3 14h12" /></svg></nav>
    </header>
    <header v-else-if="platform === 'xigua'" class="placement-top"><b>西瓜视频</b><nav>精选　电视剧　电影　短剧</nav></header>
    <header v-else-if="platform === 'huoshan'" class="placement-top"><b>抖音火山版</b><nav>关注　推荐　附近</nav></header>
    <header v-else class="placement-top"><b>内容推荐</b><nav>精选内容</nav></header>
    <div v-if="platform === 'toutiao'" class="toutiao-context toutiao-context--before" aria-hidden="true"><span /><span /><span /><span /></div>
    <article class="placement-ad">
      <div v-if="platform !== 'huoshan' && platform !== 'toutiao'" class="placement-author"><b>{{ account?.awemeName || '广告账户' }}</b><span>广告 · 效果示意</span></div>
      <h3 v-if="platform === 'toutiao' || platform === 'pangolin'">{{ copy }}</h3>
      <div class="placement-media" :class="{ 'placement-media--video':material?.type === 'VIDEO', 'placement-media--portrait': platform === 'toutiao' && portrait }">
        <video v-if="material?.type === 'VIDEO' && material.previewUrl" ref="videoElement" :key="platform + ':' + material.id + ':' + material.previewUrl" :src="material.previewUrl" :poster="material.posterUrl" :controls="platform !== 'toutiao' || playing" playsinline preload="metadata" @loadedmetadata="videoMetadata" @play="playing = true" @pause="playing = false" @ended="playing = false" @error="error = '视频暂时无法播放，请刷新预览后重试'" />
        <img v-else-if="images[index]" :key="platform + ':' + images[index]" :src="images[index]" :alt="material?.type === 'IMAGE' ? '商品卡图片' : '图文图片'" @load="imageMetadata" @error="error = '图片暂时无法加载，请刷新预览后重试'" />
        <p v-else class="placement-empty">选择素材查看预览</p>
        <button v-if="platform === 'toutiao' && material?.type === 'VIDEO' && material.previewUrl && !playing" class="toutiao-play" type="button" aria-label="播放视频" @click="playVideo"><svg viewBox="0 0 40 48" aria-hidden="true"><path d="M5 2a3 3 0 0 0-5 2v40a3 3 0 0 0 5 2l33-20a2.5 2.5 0 0 0 0-4Z" fill="currentColor" /></svg></button>
        <span v-if="material && platform !== 'toutiao'" class="placement-format">{{ material.type === 'VIDEO' ? '视频' : material.type === 'IMAGE' ? '商品卡' : '图文' }}</span>
        <div v-if="images.length > 1 && material?.type !== 'VIDEO'" class="placement-gallery"><button :disabled="index === 0" aria-label="上一张图片" @click="index--">‹</button><span>{{ index+1 }}/{{ images.length }}</span><button :disabled="index >= images.length-1" aria-label="下一张图片" @click="index++">›</button></div>
      </div>
      <audio v-if="material?.type === 'CAROUSEL' && material.previewUrl" :key="material.id + ':' + material.previewUrl" :src="material.previewUrl" controls preload="metadata" aria-label="图文配音" @error="error = '音频暂时无法播放，请刷新预览后重试'" />
      <div v-if="platform === 'huoshan'" class="placement-social" aria-hidden="true">♡<br>评论<br>分享</div>
      <h3 v-if="platform === 'xigua' || platform === 'huoshan'">{{ copy }}</h3>
      <div v-if="product && platform !== 'toutiao'" class="placement-product"><span>{{ product.name }}</span><b v-if="product.price != null">¥{{ product.price }}</b><span class="placement-cta">查看详情</span></div>
      <footer v-if="platform === 'toutiao'" class="toutiao-byline"><span>{{ account?.awemeName || '广告账户' }}</span><span>广告</span></footer>
      <footer v-else class="placement-ad-footer"><span>{{ platform === 'xigua' ? '赞　 评论　 转发' : '广告内容展示示意' }}</span></footer>
    </article>
    <div v-if="platform === 'toutiao'" class="toutiao-context toutiao-context--after" aria-hidden="true"><span /><span /><span /></div>
    <div v-if="platform === 'pangolin'" class="placement-recommendations" aria-hidden="true"><div>推荐内容示意</div><div>推荐内容示意</div></div>
    <nav v-if="platform !== 'toutiao'" class="placement-bottom" aria-hidden="true">{{ platform === 'xigua' ? '首页　放映厅　关注　我的' : platform === 'huoshan' ? '首页　同城　消息　我' : '首页　发现　我的' }}</nav>
    <p v-if="error" class="placement-error" role="alert">{{ error }}</p>
  </section>
  <p class="placement-note">{{ name }}展示位置示意；实际样式、裁切和投放位置以媒体平台为准。</p>
</template>
<script setup lang="ts">
import { computed,ref,watch } from 'vue';
import type { CreationMaterial,CreationProduct } from '@/features/delivery/creationApi';
const props=defineProps<{platform:string;material?:CreationMaterial;product?:CreationProduct;account?:{awemeName:string;avatar?:string}}>();
const emit=defineEmits<{metadata:[id:string,data:{width:number;height:number;duration:number}]}>();
const index=ref(0),error=ref('');
const videoElement=ref<HTMLVideoElement>(),playing=ref(false),mediaSize=ref<{width:number;height:number}>();
const name=computed(()=>({toutiao:'头条',xigua:'西瓜',huoshan:'抖音火山版',pangolin:'穿山甲'}[props.platform]||'媒体平台'));
const images=computed(()=>props.material?.imageUrls?.length?props.material.imageUrls:props.material?.posterUrl?[props.material.posterUrl]:[]);
const copy=computed(()=>props.material?.titles[0]||props.product?.name||'投放素材预览');
const portrait=computed(()=>{
  const width=mediaSize.value?.width||props.material?.width,height=mediaSize.value?.height||props.material?.height;
  return width && height ? height>width : props.material?.type==='VIDEO';
});
watch(()=>[props.platform,props.material?.id,props.material?.previewUrl],()=>{videoElement.value?.pause();playing.value=false;index.value=0;error.value='';mediaSize.value=undefined;});
function videoMetadata(event:Event){const v=event.target as HTMLVideoElement;mediaSize.value={width:v.videoWidth,height:v.videoHeight};if(props.material)emit('metadata',props.material.id,{width:v.videoWidth,height:v.videoHeight,duration:v.duration});}
function imageMetadata(event:Event){const image=event.target as HTMLImageElement;mediaSize.value={width:image.naturalWidth,height:image.naturalHeight};}
async function playVideo(){const video=videoElement.value;if(!video)return;try{await video.play();}catch{if(videoElement.value===video)error.value='视频暂时无法播放，请刷新预览后重试';}}
</script>
<style scoped>
.placement audio{display:block;width:100%;margin-top:10px}
.placement{position:relative;margin-top:18px;border:7px solid #1b1b27;border-radius:28px;overflow:hidden;background:#f6f6f6;color:#252631;min-height:520px;font-family:system-ui,sans-serif}.placement-status{display:flex;justify-content:space-between;padding:12px 16px 8px;font-size:11px;background:#fff}.placement-top{background:white;padding:12px 14px 0}.placement-top>b{color:#e8463f;font-size:19px}.placement-top nav{font-size:13px;padding:13px 0;border-bottom:1px solid #eee}.placement-ad{position:relative;background:white;margin:12px 0;padding:14px}.placement-author{display:flex;justify-content:space-between;gap:10px;align-items:center;margin-bottom:12px;font-size:12px}.placement-author span{font-size:10px;color:#96969e}.placement h3{font-size:15px;line-height:1.6;margin:8px 0;font-weight:600;overflow-wrap:anywhere}.placement-media{position:relative;background:#f3f3f5;overflow:hidden;border-radius:5px}.placement-media img{display:block;width:100%;max-height:420px;object-fit:contain}.placement-media video{display:block;width:100%;max-height:420px;background:#121216;aspect-ratio:16/9;object-fit:contain}.placement-format{position:absolute;right:7px;top:7px;background:#0006;color:white;font-size:10px;border-radius:5px;padding:3px 6px}.placement-gallery{position:absolute;bottom:8px;left:50%;transform:translateX(-50%);display:flex;gap:12px;align-items:center;color:white;background:#0007;border-radius:15px;padding:4px 9px;font-size:11px}.placement-gallery button{background:none;border:0;color:white;cursor:pointer}.placement-gallery button:disabled{opacity:.3}.placement-product{display:flex;gap:8px;align-items:center;padding:12px 0;font-size:11px}.placement-product>span:first-child{flex:1;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.placement-product b{color:#ed4d42;white-space:nowrap}.placement-cta{background:#f34b42;color:white;border-radius:4px;padding:6px;white-space:nowrap}.placement-ad-footer{font-size:11px;color:#92929b;padding:12px 0 0;border-top:1px solid #f3f3f3}.placement-bottom{padding:16px 8px;text-align:center;font-size:12px;background:white}.placement-note{font-size:11px;line-height:1.7;color:#8c90a7;margin-top:12px}.placement-empty{padding:70px 15px;text-align:center;font-size:13px;color:#8c90a7}.placement-error{font-size:12px;color:#d34c50;padding:12px}.placement--xigua .placement-top>b{color:#ee3447}.placement--xigua .placement-ad{padding:0 0 14px}.placement--xigua .placement-author,.placement--xigua h3,.placement--xigua .placement-product,.placement--xigua footer{margin-left:14px;margin-right:14px}.placement--huoshan{background:#15151a;color:white}.placement--huoshan .placement-top,.placement--huoshan .placement-bottom,.placement--huoshan .placement-ad{background:#15151a;color:white}.placement--huoshan .placement-status{background:#15151a;color:white}.placement--huoshan .placement-media video{aspect-ratio:9/16}.placement--huoshan .placement-top>b{color:#ff783a}.placement--huoshan .placement-social{position:absolute;right:24px;top:44%;text-align:center;line-height:3;font-size:13px;color:white;text-shadow:0 1px 5px #000}.placement--pangolin .placement-ad{margin:12px;padding:12px;border-radius:10px}.placement-recommendations{display:grid;grid-template-columns:1fr 1fr;gap:10px;padding:0 12px 15px}.placement-recommendations div{border-radius:8px;background:#e9ebef;padding:48px 4px;text-align:center;font-size:11px;color:#9a9da8}
.placement--toutiao{border:0;border-radius:12px 12px 4px 4px;min-height:578px;background:#fff;color:#333;font-family:Arial,'Microsoft YaHei',sans-serif}
.placement--toutiao .placement-status{padding:12px 28px 8px;background:#ef4043;color:#fff;font-weight:600}
.toutiao-header{background:#ef4043}
.toutiao-search{display:flex;align-items:center;gap:10px;padding:7px 14px 5px}
.toutiao-search__field{display:flex;align-items:center;flex:1;height:34px;min-width:0;border-radius:20px;background:#fff9f9;color:#666;padding:0 10px}
.toutiao-search__field svg{width:17px;height:17px}
.toutiao-publish{display:flex;flex-direction:column;align-items:center;gap:1px;color:#fff;font-size:9px;line-height:11px}
.toutiao-publish svg{width:22px;height:22px}
.toutiao-channels{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:9px 16px;background:#fff;font-size:13px;line-height:20px;white-space:nowrap}
.toutiao-channels .is-selected{position:relative;color:#f04142;font-weight:600}
.toutiao-channels .is-selected:after{position:absolute;content:'';left:0;right:0;bottom:-5px;height:2px;background:#f04142}
.toutiao-channels svg{width:15px;height:15px;flex-shrink:0;color:#aaa}
.toutiao-context{display:flex;flex-direction:column;gap:8px;margin:0 14px;padding:14px 0;pointer-events:none}
.toutiao-context span{display:block;width:68%;height:16px;background:#ececee}
.toutiao-context span:nth-child(even){width:43%;height:10px}
.toutiao-context--before{border-bottom:1px solid #f3f3f3;padding-bottom:8px}
.toutiao-context--after{margin-top:20px;border-top:1px solid #f3f3f3}
.toutiao-context--after span{width:100%}
.toutiao-context--after span:nth-child(2){width:72%;height:16px}
.placement--toutiao .placement-ad{margin:0;padding:8px 14px 10px}
.placement--toutiao h3{margin:0 0 8px;font-size:13px;line-height:20px;font-weight:400}
.placement--toutiao .placement-media{background:#f3f3f5;border-radius:6px}
.placement--toutiao .placement-media--portrait{width:148px;max-width:55%;aspect-ratio:3/4}
.placement--toutiao .placement-media--portrait :is(video,img){position:absolute;inset:0;width:100%;height:100%;max-height:none;aspect-ratio:auto;object-fit:cover}
.toutiao-play{position:absolute;inset:0;display:grid;place-items:center;width:100%;height:100%;border:0;background:transparent;color:#fff;cursor:pointer}
.toutiao-play svg{width:37px;height:45px;filter:drop-shadow(0 1px 3px #0003)}
.toutiao-play:focus-visible{outline:2px solid #575cf6;outline-offset:-3px}
.toutiao-byline{display:flex;gap:6px;margin-top:9px;color:#93969f;font-size:10px;line-height:16px}
</style>
