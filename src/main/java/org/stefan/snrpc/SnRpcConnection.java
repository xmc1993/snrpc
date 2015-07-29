package org.stefan.snrpc;

import org.stefan.snrpc.serializer.SnRpcRequest;
import org.stefan.snrpc.serializer.SnRpcResponse;

/**
 * @author zhaoliangang 2014-11-13
 */
public interface SnRpcConnection {
	/**
	 *Connection最重要的职责 接收一个request然后返回一个response
	 */
	SnRpcResponse sendRequest(SnRpcRequest request) throws Throwable;

	/**
	 *进行连接？
	 */
	void connection() throws Throwable;

	/**
	 *关闭connection
	 */
	void close() throws Throwable;

	/**
	 *返回是否处于连接状态
	 */
	boolean isConnected();

	/**
	 *连接是否已经关闭了
	 */
	boolean isClosed();
}
