# 安全策略 / Security Policy

## 报告漏洞

发现安全漏洞请**私下**报告，不要开公开 issue：

- 邮件：请联系仓库维护者（见 GitHub profile 页留出的邮箱）
- 或 GitHub 私密漏洞报告（Security advisories）

请尽量附上复现步骤与影响评估。收到后我们会在合理时间内响应。

## 关于密钥（重要）

本项目运行期配置（`data/*.json`）会承载敏感凭证：LLM API key、微信账号标识等。

- **绝对不要**在 issue、PR、截图、日志里粘贴真实 API key / token。
- `data/*.json` 已在 `.gitignore` 中忽略，仓库只跟踪 `data/*.example` 模板（占位值）。
- 若你不慎把真实密钥提交到了仓库，请**立即在对应云控制台吊销/轮换该密钥**，再清理 git 历史。
  历史中的密钥即使删除文件仍可被检索到，吊销是唯一可靠的处理。

## 本地安全建议

- 只在 `data/` 下放配置，并确保该目录不进备份/同步盘。
- `task-config.json` 的 `claudeAdminUsers` 是可执行 `/sudo` 提权的微信 userId 白名单，请只放可信账号。
- Claude Bridge 默认 `permission-mode: default`（受限只读）；提权档 `bypassPermissions` 仅管理员可切，慎用。
