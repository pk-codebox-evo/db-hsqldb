/* Copyright (c) 2001-2009, The HSQL Development Group
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


package org.hsqldb;

import java.math.BigDecimal;

import org.hsqldb.HsqlNameManager.HsqlName;
import org.hsqldb.lib.ArrayUtil;
import org.hsqldb.lib.HsqlArrayList;
import org.hsqldb.lib.IntKeyIntValueHashMap;
import org.hsqldb.types.IntervalType;
import org.hsqldb.types.NumberType;
import org.hsqldb.types.TimeData;
import org.hsqldb.types.Type;

/**
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 1.9.0
 * @since 1.9.0
 */
public class ParserBase {

    private Scanner scanner;
    protected Token token;

    //
    protected boolean       isRecording;
    protected HsqlArrayList recordedStatement;

    //
    protected boolean isCheckOrTriggerCondition;
    protected boolean isSchemaDefinition;
    protected int     parsePosition;
    static final BigDecimal LONG_MAX_VALUE_INCREMENT =
        BigDecimal.valueOf(Long.MAX_VALUE).add(BigDecimal.valueOf(1));

    /**
     * Constructs a new BaseParser object with the given context.
     *
     * @param t the token source from which to parse commands
     */
    ParserBase(Scanner t) {
        scanner = t;
        token   = scanner.token;
    }

    public Scanner getScanner() {
        return scanner;
    }

    public int getParsePosition() {
        return parsePosition;
    }

    public void setParsePosition(int parsePosition) {
        this.parsePosition = parsePosition;
    }

    /**
     *  Resets this parse context with the given SQL character sequence.
     *
     * Internal structures are reset as though a new parser were created
     * with the given sql and the originally specified database and session
     *
     * @param sql a new SQL character sequence to replace the current one
     */
    void reset(String sql) {

        scanner.reset(sql);

        //
        parsePosition             = 0;
        isCheckOrTriggerCondition = false;
        isSchemaDefinition        = false;
        isRecording               = false;
        recordedStatement         = null;
    }

    int getPosition() {
        return scanner.getTokenPosition();
    }

    void rewind(int position) throws HsqlException {

        if (position == scanner.getTokenPosition()) {
            return;
        }

        scanner.position(position);

        if (isRecording) {
            int i = recordedStatement.size() - 1;

            for (; i >= 0; i--) {
                Token token = (Token) recordedStatement.get(i);

                if (token.position < position) {
                    break;
                }
            }

            recordedStatement.setSize(i + 1);
        }

        read();
    }

    String getLastPart() {
        return scanner.getPart(parsePosition, scanner.getTokenPosition());
    }

    String getLastPart(int position) {
        return scanner.getPart(position, scanner.getTokenPosition());
    }

    String getLastPartAndCurrent(int position) {
        return scanner.getPart(position, scanner.getPosition());
    }

    String getStatement(int startPosition,
                        short[] startTokens) throws HsqlException {

        int semiPosition = 0;

        while (true) {
            if (token.tokenType == Tokens.SEMICOLON) {
                semiPosition = scanner.getPosition();
            } else if (token.tokenType == Tokens.X_ENDPARSE) {
                if (semiPosition == 0) {
                    break;
                } else {
                    rewind(semiPosition);

                    break;
                }
            } else {
                semiPosition = 0;

                if (ArrayUtil.find(startTokens, token.tokenType) != -1) {
                    break;
                }
            }

            read();
        }

        String sql = scanner.getPart(startPosition, scanner.getPosition());

        return sql;
    }

    //
    void startRecording() {

        recordedStatement = new HsqlArrayList();

        recordedStatement.add(token.duplicate());

        isRecording = true;
    }

    void recordExpressionForToken(ExpressionColumn expression)
    throws HsqlException {

        if (isRecording) {
            Token recordToken =
                (Token) recordedStatement.get(recordedStatement.size() - 1);

            recordToken.columnExpression = expression;
        }
    }

    Token[] getRecordedStatement() {

        isRecording = false;

        recordedStatement.remove(recordedStatement.size() - 1);

        Token[] tokens = new Token[recordedStatement.size()];

        recordedStatement.toArray(tokens);

        recordedStatement = null;

        return tokens;
    }

