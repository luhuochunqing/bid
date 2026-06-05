// AOP-based metric collection for clean separation of monitoring concerns
package com.xiyu.bid.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * AOP aspect for automatic metric collection on annotated methods.
 * <p>
 * Usage: annotate service methods with {@code @Timed} or {@code @Counted}
 * to automatically record execution metrics.
 */
@Aspect
@Component
public class MetricAspect {

    private final MeterRegistry registry;

    public MetricAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Automatically times method execution.
     * Usage: Add @Timed to any service method.
     */
    @Around("@annotation(io.micrometer.core.annotation.Timed)")
    public Object timedMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        Timer timer = Timer.builder("xiyu_bid_method_duration_seconds")
                .description("Method execution duration")
                .tag("class", className)
                .tag("method", methodName)
                .register(registry);

        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Automatically counts method invocations.
     * Usage: Add @Counted to any service method.
     */
    @Around("@annotation(io.micrometer.core.annotation.Counted)")
    public Object countedMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        Timer timer = Timer.builder("xiyu_bid_method_calls_total")
                .description("Method invocation count")
                .tag("class", className)
                .tag("method", methodName)
                .register(registry);

        long start = System.nanoTime();
        boolean success = true;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            success = false;
            throw t;
        } finally {
            timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            registry.counter("xiyu_bid_method_invocations_total",
                    "class", className,
                    "method", methodName,
                    "outcome", success ? "success" : "failure"
            ).increment();
        }
    }
}
