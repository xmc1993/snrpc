package org.stefan.snrpc.client;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedList;
import java.util.List;

import org.stefan.snrpc.SnRpcClient;
import org.stefan.snrpc.SnRpcConnection;
import org.stefan.snrpc.SnRpcConnectionFactory;
import org.stefan.snrpc.log.Logger;
import org.stefan.snrpc.log.LoggerFactory;
import org.stefan.snrpc.serializer.SnRpcRequest;
import org.stefan.snrpc.serializer.SnRpcResponse;
import org.stefan.snrpc.util.Sequence;


public class CommonSnRpcClient implements SnRpcClient {
	/**
	 * 这个方法实现了Client接口
	 * 服务端将使用这个类的实例进行RPC调用过程
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(CommonSnRpcClient.class);



	private SnRpcConnectionFactory connectionFactory;

	private SnRpcConnection connection;

	private SnRpcInvoker invoker = new SnRpcInvoker();

	/**
	 * 构造方法 传过来的是一个connectionFactory暂时不明白是为什么
	 *
	 */
	public CommonSnRpcClient(SnRpcConnectionFactory connectionFactory) {
		if (null == connectionFactory)
			throw new NullPointerException("connectionFactory is null...");
		this.connectionFactory = connectionFactory;
	}

	/**
	 * 构造方法 这一次传过来的是一个 connection 可是有多种构造方法么
	 *
	 */
	public CommonSnRpcClient(SnRpcConnection connection) {
		if (null == connection)
			throw new NullPointerException("connection is null...");
		this.connection = connection;
	}

	/**
	 * 销毁连接？
	 *
	 */
	public void destroy() throws Throwable {
		if (null != connection) {
			connection.close();
		}
	}

	
	/**
	 * 产生requestID...
	 */
	protected String generateRequestID() {
		return Sequence.next()+"";
	}

	/**
	 * recycle
	 * @param connection
	 */
	private void recycle(SnRpcConnection connection) {
		if (null != connection && null != connectionFactory) {
			try {
				connectionFactory.recycle(connection);
			} catch (Throwable t) {
				logger.warn("recycle rpc connection fail!", t);
			}
		}
	}

	/**
	 * 返回connection   !!!注意这里调用了connection()这个方法 很关键 在返回连接的时候进行connect
	 */
	private SnRpcConnection getConnection() throws Throwable {
		if (null != connection) {
			if (!connection.isConnected()) {
				connection.connection();
			}
			return connection;
		} else {
			return connectionFactory.getConnection();
		}
	}


	/**
	 *根据传进来的类型的Class返回代理类 关键在于 invoker!
	 *
	 */
	public <T> T proxy(Class<T> interfaceClass) throws Throwable {
		if (!interfaceClass.isInterface()) {
			throw new IllegalArgumentException(interfaceClass.getName()
					+ " is not an interface");
		}
		return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(),
				new Class<?>[] { interfaceClass }, invoker);
	}


	/**
	 *最关键的内部类 代理通过调用Invoker来实现方法的调用
	 */
	private class SnRpcInvoker implements InvocationHandler {
		/**
		 * invoke方法
		 */
		public Object invoke(Object proxy, Method method, Object[] args)
				throws Throwable {
			/**得到这个包含这个方法的类名*/
			String className = method.getDeclaringClass().getName();
			/**将参数类型列表存放到一个List中去*/
			List<String> parameterTypes = new LinkedList<String>();
			for (Class<?> parameterType : method.getParameterTypes()) {
				parameterTypes.add(parameterType.getName());
			}
			/**产生一个RequestID*/
			String requestID = generateRequestID();
			/**将它封装到一个Request里面去*/
			SnRpcRequest request = new SnRpcRequest(requestID, className,
					method.getName(), parameterTypes.toArray(new String[0]),
					args);
			/**声明相应的connection和将要得到的response*/
			SnRpcConnection connection = null;
			SnRpcResponse response = null;
			/**尝试着进行connect然后发送request请求得到返回的response*/
			try {
				connection = getConnection();
				response = connection.sendRequest(request);
			} catch (Throwable t) {
				logger.warn("send rpc request fail! request: <{}>",
						new Object[] { request }, t);
				throw new RuntimeException(t);
			} finally {
				recycle(connection);
			}

			/**如果response中存在异常那么就抛出否则就将结果返回！*/
			if (response.getException() != null) {
				throw response.getException();
			} else {
				return response.getResult();
			}
		}
	}
}
