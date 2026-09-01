package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRegisterProperties;
import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRenewalProperties;
import com.ehsy.eventlibrary.clientsdk.config.properties.ServerConfig;
import com.ehsy.eventlibrary.clientsdk.service.component.ClientRegisterComponent;
import com.ehsy.eventlibrary.clientsdk.service.component.ServiceRenewalComponent;
import java.lang.reflect.Field;
import lombok.extern.slf4j.Slf4j;

/**
 * 标讯事件总线注册+续约启动器（WithEffect，基础设施层）。
 *
 * <p>根因修复（2026-08-27）：事件总线要求生产方先通过 {@code POST /eventbus/register}
 * 完成服务注册，并持续通过 {@code POST /eventbus/renew} 保持心跳，才会将 publishEvent
 * 路由到订阅方。此前 TenderEventSdkConfig 故意跳过了 SDKClientConfiguration（避免引入
 * Kafka/ZK 消费侧组件），同时也错误地跳过了注册+续约，导致 bid 服务名从未在线，
 * 网关虽回 {@code code=00000} 但事件被丢弃。
 *
 * <p>本类手工装配 SDK 的 {@code ClientRegisterComponent}（负责注册）和
 * {@code ServiceRenewalComponent}（负责心跳续约），两者均为纯 HTTP 组件，
 * 与 Kafka/ZooKeeper 无关，可安全在生产侧使用。
 *
 * <p>字节码核实的注册 body（{@code POST /eventbus/register}）：
 * {@code {serviceName, serviceAddress, enableRegister}}。
 * 注册成功（{@code code=00000}）后自动启动 ServiceRenewalComponent.start()。
 *
 * <p>由 {@link TenderEventSdkConfig#tenderEventRegistrar} 通过 {@code initMethod="register"}
 * 在 Spring 启动后自动触发，无需手动调用。
 */
@Slf4j
final class TenderEventRegistrar implements AutoCloseable {

    /**
     * 续约初始延迟（秒）：注册成功后等待 10 秒再开始第一次续约，
     * 给网关时间处理注册请求。
     */
    private static final long RENEWAL_INITIAL_DELAY_SECONDS = 10L;

    /**
     * 续约周期（秒）：每 30 秒心跳一次。
     * SDK 的 scheduleAtFixedRate 单位为秒（字节码 TimeUnit.SECONDS 核实）。
     */
    private static final long RENEWAL_PERIOD_SECONDS = 30L;

    /**
     * 续约时长（秒）：告知网关本次续约有效期，60 秒内无续约则视为离线。
     */
    private static final long RENEWAL_DURATION_SECONDS = 60L;

    private final ClientRegisterComponent registerComponent;
    private volatile Thread registrationThread;

    TenderEventRegistrar(TenderEventSdkProperties properties) {
        this.registerComponent = buildRegisterComponent(properties);
    }

    /**
     * 触发服务注册。由 Spring {@code initMethod} 调用，在容器启动后执行。
     *
     * <p>内部循环直到注册成功（{@code code=00000}）再启动续约，符合 SDK
     * {@code ClientRegisterComponent.register()} 的原始实现逻辑（字节码核实）。
     */
    public void register() {
        log.info("标讯事件总线：开始服务注册（serviceName=bid）...");
        registrationThread = Thread.ofVirtual().name("tender-event-register").start(() -> {
            try {
                registerComponent.register();
                log.info("标讯事件总线：服务注册流程已触发，续约线程由 SDK 自动管理");
            } catch (RuntimeException e) {
                log.error("标讯事件总线：服务注册异常", e);
            }
        });
    }

    /**
     * 容器销毁或关闭时释放资源：终止 SDK 注册循环并中断注册线程。
     */
    @Override
    public void close() {
        try {
            injectField(registerComponent, "isRun", Boolean.FALSE);
            if (registrationThread != null && registrationThread.isAlive()) {
                registrationThread.interrupt();
            }
        } catch (RuntimeException e) {
            log.warn("标讯事件总线：停止注册组件异常", e);
        }
    }

