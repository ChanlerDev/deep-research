package dev.chanler.researcher.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Long id;
    private String researchId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime completeTime;
}
