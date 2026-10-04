import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { fetchContactInfo, submitContact, type ContactInfo } from "@/features/notify/notifyApi";
import { isCnMobile, PHONE_ERROR } from "@/utils/phone";

const visible = ref(false);
const submitting = ref(false);
const phoneError = ref("");
const info = reactive<ContactInfo>({ phone: "", email: "" });
const form = reactive({ phone: "" });

export function useContactDialog() {
  async function open() {
    visible.value = true;
    phoneError.value = "";
    form.phone = "";
    try {
      const data = await fetchContactInfo();
      info.phone = data.phone || "";
      info.email = data.email || "";
    } catch {
      info.phone = "";
      info.email = "";
    }
  }

  function close() {
    visible.value = false;
  }

  async function submit() {
    if (!isCnMobile(form.phone)) {
      phoneError.value = PHONE_ERROR;
      return;
    }
    phoneError.value = "";
    submitting.value = true;
    try {
      await submitContact(form.phone.trim());
      ElMessage.success("已收到，客服将尽快联系您");
      visible.value = false;
    } catch {
      /* interceptor already toasted */
    } finally {
      submitting.value = false;
    }
  }

  return { visible, submitting, phoneError, info, form, open, close, submit };
}
