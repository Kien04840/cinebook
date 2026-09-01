<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import type { UserProfileResponse } from '@/types/auth.types'
import userService from '@/services/user.service'
import { formatDateTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Input from '@/components/common/Input.vue'
import Badge from '@/components/common/Badge.vue'
import Pagination from '@/components/common/Pagination.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import Modal from '@/components/common/Modal.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useToast } from '@/composables/useToast'

const { t } = useI18n()
const toast = useToast()

const users = ref<UserProfileResponse[]>([])
const isLoading = ref(true)
const errorMessage = ref('')

// Filters & Pagination
const searchQuery = ref('')
const selectedStatus = ref<string>('ALL')
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pageSize = ref(10)

// Status change modal
const isStatusModalOpen = ref(false)
const targetUser = ref<UserProfileResponse | null>(null)
const nextStatus = ref<'ACTIVE' | 'INACTIVE' | 'BLOCKED'>('ACTIVE')
const isUpdatingStatus = ref(false)

function getStatusBadgeVariant(status: string) {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'BLOCKED':
      return 'danger'
    case 'INACTIVE':
      return 'neutral'
    default:
      return 'neutral'
  }
}

function getStatusLabel(status: string) {
  switch (status) {
    case 'ACTIVE':
      return t('status.ACTIVE')
    case 'BLOCKED':
      return t('status.BLOCKED')
    case 'INACTIVE':
      return t('status.INACTIVE')
    default:
      return status
  }
}

async function fetchUsers() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const res = await userService.getAdminUsers({
      q: searchQuery.value.trim() || undefined,
      status: selectedStatus.value !== 'ALL' ? selectedStatus.value : undefined,
      page: currentPage.value,
      size: pageSize.value,
    })

    users.value = res.content || []
    totalPages.value = res.totalPages || 0
    totalElements.value = res.totalElements || 0
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
  } finally {
    isLoading.value = false
  }
}

function promptStatusChange(user: UserProfileResponse, newStatus: 'ACTIVE' | 'INACTIVE' | 'BLOCKED') {
  targetUser.value = user
  nextStatus.value = newStatus
  isStatusModalOpen.value = true
}

async function confirmStatusChange() {
  if (!targetUser.value) return

  isUpdatingStatus.value = true
  try {
    await userService.updateUserStatus(targetUser.value.id, nextStatus.value)
    toast.success(t('profile.updateSuccess'))
    isStatusModalOpen.value = false
    await fetchUsers()
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  } finally {
    isUpdatingStatus.value = false
  }
}

function onPageChange(page: number) {
  currentPage.value = page
  fetchUsers()
}

// Watch filters
let debounceTimer: any = null
watch(searchQuery, () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    currentPage.value = 0
    fetchUsers()
  }, 400)
})

watch(selectedStatus, () => {
  currentPage.value = 0
  fetchUsers()
})

