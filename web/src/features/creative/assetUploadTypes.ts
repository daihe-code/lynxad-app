import fileRules from '../../../../lynx-ad-app/src/main/resources/asset-file-policy.json' with { type: 'json' };
import type { LibraryItemVo } from './creativeApi';

export type UploadLibrary = LibraryItemVo['type'];
const pixels = `${(fileRules.maxPixels / 10000).toLocaleString()} 万像素`;
const videoRequirements = [
  '横版视频：宽高比16:9，视频码率≥516kbps，大小≤1000M，1280*720≤分辨率≤2560*1440，4s≤时长≤300s',
  '竖版视频：宽高比9:16，视频码率≥516kbps，大小≤1000M，720*1280≤分辨率≤1440*2560，4s≤时长≤300s',
];
const policies = {
  VIDEO: { label: '视频库', note: `视频需 ${fileRules.videoCodec.toUpperCase()} 编码，不超过 ${pixels}，时长须大于 0。推荐竖版 9:16；实际规格以所选广告场景为准。` },
  IMAGE: { label: '图片库', note: `图片不超过 ${pixels}；商品卡须为 1:1 方图，其他广告场景规格分别校验。` },
  AUDIO: { label: '音频库', note: `M4A 需 ${fileRules.m4aCodec.toUpperCase()} 编码，WAV 需 PCM 编码，音频时长须大于 0。WAV 仅保存到平台；同步千川请选择 MP3 或 M4A。` },
  TEXT: { label: '文本库', note: `文本最多 ${fileRules.maxTextChars.toLocaleString()} 字符，作为文案入库，审核通过后可使用，不作为独立千川文件上传。` },
  CAROUSEL: { label: '图文库', note: '图文的图片、文案与音频分别预检；此入口上传独立文件，不会自动组装图文。WAV 仅保存到平台。' },
};

export const assetFileRules = fileRules;
export function libraryUploadPolicy(library: UploadLibrary) {
  const files=fileRules.files.filter(r => library === 'CAROUSEL' ? r.kind !== 'VIDEO' : r.kind === library);
  const extensions=files.map(r=>r.extension);
  const limits=[...new Set(files.map(r=>`${r.kind === 'IMAGE' ? '图片' : r.kind === 'VIDEO' ? '视频' : r.kind === 'AUDIO' ? '音频' : '文本'} ≤ ${r.maxBytes / 1024 ** 2}MB`))].join(' · ');
  return { ...policies[library], extensions, hint: `${extensions.map(e=>e.toUpperCase()).join(' / ')} · ${limits}`, requirements: library === 'VIDEO' ? videoRequirements : [], note: policies[library].note };
}
export function libraryFileError(filename: string, library?: UploadLibrary) {
  if (!library) return '';
  const policy = libraryUploadPolicy(library);
  const extension = filename.slice(filename.lastIndexOf('.') + 1).toLowerCase();
  return policy.extensions.includes(extension) ? '' : `${policy.label}不支持“${filename}”，请选择 ${policy.extensions.map(e => e.toUpperCase()).join(' / ')} 文件`;
}
