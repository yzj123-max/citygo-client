package com.demo.logindemo.mapper;

import com.demo.logindemo.dto.UserSearchResp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRelationMapper {

    // 根据列表类型筛选用户
    String RELATION_FILTER = """
            <choose>
                <when test="type == 'FOLLOWING'">
                    EXISTS (
                        SELECT 1
                        FROM user_follow r
                        WHERE r.follower_id = #{currentUserId}
                          AND r.followee_id = u.id
                    )
                </when>

                <when test="type == 'FOLLOWERS'">
                    EXISTS (
                        SELECT 1
                        FROM user_follow r
                        WHERE r.follower_id = u.id
                          AND r.followee_id = #{currentUserId}
                    )
                </when>

                <when test="type == 'FRIENDS'">
                    EXISTS (
                        SELECT 1
                        FROM user_follow r
                        WHERE r.follower_id = #{currentUserId}
                          AND r.followee_id = u.id
                    )
                    AND EXISTS (
                        SELECT 1
                        FROM user_follow r
                        WHERE r.follower_id = u.id
                          AND r.followee_id = #{currentUserId}
                    )
                </when>

                <otherwise>
                    1 = 0
                </otherwise>
            </choose>
            """;

    @Select("""
            <script>
                SELECT COUNT(*)
                FROM `user` u
                WHERE
            """ + RELATION_FILTER + """
            </script>
            """)
    long countUsers(
            @Param("currentUserId") Long currentUserId,
            @Param("type") String type
    );

    @Select("""
            <script>
                SELECT
                    u.id AS userId,
                    COALESCE(
                        NULLIF(TRIM(u.nickname), ''),
                        CONCAT('用户', u.id)
                    ) AS nickname,
                    u.avatar_url AS avatarUrl,

                    EXISTS (
                        SELECT 1
                        FROM user_follow f
                        WHERE f.follower_id = #{currentUserId}
                          AND f.followee_id = u.id
                    ) AS following,

                    EXISTS (
                        SELECT 1
                        FROM user_follow f
                        WHERE f.follower_id = u.id
                          AND f.followee_id = #{currentUserId}
                    ) AS followedBy,

                    (
                        EXISTS (
                            SELECT 1
                            FROM user_follow f
                            WHERE f.follower_id = #{currentUserId}
                              AND f.followee_id = u.id
                        )
                        AND EXISTS (
                            SELECT 1
                            FROM user_follow f
                            WHERE f.follower_id = u.id
                              AND f.followee_id = #{currentUserId}
                        )
                    ) AS friend

                FROM `user` u
                WHERE
            """ + RELATION_FILTER + """
                ORDER BY u.id DESC
                LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    List<UserSearchResp> selectUsers(
            @Param("currentUserId") Long currentUserId,
            @Param("type") String type,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize
    );
}