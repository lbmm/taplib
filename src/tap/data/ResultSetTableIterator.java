package tap.data;

/*
 * This file is part of TAPLibrary.
 * 
 * TAPLibrary is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * TAPLibrary is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with TAPLibrary.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright 2014 - Astronomisches Rechen Institut (ARI)
 */

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.NoSuchElementException;

import tap.metadata.TAPColumn;
import uws.ISO8601Format;
import adql.db.DBColumn;
import adql.db.DBType;
import adql.db.DBType.DBDatatype;
import adql.db.STCS.Region;
import adql.parser.ParseException;
import adql.translator.JDBCTranslator;

/**
 * <p>{@link TableIterator} which lets iterate over a SQL {@link ResultSet}.</p>
 * 
 * <p><i>Note:
 * 	{@link #getColType()} will return a TAP type based on the one declared in the {@link ResultSetMetaData} object.
 * </i></p>
 * 
 * @author Gr&eacute;gory Mantelet (ARI)
 * @version 2.0 (11/2014)
 * @since 2.0
 */
public class ResultSetTableIterator implements TableIterator {

	/** ResultSet/Dataset to read. */
	private final ResultSet data;

	/** Object which has the knowledge of the specific JDBC column types
	 * and which knows how to deal with geometrical values between the
	 * library and the database. */
	private final JDBCTranslator translator;

	/** Number of columns to read. */
	private final int nbColumns;
	/** Metadata of all columns identified before the iteration. */
	private final TAPColumn[] colMeta;

	/** Indicate whether the row iteration has already started. */
	private boolean iterationStarted = false;
	/** Indicate whether the last row has already been reached. */
	private boolean endReached = false;
	/** Index of the last read column (=0 just after {@link #nextRow()} and before {@link #nextCol()}, ={@link #nbColumns} after the last column has been read). */
	private int colIndex;

	/**
	 * <p>Build a TableIterator able to read rows and columns of the given ResultSet.</p>
	 * 
	 * <p>
	 * 	In order to provide the metadata through {@link #getMetadata()}, this constructor is trying to guess the datatype
	 * 	from the DBMS column datatype (using {@link #convertType(int, String, String)}).
	 * </p>
	 * 
	 * <h3>Type guessing</h3>
	 * 
	 * <p>
	 * 	In order to guess a TAP type from a DBMS type, this constructor will call {@link #convertType(int, String, String)}
	 * 	which deals with the most common standard datatypes known in Postgres, SQLite, MySQL, Oracle and JavaDB/Derby.
	 * 	This conversion is therefore not as precise as the one expected by a translator. That's why it is recommended
	 * 	to use one of the constructor having a {@link JDBCTranslator} in parameter.
	 * </p>
	 * 
	 * @param dataSet		Dataset over which this iterator must iterate.
	 * 
	 * @throws NullPointerException	If NULL is given in parameter.
	 * @throws DataReadException	If the given ResultSet is closed or if the metadata (columns count and types) can not be fetched.
	 * 
	 * @see #convertType(int, String, String)
	 * @see #ResultSetTableIterator(ResultSet, JDBCTranslator, String, DBColumn[])
	 */
	public ResultSetTableIterator(final ResultSet dataSet) throws NullPointerException, DataReadException{
		this(dataSet, null, null, null);
	}

	/**
	 * <p>Build a TableIterator able to read rows and columns of the given ResultSet.</p>
	 * 
	 * <p>
	 * 	In order to provide the metadata through {@link #getMetadata()}, this constructor is trying to guess the datatype
	 * 	from the DBMS column datatype (using {@link #convertType(int, String, String)}).
	 * </p>
	 * 
	 * <h3>Type guessing</h3>
	 * 
	 * <p>
	 * 	In order to guess a TAP type from a DBMS type, this constructor will call {@link #convertType(int, String, String)}
	 * 	which deals with the most common standard datatypes known in Postgres, SQLite, MySQL, Oracle and JavaDB/Derby.
	 * 	This conversion is therefore not as precise as the one expected by a translator. That's why it is recommended
	 * 	to use one of the constructor having a {@link JDBCTranslator} in parameter.
	 * </p>
	 * 
	 * <p><i><b>Important</b>:
	 * 	The second parameter of this constructor is given as second parameter of {@link #convertType(int, String, String)}.
	 * 	<b>This parameter is really used ONLY when the DBMS is SQLite ("sqlite").</b>
	 * 	Indeed, SQLite has so many datatype restrictions that it is absolutely needed to know it is the DBMS from which the
	 * 	ResultSet is coming. Without this information, type guessing will be unpredictable! 
	 * </i></p>
	 * 
	 * @param dataSet		Dataset over which this iterator must iterate.
	 * @param dbms			Lower-case string which indicates from which DBMS the given ResultSet is coming. <i>note: MAY be NULL.</i>
	 * 
	 * @throws NullPointerException	If NULL is given in parameter.
	 * @throws DataReadException	If the given ResultSet is closed or if the metadata (columns count and types) can not be fetched.
	 * 
	 * @see #convertType(int, String, String)
	 * @see ResultSetTableIterator#ResultSetTableIterator(ResultSet, JDBCTranslator, String, DBColumn[])
	 */
	public ResultSetTableIterator(final ResultSet dataSet, final String dbms) throws NullPointerException, DataReadException{
		this(dataSet, null, dbms, null);
	}

