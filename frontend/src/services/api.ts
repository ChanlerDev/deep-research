import axios from 'axios';
import { getToken } from './auth';

const API_PREFIX = import.meta.env.API_BASE_URL || '/api/v1';
const API_BASE_URL = `${API_PREFIX}/research`;

// Get user ID - now handled by auth token, this is for legacy/guest support
export const getUserId = () => {
  const stored = localStorage.getItem('userId');
  if (stored) return stored;
  const newId = '1'; // Default/Guest user
  localStorage.setItem('userId', newId);
  return newId;
};

// Backend Result wrapper
interface Result<T> {
  code: number;
  message: string;
  data: T;
}

export interface CreateResearchResponse {
  researchIds: string[];
}

export interface SendMessageRequest {
  content: string;
  modelName?: string;
  baseUrl?: string;
  apiKey?: string;
  budget?: 'MEDIUM' | 'HIGH' | 'ULTRA';
}

export interface SendMessageResponse {
  id: string;
  content: string;
}

export interface ResearchStatusResponse {
  id: string;
  title?: string;
  status: string;
  startTime?: string;
  updateTime?: string;
  completeTime?: string;
  totalInputTokens?: number;
  totalOutputTokens?: number;
}

export interface ChatMessage {
  id: number;
  researchId: string;
  role: 'user' | 'assistant';
  content: string;
  sequenceNo?: number;
  createTime: string;
}

export interface WorkflowEvent {
  id: number;
  researchId: string;
  type: string; // e.g., 'SCOPE', 'SUPERVISOR', 'RESEARCHER'
  title: string;
  content: string;
  parentEventId?: number;
  sequenceNo?: number;
  createTime: string;
}

export interface ResearchMessageResponse {
  id: string;
  status: string;
  messages: ChatMessage[];
  events: WorkflowEvent[];
  startTime?: string;
  updateTime?: string;
  completeTime?: string;
  totalInputTokens?: number;
  totalOutputTokens?: number;
}

export interface ModelInfo {
  modelName: string;
  model: string;
}

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add auth token and user ID to all requests
api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  // Legacy user ID support for non-authenticated requests
  config.headers['X-User-Id'] = getUserId();
  return config;
});

export const researchApi = {
  getModelList: async (): Promise<ModelInfo[]> => {
    const response = await api.get<Result<ModelInfo[]>>(`/models/free`);
    if (response.data.code !== 0) return [];
    return response.data.data;
  },

  create: async (num: number = 1): Promise<CreateResearchResponse> => {
    const response = await api.get<Result<CreateResearchResponse>>(`/create?num=${num}`);
    if (response.data.code !== 0) {
      throw new Error(response.data.message || 'Failed to create research');
    }
    return response.data.data;
  },

  sendMessage: async (researchId: string, content: string, modelConfig?: Partial<SendMessageRequest>): Promise<SendMessageResponse> => {
    const payload: SendMessageRequest = { content, ...modelConfig };
    const response = await api.post<Result<SendMessageResponse>>(`/research/${researchId}/messages`, payload);
    if (response.data.code !== 0) {
      throw new Error(response.data.message || 'Failed to send message');
    }
    return response.data.data;
  },

  getStatus: async (researchId: string): Promise<ResearchStatusResponse> => {
    const response = await api.get<Result<ResearchStatusResponse>>(`/research/${researchId}`);
    if (response.data.code !== 0) {
      throw new Error(response.data.message || 'Failed to get status');
    }
    return response.data.data;
  },

  getMessages: async (researchId: string): Promise<ResearchMessageResponse> => {
    const response = await api.get<Result<ResearchMessageResponse>>(`/research/${researchId}/messages`);
    if (response.data.code !== 0) {
      throw new Error(response.data.message || 'Failed to get messages');
    }
    return response.data.data;
  },

  getHistory: async (): Promise<ResearchStatusResponse[]> => {
    const response = await api.get<Result<ResearchStatusResponse[]>>(`/list`);
    if (response.data.code !== 0) {
       // If API doesn't exist yet, return empty list to avoid breaking UI
       // throw new Error(response.data.message || 'Failed to get history');
       return [];
    }
    return response.data.data;
  },
  
  getSseUrl: () => `${API_BASE_URL}/sse`
};
