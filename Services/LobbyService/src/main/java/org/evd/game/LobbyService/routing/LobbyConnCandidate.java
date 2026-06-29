package org.evd.game.LobbyService.routing;

import org.evd.game.runtime.call.CallPoint;

public record LobbyConnCandidate(CallPoint callPoint, String publicAddr, int loginCount) {
}
