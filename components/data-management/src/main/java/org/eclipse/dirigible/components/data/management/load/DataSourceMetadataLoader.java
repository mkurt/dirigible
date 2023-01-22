/*
 * Copyright (c) 2022 SAP SE or an SAP affiliate company and Eclipse Dirigible contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-FileCopyrightText: 2022 SAP SE or an SAP affiliate company and Eclipse Dirigible contributors
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.dirigible.components.data.management.load;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.eclipse.dirigible.commons.config.Configuration;
import org.eclipse.dirigible.components.data.sources.manager.DataSourcesManager;
import org.eclipse.dirigible.components.data.structures.domain.Table;
import org.eclipse.dirigible.components.data.structures.domain.TableColumn;
import org.eclipse.dirigible.components.data.structures.domain.TableConstraint;
import org.eclipse.dirigible.components.data.structures.domain.TableConstraintCheck;
import org.eclipse.dirigible.components.data.structures.domain.TableConstraintForeignKey;
import org.eclipse.dirigible.components.data.structures.domain.TableConstraintUnique;
import org.eclipse.dirigible.components.database.DatabaseParameters;
import org.eclipse.dirigible.database.sql.ISqlKeywords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.CaseFormat;


/**
 * The Class DataSourceMetadataLoader.
 */
@Component
public class DataSourceMetadataLoader implements DatabaseParameters {
	
	/** The Constant logger. */
	private static final Logger logger = LoggerFactory.getLogger(DataSourceMetadataLoader.class);

    /** The Constant IS_CASE_SENSETIVE. */
    private static final boolean IS_CASE_SENSETIVE = Boolean.parseBoolean(Configuration.get(DatabaseParameters.DIRIGIBLE_DATABASE_NAMES_CASE_SENSITIVE));

    /** The data source service. */
	private final DataSourcesManager datasourceManager;

	/**
	 * Instantiates a new data source endpoint.
	 *
	 * @param datasourceManager the datasource manager
	 */
	@Autowired
	public DataSourceMetadataLoader(DataSourcesManager datasourceManager) {
		this.datasourceManager = datasourceManager;
	}
    
    /**
     * Gets the table metadata.
     *
     * @param tableName the table name
     * @return the table metadata
     * @throws SQLException the SQL exception
     */
    public Table getTableMetadata(String tableName) throws SQLException {
        return getTableMetadata(tableName, null);
    }
    
    /**
     * Gets the table metadata.
     *
     * @param tableName the table name
     * @param schemaName the schema name
     * @return the table metadata
     * @throws SQLException the SQL exception
     */
    public Table getTableMetadata(String tableName, String schemaName) throws SQLException {
    	return loadTableMetadata(tableName, schemaName, null);
    }

    /**
     * Gets the table metadata.
     *
     * @param schemaName the schema name
     * @param tableName the table name
     * @param dataSource the data source
     * @return the table metadata
     * @throws SQLException the SQL exception
     */
    public Table loadTableMetadata(String schemaName, String tableName, DataSource dataSource) throws SQLException {
    	if (dataSource == null) {
    		dataSource = datasourceManager.getDefaultDataSource();
    	}
    	Table tableMetadata = new Table();
    	tableMetadata.setName(tableName);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData databaseMetadata = connection.getMetaData();
            try (ResultSet rs = databaseMetadata.getTables(null, schemaName, tableName, null)) {
            	if (rs.next()) {
		            addFields(databaseMetadata, connection, tableMetadata, schemaName);
		            addPrimaryKeys(databaseMetadata, connection, tableMetadata, schemaName);
		            addForeignKeys(databaseMetadata, connection, tableMetadata, schemaName);
		            addIndices(databaseMetadata, connection, tableMetadata, schemaName);
		            addTableType(databaseMetadata, connection, tableMetadata, schemaName);
            	} else {
            		return null;
            	}
            }
            tableMetadata.setSchemaName(schemaName);
        } catch (SQLException e) {
            throw e;
        }

