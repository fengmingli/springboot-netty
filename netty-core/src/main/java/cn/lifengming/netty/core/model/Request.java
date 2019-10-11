package cn.lifengming.netty.core.model;

import com.sun.javafx.image.impl.IntArgb;
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
public class Request {
    String name;
    String requestId;
    Integer age;
    Integer state;
}
