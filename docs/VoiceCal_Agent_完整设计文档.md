# VoiceCal Agent 设计文档包

## 项目定位

题目一：语音版的日历工具。

本项目首先保证基础功能拿分：用户可以通过语音或文本顺畅完成日程创建、查询、修改、删除、提醒、冲突检测和日历展示。

在基础功能稳定的前提下，系统引入“动态 Skill Manifest + 通用 Tool Agent + Workflow 工作流”作为扩展亮点。系统不为每个工具手写 Java Skill 类，而是通过配置化 Skill 描述能力，再由通用 Agent 解释执行。

## 文档结构

| 文件 | 内容 |
|---|---|
| 01_需求与评分策略.md | 题目拆解、80 分基础功能边界、加分项 |
| 02_总体架构设计.md | 系统分层、核心链路、模块关系 |
| 03_基础日历功能设计.md | 日历 CRUD、冲突检测、空闲推荐、提醒 |
| 04_语音交互与意图解析设计.md | 语音输入、语音播报、LLM JSON 解析、时间标准化 |
| 05_动态Skill与通用Agent设计.md | Skill DSL、注册、选择、ActionPlan、ToolAdapter |
| 06_Workflow确认幂等与失败处理.md | 任务状态机、确认机制、幂等、重试、部分成功 |
| 07_数据库设计.md | 核心表结构、索引建议 |
| 08_API接口设计.md | 后端接口清单、请求响应结构 |
| 09_前端页面与交互设计.md | 页面模块、交互流程、演示页面 |
| 10_开发计划与优先级.md | 三天落地计划、P0/P1/P2 |
| 11_测试用例与演示脚本.md | 测试用例、答辩演示路径 |
| 12_扩展功能设计.md | MCP、HTTP、RAG、会议、短信、邮件扩展 |

## 开发原则

1. 基础日历功能真实实现，不做纯 Demo。
2. 语音只是入口，文本兜底必须存在。
3. 日历创建、修改、删除必须经过确认。
4. 冲突检测必须在创建和修改前执行。
5. LLM 只负责解析和规划，不直接执行真实动作。
6. 新增能力优先写 Skill Manifest，不新增 Java Skill 类。
7. Tool Agent 代码固定，动态的是 Skill 配置和 ActionPlan。
8. 扩展功能可以 Mock，但执行链路必须真实可见。


---

# 01. 需求与评分策略

## 1. 题目要求

题目一：语音版的日历工具。

要求开发一个以语音交互为核心的日历管理工具，帮助用户提高日历管理效率和便捷性。系统需要准确、顺畅地实现通过语音添加、删除、查看事件提醒等能力。

## 2. 核心用户需求

真实用户在日历管理中主要有以下需求：

| 场景 | 用户表达 | 系统要做 |
|---|---|---|
| 创建日程 | 明天下午三点提醒我开组会 | 解析时间、标题，确认后创建 |
| 查询日程 | 今天下午有什么安排 | 查询对应时间段事件 |
| 修改日程 | 把明天下午三点的组会改到四点 | 找到候选事件，确认后修改 |
| 删除日程 | 取消明天上午的论文讨论 | 找到候选事件，强确认后删除 |
| 设置提醒 | 提前十分钟提醒我 | 设置提醒时间 |
| 冲突检测 | 下午三点安排论文讨论 | 检查已有日程，冲突则推荐时间 |
| 模糊补全 | 帮我安排下周组会 | 根据偏好补时间、时长、提醒 |
| 多轮确认 | 确认 / 取消 | 继续或取消 pending action |

## 3. 80 分基础功能范围

要在题目一拿到稳定分，必须优先完成以下闭环：

```text
语音输入 / 文本输入
  ↓
语音识别结果展示
  ↓
LLM 意图解析
  ↓
时间标准化
  ↓
参数缺失检查
  ↓
确认
  ↓
真实日历 CRUD
  ↓
冲突检测
  ↓
提醒设置
  ↓
日历视图刷新
  ↓
语音播报 + 文本反馈
```

## 4. 基础功能评分点

| 能力 | 重要性 | 是否真实实现 |
|---|---:|---|
| 语音输入 | 高 | 是 |
| 语音播报 | 高 | 是 |
| 文本兜底 | 高 | 是 |
| 创建日程 | 极高 | 是 |
| 查询日程 | 极高 | 是 |
| 修改日程 | 高 | 是 |
| 删除日程 | 高 | 是 |
| 提醒设置 | 高 | 是 |
| 冲突检测 | 高 | 是 |
| 多轮确认 | 高 | 是 |
| 日历可视化 | 高 | 是 |
| 执行过程展示 | 中 | 是 |
| 用户习惯补全 | 中 | 简化实现 |
| 会议 / 短信 / 邮件 | 低 | Mock |

## 5. 加分亮点

基础功能稳定后，再展示架构亮点：

```text
固定通用 Agent 框架
+ 动态 Skill Manifest
+ ActionPlan 编排
+ 多 ToolAdapter 执行源
+ Workflow 可追踪
```

答辩重点：

```text
这不是普通语音日历，而是一个以日历为核心的可扩展语音任务编排平台。
基础日历能力真实实现，扩展能力通过 Skill Manifest 配置接入。
```

## 6. 必须避免的扣分点

1. 只做聊天，不真正创建日程。
2. 语音识别错了不能手动修改。
3. 时间解析不稳定。
4. 删除日程不确认。
5. 冲突检测只是口头说，没有实际查询。
6. 页面没有日历视图。
7. 外部短信、邮件、会议投入过多，基础日历反而没做好。
8. 动态 Skill 讲得很大，但演示不出来。


---

# 02. 总体架构设计

## 1. 系统目标

VoiceCal Agent 是一个以语音交互为入口、以真实日历管理为核心、以动态 Skill 注册和通用 Tool Agent 为扩展方式的智能日程编排平台。

核心目标：

```text
用户说一句话
  ↓
系统识别语音
  ↓
解析意图和时间
  ↓
生成待执行计划
  ↓
必要时询问确认
  ↓
执行日历 / 提醒 / Mock 工具
  ↓
记录完整工作流
  ↓
文本 + 语音反馈
```

## 2. 架构原则

1. 基础日历能力必须真实落地。
2. 语音是主要入口，文本是兜底入口。
3. Agent 不绑定具体业务 Skill 类。
4. Skill 是配置，不是 Java 类。
5. ToolAdapter 是少量固定代码。
6. ActionPlan 是动态生成的执行计划。
7. Workflow 记录每一步，方便展示和调试。
8. 高风险操作必须确认。

## 3. 总体分层