	/**
	 * <p>Build a TableIterator able to read rows and columns of the given ResultSet.</p>
	 * 
	 * <p>
	 * 	In order to provide the metadata through {@link #getMetadata()}, this constructor is trying to guess the datatype
	 * 	from the DBMS column datatype (using {@link #convertType(int, String, String)}).
	 * </p>
	 * 
	 * <h3>Type guessing</h3>
	 * 
	 * <p>
	 * 	In order to guess a TAP type from a DBMS type, this constructor will call {@link #convertType(int, String, String)}
	 * 	which will ask to the given translator ({@link JDBCTranslator#convertTypeFromDB(int, String, String, String[])})
	 * 	if not NULL. However if no translator is provided, this function will proceed to a default conversion
	 * 	using the most common standard datatypes known in Postgres, SQLite, MySQL, Oracle and JavaDB/Derby.
	 * 	This conversion is therefore not as precise as the one expected by the translator.
	 * </p>
	 * 
	 * @param dataSet		Dataset over which this iterator must iterate.
	 * @param translator	The {@link JDBCTranslator} used to transform the ADQL query into SQL query. This translator is also able to convert
	 *                  	JDBC types and to parse geometrical values. <i>note: MAY be NULL</i> 
	 * 
	 * @throws NullPointerException	If NULL is given in parameter.
	 * @throws DataReadException	If the given ResultSet is closed or if the metadata (columns count and types) can not be fetched.
	 * 
	 * @see #convertType(int, String, String)
	 * @see ResultSetTableIterator#ResultSetTableIterator(ResultSet, JDBCTranslator, String, DBColumn[])
	 */
	public ResultSetTableIterator(final ResultSet dataSet, final JDBCTranslator translator) throws NullPointerException, DataReadException{
		this(dataSet, translator, null, null);
	}

	/**
	 * <p>Build a TableIterator able to read rows and columns of the given ResultSet.</p>
	 * 
	 * <p>
	 * 	In order to provide the metadata through {@link #getMetadata()}, this constructor is trying to guess the datatype
	 * 	from the DBMS column datatype (using {@link #convertType(int, String, String)}).
	 * </p>
	 * 
	 * <h3>Type guessing</h3>
	 * 
	 * <p>
	 * 	In order to guess a TAP type from a DBMS type, this constructor will call {@link #convertType(int, String, String)}
	 * 	which will ask to the given translator ({@link JDBCTranslator#convertTypeFromDB(int, String, String, String[])})
	 * 	if not NULL. However if no translator is provided, this function will proceed to a default conversion
	 * 	using the most common standard datatypes known in Postgres, SQLite, MySQL, Oracle and JavaDB/Derby.
	 * 	This conversion is therefore not as precise as the one expected by the translator.
	 * </p>
	 * 
	 * <p><i><b>Important</b>:
	 * 	The third parameter of this constructor is given as second parameter of {@link #convertType(int, String, String)}.
	 * 	<b>This parameter is really used ONLY when the translator conversion failed and when the DBMS is SQLite ("sqlite").</b>
	 * 	Indeed, SQLite has so many datatype restrictions that it is absolutely needed to know it is the DBMS from which the
	 * 	ResultSet is coming. Without this information, type guessing will be unpredictable! 
	 * </i></p>
	 * 
	 * @param dataSet		Dataset over which this iterator must iterate.
	 * @param translator	The {@link JDBCTranslator} used to transform the ADQL query into SQL query. This translator is also able to convert
	 *                  	JDBC types and to parse geometrical values. <i>note: MAY be NULL</i> 
	 * @param dbms			Lower-case string which indicates from which DBMS the given ResultSet is coming. <i>note: MAY be NULL.</i>
	 * 
	 * @throws NullPointerException	If NULL is given in parameter.
	 * @throws DataReadException	If the given ResultSet is closed or if the metadata (columns count and types) can not be fetched.
	 * 
	 * @see #convertType(int, String, String)
	 * @see ResultSetTableIterator#ResultSetTableIterator(ResultSet, JDBCTranslator, String, DBColumn[])
	 */
	public ResultSetTableIterator(final ResultSet dataSet, final JDBCTranslator translator, final String dbms) throws NullPointerException, DataReadException{
		this(dataSet, translator, dbms, null);
	}

