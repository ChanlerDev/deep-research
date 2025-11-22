package dev.chanler.researcher.interfaces.dto.resp;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author: Chanler
 */
@Builder
public class SendMessageRespDTO {
    private String id;
    private String content;
}
