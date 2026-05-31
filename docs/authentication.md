# VoiceCal 登录功能

## 数据库升级

新建数据库时直接执行 `src/main/resources/schema.sql`。

已有数据库需要执行：

```sql
ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(100) NULL AFTER username;
```

同一份脚本位于 `docs/sql/V10__user_authentication.sql`。

## 启动配置

开发环境未设置签名密钥时，后端会在每次启动时生成临时密钥。后端重启后，已有登录 Cookie 会失效。

部署环境必须设置固定密钥。使用 HTTPS 时还应启用 Cookie 的 `Secure` 属性：

```powershell
$env:VOICECAL_AUTH_SECRET = "replace-with-a-long-random-secret"
$env:VOICECAL_AUTH_COOKIE_SECURE = "true"
```

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/register` | 注册并登录 |
| `POST` | `/api/auth/login` | 登录 |
| `GET` | `/api/auth/me` | 获取当前用户 |
| `POST` | `/api/auth/logout` | 退出登录 |

登录状态保存在 HttpOnly、SameSite=Strict Cookie 中。浏览器脚本无法直接读取令牌。业务接口从 Cookie 解析当前用户，不接受客户端指定用户身份。
