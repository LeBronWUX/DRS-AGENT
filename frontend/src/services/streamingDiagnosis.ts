import { request } from './request'
import { getToken } from './auth'

export interface DiagnosisEvent {
  eventType: string
  sessionId: string
  stepNumber?: number
  stepName: string
  description: string
  thinking?: string
  action?: string
  result?: string
  status: string
  executionTimeMs?: number
  confidence?: number
  rootCause?: string
  solution?: string
  finalResult?: string
}

export interface StreamingDiagnosisOptions {
  problem: string
  context?: string
  onEvent: (event: DiagnosisEvent) => void
  onComplete: (response: any) => void
  onError: (error: Error) => void
}

export const streamingDiagnosisApi = {
  /**
   * Start streaming diagnosis using SSE.
   */
  streamDiagnosis(options: StreamingDiagnosisOptions): () => void {
    const token = getToken()
    const baseUrl = '/v1/diagnose/stream'

    // For POST requests with SSE, we need a different approach
    // Use fetch with POST and then parse SSE manually
    let abortController = new AbortController()

    const fetchData = async () => {
      try {
        const response = await fetch(baseUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': token ? `Bearer ${token}` : '',
            'Accept': 'text/event-stream'
          },
          body: JSON.stringify({
            problem: options.problem,
            context: options.context
          }),
          signal: abortController.signal
        })

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`)
        }

        const reader = response.body?.getReader()
        if (!reader) throw new Error('No reader available')

        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })

          // Parse SSE events from buffer
          const events = buffer.split('\n\n')
          buffer = events.pop() || '' // Keep incomplete event in buffer

          for (const event of events) {
            if (event.startsWith('data:')) {
              const data = event.substring(5).trim()
              if (data) {
                try {
                  const parsed = JSON.parse(data)
                  options.onEvent(parsed)

                  if (parsed.eventType === 'RESULT') {
                    if (parsed.finalResult) {
                      const response = JSON.parse(parsed.finalResult)
                      options.onComplete(response)
                    } else {
                      options.onComplete({
                        sessionId: parsed.sessionId,
                        rootCause: parsed.rootCause,
                        confidence: parsed.confidence,
                        solution: parsed.solution,
                        status: 'COMPLETED'
                      })
                    }
                  }
                } catch (e) {
                  console.error('Failed to parse SSE event:', data, e)
                }
              }
            }
          }
        }
      } catch (error: any) {
        if (error.name !== 'AbortError') {
          options.onError(error)
        }
      }
    }

    fetchData()

    // Return abort function
    return () => {
      abortController.abort()
    }
  },

  /**
   * Alternative: Use regular diagnosis endpoint with polling for progress.
   */
  async diagnose(problem: string, context?: string): Promise<any> {
    return request.post('/v1/diagnose', { problem, context })
  },

  /**
   * Get diagnosis result by session ID.
   */
  async getDiagnosisResult(sessionId: string): Promise<any> {
    return request.get(`/v1/diagnose/${sessionId}`)
  }
}