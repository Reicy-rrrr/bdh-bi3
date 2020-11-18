package com.deloitte.bdh.common.interceptor;

import com.alibaba.fastjson.JSON;
import com.deloitte.bdh.common.base.RetResult;
import com.deloitte.bdh.common.exception.BizException;
import com.deloitte.bdh.common.util.JsonUtil;
import com.deloitte.bdh.common.util.ThreadLocalHolder;
import com.deloitte.bdh.common.util.UUIDUtil;
import java.util.Arrays;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.collections4.MapUtils;
import org.apache.http.client.methods.HttpPost;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP记录WEB请求日志
 *
 * @author pengdh
 * @date 2018/04/09
 */
@Aspect
@Component
public class WebLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(WebLogAspect.class);

    @Pointcut("execution(public * com.deloitte.bdh..controller.*.*(..))"
            + "&& !@annotation(com.deloitte.bdh.common.annotation.NoLog)"
            + "&& !@annotation(com.deloitte.bdh.common.annotation.NoLocal)")
    public void logPointCut() {
    }

    @Pointcut("@annotation(com.deloitte.bdh.common.annotation.SystemLog)")
    public void sysLogPointCut() {
    }

    /**
     * 在切入点开始处切入内容
     */
    @Before("logPointCut()")
    public void doBefore(JoinPoint joinPoint) throws Throwable {
        // 接收到请求，记录请求内容
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                .getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        String traceId = UUIDUtil.generate();
        MDC.put("traceId", traceId);
        // 记录下请求内容
        logger.info("请求地址 : " + request.getRequestURL().toString());
        logger.info("HTTP METHOD : " + request.getMethod());
        logger.info("IP : " + request.getRemoteAddr());
        logger.info("CLASS_METHOD : " + joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName());

        if (joinPoint.getArgs().length == 0) {
            logger.info("参数 : {} ", "");
        } else if (request.getMethod().equals(HttpPost.METHOD_NAME)) {
            logger.info("参数 : " + JSON.toJSONString(joinPoint.getArgs()[0]) + "");
        } else {
            logger.info("参数 : " + Arrays.toString(joinPoint.getArgs()));
        }

        ThreadLocalHolder.set("tenantCode", request.getHeader("x-bdh-tenant-code"));
        //设置参数
        if (joinPoint.getArgs().length > 0) {
            Map<String, Object> params = JsonUtil.string2Obj((joinPoint.getArgs()[0]).toString(), Map.class);
            if (null != MapUtils.getString(params, "tenantId")) {
                ThreadLocalHolder.set("tenantId", MapUtils.getString(params, "tenantId"));
            }
            if (null != MapUtils.getString(params, "ip")) {
                ThreadLocalHolder.set("ip", MapUtils.getString(params, "ip"));
            }
            if (null != MapUtils.getString(params, "operator")) {
                ThreadLocalHolder.set("operator", MapUtils.getString(params, "operator"));
            }
        }
    }

    /**
     * 在切入点return内容之后切入内容
     *
     * @param ret returning的值和doAfterReturning的参数名一致
     */
    @AfterReturning(returning = "ret", pointcut = "logPointCut()")
    public void doAfterReturning(Object ret) throws Throwable {
        // 处理完请求，返回内容
        if (ret != null && ret instanceof RetResult) {
            RetResult baseResult = (RetResult) ret;
            String traceId = MDC.get("traceId");
            baseResult.setTraceId(traceId);
        }
        logger.info("返回值 : " + JSON.toJSONStringWithDateFormat(ret, "yyyy-MM-dd HH:mm:ss"));
        MDC.clear();
        ThreadLocalHolder.clear();
    }

}
