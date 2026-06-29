package org.evd.game.LobbyService.routing;

import org.evd.game.runtime.call.CallPoint;

public record LobbyPlayerCandidate(CallPoint callPoint, int onlineCount) {
}
