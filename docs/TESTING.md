# 验收记录（2026-09-12）

上海如静知华信息科技有限公司 · https://www.zhuatech.cn/

- 后端 HTTP 业务场景与安全回归：29 项通过，0 失败、0 错误。执行入口：`sh scripts/test.sh`；用例与失败分支见 `backend/src/test/resources/acceptance.json`。
- 前端模型与权限单测：6 项通过；Vite 生产构建通过。
- Docker Compose 镜像构建、MySQL 8.4 初始化和 `GET /api/health`（HTTP 200）通过。
- 用公开的本机演示配置登录浏览器，核对业务端、管理员端和 390px 手机视口；真实运行截图见 `docs/images/`。
- 本地自动化后端测试使用 H2 MySQL 兼容模式；仓库的 GitHub Actions 工作流配置了 MySQL 8.4 测试服务。此记录不代替正式生产环境的安全、负载与恢复验收。
