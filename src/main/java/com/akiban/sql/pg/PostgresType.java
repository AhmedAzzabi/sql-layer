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

package com.akiban.sql.pg;

import com.akiban.sql.StandardException;

import com.akiban.ais.model.Column;
import com.akiban.ais.model.Type;

import com.akiban.server.encoding.EncoderFactory;

import java.io.*;
import java.text.*;
import java.util.*;

/** A type according to the PostgreSQL regime.
 * Information corresponds more-or-less directly to what's in the 
 * <code>pg_attribute</code> table.
 */
public class PostgresType
{
  /*** Type OIDs ***/

  public static final int BOOL_TYPE_OID = 16;
  public static final int BYTEA_TYPE_OID = 17;
  public static final int CHAR_TYPE_OID = 18;
  public static final int NAME_TYPE_OID = 19;
  public static final int INT8_TYPE_OID = 20;
  public static final int INT2_TYPE_OID = 21;
  public static final int INT2VECTOR_TYPE_OID = 22;
  public static final int INT4_TYPE_OID = 23;
  public static final int REGPROC_TYPE_OID = 24;
  public static final int TEXT_TYPE_OID = 25;
  public static final int OID_TYPE_OID = 26;
  public static final int TID_TYPE_OID = 27;
  public static final int XID_TYPE_OID = 28;
  public static final int CID_TYPE_OID = 29;
  public static final int OIDVECTOR_TYPE_OID = 30;
  public static final int PG_TYPE_TYPE_OID = 71;
  public static final int PG_ATTRIBUTE_TYPE_OID = 75;
  public static final int PG_PROC_TYPE_OID = 81;
  public static final int PG_CLASS_TYPE_OID = 83;
  public static final int XML_TYPE_OID = 142;
  public static final int _XML_TYPE_OID = 143;
  public static final int SMGR_TYPE_OID = 210;
  public static final int POINT_TYPE_OID = 600;
  public static final int LSEG_TYPE_OID = 601;
  public static final int PATH_TYPE_OID = 602;
  public static final int BOX_TYPE_OID = 603;
  public static final int POLYGON_TYPE_OID = 604;
  public static final int LINE_TYPE_OID = 628;
  public static final int _LINE_TYPE_OID = 629;
  public static final int FLOAT4_TYPE_OID = 700;
  public static final int FLOAT8_TYPE_OID = 701;
  public static final int ABSTIME_TYPE_OID = 702;
  public static final int RELTIME_TYPE_OID = 703;
  public static final int TINTERVAL_TYPE_OID = 704;
  public static final int UNKNOWN_TYPE_OID = 705;
  public static final int CIRCLE_TYPE_OID = 718;
  public static final int _CIRCLE_TYPE_OID = 719;
  public static final int MONEY_TYPE_OID = 790;
  public static final int _MONEY_TYPE_OID = 791;
  public static final int MACADDR_TYPE_OID = 829;
  public static final int INET_TYPE_OID = 869;
  public static final int CIDR_TYPE_OID = 650;
  public static final int _BOOL_TYPE_OID = 1000;
  public static final int _BYTEA_TYPE_OID = 1001;
  public static final int _CHAR_TYPE_OID = 1002;
  public static final int _NAME_TYPE_OID = 1003;
  public static final int _INT2_TYPE_OID = 1005;
  public static final int _INT2VECTOR_TYPE_OID = 1006;
  public static final int _INT4_TYPE_OID = 1007;
  public static final int _REGPROC_TYPE_OID = 1008;
  public static final int _TEXT_TYPE_OID = 1009;
  public static final int _OID_TYPE_OID = 1028;
  public static final int _TID_TYPE_OID = 1010;
  public static final int _XID_TYPE_OID = 1011;
  public static final int _CID_TYPE_OID = 1012;
  public static final int _OIDVECTOR_TYPE_OID = 1013;
  public static final int _BPCHAR_TYPE_OID = 1014;
  public static final int _VARCHAR_TYPE_OID = 1015;
  public static final int _INT8_TYPE_OID = 1016;
  public static final int _POINT_TYPE_OID = 1017;
  public static final int _LSEG_TYPE_OID = 1018;
  public static final int _PATH_TYPE_OID = 1019;
  public static final int _BOX_TYPE_OID = 1020;
  public static final int _FLOAT4_TYPE_OID = 1021;
  public static final int _FLOAT8_TYPE_OID = 1022;
  public static final int _ABSTIME_TYPE_OID = 1023;
  public static final int _RELTIME_TYPE_OID = 1024;
  public static final int _TINTERVAL_TYPE_OID = 1025;
  public static final int _POLYGON_TYPE_OID = 1027;
  public static final int ACLITEM_TYPE_OID = 1033;
  public static final int _ACLITEM_TYPE_OID = 1034;
  public static final int _MACADDR_TYPE_OID = 1040;
  public static final int _INET_TYPE_OID = 1041;
  public static final int _CIDR_TYPE_OID = 651;
  public static final int _CSTRING_TYPE_OID = 1263;
  public static final int BPCHAR_TYPE_OID = 1042;
  public static final int VARCHAR_TYPE_OID = 1043;
  public static final int DATE_TYPE_OID = 1082;
  public static final int TIME_TYPE_OID = 1083;
  public static final int TIMESTAMP_TYPE_OID = 1114;
  public static final int _TIMESTAMP_TYPE_OID = 1115;
  public static final int _DATE_TYPE_OID = 1182;
  public static final int _TIME_TYPE_OID = 1183;
  public static final int TIMESTAMPTZ_TYPE_OID = 1184;
  public static final int _TIMESTAMPTZ_TYPE_OID = 1185;
  public static final int INTERVAL_TYPE_OID = 1186;
  public static final int _INTERVAL_TYPE_OID = 1187;
  public static final int _NUMERIC_TYPE_OID = 1231;
  public static final int TIMETZ_TYPE_OID = 1266;
  public static final int _TIMETZ_TYPE_OID = 1270;
  public static final int BIT_TYPE_OID = 1560;
  public static final int _BIT_TYPE_OID = 1561;
  public static final int VARBIT_TYPE_OID = 1562;
  public static final int _VARBIT_TYPE_OID = 1563;
  public static final int NUMERIC_TYPE_OID = 1700;
  public static final int REFCURSOR_TYPE_OID = 1790;
  public static final int _REFCURSOR_TYPE_OID = 2201;
  public static final int REGPROCEDURE_TYPE_OID = 2202;
  public static final int REGOPER_TYPE_OID = 2203;
  public static final int REGOPERATOR_TYPE_OID = 2204;
  
