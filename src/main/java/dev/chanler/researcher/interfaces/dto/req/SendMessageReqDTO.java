package dev.chanler.researcher.interfaces.dto.req;

import lombok.Getter;

/**
 * @author: Chanler
 */
@Getter
public class SendMessageReqDTO {
    private String content;
    private String modelName;  // 模型名称
    private String model;      // 模型
    private String baseUrl;    // 用户自定义
    private String apiKey;     // 用户自定义
    private String budget;     // 研究预算级别: MEDIUM, HIGH, ULTRA
}
