export interface Friend {
  id: string;
  username: string;
  online: boolean;
  since?: string;
}

export interface FriendRequest {
  id: number;
  userId: string;
  username: string;
  createdAt: string;
}

export interface UserSearchHit {
  id: string;
  username: string;
}
