export interface AdminUpdateUserRequest {
  fullName: string
  status: 'ACTIVE' | 'BLOCKED'
  roles: string[]
}
