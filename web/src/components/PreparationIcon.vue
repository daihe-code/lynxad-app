<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <template v-if="name === 'bulb'"><path d="M8 15c-1.3-1.1-2-2.5-2-4a6 6 0 0 1 12 0c0 1.5-.7 2.9-2 4l-1 2H9l-1-2Z"/><path d="M9 20h6M11 23h2M12 1v1M3 5l1 1M20 6l1-1M1 12h2M21 12h2M10 12l2 2 2-2M12 14v3"/></template>
    <template v-else-if="name === 'combination'"><path d="M10 10H5a3 3 0 0 1 0-6h5v6ZM14 10V5a3 3 0 0 1 6 0v5h-6ZM14 14h5a3 3 0 0 1 0 6h-5v-6ZM10 14v5a3 3 0 0 1-6 0v-5h6Z" fill="currentColor" stroke="none"/></template>
    <template v-else-if="name === 'grid'"><rect x="3" y="3" width="6" height="6" rx="2"/><rect x="15" y="3" width="6" height="6" rx="2"/><rect x="3" y="15" width="6" height="6" rx="2"/><rect x="15" y="15" width="6" height="6" rx="2"/></template>
    <template v-else-if="name === 'image'"><rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8" cy="8" r="1.5"/><path d="m4 18 5-5 4 3 4-6 4 6"/></template>
    <template v-else-if="name === 'settings'"><path d="m10 3-.6 2.4-2.1 1.2L5 6l-2 3 1.8 1.7v2.6L3 15l2 3 2.3-.6 2.1 1.2L10 21h4l.6-2.4 2.1-1.2 2.3.6 2-3-1.8-1.7v-2.6L21 9l-2-3-2.3.6-2.1-1.2L14 3h-4Z"/><circle cx="12" cy="12" r="3"/></template>
    <template v-else-if="name === 'document'"><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9l-6-6ZM14 3v6h6M8 13h8M8 17h6"/></template>
    <template v-else-if="name === 'chart'"><rect x="3" y="12" width="4" height="9" rx="1" fill="currentColor" stroke="none"/><rect x="10" y="3" width="4" height="18" rx="1" fill="currentColor" stroke="none"/><rect x="17" y="8" width="4" height="13" rx="1" fill="currentColor" stroke="none"/></template>
    <template v-else-if="name === 'play'"><path d="m8 4 12 8-12 8V4Z"/></template>
    <template v-else-if="name === 'refresh'"><path d="M20 7v5h-5M4 17v-5h5M5.2 7a8 8 0 0 1 13.5-2L20 7M4 17l1.3 2A8 8 0 0 0 19 17"/></template>
    <template v-else-if="name === 'external'"><path d="M14 3h7v7M21 3l-11 11M11 4H5a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h13a2 2 0 0 0 2-2v-6"/></template>
    <template v-else-if="name === 'info'"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/></template>
    <path v-else-if="name === 'chevron'" d="m6 9 6 6 6-6"/>
    <path v-else-if="name === 'back'" d="m14 6-6 6 6 6M8 12h13"/>
  </svg>
</template>
<script setup lang="ts">
defineProps<{name:'bulb'|'combination'|'grid'|'image'|'settings'|'document'|'chart'|'play'|'refresh'|'external'|'info'|'chevron'|'back'}>();
</script>
