package cn.lifengming.netty.client;

import cn.lifengming.netty.core.coder.RpcDecoder;
import cn.lifengming.netty.core.coder.RpcEncoder;
import cn.lifengming.netty.core.model.Request;
import cn.lifengming.netty.core.model.Response;
import cn.lifengming.netty.core.serialize.ProtobufSerializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.*;

/**
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
            cp.addLast(new IdleStateHandler(4,5,10));
            cp.addLast(new RpcEncoder(Request.class, new ProtobufSerializer()));
            cp.addLast(new RpcDecoder(Response.class, new ProtobufSerializer()));
            cp.addLast(new RpcClientResponseHandler());
        }
    }

    private static class RpcClientResponseHandler extends SimpleChannelInboundHandler<Response> {

        private int reconnection = 1;
        private static final Integer MAX_RECONNECTION = 5;

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Response msg) throws Exception {
            //todo  client response handler
            System.out.println(msg);
            Request request = Request.builder().name("docker").state(2).build();
            ctx.writeAndFlush(request);
        }

        /**
         * 使用事件触发器发送心跳
         */
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object obj) throws Exception {
            if (obj instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) obj;
                // 如果写通道处于空闲状态,就发送心跳命令
                if (IdleState.WRITER_IDLE.equals(event.state())) {
                    log.info("已经10秒没有接收到客户端的信息了:{}", reconnection);
                    if (reconnection > MAX_RECONNECTION) {
                        log.warn("关闭这个不活跃的channel");
                        ctx.channel().close();
                    }
                    reconnection++;
                }
            }else {
                super.userEventTriggered(ctx, obj);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("rpc client caught exception", cause);
            ctx.close();
        }
    }

}
