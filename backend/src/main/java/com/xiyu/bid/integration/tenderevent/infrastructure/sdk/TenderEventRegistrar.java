package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRegisterProperties;
import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRenewalProperties;
import com.ehsy.eventlibrary.clientsdk.config.properties.ServerConfig;
import com.ehsy.eventlibrary.clientsdk.service.component.ClientRegisterComponent;
import com.ehsy.eventlibrary.clientsdk.service.component.ServiceRenewalComponent;
import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

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
 * <p>由 {@link #onApplicationReady()} 监听 {@link ApplicationReadyEvent} 触发。EventBus 注册时会
 * 同步回调本服务的 {@code /api/task/startTask}，因此必须等嵌入式 Web 服务完成启动后再注册；
 * Bean 初始化阶段触发会让回调命中尚未监听的端口并返回 502。
 */
@Slf4j
final class TenderEventRegistrar implements AutoCloseable {

    /**
     * 续约初始延迟（秒）。SDK 调度器以 {@code TimeUnit.SECONDS} 解释该值。
     */
    private static final long RENEWAL_INITIAL_DELAY_SECONDS = 3L;

    /**
     * 续约周期（秒）：每 3 秒心跳一次。
     * SDK 的 scheduleAtFixedRate 单位为秒（字节码 TimeUnit.SECONDS 核实）。
     */
    private static final long RENEWAL_PERIOD_SECONDS = 3L;

    /**
     * 续约有效期（毫秒）：SDK 将该值原样写入续约请求的 renewalDuration 字段。
     */
    private static final long RENEWAL_DURATION_MILLIS = 3000L;

    private final ClientRegisterComponent registerComponent;
    private final ServiceRenewalComponent renewalComponent;
    private volatile Thread registrationThread;

    TenderEventRegistrar(TenderEventSdkProperties properties, Environment environment) {
        Components components = buildComponents(properties, environment);
        this.registerComponent = components.registerComponent();
        this.renewalComponent = components.renewalComponent();
    }

    /**
     * 应用完全就绪后触发服务注册。
     *
     * <p>EventBus 注册接口会同步回调对外宣告地址。使用最低优先级处理
     * {@link ApplicationReadyEvent}，确保嵌入式 Web 服务已经可以接收回调。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onApplicationReady() {
        register();
    }

    /**
     * 触发服务注册。
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
            ScheduledExecutorService scheduler =
                    (ScheduledExecutorService) readField(renewalComponent, "scheduledService");
            scheduler.shutdownNow();
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
    private static Components buildComponents(TenderEventSdkProperties props, Environment environment) {
        // 1. 服务注册属性
        ServiceRegisterProperties regProps = new ServiceRegisterProperties();
        regProps.setServiceName(props.serviceName());
        regProps.setServerRegisterUrl(props.serverRegisterUrl());
        regProps.setEnableRegister(Boolean.TRUE);

        // 2. 续约属性
        ServiceRenewalProperties renewalProps = new ServiceRenewalProperties();
        renewalProps.setInitialDelay(RENEWAL_INITIAL_DELAY_SECONDS);
        renewalProps.setPeriod(RENEWAL_PERIOD_SECONDS);
        renewalProps.setRenewalDuration(RENEWAL_DURATION_MILLIS);

        // 3. ServerConfig（提供 hostAndPort 给注册/续约 body 的 serviceAddress 字段）
        //    SDK 构造器仍从 Environment 读取 server.port；对外宣告地址优先使用标讯专用配置，
        //    缺省复用组织 SDK 已配置的 advertised host/port。
        ServerConfig serverConfig = buildServerConfig(props, environment);

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

        return new Components(registerComponent, renewalComponent);
    }

    /**
     * 基于 Spring Environment 构造 ServerConfig，并覆盖对外宣告的 host/port。
     *
     * <p>SDK 的 {@code getHostAndPort()} 用于注册/续约 body 的 {@code serviceAddress} 字段。
     * EventBus 会同步回连该地址（当前回调为 {@code POST /api/task/startTask}），因此必须宣告
     * EventBus 所在网段可达的 host/port；容器内部端口或不可达地址会让注册与续约长时间阻塞。
     */
    private static ServerConfig buildServerConfig(
            TenderEventSdkProperties properties,
            Environment environment) {
        // host 与 port 独立选择，允许标讯只覆盖其中一个字段，另一个字段继续沿组织配置回退。
        String advertisedHost = firstNonBlank(
                properties.advertisedHost(),
                firstNonBlank(
                        environment.getProperty(
                                "xiyu.integrations.organization.event-sdk.advertised-host", ""),
                        environment.getProperty("xiyu.org.event.advertised-host", "")));
        int advertisedPort = firstPositive(
                properties.advertisedPort(),
                firstPositive(
                        environment.getProperty(
                                "xiyu.integrations.organization.event-sdk.advertised-port", int.class, 0),
                        environment.getProperty("xiyu.org.event.advertised-port", int.class, 0)));
        if (advertisedHost == null || advertisedHost.isBlank() || advertisedPort <= 0) {
            log.warn("标讯事件总线 advertised host/port 未完整配置，将回退本机地址或内部 server.port；"
                    + "EventBus 同步回连可能失败或超时");
        }
        return new AdvertisedServerConfig(environment, advertisedHost, advertisedPort);
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

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "无法从 " + target.getClass().getSimpleName() + " 读取字段 " + fieldName, e);
        }
    }

    private record Components(
            ClientRegisterComponent registerComponent,
            ServiceRenewalComponent renewalComponent) {
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static int firstPositive(int preferred, int fallback) {
        return preferred > 0 ? preferred : fallback;
    }

    private static final class AdvertisedServerConfig extends ServerConfig {
        private final String advertisedHost;
        private final int advertisedPort;

        private AdvertisedServerConfig(
                Environment environment,
                String advertisedHost,
                int advertisedPort) {
            super(environment);
            this.advertisedHost = advertisedHost == null ? "" : advertisedHost.trim();
            this.advertisedPort = advertisedPort;
        }

        @Override
        public String getHostAndPort() {
            String host = advertisedHost.isBlank() ? super.getHost() : advertisedHost;
            int port = advertisedPort > 0 ? advertisedPort : getServerPort();
            return "http://" + host + ":" + port;
        }

        @Override
        public String getHost() {
            return advertisedHost.isBlank() ? super.getHost() : advertisedHost;
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
