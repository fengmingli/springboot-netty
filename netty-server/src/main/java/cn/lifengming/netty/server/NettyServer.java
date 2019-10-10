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
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author lifengming
 * @since 2019.10.08
 */
@Component
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

    public void startServer() {
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
            cp.addLast(new IdleStateHandler(4,5,10));
            cp.addLast(new RpcDecoder(Request.class, new ProtobufSerializer()));
            cp.addLast(new RpcEncoder(Response.class, new ProtobufSerializer()));
            cp.addLast(new RpcNettyServerHandler());
        }
    }

    @Slf4j
    private static class RpcNettyServerHandler extends SimpleChannelInboundHandler<Request> {

        private static ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

        private int reconnection = 1;
        private static final Integer MAX_RECONNECTION = 5;
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Request msg) throws Exception {
            //todo  server handler
            //接受到的请求
            System.out.println(msg);
            //返回一个response
            Response response = Response.builder().state(8888).build();
            ctx.writeAndFlush(response);
        }

        /**
         * 超时处理 如果5秒没有接受客户端的心跳，就触发; 如果超过两次，则直接关闭;
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
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("rpc server caught exception", cause);
            ctx.close();
        }


        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            System.out.println(channel.remoteAddress() + "上线");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            System.out.println(channel.remoteAddress() + "下线");
        }

    }
}
