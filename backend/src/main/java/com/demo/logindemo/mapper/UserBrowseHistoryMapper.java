package com.demo.logindemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.logindemo.entity.UserBrowseHistory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserBrowseHistoryMapper
        extends BaseMapper<UserBrowseHistory> {

    /**
     * 首次浏览：新增记录，次数为1。
     * 重复浏览：更新最近浏览时间，次数加1。
     * 首次创建时间保持不变。
     */
    @Insert("""
            INSERT INTO user_browse_history (
                user_id,
                target_type,
                target_id,
                view_count,
                last_viewed_at,
                create_time
            )
            VALUES (
                #{userId},
                #{targetType},
                #{targetId},
                1,
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            ON DUPLICATE KEY UPDATE
                view_count = view_count + 1,
                last_viewed_at = CURRENT_TIMESTAMP(3)
            """)
    int recordView(
            @Param("userId") Long userId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId
    );
}
