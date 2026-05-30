# VoiceCal 命令流架构

## 核心主线

```text
用户输入
  -> command_task
  -> command_action
  -> pending_confirmation（仅写操作）
  -> calendar_event
  -> reminder_job
```

`execution_log` 只记录可观察步骤并提供给前端时间线展示，不能作为业务状态判断依据。

## 表职责

| 表 | 职责 |
| --- | --- |
| `command_task` | 保存一次用户命令及其最终处理状态 |
| `command_action` | 保存一个可执行业务动作及其 payload |
| `pending_confirmation` | 保存确认 token、过期时间和确认结果 |
| `calendar_event` | 保存真实日程，独立于 Agent 生命周期 |
| `reminder_job` | 保存由真实日程派生出的提醒任务 |
| `execution_log` | 仅用于展示时间线和排查问题 |

## 创建日程示例

```text
"明天下午 3 点安排项目会"
  -> command_task(RUNNING)
  -> RouterAgent(CREATE_EVENT)
  -> 冲突检查
  -> command_action(CREATE_EVENT, WAITING_CONFIRM)
  -> pending_confirmation(PENDING)
  <- 返回 confirmToken

用户确认
  -> pending_confirmation(CONFIRMED)
  -> command_action(EXECUTED)
  -> calendar_event(ACTIVE)
  -> command_task(SUCCESS)
```

当前默认配置为 `voicecal.confirm.auto-execute-safe-writes=true`。创建和修改日程会跳过
`pending_confirmation` 并直接执行，减少交互等待时间。删除日程仍必须生成确认 token，
用户可以点击确认，也可以在前端说“确认”或“取消”。

## 修改和删除

```text
修改指令
  -> RouterAgent(UPDATE_EVENT)
  -> EventResolveService 定位唯一日程
  -> 冲突检查
  -> command_action(UPDATE_EVENT)
  -> 自动执行 calendar_event 更新
  -> 重建 reminder_job

删除指令
  -> RouterAgent(DELETE_EVENT)
  -> EventResolveService 定位唯一日程
  -> command_action(DELETE_EVENT, WAITING_CONFIRM)
  -> pending_confirmation(PENDING)
  <- 用户点击确认或语音说“确认”
  -> calendar_event(DELETED)
  -> reminder_job(CANCELED)
```

## 提醒任务

`reminder_job` 是 `calendar_event` 的派生数据。创建日程时生成，修改日程时取消旧任务并重建，
删除日程时取消未执行任务。定时扫描器会执行到期任务并记录日志。

## 状态归属

- 用户命令处理状态属于 `command_task`。
- 动作执行状态属于 `command_action`。
- 确认状态属于 `pending_confirmation`。
- 日程状态属于 `calendar_event`。
- 时间线展示属于 `execution_log`。

## 数据库迁移

已有数据库需要在启动新版后端之前执行一次
[`sql/V2__clarify_command_flow.sql`](sql/V2__clarify_command_flow.sql)。
迁移脚本不会删除旧表，旧数据会保留，方便回滚和人工核对。