```text
+--------------------------------------------------+
| 前端交互层                                        |
| 语音输入 / 文本输入 / 日历视图 / 执行时间线        |
+--------------------------------------------------+
                      |
                      v
+--------------------------------------------------+
| Agent 接入层                                      |
| VoiceCommandController / AgentController          |
+--------------------------------------------------+
                      |
                      v
+--------------------------------------------------+
| 任务理解层                                        |
| IntentParser / SlotExtractor / TimeNormalizer     |
+--------------------------------------------------+
                      |
                      v
+--------------------------------------------------+
| 规划层                                            |
| SkillSelector / ActionPlanBuilder                 |
+--------------------------------------------------+
                      |
                      v
+--------------------------------------------------+
| 通用 Tool Agent 层                                |
| GenericToolAgent / SkillRegistry / Executor       |
+--------------------------------------------------+
                      |
                      v
+--------------------------------------------------+
| ToolAdapter 层                                    |
| Native / Mock / Scheduler / HTTP / MCP / DB        |
+--------------------------------------------------+
                      |
                      v
+--------------------------------------------------+
| 业务能力层                                        |
| CalendarService / ConflictService / Reminder       |
+--------------------------------------------------+
                      |
                      v
+--------------------------------------------------+
| Workflow & Audit                                  |
| agent_task / agent_step / agent_event / log        |
+--------------------------------------------------+
```

## 4. 核心请求链路

### 4.1 创建日程

```text
用户：明天下午三点提醒我开组会
  ↓
识别文本
  ↓
LLM 解析：
intent = CREATE_EVENT
title = 组会
start_time = 明天下午3点
  ↓
TimeNormalizer 转换绝对时间
  ↓
补默认时长 60 分钟
  ↓
冲突检测
  ↓
生成确认话术
  ↓
用户确认
  ↓
创建日程
  ↓
写 Workflow
  ↓
返回结果并语音播报
```

### 4.2 修改日程

```text
用户：把明天下午三点的组会改到四点
  ↓
解析修改意图
  ↓
查询候选日程
  ↓
如果唯一匹配，生成修改计划
  ↓
检查新时间冲突
  ↓
确认
  ↓
更新日程
```

### 4.3 删除日程

```text
用户：取消明天上午的论文讨论
  ↓
解析删除意图
  ↓
查询候选日程
  ↓
如果多个，让用户选择
  ↓
强确认
  ↓
软删除
```

## 5. 模块清单

```text
com.voicecal
├── controller
├── voice
├── understanding
├── planning
├── tool
├── tool.adapter
├── workflow
├── domain.calendar
├── domain.reminder
├── domain.contact
├── domain.preference
├── mock
├── scheduler
└── audit
```

## 6. 最小可运行闭环

比赛版必须先做这个：

```text
/api/agent/execute
  ↓
IntentParser
  ↓
TimeNormalizer
  ↓
ActionPlanBuilder
  ↓
pending_action
  ↓
/api/agent/confirm
  ↓
GenericToolAgent
  ↓
NativeToolAdapter
  ↓
CalendarService
  ↓
WorkflowRecorder
```


---

# 03. 基础日历功能设计

## 1. 基础日历能力

| 功能 | 说明 |
|---|---|
| 创建日程 | 根据标题、时间、地点、描述、提醒创建事件 |
| 查询日程 | 查询今天、明天、本周、指定日期范围 |
| 修改日程 | 修改标题、时间、地点、描述、提醒 |
| 删除日程 | 软删除事件 |
| 冲突检测 | 判断目标时间段是否和已有日程重叠 |
| 空闲推荐 | 冲突时推荐最近可用时间 |
| 提醒管理 | 提前 N 分钟提醒 |
| 日历展示 | 月视图、周视图、列表视图 |

## 2. 日程实体

```java
public class CalendarEvent {
    private Long id;
    private Long userId;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private String description;
    private String meetingUrl;
    private Integer reminderMinutes;
    private String source;
    private String status;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

## 3. CalendarService 接口

```java
public interface CalendarService {

    CalendarEventVO createEvent(CreateEventRequest request);

    List<CalendarEventVO> queryEvents(QueryEventRequest request);

    CalendarEventVO updateEvent(Long eventId, UpdateEventRequest request);

    Boolean deleteEvent(Long eventId);

    List<CalendarEventVO> findCandidateEvents(EventResolveRequest request);
}
```

## 4. 创建日程流程

```text
CreateEventRequest
  ↓
校验 title / startTime / endTime
  ↓
endTime 必须大于 startTime
  ↓
事务开始
  ↓
再次执行冲突检测
  ↓
如果冲突，返回 ConflictResult
  ↓
生成 idempotencyKey
  ↓
检查是否重复创建
  ↓
插入 calendar_event
  ↓
如果有 reminderMinutes，创建 scheduled_job
  ↓
事务提交
```

## 5. 查询日程流程

支持：

```text
今天
明天
本周
下周
指定日期
指定时间段
关键词搜索
```

查询条件：

```sql
WHERE user_id = ?
  AND status != 'DELETED'
  AND start_time < #{rangeEnd}
  AND end_time > #{rangeStart}
ORDER BY start_time ASC
```

## 6. 修改日程流程

修改前不能直接更新，需要先定位事件。

```text
用户输入
  ↓
解析 old_time / title / new_time
  ↓
findCandidateEvents
  ↓
0 个：提示没有找到
1 个：进入修改确认
多个：让用户选择
  ↓
检查新时间冲突
  ↓
确认
  ↓
更新事件
```

## 7. 删除日程流程

删除必须强确认。

```text
用户输入
  ↓
解析删除条件
  ↓
findCandidateEvents
  ↓
0 个：提示没有找到
1 个：强确认
多个：让用户选择
  ↓
status = DELETED
```

## 8. 冲突检测

### 8.1 冲突条件

```text
newStart < existEnd && newEnd > existStart
```

### 8.2 SQL

```sql
SELECT *
FROM calendar_event
WHERE user_id = #{userId}
  AND status != 'DELETED'
  AND start_time < #{newEnd}
  AND end_time > #{newStart}
