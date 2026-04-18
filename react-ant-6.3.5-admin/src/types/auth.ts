export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  tokenType: string;
  accessToken: string;
  username: string;
  displayName: string;
  expiresAt: number | null;
}

export interface CurrentUserResponse {
  username: string;
  displayName: string;
  expiresAt: number | null;
}
