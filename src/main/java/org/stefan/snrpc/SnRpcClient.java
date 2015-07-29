package org.stefan.snrpc;

/**
 * @author zhaoliangang 2014-11-13
 */
public interface SnRpcClient {
	/**
	 * Client就是根据传过来的类型返回相应的代理
	 * @param interfaceClass
	 * @param <T>
	 * @return
	 * @throws Throwable
	 */
	public <T> T proxy(Class<T> interfaceClass) throws Throwable;
}
