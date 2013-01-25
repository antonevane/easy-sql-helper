package com.github.snowindy.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public class SQLUtils {

    private final static String CLASS_NAME = SQLUtils.class.getName();

    private final static Logger logger = Logger.getLogger(CLASS_NAME);

    private static final Map<String, DataSource> cachedDataSources = new ConcurrentHashMap<String, DataSource>();

    public static void cleanUpDatabaseResources(ResultSet rs, Statement stmt) throws SQLException {
        freeDatabaseResources(rs, stmt, null);
    }

    public static void cleanUpDatabaseResources(ResultSet rs, Statement stmt, Connection conn) throws SQLException {
        freeDatabaseResources(rs, stmt, conn);
    }

    public static void cleanUpDatabaseResources(Statement stmt, Connection conn) throws SQLException {
        freeDatabaseResources(null, stmt, conn);
    }

    private static void freeDatabaseResources(ResultSet rs, Statement stmt, Connection conn) throws SQLException {
        String methodName = "freeDatabaseResources(ResultSet, Statement, Connection)";
        try {
            cleanUpResultSet(rs);
        } catch (SQLException sqle) {
            logger.logp(Level.SEVERE, CLASS_NAME, methodName, "\nError occured during ResultSet close!", sqle);
        }

        try {
            cleanUpStatement(stmt);
        } catch (SQLException sqle) {
            logger.logp(Level.SEVERE, CLASS_NAME, methodName, "\nError occured during PreparedStatement close!", sqle);
        }

        try {
            cleanUpDatabaseConnection(conn);
        } catch (SQLException sqle) {
            logger.logp(Level.SEVERE, CLASS_NAME, methodName, "\nError occured during Connection close!", sqle);
            throw sqle;
        }

    }

    public static void rollbackQuietly(Connection conn) {
        try {
            rollback(conn);
        } catch (Exception e) {
        }
    }

    public static void rollbackRE(Connection conn) {
        try {
            rollback(conn);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void rollback(Connection conn) throws Exception {
        final String methodName = "rollback";
        try {
            if (conn != null){
                conn.rollback();   
            }
        } catch (Exception sqle) {
            logger.logp(Level.SEVERE, CLASS_NAME, methodName, "\nError occured during transaction rollback.", sqle);
            throw sqle;
        }
    }

    public static void cleanUpDatabaseConnection(Connection conn) throws SQLException {
        String methodName = "cleanUpDatabaseConnection(Connection)";
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    try {
                        conn.clearWarnings();
                    } catch (SQLException sqle) {
                        logger.logp(Level.SEVERE, CLASS_NAME, methodName,
                                "\nError occured during Connection warning clear!", sqle);
                    }

                    conn.close();
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer("\nConnection has been closed [ID] = " + conn.hashCode());
                    }
                }
                conn = null;
            } catch (SQLException sqle) {
                logger.logp(Level.SEVERE, CLASS_NAME, methodName, "\nError occured during Connection close!", sqle);
                throw sqle;
            }
        }

    }

    public static void cleanUpResultSet(ResultSet rset) throws SQLException {
        String methodName = "cleanUpResultSet(ResultSet)";
        if (rset != null) {
            try {
                try {
                    rset.clearWarnings();
                } catch (SQLException sqle) {
                    logger.logp(Level.SEVERE, CLASS_NAME, methodName,
                            "\nError occured during ResultSet warning clear!", sqle);
                }

                rset.close();
                rset = null;
            } catch (SQLException sqle) {
                logger.logp(Level.SEVERE, CLASS_NAME, methodName, "\nError occured during ResultSet close!", sqle);
                throw sqle;
            }
        }
    }

    public static void cleanUpBatch(Statement stmt) throws SQLException {
        String methodName = "cleanUpStatement(Statement)";
        if (stmt != null) {
            try {
                stmt.clearBatch();
            } catch (SQLException sqle) {
                logger.logp(Level.SEVERE, CLASS_NAME, methodName, "\nError occured during Statement Batch clear!", sqle);
            }
        }
    }

    public static void cleanUpStatement(Statement stmt) throws SQLException {
        String methodName = "cleanUpStatement(Statement)";
        if (stmt != null) {
            try {
                try {
                    stmt.clearWarnings();
                } catch (SQLException sqle) {
                    logger.logp(Level.SEVERE, CLASS_NAME, methodName,
                            "\nError occured during Statement warning clear!", sqle);
                }

                stmt.close();
                stmt = null;
            } catch (SQLException sqle) {
                logger.logp(Level.SEVERE, CLASS_NAME, methodName, "\nError occured during Statement close!", sqle);
                throw sqle;
            }
        }
    }

    private static Connection createConnection(String dataSourceName) throws NamingException, SQLException {
        DataSource datasource = cachedDataSources.get(dataSourceName);
        if (datasource == null) {
            Context ctx = new InitialContext();
            datasource = (DataSource) ctx.lookup(dataSourceName);
            cachedDataSources.put(dataSourceName, datasource);

            ctx.close();
        }
        return datasource.getConnection();
    }

    /*
     * Getting connection to a database
     */
    public static Connection getConnection(String dataSourceName) throws NamingException, SQLException {
        String methodName = "getConnection(String)";
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(CLASS_NAME, methodName, "\n[DATASOURCE_NAME] = '" + dataSourceName + "'");
        }

        Connection connection = null;

        try {
            connection = createConnection(dataSourceName);
        } catch (NamingException e) {
            logger.logp(Level.SEVERE, CLASS_NAME, methodName, "\nDataSource not found with name = " + dataSourceName
                    + "'", e);
            throw e;
        } catch (SQLException e) {
            logger.logp(Level.SEVERE, CLASS_NAME, methodName, "\nError occured during connection create!", e);
            throw e;
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(CLASS_NAME, methodName, "\nConnection has been created [ID] = '" + connection.hashCode()
                    + "'");
        }
        return connection;
    }

    /**
     * 
     * @param accList
     * @return string similar to "in (?, ?, ?)", number of "?" equals size.
     */
    public static String generateINForPreparedStatement(int size) {

        StringBuilder innerStr = new StringBuilder();
        innerStr.append(" in ( ");
        int sizeMinOne = size - 1;
        for (int i = 0; i < size; i++) {
            innerStr.append(i != sizeMinOne ? "?, " : "?");
        }
        innerStr.append(" ) ");
        return innerStr.toString();
    }

    public static StringBuilder buildSql(CharSequence... sqlParts) {
        StringBuilder sql = new StringBuilder();
        for (CharSequence sqlPart : sqlParts) {
            sql.append(sqlPart);
        }
        return sql;
    }

    public static void cleanUpStatementBatchQuietly(PreparedStatement pstm) {
        try {
            cleanUpStatementBatch(pstm);
        } catch (Exception e) {
        }
    }

    public static void cleanUpStatementBatchRE(PreparedStatement pstm) {
        try {
            cleanUpStatementBatch(pstm);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void cleanUpStatementBatch(PreparedStatement pstm) throws SQLException {
        cleanUpBatch(pstm);
        cleanUpStatement(pstm);
    }
}
