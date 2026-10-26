package com.demo.logindemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostFeedMapper {

    String VISIBLE_FILTER = """
            p.status = 'PUBLISHED'
            AND (
                p.user_id = #{currentUserId}

                OR p.visibility = 'PUBLIC'

                OR (
                    p.visibility = 'ALLOW_LIST'
                    AND EXISTS (
                        SELECT 1
                        FROM post_audience a
                        WHERE a.post_id = p.id
                          AND a.user_id = #{currentUserId}
                    )
                )

                OR (
                    p.visibility = 'DENY_LIST'
                    AND NOT EXISTS (
                        SELECT 1
                        FROM post_audience a
                        WHERE a.post_id = p.id
                          AND a.user_id = #{currentUserId}
                    )
                )

                OR (
                    p.visibility = 'FRIENDS'
                    AND EXISTS (
                        SELECT 1
                        FROM user_follow f
                        WHERE f.follower_id = #{currentUserId}
                          AND f.followee_id = p.user_id
                    )
                    AND EXISTS (
                        SELECT 1
                        FROM user_follow f
                        WHERE f.follower_id = p.user_id
                          AND f.followee_id = #{currentUserId}
                    )
                )
            )
            """;

    @Select("""
            SELECT COUNT(*)
            FROM ootd p
            WHERE
            """ + VISIBLE_FILTER)
    long countVisible(
            @Param("currentUserId") Long currentUserId
    );

    @Select("""
            SELECT p.id
            FROM ootd p
            WHERE
            """ + VISIBLE_FILTER + """
            ORDER BY p.create_time DESC, p.id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            """)
    List<Long> selectVisibleIds(
            @Param("currentUserId") Long currentUserId,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize
    );
}