	/**
	 * <p>Build a TableIterator able to read rows and columns of the given ResultSet.</p>
	 * 
	 * <p>
	 * 	In order to provide the metadata through {@link #getMetadata()}, this constructor is reading first the given metadata (if any),
	 * 	and then, try to guess the datatype from the DBMS column datatype (using {@link #convertType(int, String, String)}).
	 * </p>
	 * 
	 * <h3>Provided metadata</h3>
	 * 
	 * <p>The third parameter of this constructor aims to provide the metadata expected for each column of the ResultSet.</p>
	 * 
	 * <p>
	 * 	For that, it is expected that all these metadata are {@link TAPColumn} objects. Indeed, simple {@link DBColumn}
	 * 	instances do not have the type information. If just {@link DBColumn}s are provided, the ADQL name it provides will be kept
	 * 	but the type will be guessed from the type provide by the ResultSetMetadata.
	 * </p>
	 * 
	 * <p><i>Note:
	 * 	If this parameter is incomplete (array length less than the column count returned by the ResultSet or some array items are NULL),
	 * 	column metadata will be associated in the same order as the ResultSet columns. Missing metadata will be built from the
	 * 	{@link ResultSetMetaData} and so the types will be guessed.
	 * </i></p>
	 * 
	 * <h3>Type guessing</h3>
	 * 
	 * <p>
	 * 	In order to guess a TAP type from a DBMS type, this constructor will call {@link #convertType(int, String, String)}
	 * 	which will ask to the given translator ({@link JDBCTranslator#convertTypeFromDB(int, String, String, String[])})
	 * 	if not NULL. However if no translator is provided, this function will proceed to a default conversion
	 * 	using the most common standard datatypes known in Postgres, SQLite, MySQL, Oracle and JavaDB/Derby.
	 * 	This conversion is therefore not as precise as the one expected by the translator.
	 * </p>
	 * 
	 * <p><i><b>Important</b>:
	 * 	The third parameter of this constructor is given as second parameter of {@link #convertType(int, String, String)}.
	 * 	<b>This parameter is really used ONLY when the translator conversion failed and when the DBMS is SQLite ("sqlite").</b>
	 * 	Indeed, SQLite has so many datatype restrictions that it is absolutely needed to know it is the DBMS from which the
	 * 	ResultSet is coming. Without this information, type guessing will be unpredictable! 
	 * </i></p>
	 * 
	 * @param dataSet		Dataset over which this iterator must iterate.
	 * @param translator	The {@link JDBCTranslator} used to transform the ADQL query into SQL query. This translator is also able to convert
	 *                  	JDBC types and to parse geometrical values. <i>note: MAY be NULL</i> 
	 * @param dbms			Lower-case string which indicates from which DBMS the given ResultSet is coming. <i>note: MAY be NULL.</i>
	 * @param resultMeta	List of expected columns. <i>note: these metadata are expected to be really {@link TAPColumn} objects ; MAY be NULL.</i>
	 * 
	 * @throws NullPointerException	If NULL is given in parameter.
	 * @throws DataReadException	If the metadata (columns count and types) can not be fetched.
	 * 
	 * @see #convertType(int, String, String)
	 */
	public ResultSetTableIterator(final ResultSet dataSet, final JDBCTranslator translator, final String dbms, final DBColumn[] resultMeta) throws NullPointerException, DataReadException{
		// A dataset MUST BE provided:
		if (dataSet == null)
			throw new NullPointerException("Missing ResultSet object over which to iterate!");

		// Keep a reference to the ResultSet:
		data = dataSet;

		// Set the translator to use (if needed):
		this.translator = translator;

		// Count columns and determine their type:
		try{
			// get the metadata:
			ResultSetMetaData metadata = data.getMetaData();
			// count columns:
			nbColumns = metadata.getColumnCount();
			// determine their type:
			colMeta = new TAPColumn[nbColumns];
			for(int i = 1; i <= nbColumns; i++){
				if (resultMeta != null && (i - 1) < resultMeta.length && resultMeta[i - 1] != null){
					try{
						colMeta[i - 1] = (TAPColumn)resultMeta[i - 1];
					}catch(ClassCastException cce){
						DBType datatype = convertType(metadata.getColumnType(i), metadata.getColumnTypeName(i), dbms);
						colMeta[i - 1] = new TAPColumn(resultMeta[i - 1].getADQLName(), datatype);
					}
				}else{
					DBType datatype = convertType(metadata.getColumnType(i), metadata.getColumnTypeName(i), dbms);
					colMeta[i - 1] = new TAPColumn(metadata.getColumnLabel(i), datatype);
				}
			}
		}catch(SQLException se){
			throw new DataReadException("Can not get the column types of the given ResultSet!", se);
		}
	}

