/**
 * 审计模块的领域事件包。
 *
 * <p>CO-515：存放实体更新等审计相关事件，由业务模块发布、audit 模块监听。
 * 事件为纯 POJO（record），不依赖 Spring，保持 domain 纯净。
 */
package com.xiyu.bid.audit.event;
