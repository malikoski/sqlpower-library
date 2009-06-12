/*
 * Copyright (c) 2008, SQL Power Group Inc.
 *
 * This file is part of SQL Power Library.
 *
 * SQL Power Library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SQL Power Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */
package ca.sqlpower.sqlobject;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.pool.BaseObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool.Config;
import org.apache.log4j.Logger;

import ca.sqlpower.sql.JDBCDataSource;
import ca.sqlpower.sql.JDBCDSConnectionFactory;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.sql.jdbcwrapper.DatabaseMetaDataDecorator;

public class SQLDatabase extends SQLObject implements java.io.Serializable, PropertyChangeListener {
	private static Logger logger = Logger.getLogger(SQLDatabase.class);

	/**
	 * This SPDataSource describes how to connect to the 
	 * physical database that backs this SQLDatabase object.
	 */
	private JDBCDataSource dataSource;

	/**
	 * A pool of JDBC connections backed by a Jakarta Commons DBCP pool.
	 * You should access it only via the getConnectionPool() method.
	 */
	private transient BaseObjectPool connectionPool;
	
	/**
	 * Tells this database that it is being used to back the PlayPen.  Also 
	 * stops removal of children and the closure of connection when properties
	 * change.
	 */
	private boolean playPenDatabase = false;
	
	/**
	 * Indicates the maximum number of connections held active ever.
	 */
	private int maxActiveConnections = 0;
	
	/**
	 * The catalog term for the underlying database, according to the JDBC driver's
	 * database meta data at the time this database object was populated. Null means
	 * the database does not have catalogs.
	 */
	private String catalogTerm;
	
    /**
     * The schema term for the underlying database, according to the JDBC driver's
     * database meta data at the time this database object was populated. Null means
     * the database does not have schemas.
     */
	private String schemaTerm;
	
	/**
	 * Constructor for instances that connect to a real database by JDBC.
	 */
	public SQLDatabase(JDBCDataSource dataSource) {
		setDataSource(dataSource);
		children = new ArrayList();
	}
	
	/**
	 * Constructor for non-JDBC connected instances.
	 */
	public SQLDatabase() {
		children = new ArrayList();
		populated = true;
	}

	public synchronized boolean isConnected() {
		return connectionPool != null;
	}

	protected synchronized void populateImpl() throws SQLObjectException {
	    logger.debug("SQLDatabase: is populated " + populated); //$NON-NLS-1$
		if (populated) return;
		int oldSize = children.size();
		
		logger.debug("SQLDatabase: populate starting"); //$NON-NLS-1$
		
		Connection con = null;
		ResultSet rs = null;
		try {
			con = getConnection();
			DatabaseMetaData dbmd = con.getMetaData();
			
			catalogTerm = dbmd.getCatalogTerm();
			if ("".equals(catalogTerm)) catalogTerm = null; //$NON-NLS-1$
			
			schemaTerm = dbmd.getSchemaTerm();
			if ("".equals(schemaTerm)) schemaTerm = null; //$NON-NLS-1$
			
			List<SQLCatalog> catalogs = SQLCatalog.fetchCatalogs(dbmd);
			for (SQLCatalog cat : catalogs) {
			    cat.setParent(this);
			    children.add(cat);
			}

			// If there were no catalogs, we should look for schemas
			// instead (i.e. this database has no catalogs, and schemas
            // may be attached directly to the database)
			if ( children.size() == oldSize ) {
				List<SQLSchema> schemas = SQLSchema.fetchSchemas(dbmd, null);
				for (SQLSchema schema : schemas) {
				    schema.setParent(this);
				    children.add(schema);
				}
			}
            
            // Finally, look for tables directly under the database (this
            // could be a platform without catalogs or schemas at all)
            if (children.size() == oldSize) {
                List<SQLTable> tables = SQLTable.fetchTablesForTableContainer(dbmd, "", ""); //$NON-NLS-1$ //$NON-NLS-2$
                for (SQLTable table : tables) {
                    table.setParent(this);
                    children.add(table);
                }
            }
            
		} catch (SQLException e) {
			throw new SQLObjectException(Messages.getString("SQLDatabase.populateFailed"), e); //$NON-NLS-1$
		} finally {
			populated = true;
			int newSize = children.size();
			if (newSize > oldSize) {
				int[] changedIndices = new int[newSize - oldSize];
				for (int i = 0, n = newSize - oldSize; i < n; i++) {
					changedIndices[i] = oldSize + i;
				}
				fireDbChildrenInserted(changedIndices, children.subList(oldSize, newSize));
			}
			try {
				if (rs != null ) rs.close();
			} catch (SQLException e2) {
				throw new SQLObjectException(Messages.getString("SQLDatabase.closeRSFailed"), e2); //$NON-NLS-1$
			}
			try {
				if (con != null ) con.close();
			} catch (SQLException e2) {
				throw new SQLObjectException(Messages.getString("SQLDatabase.closeConFailed"), e2); //$NON-NLS-1$
			}
		}
		
		logger.debug("SQLDatabase: populate finished"); //$NON-NLS-1$
	}
	



