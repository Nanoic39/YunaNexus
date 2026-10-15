package cc.nanoic.yunanexus.common.core.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页统一响应数据。<p>
 *
 * 作为 {@link Result} 的 data 字段使用，承载分页查询结果。<br>
 * records：当前页记录列表<br>
 * total：总记录数<br>
 * page：当前页码（从 1 起）<br>
 * size：每页大小<br>
 *
 * @param <T> 记录类型
 * @author yunanexus
 */
@Data
public class PageResult<T> implements Serializable {

    /** 当前页记录列表 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 起） */
    private int page;

    /** 每页大小 */
    private int size;

    /**
     * 构造分页结果
     *
     * @param records 当前页记录
     * @param total   总记录数
     * @param page    当前页码（从 1 起）
     * @param size    每页大小
     * @param <T>     记录类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        PageResult<T> result = new PageResult<>();
        result.records = records;
        result.total = total;
        result.page = page;
        result.size = size;
        return result;
    }

    /**
     * 构造空分页结果，records 为空列表，避免前端空指针
     *
     * @param page 当前页码
     * @param size 每页大小
     * @param <T>  记录类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(int page, int size) {
        PageResult<T> result = new PageResult<>();
        result.records = Collections.emptyList();
        result.total = 0L;
        result.page = page;
        result.size = size;
        return result;
    }
}
