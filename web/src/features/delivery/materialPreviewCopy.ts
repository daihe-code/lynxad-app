import type { CreationMaterial, CreationProduct } from './creationApi';

/** Carousel descriptions are separate from text embedded in each image. */
export function materialPreviewCopy(material?: CreationMaterial, product?: CreationProduct, selectedTitle?: string) {
  const title = selectedTitle?.trim() || material?.titles.find(value => value.trim())?.trim() || material?.title?.trim() || product?.name || '';
  if (selectedTitle?.trim() || material?.type !== 'CAROUSEL') return title;
  const description = material.text?.trim();
  if (!description) return title;
  return !title || description.startsWith(title) ? description : `${title}\n${description}`;
}
