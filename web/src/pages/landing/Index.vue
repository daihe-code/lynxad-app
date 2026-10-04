<template>
  <LandingLayout>
    <main id="top">
      <section class="hero">
        <div class="wrap hero__grid">
          <div class="hero__copy">
            <span class="pill">智能广告投放专家</span>
            <h1>灵投，<em>让广告投出效果</em></h1>
            <p>用更小的广告投放成本，带来更多的客户，让生意增长<br />AI 自动接管选品、素材、投放、盯盘与优化全流程</p>
            <button class="cta" type="button" @click="goLogin">免费试用 7 天</button>
            <dl class="trust">
              <div><dt>7 天</dt><dd>免费试用</dd></div>
              <div><dt>无忧</dt><dd>不满意随时退</dd></div>
              <div><dt>官方</dt><dd>直连抖音巨量平台</dd></div>
            </dl>
          </div>
          <div class="device">
            <div class="device__frame">
              <div class="dots"><i /><i /><i /></div>
              <div class="shots">
                <figure v-for="shot in shots" :key="shot.alt">
                  <img :src="shot.src" :alt="shot.alt" />
                  <figcaption><span>{{ shot.label }}</span><span>CTR XX.XX%</span></figcaption>
                </figure>
              </div>
            </div>
            <aside class="float float--top">AI 优化效率<strong>+XX.XX%</strong></aside>
            <aside class="float float--bottom">今日消耗 / 成交<strong>¥XX.XX</strong><small>ROI XX.XX · 成交 XX 单</small></aside>
          </div>
        </div>
      </section>

      <section class="block">
        <div class="wrap">
          <h2>你是不是也这样？</h2>
          <p class="lead">中小微企业做广告投放，几乎都会卡在这四件事上</p>
          <div class="grid grid--4">
            <article v-for="item in pains" :key="item.title">
              <h3>{{ item.title }}</h3>
              <p>{{ item.body }}</p>
            </article>
          </div>
        </div>
      </section>

      <section id="features" class="block block--panel">
        <div class="wrap">
          <span class="pill pill--mint">全流程托管</span>
          <h2>灵投接管投放全流程<br />你只需专注生意</h2>
          <p class="lead">从选品到复盘，六个环节都由 AI 自动跑，你只需要看结果</p>
          <div class="grid grid--3">
            <article v-for="item in features" :key="item.title">
              <h3>{{ item.title }}</h3>
              <p>{{ item.body }}</p>
            </article>
          </div>
        </div>
      </section>

      <section id="industries" class="block">
        <div class="wrap">
          <span class="pill">行业方案</span>
          <h2>懂你的行业<br />更懂你的生意</h2>
          <p class="lead">针对不同行业特性，提供定制化的投放策略和解决方案</p>
          <div class="grid grid--2">
            <article class="industry industry--a">
              <h3>成长型电商</h3>
              <p>面向中小电商商家，智能选品、素材生成、全域投放一体化，低成本获取精准流量；从日销百元到万元的成长路径清晰可见。</p>
              <div class="tags"><span>智能选品</span><span>素材工厂</span><span>全域投放</span></div>
              <p class="roi">历史平均 ROI 提升 <b>38%</b></p>
            </article>
            <article class="industry industry--b">
              <h3>本地生活</h3>
              <p>面向餐饮、烘焙、美容等本地商家，基于 LBS 精准触达周边 3-5 公里客群，提升到店转化；团购核销、到店引流一站式搞定。</p>
              <div class="tags"><span>LBS 定向</span><span>到店核销</span><span>团购优化</span></div>
              <p class="roi">历史平均 ROI 提升 <b>52%</b></p>
            </article>
          </div>
        </div>
      </section>

      <section class="block block--data">
        <div class="wrap data">
          <div>
            <span class="pill">数据看板</span>
            <h2>数据一目了然<br />决策快人一步</h2>
            <p class="lead">消耗、成交、投产比、素材表现都在同一屏，每个数字都能追到具体的计划和素材</p>
            <ul>
              <li><b>六大核心指标</b><span>消耗、成交金额、ROI、成交订单数、点击率、转化成本，一屏看全。</span></li>
              <li><b>分钟级自动刷新</b><span>不用守着后台手动拉数据，指标变化即时同步。</span></li>
              <li><b>向下钻取到计划</b><span>指标异常直接下钻到计划与素材，知道该动哪一条。</span></li>
            </ul>
          </div>
          <div class="board">
            <div class="dots"><i /><i /><i /></div>
            <div class="metrics">
              <div><span>消耗</span><strong>¥XX.XX</strong></div>
              <div><span>成交金额</span><strong>¥XX.XX</strong></div>
              <div><span>投产比</span><strong>XX.XX</strong></div>
              <div><span>成交订单</span><strong>XX</strong></div>
            </div>
            <div class="chart" aria-hidden="true"><span v-for="n in 8" :key="n" :style="{ height: `${30 + n * 7}%` }" /></div>
          </div>
        </div>
      </section>

      <section class="why">
        <div class="wrap">
          <h2>为什么选择灵投？</h2>
          <p>无需承诺，试试就知道值不值</p>
          <div class="grid grid--4">
            <article v-for="item in reasons" :key="item.title">
              <h3>{{ item.title }}</h3>
              <p>{{ item.body }}</p>
            </article>
          </div>
        </div>
      </section>

      <section id="pricing" class="block block--price">
        <div class="wrap">
          <span class="pill">定价方案</span>
          <h2>选择适合你的方案</h2>
          <p class="lead">按月订阅，随时升级或取消，没有隐藏费用</p>
          <div class="grid grid--3 plans">
            <article>
              <h3>基础版</h3>
              <p class="price">¥299<span>/ 月</span></p>
              <p>适合刚起步、单账号试水的小微商家。</p>
              <ul>
                <li>1 个广告账户</li>
                <li>智能选品与选品建议</li>
                <li>每日素材生成 20 条</li>
                <li>标准数据看板</li>
              </ul>
              <button type="button" @click="goLogin">开始免费试用</button>
            </article>
            <article class="plan--hot">
              <span class="badge">推荐</span>
              <h3>专业版</h3>
              <p class="price">¥799<span>/ 月</span></p>
              <p>适合已有稳定出单、需要放量的成长型商家。</p>
              <ul>
                <li>5 个广告账户</li>
                <li>全流程 AI 托管</li>
                <li>每日素材生成 200 条</li>
                <li>智能盯盘与自动优化</li>
                <li>多账户聚合看板</li>
                <li>专属客户成功对接</li>
              </ul>
              <button class="cta" type="button" @click="goLogin">立即开通</button>
            </article>
            <article>
              <h3>定制版</h3>
              <p class="price">定制</p>
              <p>适合多品牌、多门店或需要私有化部署的团队。</p>
              <ul>
                <li>账户数量不限</li>
                <li>行业专属策略模板</li>
                <li>素材生成不设上限</li>
                <li>私有化部署与数据隔离</li>
                <li>专属策略顾问陪跑</li>
                <li>定制报表与对接支持</li>
              </ul>
              <button type="button" @click="openContact">联系客服咨询</button>
            </article>
          </div>
        </div>
      </section>

      <section id="faq" class="block">
        <div class="wrap">
          <h2>常见问题</h2>
          <p class="lead">关于灵投智营，你可能想先了解这些</p>
          <div class="faqs">
            <details v-for="(item, index) in faqs" :key="item.q" :open="index === 0">
              <summary>{{ item.q }}</summary>
              <p>{{ item.a }}</p>
            </details>
          </div>
        </div>
      </section>

      <section id="cta" class="cta-band">
        <div class="wrap">
          <h2>准备好让灵投<br />接管你的广告投放了吗？</h2>
          <p>7 天免费试用，不满意随时退；先看看效果，再决定要不要继续</p>
          <div class="cta-band__actions">
            <button class="cta cta--light" type="button" @click="goLogin">免费试用 7 天</button>
            <button class="cta cta--glass" type="button" @click="openContact">联系客服咨询</button>
          </div>
        </div>
      </section>
    </main>

    <footer>
      <div class="wrap foot">
        <div>
          <strong>灵投智营</strong>
          <p>让中小微企业也能获得专家级广告投放效果</p>
        </div>
        <nav>
          <router-link to="/#faq">帮助中心</router-link>
          <router-link to="/legal/privacy">隐私政策</router-link>
          <button type="button" @click="openContact">联系我们</button>
        </nav>
      </div>
      <p class="copy">© 2026 灵投智营·智能广告投放专家 &nbsp;|&nbsp; 坤顷科技</p>
    </footer>
  </LandingLayout>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import LandingLayout from "@/layouts/LandingLayout.vue";
