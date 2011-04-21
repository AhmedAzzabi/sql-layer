/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.ais.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Definitive declaration of supported data types. The fields in each Type
 * instance are:
 * 
 * <dl>
 * <dt>name</dt>
 * <dd>Canonical name for the type</dd>
 * 
 * <dt>nparams</dt>
 * <dd>How many parameters are specified by DDL (0, 1 or 2)</dd>
 * 
 * <dt>fixed</dt>
 * <dd>Whether the storage size is fixed, e.g., true for int, false for
 * varchar</dt>
 * 
 * <dt>maxBytesize</dt>
 * <dd>Storage size of elements. For fixed types, the chunk server relies on
 * this to determine how to encode/decode values. For variable-length fields,
 * this is the maximum number of bytes of data MySQL may encode, it DOES NOT
 * include an allowance for the prefix bytes written by MySQL.
 * 
 * <dt>encoding</dt>
 * <dd>Name of a member of the chunk server's Encoding enum. This guides
 * translation of a column value into a chunk server's internal format.</dd>
 * </dl>
 * 
 * @author peter
 * 
 */
public class Types {
    
    // TODO -
    // This is the largest BLOB size that will fit in a message.  Increase or
    // remove this when we are no longer limited by the message size.
    // Note that the Type objects for the BLOB types carry their MySQL-defined
    // values so that the prefix size will be computed correctly.  The
    // cap is imposed by the constructor of a Column object.
    //
    public final static int MAX_STORAGE_SIZE_CAP = 1024 * 1024 - 1024;

	// The basic numeric types, fixed length, implemented
	// (except bigint unsigned fails for numbers larger than Long.MAX_VALUE).
	//
	public static Type BIGINT =       new Type("bigint", 0, true, 8L, "INT");
	public static Type U_BIGINT = 	  new Type("bigint unsigned", 0, true, 8L, "U_INT");
	public static Type DOUBLE =       new Type("double", 0, true, 8L, "DOUBLE");
	public static Type U_DOUBLE =     new Type("double unsigned", 0, true, 8L, "U_DOUBLE");
	public static Type FLOAT =        new Type("float", 0, true, 4L, "FLOAT");
	public static Type U_FLOAT =      new Type("float unsigned", 0, true, 4L, "U_FLOAT");
	public static Type INT =          new Type("int", 0, true, 4L, "INT");
	public static Type U_INT =        new Type("int unsigned", 0, true, 4L, "U_INT");
	public static Type MEDIUMINT =    new Type("mediumint", 0, true, 3L, "INT");
	public static Type U_MEDIUMINT =  new Type("mediumint unsigned", 0, true, 3L, "U_INT");
	public static Type SMALLINT =     new Type("smallint", 0, true, 2L, "INT");
	public static Type U_SMALLINT =   new Type("smallint unsigned", 0, true, 2L, "U_INT");
	public static Type TINYINT =      new Type("tinyint", 0, true, 1L, "INT");
	public static Type U_TINYINT =    new Type("tinyint unsigned", 0, true, 1L, "U_INT");
	//
	// Date & Time types, fixed length, implemented.
	//
	public static Type DATE =         new Type("date", 0, true, 3L, "DATE");
	public static Type DATETIME =     new Type("datetime", 0, true, 8L,	"DATETIME");
	public static Type YEAR =         new Type("year", 0, true, 1L, "YEAR");
	public static Type TIME =         new Type("time", 0, true, 3L, "TIME");
	public static Type TIMESTAMP =    new Type("timestamp", 0, true, 4L, "TIMESTAMP");
	//
	// VARCHAR and TEXT types. Maximum storage size is computed in Column, numbers
	// here are not used. MaxByteSize numbers here are not used.
	//
	public static Type VARBINARY =    new Type("varbinary", 1, false, 65535L, "VARBINARY");
	public static Type BINARY =       new Type("binary", 1, false, 255L, "VARBINARY");
    public static Type VARCHAR =      new Type("varchar", 1, false, 65535L, "VARCHAR");
	public static Type CHAR =         new Type("char", 1, false, 767L, "VARCHAR");
    //
	// BLOB and TEXT types.  Currently handled identically. The maxByteSize values
	// here are used in computing the correct prefix size.  The maximum allow size
	// is constrained in Column.
	//
	public static Type TINYBLOB =     new Type("tinyblob", 0, false, 0xFFl, "BLOB");
	public static Type TINYTEXT =     new Type("tinytext", 0, false, 0xFFl, "TEXT");
	public static Type BLOB =         new Type("blob", 0, false, 0xFFFFl, "BLOB");
	public static Type TEXT =         new Type("text", 0, false, 0xFFFFl, "TEXT");
	public static Type MEDIUMBLOB =   new Type("mediumblob", 0, false, 0xFFFFFFL, "BLOB");
	public static Type MEDIUMTEXT =   new Type("mediumtext", 0, false, 0xFFFFFFL, "TEXT");
	public static Type LONGBLOB =     new Type("longblob", 0, false, 0xFFFFFFFFL, "BLOB");
	public static Type LONGTEXT =     new Type("longtext", 0, false, 0xFFFFFFFFL, "TEXT");
	//
	// DECIMAL types. The maxByteSize values are computed in Column as they are fixed for
	// a given instance. Numbers are a maximum possible (ie, decimal(65,30));
	//
    public static Type DECIMAL =      new Type("decimal", 2, true, 30L, "DECIMAL");
    public static Type U_DECIMAL =    new Type("decimal unsigned", 2, true, 30L, "U_DECIMAL");
	//
	// Halo unsupported
	//
	public static Type ENUM =         new Type("enum", 1, true, 2L, "U_INT");
	public static Type SET =          new Type("set", 1, true, 8L, "U_INT");
	public static Type BIT =          new Type("bit", 1, true, 9L, "BIT");
	public static Type GEOMETRY =           new Type("geometry", 0, false, 0L, "GEOMETRY");
	public static Type GEOMETRYCOLLECTION = new Type("geometrycollection", 0, false, 0L, "GEOMETRYCOLLECTION");
	public static Type POINT =              new Type("point", 0, false, 0L, "POINT");
	public static Type MULTIPOINT =         new Type("multipoint", 0, false, 0L, "MULTIPOINT");
	public static Type LINESTRING =         new Type("linestring", 0, false, 0L, "LINESTRING");
	public static Type MULTILINESTRING =    new Type("multilinestring", 0, false, 0L, "MULTILINESTRING");
	public static Type POLYGON =            new Type("polygon", 0, false, 0L, "POLYGON");
	public static Type MULTIPOLYGON =       new Type("multipolygon", 0, false, 0L, "MULTIPOLYGON");


