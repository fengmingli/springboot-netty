package cn.lifengming.netty.server;

import cn.lifengming.netty.core.coder.RpcDecoder;
import cn.lifengming.netty.core.coder.RpcEncoder;
import cn.lifengming.netty.core.model.Request;
import cn.lifengming.netty.core.model.Response;
import cn.lifengming.netty.core.serialize.ProtobufSerializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author lifengming
 * @since 2019.10.08
 */
@Component
@Slf4j
public class NettyServer {
    /**
     * 设置服务端口
     */
    private static final Integer SERVER_PORT = 8088;

    /**
     * 负责接受客户端的连接，并将socketChannel交给workEventLoopGroup进行处理，相当于NIO中的selector
     */
    private static EventLoopGroup bossGroup = new NioEventLoopGroup();
    /**
     * 负责IO处理
     */
    private static EventLoopGroup workGroup = new NioEventLoopGroup();

    private static ServerBootstrap serverBootstrap = new ServerBootstrap();

    void startServer() {
        try {
            serverBootstrap.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    //设置过滤器
                    .childHandler(new RpcChannelInitializer())
                    // BACKLOG用于构造服务端套接字ServerSocket对象，
                    // 标识当服务器请求处理线程全满时，用于临时存放已完成三次握手的请求的队列的最大长度。
                    // 如果未设置或所设置的值小于1，Java将使用默认值50
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    //tcp/ip协议参数，测试链接的状态，这个选项用于可能长时间没有数据交流的
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = serverBootstrap.bind(SERVER_PORT).sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 关闭EventLoopGroup，释放掉所有资源包括创建的线程
            workGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    private static class RpcChannelInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline cp = ch.pipeline();
            cp.addLast(new IdleStateHandler(0, 0, 5, TimeUnit.SECONDS));
            //添加 IdleStateHandler 传播的 IdleStateEvent 的处理器
            cp.addLast(new HeartbeatHandler());
            cp.addLast(new RpcDecoder(Request.class, new ProtobufSerializer()));
            cp.addLast(new RpcEncoder(Response.class, new ProtobufSerializer()));
            //添加普通入站处理器
            cp.addLast(new RpcNettyServerHandler());
        }
    }

    private static class HeartbeatHandler extends ChannelInboundHandlerAdapter {
        private static final ByteBuf HEARTBEAT_SEQUENCE = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("HEARTBEAT", Charset.forName("UTF-8")));

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object obj) throws Exception {
            log.info("检测到客户端心跳:{}", new Date());
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
    private static class RpcNettyServerHandler extends SimpleChannelInboundHandler<Request> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx, Request msg) throws Exception {
            //todo  server handler
            //打印接受到的请求
            log.info("接受到客户端的信息:{}", msg);
            //返回一个response
            Response response = Response.builder().state(2).build();
            ctx.writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("rpc server caught exception", cause);
            ctx.close();
        }


        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            log.info("{}:上线", channel.remoteAddress());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            log.info("{}:下线", channel.remoteAddress());
        }

    }
}
