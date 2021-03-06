/* Copyright (c) 2001-2004, The HSQL Development Group
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


package org.hsqldb.util;

import java.io.IOException;
import java.io.PrintStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.StringTokenizer;

/* $Id: SqlFile.java,v 1.42 2004/02/21 20:19:49 fredt Exp $ */

/**
 * Encapsulation of a sql text file like 'myscript.sql'.
 * The ultimate goal is to run the execute() method to feed the SQL
 * commands within the file to a jdbc connection.
 *
 * Some implementation comments and variable names use keywords based
 * on the following definitions.  <UL>
 * <LI> COMMAND = Statement || SpecialCommand || BufferCommand
 * Statement = SQL statement like "SQL Statement;"
 * SpecialCommand =  Special Command like "\x arg..."
 * BufferCommand =  Editing/buffer command like ":s/this/that/"
 *
 * When entering SQL statements, you are always "appending" to the
 * "current" command (not the "buffer", which is a different thing).
 * All you can do to the current command is append new lines to it,
 * execute it, or save it to buffer.
 *
 * In general, the special commands mirror those of Postgresql's psql,
 * but SqlFile handles command editing much different from Postgresql
 * because of Java's lack of support for raw tty I/O.
 * The \p special command, in particular, is very different from psql's.
 * Also, to keep the code simpler, we're sticking to only single-char
 * special commands until we really need more.
 *
 * Buffer commands are uniqueue to SQLFile.  The ":" commands allow 
 * you to edit the buffer and to execute the buffer.
 *
 * The command history consists only of SQL Statements (i.e., special
 * commands and editing commands are not stored for later viewing or
 * editing).
 *
 * Most of the Special Commands and all of the Editing Commands are for
 * interactive use only.
 *
 * @version $Revision: 1.42 $
 * @author Blaine Simpson
 */
public class SqlFile {

    private File             file;
    private boolean          interactive;
    private String           primaryPrompt    = "sql> ";
    private String           contPrompt       = "  +> ";
    private Connection       curConn          = null;
    private String[]         statementHistory = new String[10];
    private boolean          htmlMode         = false;

    // Ascii field separator blanks
    final private static int SEP_LEN          = 2;
    final private static String DIVIDER =
        "-----------------------------------------------------------------";
    final private static String SPACES =
        "                                                                 ";
    final private static String BANNER =
        "SqlFile processor.  Enter \"\\q\" to quit,\n"
        + "    \"\\?\" to list Special Commands, "
        + "\":?\" to list Buffer/Editing commands\n\n"
        + "SPECIAL Commands begin with '\\' and execute when you hit ENTER.\n"
        + "BUFFER Commands begin with ':' and execute when you hit ENTER.\n"
        + "An empty line within an SQL Statement moves it into the buffer.\n"
        + "All other lines comprise SQL Statements.\n"
        + "SQL Statement lines ending with ';' cause the current Statement to be executed.\n"
        + "SQL Statements consisting of only /* SQL comment */ are not executed, therefore\n"
        + "    you can comment scripts like \"/* This is a comment */;\"\n";
    final private static String BUFFER_HELP_TEXT =
          "BUFFER Commands (only available for interactive use).\n"
        + "In place of \"3\" below, you can use nothing for the previous command, or\n"
        + "an integer \"X\" to indicate the Xth previous command.\n\n"
        + "    :?          Help\n"
        + "    :a          Enter append mode with contents of buffer as current command\n"
        + "    :l          List current contents of buffer\n"
        + "    :s/from/to/ Substitute \"to\" for all occurrences of \"from\"\n"
        + "                ('$'s in \"from\" and \"to\" represent line breaks)\n"
        + "                (use \":s/from//\" to delete 'from' strings)\n"
        + "                ('/' can actually be any char which occurs in\n"
        + "                 neither \"to\" nor \"from\")\n"
        + "    :;          Execute current buffer as an SQL Statement\n"
        ;
    final private static String HELP_TEXT = "SPECIAL Commands.\n"
        + "* commands only available for interactive use.\n"
        + "In place of \"3\" below, you can use nothing for the previous command, or\n"
        + "an integer \"X\" to indicate the Xth previous command.\n\n"
        + "    \\?                   Help\n"
        + "    \\p [line to print]   Print string to stdout\n"
        + "    \\dt                  List tables\n"
        + "    \\d TABLENAME         Describe table\n"
        + "    \\H                   Toggle HTML output mode\n"
        + "    \\* [true|false]      Continue upon errors (a.o.t. abort upon error)\n"
        + "    \\s                   * Show previous commands (i.e. command history)\n"
        + "    \\-[3]                * reload a command to buffer (for / commands)\n"
        + "    \\q                   Quit (alternatively, end input like Ctrl-Z or Ctrl-D)\n\n"
        + "EXAMPLE:  To show previous commands then edit and execute the 3rd-to-last:\n"
        + "    \\s\n" + "    \\-3\n" + "    :;\n";

