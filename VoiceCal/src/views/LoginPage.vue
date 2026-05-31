<template>
  <main class="login-page">
    <section class="login-card">
      <div class="brand">
        <Calendar class="brand-icon" />
        <div>
          <h1>VoiceCal Agent</h1>
          <p>登录后管理你的日程、联系人和提醒</p>
        </div>
      </div>

      <el-tabs v-model="mode" stretch>
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>

      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" placeholder="3 到 64 位字母、数字、下划线或短横线" />
        </el-form-item>
        <el-form-item v-if="mode === 'register'" label="显示名称">
          <el-input v-model="form.displayName" autocomplete="name" placeholder="选填" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            autocomplete="current-password"
            placeholder="至少 6 位"
            show-password
            type="password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button class="submit-button" type="primary" :loading="submitting" @click="submit">
          {{ mode === 'login' ? '登录' : '注册并登录' }}
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { Calendar } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { reactive, ref } from 'vue';

import { login, register } from '@/api/authApi';
import type { AuthUser } from '@/utils/auth';

const emit = defineEmits<{ (event: 'authenticated', user: AuthUser): void }>();
const mode = ref<'login' | 'register'>('login');
const submitting = ref(false);
const form = reactive({ username: '', password: '', displayName: '' });

async function submit(): Promise<void> {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码');
    return;
  }
  submitting.value = true;
  try {
    const user = mode.value === 'login'
      ? await login({ username: form.username, password: form.password })
      : await register({ username: form.username, password: form.password, displayName: form.displayName });
    emit('authenticated', user);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败');
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 20px;
}

.login-card {
  width: min(430px, 100%);
  padding: 28px;
  border: 1px solid rgb(170 220 255 / 22%);
  border-radius: 28px;
  background:
    radial-gradient(circle at 20% 0%, rgb(74 144 226 / 28%), transparent 38%),
    linear-gradient(145deg, rgb(18 51 90 / 84%), rgb(9 27 52 / 78%));
  box-shadow: 0 26px 76px rgb(0 6 22 / 42%), inset 0 1px 0 rgb(255 255 255 / 10%);
  backdrop-filter: blur(28px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.brand-icon {
  width: 34px;
  height: 34px;
  color: #8ed8ff;
}

h1 {
  margin: 0;
  color: #f0f8ff;
  font-size: 24px;
}

p {
  margin: 7px 0 0;
  color: rgb(205 227 250 / 70%);
  font-size: 13px;
}

.submit-button {
  width: 100%;
  margin-top: 6px;
}
</style>
