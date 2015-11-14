package org.stefan.snrpc.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.stefan.snrpc.SnRpcConnection;
import org.stefan.snrpc.conf.SnRpcConfig;
import org.stefan.snrpc.serializer.ProtobufSerializer;
import org.stefan.snrpc.serializer.SnRpcRequest;
import org.stefan.snrpc.serializer.SnRpcRequestEncoder;
import org.stefan.snrpc.serializer.SnRpcResponse;
import org.stefan.snrpc.serializer.SnRpcResponseDecoder;

/**
 * 实现了Connection接口的类
 * 很重要！
 */
public class SnNettyRpcConnection extends SimpleChannelHandler implements
		SnRpcConnection {

	//inetsocket address
	private InetSocketAddress inetAddr;

	//org.jboss.netty.channel.Channel
	private volatile Channel channel;

	//response
	private volatile SnRpcResponse response;

	//exception
	private volatile Throwable exception;

	/**
	 * 这个Timer是干么的 并不知道啊...
	 */
	private volatile Timer timer;

	private boolean connected;

	private SnRpcConfig snRpcConfig = SnRpcConfig.getInstance();

	/**
	 *connection重要的构造方法
	 * 根据host和port创建相应的...
	 */
	public SnNettyRpcConnection(String host, int port) {
		snRpcConfig.loadProperties("snrpcserver.properties");
		this.inetAddr = new InetSocketAddress(host, port);
		this.timer = new HashedWheelTimer();
	}

	/**
	 * 十分十分关键一个方法
	 *
	 */
	public SnRpcResponse sendRequest(SnRpcRequest request) throws Throwable {
		/**
		 * 如果没有处于连接状态就抛出异常
		 */
		if (!isConnected()) {
			throw new IllegalStateException("not connected");
		}
		/**
		 * 发出请求
		 */
		ChannelFuture writeFuture = channel.write(request);
		/**
		 * 如果什么什么的失败了....就抛出异常
		 */
		if (!writeFuture.awaitUninterruptibly().isSuccess()) {
			close();
			throw writeFuture.getCause();
		}
		/**没有出错的话就...等待响应*/
		waitForResponse();

		Throwable ex = exception;
		SnRpcResponse resp = this.response;
		this.response = null;
		this.exception = null;
		/**如果存在异常那么就跑出异常*/
		if (null != ex) {
			close();
			throw ex;
		}
		/**返回response*/
		return resp;
	}

	/**
	 * 进行连接
	 */
	public void connection() throws Throwable {
		/**如果已经连接了那么返回*/
		if (connected) {
			return;
		}
		/**
		 * 好复杂的感觉....
		 */
		ChannelFactory factory = new NioClientSocketChannelFactory(
				Executors.newCachedThreadPool(),
				Executors.newCachedThreadPool());

		ClientBootstrap bootstrap = new ClientBootstrap(factory);

		bootstrap.setOption("tcpNoDelay", Boolean.parseBoolean(snRpcConfig
				.getProperty("snrpc.tcp.nodelay", "true")));
		bootstrap.setOption("reuseAddress", Boolean.parseBoolean(snRpcConfig
				.getProperty("snrpc.tcp.reuseAddress", "true")));

		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {     //设置pipeline的一些属性balabala的呗
			public ChannelPipeline getPipeline() {
				ChannelPipeline pipeline = Channels.pipeline();
				int readTimeout = snRpcConfig.getReadTimeout();
				if (readTimeout > 0) {
					pipeline.addLast("timeout", new ReadTimeoutHandler(timer,
							readTimeout, TimeUnit.MILLISECONDS));
				}
				pipeline.addLast("decoder", new SnRpcRequestEncoder(
						ProtobufSerializer.getInstance()));
				pipeline.addLast("aggregator", new HttpChunkAggregator(1024*1024));
				pipeline.addLast("encoder", new SnRpcResponseDecoder(
						ProtobufSerializer.getInstance()));
				pipeline.addLast("deflater", new HttpContentCompressor());
				pipeline.addLast("handler", SnNettyRpcConnection.this);
				return pipeline;
			}
		});

		ChannelFuture channelFuture = bootstrap.connect(inetAddr);

		if (!channelFuture.awaitUninterruptibly().isSuccess()) {
			bootstrap.releaseExternalResources();
			throw channelFuture.getCause();
		}

		channel = channelFuture.getChannel();
		connected = true;
	}

	/**
	 * 覆盖了SimpleChannelHandler的 messageReceived的一个方法
	 * 将取得的结果转换为response
	 */
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		response = (SnRpcResponse) e.getMessage();
		synchronized (channel) {
			channel.notifyAll();
		}
	}

	/**
	 *关闭连接
	 */
	public void close() throws Throwable {
		connected = false;
		if (null != timer) {
			timer.stop();
			timer = null;
		}
		/**
		 * 释放资源
		 * 关闭channel
		 */
		if (null != channel) {
			channel.close().awaitUninterruptibly();
			channel.getFactory().releaseExternalResources();

			this.exception = new IOException("connection closed");
			synchronized (channel) {
				channel.notifyAll();
			}
			channel = null;
		}
	}


	/**
	 * 这个waitForResponse是干么的
	 */
	public void waitForResponse() {
		synchronized (channel) {
			try {
				channel.wait();
			} catch (InterruptedException e) {
			}
		}
	}

	/**
	 *返回是否处于连接状态
	 */
	public boolean isConnected() {
		return connected;
	}

	/**
	 *返回是否关闭了
	 */
	public boolean isClosed() {
		return (null == channel) || !channel.isConnected()
				|| !channel.isReadable() || !channel.isWritable();
	}

}
