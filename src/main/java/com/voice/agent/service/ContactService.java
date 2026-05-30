package com.voice.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.voice.agent.mapper.ContactMapper;
import com.voice.agent.model.entity.ContactEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ContactService {
    private final ContactMapper contactMapper;

    public ContactService(ContactMapper contactMapper) {
        this.contactMapper = contactMapper;
    }

    public List<ContactEntity> list(Long userId, String keyword) {
        return contactMapper.selectList(
                Wrappers.lambdaQuery(ContactEntity.class)
                        .eq(ContactEntity::getUserId, userId)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(ContactEntity::getName, keyword.trim())
                                .or()
                                .like(ContactEntity::getPhone, keyword.trim())
                                .or()
                                .like(ContactEntity::getEmail, keyword.trim()))
                        .orderByAsc(ContactEntity::getName)
        );
    }

    public ContactEntity resolve(Long userId, String keyword) {
        List<ContactEntity> contacts = list(userId, keyword);
        if (contacts.isEmpty()) {
            throw new IllegalArgumentException("没有找到联系人：" + keyword);
        }
        if (contacts.size() > 1) {
            throw new IllegalArgumentException("联系人不唯一，请提供更准确的姓名：" + keyword);
        }
        return contacts.get(0);
    }

    public ContactEntity save(ContactEntity contact) {
        if (contact == null || contact.getUserId() == null || !StringUtils.hasText(contact.getName())) {
            throw new IllegalArgumentException("联系人姓名和 userId 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        contact.setUpdatedAt(now);
        if (contact.getId() == null) {
            contact.setCreatedAt(now);
            contactMapper.insert(contact);
        } else {
            contactMapper.updateById(contact);
        }
        return contact;
    }

    public boolean delete(Long id, Long userId) {
        return contactMapper.delete(
                Wrappers.lambdaQuery(ContactEntity.class)
                        .eq(ContactEntity::getId, id)
                        .eq(ContactEntity::getUserId, userId)
        ) > 0;
    }
}
