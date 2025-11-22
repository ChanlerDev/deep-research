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
public class WorkflowEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String researchId;
    private String type;
    private String status;
    private String title;
    private String content;
    private Long parentEventId;
    private Integer sequenceNo;
    private LocalDateTime createTime;
}
