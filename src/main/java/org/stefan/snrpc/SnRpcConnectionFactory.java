package org.stefan.snrpc;

/**
 * @author zhaoliangang 2014-11-13
 */
public interface SnRpcConnectionFactory {
	/**
	 * 返回一个Connection
	 */
	SnRpcConnection getConnection() throws Throwable;

	/**
	 *这个recycle并不知道是干么的...
	 */
	void recycle(SnRpcConnection connection) throws Throwable;
}
