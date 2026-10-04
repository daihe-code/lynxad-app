<template>
  <div class="wrap">
    <header>
      <span class="brand">灵投</span>
      <span>完善资料，开通 7 天免费试用</span>
    </header>
    <el-card class="card">
      <h1>开通灵投</h1>
      <p class="sub">同一手机号只会开一家公司。完成后你将成为该公司负责人。</p>
      <el-form label-position="top" :model="form">
        <div class="grid">
          <el-form-item label="昵称" required>
            <el-input v-model="form.nickname" placeholder="怎么称呼你" />
          </el-form-item>
          <el-form-item label="邮箱" required>
            <el-input v-model="form.email" placeholder="name@company.com" />
          </el-form-item>
          <el-form-item label="公司名称" required>
            <el-input v-model="form.companyName" placeholder="营业执照上的名称" />
          </el-form-item>
          <el-form-item label="所属行业" required>
            <el-select v-model="form.industry" placeholder="请选择">
              <el-option label="烘焙食品" value="烘焙食品" />
              <el-option label="美妆个护" value="美妆个护" />
              <el-option label="服饰鞋包" value="服饰鞋包" />
              <el-option label="综合电商" value="综合电商" />
              <el-option label="本地生活" value="本地生活" />
              <el-option label="其他" value="其他" />
            </el-select>
          </el-form-item>
          <el-form-item label="岗位" required>
            <el-select v-model="form.position" placeholder="请选择">
              <el-option label="店主/老板" value="店主/老板" />
              <el-option label="运营" value="运营" />
              <el-option label="市场" value="市场" />
              <el-option label="其他" value="其他" />
            </el-select>
          </el-form-item>
          <el-form-item label="月预算" required>
            <el-select v-model="form.monthlyBudget" placeholder="请选择">
              <el-option label="5,000 以内" value="5000以内" />
              <el-option label="5,000 - 30,000" value="5000-30000" />
              <el-option label="30,000 - 100,000" value="30000-100000" />
              <el-option label="100,000 以上" value="100000以上" />
            </el-select>
          </el-form-item>
          <el-form-item label="投放经验" required>
            <el-select v-model="form.adExperience" placeholder="请选择">
              <el-option label="无经验" value="无经验" />
              <el-option label="1 年以内" value="1年以内" />
              <el-option label="1-3 年" value="1-3年" />
              <el-option label="3 年以上" value="3年以上" />
            </el-select>
          </el-form-item>
          <el-form-item label="核心诉求" required>
            <el-select v-model="form.coreDemand" placeholder="请选择">
              <el-option label="成交转化" value="成交转化" />
              <el-option label="控制成本" value="控制成本" />
              <el-option label="品牌曝光" value="品牌曝光" />
              <el-option label="获客到店" value="获客到店" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="设置登录密码" required :error="passwordError">
          <el-input v-model="form.password" type="password" show-password :placeholder="PASSWORD_HINT" />
        </el-form-item>
        <el-form-item label="确认密码" required>
          <el-input v-model="confirm" type="password" show-password placeholder="再次输入密码" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.agreeTerms">
            我已阅读并同意 <a href="/legal/terms" target="_blank" rel="noopener">《用户服务协议》</a>
          </el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.agreePrivacy">
            我已阅读并同意 <a href="/legal/privacy" target="_blank" rel="noopener">《隐私政策》</a>
          </el-checkbox>
        </el-form-item>
        <el-button type="primary" :loading="loading" class="full" @click="submit">开通并开始试用</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserStore } from "@/stores/user";
import { isValidPassword, PASSWORD_HINT } from "@/utils/password";

const user = useUserStore();
const router = useRouter();
const loading = ref(false);
const confirm = ref("");
const passwordError = ref("");
const form = reactive({
  nickname: "",
  email: "",
  companyName: "",
  industry: "",
  position: "",
  monthlyBudget: "",
  adExperience: "",
  coreDemand: "",
  password: "",
  agreeTerms: false,
  agreePrivacy: false,
});

async function submit() {
  const required = ["nickname", "email", "companyName", "industry", "position", "monthlyBudget", "adExperience", "coreDemand", "password"] as const;
  if (required.some((k) => !form[k])) {
    ElMessage.warning("请完整填写资料");
    return;
  }
  if (!form.agreeTerms || !form.agreePrivacy) {
    ElMessage.warning("须同时同意用户服务协议和隐私政策");
    return;
  }
  if (!isValidPassword(form.password)) {
    passwordError.value = PASSWORD_HINT;
    ElMessage.warning(PASSWORD_HINT);
    return;
  }
  passwordError.value = "";
  if (form.password !== confirm.value) {
    ElMessage.warning("两次密码不一致");
    return;
  }
  loading.value = true;
  try {
    await user.onboard({ ...form });
    ElMessage.success("试用已开通");
    router.replace("/accounts");
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.wrap {
  min-height: 100vh;
  padding: 32px 16px 64px;
}
header {
  max-width: 760px;
  margin: 0 auto 16px;
  display: flex;
  justify-content: space-between;
  color: var(--color-muted);
}
.brand {
  color: var(--color-primary);
  font-weight: 800;
  font-size: 22px;
}
.card {
  max-width: 760px;
  margin: 0 auto;
}
h1 {
  margin: 0;
}
.sub {
  color: var(--color-muted);
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.full {
  width: 100%;
}
@media (max-width: 700px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
