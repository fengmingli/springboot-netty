package cn.lifengming.netty.core.model;

import lombok.Builder;
import lombok.Data;

/**
 * @author lifengming
 * @since 2019.10.09
 */
@Data
@Builder
public class Response {
    private String requestId;
    private Exception exception;
    private Object result;
    private Integer state;

    public boolean hasException() {
        return exception != null;
    }
}