import { useUserStore } from "@/stores/user";
import { useContactDialog } from "@/features/notify/useContactDialog";
import hero1 from "@/assets/landing/hero-1.jpg";
import hero2 from "@/assets/landing/hero-2.jpg";
import hero3 from "@/assets/landing/hero-3.jpg";
import hero4 from "@/assets/landing/hero-4.jpg";

const DEFAULT_TITLE = "灵投 LynxAd · 替你投出效果";
const PAGE_TITLE = "灵投智营 · 让广告投出效果｜智能广告投放专家";

const shots = [
  { src: hero1, alt: "手冲咖啡与甜点素材示例", label: "食品饮料" },
  { src: hero2, alt: "行李箱出行场景素材示例", label: "箱包出行" },
  { src: hero3, alt: "复古汽车素材示例", label: "汽车用品" },
  { src: hero4, alt: "服装挂架素材示例", label: "服饰鞋包" },
];

const pains = [
  { title: "投了没效果", body: "预算花完了，订单却没几单；出价来回调，ROI 还是一路往下掉，一个月下来都不知道钱到底花在哪里。" },
  { title: "不会投不敢投", body: "千川后台太复杂，出价、定向、素材、落地页样样都要学；身边没人带，自己试错一轮成本太高。" },
  { title: "找专业团队不划算", body: "代运营一个月报价几千上万，沟通成本也高；预算不大的商家根本请不动，请了也未必盯得过来。" },
  { title: "广告数据看不懂", body: "报表里几十个指标，不知道该看哪个、该调哪个；看完还是不知道下一步该做什么动作。" },
];