onMounted(() => {
  fetchUsers()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight">{{ t('adminUsers.title') }}</h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminUsers.subtitle') }}
        </p>
      </div>

      <Button variant="secondary" size="md" :loading="isLoading" @click="fetchUsers">
        <template #prefix>
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </template>
        {{ t('common.refresh') }}
      </Button>
    </div>

    <!-- Error Alert -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" />

    <!-- Filters -->
    <Card padding="sm">
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div class="sm:col-span-2">
          <Input
            v-model="searchQuery"
            :placeholder="t('adminUsers.searchPlaceholder')"
            clearable
          >
            <template #prefix>
              <svg class="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </template>
          </Input>
        </div>

        <div>
          <select
            v-model="selectedStatus"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="ALL">{{ t('adminUsers.allStatuses') }}</option>
            <option value="ACTIVE">{{ t('adminUsers.statusActive') }}</option>
            <option value="BLOCKED">{{ t('adminUsers.statusBlocked') }}</option>
            <option value="INACTIVE">{{ t('adminUsers.statusInactive') }}</option>
          </select>
        </div>
      </div>
    </Card>

    <!-- Table -->
    <Card padding="none">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-sm">
          <thead>
            <tr class="bg-slate-850 border-b border-slate-700/80 text-xs font-semibold uppercase text-slate-400 tracking-wider">
              <th class="px-4 py-3">{{ t('adminUsers.colUser') }}</th>
              <th class="px-4 py-3">{{ t('adminUsers.colPhone') }}</th>
              <th class="px-4 py-3">{{ t('adminUsers.colRoles') }}</th>
              <th class="px-4 py-3">{{ t('adminUsers.colCreatedAt') }}</th>
              <th class="px-4 py-3">{{ t('adminUsers.colStatus') }}</th>
              <th class="px-4 py-3 text-right">{{ t('adminUsers.colActions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-700/60">
            <!-- Loading Skeleton Rows -->
            <template v-if="isLoading">
              <tr v-for="i in 6" :key="'skel-usr-' + i" class="animate-pulse">
                <td class="px-4 py-4">
                  <div class="flex items-center gap-3">
                    <div class="w-9 h-9 rounded-full bg-slate-800"></div>
                    <div class="space-y-1.5"><div class="h-4 w-32 bg-slate-800 rounded"></div><div class="h-3 w-40 bg-slate-800 rounded"></div></div>
                  </div>
                </td>
                <td class="px-4 py-4"><div class="h-4 w-24 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4"><div class="h-6 w-20 bg-slate-800 rounded-full"></div></td>
                <td class="px-4 py-4"><div class="h-4 w-28 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4"><div class="h-6 w-24 bg-slate-800 rounded-full"></div></td>
                <td class="px-4 py-4 text-right"><div class="h-8 w-24 bg-slate-800 rounded ml-auto"></div></td>
              </tr>
            </template>

            <!-- Empty -->
            <tr v-else-if="users.length === 0">
              <td colspan="6" class="px-4 py-16 text-center text-slate-400">
                <div class="max-w-sm mx-auto space-y-2">
                  <svg class="w-10 h-10 mx-auto text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
                  </svg>
                  <p class="text-sm font-medium text-slate-300">{{ t('adminUsers.emptyTitle') }}</p>
                  <p class="text-xs text-slate-500">{{ t('adminUsers.emptyDesc') }}</p>
                </div>
              </td>
            </tr>

            <!-- Rows -->
            <tr
              v-for="u in users"
              :key="u.id"
              class="hover:bg-slate-750/70 transition-colors"
            >
              <td class="px-4 py-3.5">
                <div class="flex items-center gap-3">
                  <UserAvatar :src="u.avatarUrl" :name="u.fullName" size="md" />
                  <div>
                    <p class="font-bold text-slate-100">{{ u.fullName || 'User' }}</p>
                    <p class="text-xs text-slate-400 mt-0.5">{{ u.email }}</p>
                  </div>
                </div>
              </td>
              <td class="px-4 py-3.5 text-slate-300 font-mono text-xs">
                {{ u.phone || '—' }}
              </td>
              <td class="px-4 py-3.5">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="role in u.roles"
                    :key="role"
                    :class="[
                      'px-2 py-0.5 rounded text-[11px] font-semibold uppercase tracking-wider',
                      role === 'ADMIN'
                        ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                        : 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/30',
                    ]"
                  >
                    {{ role }}
                  </span>
                </div>
              </td>
              <td class="px-4 py-3.5 text-slate-300 text-xs font-mono">
                {{ formatDateTime(u.createdAt) }}
              </td>
              <td class="px-4 py-3.5">
                <Badge :variant="getStatusBadgeVariant(u.status)">
                  {{ getStatusLabel(u.status) }}
                </Badge>
              </td>
              <td class="px-4 py-3.5 text-right">
                <div class="flex items-center justify-end gap-2">
                  <Button
                    v-if="u.status === 'ACTIVE'"
                    variant="danger"
                    size="sm"
                    @click="promptStatusChange(u, 'BLOCKED')"
                  >
                    {{ t('adminUsers.blockBtn') }}
                  </Button>
                  <Button
                    v-else-if="u.status === 'BLOCKED'"
                    variant="secondary"
                    size="sm"
                    @click="promptStatusChange(u, 'ACTIVE')"
                  >
                    {{ t('adminUsers.unblockBtn') }}
                  </Button>
                  <Button
                    v-else
                    variant="primary"
                    size="sm"
                    @click="promptStatusChange(u, 'ACTIVE')"
                  >
                    {{ t('adminUsers.activateBtn') }}
                  </Button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="p-4 border-t border-slate-700/80">
        <Pagination
          :current-page="currentPage"
          :total-pages="totalPages"
          @page-change="onPageChange"
        />
      </div>
    </Card>

    <!-- Status Change Modal -->
    <Modal
      v-model="isStatusModalOpen"
      :title="t('adminUsers.statusModalTitle')"
      @close="isStatusModalOpen = false"
    >
      <div class="space-y-4 text-sm text-slate-300">
        <p>
          {{ t('adminUsers.statusModalDesc', { name: targetUser?.fullName || targetUser?.email || '', status: getStatusLabel(nextStatus) }) }}
        </p>

        <div v-if="targetUser" class="p-3.5 rounded-lg bg-slate-850 border border-slate-700 flex items-center gap-3">
          <UserAvatar :src="targetUser.avatarUrl" :name="targetUser.fullName" size="md" />
          <div class="text-xs">
            <p class="font-bold text-white">{{ targetUser.fullName }}</p>
            <p class="text-slate-400 mt-0.5">{{ targetUser.email }}</p>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end gap-3">
          <Button variant="secondary" size="md" @click="isStatusModalOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button
            :variant="nextStatus === 'BLOCKED' ? 'danger' : 'primary'"
            size="md"
            :loading="isUpdatingStatus"
            @click="confirmStatusChange"
          >
            {{ t('adminUsers.confirmUpdateBtn') }}
          </Button>
        </div>
      </template>
    </Modal>
  </div>
</template>
