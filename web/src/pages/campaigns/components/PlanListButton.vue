<template>
  <router-link class="plan-list-button" :class="{ 'plan-list-button--primary': primary }" to="/campaigns">{{ label }}</router-link>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ label?: string; primary?: boolean }>(), { label: '查看所有计划', primary: false });
</script>

<style scoped>
.plan-list-button{box-sizing:border-box;display:inline-flex;align-items:center;justify-content:center;min-width:150px;min-height:48px;padding:12px 22px;border:1px solid #e0e2f1;border-radius:10px;background:#fff;color:#525671;font:inherit;font-size:14px;font-weight:600;line-height:22px;text-decoration:none;white-space:nowrap;cursor:pointer}
.plan-list-button:hover{border-color:#5b55f5;color:#5b55f5}
.plan-list-button--primary{min-width:182px;border-color:#5b55ff;background:#5b55ff;color:#fff;box-shadow:0 4px 12px #5b55ff26}
.plan-list-button--primary:hover{border-color:#4c46ee;background:#4c46ee;color:#fff}
.plan-list-button:focus-visible{outline:2px solid #575cf6;outline-offset:3px}
@media(max-width:767px){.plan-list-button{min-width:0;min-height:44px;padding:10px 12px;font-size:13px}}
</style>
