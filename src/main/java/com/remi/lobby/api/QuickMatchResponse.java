package com.remi.lobby.api;
import com.remi.lobby.domain.LobbyGame;
public record QuickMatchResponse(boolean matched, LobbyGame game) {}