    private final static List<Type> types = listOfTypes();
    private final static Set<Type> unsupported = setOfUnsupportedTypes();
    private final static Set<Type> unsupportedInIndex = setOfUnsupportedIndexTypes();
    private final static Map<Type,Long[]> defaultParams = mapOfDefaults();
    

	private static List<Type> listOfTypes() {
	    List<Type> types = new ArrayList<Type>();
		types.add(BIGINT);
        types.add(U_BIGINT);
		types.add(BINARY);
		types.add(BIT);
		types.add(BLOB);
		types.add(CHAR);
		types.add(DATE);
		types.add(DATETIME);
		types.add(DECIMAL);
		types.add(U_DECIMAL);
		types.add(DOUBLE);
		types.add(U_DOUBLE);
		types.add(ENUM);
		types.add(FLOAT);
		types.add(U_FLOAT);
		types.add(GEOMETRY);
		types.add(GEOMETRYCOLLECTION);
		types.add(INT);
		types.add(U_INT);
		types.add(LINESTRING);
		types.add(LONGBLOB);
		types.add(LONGTEXT);
		types.add(MEDIUMBLOB);
		types.add(MEDIUMINT);
		types.add(U_MEDIUMINT);
		types.add(MEDIUMTEXT);
		types.add(MULTILINESTRING);
		types.add(MULTIPOINT);
		types.add(MULTIPOLYGON);
		types.add(POINT);
		types.add(POLYGON);
		types.add(SET);
		types.add(SMALLINT);
		types.add(U_SMALLINT);
		types.add(TEXT);
		types.add(TIME);
		types.add(TIMESTAMP);
		types.add(TINYBLOB);
		types.add(TINYINT);
		types.add(U_TINYINT);
		types.add(TINYTEXT);
		types.add(VARBINARY);
		types.add(VARCHAR);
		types.add(YEAR);
		return Collections.unmodifiableList(types);
	}

    private static Set<Type> setOfUnsupportedTypes() {
        Set<Type> unsupported = new HashSet<Type>();
        unsupported.add(null);
        unsupported.add(BIT);
        unsupported.add(ENUM);
        unsupported.add(SET);
        unsupported.add(GEOMETRY);
        unsupported.add(GEOMETRYCOLLECTION);
        unsupported.add(POINT);
        unsupported.add(MULTIPOINT);
        unsupported.add(LINESTRING);
        unsupported.add(MULTILINESTRING);
        unsupported.add(POLYGON);
        unsupported.add(MULTIPOLYGON);
        return Collections.unmodifiableSet(unsupported);
    }

    private static Set<Type> setOfUnsupportedIndexTypes() {
        Set<Type> unsupported = new HashSet<Type>();
        unsupported.add(TINYBLOB);
        unsupported.add(BLOB);
        unsupported.add(MEDIUMBLOB);
        unsupported.add(LONGBLOB);
        unsupported.add(TINYTEXT);
        unsupported.add(TEXT);
        unsupported.add(MEDIUMTEXT);
        unsupported.add(LONGTEXT);
        return Collections.unmodifiableSet(unsupported);
    }

    private static Map<Type,Long[]> mapOfDefaults() {
        Map<Type,Long[]> map = new HashMap<Type,Long[]>();
        map.put(BIT, new Long[]{1L,null});
        map.put(BINARY, new Long[]{1L,null});
        map.put(CHAR, new Long[]{1L,null});
        map.put(DECIMAL, new Long[]{10L,0L});
        map.put(U_DECIMAL, new Long[]{10L,0L});
        return Collections.unmodifiableMap(map);
    }

    /**
     * List of all known types.
     */
	public static List<Type> types() {
		return types;
	}

    /**
     * Set of all known types that are unsupported.
     */
    public static Set<Type> unsupportedTypes() {
        return unsupported;
    }

    /**
     * Set of all <b>supported</b> types that cannot be used in an index.
     */
    public static Set<Type> unsupportedIndexTypes() {
        return unsupportedInIndex;
    }

    public static Map<Type,Long[]> defaultParams() {
        return defaultParams;
    }

    public static boolean isTextType(Type type) {
        return type.equals(CHAR) || type.equals(VARCHAR) || type.equals(TEXT);
    }

    public static boolean isIntType(Type type) {
        return type.equals(TINYINT) || type.equals(U_TINYINT) ||
               type.equals(SMALLINT) || type.equals(U_SMALLINT) ||
               type.equals(INT) || type.equals(U_INT) ||
               type.equals(MEDIUMINT) || type.equals(U_MEDIUMINT) ||
               type.equals(BIGINT) || type.equals(U_BIGINT);
    }
}
