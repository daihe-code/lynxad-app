<template>
  <section v-if="notice" class="plan-review" :class="{'plan-review--delayed':notice.delayed || notice.unavailable}" aria-label="平台审核进度" aria-live="polite">
    <div class="plan-review__heading"><strong>{{ notice.title }}</strong><button type="button" :disabled="busy || notice.cooldown > 0" @click="refresh">{{ busy ? '查询中…' : notice.cooldown > 0 ? `${notice.cooldown} 秒后可刷新` : '刷新审核结果' }}</button></div>
    <p>{{ notice.message }}</p>
    <p>{{ notice.waiting }}。等待时间从系统首次记录本轮审核起计算。</p>
    <p>最近查询：{{ time(plan.review?.lastCheckedAt) }}<template v-if="plan.review?.nextCheckAt"> · 下次后台查询预计 {{ time(plan.review.nextCheckAt) }}</template></p>
    <p v-if="!plan.review?.pendingSince">历史草稿创建时间不计入本轮审核等待时间。</p>
    <p v-if="notice.unavailable">查询失败或未返回计划时，不会将计划改为审核拒绝。</p>
    <p v-if="feedback" role="status">{{ feedback }}</p>
  </section>
</template>
<script setup lang="ts">
import { computed,ref,onMounted,onUnmounted,watch } from 'vue';
import { getPlan,refreshPlanAudit,type PlanVo } from '@/features/delivery/deliveryApi';
import { reviewNotice } from '@/features/delivery/planReview';
const props=defineProps<{plan:Pick<PlanVo,'planId'|'status'|'qcPlanId'|'review'>;autoRead?:boolean}>();
const emit=defineEmits<{updated:[plan:PlanVo]}>();
const busy=ref(false),feedback=ref(''),now=ref(Date.now());
const notice=computed(()=>reviewNotice(props.plan,now.value));
let context=0,operation=0,nextRead=Date.now()+60000,timer:ReturnType<typeof setInterval>|undefined;
watch(()=>props.plan.planId,()=>{context++;operation++;nextRead=Date.now()+60000;busy.value=false;feedback.value='';});
function time(value?:string|null){if(!value)return '尚未查询';const date=new Date(value);return Number.isNaN(date.getTime())?'尚未记录':date.toLocaleString('zh-CN',{hour12:false});}
async function refresh(){
  if(busy.value || !notice.value || notice.value.cooldown>0)return;
  const id=props.plan.planId,seq=context,read=++operation;busy.value=true;feedback.value='';
  try {
    const result=await refreshPlanAudit(id);
    if(context!==seq || operation!==read || props.plan.planId!==id)return;
    emit('updated',result);now.value=Date.now();
    feedback.value=result.review?.state==='CHECK_UNAVAILABLE'?'本次未取得最终审核结果，系统会继续查询。':result.status==='PENDING_AUDIT'?'已读取当前审核进度，最终结果仍待千川确认。':'平台状态已更新。';
  } catch {if(context===seq)feedback.value='本次查询未完成，已保留当前状态，请稍后刷新；无需重复发布。';}
  finally{if(context===seq)busy.value=false;}
}
async function readCached(){
  const id=props.plan.planId,seq=context,read=++operation;
  try{const result=await getPlan(id);if(context===seq && read===operation && props.plan.planId===id)emit('updated',result);}
  catch{/* Preserve the last known state; the explicit refresh remains available. */}
}
onMounted(()=>{timer=setInterval(()=>{now.value=Date.now();if(props.autoRead && notice.value && !busy.value && now.value>=nextRead && document.visibilityState==='visible'){nextRead=now.value+60000;void readCached();}},1000);});
onUnmounted(()=>{context++;clearInterval(timer);});
</script>
<style scoped>
.plan-review{margin:16px 0;padding:14px 16px;border:1px solid #dedff7;border-radius:12px;background:#f5f5ff;color:#535a78;font-size:13px;line-height:1.7;overflow-wrap:anywhere;}
.plan-review--delayed{background:#fff8ed;border-color:#f5d9ac;}
.plan-review__heading{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;}
.plan-review__heading strong{font-size:14px;}
.plan-review p{margin:5px 0 0;font-size:13px;line-height:1.7;}
.plan-review button{border:1px solid #d4d4ef;border-radius:8px;padding:5px 10px;background:#fff;color:#5750d3;font-size:12px;cursor:pointer;}
.plan-review button:disabled{cursor:default;opacity:.55;}
</style>
