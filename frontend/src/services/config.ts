const normalizeOrigin = (value: string) => value.replace(/\/+$/, '')

export const API_ORIGIN = normalizeOrigin(import.meta.env.API_ORIGIN || 'http://localhost:8080')
export const API_VERSION_PATH = '/api/v1'
export const API_BASE_URL = `${API_ORIGIN}${API_VERSION_PATH}`
export const API_RESEARCH_BASE = `${API_BASE_URL}/research`
export const API_USER_BASE = `${API_BASE_URL}/user`
