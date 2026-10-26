package com.demo.logindemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostCollectQueryMapper {

    @Select("""
            SELECT COUNT(*)
            FROM post_collect c
            INNER JOIN ootd p ON p.id = c.post_id
            WHERE c.user_id = #{currentUserId}
              AND
            """ + PostFeedMapper.VISIBLE_FILTER)
    long countVisible(
            @Param("currentUserId") Long currentUserId
    );

    @Select("""
            SELECT p.id
            FROM post_collect c
            INNER JOIN ootd p ON p.id = c.post_id
            WHERE c.user_id = #{currentUserId}
              AND
            """ + PostFeedMapper.VISIBLE_FILTER + """
            ORDER BY c.create_time DESC, c.id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            """)
    List<Long> selectVisibleIds(
            @Param("currentUserId") Long currentUserId,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize
    );
}
