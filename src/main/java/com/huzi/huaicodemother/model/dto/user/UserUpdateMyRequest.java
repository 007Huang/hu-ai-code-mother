package com.huzi.huaicodemother.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/** 当前用户资料更新；未传或 null 的字段保持原值。 */
@Data
public class UserUpdateMyRequest implements Serializable {

    private String userName;

    private String userAvatar;

    private String userProfile;

    private static final long serialVersionUID = 1L;
}