    /**
     * Interpret lines of input file as SQL Statements, Comments, 
     * Special Commands, and Buffer Commands.
     * Most Special Commands and many Buffer commands are only for
     * interactive use.
     * 
     * @param inFile  inFile of null means to read stdin.
     * @param inInteractive  If true, prompts are printed, the interactive
     *                       Special commands are enabled, and
     *                       continueOnError defaults to true.
     */
    SqlFile(File inFile, boolean inInteractive) throws IOException {

        file        = inFile;
        interactive = inInteractive;

        if (file != null &&!file.canRead()) {
            throw new IOException("Can't read SQL file '" + file + "'");
        }
    }

    /**
     * Constructor for reading stdin instead of a file for commands.
     *
     * @see #SqlFile(File,boolean)
     */
    SqlFile(boolean inInteractive) throws IOException {
        this(null, inInteractive);
    }

    /**
     * Process all the commands on stdin.
     *
     * @param conn The JDBC connection to use for SQL Commands.
     * @see #execute(Connection,PrintStream,PrintStream)
     */
    public void execute(Connection conn)
    throws IOException, SqlToolError, SQLException {
        execute(conn, System.out, System.err);
    }

    private String      curCommand = null;
    private int         curLinenum = -1;
    private int         curHist    = -1;
    private PrintStream psStd      = null;
    private PrintStream psErr      = null;
    StringBuffer        stringBuffer  = new StringBuffer();
    /*
     * This is reset upon each execute() invocation (to true if interactive,
     * false otherwise).
     */
    private boolean continueOnError = false;
    static private final String DEFAULT_CHARSET = "US-ASCII";