    void read() throws HsqlException {

        scanner.scanNext();

        if (token.isMalformed) {
            int errorCode = -1;

            switch (token.tokenType) {

                case Tokens.X_MALFORMED_BINARY_STRING :
                    errorCode = ErrorCode.X_42587;
                    break;

                case Tokens.X_MALFORMED_BIT_STRING :
                    errorCode = ErrorCode.X_42588;
                    break;

                case Tokens.X_MALFORMED_UNICODE_STRING :
                    errorCode = ErrorCode.X_42586;
                    break;

                case Tokens.X_MALFORMED_STRING :
                    errorCode = ErrorCode.X_42584;
                    break;

                case Tokens.X_UNKNOWN_TOKEN :
                    errorCode = ErrorCode.X_42582;
                    break;

                case Tokens.X_MALFORMED_NUMERIC :
                    errorCode = ErrorCode.X_42585;
                    break;

                case Tokens.X_MALFORMED_COMMENT :
                    errorCode = ErrorCode.X_42589;
                    break;

                case Tokens.X_MALFORMED_IDENTIFIER :
                    errorCode = ErrorCode.X_42583;
                    break;
            }

            throw Error.error(errorCode);
        }

        if (isRecording) {
            Token dup = token.duplicate();

            dup.position = scanner.getTokenPosition();

            recordedStatement.add(dup);
        }
    }

    boolean isReservedKey() {
        return scanner.token.isReservedIdentifier;
    }

    boolean isCoreReservedKey() {
        return scanner.token.isCoreReservedIdentifier;
    }

    boolean isNonReservedIdentifier() {

        return !scanner.token.isReservedIdentifier
               && (scanner.token.isUndelimitedIdentifier
                   || scanner.token.isDelimitedIdentifier);
    }

    void checkIsNonReservedIdentifier() throws HsqlException {

        if (!isNonReservedIdentifier()) {
            throw unexpectedToken();
        }
    }

    boolean isNonCoreReservedIdentifier() {

        return !scanner.token.isCoreReservedIdentifier
               && (scanner.token.isUndelimitedIdentifier
                   || scanner.token.isDelimitedIdentifier);
    }

    void checkIsNonCoreReservedIdentifier() throws HsqlException {

        if (!isNonCoreReservedIdentifier()) {
            throw unexpectedToken();
        }
    }

    boolean isIdentifier() {
        return scanner.token.isUndelimitedIdentifier
               || scanner.token.isDelimitedIdentifier;
    }

    void checkIsIdentifier() throws HsqlException {

        if (!isIdentifier()) {
            throw unexpectedToken();
        }
    }

    boolean isDelimitedIdentifier() {
        return scanner.token.isDelimitedIdentifier;
    }

    void checkIsDelimitedIdentifier() throws HsqlException {

        if (token.tokenType != Tokens.X_DELIMITED_IDENTIFIER) {
            throw Error.error(ErrorCode.X_42569);
        }
    }

    void checkIsNotQuoted() throws HsqlException {

        if (token.tokenType == Tokens.X_DELIMITED_IDENTIFIER) {
            throw unexpectedToken();
        }
    }

    void checkIsValue() throws HsqlException {

        if (token.tokenType != Tokens.X_VALUE) {
            throw unexpectedToken();
        }
    }

    void checkIsValue(int dataTypeCode) throws HsqlException {

        if (token.tokenType != Tokens.X_VALUE
                || token.dataType.typeCode != dataTypeCode) {
            throw unexpectedToken();
        }
    }

    void checkIsThis(int type) throws HsqlException {

        if (token.tokenType != type) {
            throw unexpectedToken();
        }
    }

    boolean isUndelimitedSimpleName() {
        return token.isUndelimitedIdentifier && token.namePrefix == null;
    }

    boolean isDelimitedSimpleName() {
        return token.isDelimitedIdentifier && token.namePrefix == null;
    }

    boolean isSimpleName() {
        return isNonReservedIdentifier() && token.namePrefix == null;
    }

    void checkIsSimpleName() throws HsqlException {

        if (!isSimpleName()) {
            throw unexpectedToken();
        }
    }

    void readQuotedString() throws HsqlException {

        if (token.dataType.typeCode != Types.SQL_CHAR) {
            throw Error.error(ErrorCode.X_42565);
        }
    }

    void readThis(int tokenId) throws HsqlException {

        if (token.tokenType != tokenId) {
            String required = Tokens.getKeyword(tokenId);

            throw unexpectedTokenRequire(required);
        }

        read();
    }

    boolean readIfThis(int tokenId) throws HsqlException {

        if (token.tokenType == tokenId) {
            read();

            return true;
        }

        return false;
    }

