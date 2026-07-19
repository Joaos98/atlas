import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL
})

api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.username && auth.password) {
    config.auth = {
      username: auth.username,
      password: auth.password
    }
  }
  return config
})

export default api