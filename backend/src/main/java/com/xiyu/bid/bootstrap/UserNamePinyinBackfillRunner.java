// Input: users 表中 full_name_pinyin 为 NULL 且 full_name 非空的存量行
// Output: 启动时一次性回填 full_name_pinyin（PinyinUtils.toPinyin 计算），幂等
// Pos: bootstrap - 启动期回填，注入 Repository（规避 ArchitectureTest RULE 9 的 config→service 依赖）
package com.xiyu.bid.bootstrap;

import com.xiyu.bid.common.util.PinyinUtils;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启动期回填 users.full_name_pinyin。
 *
 * <p>V1099 恢复了 full_name_pinyin 列，但 MySQL 无法在迁移脚本里调用 pinyin4j 做汉字转拼音。
 * 本 runner 在应用启动时，对 full_name_pinyin 为空且 full_name 非空的存量行，用
 * {@link PinyinUtils#toPinyin(String)} 计算并回填。幂等：只处理 null 行，重复启动不重复计算。
 *
 * <p>新建/修改用户的回填由 {@link User} 的 @PrePersist/@PreUpdate 负责，本 runner 只覆盖
 * 迁移前的存量数据。
 */
@Component
@Profile({"dev", "prod", "mysql"})
@RequiredArgsConstructor
@Slf4j
public class UserNamePinyinBackfillRunner implements ApplicationRunner {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> pending = userRepository.findByFullNamePinyinNullAndFullNameNotNull();
        if (pending.isEmpty()) {
            return;
        }
        log.info("Backfilling full_name_pinyin for {} users", pending.size());
        int updated = 0;
        for (User user : pending) {
            String pinyin = PinyinUtils.toPinyin(user.getFullName());
            user.setFullNamePinyin(pinyin);
            userRepository.save(user);
            updated++;
        }
        log.info("full_name_pinyin backfill complete: {} users updated", updated);
    }
}