        return tableMetadata;
    }
    
    /**
     * Adds the fields.
     *
     * @param databaseMetadata the database metadata
     * @param connection the connection
     * @param tableMetadata the table metadata
     * @param schemaPattern the schema pattern
     * @throws SQLException the SQL exception
     */
    public static void addFields(DatabaseMetaData databaseMetadata, Connection connection, Table tableMetadata, String schemaPattern) throws SQLException {
        ResultSet columns = databaseMetadata.getColumns(connection.getCatalog(), schemaPattern, normalizeTableName(tableMetadata.getTableName()), null);
        if (columns.next()) {
            iterateFields(tableMetadata, columns);
        } else if (!IS_CASE_SENSETIVE) {
            // Fallback for PostgreSQL
            columns = databaseMetadata.getColumns(connection.getCatalog(), schemaPattern, normalizeTableName(tableMetadata.getTableName().toLowerCase()), null);
            if (!columns.next()) {
                throw new SQLException("Error in getting the information about the columns.");
            } else {
                iterateFields(tableMetadata, columns);
            }
        }
    }
    
    /**
     * Iterate fields.
     *
     * @param tableMetadata the table metadata
     * @param columns the columns
     * @throws SQLException the SQL exception
     */
    private static void iterateFields(Table tableMetadata, ResultSet columns) throws SQLException {
        do {
            tableMetadata.getColumns().add(
                    new TableColumn(
                            columns.getString(JDBC_COLUMN_NAME_PROPERTY),
                            columns.getString(JDBC_COLUMN_TYPE_PROPERTY),
                            columns.getInt(JDBC_COLUMN_SIZE_PROPERTY) + "",
                            columns.getBoolean(JDBC_COLUMN_NULLABLE_PROPERTY),
                            false,
                            null,
                            columns.getInt(JDBC_COLUMN_DECIMAL_DIGITS_PROPERTY) + "",
                            false,
                            tableMetadata
                            ));
        } while (columns.next());
    }
    
    /**
     * Adds the primary keys.
     *
     * @param databaseMetadata the database metadata
     * @param connection the connection
     * @param tableMetadata the table metadata
     * @param schema the schema
     * @throws SQLException the SQL exception
     */
    public static void addPrimaryKeys(DatabaseMetaData databaseMetadata, Connection connection, Table tableMetadata, String schema) throws SQLException {
        ResultSet primaryKeys = databaseMetadata.getPrimaryKeys(connection.getCatalog(), schema, normalizeTableName(tableMetadata.getTableName()));
        if (primaryKeys.next()) {
            iteratePrimaryKeys(tableMetadata, primaryKeys);
        } else if (!IS_CASE_SENSETIVE) {
            // Fallback for PostgreSQL
            primaryKeys = databaseMetadata.getPrimaryKeys(connection.getCatalog(), schema, normalizeTableName(tableMetadata.getTableName().toLowerCase()));
            if (!primaryKeys.next()) {
                return;
            } else {
                iteratePrimaryKeys(tableMetadata, primaryKeys);
            }
        }
    }
    
    /**
     * Iterate primary keys.
     *
     * @param tableMetadata the table metadata
     * @param primaryKeys the primary keys
     * @throws SQLException the SQL exception
     */
    private static void iteratePrimaryKeys(Table tableMetadata, ResultSet primaryKeys)
            throws SQLException {
        do {
            setColumnPrimaryKey(primaryKeys.getString(JDBC_COLUMN_NAME_PROPERTY), tableMetadata);
        } while (primaryKeys.next());
    }
    
    /**
     * Sets the column primary key.
     *
     * @param columnName the column name
     * @param tableModel the table model
     */
    public static void setColumnPrimaryKey(String columnName, Table tableModel) {
        tableModel.getColumns().forEach(column -> {
            if (column.getName().equals(columnName)) {
                column.setPrimaryKey(true);
            }
        });
    }

	/**
     * Adds the foreign keys.
     *
     * @param databaseMetadata the database metadata
     * @param connection the connection
     * @param tableMetadata the table metadata
     * @param schema the schema
     * @throws SQLException the SQL exception
     */
    public static void addForeignKeys(DatabaseMetaData databaseMetadata, Connection connection, Table tableMetadata, String schema) throws SQLException {
        ResultSet foreignKeys = databaseMetadata.getImportedKeys(connection.getCatalog(), schema, normalizeTableName(tableMetadata.getTableName()));
        if (foreignKeys.next()) {
            iterateForeignKeys(tableMetadata, foreignKeys);
        } else if (!IS_CASE_SENSETIVE) {
            // Fallback for PostgreSQL
            foreignKeys = databaseMetadata.getImportedKeys(connection.getCatalog(), schema, normalizeTableName(tableMetadata.getTableName().toLowerCase()));
            if (!foreignKeys.next()) {
                return;
            } else {
                iterateForeignKeys(tableMetadata, foreignKeys);
            }
        }
    }
    
    /**
     * Iterate foreign keys.
     *
     * @param tableMetadata the table metadata
     * @param foreignKeys the foreign keys
     * @throws SQLException the SQL exception
     */
    private static void iterateForeignKeys(Table tableMetadata, ResultSet foreignKeys)
            throws SQLException {
        do {
            TableConstraintForeignKey foreignKeyMetadata = 
            		new TableConstraintForeignKey(
            				foreignKeys.getString(JDBC_FK_NAME_PROPERTY),
            				new String[] {},
            				new String[] {foreignKeys.getString(JDBC_FK_COLUMN_NAME_PROPERTY)},
            				foreignKeys.getString(JDBC_PK_TABLE_NAME_PROPERTY),
            				new String[] {foreignKeys.getString(JDBC_PK_COLUMN_NAME_PROPERTY)},
		                    tableMetadata.getConstraints()
		            );
            tableMetadata.getConstraints().getForeignKeys().add(foreignKeyMetadata);
        } while (foreignKeys.next());
    }

    

    

    
    
    /**
     * Add indices.
     *
     * @param databaseMetadata the database metadata
     * @param connection the connection
     * @param tableMetadata the table metadata
     * @param schema the schema name
     * @throws SQLException the SQL exception
     */
    public static void addIndices(DatabaseMetaData databaseMetadata, Connection connection,
			Table tableMetadata, String schema) throws SQLException {
		
    	try (ResultSet indexes = databaseMetadata.getIndexInfo(connection.getCatalog(), schema, normalizeTableName(tableMetadata.getTableName()), false, true)) {
	        String lastIndexName = "";
	
	        while (indexes.next()) {
	            String indexName = indexes.getString("INDEX_NAME");
	            if (indexName == null) {
	                continue;
	            }
	
	            TableConstraint index = null;
	            if (!indexName.equals(lastIndexName)) {
	            	boolean unique = indexes.getBoolean("NON_UNIQUE");
	            	
	            	if (!unique) {
	            		index = new TableConstraintUnique(
	            				indexName,
	            				new String[]{},
	            				new String[]{},
	            				tableMetadata.getConstraints(),
	            				indexes.getShort("TYPE") + "",
	            				indexes.getString("ASC_OR_DESC")
	            				);
	            		tableMetadata.getConstraints().getUniqueIndexes().add((TableConstraintUnique) index);
	            	} else {
	            		index = new TableConstraintCheck(
	            				indexName,
	            				new String[]{},
	            				new String[]{},
	            				tableMetadata.getConstraints(),
	            				indexes.getShort(JDBC_FILTER_CONDITION_PROPERTY) + "");
	            		tableMetadata.getConstraints().getChecks().add((TableConstraintCheck) index);
	            	}
	                
	                lastIndexName = indexName;
	            }
	            if (index != null) {
	                String columnName = indexes.getString(JDBC_COLUMN_NAME_PROPERTY);
	                String[] array = Arrays.copyOf(index.getColumns(), index.getColumns().length + 1);
	                array[array.length - 1] = columnName;
	                index.setColumns(array);
	            }
	        }
    	}
		
	}

    /**
     * Adds the correct formatting.
     *
     * @param columnName the column name
     * @return the string
     */
    public static String addCorrectFormatting(String columnName) {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, columnName);
    }

    /**
     * Normalize table name.
     *
     * @param table the table
     * @return the string
     */
    public static String normalizeTableName(String table) {
        if (table != null && table.startsWith("\"") && table.endsWith("\"")) {
            table = table.substring(1, table.length() - 1);
        }
        return table;
    }

    /**
     * Adds the table type.
     *
     * @param databaseMetadata the database metadata
     * @param connection the connection
     * @param tableMetadata the table metadata
     * @param schemaPattern the schema pattern
     * @throws SQLException the SQL exception
     */
    public static void addTableType(DatabaseMetaData databaseMetadata, Connection connection, Table tableMetadata, String schemaPattern) throws SQLException {
        ResultSet tables = databaseMetadata.getTables(connection.getCatalog(), schemaPattern, normalizeTableName(tableMetadata.getTableName()), null);
        if (tables.next()) {
            iterateTables(tableMetadata, tables);
        } else if (!IS_CASE_SENSETIVE) {
            // Fallback for PostgreSQL
            tables = databaseMetadata.getTables(connection.getCatalog(), schemaPattern, normalizeTableName(tableMetadata.getTableName().toLowerCase()), null);
            if (!tables.next()) {
                throw new SQLException("Error in getting the information about the tables.");
            } else {
                iterateTables(tableMetadata, tables);
            }
        }
    }

    /**
     * Iterate tables.
     *
     * @param tableMetadata the table metadata
     * @param tables the tables
     * @throws SQLException the SQL exception
     */
    private static void iterateTables(Table tableMetadata, ResultSet tables) throws SQLException {
        do {
            tableMetadata.setTableType(tables.getString(JDBC_TABLE_TYPE_PROPERTY));
        } while (tables.next());
    }

    /**
     * Gets the table schema.
     *
     * @param dataSource the data source
     * @param tableName the table name
     * @return the table schema
     * @throws SQLException the SQL exception
     */
    public static String getTableSchema(DataSource dataSource, String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            ResultSet rs = databaseMetaData.getTables(connection.getCatalog(), null, tableName, new String[]{ISqlKeywords.KEYWORD_TABLE});
            if (rs.next()) {
                return rs.getString(JDBC_TABLE_SCHEME_PROPERTY);
            }
            return null;
        }
    }
    
    /**
     * Gets the tables in schema.
     *
     * @param dataSource the data source
     * @param schemaName the schema name
     * @return the tables in schema
     * @throws SQLException the SQL exception
     */
    public static List<String> getTablesInSchema(DataSource dataSource, String schemaName) throws SQLException {
    	List<String> tableNames = new ArrayList<String>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            ResultSet schemas = databaseMetaData.getSchemas(null, schemaName);
            if (schemas.next()) {
            		
	            ResultSet rs = databaseMetaData.getTables(connection.getCatalog(), schemaName, null, new String[]{ISqlKeywords.KEYWORD_TABLE});
	            while (rs.next()) {
	            	tableNames.add(rs.getString(JDBC_TABLE_NAME_PROPERTY));
	            }
	            return tableNames;
            }
        }
        return null;
    }

	/**
	 * Gets the schema metadata.
	 *
	 * @param schema the schema
	 * @param datasource the datasource
	 * @return the schema metadata
	 * @throws SQLException the SQL exception
	 */
	public List<Table> loadSchemaMetadata(String schema, DataSource datasource) throws SQLException {
		List<Table> tables = new ArrayList<Table>();
		
		List<String> tableNames = getTablesInSchema(datasource, schema);
		if (tableNames != null) {
			for (String tableName : tableNames) {
				Table tableModel = loadTableMetadata(schema, tableName, datasource);
				tables.add(tableModel);
			}
		} else {
			String error = schema + " does not exist in the target database";
			if (logger.isErrorEnabled()) {logger.error(error);}
		}
		
		return tables;
	}

	/**
	 * Gets the schemas.
	 *
	 * @param dataSource the data source
	 * @return the schemas
	 * @throws SQLException the SQL exception
	 */
	public Set<String> getSchemas(DataSource dataSource) throws SQLException {
		Set<String> result = new HashSet<>();
		try (Connection connection = dataSource.getConnection();
				ResultSet schemas = connection.getMetaData().getSchemas()) {
			while (schemas.next()) {
				String schema = schemas.getString("TABLE_SCHEM");
				result.add(schema);
			}
		}
		return result;
	}

}
