package org.evd.game.runtime.Db.table.util;

import org.slf4j.Logger;

public class TimeCostPrint {
	// 一毫秒(单位：纳秒)
	public static final long millsNano = 1_000_000;
	public static final long secondMills = 1000;
	public static final long minuteMills = 1000 * 60L;
	public static final long hourMills = 1000 * 60 * 60L;
	public static final long dayMills = hourMills * 24L;
	public static final long weekMills = dayMills * 7L;
	String param;
	Logger logger;
	long startTime;

	long errorMill = 5000;

	public TimeCostPrint(Logger logger, String param) {
		this.param = param;
		this.logger = logger;
		start();
	}

	public TimeCostPrint(Logger logger, long errorSecond, String param) {
		this.param = param;
		this.logger = logger;
		this.errorMill = errorSecond * secondMills;
		start();
	}

	public void start() {
		startTime = System.nanoTime();
	}

	public void printError() {
		print(false);
	}

	public void print() {
		print(true);
	}

	public void print(boolean printInfo) {
		long endTime = System.nanoTime();
		long mill = (endTime - startTime) / millsNano;
		if (mill > errorMill) {
			logger.error(" 时间统计 error!!! costMill {}    param {} ", mill, param, new RuntimeException());
			return;
		}

		if (printInfo) {
			logger.info(" 时间统计 costMill {}    param {} ", mill, param);
		}
	}
}
