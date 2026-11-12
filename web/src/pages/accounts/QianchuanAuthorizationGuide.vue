<template>
  <div class="authorization-guide">
    <p class="authorization-guide__intro">按以下步骤完成授权，账户与素材权限都不要遗漏。</p>
    <div class="authorization-guide__columns">
      <section class="authorization-guide__steps" aria-labelledby="authorization-guide-steps">
        <h3 id="authorization-guide-steps">按这 3 步完成授权</h3>
        <ol class="guide-timeline">
          <li>
            <span class="guide-timeline__number" aria-hidden="true">1</span>
            <h4>选择账户，完整勾选相关维度</h4>
            <p>选择「当前用户部分账户」，依次检查工作台、方舟、抖店、抖音号等栏目，完整勾选本次交给坤境管理的相关账户。</p>
            <p class="guide-timeline__emphasis">同一维度内的账户均需接入时，可使用“全选”，避免遗漏。</p>
            <aside class="guide-timeline__safety">
              <AccountIcon name="shield" />
              <div><strong>全选不代表允许任意操作</strong><p>我只会在平台授予的权限和你确认的托管范围内处理任务；需要你确认的投放操作，仍会先征求你的同意。</p></div>
            </aside>
          </li>
          <li>
            <span class="guide-timeline__number" aria-hidden="true">2</span>
            <h4>勾选素材管理授权</h4>
            <div class="guide-timeline__permission"><span class="guide-example-check" aria-hidden="true"><AccountIcon name="check" /></span><strong>允许开发者管理敏感物料</strong></div>
            <p>请阅读<span class="guide-timeline__agreement">《敏感物料授权书》</span>，确认后勾选。未勾选将影响相关素材获取、素材生成及投放能力。</p>
          </li>
          <li>
            <span class="guide-timeline__number" aria-hidden="true">3</span>
            <h4>点击「确认授权」</h4>
            <p>核对账户和授权内容后确认。返回坤境后，账户信息和授权有效期将自动同步。</p>
          </li>
        </ol>
      </section>

      <section class="authorization-guide__example" aria-labelledby="authorization-guide-example">
        <h3 id="authorization-guide-example">授权页面操作示例</h3>
        <figure class="guide-example">
          <div class="guide-example__viewport" tabindex="0" role="region" aria-label="授权页面示意图，小屏可横向滚动查看">
            <svg class="guide-example__page" viewBox="0 0 660 558" role="img" aria-label="千川官方授权页面示意：选择当前用户部分账户，逐项检查工作台、方舟、抖店和抖音号；勾选允许开发者管理敏感物料，最后确认授权。">
              <rect x=".5" y=".5" width="659" height="557" rx="8" fill="white" stroke="#e4e7f2" />
              <path d="M1 42h658" stroke="#edf0f6" />
              <g transform="translate(14 13)" fill="none" stroke="#00aaff" stroke-width="2.5" stroke-linejoin="round">
                <path d="m10 0 10 7-4 14L1 16 2 5Z" /><path d="m10 4 5 10H5l5-10Z" /><path d="m1 16 14-2 5-7" />
              </g>
              <text x="39" y="28" class="example-brand">巨量引擎开放平台</text>
              <circle cx="539" cy="21" r="12" fill="#deedff" />
              <circle cx="539" cy="18" r="4" fill="#578ef7" /><path d="M532 29v-3a7 7 0 0 1 14 0v3" fill="#578ef7" />
              <text x="557" y="17" class="example-small example-bold">示例账户</text><text x="557" y="30" class="example-muted example-tiny">c******@163.com</text>
              <path d="m636 20 3 3 3-3" fill="none" stroke="#959ab0" />

              <rect x="13" y="51" width="160" height="362" rx="4" fill="#fcfcfe" stroke="#eff0f5" />
              <text x="22" y="70" class="example-small example-bold">以下应用申请您的授权</text>
              <rect x="23" y="89" width="33" height="33" rx="10" fill="#4387ff" />
              <g fill="white"><rect x="29" y="95" width="9" height="9" rx="2" /><rect x="41" y="95" width="9" height="9" rx="2" /><rect x="29" y="107" width="9" height="9" rx="2" /><rect x="41" y="107" width="9" height="9" rx="2" /></g>
              <text x="65" y="102" class="example-small example-bold">坤境智营-v1</text><text x="65" y="118" class="example-muted example-tiny">示例应用 · 账户授权</text>
              <text x="22" y="147" class="example-small example-bold">授权后，该应用将获得以下权限</text>
              <g v-for="(permission, index) in permissions" :key="permission" :transform="`translate(29 ${172 + index * 21})`">
                <circle r="1.7" fill="#9297ac" /><text x="12" y="3" class="example-muted example-small">{{ permission }}</text>
              </g>

              <text x="186" y="66" class="example-small example-bold">对该应用授权以下账户权限</text>
              <rect x="187" y="75" width="143" height="46" rx="3" fill="#f8f9fc" />
              <text x="200" y="94" class="example-small example-bold">当前用户全量账户</text><text x="200" y="110" class="example-muted example-tiny">开发者可访问您授权的全部账户</text>
              <rect x="337" y="75" width="143" height="46" rx="3" fill="#fff" stroke="#4387ff" stroke-width="1.5" />
              <text x="350" y="94" class="example-small example-blue example-bold">当前用户部分账户</text><text x="350" y="110" class="example-muted example-tiny">仅向开发者开放部分账户数据</text>

              <g class="example-callout">
                <rect x="524" y="97" width="125" height="27" rx="8" fill="#713dff" /><circle cx="537" cy="110.5" r="8" fill="white" />
                <text x="537" y="114" text-anchor="middle" fill="#713dff">1</text><text x="550" y="115" fill="white">逐项检查各维度</text>
                <path d="m584 124-5 8-4-8" fill="#713dff" />
              </g>
              <rect x="185" y="129" width="446" height="30" rx="4" fill="#fcfaff" stroke="#8a56ff" stroke-width="1.5" />
              <g v-for="(dimension, index) in dimensions" :key="dimension" :transform="`translate(${201 + index * 101} 147)`">
                <text :class="index === 0 ? 'example-small example-blue example-bold' : 'example-small example-muted'">{{ dimension }}</text>
              </g>
              <circle cx="198" cy="173" r="4.5" fill="#4387ff" /><text x="198" y="176" text-anchor="middle" fill="white" font-size="8">i</text>
              <text x="208" y="177" class="example-muted example-tiny">支持授权巨量千川投放账户，按维度选择需接入的账户。</text>
              <rect x="198" y="187" width="113" height="22" rx="3" fill="#f3f4f7" /><text x="207" y="202" class="example-muted example-small">账户类型：请选择</text>
              <rect x="317" y="187" width="131" height="22" rx="3" fill="#f3f4f7" /><text x="326" y="202" class="example-muted example-small">工作台名称/ID　⌄</text>
              <rect x="454" y="187" width="179" height="22" rx="3" fill="#f3f4f7" /><text x="463" y="202" class="example-muted example-small">⌕　请输入工作台名称/ID</text>

              <rect x="198" y="220" width="206" height="24" rx="4" fill="#fff" stroke="#8a56ff" stroke-width="1.5" />
              <rect x="210" y="225" width="13" height="13" rx="2" fill="#3376ff" /><path d="m213 231 3 3 5-6" fill="none" stroke="white" stroke-width="1.5" />
              <text x="235" y="236" class="example-small example-purple example-bold">本维度需接入的账户可全选</text>
              <rect x="198" y="249" width="435" height="26" fill="#f8f9fc" />
              <rect x="210" y="255" width="13" height="13" rx="2" fill="#9eabff" /><path d="m213 261 3 3 5-6" fill="none" stroke="white" stroke-width="1.5" />
              <text x="255" y="266" class="example-muted example-small">账户信息</text><text x="479" y="266" class="example-muted example-small">授权状态</text><text x="558" y="266" class="example-muted example-small">账户类型</text>
              <g v-for="(account, index) in exampleAccounts" :key="account" :transform="`translate(198 ${275 + index * 55})`">
                <path d="M0 55h435" stroke="#eff0f5" /><rect x="12" y="20" width="13" height="13" rx="2" fill="#3376ff" /><path d="m15 26 3 3 5-6" fill="none" stroke="white" stroke-width="1.5" />
                <rect x="57" y="9" width="35" height="13" rx="3" fill="#f1f2f6" /><text x="60" y="19" class="example-muted example-tiny">管理员</text><text x="99" y="19" class="example-small">{{ account }}</text>
                <text x="57" y="34" class="example-muted example-tiny">ID: ************</text><text x="57" y="48" class="example-blue example-tiny">查看投放账户</text>
                <circle cx="284" cy="28" r="3" fill="#64b835" /><text x="292" y="32" class="example-small">已授权</text><text x="360" y="32" class="example-muted example-tiny">巨量引擎工作台</text>
              </g>
              <text x="340" y="404" class="example-muted example-tiny">共 2 条记录</text>
              <rect x="405" y="391" width="18" height="18" rx="3" fill="#fafbfe" stroke="#e6e8f0" /><text x="412" y="403" class="example-muted example-small">‹</text>
              <rect x="430" y="391" width="18" height="18" rx="3" fill="#f2f5ff" stroke="#4387ff" /><text x="436" y="404" class="example-blue example-small">1</text>
              <rect x="455" y="391" width="18" height="18" rx="3" fill="#fafbfe" stroke="#e6e8f0" /><text x="462" y="403" class="example-muted example-small">›</text>
              <text x="491" y="404" class="example-muted example-tiny">10 条/页　⌄　　跳转</text>

              <rect x="8" y="428" width="642" height="104" rx="10" fill="#fff8e9" stroke="#ffb333" />
              <circle cx="32" cy="446" r="9" fill="#e69800" /><text x="32" y="450" text-anchor="middle" fill="white" font-size="12" font-weight="600">2</text>
              <text x="49" y="451" fill="#cd8700" font-size="14" font-weight="600">左下角选项放大示意</text>
              <rect x="23" y="461" width="331" height="61" rx="6" fill="white" stroke="#f4d49c" />
              <rect x="47" y="473" width="21" height="21" rx="3" fill="#3376ff" /><path d="m52 483 5 5 8-10" fill="none" stroke="white" stroke-width="2" />
              <text x="83" y="486" font-size="15" font-weight="600">允许开发者管理敏感物料</text>
              <text x="83" y="508" fill="#777d98" font-size="11">勾选即表示已同意<tspan fill="#3376ff">《敏感物料授权书》</tspan></text>
              <g class="example-callout">
                <rect x="530" y="441" width="105" height="27" rx="8" fill="#713dff" /><circle cx="544" cy="454.5" r="8" fill="white" />
                <text x="544" y="458" text-anchor="middle" fill="#713dff">3</text><text x="557" y="459" fill="white">确认授权</text><path d="m587 468-5 8-4-8" fill="#713dff" />
              </g>
              <rect x="523" y="478" width="119" height="45" rx="6" fill="none" stroke="#713dff" stroke-width="1.5" />
              <rect x="530" y="484" width="105" height="33" rx="4" fill="#386bff" /><text x="582.5" y="505" text-anchor="middle" fill="white" font-size="13">确认授权</text>
              <text x="13" y="550" class="example-muted" font-size="11">操作示例，实际页面以平台展示为准</text>
            </svg>
          </div>
          <figcaption class="guide-example__mobile-hint">左右滑动查看完整示例</figcaption>
        </figure>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import AccountIcon from './AccountIcon.vue';

