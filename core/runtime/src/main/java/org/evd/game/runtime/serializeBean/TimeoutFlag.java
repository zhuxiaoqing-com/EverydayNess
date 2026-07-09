package org.evd.game.runtime.serializeBean;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.runtime.Service;

/**
 *
 *
 * 带超时的flag
 */
@SerializeClass
public class TimeoutFlag extends TickTimer {


	public TimeoutFlag() {
	}

	public TimeoutFlag(long interval) {
		super(interval);
	}

	/**
	 * 检测flag是否过期
	 * @param timeoutFlag
	 * @return
	 */
	public static boolean checkExpire(TimeoutFlag timeoutFlag) {
		if (timeoutFlag == null || !timeoutFlag.getRunning()) {
			return true;
		}
		return timeoutFlag.isOnce(Service.getTime());
	}
}
