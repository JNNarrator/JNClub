package com.jnclub.clipboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.clipboard.entity.ClipboardUserSeq;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 每用户同步游标 Mapper。
 *
 * <p>取号用「行锁 + 批量写回」而不是逐条自增：一次 push 可能带上百条变更，
 * 逐条 {@code UPDATE ... seq = seq + 1} 会产生同样多次往返。
 * {@link #lockSeq} 用 {@code FOR UPDATE} 把本用户的游标行锁住，
 * 调用方在事务内连续分配后再 {@link #updateSeq} 一次写回，
 * 既保证不重号，也把往返压到两次。
 *
 * <p>行不存在时 {@code FOR UPDATE} 锁不到任何东西，所以调用方必须先
 * {@link #insertSeqIfAbsent} 建行，再 {@link #lockSeq}。
 */
@Mapper
public interface ClipboardUserSeqMapper extends BaseMapper<ClipboardUserSeq> {

    /** 建行（已存在则忽略），初值 0 */
    @Insert("""
            INSERT IGNORE INTO t_clipboard_user_seq (user_id, seq) VALUES (#{userId}, 0)
            """)
    int insertSeqIfAbsent(@Param("userId") String userId);

    /** 锁住本用户的游标行并读回当前值 */
    @Select("SELECT seq FROM t_clipboard_user_seq WHERE user_id = #{userId} FOR UPDATE")
    Long lockSeq(@Param("userId") String userId);

    /** 写回分配后的上界 */
    @Update("UPDATE t_clipboard_user_seq SET seq = #{seq} WHERE user_id = #{userId}")
    int updateSeq(@Param("userId") String userId, @Param("seq") long seq);

    /** 只读当前值（pull 用，不加锁） */
    @Select("SELECT seq FROM t_clipboard_user_seq WHERE user_id = #{userId}")
    Long currentSeq(@Param("userId") String userId);

    /**
     * 已落库数据的最大 seq。
     *
     * <p>与 {@link #currentSeq} 的区别：游标行记录的是「已分配的号码上界」，
     * 而批量分配会预留整段区间，冲突、幂等重放、被白名单拒掉的设置都会让区间里留下空洞。
     * 客户端拿游标上界做「是否落后」的判断会永远看到自己落后，
     * 于是反复 pull 空页。这里返回真正存在的最大 seq，空洞不会造成假落后。
     */
    @Select("""
            SELECT COALESCE(GREATEST(
                COALESCE((SELECT MAX(seq) FROM t_clipboard_record WHERE user_id = #{userId}), 0),
                COALESCE((SELECT MAX(seq) FROM t_clipboard_setting WHERE user_id = #{userId}), 0)
            ), 0)
            """)
    Long maxVisibleSeq(@Param("userId") String userId);
}
