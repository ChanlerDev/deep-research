package dev.chanler.researcher.interfaces.dto.resp;

import lombok.Data;

/**
 * Google 用户信息响应
 * @author: Chanler
 */
@Data
public class GoogleUserInfoRespDTO {
    private String sub;  // Google OpenID，作为 googleId 存储
}
