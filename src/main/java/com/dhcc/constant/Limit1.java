package com.dhcc.constant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author zhangqi
 * @date 2020/6/29
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Limit1 {
    LimitTypeEnum limitType() default LimitTypeEnum.IP;

    /**
     * 存入到redis中的key
     */
    String key() default "";

    /**
     * 限流次数
     */
    int count() default 3;

    /**
     * 限流时间，单位（秒）
     */
    int period() default 10;


}
