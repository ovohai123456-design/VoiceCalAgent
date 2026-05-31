<template>
  <div v-if="checkingSession" class="session-loading">正在检查登录状态...</div>
  <LoginPage v-else-if="!currentUser" @authenticated="handleAuthenticated" />
  <VoiceCalPage v-else :user="currentUser" @logout="handleLogout" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';

import { getCurrentUser, logout } from '@/api/authApi';
import type { AuthUser } from '@/utils/auth';
import { UNAUTHORIZED_EVENT } from '@/utils/auth';
import LoginPage from '@/views/LoginPage.vue';
import VoiceCalPage from '@/views/VoiceCalPage.vue';

const currentUser = ref<AuthUser | null>(null);
const checkingSession = ref(true);

onMounted(async () => {
  window.addEventListener(UNAUTHORIZED_EVENT, clearSession);
  try {
    handleAuthenticated(await getCurrentUser());
  } catch {
    clearSession();
  } finally {
    checkingSession.value = false;
  }
});

onBeforeUnmount(() => window.removeEventListener(UNAUTHORIZED_EVENT, clearSession));

function handleAuthenticated(user: AuthUser): void {
  currentUser.value = user;
}

async function handleLogout(): Promise<void> {
  try {
    await logout();
  } finally {
    clearSession();
  }
}

function clearSession(): void {
  currentUser.value = null;
  checkingSession.value = false;
}
</script>

<style scoped>
.session-loading {
  display: grid;
  min-height: 100vh;
  place-items: center;
  color: rgb(205 227 250 / 76%);
}
</style>
