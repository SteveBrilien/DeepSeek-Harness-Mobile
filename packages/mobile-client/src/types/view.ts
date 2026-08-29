import type { RpcId } from '@deepseek-ai/dsh-host-apiproxy/api'

export interface ConversationItem {
  key: string
  kind: 'user' | 'assistant' | 'reasoning' | 'tool' | 'notice'
  text: string
  title?: string
  pending?: boolean
  seq?: number
  callId?: string
}

export interface PendingApproval {
  rpcId: RpcId
  sessionId: string
  approvalId: string
  toolName: string
  reason?: string
}

export interface PendingQuestion {
  rpcId: RpcId
  sessionId: string
  questions: Array<{ header?: string; question?: string; options?: Array<{ label: string; description?: string }> }>
}