  /*** Representation. ***/
  private int oid;
  private short length;
  private int modifier;

  public PostgresType(int oid, short length, int modifier) {
    this.oid = oid;
    this.length = length;
    this.modifier = modifier;
  }

  public int getOid() {
    return oid;
  }
  public short getLength() {
    return length;
  }
  public int getModifier() {
    return modifier;
  }

  public static PostgresType fromAIS(Column aisColumn) throws StandardException {
    int oid;
    short length = -1;
    int modifier = -1;

    Type aisType = aisColumn.getType();
    String name = aisType.name();

    if ("varchar".equals(name))
      oid = VARCHAR_TYPE_OID;
    else if ("int".equals(name))
      oid = INT4_TYPE_OID;
    else if ("date".equals(name))
      oid = DATE_TYPE_OID;
    else
      throw new StandardException("Don't know type for " + name);

    if (aisType.fixedSize())
      length = aisType.maxSizeBytes().shortValue();

    switch (aisType.nTypeParameters()) {
    case 1:
      modifier = aisColumn.getTypeParameter1().intValue();
      break;
    }

    return new PostgresType(oid, length, modifier);
  }

  public static final DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

  public byte[] encodeValue(Object value, Column aisColumn, 
                            String encoding, boolean binary) 
      throws IOException, StandardException {
    if (value == null)
      return null;
    try {
      if (binary) {
        throw new StandardException("Binary encoding not yet supported.");
      }
      else {
        switch (oid) {
        case DATE_TYPE_OID:
          if (value instanceof Date)
            value = dateFormatter.format((Date)value);
          else
            value = EncoderFactory.DATE.decodeToString((Long)value);
          break;
        }
      }
      return value.toString().getBytes(encoding);
    }
    catch (UnsupportedEncodingException ex) {
      throw new StandardException(ex);
    }
  }

}