const dimensions = ['巨量引擎工作台', '巨量引擎方舟', '抖店', '抖音号'];
const permissions = ['工具', '投放管理', '素材管理', '资金与流水查询', '巨量引擎工作台管理', '代理商管理', '数据报表', '店铺账户管理'];
const exampleAccounts = ['示例账户 BP-********', '示例店铺'];
</script>

<style scoped>
.authorization-guide__intro { margin: 0; color: #747996; font-size: 14px; line-height: 1.7; }
.authorization-guide__columns { display: grid; grid-template-columns: minmax(0, .6fr) minmax(0, 1fr); gap: 20px; margin-top: 16px; }
.authorization-guide h3 { margin: 0 0 14px; color: #22243a; font-size: 18px; line-height: 1.5; font-weight: 600; }
.authorization-guide__steps { padding: 14px 0 6px; }
.authorization-guide__example { min-width: 0; padding-left: 20px; border-left: 1px solid #e8eaf3; }
.authorization-guide__example h3 { margin-bottom: 8px; }
.guide-timeline { list-style: none; padding: 8px 0 0; margin: 0; counter-reset: guide-step; }
.guide-timeline > li { position: relative; padding: 0 0 40px 47px; }
.guide-timeline > li:last-child { padding-bottom: 0; }
.guide-timeline > li:not(:last-child)::before { content: ''; position: absolute; left: 15px; top: 35px; bottom: 6px; border-left: 1px solid #d8dbee; }
.guide-timeline__number { position: absolute; left: 0; top: -2px; display: grid; place-items: center; width: 32px; height: 32px; color: white; background: linear-gradient(145deg, #7464ff, #5137ff); box-shadow: inset 0 0 0 2px #ffffff1a; border-radius: 50%; font-size: 19px; font-weight: 500; }
.guide-timeline h4 { margin: 0 0 12px; color: #25283f; font-size: 17px; line-height: 1.65; font-weight: 600; }
.guide-timeline p { margin: 0; font-size: 13px; line-height: 1.8; color: #747996; }
.guide-timeline p + p { margin-top: 9px; }
.guide-timeline .guide-timeline__emphasis { font-weight: 600; color: #25283f; }
.guide-timeline__safety { display: flex; gap: 11px; margin-top: 10px; padding: 12px; border-radius: 8px; background: #f5f3ff; }
.guide-timeline__safety > svg { flex: none; width: 26px; height: 26px; color: #6258ff; }
.guide-timeline__safety strong { color: #25283f; font-size: 13px; font-weight: 600; line-height: 1.6; }
.guide-timeline__safety p { margin-top: 3px; font-size: 12px; line-height: 1.7; }
.guide-timeline__permission { display: flex; align-items: center; gap: 10px; margin: 14px 0 12px; color: #25283f; font-size: 13px; line-height: 1.6; }
.guide-example-check { flex: none; display: grid; place-items: center; width: 20px; height: 20px; border-radius: 3px; color: white; background: #386bff; }
.guide-example-check svg { width: 19px; height: 19px; }
.guide-timeline__agreement { color: #386bff; }
.guide-example { margin: 0; }
.guide-example__viewport { overflow-x: auto; overscroll-behavior-x: contain; border-radius: 8px; }
.guide-example__viewport:focus-visible { outline: 2px solid #a6a0ff; outline-offset: 3px; }
.guide-example__page { display: block; width: 100%; min-width: 560px; height: auto; color: #25283f; font-family: inherit; }
.guide-example__page text:not([fill]) { fill: currentColor; }
.example-brand { font-size: 16px; font-weight: 600; }
.example-small { font-size: 10px; }
.example-tiny { font-size: 8px; }
.example-muted { color: #81869b; }
.example-bold { font-weight: 600; }
.example-blue { color: #3376ff; }
.example-purple { color: #8a40ff; }
.example-callout { font-size: 12px; font-weight: 600; }
.guide-example__mobile-hint { display: none; }
@media (max-width: 1099px) {
  .authorization-guide__columns { grid-template-columns: minmax(0, 1fr); gap: 26px; }
  .authorization-guide__steps { padding-top: 0; }
  .authorization-guide__example { padding: 22px 0 0; border-left: 0; border-top: 1px solid #e8eaf3; }
  .guide-timeline > li { padding-bottom: 28px; }
}
@media (max-width: 639px) {
  .authorization-guide__intro { font-size: 13px; }
  .authorization-guide h3 { font-size: 16px; }
  .guide-timeline > li { padding-left: 40px; }
  .guide-timeline h4 { font-size: 15px; }
  .guide-timeline__number { width: 28px; height: 28px; font-size: 17px; }
  .guide-timeline > li:not(:last-child)::before { left: 13px; top: 32px; }
  .guide-timeline__safety { gap: 8px; padding: 10px; }
  .guide-timeline__safety > svg { width: 23px; height: 23px; }
  .guide-example__mobile-hint { display: block; margin-top: 8px; text-align: center; color: #858aa5; font-size: 12px; }
}
</style>