const features = [
  { title: "智能选品", body: "结合类目大盘走势与商品自身潜力，自动挑出真正值得投放的品，冷启动不再凭感觉做决策。" },
  { title: "素材智能生成", body: "一条商品链接批量产出短视频与图文素材，脚本、画面、口播一次到位，不用再等设计排期。" },
  { title: "智能盯盘优化", body: "7×24 小时盯着账户跑量，计划跑偏自动拉回，预算始终花在能出单的时段和人群上。" },
  { title: "智能预算调配", body: "按实时投产比自动加减预算，好计划加码、差计划止损，不用每天早起手动改出价。" },
  { title: "账户智能诊断", body: "一键体检全部在投计划，定位消耗跑不出去、转化成本偏高的真实原因，并给出调整建议。" },
  { title: "智能审核修复", body: "投放内容先过一遍合规校验，违规词与风险画面提前拦下，避免账户被限流影响整体跑量。" },
];

const reasons = [
  { title: "7 天免费试用", body: "注册即可开通全部功能，免费试用 7 天，期间不限制功能，也不需要先付费。" },
  { title: "效果看得见", body: "消耗、成交、ROI 全程透明，每一笔变化都能追溯到具体计划与素材，效果不靠感觉判断。" },
  { title: "安全合规", body: "官方接口授权，账户信息加密存储，素材先审核后投放，全程不接触你的账号密码。" },
  { title: "不满意随时退", body: "试用期内如果不满意，按剩余天数无条件退款，不设任何门槛和附加条件。" },
];

const faqs = [
  { q: "灵投和巨量千川官方后台是什么关系？", a: "灵投智营是基于巨量千川官方开放接口构建的智能投放工具，通过官方授权访问你的广告账户。它不是后台的替代品，而是帮你把选品、素材、出价、盯盘这些重复动作自动化，你在千川后台里看到的数据和灵投保持一致。" },
  { q: "用灵投需要自己有投放经验吗？", a: "不需要。灵投会自动完成选品、生成素材、搭建计划并持续优化，你只需要确认预算和商品，剩下的交给系统。后台也提供每一步的说明，方便你随时了解系统在做什么。" },
  { q: "我可以随时取消使用灵投服务吗？", a: "可以。灵投按月订阅，随时可以在账户设置里取消，取消后服务持续到当前周期结束，不会自动续费，也不影响你在千川后台的投放。" },
  { q: "广告账户授权安全吗？", a: "授权走巨量引擎官方 OAuth 流程，灵投不会接触你的账号密码。所有凭证加密存储，且可以随时在灵投或巨量引擎后台收回授权。" },
  { q: "灵投生成的素材会有版权风险吗？", a: "灵投生成的素材均为原创产出，并会在生成侧做原创度比对与合规校验，投放前再过一遍违规词与风险画面检查，降低限流风险。" },
  { q: "多久能看到投放效果？", a: "接入账户后当天即可开始投放，通常 3 天左右能跑出稳定的基础数据，7 天可以形成第一轮优化闭环，之后效果会随着数据积累持续改善。" },
];

