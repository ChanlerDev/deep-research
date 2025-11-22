package dev.chanler.researcher.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.chanler.researcher.domain.entity.WorkflowEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author: Chanler
 */
@Mapper
public interface WorkflowEventMapper extends BaseMapper<WorkflowEvent> {
}
