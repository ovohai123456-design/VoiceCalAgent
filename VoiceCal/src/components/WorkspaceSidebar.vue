<template>
  <aside class="workspace-sidebar">
    <el-tabs v-model="activeTab" class="workspace-tabs">
      <el-tab-pane label="提醒" name="reminders">
        <div class="tab-toolbar">
          <strong>提醒任务</strong>
          <el-button :icon="Bell" circle plain size="small" title="开启系统通知" @click="$emit('request-notification')" />
        </div>
        <p class="permission-text">{{ notificationButtonText }}</p>
        <el-empty v-if="!reminders.length" description="暂无提醒" :image-size="64" />
        <div v-else class="compact-list">
          <div v-for="reminder in reminders" :key="reminder.id" class="compact-row">
            <div>
              <strong>{{ resolveReminderTitle(reminder) }}</strong>
              <span>{{ reminder.runAt }}</span>
            </div>
            <el-tag :type="resolveReminderType(reminder.status)" effect="plain" size="small">
              {{ resolveReminderStatus(reminder.status) }}
            </el-tag>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="Skills" name="skills">
        <div class="tab-toolbar">
          <strong>Skill Registry</strong>
          <el-button :icon="Refresh" circle plain size="small" title="重新加载 Skill" :loading="loadingSkills" @click="loadSkills(true)" />
        </div>
        <div class="skill-summary">
          <span>{{ skills.length }} 项能力</span>
          <span>{{ enabledSkillCount }} 项启用</span>
        </div>
        <div class="compact-list">
          <div v-for="skill in skills" :key="skill.skillId" class="skill-row">
            <div class="skill-title">
              <strong>{{ skill.name }}</strong>
              <el-tag :type="skill.enabled ? 'success' : 'info'" effect="plain" size="small">
                {{ skill.enabled ? '已启用' : '已停用' }}
              </el-tag>
            </div>
            <code>{{ skill.skillId }}</code>
            <div class="skill-meta">
              <span>{{ skill.category || 'general' }}</span>
              <span>{{ skill.executor?.type || '-' }}</span>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="联系人" name="contacts">
        <div class="tab-toolbar">
          <strong>联系人</strong>
          <el-button :icon="Plus" circle plain size="small" title="新增联系人" @click="openContactDialog()" />
        </div>
        <el-empty v-if="!contacts.length" description="暂无联系人" :image-size="64" />
        <div v-else class="compact-list">
          <div v-for="contact in contacts" :key="contact.id" class="contact-row">
            <button class="contact-main" type="button" @click="openContactDialog(contact)">
              <strong>{{ contact.name }}</strong>
              <span>{{ contact.phone || '未设置手机号' }}</span>
              <span>{{ contact.email || '未设置邮箱' }}</span>
            </button>
            <el-button :icon="Delete" circle text type="danger" title="删除联系人" @click="removeContact(contact)" />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="偏好" name="preferences">
        <el-form label-position="top" class="preference-form">
          <el-form-item label="默认时长">
            <el-input-number v-model="preference.defaultDurationMinutes" :min="15" :step="15" />
            <span class="unit">分钟</span>
          </el-form-item>
          <el-form-item label="提前提醒">
            <el-input-number v-model="preference.defaultReminderMinutes" :min="0" :step="5" />
            <span class="unit">分钟</span>
          </el-form-item>
          <el-form-item label="默认地点">
            <el-input v-model="preference.defaultLocation" />
          </el-form-item>
          <el-form-item label="默认通知邮箱">
            <el-input v-model="preference.defaultEmail" />
          </el-form-item>
          <el-button type="primary" :loading="savingPreference" @click="savePreference">保存偏好</el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="contactDialogVisible" :title="contactForm.id ? '编辑联系人' : '新增联系人'" width="420px">
      <el-form label-position="top">
        <el-form-item label="姓名"><el-input v-model="contactForm.name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="contactForm.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="contactForm.email" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingContact" @click="submitContact">保存</el-button>
      </template>
    </el-dialog>
  </aside>
</template>

<script setup lang="ts">
import { Bell, Delete, Plus, Refresh } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import {
  deleteContact,
  getUserPreference,
  listContacts,
  saveContact,
  saveUserPreference,
  type ContactItem,
  type UserPreference,
} from '@/api/directoryApi';
import type { ReminderTask } from '@/api/reminderApi';
import { listSkills, reloadSkills, type SkillDefinition } from '@/api/skillApi';
import { DEFAULT_USER_ID } from '@/utils/session';

defineProps<{
  reminders: ReminderTask[];
  notificationButtonText: string;
}>();

defineEmits<{
  (event: 'request-notification'): void;
}>();