    int readInteger() throws HsqlException {

        boolean minus = false;

        if (token.tokenType == Tokens.MINUS) {
            minus = true;

            read();
        }

        checkIsValue();

        if (minus && token.dataType.typeCode == Types.SQL_BIGINT
                && ((Number) token.tokenValue).longValue()
                   == -(long) Integer.MIN_VALUE) {
            read();

            return Integer.MIN_VALUE;
        }

        if (token.dataType.typeCode != Types.SQL_INTEGER) {
            throw Error.error(ErrorCode.X_42565);
        }

        int val = ((Number) token.tokenValue).intValue();

        if (minus) {
            val = -val;
        }

        read();

        return val;
    }

    long readBigint() throws HsqlException {

        boolean minus = false;

        if (token.tokenType == Tokens.MINUS) {
            minus = true;

            read();
        }

        checkIsValue();

        if (minus && token.dataType.typeCode == Types.SQL_NUMERIC
                && LONG_MAX_VALUE_INCREMENT.equals(token.tokenValue)) {
            read();

            return Long.MIN_VALUE;
        }

        if (token.dataType.typeCode != Types.SQL_INTEGER
                && token.dataType.typeCode != Types.SQL_BIGINT) {
            throw Error.error(ErrorCode.X_42565);
        }

        long val = ((Number) token.tokenValue).longValue();

        if (minus) {
            val = -val;
        }

        read();

        return val;
    }

    Expression readDateTimeIntervalLiteral() throws HsqlException {

        int pos = getPosition();

        switch (token.tokenType) {

            case Tokens.DATE : {
                read();

                if (token.tokenType != Tokens.X_VALUE
                        || token.dataType.typeCode != Types.SQL_CHAR) {
                    break;
                }

                String s = token.tokenString;

                read();

                Object date = scanner.newDate(s);

                return new ExpressionValue(date, Type.SQL_DATE);
            }
            case Tokens.TIME : {
                read();

                if (token.tokenType != Tokens.X_VALUE
                        || token.dataType.typeCode != Types.SQL_CHAR) {
                    break;
                }

                String s = token.tokenString;

                read();

                TimeData value    = scanner.newTime(s);
                Type     dataType = scanner.dateTimeType;

                return new ExpressionValue(value, dataType);
            }
            case Tokens.TIMESTAMP : {
                read();

                if (token.tokenType != Tokens.X_VALUE
                        || token.dataType.typeCode != Types.SQL_CHAR) {
                    break;
                }

                String s = token.tokenString;

                read();

                Object date     = scanner.newTimestamp(s);
                Type   dataType = scanner.dateTimeType;

                return new ExpressionValue(date, dataType);
            }
            case Tokens.INTERVAL : {
                boolean minus = false;

                read();

                if (token.tokenType == Tokens.MINUS) {
                    read();

                    minus = true;
                } else if (token.tokenType == Tokens.PLUS) {
                    read();
                }

                if (token.tokenType != Tokens.X_VALUE
                        || token.dataType.typeCode != Types.SQL_CHAR) {
                    break;
                }

                String s = token.tokenString;

                read();

                IntervalType dataType = readIntervalType();
                Object       interval = scanner.newInterval(s, dataType);

                dataType = (IntervalType) scanner.dateTimeType;

                if (minus) {
                    interval = dataType.negate(interval);
                }

                return new ExpressionValue(interval, dataType);
            }
            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "Parser");
        }

        rewind(pos);

