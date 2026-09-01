export type UserRole = 'ADMIN' | 'CUSTOMER' 

export interface User {
  id: string
  email: string
  fullName: string
  phone?: string
  avatarUrl?: string
  status?: 'ACTIVE' | 'INACTIVE' | 'BLOCKED'
  emailVerified?: boolean
  roles: string[]
  createdAt?: string
  updatedAt?: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export interface UserProfileResponse {
  id: string
  email: string
  fullName: string
  phone?: string
  avatarUrl?: string
  status: 'ACTIVE' | 'INACTIVE' | 'BLOCKED'
  emailVerified: boolean
  roles: string[]
  createdAt: string
  updatedAt?: string
}

export interface LoginPayload {
  email: string
  password: string
}

export interface RegisterPayload {
  email: string
  password: string
  fullName: string
  phone?: string
}

export interface RefreshTokenPayload {
  refreshToken: string
}

export interface UpdateProfilePayload {
  fullName: string
  phone?: string
  avatarUrl?: string
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
}