	@Override
	public void close() throws DataReadException{
		try{
			data.close();
		}catch(SQLException se){
			throw new DataReadException("Can not close the iterated ResultSet!", se);
		}
	}

	@Override
	public TAPColumn[] getMetadata(){
		return colMeta;
	}

	@Override
	public boolean nextRow() throws DataReadException{
		try{
			// go to the next row:
			boolean rowFetched = data.next();
			endReached = !rowFetched;
			// prepare the iteration over its columns:
			colIndex = 0;
			iterationStarted = true;
			return rowFetched;
		}catch(SQLException e){
			throw new DataReadException("Unable to read a result set row!", e);
		}
	}

	/**
	 * <p>Check the row iteration state. That's to say whether:</p>
	 * <ul>
	 * 	<li>the row iteration has started = the first row has been read = a first call of {@link #nextRow()} has been done</li>
	 * 	<li>AND the row iteration is not finished = the last row has been read.</li>
	 * </ul>
	 * 
	 * @throws IllegalStateException
	 */
	private void checkReadState() throws IllegalStateException{
		if (!iterationStarted)
			throw new IllegalStateException("No row has yet been read!");
		else if (endReached)
			throw new IllegalStateException("End of ResultSet already reached!");
	}

	@Override
	public boolean hasNextCol() throws IllegalStateException, DataReadException{
		// Check the read state:
		checkReadState();

		// Determine whether the last column has been reached or not:
		return (colIndex < nbColumns);
	}

	@Override
	public Object nextCol() throws NoSuchElementException, IllegalStateException, DataReadException{
		// Check the read state and ensure there is still at least one column to read:
		if (!hasNextCol())
			throw new NoSuchElementException("No more column to read!");

		// Get the column value:
		try{
			Object o = data.getObject(++colIndex);
			if (o != null){
				DBType colType = getColType();
				// if the column value is a Timestamp object, format it in ISO8601:
				if (o instanceof Timestamp)
					o = ISO8601Format.format(((Timestamp)o).getTime());
				// if the column value is a geometrical object, it must be serialized in STC-S:
				else if (translator != null && colType.isGeometry()){
					Region region = translator.translateGeometryFromDB(o);
					if (region != null)
						o = region.toSTCS();
				}
			}
			return o;
		}catch(SQLException se){
			throw new DataReadException("Can not read the value of the " + colIndex + "-th column!", se);
		}catch(ParseException pe){
			throw new DataReadException(pe.getMessage());
		}
	}

	@Override
	public DBType getColType() throws IllegalStateException, DataReadException{
		// Basically check the read state (for rows iteration):
		checkReadState();

		// Check deeper the read state (for columns iteration):
		if (colIndex <= 0)
			throw new IllegalStateException("No column has yet been read!");
		else if (colIndex > nbColumns)
			throw new IllegalStateException("All columns have already been read!");

		// Return the column type:
		return colMeta[colIndex - 1].getDatatype();
	}