        return null;
    }

    IntervalType readIntervalType() throws HsqlException {

        int precision = -1;
        int scale     = -1;
        int startToken;
        int endToken;

        startToken = endToken = token.tokenType;

        read();

        if (token.tokenType == Tokens.OPENBRACKET) {
            read();

            precision = readInteger();

            if (precision <= 0) {
                throw Error.error(ErrorCode.X_42592);
            }

            if (token.tokenType == Tokens.COMMA) {
                if (startToken != Tokens.SECOND) {
                    throw unexpectedToken();
                }

                read();

                scale = readInteger();

                if (scale < 0) {
                    throw Error.error(ErrorCode.X_42592);
                }
            }

            readThis(Tokens.CLOSEBRACKET);
        }

        if (token.tokenType == Tokens.TO) {
            read();

            endToken = token.tokenType;

            read();
        }

        if (token.tokenType == Tokens.OPENBRACKET) {
            if (endToken != Tokens.SECOND || endToken == startToken) {
                throw unexpectedToken();
            }

            read();

            scale = readInteger();

            if (scale < 0) {
                throw Error.error(ErrorCode.X_42592);
            }

            readThis(Tokens.CLOSEBRACKET);
        }

        int startIndex = ArrayUtil.find(Tokens.SQL_INTERVAL_FIELD_CODES,
                                        startToken);
        int endIndex = ArrayUtil.find(Tokens.SQL_INTERVAL_FIELD_CODES,
                                      endToken);

        return IntervalType.getIntervalType(startIndex, endIndex, precision,
                                            scale);
    }

    static int getExpressionType(int tokenT) {

        int type = expressionTypeMap.get(tokenT, -1);

        if (type == -1) {
            throw Error.runtimeError(ErrorCode.U_S0500, "Parser");
        }

        return type;
    }

    private static final IntKeyIntValueHashMap expressionTypeMap =
        new IntKeyIntValueHashMap(37);

    static {

        // comparison
        expressionTypeMap.put(Tokens.EQUALS, OpTypes.EQUAL);
        expressionTypeMap.put(Tokens.GREATER, OpTypes.GREATER);
        expressionTypeMap.put(Tokens.LESS, OpTypes.SMALLER);
        expressionTypeMap.put(Tokens.GREATER_EQUALS, OpTypes.GREATER_EQUAL);
        expressionTypeMap.put(Tokens.LESS_EQUALS, OpTypes.SMALLER_EQUAL);
        expressionTypeMap.put(Tokens.NOT_EQUALS, OpTypes.NOT_EQUAL);

        // aggregates
        expressionTypeMap.put(Tokens.COUNT, OpTypes.COUNT);
        expressionTypeMap.put(Tokens.MAX, OpTypes.MAX);
        expressionTypeMap.put(Tokens.MIN, OpTypes.MIN);
        expressionTypeMap.put(Tokens.SUM, OpTypes.SUM);
        expressionTypeMap.put(Tokens.AVG, OpTypes.AVG);
        expressionTypeMap.put(Tokens.EVERY, OpTypes.EVERY);
        expressionTypeMap.put(Tokens.ANY, OpTypes.SOME);
        expressionTypeMap.put(Tokens.SOME, OpTypes.SOME);
        expressionTypeMap.put(Tokens.STDDEV_POP, OpTypes.STDDEV_POP);
        expressionTypeMap.put(Tokens.STDDEV_SAMP, OpTypes.STDDEV_SAMP);
        expressionTypeMap.put(Tokens.VAR_POP, OpTypes.VAR_POP);
        expressionTypeMap.put(Tokens.VAR_SAMP, OpTypes.VAR_SAMP);
    }

    HsqlException unexpectedToken(String tokenS) {
        return Error.error(ErrorCode.X_42581, tokenS);
    }

    HsqlException unexpectedTokenRequire(String required) {

        if (token.tokenType == Tokens.X_ENDPARSE) {
            return Error.error(ErrorCode.X_42590, ErrorCode.TOKEN_REQUIRED,
                               new Object[] {
                "", required
            });
        }

        String tokenS;

        if (token.charsetSchema != null) {
            tokenS = token.charsetSchema;
        } else if (token.charsetName != null) {
            tokenS = token.charsetName;
        } else if (token.namePrePrefix != null) {
            tokenS = token.namePrePrefix;
        } else if (token.namePrefix != null) {
            tokenS = token.namePrefix;
        } else {
            tokenS = token.tokenString;
        }

        return Error.error(ErrorCode.X_42581, ErrorCode.TOKEN_REQUIRED,
                           new Object[] {
            tokenS, required
        });
    }

    HsqlException unexpectedToken() {

        if (token.tokenType == Tokens.X_ENDPARSE) {
            return Error.error(ErrorCode.X_42590);
        }

        String tokenS;

        if (token.charsetSchema != null) {
            tokenS = token.charsetSchema;
        } else if (token.charsetName != null) {
            tokenS = token.charsetName;
        } else if (token.namePrePrefix != null) {
            tokenS = token.namePrePrefix;
        } else if (token.namePrefix != null) {
            tokenS = token.namePrefix;
        } else {
            tokenS = token.tokenString;
        }

        return Error.error(ErrorCode.X_42581, tokenS);
    }

    HsqlException tooManyIdentifiers() {

        String tokenS;

        if (token.namePrePrefix != null) {
            tokenS = token.namePrePrefix;
        } else if (token.namePrefix != null) {
            tokenS = token.namePrefix;
        } else {
            tokenS = token.tokenString;
        }

        return Error.error(ErrorCode.X_42551, tokenS);
    }

    HsqlException unsupportedFeature() {
        return Error.error(ErrorCode.X_0A501, token.tokenString);
    }

    HsqlException unsupportedFeature(String string) {
        return Error.error(ErrorCode.X_0A501, string);
    }

    public Number convertToNumber(String s,
                                  NumberType type) throws HsqlException {
        return scanner.convertToNumber(s, type);
    }
}
