package com.feng.system.module.image.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL JSONB 字段类型处理器。
 * 写入时以 Types.OTHER 传参使驱动按 jsonb 处理,读取时直接取字符串。
 */
public class JsonbTypeHandler extends BaseTypeHandler<String> {
    /**
     * 以 Types.OTHER 写入参数,使 PostgreSQL 驱动将字符串识别为 jsonb 类型。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override public String getNullableResult(ResultSet rs, String columnName) throws SQLException { return rs.getString(columnName); }
    @Override public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException { return rs.getString(columnIndex); }
    @Override public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException { return cs.getString(columnIndex); }
}
