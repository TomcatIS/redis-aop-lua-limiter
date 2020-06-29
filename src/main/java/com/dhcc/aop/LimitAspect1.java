package com.dhcc.aop;

import com.dhcc.constant.Limit;
import com.dhcc.constant.LimitTypeEnum;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhangqi
 * @date 2020/6/28
 */
@Aspect
@Component
public class LimitAspect1 {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Around("execution(public * com.dhcc.controller..*.*(..)) && @annotation(com.dhcc.constant.Limit)")
    public Object interceptor(ProceedingJoinPoint joinPoint) {
        logger.info("开始执行前置方法");
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        Limit limitAnnotation = method.getAnnotation(Limit.class);
        LimitTypeEnum limitType = limitAnnotation.limitType();
        int limitCount = limitAnnotation.count();
        int limitPeriod = limitAnnotation.period();
        String key;
        switch (limitType) {
            case CUSTOMER:
                key = limitAnnotation.key();
                break;
            case IP:
                key = getIpAddress();
                break;
            default:
                key = method.getName();
        }
        int curCount = executeLuaScript(key, limitCount, limitPeriod);
        if (curCount <= limitCount) {
            try {
                logger.info("放行目标方法");
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new RuntimeException("目标方法执行失败");
            }
        }
        throw new RuntimeException("当前服务器正忙，请稍后重试!");

    }

    public int executeLuaScript(String key, int limitCount, int limitPeriod) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setResultType(Long.class);
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/limit.lua")));
        List<String> keyList = new ArrayList<>(1);
        keyList.add(key);
        // 只能设置为number类型，其它类型接收肯能会报错
        Number curCount;
        try {
            curCount = redisTemplate.execute(redisScript, keyList, limitCount, limitPeriod);
            if (curCount != null) {
                return curCount.intValue();
            }
            throw new RuntimeException("执行lua脚本返回值为空");
        } catch (Exception e) {
            throw new RuntimeException("lua脚本执行失败!===>" + e.getMessage());
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
        if (StringUtils.isEmpty(ipsStr)) {
            return request.getRemoteAddr();
        }
        // 有代理服务器的情况
        String[] ips = ipsStr.split(",");
        return ips[0].trim();
    }
}
