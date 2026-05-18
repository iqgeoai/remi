import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { ChannelKey, ChatMessage, DmConversation } from './chat.models';

export const ChatActions = createActionGroup({
  source: 'Chat',
  events: {
    'Load Match History': props<{ matchId: string }>(),
    'Load Dm History': props<{ otherUserId: string }>(),
    'History Loaded': props<{ channelKey: ChannelKey; messages: ChatMessage[] }>(),
    'Send Match Message': props<{ matchId: string; body: string }>(),
    'Send Dm Message': props<{ otherUserId: string; body: string }>(),
    'Message Received': props<{ channelKey: ChannelKey; message: ChatMessage }>(),
    'Load Conversations': emptyProps(),
    'Conversations Loaded': props<{ conversations: DmConversation[] }>(),
  },
});
