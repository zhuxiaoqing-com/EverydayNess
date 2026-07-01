package org.evd.game.runtime;

import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.support.LogCore;

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
