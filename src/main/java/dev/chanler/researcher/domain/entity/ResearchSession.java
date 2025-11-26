package dev.chanler.researcher.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;

/**
 * @author: Chanler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchSession {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;
    private Integer userId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime startTime;
    private LocalDateTime updateTime;
    private LocalDateTime completeTime;
    private Long totalInputTokens;
    private Long totalOutputTokens;
}
