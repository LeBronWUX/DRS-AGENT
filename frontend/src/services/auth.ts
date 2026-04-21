import { request } from './request'
import { ref } from 'vue'

export interface LoginResponse {
  success: boolean
  message: string
  token?: string
  username?: string
  role?: string
  displayName?: string
}

export interface AuthCheckResponse {
  authenticated: boolean
  isAdmin: boolean
}

// Store token in localStorage
const TOKEN_KEY = 'drs_token'
const USER_KEY = 'drs_user'

export const tokenRef = ref(localStorage.getItem(TOKEN_KEY))
export const userRef = ref<any>(null)

export const authApi = {
  login(username: string, password: string): Promise<LoginResponse> {
    return request.post('/v1/auth/login', { username, password })
  },

  logout(): Promise<{ success: boolean; message: string }> {
    return request.post('/v1/auth/logout')
  },

  getCurrentUser(): Promise<{ username: string; role: string; displayName: string }> {
    return request.get('/v1/auth/me')
  },

  checkAuth(): Promise<AuthCheckResponse> {
    return request.get('/v1/auth/check')
  }
}

export function saveToken(token: string, user: any) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
  tokenRef.value = token
  userRef.value = user
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  tokenRef.value = null
  userRef.value = null
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUser(): any {
  const userStr = localStorage.getItem(USER_KEY)
  return userStr ? JSON.parse(userStr) : null
}

export function isAuthenticated(): boolean {
  return !!getToken()
}

export function isAdmin(): boolean {
  const user = getUser()
  return user?.role === 'ADMIN'
}