	public SQLCatalog getCatalogByName(String catalogName) throws SQLObjectException {
		populate();
		if (children == null || children.size() == 0) {
			return null;
		}
		if (! (children.get(0) instanceof SQLCatalog) ) {
			// this database doesn't contain catalogs!
			return null;
		}
		Iterator childit = children.iterator();
		while (childit.hasNext()) {
			SQLCatalog child = (SQLCatalog) childit.next();
			if (child.getName().equalsIgnoreCase(catalogName)) {
				return child;
			}
		}
		return null;
	}

	/**
	 * Searches for the named schema as a direct child of this
	 * database, or as a child of any catalog of this database.
	 *
	 * <p>Note: there may be more than one schema with the given name,
	 * if your RDBMS supports catalogs.  In that case, use {@link
	 * SQLCatalog#getSchemaByName} or write another version of this
	 * method that return an array of SQLSchema.
	 *
	 * @return the first SQLSchema whose name matches the given schema
	 * name.
	 */
	public SQLSchema getSchemaByName(String schemaName) throws SQLObjectException {
		populate();
		if (children == null || children.size() == 0) {
			return null;
		}
		if (! (children.get(0) instanceof SQLSchema || children.get(0) instanceof SQLCatalog) ) {
			// this database doesn't contain schemas or catalogs!
			return null;
		}
		Iterator childit = children.iterator();
		while (childit.hasNext()) {
			SQLObject child = (SQLObject) childit.next();
			if (child instanceof SQLCatalog) {
				// children are tables or schemas
				SQLSchema schema = ((SQLCatalog) child).getSchemaByName(schemaName);
				if (schema != null) {
					return schema;
				}
			} else if (child instanceof SQLSchema) {
				boolean match = (child.getName() == null ?
								 schemaName == null :
								 child.getName().equalsIgnoreCase(schemaName)); 
				if (match) {
					return (SQLSchema) child;
				}
			} else {
				throw new IllegalStateException("Database contains a mix of schemas or catalogs with other objects"); //$NON-NLS-1$
			}
		}
		return null;
	}

	public SQLTable getTableByName(String tableName) throws SQLObjectException {
		return getTableByName(null, null, tableName);
	}