ORDER BY start_time ASC;
```

### 8.3 返回结构

```java
public class ConflictResult {
    private Boolean hasConflict;
    private List<CalendarEventVO> conflictEvents;
    private List<FreeSlotVO> suggestedSlots;
}
```

## 9. 空闲时间推荐

比赛版规则：

```text
1. 输入目标日期、目标时长
2. 查询当天所有日程
3. 按 start_time 排序
4. 找到两个事件之间的空档
5. 返回最近 3 个可用时间段
```

默认工作时间：

```text
09:00 - 22:00
```

## 10. 提醒设计

提醒分两类：

| 类型 | 比赛版实现 |
|---|---|
| 本地站内提醒 | scheduled_job + 前端轮询 |
| 语音提醒 | 到点弹窗 + TTS 播报 |

外部短信、邮件属于扩展能力，比赛版可 Mock。

## 11. 必须注意

1. 创建和修改都要做冲突检测。
2. 删除用软删除。
3. 修改/删除前必须先定位事件。
4. 创建日程要做幂等。
5. 时间字段统一处理时区。
6. 查询不能返回已删除事件。
7. 冲突检测要加数据库索引。


---

# 04. 语音交互与意图解析设计

## 1. 语音交互目标

系统以语音交互为核心，但必须提供文本兜底。原因：

1. 浏览器语音识别可能不稳定。
2. 用户需要修改识别错误文本。
3. 答辩演示不能完全依赖麦克风环境。

## 2. 前端语音能力

比赛版推荐使用 Web Speech API。

功能：

```text
1. 点击开始录音
2. 浏览器识别语音
3. 将识别文本展示到输入框
4. 用户可手动修改
5. 点击发送
6. 后端返回 replyText / speakText
7. 前端调用语音播报
```

## 3. 请求结构

```json
{
  "userId": 1,
  "sessionId": "session_001",
  "inputType": "VOICE",
  "text": "明天下午三点提醒我开组会",
  "timezone": "Asia/Shanghai",
  "currentTime": "2026-05-29 09:00:00"
}
```

## 4. 支持意图

```text
CREATE_EVENT
QUERY_EVENT
UPDATE_EVENT
DELETE_EVENT
CREATE_MEETING_EVENT
SEND_SMS
SCHEDULE_EMAIL
SUMMARY_SCHEDULE
CONFIRM
CANCEL
UNKNOWN
```

基础功能必须优先保证：

```text
CREATE_EVENT
QUERY_EVENT
UPDATE_EVENT
DELETE_EVENT
CONFIRM
CANCEL
```

## 5. LLM 输出 JSON

```json
{
  "intent": "CREATE_EVENT",
  "slots": {
    "title": "组会",
    "start_time": "2026-06-01 15:00:00",
    "end_time": "2026-06-01 16:00:00",
    "location": "",
    "description": "",
    "reminder_minutes": 10
  },
  "missing_fields": [],
  "confidence": 0.92
}
```

## 6. Prompt 模板

```text
你是语音日历工具的意图解析模块。
请将用户输入解析为严格 JSON，不要输出解释。

当前时间：{current_time}
用户时区：{timezone}

可用 intent：
- CREATE_EVENT
- QUERY_EVENT
- UPDATE_EVENT
- DELETE_EVENT
- CREATE_MEETING_EVENT
- SEND_SMS
- SCHEDULE_EMAIL
- SUMMARY_SCHEDULE
- CONFIRM
- CANCEL
- UNKNOWN

要求：
1. 只输出 JSON
2. 时间必须转换为 yyyy-MM-dd HH:mm:ss
3. 如果只有开始时间，没有结束时间，end_time 留空
4. 不确定字段放入 missing_fields
5. 不要编造联系人电话、邮箱
6. confidence 范围 0 到 1

用户输入：
{text}
```

## 7. JSON 解析保护

LLM 输出不能直接相信，必须做保护：

```text
原始输出
  ↓
提取 JSON 块
  ↓
Jackson 解析
  ↓
intent 枚举校验
  ↓
required 字段校验
  ↓
时间格式校验
  ↓
confidence 阈值判断
  ↓
生成 AgentCommand
```

如果解析失败：

```json
{
  "success": false,
  "needClarify": true,
  "replyText": "我没有理解清楚，你可以换一种说法吗？",
  "speakText": "我没有理解清楚，你可以换一种说法吗？"
}
```

## 8. 时间标准化规则

### 8.1 必须传入当前时间

所有相对时间必须基于当前时间解析。

```text
今天
明天
后天
下周一
本周五
会议前一天
提前半小时
```

### 8.2 默认值

| 字段 | 默认值 |
|---|---|
| 日程时长 | 60 分钟 |
| 提醒时间 | 提前 10 分钟 |
| 地点 | 空 |
| 描述 | 空 |
| 标题 | 从用户句子提取，失败则追问 |

### 8.3 缺失处理

如果缺少必要字段：

```json
{
  "success": false,
  "needClarify": true,
  "missingFields": ["start_time"],
  "replyText": "你想安排在什么时间？",
  "speakText": "你想安排在什么时间？"
}
```

## 9. 多轮上下文

每个 session 保存最近一个未完成任务：

```text
WAITING_CLARIFY
WAITING_CONFIRM
```

用户说“确认”时：

```text
查找 session 最近 pending_action
  ↓
存在则执行
  ↓
不存在则提示没有待确认操作
```

## 10. 语音交互体验要求

1. 识别文本必须显示出来。
2. 用户可以手动修改识别结果。
3. 系统回复同时展示文本和播报语音。
4. 确认、取消按钮和语音确认都支持。
5. 操作成功后刷新日历视图。
6. 失败时不要只报错，要给用户下一步建议。


---

# 05. 动态 Skill 与通用 Agent 设计

## 1. 核心思想

本系统不为每个工具手写 Java Skill 类，而是采用：

```text
固定通用 Agent 框架
+ 动态 Skill Manifest
+ ActionPlan 编排
+ ToolAdapter 执行
```

也就是说：

```text
Skill 是数据
Agent 是解释器
Adapter 是执行器
ActionPlan 是运行时计划
```

## 2. 什么是动态 Skill

动态 Skill 不是动态生成 Java 代码，而是动态注册能力描述。

一个 Skill 描述：

```text
1. 它能做什么
2. 什么时候可能被选中
3. 需要哪些输入参数
4. 是否需要确认
5. 调用哪个执行源
6. 失败后如何处理
7. 输出如何给后续步骤使用
```

## 3. Skill Manifest 示例

```yaml
skill_id: calendar.create
name: 创建日程
description: 在用户日历中创建一个新的日程事件
category: calendar
enabled: true

trigger_examples:
  - 明天下午三点提醒我开会
  - 下周一上午十点安排组会
  - 今晚八点提醒我写论文

input_schema:
  type: object
  required:
    - title
    - start_time
    - end_time
  properties:
    title:
      type: string
    start_time:
      type: string
    end_time:
      type: string
    location:
      type: string
    description:
      type: string
    reminder_minutes:
      type: integer

executor:
  type: native
  tool_key: calendar.create
  arguments_mapping:
    title: $.title
    startTime: $.start_time
    endTime: $.end_time
    location: $.location
    description: $.description
    reminderMinutes: $.reminder_minutes

confirm_policy:
  required: true
  level: MEDIUM
  message_template: 我将创建「${title}」，时间是 ${start_time} 到 ${end_time}，是否确认？

failure_policy:
  on_failure: stop
  retry: 0
  fallback: 创建日程失败，请重新确认时间和标题。
