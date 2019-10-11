package cn.lifengming.netty.core.model;

import lombok.*;

/**
 * @author lifengming
 * @since 2019.10.09
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@ToString
public class Response {
    private String requestId;
    private Exception exception;
    private Object result;
    private Integer state;

    public boolean hasException() {
        return exception != null;
    }
}
