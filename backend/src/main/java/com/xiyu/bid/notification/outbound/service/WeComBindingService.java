// Input: admin binding requests
// Output: User.wecomUserId persistence
// Pos: Service/企微绑定服务（只负责持久化）
package com.xiyu.bid.notification.outbound.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class WeComBindingService {

    private final UserRepository userRepository;

    public WeComBindingService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void bind(Long userId, String wecomUserId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NoSuchElementException("用户不存在: " + userId));
        user.setWecomUserId(wecomUserId);
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("该企微 userid 已绑定其他用户", e);
        }
    }

    @Transactional
    public void unbind(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NoSuchElementException("用户不存在: " + userId));
        user.setWecomUserId(null);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public String currentBinding(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NoSuchElementException("用户不存在: " + userId));
        return user.getWecomUserId();
    }
}
