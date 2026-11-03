import { watch, type Ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { CreationState } from './creationApi';
import { createCreationActionNotices } from './creationActionNotices';

// Shared across a published plan and its embedded editing draft, so handoff cannot notify twice.
const notices=createCreationActionNotices(options=>ElMessage.success(options));
let session:string|null=localStorage.getItem('lynx.token');
export function useCreationActionNotices(state:Ref<CreationState|undefined>) {
  watch(state,value=>{
    const current=localStorage.getItem('lynx.token');
    if(current!==session){notices.reset();session=current;}
    if(current)notices.observe(value);
  },{immediate:true,flush:'sync'});
  return notices;
}
