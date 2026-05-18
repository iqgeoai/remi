export interface ChatMessage {
  id: number;
  senderId: string;
  senderUsername: string;
  body: string;
  createdAt: string;
}

export interface DmConversation {
  otherUserId: string;
  otherUsername: string;
  lastMessageAt: string;
  lastBody: string;
}

/** Channel key format: "match:<uuid>" or "dm:<uuid>" */
export type ChannelKey = string;

export function matchKey(matchId: string): ChannelKey {
  return `match:${matchId}`;
}

export function dmKey(otherUserId: string): ChannelKey {
  return `dm:${otherUserId}`;
}
