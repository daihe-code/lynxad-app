export interface VideoMetadata { width: number; height: number; duration?: number }
interface VideoSource { width?: number | null; height?: number | null; durationSec?: number | null }
const positive = (value?: number | null): value is number => typeof value === 'number' && Number.isFinite(value) && value > 0;
export function videoMetadataValues(source: VideoSource, observed?: VideoMetadata) {
  return {width:positive(observed?.width)?observed.width:source.width,
    height:positive(observed?.height)?observed.height:source.height,
    duration:positive(observed?.duration)?observed.duration:source.durationSec};
}
export function hasVideoMetadata(source: VideoSource, observed?: VideoMetadata) {
  const value=videoMetadataValues(source,observed);
  return positive(value.width) && positive(value.height) && positive(value.duration);
}
export function videoMetadataLabel(source: VideoSource, observed?: VideoMetadata, unavailable=false) {
  const {width,height,duration}=videoMetadataValues(source,observed);
  const missing=unavailable?'暂不可用':'读取中';
  const orientation=positive(width)&&positive(height)?height>width?'竖屏':width>height?'横屏':'方屏':`画面方向${missing}`;
  const time=positive(duration)?`${Math.round(duration*10)/10}s`:`时长${missing}`;
  return `${time} · ${orientation}`;
}

/** Read only video headers for legacy records without metadata; never start playback. */
export function readVideoMetadata(url: string, signal: AbortSignal): Promise<VideoMetadata | undefined> {
  if (signal.aborted) return Promise.resolve(undefined);
  return new Promise(resolve=>{
    const video=document.createElement('video');video.preload='metadata';video.muted=true;
    let finished=false;
    const finish=(value?:VideoMetadata)=>{
      if(finished)return;finished=true;clearTimeout(timer);signal.removeEventListener('abort',abort);
      video.onloadedmetadata=null;video.onerror=null;video.removeAttribute('src');video.load();resolve(value);
    };
    const abort=()=>finish();
    const timer=setTimeout(abort,10000);
    signal.addEventListener('abort',abort,{once:true});
    video.onerror=abort;
    video.onloadedmetadata=()=>{
      const value={width:video.videoWidth,height:video.videoHeight,duration:video.duration};
      finish(hasVideoMetadata({},value)?value:undefined);
    };
    video.src=url;video.load();
  });
}
