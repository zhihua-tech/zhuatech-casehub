# 知华企业案件协作：架构与接口

上海如静知华信息科技有限公司 · https://www.zhuatech.cn/

## 数据与服务边界

- Vue 3 H5 通过同域 `/api` 请求 Spring Boot；Nginx 反向代理，不在浏览器存放数据库凭证。
- Java 服务按 `tenant` 限制每次记录与附件读取；演示部署只有 `default` 租户，不提供自助租户开户。
- Flyway 建立 MySQL 业务记录、审计、幂等、账号、会话和附件表。业务动作在事务中锁定租户行，保存前检查版本号。
- `catalog.json` 只定义输入字段和可见状态动作；`Domain.java` 执行关联、金额/时限或闭环等领域校验；`Engine.java` 统一负责持久化与审计。
- 自动流水模块不允许通过通用创建接口直接写入，防止绕过业务动作伪造结果。

## 模块与动作

| API 模块键 | 页面名称 | 数据来源 | 业务动作 |
| --- | --- | --- | --- |
| `cases` | 案件台账 | 用户可新建 | `triage`、`submit`、`approve`、`return` |
| `tasks` | 调查任务 | 用户可新建 | `start`、`complete`、`reopen` |
| `evidence` | 证据登记 | 用户可新建 | `seal` |
| `closures` | 结案档案 | 仅业务动作生成 | — |

## API 最小调用约定

`POST /api/auth/login` 使用账号密码获取 Bearer token。业务创建为 `POST /api/records/{module}`，请求体包含 `code` 和 `data`，并须携带 8–80 位 `Idempotency-Key`。动作调用为 `POST /api/records/{id}/actions/{action}`，请求体包含 `version`、`data` 与非空 `remark`。失败时会返回 `message`，并不提交部分业务流水。

查询入口包括 `GET /api/catalog`、`/dashboard`、`/records?module=...&page=1&size=20`、`/records/{id}/history`；管理员使用 `/admin/users` 与 `/admin/audit`。CSV 为 `GET /api/export/{module}`。附件需登录上传与下载；扩展名和 2 MB 大小受限。

## 鉴权矩阵

| 角色 | 读业务 | 新建/一般处理 | 审核动作 | 管理账号与审计 |
| --- | --- | --- | --- | --- |
| VIEWER | 是 | 否 | 否 | 否 |
| OPERATOR | 是 | 是 | 否 | 否 |
| REVIEWER | 是 | 是 | 是 | 否 |
| ADMIN | 是 | 是 | 是（仍受自审隔离） | 是 |

动作的具体角色与独立审批要求以服务端目录配置及领域逻辑为准，前端禁用按钮不是安全边界。
