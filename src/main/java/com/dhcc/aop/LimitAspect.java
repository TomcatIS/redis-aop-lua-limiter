package com.dhcc.aop;

import com.dhcc.constant.Limit;
import com.dhcc.constant.LimitTypeEnum;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * @author zhangqi
 * @date 2020/6/27
 */
//@Aspect
//@Configuration
public class LimitAspect {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String UNKNOWN = "unknown";

    @Around("execution(public * *(..)) && @annotation(com.dhcc.constant.Limit)")
    public Object interceptor(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature)joinPoint.getSignature();
        Method method = signature.getMethod();
        // 获取注解
        Limit annotation = method.getAnnotation(Limit.class);
        String key;
        int period = annotation.period();
        int limitCount = annotation.count();
        switch (annotation.limitType()) {
            case IP:
                key = getIpAddress();
                break;
            case CUSTOMER:
                key = annotation.key();
                break;
            default:
                key = StringUtils.upperCase(method.getName());
        }

        try {
            ImmutableList<String> keys = ImmutableList.of(StringUtils.join(annotation.prefix(), key));
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/limit.lua")));
            redisScript.setResultType(Long.class);
            // 获取lua脚本返回值
            // keys:操作的key的数组
            // 第二个参数后面的都属于argv
            Number count = redisTemplate.execute(redisScript, keys, limitCount, period);
            System.out.println(count);
            if (count != null && count.intValue() <= limitCount) {
                return joinPoint.proceed();
            }
            throw new RuntimeException("You have been dragged into the blacklist");
        } catch (Throwable e) {
            if (e instanceof RuntimeException) {
                e.printStackTrace();
            }
            throw new RuntimeException("server exception");
        }
    }

    public String getIpAddress() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            throw new RuntimeException("请求错误！");
        }
        HttpServletRequest request = requestAttributes.getRequest();
        // 源码忽略了大小写
        String ipsStr = request.getHeader("x-forwarded-for");
        // 无代理服务器的情况
        if (org.springframework.util.StringUtils.isEmpty(ipsStr)) {
            return request.getRemoteAddr();
        }
        // 有代理服务器的情况
        String[] ips = ipsStr.split(",");
        return ips[0].trim();
    }

    /*public String getIpAddress() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }*/
}
