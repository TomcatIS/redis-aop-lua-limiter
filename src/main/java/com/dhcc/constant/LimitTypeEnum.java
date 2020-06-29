package com.dhcc.constant;

/**
 * 限流类型枚举类
 * @author zhangqi
 * @date 2020/6/27
 */
public enum  LimitTypeEnum {
    /**
     * 自定义限流类型
     */
    CUSTOMER,
    /**
     * 根据IP限流
     */
    IP;
}
