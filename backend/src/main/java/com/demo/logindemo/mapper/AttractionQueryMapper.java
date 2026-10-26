package com.demo.logindemo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Mapper
public interface AttractionQueryMapper {

    @SelectProvider(
            type = SqlProvider.class,
            method = "countMatching"
    )
    long countMatching(
            @Param("city") String city,
            @Param("keyword") String keyword,
            @Param("categoryCode") String categoryCode,
            @Param("ticketStatus") String ticketStatus,
            @Param("petPolicy") String petPolicy,
            @Param("now") Date now
    );

    @SelectProvider(
            type = SqlProvider.class,
            method = "selectMatchingIds"
    )
    List<Long> selectMatchingIds(
            @Param("city") String city,
            @Param("keyword") String keyword,
            @Param("categoryCode") String categoryCode,
            @Param("ticketStatus") String ticketStatus,
            @Param("petPolicy") String petPolicy,
            @Param("now") Date now,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize
    );

    class SqlProvider {

        private static final String CURRENT_POLICY = """
                p.status = 'ACTIVE'
                AND p.verified_at IS NOT NULL
                AND p.verified_at <= #{now}
                AND (
                    p.effective_from IS NULL
                    OR p.effective_from <= #{now}
                )
                AND (
                    p.effective_until IS NULL
                    OR p.effective_until > #{now}
                )
                AND (
                    p.effective_from IS NULL
                    OR p.effective_until IS NULL
                    OR p.effective_until > p.effective_from
                )
                AND (
                    p.review_due_at IS NULL
                    OR p.review_due_at > #{now}
                )
                """;

        public String countMatching(
                Map<String, Object> params
        ) {
            return "SELECT COUNT(*) FROM attractions a WHERE "
                    + buildFilter(params);
        }

        public String selectMatchingIds(
                Map<String, Object> params
        ) {
            return "SELECT a.id FROM attractions a WHERE "
                    + buildFilter(params)
                    + " ORDER BY a.sort_weight DESC, a.id DESC"
                    + " LIMIT #{pageSize} OFFSET #{offset}";
        }

        private String buildFilter(
                Map<String, Object> params
        ) {
            StringBuilder sql =
                    new StringBuilder("a.status = 'ACTIVE'");

            if (hasText(params.get("city"))) {
                sql.append(" AND a.city = #{city}");
            }

            if (hasText(params.get("keyword"))) {
                sql.append(
                        " AND INSTR(a.attr_name, #{keyword}) > 0"
                );
            }

            String categoryCode =
                    text(params.get("categoryCode"));

            if (categoryCode != null) {
                if (!categoryCode.matches("\\d{6}")) {
                    throw new IllegalArgumentException(
                            "景点分类编码必须为六位数字"
                    );
                }

                if (categoryCode.endsWith("0000")) {
                    sql.append(
                            " AND a.category_code LIKE "
                                    + "CONCAT(LEFT(#{categoryCode}, 2), '%')"
                    );

                } else if (categoryCode.endsWith("00")) {
                    sql.append(
                            " AND a.category_code LIKE "
                                    + "CONCAT(LEFT(#{categoryCode}, 4), '%')"
                    );

                } else {
                    sql.append(
                            " AND a.category_code = #{categoryCode}"
                    );
                }
            }

            appendPolicyFilter(
                    sql,
                    "TICKET",
                    "ticketStatus",
                    text(params.get("ticketStatus")),
                    Set.of("FREE", "PAID", "PARTIAL", "UNKNOWN"),
                    "'FREE', 'PAID', 'PARTIAL'"
            );

            appendPolicyFilter(
                    sql,
                    "PET",
                    "petPolicy",
                    text(params.get("petPolicy")),
                    Set.of(
                            "ALLOWED",
                            "CONDITIONAL",
                            "NOT_ALLOWED",
                            "UNKNOWN"
                    ),
                    "'ALLOWED', 'CONDITIONAL', 'NOT_ALLOWED'"
            );

            return sql.toString();
        }

        private void appendPolicyFilter(
                StringBuilder sql,
                String policyType,
                String parameterName,
                String requestedValue,
                Set<String> allowedValues,
                String knownValuesSql
        ) {
            if (requestedValue == null) {
                return;
            }

            String value =
                    requestedValue.toUpperCase(Locale.ROOT);

            if (!allowedValues.contains(value)) {
                throw new IllegalArgumentException(
                        "不支持的景点政策筛选值：" + requestedValue
                );
            }

            boolean unknown = "UNKNOWN".equals(value);

            sql.append(
                    unknown
                            ? " AND NOT EXISTS ("
                            : " AND EXISTS ("
            );

            sql.append(
                    "SELECT 1 FROM attraction_policy p"
                            + " WHERE p.attraction_id = a.id"
            );

            // policyType由本类固定传入，不来自用户输入。
            sql.append(" AND p.policy_type = '")
                    .append(policyType)
                    .append("'");

            if (unknown) {
                // knownValuesSql也是本类固定的状态列表。
                sql.append(" AND p.policy_value IN (")
                        .append(knownValuesSql)
                        .append(")");
            } else {
                // 用户提供的值通过MyBatis参数绑定。
                sql.append(" AND p.policy_value = #{")
                        .append(parameterName)
                        .append("}");
            }

            sql.append(" AND ")
                    .append(CURRENT_POLICY)
                    .append(")");
        }

        private boolean hasText(Object value) {
            return text(value) != null;
        }

        private String text(Object value) {
            if (value == null) {
                return null;
            }

            String result = value.toString().trim();

            return result.isEmpty() ? null : result;
        }
    }
}