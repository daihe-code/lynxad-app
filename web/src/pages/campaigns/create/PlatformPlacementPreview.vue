<template>
  <section class="placement" :class="`placement--${platform}`" :aria-label="`${name}投放效果示意`">
    <div v-if="platform !== 'pangolin'" class="placement-status" aria-hidden="true">9:41 <span v-if="platform === 'xigua'" class="xigua-status-icons"><FeedPreviewIcon name="signal" /><FeedPreviewIcon name="wifi" /><i /></span><span v-else-if="platform !== 'toutiao'">● ▰</span></div>
    <header v-if="platform === 'toutiao'" class="toutiao-header" aria-hidden="true">
      <div class="toutiao-search"><div class="toutiao-search__field"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m15.5 15.5 4 4" /></svg></div><div class="toutiao-publish"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 5 9.5 3h5L16 5h4v15H4V5Z" /><circle cx="12" cy="12" r="4" /><path d="M19 2v5M16.5 4.5h5" /></svg><span>发布</span></div></div>
      <nav class="toutiao-channels"><span>关注</span><span class="is-selected">推荐</span><span>快讯</span><span>热点</span><span>视频</span><span>新时代</span><svg viewBox="0 0 18 18" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 4h12M3 9h12M3 14h12" /></svg></nav>
    </header>
    <header v-else-if="platform === 'xigua'" class="xigua-header" aria-hidden="true"><div class="xigua-search"><FeedPreviewIcon name="search" /></div><nav class="xigua-channels"><b>精选</b><span>电视剧</span><span>抗疫</span><span>好声音</span><svg viewBox="0 0 18 18" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 4h12M3 8h12M3 12h8M14 10v6M11 13h6" /></svg></nav></header>
    <header v-else-if="platform === 'huoshan'" class="placement-top"><b>抖音火山版</b><nav>关注　推荐　附近</nav></header>
    <div v-if="platform === 'toutiao'" class="toutiao-context toutiao-context--before" aria-hidden="true"><span /><span /><span /><span /></div>
    <article class="placement-ad">
      <h3 v-if="platform === 'toutiao'">{{ copy }}</h3>
      <div class="placement-media" :class="{ 'placement-media--video':material?.type === 'VIDEO', 'placement-media--portrait': (platform === 'toutiao' || platform === 'xigua') && portrait }" :style="platform === 'pangolin' ? { aspectRatio: mediaAspectRatio } : undefined">
        <video v-if="material?.type === 'VIDEO' && material.previewUrl" ref="videoElement" :key="platform + ':' + material.id + ':' + material.previewUrl" :src="material.previewUrl" :poster="material.posterUrl" :muted="muted" :controls="platform !== 'xigua' && ((platform !== 'toutiao' && platform !== 'pangolin') || playing)" playsinline preload="metadata" @click="platform === 'xigua' && toggleMedia()" @loadedmetadata="videoMetadata" @timeupdate="updateTime" @play="playing = true" @pause="playing = false" @ended="playing = false" @error="error = '视频暂时无法播放，请刷新预览后重试'" />
        <img v-else-if="images[index]" :key="platform + ':' + images[index]" :src="images[index]" :alt="material?.type === 'IMAGE' ? '商品卡图片' : '图文图片'" @load="imageMetadata" @error="error = '图片暂时无法加载，请刷新预览后重试'" />
        <p v-else class="placement-empty">选择素材查看预览</p>
        <button v-if="platform === 'toutiao' && material?.type === 'VIDEO' && material.previewUrl && !playing" class="toutiao-play" type="button" aria-label="播放视频" @click="playVideo"><svg viewBox="0 0 40 48" aria-hidden="true"><path d="M5 2a3 3 0 0 0-5 2v40a3 3 0 0 0 5 2l33-20a2.5 2.5 0 0 0 0-4Z" fill="currentColor" /></svg></button>
        <button v-if="platform === 'pangolin' && material?.type === 'VIDEO' && material.previewUrl && !playing" class="pangolin-play" type="button" aria-label="播放视频" @click="playVideo"><svg viewBox="0 0 40 48" aria-hidden="true"><path d="M5 2a3 3 0 0 0-5 2v40a3 3 0 0 0 5 2l33-20a2.5 2.5 0 0 0 0-4Z" fill="currentColor" /></svg></button>
        <button v-if="platform === 'xigua' && material?.type === 'VIDEO' && material.previewUrl && !playing" class="xigua-play" type="button" :disabled="playPending" aria-label="播放视频" @click="toggleMedia"><FeedPreviewIcon name="play" solid /></button>
        <template v-if="platform === 'pangolin' && material"><span class="pangolin-ad-mark" aria-hidden="true"><ReferenceIcon name="plat-pangolin" />广告</span><span class="pangolin-close" aria-hidden="true">×</span></template>
        <span v-if="material && platform === 'huoshan'" class="placement-format">{{ material.type === 'VIDEO' ? '视频' : material.type === 'IMAGE' ? '商品卡' : '图文' }}</span>
        <div v-if="images.length > 1 && material?.type !== 'VIDEO'" class="placement-gallery"><button :disabled="index === 0" aria-label="上一张图片" @click="index--">‹</button><span>{{ index+1 }}/{{ images.length }}</span><button :disabled="index >= images.length-1" aria-label="下一张图片" @click="index++">›</button></div>
      </div>
      <audio v-if="platform !== 'pangolin' && material?.type === 'CAROUSEL' && material.previewUrl" ref="audioElement" :key="platform + ':' + material.id + ':' + material.previewUrl" :src="material.previewUrl" :muted="muted" controls preload="metadata" aria-label="图文配音" @loadedmetadata="audioMetadata" @timeupdate="updateTime" @play="playing = true" @pause="playing = false" @ended="playing = false" @error="error = '音频暂时无法播放，请刷新预览后重试'" />
      <div v-if="platform === 'huoshan'" class="placement-social" aria-hidden="true">♡<br>评论<br>分享</div>
      <template v-if="platform === 'xigua' && material">
        <aside class="xigua-social" aria-hidden="true"><FeedPreviewIcon name="thumb-up" solid /><FeedPreviewIcon name="message-square" solid /><FeedPreviewIcon name="save" solid /><FeedPreviewIcon name="more" /></aside>
        <div class="xigua-caption"><div class="xigua-account"><img v-if="account?.avatar && !avatarBroken" :src="account.avatar" alt="账号头像" @error="avatarBroken = true" /><span v-else class="xigua-avatar">{{ accountName.slice(0, 1) }}</span><b>{{ accountName }}</b></div><p>{{ copy }}</p></div>
      </template>
      <h3 v-if="platform === 'huoshan' || platform === 'pangolin'">{{ copy }}</h3>
      <div v-if="product && platform === 'huoshan'" class="placement-product"><span>{{ product.name }}</span><b v-if="product.price != null">¥{{ product.price }}</b><span class="placement-cta">查看详情</span></div>
      <footer v-if="platform === 'toutiao'" class="toutiao-byline"><span>{{ account?.awemeName || '广告账户' }}</span><span>广告</span></footer>
      <footer v-else-if="platform === 'huoshan'" class="placement-ad-footer"><span>广告内容展示示意</span></footer>
    </article>
    <template v-if="platform === 'pangolin'"><div v-for="card in 3" :key="card" class="pangolin-placeholder" aria-hidden="true"><div class="pangolin-placeholder__media" /><div class="pangolin-placeholder__title" /></div></template>
    <audio v-if="platform === 'pangolin' && material?.type === 'CAROUSEL' && material.previewUrl" :key="material.id + ':' + material.previewUrl" :src="material.previewUrl" class="pangolin-audio" controls preload="metadata" aria-label="图文配音" @error="error = '音频暂时无法播放，请刷新预览后重试'" />
    <div v-if="platform === 'toutiao'" class="toutiao-context toutiao-context--after" aria-hidden="true"><span /><span /><span /></div>
    <nav v-if="platform === 'xigua'" class="xigua-bottom" aria-hidden="true"><span class="is-active"><FeedPreviewIcon name="home" solid />首页</span><span><FeedPreviewIcon name="screen" />影视剧</span><span><FeedPreviewIcon name="plus-circle" />发布</span><span><FeedPreviewIcon name="message-square" />消息</span><span><FeedPreviewIcon name="profile" />我的</span></nav>
    <nav v-else-if="platform === 'huoshan'" class="placement-bottom" aria-hidden="true">首页　同城　消息　我</nav>
    <p v-if="error" class="placement-error" role="alert">{{ error }}</p>
  </section>
  <div v-if="platform === 'xigua' && hasPlayableMedia" class="xigua-controls"><div><button type="button" :disabled="playPending" :aria-label="playing ? '暂停播放' : material?.type === 'VIDEO' ? '播放视频' : '播放图文配音'" @click="toggleMedia"><ReferenceIcon :name="playing ? 'pause' : 'play'" /></button><span>{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span><button type="button" :aria-label="muted ? '开启声音' : '静音'" :aria-pressed="muted" @click="muted = !muted"><FeedPreviewIcon :name="muted ? 'muted' : 'volume'" /></button></div><input type="range" :min="0" :max="duration || 1" :value="currentTime" :disabled="!duration" step="0.1" aria-label="播放进度" @input="seek" /></div>
  <p v-if="platform === 'toutiao' || platform === 'huoshan'" class="placement-note">{{ name }}展示位置示意；实际样式、裁切和投放位置以媒体平台为准。</p>
</template>
<script setup lang="ts">
import { computed,onBeforeUnmount,ref,watch } from 'vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import FeedPreviewIcon from './FeedPreviewIcon.vue';
import { materialPreviewCopy } from '@/features/delivery/materialPreviewCopy';
import type { CreationMaterial,CreationProduct } from '@/features/delivery/creationApi';
const props=defineProps<{platform:string;material?:CreationMaterial;product?:CreationProduct;title?:string;account?:{awemeName:string;avatar?:string}}>();
const emit=defineEmits<{metadata:[id:string,data:{width:number;height:number;duration:number}]}>();
const index=ref(0),error=ref('');
const videoElement=ref<HTMLVideoElement>(),audioElement=ref<HTMLAudioElement>(),playing=ref(false),mediaSize=ref<{width:number;height:number}>();
const muted=ref(false),currentTime=ref(0),duration=ref(0),playPending=ref(false),avatarBroken=ref(false);
const accountName=computed(()=>props.account?.awemeName||'商品推广');
const activeMedia=computed(()=>props.material?.type==='VIDEO'?videoElement.value:props.material?.type==='CAROUSEL'?audioElement.value:undefined);
const hasPlayableMedia=computed(()=>!!props.material?.previewUrl&&(props.material.type==='VIDEO'||props.material.type==='CAROUSEL'));
const name=computed(()=>({toutiao:'头条',xigua:'西瓜',huoshan:'抖音火山版',pangolin:'穿山甲'}[props.platform]||'媒体平台'));
const images=computed(()=>props.material?.imageUrls?.length?props.material.imageUrls:props.material?.posterUrl?[props.material.posterUrl]:[]);
const copy=computed(()=>props.platform==='xigua'?materialPreviewCopy(props.material,props.product,props.title):props.material?.titles[0]||props.product?.name||'投放素材预览');
const portrait=computed(()=>{
  const width=mediaSize.value?.width||props.material?.width,height=mediaSize.value?.height||props.material?.height;
  return width && height ? height>width : props.material?.type==='VIDEO';
});
const mediaAspectRatio=computed(()=>{
  const width=mediaSize.value?.width||props.material?.width,height=mediaSize.value?.height||props.material?.height;
  return width && height ? `${width} / ${height}` : props.material?.type==='VIDEO' ? '9 / 16' : '1';
});
watch(()=>[props.platform,props.material?.id,props.material?.previewUrl],()=>{videoElement.value?.pause();audioElement.value?.pause();playing.value=false;playPending.value=false;currentTime.value=0;duration.value=0;index.value=0;error.value='';mediaSize.value=undefined;});
watch(()=>props.account?.avatar,()=>{avatarBroken.value=false;});
onBeforeUnmount(()=>{videoElement.value?.pause();audioElement.value?.pause();});
function videoMetadata(event:Event){const v=event.target as HTMLVideoElement;mediaSize.value={width:v.videoWidth,height:v.videoHeight};duration.value=Number.isFinite(v.duration)?v.duration:0;if(props.material)emit('metadata',props.material.id,{width:v.videoWidth,height:v.videoHeight,duration:v.duration});}
function audioMetadata(event:Event){const media=event.target as HTMLAudioElement;duration.value=Number.isFinite(media.duration)?media.duration:0;}
function updateTime(event:Event){currentTime.value=(event.target as HTMLMediaElement).currentTime;}
function formatTime(value:number){const seconds=Math.floor(value||0);return `${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,'0')}`;}
function seek(event:Event){if(activeMedia.value&&duration.value)activeMedia.value.currentTime=Number((event.target as HTMLInputElement).value);}
async function toggleMedia(){const media=activeMedia.value;if(!media||playPending.value)return;if(!media.paused){media.pause();return;}error.value='';playPending.value=true;try{await media.play();}catch(cause){if(activeMedia.value===media&&!(cause instanceof DOMException&&cause.name==='AbortError'))error.value='素材暂时无法播放，请刷新预览后重试';}finally{if(activeMedia.value===media)playPending.value=false;}}
function imageMetadata(event:Event){const image=event.target as HTMLImageElement;mediaSize.value={width:image.naturalWidth,height:image.naturalHeight};}
async function playVideo(){const video=videoElement.value;if(!video)return;try{await video.play();}catch{if(videoElement.value===video)error.value='视频暂时无法播放，请刷新预览后重试';}}
</script>
<style scoped>
.placement audio{display:block;width:100%;margin-top:10px}
.placement{position:relative;margin-top:18px;border:7px solid #1b1b27;border-radius:28px;overflow:hidden;background:#f6f6f6;color:#252631;min-height:520px;font-family:system-ui,sans-serif}.placement-status{display:flex;justify-content:space-between;padding:12px 16px 8px;font-size:11px;background:#fff}.placement-top{background:white;padding:12px 14px 0}.placement-top>b{color:#e8463f;font-size:19px}.placement-top nav{font-size:13px;padding:13px 0;border-bottom:1px solid #eee}.placement-ad{position:relative;background:white;margin:12px 0;padding:14px}.placement-author{display:flex;justify-content:space-between;gap:10px;align-items:center;margin-bottom:12px;font-size:12px}.placement-author span{font-size:10px;color:#96969e}.placement h3{font-size:15px;line-height:1.6;margin:8px 0;font-weight:600;overflow-wrap:anywhere}.placement-media{position:relative;background:#f3f3f5;overflow:hidden;border-radius:5px}.placement-media img{display:block;width:100%;max-height:420px;object-fit:contain}.placement-media video{display:block;width:100%;max-height:420px;background:#121216;aspect-ratio:16/9;object-fit:contain}.placement-format{position:absolute;right:7px;top:7px;background:#0006;color:white;font-size:10px;border-radius:5px;padding:3px 6px}.placement-gallery{position:absolute;bottom:8px;left:50%;transform:translateX(-50%);display:flex;gap:12px;align-items:center;color:white;background:#0007;border-radius:15px;padding:4px 9px;font-size:11px}.placement-gallery button{background:none;border:0;color:white;cursor:pointer}.placement-gallery button:disabled{opacity:.3}.placement-product{display:flex;gap:8px;align-items:center;padding:12px 0;font-size:11px}.placement-product>span:first-child{flex:1;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.placement-product b{color:#ed4d42;white-space:nowrap}.placement-cta{background:#f34b42;color:white;border-radius:4px;padding:6px;white-space:nowrap}.placement-ad-footer{font-size:11px;color:#92929b;padding:12px 0 0;border-top:1px solid #f3f3f3}.placement-bottom{padding:16px 8px;text-align:center;font-size:12px;background:white}.placement-note{font-size:11px;line-height:1.7;color:#8c90a7;margin-top:12px}.placement-empty{padding:70px 15px;text-align:center;font-size:13px;color:#8c90a7}.placement-error{font-size:12px;color:#d34c50;padding:12px}.placement--xigua .placement-top>b{color:#ee3447}.placement--xigua .placement-ad{padding:0 0 14px}.placement--xigua .placement-author,.placement--xigua h3,.placement--xigua .placement-product,.placement--xigua footer{margin-left:14px;margin-right:14px}.placement--huoshan{background:#15151a;color:white}.placement--huoshan .placement-top,.placement--huoshan .placement-bottom,.placement--huoshan .placement-ad{background:#15151a;color:white}.placement--huoshan .placement-status{background:#15151a;color:white}.placement--huoshan .placement-media video{aspect-ratio:9/16}.placement--huoshan .placement-top>b{color:#ff783a}.placement--huoshan .placement-social{position:absolute;right:24px;top:44%;text-align:center;line-height:3;font-size:13px;color:white;text-shadow:0 1px 5px #000}
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
.placement--pangolin{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;min-height:0;padding:14px 12px;border:0;border-radius:6px;background:#f2f3f5;font-family:Arial,'Microsoft YaHei',sans-serif}
.placement--pangolin .placement-ad,.pangolin-placeholder{min-width:0;margin:0;padding:12px;background:#fff;border-radius:2px}
.placement--pangolin .placement-media{border-radius:5px}
.placement--pangolin .placement-media :is(video,img){position:absolute;inset:0;width:100%;height:100%;max-height:none;aspect-ratio:auto;object-fit:contain}
.placement--pangolin h3{display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden;min-height:32px;margin:4px 0 0;font-size:11px;line-height:16px;font-weight:400;color:#666}
.pangolin-placeholder__media{aspect-ratio:9/16;background:#f5f5f8}
.pangolin-placeholder__title{height:18px;margin-top:8px;background:#f5f5f8}
.pangolin-play{position:absolute;inset:0;display:grid;place-items:center;width:100%;height:100%;padding:0;border:0;background:transparent;color:#fff;cursor:pointer}
.pangolin-play svg{width:40px;max-width:45%;height:48px;filter:drop-shadow(0 1px 3px #0003)}
.pangolin-play:focus-visible{outline:2px solid #575cf6;outline-offset:-3px}
.pangolin-ad-mark{position:absolute;left:4px;top:4px;display:flex;align-items:center;gap:2px;color:#fff;font-size:8px;line-height:12px;text-shadow:0 1px 3px #0009;pointer-events:none}
.pangolin-ad-mark svg{width:12px;height:12px}
.pangolin-close{position:absolute;right:4px;top:5px;width:11px;height:11px;border-radius:50%;background:#0004;color:#fff;font-size:11px;line-height:10px;text-align:center;pointer-events:none}
.placement--pangolin .placement-empty{margin:0;padding:32px 5px;font-size:11px}
.placement--pangolin .placement-error{grid-column:1/-1;margin:0;padding:0}
.placement--pangolin .pangolin-audio{grid-column:1/-1;min-width:0;margin:0}
.placement--xigua{aspect-ratio:9/16;min-height:0;border:0;border-radius:5px;background:#111;color:#fff;font-family:Arial,'Microsoft YaHei',sans-serif;isolation:isolate}
.placement--xigua .placement-status{position:absolute;z-index:2;inset:0 0 auto;padding:12px 24px 10px;background:transparent;color:#fff;font-size:11px;font-weight:600;text-shadow:0 1px 3px #0005}
.xigua-status-icons{display:flex;align-items:center;gap:3px}
.xigua-status-icons svg{width:13px;height:13px}
.xigua-status-icons i{position:relative;width:17px;height:8px;margin-left:2px;border:1px solid #ddd;border-radius:1px;background:linear-gradient(to right,#fff 85%,transparent 85%)}
.xigua-status-icons i:after{position:absolute;content:'';right:-3px;top:2px;width:2px;height:3px;background:#ddd}
.xigua-header{position:absolute;z-index:2;inset:37px 10px auto;pointer-events:none;color:#d2d2d2;text-shadow:0 1px 3px #0003}
.xigua-search{display:flex;align-items:center;height:26px;padding:0 7px;background:#0002}
.xigua-search svg{width:15px;height:15px;color:#d6d6d6}
.xigua-channels{display:flex;align-items:center;justify-content:flex-end;gap:14px;margin-top:9px;font-size:11px;line-height:23px;white-space:nowrap}
.xigua-channels b{font-size:16px;color:#fff}
.xigua-channels svg{width:13px;height:13px;flex-shrink:0}
.placement--xigua .placement-ad{position:absolute;inset:0 0 44px;margin:0;padding:0;background:#000}
.placement--xigua .placement-media{position:absolute;inset:0;border-radius:0;background:#000}
.placement--xigua .placement-media :is(video,img){position:absolute;inset:0;width:100%;height:100%;max-height:none;aspect-ratio:auto;object-fit:contain}
.placement--xigua .placement-media--portrait video{object-fit:cover}
.xigua-play{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);display:grid;place-items:center;width:62px;height:62px;padding:0;border:0;background:transparent;color:#fff;filter:drop-shadow(0 1px 3px #0003);cursor:pointer}
.xigua-play svg{width:52px;height:52px}
.xigua-play:focus-visible,.xigua-controls button:focus-visible,.xigua-controls input:focus-visible{outline:2px solid #a5a0ff;outline-offset:2px}
.xigua-social{position:absolute;right:12px;top:49%;bottom:44px;display:flex;flex-direction:column;justify-content:space-between;align-items:center;color:#fff;pointer-events:none;filter:drop-shadow(0 1px 2px #0003)}
.xigua-social svg{width:23px;height:23px}
.xigua-social svg:last-child{width:21px;height:16px}
.xigua-caption{position:absolute;left:0;right:0;bottom:0;padding:20px 44px 13px 10px;background:linear-gradient(transparent,#0006);color:#fff;text-shadow:0 1px 3px #0004;pointer-events:none}
.xigua-account{display:flex;align-items:center;gap:8px;margin-bottom:6px;min-width:0}
.xigua-account img,.xigua-avatar{width:24px;height:24px;flex-shrink:0;border:1px solid #fff;border-radius:50%;object-fit:cover}
.xigua-avatar{display:grid;place-items:center;background:#6658ff;color:#fff;font-size:11px}
.xigua-account b{overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:12px;font-weight:500}
.xigua-caption p{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;margin:0;font-size:11px;line-height:17px;white-space:pre-line}
.xigua-bottom{position:absolute;inset:auto 0 0;height:44px;display:flex;align-items:center;justify-content:space-around;background:#050505;color:#8a8a8a}
.xigua-bottom span{display:flex;flex-direction:column;align-items:center;gap:3px;font-size:8px;line-height:10px}
.xigua-bottom svg{width:18px;height:18px}
.xigua-bottom .is-active{color:#fff}
.placement--xigua .placement-gallery{bottom:82px;z-index:1;gap:10px}
.placement--xigua audio{display:none}
.placement--xigua .placement-empty{position:absolute;inset:25% 0 auto;margin:0;padding:30px 16px;color:#d5d7e5}
.placement--xigua .placement-error{position:absolute;z-index:3;inset:108px 10px auto;margin:0;padding:8px;border-radius:4px;background:#fff3f3}
.xigua-controls{margin-top:10px;color:#59657d}
.xigua-controls>div{display:flex;align-items:center;gap:7px}
.xigua-controls button{display:grid;place-items:center;width:22px;height:22px;padding:0;border:0;background:transparent;color:inherit;cursor:pointer}
.xigua-controls svg{width:16px;height:16px}
.xigua-controls span{flex:1;font-size:11px}
.xigua-controls input{display:block;width:100%;height:12px;margin:3px 0 0;padding:0;accent-color:#575cf6;cursor:pointer;border:0;box-shadow:none;background:transparent}
</style>
