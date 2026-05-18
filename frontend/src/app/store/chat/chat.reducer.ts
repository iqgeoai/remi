import { createFeature, createReducer, on } from '@ngrx/store';
import { ChatActions } from './chat.actions';
import { ChannelKey, ChatMessage, DmConversation } from './chat.models';

export interface ChatState {
  messagesByChannel: Record<ChannelKey, ChatMessage[]>;
  conversations: DmConversation[];
}

export const initialChatState: ChatState = {
  messagesByChannel: {},
  conversations: [],
};

export const chatFeature = createFeature({
  name: 'chat',
  reducer: createReducer<ChatState>(
    initialChatState,
    on(ChatActions.historyLoaded, (s, { channelKey, messages }) => ({
      ...s,
      messagesByChannel: { ...s.messagesByChannel, [channelKey]: messages },
    })),
    on(ChatActions.messageReceived, (s, { channelKey, message }) => {
      const existing = s.messagesByChannel[channelKey] ?? [];
      if (existing.some(m => m.id === message.id)) return s;
      return {
        ...s,
        messagesByChannel: {
          ...s.messagesByChannel,
          [channelKey]: [...existing, message],
        },
      };
    }),
    on(ChatActions.conversationsLoaded, (s, { conversations }) => ({
      ...s,
      conversations,
    })),
  ),
});
