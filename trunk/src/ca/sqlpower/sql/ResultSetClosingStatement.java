package ca.sqlpower.sql;

import java.sql.*;

/**
 * The ResultSetClosingStatement class helps enforce the documented
 * JDBC behaviour of "when a statement closes, so does its resultset."
 */
public class ResultSetClosingStatement implements Statement {

	/**
	 * The actual statement that performs all the operations.
	 */
	protected Statement actualStatement;

	/**
	 * The connection object that created this statement.
	 */
	protected Connection con;

	/**
	 * The current result set (this implementation allows only one at a time).
	 */
	protected ResultSet results;

	ResultSetClosingStatement(Connection con, Statement actualStmt) {
		this.con=con;
		this.actualStatement=actualStmt;
		this.results=null;
	}
 
    public ResultSet executeQuery(String sql) throws SQLException {
		if(results != null) {
			results.close();
		}
		results=actualStatement.executeQuery(sql);
		return results;
	}
	    
    public int executeUpdate(String sql) throws SQLException {
		return actualStatement.executeUpdate(sql);
	}
	    
    public void close() throws SQLException {
		if(results != null) {
			results.close();
		}
		actualStatement.close();
	}
	    
    public int getMaxFieldSize() throws SQLException {
		return actualStatement.getMaxFieldSize();
	}
        
    public void setMaxFieldSize(int max) throws SQLException {
		actualStatement.setMaxFieldSize(max);
	}
	    
    public int getMaxRows() throws SQLException {
		return actualStatement.getMaxRows();
	}
	    
    public void setMaxRows(int max) throws SQLException {
		actualStatement.setMaxRows(max);
	}
	    
    public void setEscapeProcessing(boolean enable) throws SQLException {
		actualStatement.setEscapeProcessing(enable);
	}
	    
    public int getQueryTimeout() throws SQLException {
		return actualStatement.getQueryTimeout();
	}
	    
    public void setQueryTimeout(int seconds) throws SQLException {
		actualStatement.setQueryTimeout(seconds);
	}
	    
    public void cancel() throws SQLException {
		actualStatement.cancel();
	}
	    
    public SQLWarning getWarnings() throws SQLException {
		return actualStatement.getWarnings();
	}
	    
    public void clearWarnings() throws SQLException {
		actualStatement.clearWarnings();
	}
	    
    public void setCursorName(String name) throws SQLException {
		actualStatement.setCursorName(name);
	}
	    
	/**
	 * Not implemented because we allow only one result set per
	 * statement (at a time).
	 */
    public boolean execute(String sql) throws SQLException {
		throw new UnsupportedOperationException("Not allowed by ResultSetClosingStatement");
	}
	    
    public ResultSet getResultSet() throws SQLException {
		return results;
	}
	    
    public int getUpdateCount() throws SQLException {
		return actualStatement.getUpdateCount();
	}
	    
	/**
	 * Not implemented because we allow only one result set per
	 * statement (at a time).
	 */
    public boolean getMoreResults() throws SQLException {
		throw new UnsupportedOperationException("Not allowed by ResultSetClosingStatement");
	}
	    
    public void setFetchDirection(int direction) throws SQLException {
		actualStatement.setFetchDirection(direction);
	}
	    
    public int getFetchDirection() throws SQLException {
		return actualStatement.getFetchDirection();
	}
	    
    public void setFetchSize(int rows) throws SQLException {
		actualStatement.setFetchSize(rows);
	}
	    
    public int getFetchSize() throws SQLException {
		return actualStatement.getFetchSize();
	}
	    
    public int getResultSetConcurrency() throws SQLException {
		return actualStatement.getResultSetConcurrency();
	}
	    
    public int getResultSetType()  throws SQLException {
		return actualStatement.getResultSetType();
	}
	    
    public void addBatch( String sql ) throws SQLException {
		actualStatement.addBatch(sql);
	}
	    
    public void clearBatch() throws SQLException {
		actualStatement.clearBatch();
	}
	    
   public  int[] executeBatch() throws SQLException {
		return actualStatement.executeBatch();
	}
	    
    public Connection getConnection() throws SQLException {
		return con;
	}

	/**
	 * Closes the result set.
	 */
	public void finalize() {
		try {
			if(results != null) {
				results.close();
			}
		} catch (SQLException e) {
			// haha. we lose.
		}
	}
}	