    /**
     * Process all the commands in the file (or stdin) associated with
     * "this" object.
     * Run SQL in the file through the given database connection.
     *
     * This is synchronized so that I can use object variables to keep
     * track of current line number, command, connection, i/o streams, etc.
     *
     * Sets encoding character set to that specified with System Property
     * 'sqlfile.charset'.  Defaults to "US-ASCII".
     *
     * @param conn The JDBC connection to use for SQL Commands.
     */
    public synchronized void execute(Connection conn, PrintStream stdIn,
                                     PrintStream errIn)
                                     throws IOException, SqlToolError,
                                         SQLException {

        psStd      = stdIn;
        psErr      = errIn;
        curConn    = conn;
        curLinenum = -1;

        String inputLine;
        String trimmedCommand;
        String trimmedInput;
        String deTerminated;

        continueOnError = interactive;

        BufferedReader br = null;
        String specifiedCharSet = System.getProperty("sqlfile.charset");

        try {
            br = new BufferedReader(new InputStreamReader((file == null)
                    ? System.in
                    : new FileInputStream(file), ((specifiedCharSet == null)
                            ? DEFAULT_CHARSET 
                                                  : specifiedCharSet)));
            curLinenum = 0;

            if (interactive) {
                stdprint(BANNER);
            }

            while (true) {
                if (interactive) {
                    psStd.print((stringBuffer.length() == 0) ? primaryPrompt
                                                          : contPrompt);
                }

                inputLine = br.readLine();

                if (inputLine == null) {
                    /*
                     * This is because interactive EOD on some OSes doesn't
                     * send a line-break, resulting in no linebreak at all
                     * after the SqlFile prompt or whatever happens to be 
                     * on their screen.
                     */
                    if (interactive) {
                        psStd.println();
                    }

                    break;
                }

                curLinenum++;

                trimmedInput = inputLine.trim();

                try {

                    // This is the try for SQLException.  SQLExceptions are
                    // normally thrown below in Statement processing, but
                    // could be called up above if a Special processing
                    // executes a SQL command from history.
                    if (stringBuffer.length() == 0) {

                        // This is just to filter out useless newlines at
                        // beginning of commands.
                        if (trimmedInput.length() == 0) {
                            continue;
                        }

                        if (trimmedInput.charAt(0) == '\\') {
                            try {
                                processSpecial(trimmedInput.substring(1));
                            } catch (QuitNow qn) {
                                return;
                            } catch (BadSpecial bs) {
                                errprint("Error at '"
                                         + ((file == null) ? "stdin"
                                        : file.toString()) + "' line "
                                                           + curLinenum
                                                           + ":\n\""
                                                           + inputLine
                                                           + "\"\n"
                                                           + bs.getMessage());

                                if (!continueOnError) {
                                    throw new SqlToolError(bs);
                                }
                            }

                            continue;
                        }

                        if (interactive && trimmedInput.charAt(0) == ':') {
                            try {
                                processBuffer(trimmedInput.substring(1));
                            } catch (BadSpecial bs) {
                                errprint("Error at '"
                                         + ((file == null) ? "stdin"
                                        : file.toString()) + "' line "
                                                           + curLinenum
                                                           + ":\n\""
                                                           + inputLine
                                                           + "\"\n"
                                                           + bs.getMessage());

                                if (!continueOnError) {
                                    throw new SqlToolError(bs);
                                }
                            }

                            continue;
                        }
                    }

                    if (trimmedInput.length() == 0) {
                        if (interactive) {
                            setBuf(stringBuffer.toString());
                            stringBuffer.setLength(0);
                            stdprint("Current input moved into buffer.");
                        }

                        continue;
                    }

                    deTerminated = deTerminated(inputLine);

                    // A null terminal line (i.e., /\s*;\s*$/) is never useful.
                    if (!trimmedInput.equals(";")) {
                        if (stringBuffer.length() > 0) {
                            stringBuffer.append('\n');
                        }

                        stringBuffer.append((deTerminated == null) ? inputLine
                                : deTerminated);
                    }

                    if (deTerminated == null) {
                        continue;
                    }

                    // If we reach here, then stringBuffer contains a complete
                    // SQL command.
                    curCommand     = stringBuffer.toString();
                    trimmedCommand = curCommand.trim();

                    if (trimmedCommand.length() == 0) {
                        throw new SQLException("Empty SQL Statement");
                    }

                    // If not completely SQL comment
                    if ((!trimmedCommand.startsWith(
                            "/*")) || (!trimmedCommand.endsWith(
                            "*/")) || (trimmedCommand.indexOf(
                                "/*", 2) > -1) || (trimmedCommand.lastIndexOf(
                                "*/", trimmedCommand.length() - 4) > -1)) {
                        if (interactive) {
                            setBuf(curCommand);
                        }

                        processStatement();
                    }
                } catch (SQLException se) {
                    errprint("SQL Error at '" + ((file == null) ? "stdin"
                                                                : file.toString()) + "' line "
                                                                + curLinenum
                                                                    + ":\n\""
                                                                        + curCommand
                                                                            + "\"\n"
                                                                                + se
                                                                                .getMessage());

                    if (!continueOnError) {
                        throw se;
                    }
                }

                stringBuffer.setLength(0);
            }

            if (stringBuffer.length() != 0) {
                errprint("Unterminated input:  [" + stringBuffer + ']');
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }
    }

    /**
     * Returns a copy of given string without a terminating semicolon.
     * If there is no terminating semicolon, null is returned.
     *
     * @param inString Base String, which will not be modified (because
     *                 a "copy" will be returned).
     */
    static private String deTerminated(String inString) {

        int index = inString.lastIndexOf(';');

        if (index < 0) {
            return null;
        }

        for (int i = index + 1; i < inString.length(); i++) {
            if (!Character.isWhitespace(inString.charAt(i))) {
                return null;
            }
        }

        return inString.substring(0, index);
    }

    /**
     * Utility nested Exception class for internal use.
     */
    private class BadSpecial extends Exception {

        private BadSpecial(String s) {
            super(s);
        }
    }

    /**
     * Utility nested Exception class for internal use.
     */
    private class QuitNow extends Exception {}

    /**
     * Utility nested Exception class for internal use.
     */
    private class BadSwitch extends Exception {

        private BadSwitch(int i) {
            super(Integer.toString(i));
        }
    }

    /**
     * Process a Buffer/Edit Command.
     *
     * @param inString Complete command, less the leading ':' character.
     * @throws SQLException Passed through from processStatement()
     * @throws BadSpecial Runtime error()
     */
    private void processBuffer(String inString)
    throws BadSpecial, SQLException {

        int    index = 0;
        int    special;
        char   commandChar = 'i';
        String other = null;

        if (inString.length() > 0) {
            commandChar = inString.charAt(0);
            other = inString.substring(1).trim();

            if (other.length() == 0) {
                other = null;
        }
        }

        switch (commandChar) {

            case ';' :
                curCommand = commandFromHistory(0);

                stdprint("Executing command from buffer:\n" + curCommand
                         + '\n');
                processStatement();

                return;

            case 'a' :
            case 'A' :
                stringBuffer.append(commandFromHistory(0));

                return;

            case 'l' :
            case 'L' :
                stdprint("Current Buffer:\n" + commandFromHistory(0));

                return;

            case 's' :
            case 'S' :
                try {
                    String fromHist = commandFromHistory(0);
                    StringBuffer sb = new StringBuffer(fromHist);

                    if (other == null) {
                        throw new BadSwitch(0);
                    }

                    String delim = other.substring(0, 1);
                    StringTokenizer toker = new StringTokenizer(other, delim,
                        true);

                    if (toker.countTokens() < 4
                            ||!toker.nextToken().equals(delim)) {
                        throw new BadSwitch(1);
                            }

                    String from = toker.nextToken().replace('$', '\n');

                    if (!toker.nextToken().equals(delim)) {
                        throw new BadSwitch(2);
                    }

                    String to = toker.nextToken().replace('$', '\n');

                    if (to.equals(delim)) {
                        to = "";
                    } else {
                        if (toker.countTokens() < 1
                                ||!toker.nextToken().equals(delim)) {
                            throw new BadSwitch(3);
                         }
                    }

                    if (toker.countTokens() > 0) {
                        throw new BadSwitch(4);
                    }

                    int i = fromHist.length();

                    while ((i = fromHist.lastIndexOf(from, i - 1)) > -1) {
                        sb.replace(i, i + from.length(), to);
                    }

                    statementHistory[curHist] = sb.toString();

                    stdprint("Current Buffer:\n" + commandFromHistory(0));
                } catch (BadSwitch badswitch) {
                    throw new BadSpecial(
                            "Switch syntax:  \":s/from this/to that/\".  "
                            + "Use '$' for line separations.  ["
                            + badswitch.getMessage() + ']');
                }

                return;

            case '?' :
                stdprint(BUFFER_HELP_TEXT);

                return;
        }

        throw new BadSpecial("Unknown Buffer Command");
    }

    /**
     * Process a Special Command.
     *
     * @param inString Complete command, less the leading '\' character.
     * @throws SQLException Passed through from processStatement()
     * @throws BadSpecial Runtime error()
     * @throws QuitNot Command execution (but not the JVM!) should stop
     */
    private void processSpecial(String inString)
    throws BadSpecial, QuitNow, SQLException {

        int    index = 0;
        int    special;
        String arg1,
               other = null;

        if (inString.length() < 1) {
            throw new BadSpecial("Null special command");
        }

        StringTokenizer toker = new StringTokenizer(inString);

        arg1 = toker.nextToken();

        if (toker.hasMoreTokens()) {
            other = toker.nextToken("").trim();
        }

        switch (arg1.charAt(0)) {

            case 'q' :
                throw new QuitNow();
            case 'H' :
                htmlMode = !htmlMode;

                stdprint(htmlMode ? "<HTML>"
                                  : "</HTML>");

                return;

            case 'd' :
                if (arg1.length() > 1 && arg1.charAt(1) == 't') {
                    listTables();

                    return;
                }

                if (arg1.length() == 1 && other != null) {
                    describe(other);

                    return;
                }
                break;

            case 'p' :
                if (other == null) {
                    stdprint();
                } else {
                    stdprint(other);
                }

                return;

            case '*' :
                if (other != null) {

                    // But remember that we have to abort on some I/O errors.
                    continueOnError = Boolean.valueOf(other).booleanValue();
                }

                stdprint("Continue-on-error is set to: " + continueOnError);

                return;

            case 's' :
                showHistory();

                return;

            case '-' :
                int     commandsAgo = 0;
                String  numStr;

                numStr = (arg1.length() == 1) ? null
                                              : arg1.substring(1,
                                              arg1.length());

                if (numStr == null) {
                    commandsAgo = 0;
                } else {
                    try {
                        commandsAgo = Integer.parseInt(numStr);
                    } catch (NumberFormatException nfe) {
                        throw new BadSpecial("Malformatted command number");
                    }
                }

                setBuf(commandFromHistory(commandsAgo));
                stdprint(
                    "RESTORED following command to buffer.  Enter \":?\" "
                        + "to see buffer commands:\n" + commandFromHistory(0));

                return;

            case '?' :
                stdprint(HELP_TEXT);

                return;

            case '!' :
                System.err.println("Run '" + ((other == null) ? "SHELL"
                                                              : other) + "'");

                return;
        }

        throw new BadSpecial("Unknown Special Command");
    }

    /**
     * Encapsulates normal output.
     *
     * Conditionally HTML-ifies output.
     */
    private void stdprint() {

        if (htmlMode) {
            psStd.println("<BR>");
        } else {
            psStd.println();
        }
    }

    /**
     * Encapsulates error output.
     *
     * Conditionally HTML-ifies error output.
     */
    private void errprint(String s) {

        psErr.println(htmlMode
                      ? ("<DIV style='color:white; background: red; "
                         + "font-weight: bold'>" + s + "</DIV>")
                      : s);
    }

    /**
     * Encapsulates normal output.
     *
     * Conditionally HTML-ifies output.
     */
    private void stdprint(String s) {
        psStd.println(htmlMode ? ("<P>" + s + "</P>")
                               : s);
    }

    static private final int DEFAULT_ELEMENT = 0,
        HSQLDB_ELEMENT  = 1,
        ORACLE_ELEMENT  = 2
    ;

    /** Column numbering starting at 1. */
    static private final int[][] listMDTableCols = {
        {
            2, 3
        },        // Default
        { 3 },       // HSQLDB
        {
            2, 3
        },        // Oracle
    };

    /**
     * Dimensions: [DB-TYPE][COLUMN IN TABLE MD RESULTSET][VALUES]
     *     where 2nd dimension is column number starting at 0.
     *     Nulls permitted.
     */
    static private final String[][][] requireMDTableVals = {
        {
            null, null, null, { "TABLE" }
        },        // Default
        {
            null, null, null, { "TABLE" }
        },        // HSQLDB
        {
            null, null, null, { "TABLE" }
        },        // Oracle
    };

    /**
     * Dimensions: [DB-TYPE][COLUMN IN TABLE MD RESULTSET][VALUES]
     *     where 2nd dimension is column number starting at 0.
     *     Nulls permitted.
     */
    static private final String[][][] prohibitMDTableVals = {
        null,                               // Default
        null,                               // HSQLDB
        {
            null, {
                "SYS", "SYSTEM"
            }
        },        // Oracle
    };

    /**
     * Lists available database tables.
     * This method needs work.  See the implementation comments.
     */
    private void listTables() throws SQLException {

        int[] listSet = null;
        String[][] reqSet = null;
        String[][] prohibSet = null;
        java.sql.DatabaseMetaData md = curConn.getMetaData();
        String dbProductName = md.getDatabaseProductName();

        //System.err.println("DB NAME = (" + dbProductName + ')');
        // Database-specific table filtering.
        String excludePrefix = null;

        if (dbProductName.indexOf("HSQL") > -1) {
            listSet = listMDTableCols[HSQLDB_ELEMENT];
            reqSet = requireMDTableVals[HSQLDB_ELEMENT]; 
            prohibSet = prohibitMDTableVals[HSQLDB_ELEMENT]; 
        } else if (dbProductName.indexOf("Oracle") > -1) {
            listSet = listMDTableCols[ORACLE_ELEMENT];
            reqSet = requireMDTableVals[ORACLE_ELEMENT]; 
            prohibSet = prohibitMDTableVals[ORACLE_ELEMENT]; 
        } else {
            listSet = listMDTableCols[DEFAULT_ELEMENT];
            reqSet = requireMDTableVals[DEFAULT_ELEMENT]; 
            prohibSet = prohibitMDTableVals[DEFAULT_ELEMENT]; 
        }

        displayResultSet(null, md.getTables(null, null, null, null), listSet,
                         reqSet, prohibSet);
    }

    /**
     * Process the currenct command as an SQL Statement
     */
    private void processStatement() throws SQLException {

        Statement statement = curConn.createStatement();

        statement.execute(curCommand);
        displayResultSet(statement, statement.getResultSet(), null, null,
                         null);
    }

    /**
     * Display the given result set for user.
     * The last 3 params are to narrow down records and columns where
     * that can not be done with a where clause (like in metadata queries).
     *
     * @param statement The SQL Statement that the result set is for.
     *                  (I think that this is just for reporting purposes.
     * @param r         The ResultSet to display.
     * @param incCols   Optional list of which columns to include (i.e., if
     *                  given, then other columns will be skipped).
     * @param requireVals  Require one of these Strings in the corresponding
     *                  field, or skip the record.
     * @param prohibVals  Prohibit any of these Strings in the corresponding
     *                  field.  If match, skip the record.
     */
    private void displayResultSet(Statement statement, ResultSet r,
                                  int[] incCols, String[][] requireVals,
                                  String[][] prohibVals) throws SQLException {

        int updateCount = (statement == null) ? -1
                                              : statement.getUpdateCount();

        switch (updateCount) {

            case -1 :
                if (r == null) {
                    stdprint("No result");

                    break;
                }

                ResultSetMetaData m        = r.getMetaData();
                int               cols     = m.getColumnCount();
                int               incCount = (incCols == null) ? cols
                                                               : incCols
                                                                   .length;
                String            val;
                ArrayList         rows        = new ArrayList();
                String[]          headerArray = null;
                String[]          fieldArray;
                int[]             maxWidth = new int[incCount];
                int               insi;
                boolean           skip;
                String            dataType;
                boolean           ok;

                // STEP 1: GATHER DATA
                if (!htmlMode) {
                    for (int i = 0; i < maxWidth.length; i++) {
                        maxWidth[i] = 0;
                    }
                }

                boolean[] rightJust = new boolean[incCount];

                if (incCount > 1) {
                    insi        = -1;
                    headerArray = new String[incCount];

                    for (int i = 1; i <= cols; i++) {
                        if (incCols != null) {
                            skip = true;

                            for (int j = 0; j < incCols.length; j++) {
                                if (i == incCols[j]) {
                                    skip = false;
                                }
                            }

                            if (skip) {
                                continue;
                            }
                        }

                        headerArray[++insi] = m.getColumnLabel(i);
                        dataType            = m.getColumnTypeName(i);
                        rightJust[insi] = dataType.equals("INTEGER")
                                          || dataType.equals("NUMBER");

                        if (htmlMode) {
                            continue;
                        }

                        if (headerArray[insi].length() > maxWidth[insi]) {
                            maxWidth[insi] = headerArray[insi].length();
                        }
                    }
                }

                EACH_ROW:
                while (r.next()) {
                    fieldArray = new String[incCount];
                    insi       = -1;

                    for (int i = 1; i <= cols; i++) {
                        val = r.getString(i);

                        if (requireVals != null && requireVals.length >= i
                                && requireVals[i - 1] != null) {
                            ok = false;

                            for (int j = 0; j < requireVals[i - 1].length;
                                    j++) {
                                if (requireVals[i - 1][j] != null
                                        && requireVals[i - 1][j].equals(
                                            val)) {
                                    ok = true;

                                    break;
                                }
                            }

                            if (!ok) {
                                continue EACH_ROW;
                            }
                        }

                        if (prohibVals != null && prohibVals.length >= i
                                && prohibVals[i - 1] != null) {
                            for (int j = 0; j < prohibVals[i - 1].length;
                                    j++) {
                                if (prohibVals[i - 1][j] != null
                                        && prohibVals[i - 1][j].equals(val)) {
                                    continue EACH_ROW;
                                }
                            }
                        }

                        if (incCols != null) {
                            skip = true;

                            for (int j = 0; j < incCols.length; j++) {
                                if (i == incCols[j]) {
                                    skip = false;
                                }
                            }

                            if (skip) {
                                continue;
                            }
                        }

                        fieldArray[++insi] = r.wasNull()
                                             ? (htmlMode ? "<I>null</I>"
                                                         : "null")
                                             : val;

                        if (htmlMode) {
                            continue;
                        }

                        if (fieldArray[insi].length() > maxWidth[insi]) {
                            maxWidth[insi] = fieldArray[insi].length();
                        }
                    }

                    rows.add(fieldArray);
                }

                // STEP 2: DISPLAY DATA
                if (htmlMode) {
                    psStd.println("<TABLE border='1'>");
                }

                if (headerArray != null) {
                    if (htmlMode) {
                        psStd.print(htmlRow(COL_HEAD) + '\n' + PRE_TD);
                    }

                    for (int i = 0; i < headerArray.length; i++) {
                        psStd.print(htmlMode
                                    ? ("<TD>" + headerArray[i] + "</TD>")
                                    : (((i > 0) ? spaces(2)
                                                : "") + pad(headerArray[i],
                                                            maxWidth[i],
                                                            rightJust[i])));
                    }

                    psStd.println(htmlMode ? ("\n" + PRE_TR + "</TR>")
                                           : "");

                    if (!htmlMode) {
                        for (int i = 0; i < headerArray.length; i++) {
                            psStd.print(((i > 0) ? spaces(2)
                                                 : "") + divider(
                                                     maxWidth[i]));
                        }

                        psStd.println();
                    }
                }

                for (int i = 0; i < rows.size(); i++) {
                    if (htmlMode) {
                        psStd.print(htmlRow(((i % 2) == 0) ? COL_EVEN
                                                           : COL_ODD) + '\n'
                                                           + PRE_TD);
                    }

                    fieldArray = (String[]) rows.get(i);

                    for (int j = 0; j < fieldArray.length; j++) {
                        psStd.print(htmlMode
                                    ? ("<TD>" + fieldArray[j] + "</TD>")
                                    : (((j > 0) ? spaces(2)
                                                : "") + pad(fieldArray[j],
                                                            maxWidth[j],
                                                            rightJust[j])));
                    }

                    psStd.println(htmlMode ? ("\n" + PRE_TR + "</TR>")
                                           : "");
                }

                if (htmlMode) {
                    psStd.println("</TABLE>");
                }

                if (rows.size() != 1) {
                    stdprint("\n" + rows.size() + " rows");
                }
                break;

            default :
                if (updateCount != 0) {
                    stdprint(Integer.toString(updateCount) + " row"
                             + ((updateCount == 1) ? ""
                                                   : "s") + " updated");
                }
                break;
        }
    }

    final static private int    COL_HEAD = 0,
                                COL_ODD  = 1,
                                COL_EVEN = 2
    ;
    static private final String PRE_TR   = spaces(4);
    static private final String PRE_TD   = spaces(8);

    /**
     * Print a properly formatted HTML &lt;TR&gt; command for the given 
     * situation.
     *
     * @param colType Column type:  COL_HEAD, COL_ODD or COL_EVEN.
     */
    static private String htmlRow(int colType) {

        switch (colType) {

            case COL_HEAD :
                return PRE_TR + "<TR style='font-weight: bold;'>";

            case COL_ODD :
                return PRE_TR
                       + "<TR style='background: #94d6ef; font: normal "
                       + "normal 10px/10px Arial, Helvitica, sans-serif;'>";

            case COL_EVEN :
                return PRE_TR
                       + "<TR style='background: silver; font: normal "
                       + "normal 10px/10px Arial, Helvitica, sans-serif;'>";
        }

        return null;
    }

    /**
     * Returns a divider of hypens of requested length.
     *
     * @param len Length of output String.
     */
    static private String divider(int len) {
        return (len > DIVIDER.length()) ? DIVIDER
                                        : DIVIDER.substring(0, len);
    }

    /**
     * Returns a String of spaces of requested length.
     *
     * @param len Length of output String.
     */
    static private String spaces(int len) {
        return (len > SPACES.length()) ? SPACES
                                       : SPACES.substring(0, len);
    }

    /**
     * Pads given input string out to requested length with space
     * characters.
     *
     * @param inString Base string.
     * @param fulllen  Output String length.
     * @param rightJustify  True to right justify, false to left justify.
     */
    static private String pad(String inString, int fulllen,
                              boolean rightJustify) {

        int len = fulllen - inString.length();

        if (len < 1) {
            return inString;
        }

        String pad = spaces(len);

        return ((rightJustify ? pad
                              : "") + inString + (rightJustify ? ""
                                                               : pad));
    }

    /**
     * Display command history, which consists of complete or incomplete SQL
     * commands.
     */
    private void showHistory() {

        int      ctr = -1;
        String   s;
        String[] reversedList = new String[statementHistory.length];

        try {
            for (int i = curHist; i >= 0; i--) {
                s = statementHistory[i];

                if (s == null) {
                    return;
                }

                reversedList[++ctr] = s;
            }

            for (int i = 9; i > curHist; i--) {
                s = statementHistory[i];

                if (s == null) {
                    return;
                }

                reversedList[++ctr] = s;
            }
        } finally {
            if (ctr < 0) {
                stdprint("<<<    No history yet    >>>");

                return;
            }

            for (int i = ctr; i >= 0; i--) {
                psStd.println(((i == 0) ? "BUFR"
                                        : ("-" + i + "  ")) + " **********************************************\n"
                    + reversedList[i]);
            }

            psStd.println(
                "\n<<<  Copy a command to buffer like \"\\-3\"       "
                          + "Re-execute buffer like \":;\"  >>>");
        }
    }

    /**
     * Return a SQL Command from command history.
     */
    private String commandFromHistory(int commandsAgo) throws BadSpecial {

        if (commandsAgo >= statementHistory.length) {
            throw new BadSpecial("History can only hold up to "
                                 + statementHistory.length + " commands");
        }

        String s =
            statementHistory[(statementHistory.length + curHist - commandsAgo) % statementHistory.length];

        if (s == null) {
            throw new BadSpecial("History doesn't go back that far");
        }

        return s;
    }

    /**
     * Push a command onto the history array (the first element of which
     * is the "Buffer").
     */
    private void setBuf(String inString) {

        curHist++;

        if (curHist == statementHistory.length) {
            curHist = 0;
        }

        statementHistory[curHist] = inString;
    }

    /**
     * Describe the columns of specified table.
     *
     * @param tableName  Table that will be described.
     */
    private void describe(String tableName) throws SQLException {

        Statement statement = curConn.createStatement();

        statement.execute("SELECT * FROM " + tableName + " WHERE 1 = 2");

        ResultSet         r    = statement.getResultSet();
        ResultSetMetaData m    = r.getMetaData();
        int               cols = m.getColumnCount();
        String            val;
        ArrayList         rows        = new ArrayList();
        String[]          headerArray = {
            "name", "datatype", "width", "no-nulls"
        };
        String[]          fieldArray;
        int[]             maxWidth  = {
            0, 0, 0, 0
        };
        boolean[]         rightJust = {
            false, false, true, false
        };

        // STEP 1: GATHER DATA
        for (int i = 0; i < headerArray.length; i++) {
            if (htmlMode) {
                continue;
            }

            if (headerArray[i].length() > maxWidth[i]) {
                maxWidth[i] = headerArray[i].length();
            }
        }

        for (int i = 0; i < cols; i++) {
            fieldArray    = new String[4];
            fieldArray[0] = m.getColumnName(i + 1);
            fieldArray[1] = m.getColumnTypeName(i + 1);
            fieldArray[2] = Integer.toString(m.getColumnDisplaySize(i + 1));
            fieldArray[3] =
                ((m.isNullable(i + 1) == java.sql.ResultSetMetaData.columnNullable)
                 ? (htmlMode ? "&nbsp;"
                             : "")
                            : "*");

            rows.add(fieldArray);

            for (int j = 0; j < fieldArray.length; j++) {
                if (fieldArray[j].length() > maxWidth[j]) {
                    maxWidth[j] = fieldArray[j].length();
                }
            }
        }

        // STEP 2: DISPLAY DATA
        if (htmlMode) {
            psStd.println("<TABLE border='1'>");
        }

        if (htmlMode) {
            psStd.print(htmlRow(COL_HEAD) + '\n' + PRE_TD);
        }

        for (int i = 0; i < headerArray.length; i++) {
            psStd.print(htmlMode ? ("<TD>" + headerArray[i] + "</TD>")
                                 : (((i > 0) ? spaces(2)
                                             : "") + pad(headerArray[i],
                                             maxWidth[i], rightJust[i])));
        }

        psStd.println(htmlMode ? ("\n" + PRE_TR + "</TR>")
                               : "");

        if (!htmlMode) {
            for (int i = 0; i < headerArray.length; i++) {
                psStd.print(((i > 0) ? spaces(2)
                                     : "") + divider(maxWidth[i]));
            }

            psStd.println();
        }

        for (int i = 0; i < rows.size(); i++) {
            if (htmlMode) {
                psStd.print(htmlRow(((i % 2) == 0) ? COL_EVEN
                                                   : COL_ODD) + '\n'
                                                   + PRE_TD);
            }

            fieldArray = (String[]) rows.get(i);

            for (int j = 0; j < fieldArray.length; j++) {
                psStd.print(htmlMode ? ("<TD>" + fieldArray[j] + "</TD>")
                                     : (((j > 0) ? spaces(2)
                                                 : "") + pad(fieldArray[j],
                                                 maxWidth[j], rightJust[j])));
            }

            psStd.println(htmlMode ? ("\n" + PRE_TR + "</TR>")
                                   : "");
        }

        if (htmlMode) {
            stdprint("\n</TABLE>");
        }
    }
}
