package org.apache.sshd.jp.service.impl.assetcommand;

import ch.qos.logback.classic.spi.STEUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.LateralSubSelect;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.sshd.jp.model.entity.AssetOperation;
import org.apache.sshd.jp.model.req.WsMessage;
import org.apache.sshd.jp.service.def.AssetCommandService;
import org.apache.sshd.server.shell.test.Asset;
import org.apache.sshd.server.shell.test.AssetService;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.stream.IntStream;

import static org.apache.sshd.jp.constants.AssetTypeConstants.ASSET_SERVICE_DM;

@Slf4j
@Component(ASSET_SERVICE_DM)
public class DmDatabaseCommandService implements AssetCommandService {

    private static Set<String> DML = new HashSet<>() {{
        add("select");
        add("insert");
        add("delete");
        add("update");
    }};

    @Override
    public AssetOperation parse(WsMessage message) {
        AssetOperation assetOperation = new AssetOperation()
                .setAssetId(message.getAssetId())
                .setOpt(message.getMessage());
        if (isDml(message.getMessage())) {
            try {
                Statement statement = CCJSqlParserUtil.parse(message.getMessage());
                if(statement instanceof Select ps) {
                    ps.getWithItemsList();
                }



            } catch (JSQLParserException e) {
                log.error(e.getMessage(), e);
            }
        }
        return assetOperation;
    }

    private boolean isDml(String message) {
        return DML.stream().filter(act -> message.startsWith(act) || message.startsWith(act.toUpperCase())).findFirst().isPresent();
    }

    @Override
    public void execute(AssetOperation operation) {
        try {
            Asset asset = AssetService.lookupAsset(operation.getAssetId());
            Class.forName("dm.jdbc.driver.DmDriver"); // 加载驱动程序
            String url = "jdbc:dm://%s:%s".formatted(asset.getAddress(), asset.getPort());
            try (Connection connection = DriverManager.getConnection(url, asset.getUsername(), asset.getPassword())) {
                java.sql.Statement statement = connection.createStatement();
                boolean execute = statement.execute(operation.getOpt());
                ResultSet resultSet = statement.getResultSet();
                if (execute) {
                    // 结果集解析（含列名）
                    List<Object> result = new ArrayList<>();
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    // 列名
                    List<String> columns = extractColumns(metaData);
                    result.add(columns);
                    // 列值
                    List<Object> rows = extractRows(resultSet, metaData);
                    result.addAll(rows);
                    operation.setResult(JSON.toJSONString(result));
                } else {
                    if (resultSet.first()) {
                        // update sql
                        long affectLines = resultSet.getLong(0);
                        operation.setResult(String.format("执行成功 , 受影响行数: %s", affectLines));
                    } else {
                        // no result
                        List<String> columns = extractColumns(resultSet.getMetaData());
                        operation.setResult(JSON.toJSONString(Arrays.asList(columns)));
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private List<Object> extractRows(ResultSet resultSet, ResultSetMetaData metaData) throws SQLException {
        List<Object> result = new ArrayList<>();
        while (resultSet.next()) {
            List<Object> values = new ArrayList<>();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                Object value = resolveColumnValue(resultSet, i, metaData.getColumnType(i));
                values.add(value);
            }
            result.add(values);
        }
        return result;
    }

    private static List<String> extractColumns(ResultSetMetaData metaData) throws SQLException {
        List<String> columns = IntStream.range(0, metaData.getColumnCount())
                .mapToObj(i -> {
                    try {
                        return metaData.getColumnName(i + 1);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
        return columns;
    }

    private Object resolveColumnValue(ResultSet resultSet, int i, int columnType) {
        try {
            switch (columnType) {
                case Types.TINYINT:
                case Types.SMALLINT:
                    return resultSet.getShort(i);
                case Types.INTEGER:
                    return resultSet.getInt(i);
                case Types.BIGINT:
                    return resultSet.getLong(i);
                case Types.FLOAT:
                    return resultSet.getFloat(i);
                case Types.DOUBLE:
                    return resultSet.getDouble(i);
                case Types.DECIMAL:
                    return resultSet.getBigDecimal(i);
                case Types.CHAR:
                case Types.VARCHAR:
                case Types.LONGVARCHAR:
                case Types.NCHAR:
                case Types.NVARCHAR:
                case Types.LONGNVARCHAR:
                case Types.CLOB:
                    return resultSet.getString(i);
                case Types.BLOB:
                    return resultSet.getBytes(i);
                case Types.DATE:
                    return resultSet.getDate(i);
                case Types.TIME:
                case Types.TIME_WITH_TIMEZONE:
                    return resultSet.getTime(i);
                case Types.TIMESTAMP:
                case Types.TIMESTAMP_WITH_TIMEZONE:
                    return resultSet.getTimestamp(i);
                case Types.REAL:
                case Types.NUMERIC:
                default:
                    log.info("unknown data type:{}", columnType);
                    return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
