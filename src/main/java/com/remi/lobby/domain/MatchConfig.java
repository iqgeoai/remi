package com.remi.lobby.domain;

import com.remi.engine.domain.Difficulty;
import com.remi.engine.domain.Mode;

public record MatchConfig(int numPlayers, Mode mode, Difficulty difficulty) {}
