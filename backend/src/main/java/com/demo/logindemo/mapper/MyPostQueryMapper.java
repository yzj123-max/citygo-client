package com.demo.logindemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MyPostQueryMapper {

    @Select("""
            SELECT COUNT(*)
            FROM ootd
            WHERE user_id = #{currentUserId}
              AND status = 'PUBLISHED'
            """)
    long countMyPosts(
            @Param("currentUserId") Long currentUserId
    );

    @Select("""
            SELECT id
            FROM ootd
            WHERE user_id = #{currentUserId}
              AND status = 'PUBLISHED'
            ORDER BY create_time DESC, id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            """)
    List<Long> selectMyPostIds(
            @Param("currentUserId") Long currentUserId,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize
    );
}