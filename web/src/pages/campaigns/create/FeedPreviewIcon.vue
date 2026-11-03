<template><svg viewBox="0 0 24 24" aria-hidden="true" :fill="solid ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" v-html="paths[name] || paths.search" /></template>
<script setup lang="ts">
defineProps<{ name: string; solid?: boolean }>();
const paths: Record<string, string> = {
  'thumb-up': '<path d="M8 10 12 3c1-1 3 0 2.7 2l-.8 4H20c1.4 0 2.3 1.1 2 2.5l-1.5 7C20.3 20 19.5 21 18 21H8V10ZM2 10h3v11H2Z"/>',
  'message-square': '<path d="M3 3h18v14H9l-6 4V3Z"/>',
  more: '<g fill="currentColor" stroke="none"><circle cx="4" cy="12" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="20" cy="12" r="1.8"/></g>',
  home: '<path d="m3 10 9-7 9 7v11h-7v-7h-4v7H3V10Z"/>',
  screen: '<rect x="3" y="3" width="18" height="14" rx="1"/><path d="M8 21h8m-4-4v4"/>',
  profile: '<circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>',
  play: '<path d="M6 3.5a1 1 0 0 1 1.5-.9l13 8.5a1 1 0 0 1 0 1.8l-13 8.5a1 1 0 0 1-1.5-.9V3.5Z" stroke="none"/>',
  'plus-circle': '<circle cx="12" cy="12" r="9"/><path d="M8 12h8m-4-4v8"/>',
  signal: '<path d="M3 19v-3m5 3v-6m5 6V9m5 10V5" stroke-width="3"/>',
  wifi: '<path d="M2 8a16 16 0 0 1 20 0M5 12a11 11 0 0 1 14 0m-11 4a6 6 0 0 1 8 0"/><circle cx="12" cy="20" r="1" fill="currentColor"/>',
  'comment-fill': '<path d="M21 11.5a9 9 0 0 1-9 8.5 10 10 0 0 1-3.7-.7L3 21l1.4-4.5A8 8 0 0 1 3 11.5a9 9 0 0 1 18 0Z" fill="currentColor"/><g fill="#929292" stroke="none"><circle cx="7" cy="11.5" r="1.3"/><circle cx="12" cy="11.5" r="1.3"/><circle cx="17" cy="11.5" r="1.3"/></g>',
  heart: '<path d="M20.8 4.9a5.5 5.5 0 0 0-7.8 0L12 6l-1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.3a5.5 5.5 0 0 0 0-7.8Z"/>',
  comment: '<path d="M21 11.5a9 9 0 0 1-9 8.5 10 10 0 0 1-3.7-.7L3 21l1.4-4.5A8 8 0 0 1 3 11.5a9 9 0 0 1 18 0Z"/><path d="M7 11.5h.5m4.3 0h.5m4.2 0h.5"/>',
  save: '<path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9Z"/>',
  share: '<path d="m14 3 8 8-8 7v-5c-5 0-8 2-12 7 1-9 5-12 12-12V3Z"/>',
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
  volume: '<path d="m11 4-5 4H2v8h4l5 4V4Z"/><path d="M15 8a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/>',
  muted: '<path d="m11 4-5 4H2v8h4l5 4V4Z"/><path d="m16 9 6 6m0-6-6 6"/>',
  fullscreen: '<path d="M9 3H3v6m12-6h6v6M3 15v6h6m12-6v6h-6"/>',
  music: '<path d="M10 18V5l10-2v13M10 9l10-2"/><ellipse cx="6.5" cy="18" rx="3.5" ry="3"/><ellipse cx="16.5" cy="16" rx="3.5" ry="3"/>',
  bag: '<path d="M5 7h14l1 14H4L5 7Z"/><path d="M8 8V6a4 4 0 0 1 8 0v2"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
  close: '<path d="m5 5 14 14M19 5 5 19"/>'
};
</script>
