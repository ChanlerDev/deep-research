package dev.chanler.researcher.interfaces.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author: Chanler
 */
@Data
@Builder
public class SendMessageRespDTO {
    private String id;
    private String content;
}
