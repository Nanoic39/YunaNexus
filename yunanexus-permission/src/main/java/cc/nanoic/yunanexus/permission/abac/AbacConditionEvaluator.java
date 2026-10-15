package cc.nanoic.yunanexus.permission.abac;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ABAC 条件求值器。<p>
 *
 * 条件表达式为 JSON 数组，元素形如
 * {@code {"attribute":"ip","operator":"EQ","value":"10.0.0.1"}}；数组内全部条件以 AND 组合，
 * 空条件视为满足。支持的 {@code operator}：{@code EQ} / {@code NE} / {@code IN} /
 * {@code NOT_IN} / {@code EXISTS} / {@code NOT_EXISTS}。<p>
 *
 * {@code attribute} 取自判定请求的 {@code context}（扁平键）；比较统一按字符串进行，
 * {@code IN} / {@code NOT_IN} 的 {@code value} 需为 JSON 数组。
 *
 * @author yunanexus
 */
@Component
public class AbacConditionEvaluator {

    private static final Set<String> OPERATORS =
            Set.of("EQ", "NE", "IN", "NOT_IN", "EXISTS", "NOT_EXISTS");

    private final ObjectMapper objectMapper;

    public AbacConditionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验条件表达式格式（保存策略时调用）。空表达式合法。
     *
     * @param conditions 条件 JSON 字符串
     */
    public void validate(String conditions) {
        if (conditions == null || conditions.isBlank()) {
            return;
        }
        JsonNode root = parse(conditions, "conditions 不是合法 JSON");
        if (!root.isArray()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "conditions 必须是 JSON 数组");
        }
        for (JsonNode condition : root) {
            String attribute = condition.path("attribute").asText(null);
            String operator = condition.path("operator").asText(null);
            if (attribute == null || operator == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "conditions 元素需包含 attribute 与 operator");
            }
            if (!OPERATORS.contains(operator.toUpperCase(Locale.ROOT))) {
                throw new BizException(ErrorCode.BAD_REQUEST, "不支持的 operator: " + operator);
            }
        }
    }

    /**
     * 评估条件是否满足。
     *
     * @param conditions 条件 JSON 字符串（空则视为满足）
     * @param context    属性上下文（可为 null）
     * @return true 表示全部条件满足
     */
    public boolean evaluate(String conditions, Map<String, Object> context) {
        if (conditions == null || conditions.isBlank()) {
            return true;
        }
        JsonNode root = parse(conditions, "conditions 不是合法 JSON");
        if (!root.isArray()) {
            return false;
        }
        for (JsonNode condition : root) {
            if (!matches(condition, context)) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(JsonNode condition, Map<String, Object> context) {
        String attribute = condition.path("attribute").asText(null);
        String operator = condition.path("operator").asText(null);
        if (attribute == null || operator == null) {
            return false;
        }
        Object actual = context == null ? null : context.get(attribute);
        JsonNode expected = condition.get("value");
        String actualText = actual == null ? null : String.valueOf(actual);

        return switch (operator.toUpperCase(Locale.ROOT)) {
            case "EXISTS" -> actual != null;
            case "NOT_EXISTS" -> actual == null;
            case "EQ" -> actualText != null && expected != null && actualText.equals(expected.asText());
            case "NE" -> actualText == null || expected == null || !actualText.equals(expected.asText());
            case "IN" -> actualText != null && expected != null && expected.isArray() && contains(expected, actualText);
            case "NOT_IN" -> actualText == null || expected == null || !expected.isArray()
                    || !contains(expected, actualText);
            default -> false;
        };
    }

    private boolean contains(JsonNode array, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode parse(String conditions, String errorMessage) {
        try {
            return objectMapper.readTree(conditions);
        } catch (JacksonException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, errorMessage);
        }
    }
}