	/**
	 * Searches this database's list of tables for one with the given
	 * name, ignoring case because SQL isn't (usually) case sensitive.
	 *
	 * @param catalogName The name of the catalog to search, or null
	 * if you want to search all catalogs.
	 * @param schemaName The name of the schema to search (in this
	 * database or in the given catalog) or null to search all
	 * schemas.
	 * @param tableName The name of the table to look for (null is not
	 * allowed).
	 * @return the first SQLTable with the given name, or null if no
	 * such table exists.
	 */
	public SQLTable getTableByName(String catalogName, String schemaName, String tableName)
		throws SQLObjectException {

		this.populate();

		if (tableName == null || tableName.length() == 0) {
			throw new NullPointerException("Table Name must be specified"); //$NON-NLS-1$
		}
		
		// we will recursively search a target (database, catalog, or schema)
		SQLObject target = this;
		
		if (catalogName != null && catalogName.length() > 0 ) {
			target = getCatalogByName(catalogName);
		}

		// no such catalog?
		if (target == null) {
		    if (logger.isDebugEnabled())
		        logger.debug("getTableByName("+catalogName+","+schemaName+","+tableName+"): no such catalog!"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			return null;
		}

		if (schemaName != null && schemaName.length() > 0) {
			if (target instanceof SQLDatabase) {
				target = ((SQLDatabase) target).getSchemaByName(schemaName);
			} else if (target instanceof SQLCatalog) {
				target = ((SQLCatalog) target).getSchemaByName(schemaName);
			} else {
				throw new IllegalStateException("Oops, somebody forgot to update this!"); //$NON-NLS-1$
			}
		}

		// no such schema or catalog.schema?
		if (target == null) {
		    if (logger.isDebugEnabled())
		        logger.debug("getTableByName("+catalogName+","+schemaName+","+tableName+"): no such schema!"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			return null;
		}
		target.populate();		
		Iterator childit = target.children.iterator();
		while (childit.hasNext()) {
			SQLObject child = (SQLObject) childit.next();
			if (child instanceof SQLTable) {
				SQLTable table = (SQLTable) child;
				if (table.getName().equalsIgnoreCase(tableName)) {
					return table;
				}
			} else if (child instanceof SQLCatalog) {
				SQLTable table = ((SQLCatalog) child).getTableByName(tableName);
				if (table != null) {
					return table;
				}
			} else if (child instanceof SQLSchema) {
				SQLTable table = ((SQLSchema) child).getTableByName(tableName);
				if (table != null) {
					return table;
				}
			}
		}

		if (logger.isDebugEnabled())
	        logger.debug("getTableByName("+catalogName+","+schemaName+","+tableName+"): catalog and schema ok; no such table!"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		return null;
	}

	// ---------------------- SQLObject support ------------------------

	@Override
	public String getName() {
		if (isPlayPenDatabase()) {
			return Messages.getString("SQLDatabase.playPenDB"); //$NON-NLS-1$
		} else if (dataSource != null) {
		    return dataSource.getDisplayName();
		} else {
		    return Messages.getString("SQLDatabase.disconnected"); //$NON-NLS-1$
		}
	}
	/**
	 * Sets the data source name if the data source is not null
	 */
	@Override
	public void setName(String argName)
	{
		if (dataSource != null) {
			dataSource.setName(argName);
		}
		
	}

	public String getShortDisplayName() {
		return getName();
	}
	
	public boolean allowsChildren() {
		return true;
	}

	/**
	 * Determines whether this SQL object is a container for catalog
	 *
	 * @return true (the default) if there are no children; false if
	 * the first child is not of type SQLCatlog.
	 */
	public boolean isCatalogContainer() throws SQLObjectException {
		if (getParent() != null){
			populate();
		}
			
		if (children.size() == 0) {
			return true;
		} else {
			return (children.get(0) instanceof SQLCatalog);
		}
	}
	
	/**
	 * Determines whether this SQL object is a container for schemas
	 *
	 * @return true (the default) if there are no children; false if
	 * the first child is not of type SQLSchema.
	 */
	public boolean isSchemaContainer() throws SQLObjectException {
		if (getParent() != null){
			populate();
		}
	
		// catalog has been populated
	
		if (children.size() == 0) {
			return true;
		} else {
			return (children.get(0) instanceof SQLSchema);
		}
	}

	// ----------------- accessors and mutators -------------------
	
	/**
	 * Recursively searches this database for SQLTable descendants,
	 * compiles a list of those that were found, and returns that
	 * list.
	 *
	 * <p>WARNING: Calling this method will populate the entire
	 * database!  Think carefully about using it on lazy-loading
	 * source databases (it is safe to use on the playpen database).
	 *
	 * @return the value of tables
	 */
	public List<SQLTable> getTables() throws SQLObjectException {
		return getTableDescendants(this);
	}

	/**
	 * This is the recursive subroutine used by {@link #getTables}.
	 */
	private static List<SQLTable> getTableDescendants(SQLObject o) throws SQLObjectException {

		// this seemingly redundant short-circuit is required because
		// we don't want o.getChildren() to be null
		if (!o.allowsChildren()) return Collections.emptyList();

		List<SQLTable> tables = new LinkedList<SQLTable>();
		Iterator it = o.getChildren().iterator();
		while (it.hasNext()) {
			SQLObject c = (SQLObject) it.next();
			if (c instanceof SQLTable) {
				tables.add((SQLTable) c);
			} else {
				tables.addAll(getTableDescendants(c));
			}
		}
		return tables;
	}

	/**
	 * Gets the value of dataSource
	 *
	 * @return the value of dataSource
	 */
	public JDBCDataSource getDataSource()  {
		return this.dataSource;
	}

	/**
	 * Sets the value of dataSource
	 *
	 * @param argDataSource Value to assign to this.dataSource
	 */
	public void setDataSource(JDBCDataSource argDataSource) {
		SPDataSource oldDataSource = this.dataSource;
		if (dataSource != null) {
			dataSource.removePropertyChangeListener(this);
			reset();
		}
		dataSource = argDataSource;
		dataSource.addPropertyChangeListener(this);		
		fireDbObjectChanged("dataSource",oldDataSource,argDataSource); //$NON-NLS-1$
	}

	public void setPlayPenDatabase(boolean v) {
		boolean oldValue = playPenDatabase;
		playPenDatabase = v;

		if (oldValue != v) {
			fireDbObjectChanged("playPenDatabase", oldValue, v); //$NON-NLS-1$
		}
	}

	public boolean isPlayPenDatabase() {
		return playPenDatabase;
	}

	/**
	 * Removes all children, closes and discards the JDBC connection.  
	 * Unless {@link #playPenDatabase} is true
	 */
	protected synchronized void reset() {
		
		if (playPenDatabase) {
			// preserve the objects that are in the Target system when
            // the connection spec changes
			logger.debug("Ignoring Reset request for: " + getDataSource()); //$NON-NLS-1$
			populated = true;
		} else {
			// discard everything and reload (this is generally for source systems)
			logger.debug("Resetting: " + getDataSource() ); //$NON-NLS-1$
			// tear down old connection stuff
			List old = children;
			if (old != null && old.size() > 0) {
				int[] oldIndices = new int[old.size()];
				for (int i = 0, n = old.size(); i < n; i++) {
					oldIndices[i] = i;
				}
				fireDbChildrenRemoved(oldIndices, old);
				
			}
			children = new ArrayList();
			populated = false;
		}
		
		// destroy connection pool in either case (it still points to the old data source)
		if (connectionPool != null) {
			try {
				connectionPool.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		connectionPool = null;
	}

	/**
	 * Listens for changes in DBCS properties, and resets this
	 * SQLDatabase if a critical property (url, driver, username)
	 * changes.
	 */
	public void propertyChange(PropertyChangeEvent e) {		
		String pn = e.getPropertyName();
		if ( (e.getOldValue() == null && e.getNewValue() != null)
			 || (e.getOldValue() != null && e.getNewValue() == null)
			 || (e.getOldValue() != null && e.getNewValue() != null 
				 && !e.getOldValue().equals(e.getNewValue())) ) {
			if ("url".equals(pn) || "driverClass".equals(pn) || "user".equals(pn)) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				reset();
			} else if ("name".equals(pn)) {				 //$NON-NLS-1$
				fireDbObjectChanged("shortDisplayName",e.getOldValue(),e.getNewValue()); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Returns a JDBC connection to the backing database, if there
	 * is one.  The connection that you get will be yours and only yours
	 * until you call close() on it.  To maximize efficiency of the pool,
	 * try to call close() as soon as you are done with the connection.
	 *
	 * @return an open connection if this database has a valid
	 * dataSource; null if this is a dummy database (such as the
	 * playpen instance).
	 */
	public Connection getConnection() throws SQLObjectException {
		if (dataSource == null) {
			return null;
		} else {
			try {
			    int newActiveCount = getConnectionPool().getNumActive() + 1;
                maxActiveConnections = Math.max(maxActiveConnections,
			            newActiveCount);
			    if (logger.isDebugEnabled()) {
			        logger.debug("getConnection(): giving out active connection " + newActiveCount); //$NON-NLS-1$
			        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
			        	logger.debug(ste.toString());
			        }
			    }
				return (Connection) getConnectionPool().borrowObject();
			} catch (Exception e) {
			    SQLObjectException ex = new SQLObjectException(
			            "Couldn't connect to database: "+e.getMessage(), e); //$NON-NLS-1$
			    setChildrenInaccessibleReason(ex, true);
			    throw new AssertionError("Unreachable code");
			}
		}
	}

	public String toString() {
		return getName();
	}
	
	/**
	 * Closes all connections and other resources that were allocated
	 * by the connect() method.  Logs, but does not propogate, SQL exceptions.
	 */
	public void disconnect() {
		try {
			if (connectionPool != null){
				connectionPool.close();
            }
		} catch (Exception ex) {
			logger.error("Error closing connection pool", ex); //$NON-NLS-1$
		} finally {
			connectionPool = null;
		}
		maxActiveConnections = 0;
	}
	
	synchronized BaseObjectPool getConnectionPool() {
		if (connectionPool == null) {
            Config poolConfig = new GenericObjectPool.Config();
            poolConfig.maxActive = 5;
            poolConfig.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_FAIL;
			connectionPool = new GenericObjectPool(null, poolConfig);
			ConnectionFactory cf = new JDBCDSConnectionFactory(dataSource);			
			new PoolableConnectionFactory(cf, connectionPool, null,
					null, false, true);
		}
		return connectionPool;
	}

	@Override
	public Class<? extends SQLObject> getChildType() {
		if (children.size() == 0){
			return null;
		}
		else{
			return ((SQLObject)children.get(0)).getClass();
		}
	}

	/**
	 * Returns the maximum number of active connections that
	 * this database has ever opened. 
	 * @return Maximum number of active connections ever opened.
	 */
    public int getMaxActiveConnections() {
        return maxActiveConnections;
    }

    /**
     * Re-reads the definition of all populated objects for this entire
     * database. This involves a lot of recursion and re-populating. When
     * SQLObjects are added, removed, or modified as a result of this process,
     * they will fire the appropriate SQLObject events. The entire operation
     * happens in the context of a single compound event.
     * <p>
     * The refresh will not cause any additional SQLObjects to become populated,
     * although it may end up removing some objects that were already populated
     * (because they were dropped in the physical database).
     */
    public void refresh() throws SQLObjectException {
        if (!populated) {
            logger.info("Not refreshing unpopulated database " + getName()); //$NON-NLS-1$
            return;
        }

        DatabaseMetaDataDecorator.putHint(DatabaseMetaDataDecorator.CACHE_STALE_DATE, new Date());

        // We're going to just leave caching on all the time and see how it pans out
        DatabaseMetaDataDecorator.putHint(
                DatabaseMetaDataDecorator.CACHE_TYPE,
                DatabaseMetaDataDecorator.CacheType.EAGER_CACHE);

        Connection con = null;
        try {
            startCompoundEdit(Messages.getString("SQLDatabase.refreshDatabase", getName())); //$NON-NLS-1$

            con = getConnection();
            DatabaseMetaData dbmd = con.getMetaData();
            
            if (catalogTerm != null) {
                logger.debug("refresh: catalogTerm is '"+catalogTerm+"'. refreshing catalogs!"); //$NON-NLS-1$ //$NON-NLS-2$
                List<SQLCatalog> newCatalogs = SQLCatalog.fetchCatalogs(dbmd);
                SQLObjectUtils.refreshChildren(this, newCatalogs);
            } else if (schemaTerm != null) {
                logger.debug("refresh: schemaTerm is '"+schemaTerm+"'. refreshing schemas!"); //$NON-NLS-1$ //$NON-NLS-2$
                List<SQLSchema> newSchemas = SQLSchema.fetchSchemas(dbmd, null);
                SQLObjectUtils.refreshChildren(this, newSchemas);
            }
            
            // close connection before invoking the super refresh,
            // which will probably want to open another one
            con.close();
            con = null;
            
            // bootstrap: if this database is a catalog or schema container, 
            super.refresh();

        } catch (SQLException e) {
            throw new SQLObjectException(Messages.getString("SQLDatabase.refreshFailed"), e); //$NON-NLS-1$
        } finally {
            endCompoundEdit(Messages.getString("SQLDatabase.refreshDatabase", getName())); //$NON-NLS-1$
            
            try {
                if (con != null) con.close();
            } catch (SQLException ex) {
                logger.warn("Failed to close connection. Squishing this exception:", ex); //$NON-NLS-1$
            }
        }
    }
}