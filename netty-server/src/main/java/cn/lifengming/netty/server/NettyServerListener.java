package cn.lifengming.netty.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * <p>当spring容器加载完之后触发初始化环境<p/>
 *
 * @author lifengming
 * @since 2019.10.09
 */
@Component
public class NettyServerListener implements ApplicationListener<ContextRefreshedEvent> {
    @Autowired
    private NettyServer nettyServer;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        if (contextRefreshedEvent.getApplicationContext().getParent() == null) {
            nettyServer.startServer();
        }
    }
}
