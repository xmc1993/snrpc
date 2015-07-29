package org.stefan.snrpc;

/**
 * @author zhaoliangang 2014-11-13
 */
public interface SnRpcServer {
	/**
	 * Server的开始方法
	 * @throws Throwable
	 */
	void start() throws Throwable;
	/**
	 * Server的结束方法
	 * @throws Throwable
	 */
	void stop() throws Throwable;
}
