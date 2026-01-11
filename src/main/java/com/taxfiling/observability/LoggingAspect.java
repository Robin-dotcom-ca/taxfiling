package com.taxfiling.observability;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Aspect for logging method execution in service and controller layers.
 * Provides automatic entry/exit logging with timing information.
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    /**
     * Pointcut for all service methods
     */
    @Pointcut("within(com.taxfiling.service..*)")
    public void serviceLayer() {}

    /**
     * Pointcut for all controller methods
     */
    @Pointcut("within(com.taxfiling.controller..*)")
    public void controllerLayer() {}

    /**
     * Pointcut for repository methods (for debugging)
     */
    @Pointcut("within(com.taxfiling.repository..*)")
    public void repositoryLayer() {}

    /**
     * Log service method execution with timing
     */
    @Around("serviceLayer()")
    public Object logServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "SERVICE");
    }

    /**
     * Log controller method execution with timing
     */
    @Around("controllerLayer()")
    public Object logControllerMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "CONTROLLER");
    }

    private Object logMethodExecution(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethod = className + "." + methodName;

        // Log entry
        if (log.isDebugEnabled()) {
            log.debug("[{}] Entering {} with args: {}",
                    layer, fullMethod, summarizeArgs(joinPoint.getArgs()));
        }

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            // Log successful exit
            if (log.isDebugEnabled()) {
                log.debug("[{}] Exiting {} - duration={}ms, result={}",
                        layer, fullMethod, duration, summarizeResult(result));
            } else if (duration > 1000) {
                // Always log slow operations
                log.warn("[{}] Slow operation {} - duration={}ms",
                        layer, fullMethod, duration);
            }

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[{}] Exception in {} after {}ms: {} - {}",
                    layer, fullMethod, duration, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    private String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        return Arrays.stream(args)
                .map(this::summarizeObject)
                .toList()
                .toString();
    }

    private String summarizeResult(Object result) {
        return summarizeObject(result);
    }

    private String summarizeObject(Object obj) {
        if (obj == null) {
            return "null";
        }

        String className = obj.getClass().getSimpleName();

        // Avoid logging sensitive data
        if (className.contains("Password") || className.contains("Secret") ||
            className.contains("Token") || className.contains("Credential")) {
            return className + "[REDACTED]";
        }

        // Summarize collections
        if (obj instanceof java.util.Collection<?> coll) {
            return className + "[size=" + coll.size() + "]";
        }

        // Summarize pages
        if (obj instanceof org.springframework.data.domain.Page<?> page) {
            return "Page[content=" + page.getNumberOfElements() +
                   ", total=" + page.getTotalElements() + "]";
        }

        // For simple types, show value; for complex types, show class name
        if (obj instanceof String || obj instanceof Number ||
            obj instanceof Boolean || obj instanceof java.util.UUID) {
            String value = obj.toString();
            if (value.length() > 50) {
                return value.substring(0, 47) + "...";
            }
            return value;
        }

        return className;
    }
}