	/**
	 * <p>Convert the given DBMS type into the corresponding {@link DBType} instance.</p>
	 * 
	 * <p>
	 *	This function first tries the conversion using the translator ({@link JDBCTranslator#convertTypeFromDB(int, String, String, String[])}).
	 * 	If the translator fails, a default conversion is done.
	 * </p>
	 * 
	 * <p><b>Warning:
	 * 	It is not recommended to rely on the default conversion.
	 * 	This conversion is just a matter of guessing the better matching {@link DBType}
	 * 	considering the types of the following DBMS: PostgreSQL, SQLite, MySQL, Oracle and Java/DB/Derby.
	 * </b></p>
	 * 
	 * @param dbmsType	DBMS column data-type name.
	 * @param dbms		Lower-case string which indicates which DBMS the ResultSet is coming from. <i>note: MAY be NULL.</i>
	 * 
	 * @return	The best suited {@link DBType} object.
	 * 
	 * @see JDBCTranslator#convertTypeFromDB(int, String, String, String[])
	 * @see #defaultTypeConversion(String, String[], String)
	 */
	protected DBType convertType(final int dbmsType, String dbmsTypeName, final String dbms) throws DataReadException{
		// If no type is provided return VARCHAR:
		if (dbmsTypeName == null || dbmsTypeName.trim().length() == 0)
			return new DBType(DBDatatype.VARCHAR, DBType.NO_LENGTH);

		// Extract the type prefix and lower-case it:
		int startParamIndex = dbmsTypeName.indexOf('('), endParamIndex = dbmsTypeName.indexOf(')');
		String dbmsTypePrefix = (startParamIndex <= 0) ? dbmsTypeName : dbmsTypeName.substring(0, endParamIndex);
		dbmsTypePrefix = dbmsTypePrefix.trim().toLowerCase();
		String[] typeParams = (startParamIndex <= 0) ? null : dbmsTypeName.substring(startParamIndex + 1, endParamIndex).split(",");

		// Ask first to the translator:
		DBType dbType = null;
		if (translator != null)
			dbType = translator.convertTypeFromDB(dbmsType, dbmsTypeName, dbmsTypePrefix, typeParams);

		// And if unsuccessful, apply a default conversion:
		if (dbType == null)
			dbType = defaultTypeConversion(dbmsTypePrefix, typeParams, dbms);

		return dbType;
	}