    /**
     * 手工装配 {@code ClientRegisterComponent}，通过反射注入三个私有字段：
     * <ul>
     *   <li>{@code registerProperties} — serviceName / serverRegisterUrl / enableRegister</li>
     *   <li>{@code serverConfig} — 提供 serviceAddress（hostAndPort），注册 body 必需</li>
     *   <li>{@code serviceRenewalComponent} — 注册成功后由 register() 自动调用 start()</li>
     * </ul>
     * 均不注册为 Spring bean，避免 {@code @ConfigurationProperties} / {@code @Autowired}
     * 造成容器污染（与 SystemInteractiveServiceImpl 的反射隔离策略一致）。
     */
    private static ClientRegisterComponent buildRegisterComponent(TenderEventSdkProperties props) {
        // 1. 服务注册属性
        ServiceRegisterProperties regProps = new ServiceRegisterProperties();
        regProps.setServiceName(props.serviceName());
        regProps.setServerRegisterUrl(props.serverRegisterUrl());
        regProps.setEnableRegister(Boolean.TRUE);

        // 2. 续约属性
        ServiceRenewalProperties renewalProps = new ServiceRenewalProperties();
        renewalProps.setInitialDelay(RENEWAL_INITIAL_DELAY_SECONDS);
        renewalProps.setPeriod(RENEWAL_PERIOD_SECONDS);
        renewalProps.setRenewalDuration(RENEWAL_DURATION_SECONDS);

        // 3. ServerConfig（提供 hostAndPort 给注册/续约 body 的 serviceAddress 字段）
        //    SDK 构造器依赖 Environment 读 server.port；此处直接设默认值 18080
        //    （与 backend.env SERVER_PORT 一致，serviceAddress 仅供总线内部路由参考）
        ServerConfig serverConfig = buildServerConfig();

        // 4. ServiceRenewalComponent（注册成功后由 ClientRegisterComponent.register() 触发 start()）
        ServiceRenewalComponent renewalComponent = new ServiceRenewalComponent();
        injectField(renewalComponent, "registerProperties", regProps);
        injectField(renewalComponent, "serverConfig", serverConfig);
        injectField(renewalComponent, "renewalProperties", renewalProps);

        // 5. ClientRegisterComponent
        ClientRegisterComponent registerComponent = new ClientRegisterComponent();
        injectField(registerComponent, "registerProperties", regProps);
        injectField(registerComponent, "serverConfig", serverConfig);
        injectField(registerComponent, "serviceRenewalComponent", renewalComponent);
        injectField(registerComponent, "isRun", Boolean.TRUE);

        return registerComponent;
    }

    /**
     * 构造 ServerConfig 并注入 serverPort。
     *
     * <p>SDK 的 {@code getHostAndPort()} 返回 {@code localHost:serverPort}，
     * 用于注册/续约 body 的 {@code serviceAddress} 字段。此值仅供总线内部路由参考，
     * 不影响 publishEvent 的事件路由。
     */
    private static ServerConfig buildServerConfig() {
        // ServerConfig 的唯一构造器需要 Environment，此处用反射绕过
        // 直接构造空对象再设 serverPort 字段
        try {
            ServerConfig cfg = (ServerConfig)
                    sun.misc.Unsafe.class.getDeclaredMethod("allocateInstance", Class.class)
                            .invoke(getUnsafe(), ServerConfig.class);
            injectField(cfg, "serverPort", 18080);
            return cfg;
        } catch (ReflectiveOperationException | IllegalAccessError e) {
            throw new IllegalStateException("无法构造 SDK ServerConfig", e);
        }
    }

    private static Object getUnsafe() throws ReflectiveOperationException {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return f.get(null);
    }

    private static void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            // boolean 原始类型特殊处理
            if (field.getType() == boolean.class && value instanceof Boolean) {
                field.setBoolean(target, (Boolean) value);
            } else {
                field.set(target, value);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "无法向 " + target.getClass().getSimpleName() + " 注入字段 " + fieldName, e);
        }
    }

    /** 向上查找字段，支持父类字段。 */
    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("字段 " + name + " 在 " + clazz.getName() + " 及其父类中均未找到");
    }
}
