import axios from 'axios';

const API_PREFIX = import.meta.env.API_BASE_URL || '/api/v1';
const API_BASE_URL = `${API_PREFIX}/user`;

// Google OAuth Config
export const GOOGLE_CLIENT_ID = import.meta.env.GOOGLE_CLIENT_ID || '';
export const GOOGLE_CLIENT_SECRET = import.meta.env.GOOGLE_CLIENT_SECRET || '';
export const REDIRECT_URI = import.meta.env.GOOGLE_REDIRECT_URI || 'https://research.chanler.dev/oauth2callback';

interface Result<T> {
  code: number;
  message: string;
  data: T;
}

export interface AuthResponse {
  token: string;
}

export interface UserInfo {
  avatarUrl: string;
}

// Token management
export const getToken = (): string | null => localStorage.getItem('token');
export const setToken = (token: string) => localStorage.setItem('token', token);
export const removeToken = () => localStorage.removeItem('token');
export const isAuthenticated = () => !!getToken();

// Create axios instance with auth header
const authApi = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

// Add auth token to requests
authApi.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const authService = {
  register: async (username: string, password: string): Promise<AuthResponse> => {
    const response = await authApi.post<Result<AuthResponse>>('/register', { username, password });
    if (response.data.code !== 0) {
      throw new Error(response.data.message || 'Registration failed');
    }
    return response.data.data;
  },

  login: async (username: string, password: string): Promise<AuthResponse> => {
    const response = await authApi.post<Result<AuthResponse>>('/login', { username, password });
    if (response.data.code !== 0) {
      throw new Error(response.data.message || 'Login failed');
    }
    return response.data.data;
  },

  googleCallback: async (code: string): Promise<AuthResponse> => {
    const response = await authApi.get<Result<AuthResponse>>(`/google/callback?code=${code}`);
    if (response.data.code !== 0) {
      throw new Error(response.data.message || 'Google login failed');
    }
    return response.data.data;
  },

  // Google One Tap uses credential (ID token) instead of code
  googleOneTap: async (credential: string): Promise<AuthResponse> => {
    const response = await authApi.post<Result<AuthResponse>>('/google/onetap', { credential });
    if (response.data.code !== 0) {
      throw new Error(response.data.message || 'Google login failed');
    }
    return response.data.data;
  },

  getMe: async (): Promise<UserInfo> => {
    const response = await authApi.get<Result<UserInfo>>('/me');
    if (response.data.code !== 0) {
      throw new Error(response.data.message || 'Failed to get user info');
    }
    return response.data.data;
  },

  logout: () => {
    removeToken();
  },

  // Build Google OAuth URL for redirect flow
  getGoogleAuthUrl: () => {
    return (
      `https://accounts.google.com/o/oauth2/v2/auth` +
      `?client_id=${GOOGLE_CLIENT_ID}` +
      `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}` +
      `&response_type=code` +
      `&scope=openid` +
      `&access_type=offline`
    );
  },
};
