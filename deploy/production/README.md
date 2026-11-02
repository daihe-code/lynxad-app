# 应用发布来源

应用前端 `web/` 和后端 `lynx-ad-app/` 统一使用 [GitHub 应用仓库](https://github.com/daihe-code/lynxad-app)，发布分支为 `main`。Gitee 已停用，不作为拉取、推送或发布来源。

本机 `origin` 的拉取与推送地址均为 `https://github.com/daihe-code/lynxad-app.git`。

发布前先完成必要验证并推送代码，再执行 `git fetch origin main`，确认工作区干净且待发布提交与 GitHub 的 `origin/main` 一致。前后端构建使用该提交的 Git 归档，并在发布记录中保留提交哈希。

只有用户单独明确要求上线时才执行生产部署；推送代码不会自动发布。
