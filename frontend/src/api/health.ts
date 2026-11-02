const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api'

export type HealthStatus = {
  status: 'UP' | 'DOWN'
}

export async function fetchHealth(): Promise<HealthStatus> {
  const response = await fetch(`${apiBaseUrl}/health`)
  if (!response.ok) {
    throw new Error(`Health request failed (${response.status})`)
  }
  return response.json() as Promise<HealthStatus>
}
