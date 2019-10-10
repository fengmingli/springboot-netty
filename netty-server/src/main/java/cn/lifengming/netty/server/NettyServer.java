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
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
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
            cp.addLast(new RpcDecoder(Request.class, new ProtobufSerializer()));
            cp.addLast(new RpcEncoder(Response.class, new ProtobufSerializer()));
            cp.addLast(new RpcNettyServerHandler());
        }
    }

    @Slf4j
    private static class RpcNettyServerHandler extends SimpleChannelInboundHandler<Request> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Request msg) throws Exception {
            //todo  server handler
            Channel channel = ctx.channel();//获取当前channel
            System.out.println(channel.remoteAddress()); //显示客户端的远程地址
            Response response=new Response();
            response.setRequestId("123456");
            response.setException(null);
            response.setResult(new String("docker"));
            //把响应刷到客户端
            ctx.writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("server caught exception", cause);
            ctx.close();
        }
    }
}