```

## 4. 为什么不用 bean + method 开放反射

不建议在比赛版开放：

```yaml
bean: calendarService
method: createEvent
```

更建议使用：

```yaml
tool_key: calendar.create
```

原因：

```text
1. 更安全
2. 更好调试
3. 参数转换更简单
4. 不会被配置调用任意方法
5. 更适合答辩解释
```

后端维护一个 NativeToolRegistry：

```java
@Component
public class NativeToolRegistry {

    private final Map<String, InvokableTool> tools = new HashMap<>();

    @PostConstruct
    public void init() {
        tools.put("calendar.create", calendarCreateTool);
        tools.put("calendar.query", calendarQueryTool);
        tools.put("calendar.update", calendarUpdateTool);
        tools.put("calendar.delete", calendarDeleteTool);
        tools.put("calendar.conflict_check", conflictCheckTool);
    }

    public InvokableTool get(String toolKey) {
        return tools.get(toolKey);
    }
}
```

## 5. Skill 生命周期

```text
resources/skills/*.yaml
  ↓
SkillLoader 加载
  ↓
SkillValidator 校验
  ↓
SkillRegistry 注册
  ↓
SkillSelector 召回
  ↓
ActionPlanBuilder 编排
  ↓
GenericToolAgent 执行
```

## 6. SkillValidator 必须校验

```text
1. skill_id 不能为空且唯一
2. name / description 不能为空
3. input_schema 合法
4. executor.type 合法
5. native 的 tool_key 必须在注册表中
6. mock 的 mock_name 必须存在
7. scheduler 的 job_type 必须受支持
8. confirm_policy 必须存在
9. 高风险操作必须确认
10. failure_policy 必须存在
```

## 7. ToolAdapter 类型

| 类型 | 说明 | 比赛版 |
|---|---|---|
| native | 调用本地业务工具 | 必做 |
| mock | 调用 Mock Provider | 必做 |
| scheduler | 创建定时任务 | 尽量做 |
| http | 调用外部 HTTP API | 设计即可 |
| mcp | 调用 MCP Tool | 设计即可 |
| database | 受控数据库查询 | 可选 |

## 8. 通用 ToolAgent

```java
public class GenericToolAgent {

    public ToolExecutionResult execute(ActionPlan plan) {
        for (ActionStep step : plan.getSteps()) {
            SkillDefinition skill = skillRegistry.get(step.getSkillId());
            validateArguments(skill, step.getArguments());
            ToolAdapter adapter = adapterFactory.get(skill.getExecutor().getType());
            StepExecutionResult result = adapter.execute(skill, step.getArguments());
            workflowRecorder.recordStep(step, result);
            context.putStepOutput(step.getOrder(), result.getData());
            if (!result.isSuccess()) {
                handleFailure(step, result);
            }
        }
        return buildFinalResult();
    }
}
```

## 9. ActionPlan

```json
{
  "task_id": "task_001",
  "goal": "创建线上会议并写入日历",
  "need_confirm": true,
  "steps": [
    {
      "order": 1,
      "skill_id": "calendar.conflict_check",
      "arguments": {
        "start_time": "2026-06-01 15:00:00",
        "end_time": "2026-06-01 16:00:00"
      },
      "on_failure": "ask_user"
    },
    {
      "order": 2,
      "skill_id": "meeting.create",
      "arguments": {
        "title": "组会",
        "start_time": "2026-06-01 15:00:00",
        "end_time": "2026-06-01 16:00:00"
      },
      "output_alias": "meeting",
      "on_failure": "stop"
    },
    {
      "order": 3,
      "skill_id": "calendar.create",
      "arguments": {
        "title": "组会",
        "start_time": "2026-06-01 15:00:00",
        "end_time": "2026-06-01 16:00:00",
        "description": "线上会议链接：${meeting.meeting_url}"
      },
      "on_failure": "stop"
    }
  ]
}
```

## 10. 步骤变量传递

必须支持：

```text
前一步输出
  ↓
存入 ExecutionContext
  ↓
后续步骤通过 ${alias.field} 引用
```

示例：

```text
meeting.create 输出 meeting_url
calendar.create 的 description 使用 ${meeting.meeting_url}
```

## 11. Skill 选择策略

比赛版三层策略：

```text
1. trigger_examples 关键词召回
2. description 相似度召回
3. LLM 从候选 Skill 中选择
```

注意：

```text
LLM 只能从候选 Skill 中选，不能编造不存在的 skill_id。
```

## 12. 基础 Skill 清单

必须实现：

```text
calendar.create
calendar.query
calendar.update
calendar.delete
calendar.conflict_check
calendar.resolve_event
```

尽量实现：

```text
meeting.create
sms.send
email.schedule
contact.query
schedule.summary
```

## 13. 关键边界

1. 动态 Skill 不是动态生成 Java 类。
2. 新能力必须绑定已有 executor。
3. LLM 不允许生成任意 executor。
4. 高风险 Skill 必须确认。
5. Manifest 只描述能力，不直接绕过权限。
6. 比赛版 pre_actions / post_actions 不自动执行，统一由 ActionPlan 显式编排。


---

# 06. Workflow、确认、幂等与失败处理

## 1. 为什么需要 Workflow

语音 Agent 容易出现用户不信任的问题。系统需要展示：

```text
我听到了什么
我理解成什么
我准备做什么
我执行到哪一步
哪一步成功
哪一步失败
```

所以必须记录 task、step、event、tool_call_record。

## 2. AgentTask 状态机

```text
RECEIVED
  ↓
UNDERSTANDING
  ↓
PLANNING
  ↓
WAITING_CONFIRM
  ↓
EXECUTING
  ↓
DONE / PARTIAL_SUCCESS / FAILED

WAITING_CONFIRM → CANCELLED
```

## 3. 状态说明

| 状态 | 含义 |
|---|---|
| RECEIVED | 收到用户输入 |
| UNDERSTANDING | 正在解析意图 |
| PLANNING | 正在生成计划 |
| WAITING_CONFIRM | 等待用户确认 |
| EXECUTING | 正在执行工具 |
| DONE | 全部成功 |
| PARTIAL_SUCCESS | 部分成功 |
| FAILED | 失败 |
| CANCELLED | 用户取消 |

## 4. agent_step 记录内容

```text
step_order
skill_id
executor_type
input_json
output_json
status
latency_ms
retry_count
error_message
```

## 5. agent_event 类型

```text
TASK_RECEIVED
INTENT_PARSED
PLAN_BUILT
WAITING_CONFIRM
TASK_CONFIRMED
TASK_CANCELLED
STEP_STARTED
STEP_COMPLETED
STEP_FAILED
TASK_DONE
TASK_FAILED
```

## 6. 确认策略

### 6.1 确认等级

| 等级 | 场景 | 策略 |
|---|---|---|
| LOW | 查询日程、总结日程 | 不确认 |
| MEDIUM | 创建日程、修改日程、创建会议 | 需要确认 |
| HIGH | 删除日程、发送短信、发送邮件 | 强确认 |

### 6.2 PendingAction 流程

```text
ActionPlan 生成
  ↓
判断需要确认
  ↓
保存 pending_action
  ↓
返回 confirmToken
  ↓
用户说确认 / 点击确认
  ↓
校验 confirmToken
  ↓
执行 ActionPlan
```

### 6.3 confirmToken 规则

```text
1. 只能使用一次
2. 默认 5 分钟过期
3. 绑定 userId
4. 绑定 sessionId
5. 绑定 taskId
6. 重复确认直接返回已处理
```

## 7. Session 级确认规则

比赛版推荐：

```text
一个 session 同一时间只允许存在一个 WAITING_CONFIRM 任务。
```

如果新任务进来：

```text
当前有待确认任务
  ↓
提示用户先确认或取消
```

避免用户说“确认”时不知道确认哪个任务。

## 8. 幂等设计

### 8.1 场景

```text
1. 用户重复点击确认
2. 前端重复提交
3. 网络超时重试
4. 定时任务重复扫描
5. ToolAgent 步骤重试
```

### 8.2 幂等键

创建日程：

```text
userId + title + startTime + endTime
```

发送短信：

```text
userId + receiver + content + sendTime
```

定时任务：

```text
userId + jobType + runAt + payloadHash
```

确认操作：

```text
confirmToken
```

## 9. 失败策略

| 策略 | 含义 |
|---|---|
| stop | 当前步骤失败，停止整个任务 |
| continue | 当前步骤失败，继续后续步骤 |
| retry | 重试当前步骤 |
| ask_user | 询问用户下一步 |
| rollback | 执行补偿动作，比赛版可不做 |

## 10. 部分成功

示例：

```text
会议创建成功
日历写入成功
短信提醒失败
```

返回：

```json
{
  "success": false,
  "status": "PARTIAL_SUCCESS",
  "replyText": "会议和日程已创建，但短信提醒失败。",
  "data": {
    "successSteps": ["meeting.create", "calendar.create"],
    "failedSteps": ["sms.send"]
  }
}
```

## 11. 重试规则

```text
1. 日历创建默认不自动重试，避免重复创建
2. Mock / HTTP / Scheduler 可以重试
3. retry 次数由 failure_policy 控制
4. 每次重试都记录 retry_count
```

## 12. 事务边界

创建日程时：

```text
checkConflict + insert calendar_event + create reminder job
```

应放在同一个事务中。

修改日程时：

```text
resolveEvent + checkConflict + update
```

应放在同一个事务中。

删除日程时：

```text
resolveEvent + softDelete
```

应放在同一个事务中。


---

# 07. 数据库设计

## 1. calendar_event

```sql
CREATE TABLE calendar_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    location VARCHAR(255),
    description TEXT,
    meeting_url VARCHAR(500),
    reminder_minutes INT,
    source VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    idempotency_key VARCHAR(128),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE INDEX idx_calendar_user_time
ON calendar_event(user_id, start_time, end_time, status);

CREATE UNIQUE INDEX uk_calendar_idempotency
ON calendar_event(idempotency_key);
```

## 2. agent_task

```sql
CREATE TABLE agent_task (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100),
    input_text TEXT,
    input_type VARCHAR(50),
    main_intent VARCHAR(100),
    status VARCHAR(50),
    final_output TEXT,
    error_message TEXT,
    trace_id VARCHAR(100),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE INDEX idx_agent_task_user_session
ON agent_task(user_id, session_id, status);
```

## 3. agent_step

```sql
CREATE TABLE agent_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    step_order INT NOT NULL,
    skill_id VARCHAR(100),
    executor_type VARCHAR(50),
    input_json TEXT,
    output_json TEXT,
    status VARCHAR(50),
    latency_ms BIGINT,
    retry_count INT DEFAULT 0,
    error_message TEXT,
    created_at DATETIME NOT NULL
);

CREATE INDEX idx_agent_step_task
ON agent_step(task_id, step_order);
```

## 4. agent_event

```sql
CREATE TABLE agent_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100),
    from_status VARCHAR(50),
    to_status VARCHAR(50),
    message TEXT,
    created_at DATETIME NOT NULL
);

CREATE INDEX idx_agent_event_task
ON agent_event(task_id, created_at);
```

## 5. pending_action

```sql
CREATE TABLE pending_action (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100),
    confirm_token VARCHAR(128) NOT NULL,
    action_plan_json TEXT NOT NULL,
    expired_at DATETIME NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE UNIQUE INDEX uk_pending_confirm_token
ON pending_action(confirm_token);

CREATE INDEX idx_pending_user_session
ON pending_action(user_id, session_id, used, expired_at);
```

## 6. tool_call_record

```sql
CREATE TABLE tool_call_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64),
    step_id BIGINT,
    skill_id VARCHAR(100),
    executor_type VARCHAR(50),
    input_json TEXT,
    output_json TEXT,
    success BOOLEAN,
    latency_ms BIGINT,
    error_message TEXT,
    created_at DATETIME NOT NULL
);

CREATE INDEX idx_tool_call_task
ON tool_call_record(task_id, created_at);
```

## 7. skill_definition

```sql
CREATE TABLE skill_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    skill_id VARCHAR(100) NOT NULL,
    name VARCHAR(100),
    category VARCHAR(100),
    manifest_content TEXT,
    version VARCHAR(50),
    enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE UNIQUE INDEX uk_skill_id
ON skill_definition(skill_id);
```

## 8. scheduled_job

```sql
CREATE TABLE scheduled_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64),
    user_id BIGINT NOT NULL,
    job_type VARCHAR(100),
    run_at DATETIME NOT NULL,
    payload_json TEXT,
    status VARCHAR(50),
    retry_count INT DEFAULT 0,
    idempotency_key VARCHAR(128),
    last_error TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE INDEX idx_scheduled_job_scan
ON scheduled_job(status, run_at);

CREATE UNIQUE INDEX uk_scheduled_job_idempotency
ON scheduled_job(idempotency_key);
```

## 9. user_preference

```sql
CREATE TABLE user_preference (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    preference_type VARCHAR(100),
    keyword VARCHAR(100),
    content TEXT,
    confidence DOUBLE,
    source VARCHAR(100),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE INDEX idx_preference_user_keyword
ON user_preference(user_id, keyword);
```

## 10. contact

```sql
CREATE TABLE contact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255),
    group_name VARCHAR(100),
    relation VARCHAR(100),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE INDEX idx_contact_user_name
ON contact(user_id, name);

CREATE INDEX idx_contact_user_group
ON contact(user_id, group_name);
```

## 11. operation_log

```sql
CREATE TABLE operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    task_id VARCHAR(64),
    operation_type VARCHAR(100),
    request_text TEXT,
    response_text TEXT,
    success BOOLEAN,
    trace_id VARCHAR(100),
    created_at DATETIME NOT NULL
);
```


---

# 08. API 接口设计

## 1. Agent 接口

### 1.1 执行用户指令

```http
POST /api/agent/execute
```

请求：

```json
{
  "userId": 1,
  "sessionId": "session_001",
  "inputType": "VOICE",
  "text": "明天下午三点提醒我开组会",
  "timezone": "Asia/Shanghai",
  "currentTime": "2026-05-29 09:00:00"
}
```

响应：

```json
{
  "success": true,
  "taskId": "task_001",
  "needConfirm": true,
  "confirmToken": "ct_001",
  "replyText": "我将创建「组会」，时间是明天下午3点到4点，是否确认？",
  "speakText": "我将创建组会，时间是明天下午三点到四点，是否确认？",
  "data": {}
}
```

### 1.2 确认执行

```http
POST /api/agent/confirm
```

请求：

```json
{
  "userId": 1,
  "sessionId": "session_001",
  "confirmToken": "ct_001"
}
```

响应：

```json
{
  "success": true,
  "taskId": "task_001",
  "replyText": "已创建日程「组会」。",
  "speakText": "已创建日程组会。",
  "data": {
    "eventId": 1001
  }
}
```

### 1.3 取消

```http
POST /api/agent/cancel
```

请求：

```json
{
  "userId": 1,
  "sessionId": "session_001",
  "confirmToken": "ct_001"
}
```

## 2. 日历接口

### 2.1 创建日程

```http
POST /api/calendar/events
```

请求：

```json
{
  "userId": 1,
  "title": "组会",
  "startTime": "2026-06-01 15:00:00",
  "endTime": "2026-06-01 16:00:00",
  "location": "",
  "description": "",
  "reminderMinutes": 10
}
```

### 2.2 查询日程

```http
GET /api/calendar/events?userId=1&startTime=2026-06-01 00:00:00&endTime=2026-06-02 00:00:00
```

### 2.3 修改日程

```http
PUT /api/calendar/events/{eventId}
```

### 2.4 删除日程

```http
DELETE /api/calendar/events/{eventId}
```

### 2.5 冲突检测

```http
POST /api/calendar/conflict/check
```

请求：

```json
{
  "userId": 1,
  "startTime": "2026-06-01 15:00:00",
  "endTime": "2026-06-01 16:00:00",
  "excludeEventId": null
}
```

响应：

```json
{
  "hasConflict": true,
  "conflictEvents": [
    {
      "id": 1001,
      "title": "组会",
      "startTime": "2026-06-01 15:00:00",
      "endTime": "2026-06-01 16:00:00"
    }
  ],
  "suggestedSlots": [
    {
      "startTime": "2026-06-01 16:00:00",
      "endTime": "2026-06-01 17:00:00"
    }
  ]
}
```

### 2.6 空闲推荐

```http
POST /api/calendar/free-slots
```

## 3. Workflow 接口

### 3.1 查询任务详情

```http
GET /api/agent/tasks/{taskId}
```

### 3.2 查询任务步骤

```http
GET /api/agent/tasks/{taskId}/steps
```

响应：

```json
[
  {
    "stepOrder": 1,
    "skillId": "calendar.conflict_check",
    "status": "SUCCESS",
    "latencyMs": 30
  },
  {
    "stepOrder": 2,
    "skillId": "calendar.create",
    "status": "SUCCESS",
    "latencyMs": 50
  }
]
```

## 4. Skill 接口

### 4.1 查询 Skill

```http
GET /api/skills
```

### 4.2 重新加载 Skill

```http
POST /api/skills/reload
```

### 4.3 查询 Skill 详情

```http
GET /api/skills/{skillId}
```

## 5. 定时任务接口

```http
GET /api/scheduled-jobs
POST /api/scheduled-jobs/{jobId}/cancel
```

## 6. 统一响应结构

```java
public class ApiResponse<T> {
    private Boolean success;
    private String code;
    private String message;
    private T data;
}
```

Agent 响应：

```java
public class AgentResponse {
    private Boolean success;
    private String taskId;
    private Boolean needConfirm;
    private String confirmToken;
    private Boolean needClarify;
    private List<String> missingFields;
    private String replyText;
    private String speakText;
    private Object data;
}
```


---

# 09. 前端页面与交互设计

## 1. 页面目标

前端必须让评委直观看到：

```text
用户说了什么
系统理解了什么
系统准备做什么
用户是否确认
系统执行了哪些步骤
日历是否真的变化
```

## 2. 页面布局

```text
+------------------------------------------------------+
| 顶部：VoiceCal Agent 标题 + 当前时间                  |
+------------------------+-----------------------------+
| 左侧：语音助手面板       | 右侧：日历视图                |
| - 开始语音              | - 月视图                     |
| - 识别文本输入框         | - 周视图                     |
| - 发送按钮              | - 日程详情                   |
| - 系统回复              |                             |
| - 确认 / 取消按钮        |                             |
+------------------------+-----------------------------+
| 底部：执行时间线 / Workflow Steps                     |
+------------------------------------------------------+
```

## 3. 页面模块

### 3.1 语音控制区

功能：

```text
1. 开始录音
2. 停止录音
3. 显示识别文本
4. 用户可手动编辑
5. 发送到后端
```

### 3.2 Agent 对话区

展示：

```text
用户输入
系统回复
确认按钮
取消按钮
错误提示
```

### 3.3 日历视图区

使用 FullCalendar。

支持：

```text
月视图
周视图
日视图
日程点击查看详情
创建/修改/删除后自动刷新
```

### 3.4 执行时间线区

示例：

```text
✓ 收到语音指令
✓ 识别意图：创建日程
✓ 标准化时间：2026-06-01 15:00
✓ 检查冲突：无冲突
✓ 等待用户确认
✓ 创建日程：成功
✓ 创建提醒：成功
```

### 3.5 Skill 面板

展示当前已注册 Skill：

```text
calendar.create      native      enabled
calendar.query       native      enabled
calendar.update      native      enabled
calendar.delete      native      enabled
calendar.conflict    native      enabled
meeting.create       mock        enabled
sms.send             mock        enabled
email.schedule       scheduler   enabled
```

## 4. 前端状态

```text
IDLE
LISTENING
RECOGNIZED
WAITING_RESPONSE
WAITING_CONFIRM
EXECUTING
DONE
FAILED
```

## 5. 关键交互流程

### 5.1 创建日程

```text
点击开始语音
  ↓
说：明天下午三点提醒我开组会
  ↓
识别文本进入输入框
  ↓
用户点击发送
  ↓
系统返回确认话术
  ↓
点击确认
  ↓
日历新增事件
  ↓
时间线展示执行步骤
  ↓
语音播报结果
```

### 5.2 冲突检测

```text
已有：明天下午三点组会
用户：明天下午三点安排论文讨论
  ↓
系统检测冲突
  ↓
提示：该时间已有组会，建议改到四点
  ↓
用户确认
  ↓
创建四点日程
```

### 5.3 修改日程

```text
用户：把明天下午三点的组会改到四点
  ↓
系统找到候选事件
  ↓
确认修改
  ↓
更新日历
```

### 5.4 删除日程

```text
用户：取消明天下午的组会
  ↓
系统找到候选事件
  ↓
强确认删除
  ↓
日历移除事件
```

## 6. 技术选择

```text
Vue 3 / React
FullCalendar
Web Speech API
SpeechSynthesis API
Axios
Element Plus / Ant Design
```

## 7. 演示要求

1. 输入框必须允许修改语音识别结果。
2. 每次操作后日历必须刷新。
3. Workflow 时间线必须显示。
4. 确认/取消按钮必须明显。
5. 出错时要有可读提示。


---

# 10. 开发计划与优先级

## 1. 总原则

先保证题目一基础功能拿分，再做扩展亮点。

开发顺序：

```text
基础日历闭环
→ 语音交互闭环
→ 确认和冲突检测
→ Workflow 展示
→ 动态 Skill
→ Mock 扩展功能
```

不要一开始做 MCP、RAG、HTTP 外部接口。

## 2. P0：必须完成

```text
1. 语音输入和语音播报
2. 文本兜底输入
3. 日历 CRUD
4. 冲突检测
5. 空闲时间推荐
6. 提醒设置
7. LLM JSON 意图解析
8. TimeNormalizer
9. Agent execute / confirm / cancel
10. pending_action 确认机制
11. Workflow task / step / event
12. 前端日历视图
13. 前端执行时间线
```

## 3. P1：尽量完成

```text
1. Skill Manifest 加载
2. SkillRegistry
3. SkillSelector
4. ActionPlanBuilder
5. GenericToolAgent
6. NativeToolAdapter
7. MockToolAdapter
8. meeting.create Mock
9. sms.send Mock
10. contact.query
11. user_preference 简化补全
```

## 4. P2：有时间再做

```text
1. SchedulerToolAdapter
2. email.schedule
3. schedule.summary
4. HTTPToolAdapter
5. MCPToolAdapter
6. Vector RAG
7. 多用户复杂权限
8. 分布式定时任务
```

## 5. 三天落地计划

### Day 1：基础日历闭环

必须完成：

```text
1. calendar_event 表
2. CalendarService CRUD
3. ConflictCheckService
4. FreeSlotService
5. CalendarController
6. 前端日历视图
7. 文本输入创建/查询日程
8. 简单语音输入和播报
```

演示目标：

```text
用户说：明天下午三点提醒我开组会
系统确认后创建日程，日历刷新。
```

### Day 2：Agent 与确认闭环

必须完成：

```text
1. AgentController execute / confirm / cancel
2. IntentParser
3. TimeNormalizer
4. DefaultValueResolver
5. MissingFieldChecker
6. pending_action
7. Workflow task / step / event
8. 修改和删除前事件定位
9. 冲突后推荐时间
```

演示目标：

```text
创建、查询、修改、删除、冲突检测都能通过语音完成。
```

### Day 3：动态 Skill 与包装

必须完成：

```text
1. Skill Manifest 格式
2. SkillLoader
3. SkillRegistry
4. GenericToolAgent
5. NativeToolAdapter
6. MockToolAdapter
7. meeting.create Mock
8. sms.send Mock
9. 前端执行时间线优化
10. README 和答辩脚本
```

演示目标：

```text
用户：下午三点帮我创建线上会议，并短信提醒张三。
系统动态选择 meeting.create、calendar.create、sms.send，生成 ActionPlan 并执行。
```

## 6. 推荐开发顺序

```text
1. 数据库表
2. 日历 CRUD
3. 冲突检测
4. 前端日历展示
5. LLM JSON 解析
6. Agent execute
7. confirmToken
8. Workflow 记录
9. 修改/删除事件定位
10. Skill Manifest
11. GenericToolAgent
12. Mock 扩展能力
```

## 7. 不要做的事

比赛版不要优先做：

```text
1. 真实短信平台
2. 真实腾讯会议
3. 真实邮件发送
4. 完整 MCP 市场
5. 复杂权限系统
6. 向量数据库
7. 分布式调度
8. 多租户管理后台
```

这些可以放在扩展设计里讲。


---

# 11. 测试用例与演示脚本

## 1. 基础测试用例

| 编号 | 输入 | 期望 |
|---|---|---|
| T01 | 明天下午三点提醒我开组会 | 解析时间，确认后创建日程 |
| T02 | 今天下午有什么安排 | 查询今天下午日程 |
| T03 | 明天有什么安排 | 查询明天日程 |
| T04 | 把明天下午三点的组会改到四点 | 定位事件，确认后修改 |
| T05 | 取消明天下午的组会 | 定位事件，强确认后删除 |
| T06 | 下午三点安排论文讨论 | 如果冲突，提示冲突并推荐时间 |
| T07 | 提前半小时提醒我 | 设置 reminderMinutes=30 |
| T08 | 帮我安排下周组会 | 使用用户习惯补全 |
| T09 | 确认 | 执行 pending_action |
| T10 | 取消 | 取消 pending_action |
| T11 | 重复确认 | 不重复执行 |
| T12 | 没有待确认时说确认 | 提示没有待确认操作 |
| T13 | 时间缺失 | 追问时间 |
| T14 | 标题缺失 | 追问标题 |
| T15 | LLM 输出异常 | 兜底提示重新输入 |

## 2. 扩展测试用例

| 编号 | 输入 | 期望 |
|---|---|---|
| E01 | 下午三点创建线上会议 | Mock 创建会议并写入日历 |
| E02 | 短信提醒张三参加会议 | 查询联系人，Mock 创建短信提醒 |
| E03 | 每周五下午六点总结日程 | 创建 scheduled_job |
| E04 | 创建会议失败 | 返回失败原因 |
| E05 | 短信失败 | 返回 PARTIAL_SUCCESS |
| E06 | 新增 Skill Manifest | reload 后可展示在 Skill 面板 |

## 3. 答辩演示脚本

### 场景一：基础创建

```text
用户：明天下午三点提醒我开组会。
系统：我将创建「组会」，时间是明天下午3点到4点，是否确认？
用户：确认。
系统：已创建日程「组会」。
页面：日历出现组会，时间线显示成功。
```

展示点：

```text
语音识别
时间解析
确认机制
真实日历写入
语音播报
```

### 场景二：查询日程

```text
用户：明天下午有什么安排？
系统：明天下午3点到4点有组会。
```

展示点：

```text
自然语言时间范围查询
日历数据真实读取
```

### 场景三：冲突检测

先保证明天下午三点已有组会。

```text
用户：明天下午三点安排论文讨论。
系统：该时间已有组会，建议改到下午4点，是否确认？
用户：确认。
系统：已为你安排明天下午4点的论文讨论。
```

展示点：

```text
冲突检测
空闲时间推荐
确认后创建
```

### 场景四：修改日程

```text
用户：把明天下午四点的论文讨论改到五点。
系统：我将把「论文讨论」改到明天下午5点，是否确认？
用户：确认。
系统：已修改。
```

展示点：

```text
事件定位
修改确认
日历刷新
```

### 场景五：删除日程

```text
用户：取消明天下午的论文讨论。
系统：我将删除「论文讨论」，是否确认？
用户：确认。
系统：已删除。
```

展示点：

```text
删除强确认
软删除
日历刷新
```

### 场景六：动态 Skill 扩展

```text
用户：下午三点帮我创建线上会议，并短信提醒张三。
系统：我将创建线上会议，写入日历，并短信提醒张三，是否确认？
用户：确认。
系统：会议和日程已创建，短信提醒已设置。
```

时间线展示：

```text
✓ contact.query
✓ calendar.conflict_check
✓ meeting.create
✓ calendar.create
✓ sms.send
```

展示点：

```text
不是写死功能
动态 Skill 选择
ActionPlan 编排
Mock 扩展能力
Workflow 可视化
```

## 4. 答辩话术

```text
我们先保证题目要求的基础语音日历能力真实可用，包括语音添加、查询、修改、删除、提醒和冲突检测。

在此基础上，我们没有为短信、会议、邮件等能力分别写死 Skill 类，而是设计了动态 Skill Manifest。每个 Skill 用配置描述输入参数、确认策略、失败策略和执行源。通用 Tool Agent 读取配置后执行 ActionPlan。

这样系统既能完成题目一的基础日历工具，又具备后续扩展成通用语音任务编排平台的能力。
```


---

# 12. 扩展功能设计

## 1. 扩展原则

扩展能力不能影响基础日历稳定性。

原则：

```text
基础日历真实实现
扩展工具优先 Mock
通用 Agent 统一执行
Workflow 统一记录
敏感操作必须确认
```

## 2. 线上会议创建

### 2.1 Skill

```yaml
skill_id: meeting.create
name: 创建线上会议
description: 创建一个线上会议链接
category: meeting
enabled: true

trigger_examples:
  - 创建线上会议
  - 帮我建一个会议链接
  - 下周一给项目组建会议

input_schema:
  type: object
  required:
    - title
    - start_time
    - end_time
  properties:
    title:
      type: string
    start_time:
      type: string
    end_time:
      type: string
    participants:
      type: array

executor:
  type: mock
  mock_name: meetingCreateMock
  arguments_mapping:
    title: $.title
    startTime: $.start_time
    endTime: $.end_time
  response_template:
    meeting_id: "m_${uuid}"
    meeting_url: "https://meeting.example.com/${uuid}"

confirm_policy:
  required: true
  level: MEDIUM

failure_policy:
  on_failure: stop
  retry: 1
```

### 2.2 输出给后续步骤

```text
meeting.create 输出 meeting_url
calendar.create description 引用 ${meeting.meeting_url}
```

## 3. 短信提醒

比赛版 Mock，不接真实短信平台。

```yaml
skill_id: sms.send
name: 发送短信
description: 给联系人发送短信提醒
category: communication

input_schema:
  type: object
  required:
    - receiver
    - content
  properties:
    receiver:
      type: string
    phone:
      type: string
    content:
      type: string
    send_time:
      type: string

executor:
  type: mock
  mock_name: smsSendMock

confirm_policy:
  required: true
  level: HIGH

failure_policy:
  on_failure: continue
  retry: 3
```

## 4. 定时邮件

比赛版只创建 scheduled_job，不真实发送。

```yaml
skill_id: email.schedule
name: 定时邮件
description: 在指定时间发送邮件
category: communication

executor:
  type: scheduler
  job_type: scheduled_email
  run_at: $.send_time

confirm_policy:
  required: true
  level: HIGH

failure_policy:
  on_failure: continue
  retry: 3
```

## 5. 用户习惯补全

### 5.1 目标

用户说：

```text
帮我安排下周组会
```

系统根据习惯补全：

```text
组会默认周一上午10点
组会默认时长1小时
组会默认线上会议
组会默认提前10分钟提醒
```

### 5.2 比赛版实现

不用向量数据库，直接 MySQL 关键词匹配。

表：

```text
user_preference
```

查询：

```sql
SELECT *
FROM user_preference
WHERE user_id = ?
  AND keyword LIKE ?
ORDER BY confidence DESC;
```

### 5.3 后续扩展

```text
pgvector
Milvus
向量检索 + 关键词检索
长期记忆
团队日程规则
```

## 6. MCP 扩展

### 6.1 定位

MCP 作为后续工具接入标准，不作为比赛版主功能。

支持：

```text
本地 MCP Server
远程 MCP Server
搜索工具
文件工具
GitHub 工具
数据库工具
```

### 6.2 McpToolAdapter

```text
Skill executor.type = mcp
  ↓
读取 server_id
  ↓
McpServerRegistry 获取连接
  ↓
McpClientManager 调用 tool
  ↓
转换为 ToolExecutionResult
```

### 6.3 安全要求

```text
1. MCP Server 必须注册
2. MCP Tool 必须白名单
3. 高风险工具必须确认
4. 所有调用写 tool_call_record
5. 失败不能影响基础日历
```

## 7. HTTP 扩展

适合接真实平台：

```text
腾讯会议
飞书日历
企业微信
短信平台
邮件服务
搜索 API
```

要求：

```text
1. URL 域名白名单
2. 超时控制
3. 重试控制
4. Token 不写进 Manifest 明文
5. 响应统一转换
```

## 8. Database Tool 扩展

只允许受控查询，不允许用户写 SQL。

```yaml
executor:
  type: database
  query_key: contact.find_by_name
```

后端维护：

```text
contact.find_by_name -> 固定 SQL 模板
preference.search -> 固定 SQL 模板
```

## 9. 扩展功能边界

比赛版只需要证明：

```text
新增能力不需要新增 Java Skill 类
只要写 Skill Manifest
通用 Agent 能选择并执行
Workflow 能记录
```

不要承诺比赛版完成所有真实平台接入。
