/* Copyright (c) 2001-2007, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package org.hsqldb.rowio;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

import org.hsqldb.HsqlException;
import org.hsqldb.Trace;
import org.hsqldb.Types;
import org.hsqldb.lib.HsqlByteArrayInputStream;
import org.hsqldb.types.BinaryData;
import org.hsqldb.types.BlobData;
import org.hsqldb.types.ClobData;
import org.hsqldb.types.IntervalMonthData;
import org.hsqldb.types.IntervalSecondData;
import org.hsqldb.types.TimeData;
import org.hsqldb.types.Type;

/**
 * Base class for reading the data for a database row in different formats.
 * Defines the methods that are independent of storage format and declares
 * the format-dependent methods that subclasses should define.
 *
 * @author sqlbob@users (RMP)
 * @author fredt@users
 * @version 1.9.0
 * @since 1.7.0
 */
abstract class RowInputBase extends HsqlByteArrayInputStream {

    static final int NO_POS = -1;

    // fredt - initialisation may be unnecessary as it's done in resetRow()
    protected int filePos = NO_POS;
    protected int size;

    RowInputBase() {
        this(new byte[4]);
    }

    /**
     * Constructor takes a complete row
     */
    RowInputBase(byte[] buf) {

        super(buf);

        size = buf.length;
    }

    public int getPos() {

        if (filePos == NO_POS) {

//                Trace.printSystemOut(Trace.DatabaseRowInput_getPos);
        }

        return (filePos);
    }

    public int getSize() {
        return size;
    }

// fredt@users - comment - methods used for node and type data

    public abstract int readType() throws IOException;

    public abstract String readString() throws IOException;

// fredt@users - comment - methods used for SQL types
    protected abstract boolean checkNull() throws IOException;

    protected abstract String readChar(Type type)
    throws IOException, HsqlException;

    protected abstract Integer readSmallint()
    throws IOException, HsqlException;

    protected abstract Integer readInteger()
    throws IOException, HsqlException;

    protected abstract Long readBigint() throws IOException, HsqlException;

    protected abstract Double readReal() throws IOException, HsqlException;

    protected abstract BigDecimal readDecimal()
    throws IOException, HsqlException;

    protected abstract Boolean readBit() throws IOException, HsqlException;

    protected abstract TimeData readTime(Type type)
    throws IOException, HsqlException;

    protected abstract Date readDate(Type type) throws IOException, HsqlException;

    protected abstract Timestamp readTimestamp(Type type)
    throws IOException, HsqlException;

    protected abstract IntervalMonthData readYearMonthInterval(Type type)
    throws IOException, HsqlException;

    protected abstract IntervalSecondData readDaySecondInterval(Type type)
    throws IOException, HsqlException;

    protected abstract Object readOther() throws IOException, HsqlException;

    protected abstract BinaryData readBinary()
    throws IOException, HsqlException;

    protected abstract ClobData readClob() throws IOException, HsqlException;

    protected abstract BlobData readBlob() throws IOException, HsqlException;

    /**
     *  reads row data from a stream using the JDBC types in colTypes
     *
     * @param  colTypes
     * @return
     * @throws  IOException
     * @throws  HsqlException
     */
    public Object[] readData(Type[] colTypes)
    throws IOException, HsqlException {

        int      l    = colTypes.length;
        Object[] data = new Object[l];
        Object   o;
        Type     type;

        for (int i = 0; i < l; i++) {
            if (checkNull()) {
                continue;
            }

            o    = null;
            type = colTypes[i];

            switch (type.type) {

                case Types.SQL_ALL_TYPES :
                case Types.SQL_CHAR :
                case Types.SQL_VARCHAR :
                case Types.VARCHAR_IGNORECASE :
                    o = readChar(type);
                    break;

                case Types.TINYINT :
                case Types.SQL_SMALLINT :
                    o = readSmallint();
                    break;

                case Types.SQL_INTEGER :
                    o = readInteger();
                    break;

                case Types.SQL_BIGINT :
                    o = readBigint();
                    break;

                //fredt although REAL is now Double, it is read / written in
                //the old format for compatibility
                case Types.SQL_REAL :
                case Types.SQL_FLOAT :
                case Types.SQL_DOUBLE :
                    o = readReal();
                    break;

                case Types.SQL_NUMERIC :
                case Types.SQL_DECIMAL :
                    o = readDecimal();
                    break;

                case Types.SQL_DATE :
                    o = readDate(type);
                    break;

                case Types.SQL_TIME :
                    o = readTime(type);
                    break;

                case Types.SQL_TIMESTAMP :
                    o = readTimestamp(type);
                    break;

                case Types.SQL_INTERVAL_YEAR :
                case Types.SQL_INTERVAL_YEAR_TO_MONTH :
                case Types.SQL_INTERVAL_MONTH :
                    o = readYearMonthInterval(type);
                    break;

                case Types.SQL_INTERVAL_DAY :
                case Types.SQL_INTERVAL_DAY_TO_HOUR :
                case Types.SQL_INTERVAL_DAY_TO_MINUTE :
                case Types.SQL_INTERVAL_DAY_TO_SECOND :
                case Types.SQL_INTERVAL_HOUR :
                case Types.SQL_INTERVAL_HOUR_TO_MINUTE :
                case Types.SQL_INTERVAL_HOUR_TO_SECOND :
                case Types.SQL_INTERVAL_MINUTE :
                case Types.SQL_INTERVAL_MINUTE_TO_SECOND :
                case Types.SQL_INTERVAL_SECOND :
                    o = readDaySecondInterval(type);
                    break;

                case Types.SQL_BOOLEAN :
                    o = readBit();
                    break;

                case Types.OTHER :
                    o = readOther();
                    break;

                case Types.SQL_CLOB :
                    o = readClob();
                    break;

                case Types.SQL_BLOB :
                    o = readBlob();
                    break;

                case Types.SQL_BINARY :
                case Types.SQL_VARBINARY :
                    o = readBinary();
                    break;

                default :
                    throw Trace.runtimeError(
                        Trace.UNSUPPORTED_INTERNAL_OPERATION,
                        "RowInputBase " + type.getName());
            }

            data[i] = o;
        }

        return data;
    }

    /**
     *  Used to reset the row, ready for a new row to be written into the
     *  byte[] buffer by an external routine.
     *
     */
    public void resetRow(int filepos, int rowsize) throws IOException {

        mark = 0;

        reset();

        if (buf.length < rowsize) {
            buf = new byte[rowsize];
        }

        filePos = filepos;
        size    = count = rowsize;
        pos     = 4;
        buf[0]  = (byte) ((rowsize >>> 24) & 0xFF);
        buf[1]  = (byte) ((rowsize >>> 16) & 0xFF);
        buf[2]  = (byte) ((rowsize >>> 8) & 0xFF);
        buf[3]  = (byte) ((rowsize >>> 0) & 0xFF);
    }

    public byte[] getBuffer() {
        return buf;
    }

    public int skipBytes(int n) throws IOException {
        throw Trace.runtimeError(Trace.UNSUPPORTED_INTERNAL_OPERATION,
                                 "RowInputBase");
    }

    public String readLine() throws IOException {
        throw Trace.runtimeError(Trace.UNSUPPORTED_INTERNAL_OPERATION,
                                 "RowInputBase");
    }
}
