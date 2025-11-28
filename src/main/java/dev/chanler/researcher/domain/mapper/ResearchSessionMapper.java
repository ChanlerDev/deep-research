package dev.chanler.researcher.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.chanler.researcher.domain.entity.ResearchSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author: Chanler
 */
@Mapper
public interface ResearchSessionMapper extends BaseMapper<ResearchSession> {

    @Select("SELECT * FROM research_session WHERE id = #{id} FOR UPDATE")
    ResearchSession selectByIdForUpdate(@Param("id") String id);

    @Update("""
            <script>
            UPDATE research_session
            SET status = #{status},
                update_time = NOW()
                <if test="setStartTime">, start_time = NOW()</if>
                <if test="setCompleteTime">, complete_time = NOW()</if>
                , total_input_tokens = COALESCE(total_input_tokens, 0) + #{inputTokens}
                , total_output_tokens = COALESCE(total_output_tokens, 0) + #{outputTokens}
            WHERE id = #{id}
            </script>
            """)
    void updateSession(@Param("id") String id, @Param("status") String status,
                       @Param("setStartTime") boolean setStartTime, @Param("setCompleteTime") boolean setCompleteTime,
                       @Param("inputTokens") long inputTokens, @Param("outputTokens") long outputTokens);

    // 后续支持历史研究继续研究
    @Update("""
            UPDATE research_session
            SET status = 'QUEUE', update_time = NOW()
            WHERE id = #{id} AND user_id = #{userId}
              AND status IN ('NEW', 'NEED_CLARIFICATION')
            """)
    int casUpdateToQueue(@Param("id") String id, @Param("userId") Integer userId);
}
