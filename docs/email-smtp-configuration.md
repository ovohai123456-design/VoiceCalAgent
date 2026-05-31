# SMTP 邮件配置

日程邮件默认关闭。启用前需要为用户偏好设置 `defaultEmail`，或在创建日程请求中传入 `emailReceiver`。

## 环境变量

QQ 邮箱可直接使用默认的 `smtp.qq.com:465`，`MAIL_PASSWORD` 填邮箱 SMTP 授权码，不是登录密码。

```powershell
$env:MAIL_USERNAME = "sender@qq.com"
$env:MAIL_PASSWORD = "smtp-authorization-code"
$env:MAIL_FROM = "sender@qq.com"
$env:VOICECAL_EMAIL_ENABLED = "true"
```

其他邮箱服务可以覆盖以下配置：

```powershell
$env:MAIL_HOST = "smtp.example.com"
$env:MAIL_PORT = "465"
$env:MAIL_SMTP_SSL_ENABLE = "true"
$env:MAIL_SMTP_STARTTLS_ENABLE = "false"
```

## 发送规则

- 创建日程后，发送一封创建成功通知。
- `reminderMinutes > 0` 时，在日程开始前对应分钟数发送提醒邮件。
- 同一日程的站内提醒仍然保留。
- 定时扫描默认每 `60` 秒执行一次，因此提醒邮件可能在计划时间后最多约 `60` 秒发出。
- 先创建日程、后开启 SMTP 时，需要更新或重建旧日程，才能补建邮件提醒任务。
