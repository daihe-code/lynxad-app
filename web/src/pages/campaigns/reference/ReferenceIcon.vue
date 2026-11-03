<template>
  <svg v-bind="icon.attrs" aria-hidden="true" v-html="icon.content" />
</template>
<script setup lang="ts">
import { computed } from "vue";
const props = defineProps<{ name: string }>();
// Only bundled static SVG paths are rendered, never API HTML.
const icons: Record<
  string,
  { attrs: Record<string, string>; content: string }
> = {
  user: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "currentColor",
    },
    content:
      '<circle cx="12" cy="8.4" r="4.1"/>\n    <path d="M4.4 20.4c0-3.6 3.4-6.2 7.6-6.2s7.6 2.6 7.6 6.2v.4H4.4v-.4Z"/>',
  },
  bell: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M18.2 8.8a6.2 6.2 0 1 0-12.4 0c0 5.3-2.1 6.9-2.1 6.9h16.6s-2.1-1.6-2.1-6.9Z"/>\n    <path d="M13.7 19.2a1.9 1.9 0 0 1-3.4 0"/>',
  },
  caret: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "2",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content: '<path d="m6.5 9.4 5.5 5.4 5.5-5.4"/>',
  },
  menu: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
    },
    content: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  },
  mountain: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "currentColor",
    },
    content: '<path d="M2.6 19.4 9.4 7.6l4 6.5 2.1-3.2 5.9 8.5H2.6Z"/>',
  },
  up: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "2.2",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content: '<path d="M12 19V5.6M6.6 11 12 5.6 17.4 11"/>',
  },
  "arrow-left": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.9",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content: '<path d="M19.6 12H4.4M10.6 5.8 4.4 12l6.2 6.2"/>',
  },
  copy: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<rect x="8.6" y="8.6" width="11.6" height="11.6" rx="2.4"/>\n    <path d="M15.4 5.4a2.4 2.4 0 0 0-2.4-2.4H6.2a2.4 2.4 0 0 0-2.4 2.4v6.8a2.4 2.4 0 0 0 2.4 2.4"/>',
  },
  edit: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.8",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M15.6 4.2 19.8 8.4 9 19.2H4.8v-4.2z"/><path d="m13.2 6.6 4.2 4.2"/>',
  },
  pause: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<circle cx="12" cy="12" r="8.6"/><path d="M10 9.2v5.6M14 9.2v5.6" stroke-width="2.1"/>',
  },
  play: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linejoin": "round",
    },
    content:
      '<circle cx="12" cy="12" r="8.6"/><path d="M10.3 8.4 15.6 12l-5.3 3.6z" fill="currentColor"/>',
  },
  down: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "2.2",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content: '<path d="M12 5v13.4M6.6 13 12 18.4 17.4 13"/>',
  },
  check: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "2.1",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content: '<path d="m5 12.6 4.6 4.4L19 7.4"/>',
  },
  forward: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.8",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content: '<path d="M14.4 5.6 20 12l-5.6 6.4"/><path d="M20 12H4.6"/>',
  },
  file: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M6.2 2.8h7.4L19 8.2v13H6.2z"/><path d="M13.4 3v5.4h5.4"/><path d="M9.2 13.4h6M9.2 17h3.9"/>',
  },
  sliders: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M4 7.6h9M4 16.4h5M16 16.4h4M13 7.6h7"/>\n    <circle cx="14.4" cy="16.4" r="1.9"/><circle cx="11.4" cy="7.6" r="1.9"/>',
  },
  cart: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M3.2 4.4h2.4l2.2 10.4h9.7l2.3-7.6H6"/>\n    <circle cx="9.6" cy="18.6" r="1.5"/><circle cx="16.6" cy="18.6" r="1.5"/>',
  },
  video: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<rect x="3.2" y="5.4" width="17.6" height="13.2" rx="3"/>\n    <path d="M10.4 9.4 15 12l-4.6 2.6z" fill="currentColor"/>',
  },
  flow: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<circle cx="6.4" cy="6.4" r="2.6"/><circle cx="17.6" cy="17.6" r="2.6"/>\n    <path d="M6.4 9v4.4a4.2 4.2 0 0 0 4.2 4.2H15"/>',
  },
  coins: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<ellipse cx="12" cy="6.6" rx="7" ry="2.8"/>\n    <path d="M5 6.8v4.2c0 1.55 3.13 2.8 7 2.8s7-1.25 7-2.8V6.8"/>\n    <path d="M5 11.6v4.2c0 1.55 3.13 2.8 7 2.8s7-1.25 7-2.8v-4.2"/>',
  },
  loading: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.9",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M20 11.4A8 8 0 0 0 6.3 6.1L3.8 8.4"/><path d="M4 12.6a8 8 0 0 0 13.7 5.3l2.5-2.3"/>\n    <path d="M3.4 4.4v4.2h4.2M20.6 19.6v-4.2h-4.2"/>',
  },
  "alert-fill": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "currentColor",
    },
    content:
      '<circle cx="12" cy="12" r="10"/>\n    <path d="M12 6.4v7.2M12 17.1h.01" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round"/>',
  },
  "help-fill": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "currentColor",
    },
    content:
      '<circle cx="12" cy="12" r="10"/>\n    <path d="M9.5 9.4a2.6 2.6 0 0 1 5 .9c0 1.7-2.4 2.1-2.4 3.6" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"/>\n    <path d="M12.1 17.2h.01" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round"/>',
  },
  bulb: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.6",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M12 3.6a5.6 5.6 0 0 0-3.3 10.1c.5.4.9 1 .9 1.6v1.1h4.8v-1.1c0-.6.4-1.2.9-1.6A5.6 5.6 0 0 0 12 3.6Z"/>\n    <path d="M10.2 19.4h3.6"/>\n    <path d="M13.2 7.4 10.5 11.3h2.9l-2.6 3.9"/>',
  },
  "shield-check": {
    attrs: {
      viewBox: "0 0 24 24",
    },
    content:
      '<path fill="currentColor" d="M12 2.3c1.9 1.2 4.1 1.9 6.3 1.9.4 0 .8.3.8.8v6.3c0 4.6-3 8.8-7.1 10.4C7.9 20.1 4.9 15.9 4.9 11.3V5c0-.5.3-.8.8-.8 2.2 0 4.4-.7 6.3-1.9Z"/>\n    <path fill="#fff" d="m8.1 11.8 1.5-1.5 2.1 2.1 4.1-4.2 1.5 1.5-5.6 5.7-3.6-3.6Z"/>',
  },
  "arrow-right": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.9",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content: '<path d="M4.4 12h15.2M13.4 5.8 19.6 12l-6.2 6.2"/>',
  },
  tipmark: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "currentColor",
    },
    content:
      '<path d="M6.2 3.4h11.6c.9 0 1.6.7 1.6 1.6v14.6c0 .9-1 1.4-1.7.9L12 16.4l-5.7 4.1c-.7.5-1.7 0-1.7-.9V5c0-.9.7-1.6 1.6-1.6Z"/>\n    <path fill="#fff" d="m12 7.1.95 1.95 2.15.3-1.55 1.5.37 2.13L12 11.98l-1.92 1 .37-2.13-1.55-1.5 2.15-.3L12 7.1Z"/>',
  },
  image: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.8",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<rect x="3" y="4.6" width="18" height="14.8" rx="3"/>\n    <path d="m5.6 16.4 4-4.3 3.1 3.3 2.2-2.3 3.5 3.6"/>\n    <circle cx="8.6" cy="9.2" r="1.4" fill="currentColor" stroke="none"/>',
  },
  article: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.8",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M5 3.6h9.4L19 8.2v12.2H5z"/>\n    <path d="M14 3.8V8.6h4.8M8.2 12.4h7.6M8.2 16.2h7.6"/>',
  },
  sparkle: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "currentColor",
    },
    content:
      '<path d="M12 3.2l1.7 4.6 4.6 1.7-4.6 1.7L12 15.8l-1.7-4.6-4.6-1.7 4.6-1.7z"/>\n    <path d="M18.4 14.6l.8 2.1 2.1.8-2.1.8-.8 2.1-.8-2.1-2.1-.8 2.1-.8z"/>',
  },
  replace: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.9",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M20 11.4A8 8 0 0 0 6.3 6.1L3.8 8.4"/>\n    <path d="M4 12.6a8 8 0 0 0 13.7 5.3l2.5-2.3"/>\n    <path d="M3.4 4.4v4.2h4.2M20.6 19.6v-4.2h-4.2"/>',
  },
  trash: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.8",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M4.4 6.6h15.2M9.4 6.6V4.4h5.2v2.2"/>\n    <path d="M6.4 6.6 7.2 20h9.6l.8-13.4"/>\n    <path d="M10.4 10.2v6M13.6 10.2v6"/>',
  },
  plus: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "2",
      "stroke-linecap": "round",
    },
    content: '<path d="M12 5.2v13.6M5.2 12h13.6"/>',
  },
  "plus-box": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.8",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M20.4 13.6V19a1.6 1.6 0 0 1-1.6 1.6H5.2A1.6 1.6 0 0 1 3.6 19V5.2A1.6 1.6 0 0 1 5.2 3.6h5.4"/>\n    <path d="M17.4 3.4v6M14.4 6.4h6"/>',
  },
  "cloud-upload": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "currentColor",
    },
    content:
      '<path d="M6.3 20.5a5.8 5.8 0 0 1-1.2-11.4A7.1 7.1 0 0 1 18.7 8a6.3 6.3 0 0 1-.8 12.5H6.3Z"/><path d="M12 20V10m-4 4 4-4 4 4" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>',
  },
  upload: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.8",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M12 15.6V4.4M8 8.2 12 4.2l4 4"/>\n    <path d="M4.6 15v3.6a1.8 1.8 0 0 0 1.8 1.8h11.2a1.8 1.8 0 0 0 1.8-1.8V15"/>',
  },
  rocket: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "currentColor",
    },
    content:
      '<path d="M13.6 2.6c3.3 0 6 1.6 7.2 4.2 1.1 2.5.5 5.5-1.7 8.3-1.3 1.7-3.1 3.1-5.2 4.2l-2.3-2.3-2.6-2.6C9.9 12 11.9 9 13.6 2.6Z" opacity=".92"/>\n    <path d="M8.4 15.6c-1 1-1.4 2.6-1.2 4.6 2 .2 3.6-.2 4.6-1.2z" opacity=".7"/>\n    <circle cx="14.8" cy="9.2" r="1.9" fill="#fff"/>',
  },
  close: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "2",
      "stroke-linecap": "round",
    },
    content: '<path d="M6.4 6.4l11.2 11.2M17.6 6.4 6.4 17.6"/>',
  },
  "check-circle": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<circle cx="12" cy="12" r="8.9"/>\n    <path d="m8.2 12.3 2.5 2.5 5.1-5.3"/>',
  },
  "plat-douyin": {
    attrs: {
      viewBox: "0 0 24 24",
    },
    content:
      '<path fill="#25F4EE" d="M12.6 3.4h2.6c.3 2 1.4 3.2 3.3 3.5v2.6c-1.2 0-2.3-.4-3.3-.9v6a5.2 5.2 0 1 1-4.4-5.15v2.7a2.5 2.5 0 1 0 1.7 2.4V3.4Z"/>\n    <path fill="#FE2C55" d="M14.6 5.2h2.6c.3 2 1.4 3.2 3.3 3.5v2.6c-1.2 0-2.3-.4-3.3-.9v6a5.2 5.2 0 1 1-4.4-5.15v2.7a2.5 2.5 0 1 0 1.7 2.4V5.2Z"/>\n    <path fill="#fff" d="M13.6 4.3h2.6c.3 2 1.4 3.2 3.3 3.5v2.6c-1.2 0-2.3-.4-3.3-.9v6a5.2 5.2 0 1 1-4.4-5.15v2.7a2.5 2.5 0 1 0 1.7 2.4V4.3Z"/>',
  },
  "plat-xigua": {
    attrs: {
      viewBox: "0 0 24 24",
    },
    content:
      '<circle cx="12" cy="12" r="9.6" fill="#FF0046"/>\n    <path fill="#fff" d="M9.6 7.6 16.6 12l-7 4.4z"/>',
  },
  "plat-huoshan": {
    attrs: {
      viewBox: "0 0 24 24",
    },
    content:
      '<path fill="#F0A98A" d="M0 16.6c3.1 0 5.6-1.7 8.3-1.7 2.7 0 4.5 1.7 7.2 1.7 2.9 0 5.4-1.6 8.5-1.6V24H0Z"/>\n    <path fill="#fff" d="M12.7 3.2h2.3c.3 1.8 1.2 2.9 3 3.1v2.3c-1.1 0-2.1-.3-3-.8v5.5a4.7 4.7 0 1 1-4-4.65v2.4a2.2 2.2 0 1 0 1.7 2.15V3.2Z"/>',
  },
  "plat-pangolin": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "currentColor",
    },
    content:
      '<path d="m3 14.8 5.6-4.9v4.9H3Zm6.4-5.6 2.2-1.9v8.2H9.4V9.2Zm3.1-1.9 2.2 1.9v6.3h-2.2V7.3Zm3.1 2.6 5.4 4.9h-5.4V9.9Z"/>',
  },
  "plat-jisu": {
    attrs: {
      viewBox: "0 0 24 24",
    },
    content:
      '<circle cx="12" cy="12" r="11" fill="#F2A0A4"/>\n    <path fill="#fff" d="M6.9 7.6 12 12l-5.1 4.4z"/>\n    <path fill="#fff" d="M13.4 7.6 18.5 12l-5.1 4.4z"/>',
  },
  info: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<circle cx="12" cy="12" r="9"/>\n    <path d="M12 11.2v5.4M12 7.9h.01"/>',
  },
  "warn-fill": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "currentColor",
    },
    content:
      '<path d="M12 2.6a9.4 9.4 0 1 0 0 18.8 9.4 9.4 0 0 0 0-18.8Zm0 4.2c.6 0 1.05.5 1 1.1l-.35 5.3a.65.65 0 0 1-1.3 0L11 7.9c-.05-.6.4-1.1 1-1.1Zm0 10.9a1.35 1.35 0 1 1 0-2.7 1.35 1.35 0 0 1 0 2.7Z"/>',
  },
  doc: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M6.2 2.8h7.4L19 8.2v13H6.2z"/>\n    <path d="M13.4 3v5.4h5.4"/>\n    <path d="M9.2 13.4h6M9.2 17h3.9"/>',
  },
  hourglass: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M7 3.6h10M7 20.4h10"/>\n    <path d="M8.2 3.8v2.9c0 2.2 3.8 3.9 3.8 5.3s-3.8 3.1-3.8 5.3v3.1"/>\n    <path d="M15.8 3.8v2.9c0 2.2-3.8 3.9-3.8 5.3s3.8 3.1 3.8 5.3v3.1"/>',
  },
  "warn-tri": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "currentColor",
    },
    content:
      '<path d="M12 3.4c.5 0 .95.26 1.2.7l8 13.9c.52.9-.13 2.02-1.17 2.02H3.97c-1.04 0-1.69-1.12-1.17-2.02l8-13.9c.25-.44.7-.7 1.2-.7Z"/>\n    <path d="M12 8.4v5.2" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"/>\n    <circle cx="12" cy="16.9" r="1.15" fill="#fff"/>',
  },
  "check-clock": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<circle cx="10.7" cy="10.7" r="7.3"/>\n    <path d="M10.7 6.5v4.5l3 1.9"/>\n    <path d="m15.9 18.4 1.8 1.8 3.2-3.5"/>',
  },
  stop: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linejoin": "round",
    },
    content:
      '<circle cx="12" cy="12" r="8.6"/>\n    <rect x="9.2" y="9.2" width="5.6" height="5.6" rx="1.1" fill="currentColor"/>',
  },
  "check-done": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.9",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<circle cx="12" cy="12" r="8.6"/>\n    <path d="m8.1 12.3 2.8 2.8 5.2-5.8"/>',
  },
  refresh: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.9",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M20 11.4A8 8 0 0 0 6.3 6.1L3.8 8.4"/>\n    <path d="M4 12.6a8 8 0 0 0 13.7 5.3l2.5-2.3"/>\n    <path d="M3.4 4.4v4.2h4.2M20.6 19.6v-4.2h-4.2"/>',
  },
  eye: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M2.4 12S5.9 5.6 12 5.6 21.6 12 21.6 12 18.1 18.4 12 18.4 2.4 12 2.4 12Z"/>\n    <circle cx="12" cy="12" r="3.1"/>',
  },
  target: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<circle cx="12" cy="12" r="8.4"/>\n    <circle cx="12" cy="12" r="4.4"/>\n    <circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none"/>',
  },
  clock: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<circle cx="12" cy="12" r="8.6"/>\n    <path d="M12 6.6V12l3.4 2.1"/>',
  },
  "chart-up": {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M3.6 19.6h16.8"/>\n    <path d="M6.4 15.8 10 11.2l3.2 2.8 4.4-5.6"/>\n    <path d="M14.4 8.4h3.2v3.2"/>',
  },
  lightning: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content: '<path d="M13.4 2.6 5.6 13.4h5.2l-.8 8 7.8-10.8h-5.2z"/>',
  },
  list: {
    attrs: {
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: "currentColor",
      "stroke-width": "1.7",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
    },
    content:
      '<path d="M8.6 6.4h11M8.6 12h11M8.6 17.6h11"/>\n    <circle cx="4.7" cy="6.4" r="1.15" fill="currentColor" stroke="none"/>\n    <circle cx="4.7" cy="12" r="1.15" fill="currentColor" stroke="none"/>\n    <circle cx="4.7" cy="17.6" r="1.15" fill="currentColor" stroke="none"/>',
  },
};
const icon = computed(() => icons[props.name] || icons.file!);
</script>
