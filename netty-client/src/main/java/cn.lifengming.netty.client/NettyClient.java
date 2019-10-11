package cn.lifengming.netty.client;

import cn.lifengming.netty.core.coder.RpcDecoder;
import cn.lifengming.netty.core.coder.RpcEncoder;
import cn.lifengming.netty.core.model.Request;
import cn.lifengming.netty.core.model.Response;
import cn.lifengming.netty.core.serialize.ProtobufSerializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.*;

/**
 * <p>
 * channelActive()——在到服务器的连接已经建立之后将被调用；
 * channelRead0()——当从服务器接收到一条消息时被调用；
 * exceptionCaught()——在处理过程中引发异常时被调用。
 * <p/>
 *
 * @author lifengming
 * @since 2019.10.08
 */
@Slf4j
@Component
public class NettyClient {
    private static final String IP = "127.0.0.1";
    private static final Integer SERVER_PORT = 8088;
    private ExecutorService cachedThreadPool = newCachedThreadPool();

    /**
     * 通过nio方式来接收连接和处理连接
     */
    private EventLoopGroup workGroup = new NioEventLoopGroup();
    /**
     * 唯一标记
     */
    private boolean initFlag = true;


    void startClientServer() {
        for (int i = 0; i < 3; i++) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            cachedThreadPool.execute(() -> {
                log.info("client Thread running: {}", Thread.currentThread().getName());
                doConnect(new Bootstrap(), workGroup);
            });
        }
    }

    /**
     * 连接服务端
     */
    private void doConnect(Bootstrap bootstrap, EventLoopGroup workGroup) {
        ChannelFuture channelFuture = null;
        try {
            if (bootstrap != null) {
                bootstrap.group(workGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new RpcChannelInitializer())
                        .option(ChannelOption.SO_KEEPALIVE, true);

                channelFuture = bootstrap.connect(new InetSocketAddress(IP, SERVER_PORT));
                channelFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        final EventLoop eventLoop = future.channel().eventLoop();
                        if (!future.isSuccess()) {
                            log.info("Disconnect from the server, reconnect after 10s!");
                            eventLoop.schedule(() -> doConnect(new Bootstrap(), eventLoop), 10, TimeUnit.SECONDS);
                        }
                    }
                });

                if (initFlag) {
                    log.info("netty client start success!");
                    initFlag = false;
                }
                // 阻塞
                channelFuture.channel().closeFuture().sync();
            }
        } catch (Exception e) {
            log.error("Client connection failed:{}", e.getMessage());
        }

    }

    private static class RpcChannelInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline cp = ch.pipeline();
            cp.addLast(new IdleStateHandler(0, 0, 5, TimeUnit.SECONDS));
            cp.addLast(new HeartbeatHandler());
            cp.addLast(new RpcEncoder(Request.class, new ProtobufSerializer()));
            cp.addLast(new RpcDecoder(Response.class, new ProtobufSerializer()));
            cp.addLast(new RpcClientResponseHandler());
        }
    }

    @Slf4j
    private static class HeartbeatHandler extends ChannelInboundHandlerAdapter {
        private static final ByteBuf HEARTBEAT_SEQUENCE = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("HEARTBEAT", Charset.forName("UTF-8")));

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object obj) throws Exception {
            log.info("检测到服务端心跳:{}", new Date());
            if (obj instanceof IdleStateEvent) {
                //当捕获到 IdleStateEvent，发送心跳到远端，并添加一个监听器，如果发送失败就关闭服务端连接
                ctx.writeAndFlush(HEARTBEAT_SEQUENCE.duplicate()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            } else {
                //如果捕获到的时间不是一个 IdleStateEvent，就将该事件传递到下一个处理器
                super.userEventTriggered(ctx, obj);
            }
        }

    }

    @ChannelHandler.Sharable
    private static class RpcClientResponseHandler extends SimpleChannelInboundHandler<Response> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx, Response msg) throws Exception {
            //todo  client response handler
            try {
                log.info("receive data{}:", msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("rpc client caught exception", cause);
            ctx.close();
        }

        /**
         * 在到服务器的连接已经建立之后将被调用
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("Established connection with the remote Server.");
            Request docker = Request.builder().name("docker").age(20).build();
            ctx.writeAndFlush(docker);
        }
    }
}
