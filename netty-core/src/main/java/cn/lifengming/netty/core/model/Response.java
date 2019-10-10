package cn.lifengming.netty.core.model;

import lombok.Data;

/**
 * @author lifengming
 * @since 2019.10.09
 */
@Data
public class Response {
    private String requestId;
    private Exception exception;
    private Object result;

    public boolean hasException() {
        return exception != null;
    }
}