	/**
	 * <p>Convert the given DBMS type into the better matching {@link DBType} instance.
	 * This function is used to <b>guess</b> the TAP type of a column when it is not provided in the constructor.
	 * It aims not to be exhaustive, but just to provide a type when the given TAP metadata are incomplete.</p>
	 * 
	 * <p><i>Note:
	 * 	Any unknown DBMS data-type will be considered and translated as a VARCHAR.
	 * 	This latter will be also returned if the given parameter is an empty string or NULL.
	 * </i></p>
	 * 
	 * <p><i>Note:
	 * 	This type conversion function has been designed to work with all standard data-types of the following DBMS:
	 * 	PostgreSQL, SQLite, MySQL, Oracle and JavaDB/Derby.
	 * </i></p>
	 * 
	 * <p><i><b>Important</b>:
	 * 	<b>The third parameter is REALLY NEEDED when the DBMS is SQLite ("sqlite")!</b>
	 * 	Indeed, SQLite has a so restrictive list of data-types that this function can reliably convert
	 * 	only if it knows the DBMS is SQLite. Otherwise, the conversion result would be unpredictable.
	 * 	</i>In this default implementation of this function, all other DBMS values are ignored.<i>
	 * </i></p>
	 * 
	 * <p><b>Warning</b>:
	 * 	This function is not translating the geometrical data-types. If a such data-type is encountered,
	 * 	it will considered as unknown and so, a VARCHAR TAP type will be returned.
	 * </p>
	 * 
	 * @param dbmsTypeName	Name of type, without the eventual parameters.
	 * @param params		The eventual type parameters (e.g. char string length).
	 * @param dbms			The targeted DBMS.
	 * 
	 * @return	The corresponding ADQL/TAP type. <i>NEVER NULL</i>
	 */
	protected final DBType defaultTypeConversion(final String dbmsTypeName, final String[] params, final String dbms){
		// Get the length parameter (always in first position):
		int lengthParam = DBType.NO_LENGTH;
		if (params != null && params.length > 0){
			try{
				lengthParam = Integer.parseInt(params[0]);
			}catch(NumberFormatException nfe){}
		}

		// CASE: SQLITE
		if (dbms != null && dbms.equals("sqlite")){
			// INTEGER -> SMALLINT, INTEGER, BIGINT
			if (dbmsTypeName.equals("integer"))
				return new DBType(DBDatatype.BIGINT);
			// REAL -> REAL, DOUBLE
			else if (dbmsTypeName.equals("real"))
				return new DBType(DBDatatype.DOUBLE);
			// TEXT -> CHAR, VARCHAR, CLOB, TIMESTAMP
			else if (dbmsTypeName.equals("text"))
				return new DBType(DBDatatype.VARCHAR);
			// BLOB -> BINARY, VARBINARY, BLOB
			else if (dbmsTypeName.equals("blob"))
				return new DBType(DBDatatype.BLOB);
			// Default:
			else
				return new DBType(DBDatatype.VARCHAR, DBType.NO_LENGTH);
		}
		// CASE: OTHER DBMS
		else{
			// SMALLINT
			if (dbmsTypeName.equals("smallint") || dbmsTypeName.equals("int2") || dbmsTypeName.equals("smallserial") || dbmsTypeName.equals("serial2") || dbmsTypeName.equals("boolean") || dbmsTypeName.equals("bool"))
				return new DBType(DBDatatype.SMALLINT);
			// INTEGER
			else if (dbmsTypeName.equals("integer") || dbmsTypeName.equals("int") || dbmsTypeName.equals("int4") || dbmsTypeName.equals("serial") || dbmsTypeName.equals("serial4"))
				return new DBType(DBDatatype.INTEGER);
			// BIGINT
			else if (dbmsTypeName.equals("bigint") || dbmsTypeName.equals("int8") || dbmsTypeName.equals("bigserial") || dbmsTypeName.equals("bigserial8") || dbmsTypeName.equals("number"))
				return new DBType(DBDatatype.BIGINT);
			// REAL
			else if (dbmsTypeName.equals("real") || dbmsTypeName.equals("float4") || (dbmsTypeName.equals("float") && lengthParam <= 63))
				return new DBType(DBDatatype.REAL);
			// DOUBLE
			else if (dbmsTypeName.equals("double") || dbmsTypeName.equals("double precision") || dbmsTypeName.equals("float8") || (dbmsTypeName.equals("float") && lengthParam > 63))
				return new DBType(DBDatatype.DOUBLE);
			// BINARY
			else if (dbmsTypeName.equals("bit") || dbmsTypeName.equals("binary") || dbmsTypeName.equals("raw") || ((dbmsTypeName.equals("char") || dbmsTypeName.equals("character")) && dbmsTypeName.endsWith(" for bit data")))
				return new DBType(DBDatatype.BINARY, lengthParam);
			// VARBINARY
			else if (dbmsTypeName.equals("bit varying") || dbmsTypeName.equals("varbit") || dbmsTypeName.equals("varbinary") || dbmsTypeName.equals("long raw") || ((dbmsTypeName.equals("varchar") || dbmsTypeName.equals("character varying")) && dbmsTypeName.endsWith(" for bit data")))
				return new DBType(DBDatatype.VARBINARY, lengthParam);
			// CHAR
			else if (dbmsTypeName.equals("char") || dbmsTypeName.equals("character"))
				return new DBType(DBDatatype.CHAR, lengthParam);
			// VARCHAR
			else if (dbmsTypeName.equals("varchar") || dbmsTypeName.equals("varchar2") || dbmsTypeName.equals("character varying"))
				return new DBType(DBDatatype.VARCHAR, lengthParam);
			// BLOB
			else if (dbmsTypeName.equals("bytea") || dbmsTypeName.equals("blob") || dbmsTypeName.equals("binary large object"))
				return new DBType(DBDatatype.BLOB);
			// CLOB
			else if (dbmsTypeName.equals("text") || dbmsTypeName.equals("clob") || dbmsTypeName.equals("character large object"))
				return new DBType(DBDatatype.CLOB);
			// TIMESTAMP
			else if (dbmsTypeName.equals("timestamp") || dbmsTypeName.equals("timestamptz") || dbmsTypeName.equals("time") || dbmsTypeName.equals("timetz") || dbmsTypeName.equals("date"))
				return new DBType(DBDatatype.TIMESTAMP);
			// Default:
			else
				return new DBType(DBDatatype.VARCHAR, DBType.NO_LENGTH);
		}
	}

}
