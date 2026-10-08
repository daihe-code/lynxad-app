import type { LibraryItemVo } from './creativeApi';

export type UploadLibrary = LibraryItemVo['type'];
const policies = {
  VIDEO: { label: '视频库', extensions: ['mp4'], hint: '仅支持 H.264 MP4 视频，≤ 500MB', note: '视频不超过 4,000 万像素，时长须大于 0。推荐竖版 9:16；实际规格以所选广告场景为准。' },
  IMAGE: { label: '图片库', extensions: ['jpg', 'jpeg', 'png'], hint: '仅支持 JPG/JPEG/PNG 图片，≤ 1.5MB', note: '图片不超过 4,000 万像素；实际规格以所选广告场景为准。' },
  AUDIO: { label: '音频库', extensions: ['mp3', 'm4a', 'wav'], hint: '仅支持 MP3 / AAC M4A / PCM WAV 音频，≤ 10MB', note: '音频时长须大于 0。WAV 仅保存到平台；同步千川请选择 MP3 或 M4A。' },
  TEXT: { label: '文本库', extensions: ['txt'], hint: '仅支持 TXT 文本，≤ 1MB、50,000 字符', note: '文本作为文案入库，审核通过后可使用，不作为独立千川文件上传。' },
  CAROUSEL: { label: '图文库', extensions: ['jpg', 'jpeg', 'png', 'mp3', 'm4a', 'wav', 'txt'], hint: '图文组件：图片 ≤ 1.5MB · 音频 ≤ 10MB · TXT ≤ 1MB、50,000 字符', note: '图文的图片、文案与音频分别预检；此入口上传独立文件，不会自动组装图文。WAV 仅保存到平台。' },
};

export function libraryUploadPolicy(library: UploadLibrary) { return policies[library]; }
export function libraryFileError(filename: string, library?: UploadLibrary) {
  if (!library) return '';
  const policy = policies[library];
  const extension = filename.slice(filename.lastIndexOf('.') + 1).toLowerCase();
  return policy.extensions.includes(extension) ? '' : `${policy.label}不支持“${filename}”，请选择 ${policy.extensions.map(e => e.toUpperCase()).join(' / ')} 文件`;
}