const activeTab = ref('reminders');
const skills = ref<SkillDefinition[]>([]);
const contacts = ref<ContactItem[]>([]);
const loadingSkills = ref(false);
const savingContact = ref(false);
const savingPreference = ref(false);
const contactDialogVisible = ref(false);
const contactForm = reactive<ContactItem>({ userId: Number(DEFAULT_USER_ID), name: '', phone: '', email: '' });
const preference = reactive<UserPreference>({ userId: Number(DEFAULT_USER_ID) });
const enabledSkillCount = computed(() => skills.value.filter((skill) => skill.enabled).length);

onMounted(() => {
  void Promise.all([loadSkills(), loadContacts(), loadPreference()]);
});

async function loadSkills(force = false): Promise<void> {
  loadingSkills.value = true;
  try {
    skills.value = force ? await reloadSkills() : await listSkills();
    if (force) ElMessage.success('Skill 已重新加载');
  } catch (error) {
    console.error(error);
    ElMessage.error('Skill 加载失败');
  } finally {
    loadingSkills.value = false;
  }
}

async function loadContacts(): Promise<void> {
  contacts.value = await listContacts(DEFAULT_USER_ID);
}

async function loadPreference(): Promise<void> {
  Object.assign(preference, await getUserPreference(DEFAULT_USER_ID));
}

function openContactDialog(contact?: ContactItem): void {
  Object.assign(contactForm, {
    id: contact?.id,
    userId: Number(DEFAULT_USER_ID),
    name: contact?.name ?? '',
    phone: contact?.phone ?? '',
    email: contact?.email ?? '',
  });
  contactDialogVisible.value = true;
}

async function submitContact(): Promise<void> {
  if (!contactForm.name.trim()) {
    ElMessage.warning('请输入联系人姓名');
    return;
  }
  savingContact.value = true;
  try {
    await saveContact({ ...contactForm });
    contactDialogVisible.value = false;
    await loadContacts();
    ElMessage.success('联系人已保存');
  } finally {
    savingContact.value = false;
  }
}

async function removeContact(contact: ContactItem): Promise<void> {
  if (!contact.id) return;
  await ElMessageBox.confirm(`确认删除联系人“${contact.name}”吗？`, '删除联系人', { type: 'warning' });
  await deleteContact(contact.id, DEFAULT_USER_ID);
  await loadContacts();
}

async function savePreference(): Promise<void> {
  savingPreference.value = true;
  try {
    Object.assign(preference, await saveUserPreference({ ...preference }));
    ElMessage.success('偏好已保存');
  } finally {
    savingPreference.value = false;
  }
}

function resolveReminderTitle(reminder: ReminderTask): string {
  if (!reminder.jobPayloadJson) return `日程 ${reminder.eventId}`;
  try {
    const payload = JSON.parse(reminder.jobPayloadJson) as { title?: string; receiver?: string };
    return payload.title || payload.receiver || `日程 ${reminder.eventId}`;
  } catch {
    return `日程 ${reminder.eventId}`;
  }
}

function resolveReminderStatus(status: string): string {
  return { PENDING: '待执行', EXECUTED: '已完成', CANCELED: '已取消', FAILED: '失败' }[status] ?? status;
}

function resolveReminderType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'PENDING') return 'warning';
  if (status === 'EXECUTED') return 'success';
  if (status === 'FAILED') return 'danger';
  return 'info';
}
</script>

<style scoped>
.workspace-sidebar {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid #d9e0e8;
  border-radius: 8px;
  background: #fff;
}

.workspace-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.workspace-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
}

.tab-toolbar,
.skill-title,
.skill-meta,
.contact-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.permission-text,
.skill-summary {
  color: #667085;
  font-size: 12px;
}

.permission-text {
  margin: 6px 0 12px;
}

.skill-summary {
  display: flex;
  gap: 14px;
  margin: 8px 0 12px;
}

.compact-list {
  display: grid;
  gap: 8px;
  max-height: 540px;
  overflow-y: auto;
}

.compact-row,
.skill-row,
.contact-row {
  padding: 10px;
  border: 1px solid #e7ebf0;
  border-radius: 6px;
}

.compact-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.compact-row div,
.skill-row,
.contact-main {
  display: grid;
  gap: 4px;
}

.compact-row span,
.contact-main span {
  color: #667085;
  font-size: 12px;
}

.skill-row code {
  overflow: hidden;
  color: #245b8a;
  font-size: 12px;
  text-overflow: ellipsis;
}

.skill-meta {
  color: #667085;
  font-size: 12px;
}

.contact-main {
  min-width: 0;
  border: 0;
  background: transparent;
  color: #1f2937;
  text-align: left;
  cursor: pointer;
}

.preference-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.unit {
  margin-left: 8px;
  color: #667085;
  font-size: 13px;
}
</style>