const router = useRouter();
const user = useUserStore();
const { open: openContact } = useContactDialog();

onMounted(() => {
  document.title = PAGE_TITLE;
});
onUnmounted(() => {
  document.title = DEFAULT_TITLE;
});

function goLogin() {
  router.push(user.isLoggedIn ? "/subscription" : "/login");
}
</script>

<style scoped>
#features, #industries, #pricing, #faq, #cta, #top { scroll-margin-top: 72px; }
.wrap { width: min(1200px, calc(100% - 48px)); margin: 0 auto; }
h2 { margin: 16px 0 0; color: #1a1b2e; font-size: 38px; line-height: 1.22; letter-spacing: -0.02em; text-align: center; }
.lead { margin: 12px auto 0; max-width: 640px; color: #7c7f99; font-size: 16px; line-height: 1.75; text-align: center; }
.pill { display: inline-flex; padding: 6px 12px; border-radius: 999px; background: #fff; color: #4348db; font-size: 13px; font-weight: 600; }
.pill--mint { background: #e8f8f1; color: #12b76a; }
.block { padding: 80px 0; text-align: center; }
.block--panel { background: #f7f7ff; }
.block--data { background: #faf9ff; text-align: left; }
.block--price { background: #f5f5ff; }
.grid { display: grid; gap: 20px; margin-top: 48px; text-align: left; }
.grid article { padding: 32px; border-radius: 16px; background: #fff; box-shadow: 0 10px 30px -18px rgba(26, 27, 46, 0.25); }
.grid h3 { margin: 0; color: #1a1b2e; font-size: 18px; }
.grid p, .grid li { color: #4e5069; font-size: 14px; line-height: 1.7; }
.hero { background: #eef0fd; }
.hero__grid { display: grid; gap: 48px; align-items: center; padding: 72px 0; }
.hero__copy { text-align: center; }
.hero h1 { margin: 20px 0 0; color: #1a1b2e; font-size: 52px; line-height: 1.12; }
.hero h1 em { color: #575cf6; font-style: normal; }
.hero__copy > p { margin: 20px 0 0; color: #4e5069; font-size: 16px; line-height: 1.75; }
.cta {
  height: 48px; margin-top: 28px; padding: 0 36px; border: 0; border-radius: 12px; cursor: pointer;
  background: linear-gradient(90deg, #575cf6, #3b82f6); color: #fff; font-size: 16px; font-weight: 600;
}
.trust { display: flex; justify-content: center; gap: 48px; margin: 40px 0 0; }
.trust dt { color: #575cf6; font-size: 24px; font-weight: 700; }
.trust dd { margin: 8px 0 0; color: #7c7f99; font-size: 14px; }
.device { position: relative; }
.device__frame, .board { border-radius: 20px; background: #1e1e38; padding: 10px; }
.dots { display: flex; gap: 6px; padding: 8px 6px; }
.dots i { width: 8px; height: 8px; border-radius: 99px; background: #ff5f57; }
.dots i:nth-child(2) { background: #febc2e; }
.dots i:nth-child(3) { background: #28c840; }
.shots { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; padding: 0 6px 8px; background: #fff; border-radius: 12px; }
.shots img { width: 100%; height: 140px; object-fit: cover; border-radius: 8px; }
.shots figcaption { display: flex; justify-content: space-between; margin-top: 6px; color: #7c7f99; font-size: 11px; }
.float { position: absolute; padding: 12px 14px; border-radius: 14px; background: #fff; box-shadow: 0 12px 30px rgba(26, 27, 46, 0.12); font-size: 12px; color: #7c7f99; }
.float strong { display: block; margin-top: 4px; color: #1a1b2e; font-size: 16px; }
.float--top { top: -12px; right: -8px; }
.float--bottom { left: -8px; bottom: -16px; }
.industry--a { background: #eef3ff; }
.industry--b { background: #f3eefe; }
.tags { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
.tags span { padding: 6px 12px; border-radius: 999px; background: #fff; color: #4348db; font-size: 13px; }
.roi b { margin-left: 8px; color: #4348db; font-size: 32px; }
.data { display: grid; gap: 40px; align-items: center; }
.data .lead, .data h2 { text-align: left; margin-left: 0; }
.data ul { margin: 28px 0 0; padding: 0; list-style: none; }
.data li { margin-top: 18px; }
.data b, .data span { display: block; }
.board { background: #1e1e38; }
.metrics, .chart { display: grid; gap: 8px; padding: 12px; border-radius: 12px; background: #fff; }
.metrics { grid-template-columns: repeat(4, 1fr); }
.metrics span { color: #7c7f99; font-size: 12px; }
.chart { grid-template-columns: repeat(8, 1fr); align-items: end; height: 120px; margin-top: 8px; }
.chart span { display: block; border-radius: 3px 3px 0 0; background: rgba(87, 92, 246, 0.2); }
.chart span:last-child { background: #575cf6; }
.why { padding: 86px 0 100px; background: #1e1e38; text-align: center; }
.why h2, .why > .wrap > p { color: #fff; }
.why article { background: #2a2a48; }
.why h3 { color: #fff; }
.why p { color: rgba(255, 255, 255, 0.65); }
.plans article { display: flex; flex-direction: column; }
.plans ul { margin: 20px 0; padding: 0; list-style: none; }
.price { color: #1a1b2e; font-size: 40px; font-weight: 700; }
.price span { margin-left: 6px; color: #7c7f99; font-size: 13px; font-weight: 400; }
.plans button { margin-top: auto; height: 40px; border: 0; border-radius: 10px; background: #eeeefe; color: #4348db; font-weight: 600; cursor: pointer; }
.plan--hot { border: 2px solid #575cf6; }
.badge { align-self: flex-end; padding: 4px 10px; border-radius: 999px; background: #575cf6; color: #fff; font-size: 12px; }
.faqs { max-width: 920px; margin: 40px auto 0; text-align: left; }
.faqs details { margin-top: 12px; border-radius: 12px; background: #f7f8fe; }
.faqs summary { padding: 20px 24px; color: #1a1b2e; font-weight: 600; cursor: pointer; }
.faqs p { margin: 0; padding: 0 24px 20px; }
.cta-band {
  padding: 80px 0; text-align: center; color: #fff;
  background:
    radial-gradient(58% 92% at 80% 26%, rgba(139, 92, 246, 0.55), transparent 68%),
    linear-gradient(118deg, #5a5bf4 0%, #4a4cdd 34%, #4543cb 66%, #3634a8 100%);
}
.cta-band h2 { color: #fff; font-size: 44px; }
.cta--light { background: #fff; color: #4348db; }
.cta--glass { margin-left: 12px; background: transparent; color: #fff; border: 1px solid rgba(255, 255, 255, 0.45); }
footer { padding: 28px 0 36px; background: #4645cc; color: rgba(255, 255, 255, 0.85); }
.foot { display: flex; justify-content: space-between; gap: 24px; }
.foot strong { color: #fff; font-size: 19px; }
.foot nav { display: flex; gap: 28px; align-items: center; }
.foot a, .foot button { border: 0; background: transparent; color: rgba(255, 255, 255, 0.9); font-size: 14px; cursor: pointer; text-decoration: none; }
.copy { margin: 28px 0 0; text-align: center; font-size: 13px; }
@media (min-width: 900px) {
  .hero__grid, .data { grid-template-columns: 1.1fr 0.9fr; }
  .grid--4 { grid-template-columns: repeat(4, 1fr); }
  .grid--3 { grid-template-columns: repeat(3, 1fr); }
  .grid--2 { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 720px) {
  .hero h1, .cta-band h2, h2 { font-size: 32px; }
  .trust, .foot, .metrics { display: grid; grid-template-columns: 1fr 1fr; }
}
</style>
