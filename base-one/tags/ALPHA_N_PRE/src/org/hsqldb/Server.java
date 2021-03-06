/* Copyrights and Licenses
 *
 * This product includes Hypersonic SQL.
 * Originally developed by Thomas Mueller and the Hypersonic SQL Group. 
 *
 * Copyright (c) 1995-2000 by the Hypersonic SQL Group. All rights reserved. 
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met: 
 *     -  Redistributions of source code must retain the above copyright notice, this list of conditions
 *         and the following disclaimer. 
 *     -  Redistributions in binary form must reproduce the above copyright notice, this list of
 *         conditions and the following disclaimer in the documentation and/or other materials
 *         provided with the distribution. 
 *     -  All advertising materials mentioning features or use of this software must display the
 *        following acknowledgment: "This product includes Hypersonic SQL." 
 *     -  Products derived from this software may not be called "Hypersonic SQL" nor may
 *        "Hypersonic SQL" appear in their names without prior written permission of the
 *         Hypersonic SQL Group. 
 *     -  Redistributions of any form whatsoever must retain the following acknowledgment: "This
 *          product includes Hypersonic SQL." 
 * This software is provided "as is" and any expressed or implied warranties, including, but
 * not limited to, the implied warranties of merchantability and fitness for a particular purpose are
 * disclaimed. In no event shall the Hypersonic SQL Group or its contributors be liable for any
 * direct, indirect, incidental, special, exemplary, or consequential damages (including, but
 * not limited to, procurement of substitute goods or services; loss of use, data, or profits;
 * or business interruption). However caused any on any theory of liability, whether in contract,
 * strict liability, or tort (including negligence or otherwise) arising in any way out of the use of this
 * software, even if advised of the possibility of such damage. 
 * This software consists of voluntary contributions made by many individuals on behalf of the
 * Hypersonic SQL Group.
 *
 *
 * For work added by the HSQL Development Group:
 *
 * Copyright (c) 2001-2002, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer, including earlier
 * license statements (above) and comply with all above license conditions.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution, including earlier
 * license statements (above) and comply with all above license conditions.
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

import java.io.IOException;
import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;
import org.hsqldb.lib.HsqlHashSet;
import org.hsqldb.lib.StopWatch;
import org.hsqldb.lib.java.javaSystem;
import org.hsqldb.resources.BundleHandler;

// fredt@users 20020215 - patch 1.7.0 by fredt
// method rorganised to use new HsqlServerProperties class
// fredt@users 20020424 - patch 1.7.0 by fredt - shutdown without exit
// see the comments in ServerConnection.java
// unsaved@users 20021113 - patch 1.7.2 - SSL support
// boucherb@users 20030510-14 - p 1.7.2 - SSL support -> factory interface
//                                      - WebServer completely reuses Server
//                                      - refactoring and xdoclet tags
//                                        toward MBean interface
//                                      - use HsqlRuntime registration
//                                      - public method invocations are 
//                                        thread-safe
//                                      - added non-blocking start()/stop() 
//                                        service control methods 
//                                      - settable InetAddress spec. 
//                                        for ServerSocket host address
//                                      - server implements HsqlSocketRequestHandler
//                                      - stub for hosts allow / deny
//                                      - stub for pluggable protocol adaptors
//                                        

/**
 * Server acts as a network database server and is one way of using
 * the client-server mode of HSQL Database Engine. This server
 * can only process database queries.<p>
 * An applet or application will use only the JDBC classes to access
 * the database.<p>
 *
 * The Server can be configured with the file 'server.properties'.
 * This is an example of the file:
 * <pre>
 * server.port=9001<p>
 * server.database=test<p>
 * server.silent=true<p>
 * </pre>
 *
 *  If the server is embedded in an application server, such as when
 *  DataSource or HsqlServerFactory classes are used, it is necessary
 *  to avoid calling System.exit() when the HSQLDB is shutdown with
 *  an SQL command.<br>
 *  For this, the server.no_system_exit property can be
 *  set either on the command line or in server.properties file.
 *  This ensures that System.exit() is not called at the end.
 *  All that is left for the embedded application server is to release
 *  the "empty" Server object and create another one to reopen the
 *  database (fredt@users). <p>
 *
 *  <b>1.7.2 Notes:</b> start() and stop() methods added and Server always
 *  runs in its own thread, tracked by HsqlRuntime.  Default is now to
 *  set server.no_system_exit=true when calling start() directly, while
 *  setting server.no_system_exit=false by default when server is started
 *  by calling main(String[] args).  main(String[] args) no longer
 *  blocks, because server runs in its own thread. (boucherb@users)
 *
 * @version 1.7.2
 *
 * @jmx.mbean
 *    description="HSQLDB Server"
 */
public class Server implements ServerConstants, HsqlSocketRequestHandler {

//******************************** fields **************************************
//-------------------------------- static --------------------------------------
    static final String      mServerName        = "HSQLDB/1.7.2";
    static final char        mPathSeparatorChar = File.separatorChar;
    static final HsqlRuntime runtime = HsqlRuntime.getHsqlRuntime();
    private static final String dashes =
        "----------------------------------------------------------------------";
    private static final int bhnd =
        BundleHandler.getBundleHandle("org_hsqldb_Server_messages", null);

//-------------------------------- package -------------------------------------
    Vector         serverConnList;
    Database       mDatabase;
    HsqlProperties serverProperties;

//------------------------------- protected ------------------------------------
    protected String            serverId;
    protected int               serverProtocol;
    protected ThreadGroup       serverThreadGroup;
    protected ThreadGroup       serverConnectionThreadGroup;
    protected HsqlSocketFactory socketFactory;
    protected ServerSocket      socket;

//-------------------------------- private -------------------------------------
    private volatile Thread serverThread;
    private int             serverState;
    private Throwable       serverError;
    private final Object    mDatabase_mutex    = new Object();
    private final Object    serverState_mutex  = new Object();
    private final Object    serverThread_mutex = new Object();
    private final Object    status_monitor     = new Object();
    private final Object    socket_mutex       = new Object();

//-------------------------------- Inner Classes -------------------------------

    /**
     * A specialized Thread inner class in which the run() method of this
     * server executes.
     */
    private class ServerThread extends Thread {

        /**
         * Constructs a new thread in which to execute the run method
         * of this server.
         *
         * @param tg The thread group
         * @param name the thread name
         */
        ServerThread(ThreadGroup tg, String name) {
            super(tg, name);
        }

        /**
         * Executes the run() method of this server
         */
        public void run() {
            Server.this.run();
            trace("ServerThread.run() exiting");
        }
    }

//--------------------------------- Constructors -------------------------------
    public Server() {
        this(SC_PROTOCOL_HSQL);
    }

    protected Server(int protocol) {
        init(protocol);
    }

//---------------------------- Command Line Invocation -------------------------

    /**
     * Creates and starts a new Server.  <p>
     *
     * Allows starting a Server via the command line interface.
     *
     * @param args the command line arguments for the Server instance
     */
    public static void main(String args[]) {

        Server         server;
        HsqlProperties props;
        String         propsPath;

        if (args.length > 0) {
            String p = args[0];

            if ((p != null) && p.startsWith("-?")) {
                printHelp("server.help");

                return;
            }
        }

        props = HsqlProperties.argArrayToProps(args, SC_KEY_PREFIX);

        // Standard behaviour when started from the command line
        // is to halt the VM when the server shuts down.  This may, of 
        // course, be overridden by whatever, if any, security policy
        // is in place.
        props.setPropertyIfNotExists(SC_KEY_NO_SYSTEM_EXIT, "false");

        server    = new Server();
        propsPath = server.getDefaultPropertiesPath();

        server.print("Startup sequence initiated from main() method");
        server.print("Loading properties from [" + propsPath + "]");

        if (!server.putPropertiesFromFile(propsPath)) {
            server.print("Could not load properties from file");
            server.print("Using cli/default properties only");
        }

        server.setProperties(props);
        server.start();
    }

// ------------------------------ public methods -------------------------------

    /**
     * Checks if this Server object is or is not running and throws if the
     * current state does not match the specified value.
     *
     * @param running if true, ensure the server is running, else ensure the
     *      server is not running
     * @throws RuntimeException if the supplied value does not match the
     *      current running status
     */
    public void checkRunning(boolean running) throws RuntimeException {

        int     state;
        boolean error;

        trace("checkRunning() entered");

        state = getState();
        error = (running && state != SERVER_ONLINE)
                || (!running && state != SERVER_SHUTDOWN);

        if (error) {
            String msg = "server is " + (running ? "not"
                                                 : "") + " running";

            throw new RuntimeException(msg);
        }

        trace("checkRunning() exited");
    }

    /**
     * Closes all connections to this Server.
     *
     * @jmx.managed-operation
     *  impact="ACTION"
     *  description="Closes all open connections"
     */
    public void closeAllServerConnections() {

        Vector           scl;
        ServerConnection sc;

        trace("closeAllServerConnections() entered");

        scl = serverConnList;

        trace("Closing all server connections:");

        for (int i = scl.size() - 1; i >= 0; i--) {
            sc = (ServerConnection) scl.elementAt(i);

            trace("Closing " + sc);
            sc.close();
        }

        //fredt@users - when we implement restart on shutdown add resize to 16
        scl.removeAllElements();
        trace("closeAllServerConnections() exited");
    }

    /**
     * Retrieves, in string form, this server's host address.
     *
     * @return this server's host address
     *
     * @jmx.managed-attribute
     *  access="read-write"
     *  description="Host InetAddress"
     */
    public String getAddress() {
        return socket == null ? serverProperties.getProperty(SC_KEY_ADDRESS)
                              : socket.getInetAddress().toString();
    }

    /**
     * Retrieves the absolute path of the database this Server hosts.
     *
     * @return the absolute path of the database this Server hosts
     *
     * @jmx.managed-attribute
     *  access="read-write"
     *  description="For hosted database"
     */
    public String getDatabasePath() {
        return HsqlRuntime.absoluteDatabasePath(
            serverProperties.getProperty(SC_KEY_DATABASE));
    }

    /**
     * Retrieves the default port that this Server will try to use in the
     * abscence of an explicitly specified one, given the specified
     * value for whether or not to use secure sockets.
     *
     * @param isTls if true, retrieve the default port when using secure
     *      sockets, else the default port when using plain sockets
     * @return the default port used in the abscence of an explicit
     *      specification.
     */
    public int getDefaultPort(boolean isTls) {

        switch (serverProtocol) {

            case SC_PROTOCOL_HTTP : {
                return isTls ? SC_DEFAULT_HTTPS_SERVER_PORT
                             : SC_DEFAULT_HTTP_SERVER_PORT;
            }
            case SC_PROTOCOL_HSQL :
            default : {
                return isTls ? SC_DEFAULT_HSQLS_SERVER_PORT
                             : SC_DEFAULT_HSQL_SERVER_PORT;
            }
        }
    }

    /**
     * Retrieves the path that will be used by default if a null or zero-length
     * path is specified to putPropertiesFromFile().
     *
     * @return The path that will be used by default if null is specified to
     *      putPropertiesFromFile()
     *
     * @jmx.managed-attribute
     *  access="read-only"
     *  description="Read by putPropertiesFromFile()"
     */
    public String getDefaultPropertiesPath() {

        switch (serverProtocol) {

            case SC_PROTOCOL_HTTP : {
                return (new File("webserver.properties")).getAbsolutePath();
            }
            case SC_PROTOCOL_HSQL :
            default : {
                return (new File("server.properties")).getAbsolutePath();
            }
        }
    }

    /**
     * Retrieves the name of the web page served when no page is specified.
     * This attribute is relevant only when server protocol is HTTP(S).
     *
     * @return the name of the web page served when no page is specified
     *
     * @jmx.managed-attribute
     *  access="read-write"
     *  description="Used when server protocol is HTTP(S)"
     */
    public String getDefaultWebPage() {

        if (serverProtocol != SC_PROTOCOL_HTTP) {
            return "IGNORED";
        }

        return serverProperties.getProperty(SC_KEY_WEB_DEFAULT_PAGE);
    }

    /**
     * Retrieves a String object describing the command line and
     * properties options for this Server.
     *
     * @return the command line and properties options help for this Server
     */
    public String getHelpString() {

        String key;

        switch (serverProtocol) {

            case SC_PROTOCOL_HTTP :
                key = "webserver.help";
                break;

            case SC_PROTOCOL_HSQL :
            default :
                key = "server.help";
        }

        return BundleHandler.getString(bhnd, key);
    }

    /**
     * Retrieves this server's host port.
     *
     * @return this server's host port
     *
     * @jmx.managed-attribute
     *  access="read-write"
     *  description="Of ServerSocket"
     */
    public int getPort() {
        return serverProperties.getIntegerProperty(SC_KEY_PORT,
                getDefaultPort(isTls()));
    }

    /**
     * Retrieves this server's product name.  <p>
     *
     * Typically, this will be something like: "HSQLDB xxx server".
     *
     * @return the product name of this server
     *
     * @jmx.managed-attribute
     *  access="read-only"
     *  description="Of Server"
     */
    public String getProductName() {

        switch (serverProtocol) {

            case SC_PROTOCOL_HTTP : {
                return "HSQLDB web server";
            }
            case SC_PROTOCOL_HSQL :
            default : {
                return "HSQLDB server";
            }
        }
    }

    /**
     * Retrieves the server's product version, as a String.  <p>
     *
     * Typically, this will be something like: "1.x.x" or "2.x.x" and so on.
     *
     * @return the product version of the server
     *
     * @jmx.managed-attribute
     *  access="read-only"
     *  description="Of Server"
     */
    public String getProductVersion() {
        return jdbcDriver.VERSION;
    }

    /**
     * Retrieves a string respresentaion of the network protocol
     * this server offers, typically one of 'HTTP', HTTPS', 'HSQL' or 'HSQLS'.
     *
     * @return string respresentation of this server's protocol
     *
     * @jmx.managed-attribute
     *  access="read-only"
     *  description="Used to handle connections"
     */
    public String getProtocol() {

        String name;

        name = "";

        switch (serverProtocol) {

            case SC_PROTOCOL_HSQL :
            default : {
                name = "HSQL";

                break;
            }
            case SC_PROTOCOL_HTTP : {
                name = "HTTP";

                break;
            }
        }

        if (isTls()) {
            name += "S";
        }

        return name;
    }

    /**
     * Retrieves a String identifying this Server object.
     *
     * @return a String identifying this Server object
     *
     * @jmx.managed-attribute
     *  access="read-only"
     *  description="Identifying Server"
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Retrieves current state of this server in numerically coded form. <p>
     *
     * Typically, this will be one of: <p>
     *
     * <ol>
     * <li>SERVER_ONLINE
     * <li>SERVER_OPENING
     * <li>SERVER_CLOSING
     * <li>SERVER_SHUTDOWN
     * </ol>
     *
     * @return this server's state code.
     *
     * @jmx.managed-attribute
     *  access="read-only"
     *  description="1:ONLINE 4:OPENING 8:CLOSING, 16:SHUTDOWN"
     */
    public int getState() {

        synchronized (serverState_mutex) {
            return serverState;
        }
    }

    /**
     * Retrieves a character sequence describing this server's current state,
     * including the message of the last exception, if there is one and it
     * is still in context.
     *
     * @return this server's state represented as a character sequence.
     *
     * @jmx.managed-attribute
     *  access="read-only"
     *  description="State [: exception ]"
     */
    public String getStateDescriptor() {

        int    state;
        String sState;

        state = getState();

        switch (serverState) {

            case SERVER_SHUTDOWN :
                sState = "SHUTDOWN";
                break;

            case SERVER_OPENING :
                sState = "OPENING";
                break;

            case SERVER_CLOSING :
                sState = "CLOSING";
                break;

            case SERVER_ONLINE :
                sState = "ONLINE";
                break;

            default :
                sState = "UNKNOWN";
                break;
        }

        if (serverError != null) {
            sState += ": " + serverError.toString();
        }

        return sState;
    }

    /**
     * Retrieves the root context (directory) from which web content
     * is served.  This property is relevant only when the server
     * protocol is HTTP(S).  Although unlikely, in the future, other
     * contexts, such as jar urls may be supported, so that pages can
     * be served from the contents of a jar or from the JVM class path,
     * for example.
     *
     * @return the root context (directory) from which web content is served
     *
     * @jmx.managed-attribute
     *  access="read-write"
     *  description="Context (directory)"
     */
    public String getWebRoot() {

        if (serverProtocol != SC_PROTOCOL_HTTP) {
            return "IGNORED";
        }

        return serverProperties.getProperty(SC_KEY_WEB_ROOT);
    }

    /**
     * Assigns the specified socket to a new conection handler and
     * starts the handler in a new Thread.
     *
     * @param socket the socket to connect
     */
    public void handleConnection(Socket s) {

        Thread   t;
        Runnable r;

        trace("handleConnection(): " + s);
        checkRunning(true);

        if (!allowConnection(s)) {
            try {
                s.close();
            } catch (Exception e) {}

            trace("handleConnection(): connection refused");

            return;
        }

        // Maybe set up socket options, SSL 
        // Session tracing/callbacks, etc.
        socketFactory.configureSocket(s);

        r = newConnectionHandler(s);
        t = new Thread(serverConnectionThreadGroup, r, "[" + s + "]");

        t.start();
    }

    /**
     * Retrieves whether this server calls System.exit() when shutdown.
     *
     * @return true if this server does not call System.exit()
     *
     * @jmx.managed-attribute
     *  access="read-write"
     *  description="When Shutdown"
     */
    public boolean isNoSystemExit() {
        return serverProperties.isPropertyTrue(SC_KEY_NO_SYSTEM_EXIT);
    }

    /**
     * Retrieves whether this server restarts on shutdown (currently not
     * implemented).
     *
     * @return true this server restarts on shutdown
     *
     * @jmx.managed-attribute
     *  access="read-write"
     *  description="Automatically"
     */
    public boolean isRestartOnShutdown() {
        return serverProperties.isPropertyTrue(SC_KEY_AUTORESTART_SERVER);
    }

    /**
     * Retrieves whether silent mode operation was requested in
     * the server properties.
     *
     * @return if true, silent mode was requested, else trace messages
     *      are to be printed
     *
     * @jmx.managed-attribute
     *  access="read-write"
     *  description="No trace messages?"
     */
    public boolean isSilent() {
        return serverProperties.isPropertyTrue(SC_KEY_SILENT);
    }

    /**
     * Retreives whether the use secure sockets was requested in the
     * server properties.
     *
     * @return if true, secure sockets are requested, else not
     *
     * @jmx.managed-attribute
     *  access="read-write"
     *  description="Use TLS/SSL sockets?"
     */
    public boolean isTls() {
        return serverProperties.isPropertyTrue(SC_KEY_TLS);
    }

    /**
     * Retrieves whether trace messages are to go to System.out or the
     * DriverManger PrintStream/PrintWriter, if any.
     *
     * @return true if tracing is on
     *
     * @jmx.managed-attribute
     *  access="read-write"
     *  description="JDBC -> System.out?"
     */
    public boolean isTrace() {
        return serverProperties.isPropertyTrue(SC_KEY_TRACE);
    }

    /**
     * Attempts to put properties from the file
     * with the specified path.  If the path is null or
     * zero length, the default path is assumed.
     *
     * @param path the path of the desired properties file
     * @throws RuntimeException if this server is running
     * @return true if the indicated file was read sucessfully, else false
     *
     * @jmx.managed-operation
     *  impact="ACTION"
     *  description="Reads in properties"
     *
     * @jmx.managed-operation-parameter
     *   name="path"
     *   type="java.lang.String"
     *   position="0"
     *   description="null/zero-length => default path"
     */
    public boolean putPropertiesFromFile(String path)
    throws RuntimeException {

        HsqlProperties p;
        boolean        loaded;

        checkRunning(false);

        if (path == null || path.trim().length() == 0) {
            path = getDefaultPropertiesPath();
        } else {
            path = (new File(path)).getAbsolutePath();
        }

        trace("putPropertiesFromFile(): [" + path + "]");

        p      = new HsqlProperties(path);
        loaded = false;

        try {
            loaded = p.load();
        } catch (Exception e) {}

        if (loaded) {
            setProperties(p);
        }

        return loaded;
    }

    /**
     * Puts properties from the supplied string argument.  The relevant
     * key value pairs are the same as those for the (web)server.properties
     * file format, except that the 'server.' prefix does not need to
     * be specified.
     *
     * @param s semicolon-delimited key=value pair string,
     *      e.g. k1=v1;k2=v2;k3=v3...
     * @throws RuntimeException if this server is running
     *
     * @jmx.managed-operation
     *   impact="ACTION"
     *   description="'server.' key prefix automatically supplied"
     *
     * @jmx.managed-operation-parameter
     *   name="s"
     *   type="java.lang.String"
     *   position="0"
     *   description="semicolon-delimited key=value pairs"
     */
    public void putPropertiesFromString(String s) throws RuntimeException {

        HsqlProperties p;

        checkRunning(false);

        if (s == null || s.length() == 0) {
            return;
        }

        trace("putPropertiesFromString(): [" + s + "]");

        p = HsqlProperties.delimitedArgPairsToProps(s, "=", ";",
                SC_KEY_PREFIX);

        setProperties(p);
    }

    /**
     * Sets the InetAddress with which this servers ServerSocket  will be
     * constructed.  The special value "any" can be used to bypass explicit
     * selection, causing the ServerSocket to be constructed
     * without specifying an InetAddress.
     *
     * @param address A string representing the desired InetAddress as would be
     *    retrieved by InetAddres.getByName(), or "any" to signify
     *    that server sockets should be constructed using the signature
     *    that does not specify the InetAddres.
     * @throws RuntimeException if this server is running
     *
     * @jmx.managed-operation
     */
    public void setAddress(String address) throws RuntimeException {

        checkRunning(false);
        trace("setAddress(): " + address);
        serverProperties.setProperty(SC_KEY_ADDRESS, address);
    }

    /**
     * Sets the path of the hosted database.
     *
     * @param path The path of the HSQLDB database instance this server
     *      is to host. The special value '.' can be used to specify a
     *      non-persistent, 100% in-memory mode database instance.
     * @throws RuntimeException if this server is running
     *
     * @jmx.managed-operation
     */
    public void setDatabasePath(String path) throws RuntimeException {

        checkRunning(false);
        trace("setDatabasePath(): " + path);
        serverProperties.setProperty(SC_KEY_DATABASE, path);
    }

    /**
     * Sets the name of the web page served when no page is specified.
     *
     * @param file the name of the web page served when no page is specified
     *
     * @jmx.managed-operation
     */
    public void setDefaultWebPage(String file) {

        checkRunning(false);
        trace("setDefaultWebPage(): " + file);

        if (serverProtocol != SC_PROTOCOL_HTTP) {
            return;
        }

        serverProperties.setProperty(SC_KEY_WEB_DEFAULT_PAGE, file);
    }

    /**
     * Sets the server listen port.
     *
     * @param port the port at which the server listens
     * @throws RuntimeException if this server is running
     *
     * @jmx.managed-operation
     */
    public void setPort(int port) throws RuntimeException {

        checkRunning(false);
        trace("setPort(): " + port);
        serverProperties.setProperty(SC_KEY_PORT, port);
    }

    /**
     * Sets whether the server calls System.exit() when shutdown.
     *
     * @param noExit if true, System.exit() will not be called.
     *
     * @jmx.managed-operation
     */
    public void setNoSystemExit(boolean noExit) {
        trace("setNoSystemExit(): " + noExit);
        serverProperties.setProperty(SC_KEY_NO_SYSTEM_EXIT, noExit);
    }

    /**
     * Sets whether this server restarts on shutdown (currently not
     * implemented).
     *
     * @param restart if true, this server restarts on shutdown
     *
     * @jmx.managed-operation
     */
    public void setRestartOnShutdown(boolean restart) {
        trace("setRestartOnShutdown(): " + restart);
        serverProperties.setProperty(this.SC_KEY_AUTORESTART_SERVER, restart);
    }

    /**
     * Sets silent mode operation
     *
     * @param silent if true, then silent mode, else trace messages
     *  are to be printed
     *
     * @jmx.managed-operation
     */
    public void setSilent(boolean silent) {
        trace("setSilent(): " + silent);
        serverProperties.setProperty(this.SC_KEY_SILENT, silent);
    }

    /**
     * Sets whether to use secure sockets
     *
     * @param tls true for secure sockets, else false
     * @throws RuntimeException if this server is running
     *
     * @jmx.managed-operation
     */
    public void setTls(boolean tls) throws RuntimeException {

        checkRunning(false);
        trace("setTls(): " + tls);
        serverProperties.setProperty(SC_KEY_TLS, tls);
    }

    /**
     * Sets whether trace messages go to System.out or the
     * DriverManger PrintStream/PrintWriter, if any.
     *
     * @param trace if true, trace to System.out
     *
     * @jmx.managed-operation
     */
    public void setTrace(boolean trace) {
        trace("setTrace(): " + trace);
        serverProperties.setProperty(SC_KEY_TRACE, trace);
    }

    /**
     * Sets the path of the root directory from which web content is served.
     *
     * @param root the root (context) directory from which web content
     *      is served
     *
     * @jmx.managed-operation
     */
    public void setWebRoot(String root) {

        checkRunning(false);

        root = (new File(root)).getAbsolutePath();

        trace("setWebRoot(): " + root);

        if (serverProtocol != SC_PROTOCOL_HTTP) {
            return;
        }

        serverProperties.setProperty(SC_KEY_WEB_ROOT, root);
    }

    /**
     * Sets properties using the specified properties object
     *
     * @param p The object containing properties to set
     * @throws RuntimeException if this server is running
     */
    public void setProperties(HsqlProperties p) throws RuntimeException {

        boolean loaded;
        String  path;

        checkRunning(false);

        if (p != null) {
            serverProperties.addProperties(p);
            translateAddressProperty(serverProperties);
        }

        javaSystem.setLogToSystem(isTrace());
    }

    /**
     * Starts this server. <p>
     *
     * If already started, this method returns immediately. Otherwise, it
     * blocks only until the server's background thread notifies the calling
     * thread that the server has either started successfully
     * of failed to do so.  If the return value is not SERVER_ONLINE,
     * getStateDescriptor() can be called to retrieve a more detailed
     * description of the failure.
     *
     * @return the server status at the point this call exits
     *
     * @jmx.managed-operation
     *  impact="ACTION_INFO"
     *  description="Invokes startup sequence; returns resulting state"
     */
    public int start() {

        synchronized (serverThread_mutex) {
            trace("start() entered");

            if (serverThread != null) {
                trace("start(): serverThread != null; returning immediately.");

                return getState();
            }

            serverThread = new ServerThread(serverThreadGroup, serverId);

            serverThread.start();
            waitForStatus();
            trace("start() exiting");

            return getState();
        }
    }

    /**
     * Stops this server. <p>
     *
     * If already stopped, this method returns immediately. Otherwise, it
     * blocks only until the server's background thread notifies the calling
     * thread that the server has either shtudown successfully
     * or has failed to do so.  If the return value is not SERVER_SHUTDOWN,
     * getStateDescriptor() can be called to retrieve a description of
     * the failure.
     *
     * @return the server status at the point this call exits
     *
     * @jmx.managed-operation
     *  impact="ACTION_INFO"
     *  description="Invokes shutdown sequence; returns resulting state"
     */
    public int stop() {

        synchronized (serverThread_mutex) {
            trace("stop() entered");

            if (serverThread == null) {
                trace("stop() serverThread is null; returning immediately.");

                return getState();
            }

            releaseServerSocket();
            waitForStatus();
            trace("stop() exiting");

            return getState();
        }
    }

//----------------------------- protected methods ------------------------------

    /**
     * Retrieves whether the specified socket should be allowed
     * to make a connection.  By default, this method always returns
     * true, but it can be overidden to implement hosts allow-deny
     * functionality.
     *
     * @param the socket to test.
     */
    protected boolean allowConnection(Socket socket) {
        return true;
    }

    /**
     * Initializes this server, setting the accepted connection protocol.
     *
     * @param protocol typically either SC_PROTOCOL_HTTP or SC_PROTOCOL_HSQL
     */
    protected void init(int protocol) {

        serverState       = SERVER_SHUTDOWN;
        serverConnList    = new Vector(16);
        serverId          = toString();
        serverProtocol    = protocol;
        serverProperties  = newDefaultProperties();
        serverThreadGroup = runtime.getServerThreadGroup(serverProtocol);
        serverConnectionThreadGroup = new ThreadGroup(serverThreadGroup,
                serverId);

        serverConnectionThreadGroup.setDaemon(false);
        javaSystem.setLogToSystem(isTrace());
    }

    protected Runnable newConnectionHandler(Socket socket) {

        switch (serverProtocol) {

            default :
            case SC_PROTOCOL_HSQL : {
                return new ServerConnection(socket, this);
            }
            case SC_PROTOCOL_HTTP : {
                return new WebServerConnection(socket, this);
            }
        }
    }

// ---------------------------------- package methods --------------------------
// Package visibility for related classes and interfaces, 
// such as HsqlRuntime, XXXServerConnection and HsqlSocketRequestHandlerImpl,
// etc. that may need to make calls back here.

    /**
     * Notifies this server that something of interest has happend. <p>
     *
     * This is a callback method used by server connection objects.
     * Currently, the only thing of interest is when a connection
     * closes; if a connection closes and the database is detected
     * to be shut down, then this server and all of its connections
     * are shut down also.
     *
     * @param action a code indicating what has happend
     */
    final void notify(int action) {

        trace("notifiy() entered");

        // only (action == SC_CONNECTION_CLOSED) is used
        if (action != SC_CONNECTION_CLOSED) {
            return;
        }

        if (mDatabase == null ||!mDatabase.isShutdown()) {
            return;
        }

        releaseServerSocket();
        trace("notifiy() exiting");
    }

    /**
     * Prints the specified message, s, formatted to identify that the print
     * operation is against this server instance.
     *
     * @param msg The message to print
     */
    final void print(String msg) {
        Trace.printSystemOut("[" + serverId + "]: " + msg);
    }

    /**
     * Prints value from server's resource bundle, formatted to
     * identify that the print operation is against this server instance.
     * Value may be localized according to the default JVM locale
     *
     * @param key the resource key
     */
    final void printResource(String key) {

        String          resource = BundleHandler.getString(bhnd, key);
        StringTokenizer st       = new StringTokenizer(resource, "\n\r");

        while (st.hasMoreTokens()) {
            print(st.nextToken());
        }
    }

    /**
     * Prints the specified message, s, prepended with a timestamp representing
     * the current date and time, formatted to identify that the print
     * operation is against this server instance.
     *
     * @param msg the message to print
     */
    final void printWithTimestamp(String msg) {
        print(new Timestamp(System.currentTimeMillis()) + " " + msg);
    }

    /**
     * Sets the server state value.
     *
     * @param state the new value
     */
    final void setState(int state) {

        synchronized (serverState_mutex) {
            serverState = state;
        }
    }

    /**
     * Prints a message iff isSilent() is false.  The message is formatted
     * similarly to print(String), additionally identifying the
     * current (calling) thread.
     *
     * @param s the message to print
     */
    final void trace(String msg) {

        if (!isSilent()) {
            print("[" + Thread.currentThread() + "]: " + msg);
        }
    }

    /**
     * Prints an error message.  The message is formatted
     * similarly to print(String), additionally identifying the
     * current (calling) thread.
     *
     * @param msg the message to print
     */
    final void traceError(String msg) {
        print("[" + Thread.currentThread() + "]: " + msg);
    }

//------------------------------- private methods ------------------------------

    /**
     * Starts the ServerSocket accept connection request handling loop.
     *
     * @throws Exception if a network or io exception occurs
     */
    private final void listen() throws Exception {

        print("Listening for connections...");
        print(dashes);

        while (true) {
            handleConnection(socket.accept());
        }
    }

    /**
     * Retrieves a new default properties object for this server
     *
     * @return a new default properties object
     */
    private HsqlProperties newDefaultProperties() {

        HsqlProperties p;
        boolean        isTls;

        p = new HsqlProperties();

        p.setProperty(SC_KEY_AUTORESTART_SERVER,
                      SC_DEFAULT_SERVER_AUTORESTART);
        p.setProperty(SC_KEY_ADDRESS, SC_DEFAULT_ADDRESS);
        p.setProperty(SC_KEY_DATABASE, SC_DEFAULT_DATABASE);
        p.setProperty(SC_KEY_NO_SYSTEM_EXIT, SC_DEFAULT_NO_SYSTEM_EXIT);

        isTls = SC_DEFAULT_TLS;

        try {
            isTls = System.getProperty("javax.net.ssl.keyStore") != null;
        } catch (Exception e) {}

        p.setProperty(SC_KEY_PORT, getDefaultPort(isTls));
        p.setProperty(SC_KEY_SILENT, SC_DEFAULT_SILENT);
        p.setProperty(SC_KEY_TLS, isTls);
        p.setProperty(SC_KEY_TRACE, SC_DEFAULT_TRACE);
        p.setProperty(SC_KEY_WEB_DEFAULT_PAGE, SC_DEFAULT_WEB_PAGE);
        p.setProperty(SC_KEY_WEB_ROOT, SC_DEFAULT_WEB_ROOT);

        return p;
    }

    /** Notifies the thread, if any, that is blocking in waitForStatus() */
    private final void notifyStatus() {

        trace("notifyStatus() entered");

        synchronized (status_monitor) {
            status_monitor.notify();
        }

        trace("notifyStatus() exited");
    }

    /**
     * Opens this server's database instance.
     *
     * @throws SQLException if a database access error occurs
     */
    final void openDB() throws SQLException {

        String    path;
        StopWatch sw;

        trace("openDB() entered");

        synchronized (mDatabase_mutex) {
            path = getDatabasePath();

            if (mDatabase == null) {
                trace("Opening database: [" + path + "]");

                sw        = new StopWatch();
                mDatabase = runtime.getDatabase(path, this);

                print(sw.elapsedTimeToMessage("Database opened sucessfully"));
            } else {
                trace("database already open: [" + path + "]");
            }
        }

        trace("openDB() exiting");
    }

    /**
     * Constructs and installs a new ServerSocket instance for this server.
     *
     * @throws Exception if it is not possible to construct and install
     *      a new ServerSocket
     */
    private final void openServerSocket() throws Exception {

        String    address;
        int       port;
        Vector    candidateAddrs;
        String    emsg;
        StopWatch sw;

        trace("openServerSocket() entered");

        if (isTls()) {
            trace("Requesting TLS/SSL-encrypted JDBC");
        }

        sw            = new StopWatch();
        socketFactory = HsqlSocketFactory.getInstance(isTls());
        address       = getAddress();
        port          = getPort();

        if (address == null || SC_DEFAULT_ADDRESS.equalsIgnoreCase(address)) {
            socket = socketFactory.createServerSocket(port);
        } else {
            try {
                socket = socketFactory.createServerSocket(port, address);
            } catch (UnknownHostException e) {
                candidateAddrs = listLocalInetAddressNames();
                emsg           = "Invalid address : " + address;

                if (candidateAddrs.size() > 0) {
                    emsg += "\nTry one of: " + candidateAddrs;
                }

                throw new UnknownHostException(emsg);
            }
        }

        trace("Got server socket: " + socket);
        print(sw.elapsedTimeToMessage("Server socket opened successfully"));

        if (socketFactory.isSecure()) {
            print("Using TLS/SSL-encrypted JDBC");
        }

        trace("openServerSocket() exiting");
    }

    /** Prints a timestamped message indicating that this server is online */
    private final void printServerOnlineMessage() {

        String productDescription;

        productDescription = getProductName() + " " + getProductVersion();

        print(dashes);
        printWithTimestamp(productDescription + " is online");
        printResource("online.help");
        print(dashes);
    }

    /**
     * Prints a description of the server properties iff !isSilent().
     */
    private final void traceProperties() {

        Enumeration e;
        String      key;
        String      value;

        // Avoid the waste of generating each description,
        // only for trace() to silently discard it
        if (isSilent()) {
            return;
        }

        e = serverProperties.propertyNames();

        while (e.hasMoreElements()) {
            key   = (String) e.nextElement();
            value = serverProperties.getProperty(key);

            trace(key + "=" + value);
        }
    }

    /**
     * Releases this server's database.  The result is to notify HsqlRuntime
     * that this server is no longer using the database instance and to
     * nullify the internal reference.
     */
    private final void releaseDB() {

        trace("releaseDB() entered");

        synchronized (mDatabase_mutex) {
            if (mDatabase != null) {
                trace("Releasing database: [" + mDatabase.getName() + "]");
                runtime.releaseDatabase(mDatabase, this);

                mDatabase = null;
            }
        }

        trace("releaseDB() exiting");
    }

    /**
     * Puts this server into the SERVER_CLOSING state, closes the ServerSocket
     * and nullifies the reference to it. If the ServerSocket is already null,
     * this method exists immediately, otherwise, the result is to fully
     * shut down the server.
     */
    private final void releaseServerSocket() {

        synchronized (socket_mutex) {
            if (socket != null) {
                trace("Releasing server socket: [" + socket + "]");
                setState(SERVER_CLOSING);

                try {
                    socket.close();
                } catch (IOException e) {
                    traceError("Exception closing server socket");
                    traceError("releaseServerSocket(): " + e);
                }

                socket = null;
            }
        }
    }

    /**
     * Attempts to bring this server fully online by opening
     * a new ServerSocket, obtaining the hosted database from
     * HsqlRuntime, notifying the status waiter thread (if any) and
     * finally entering the listen loop if all else succeeds.
     * If any part of the process fails, then this server enters
     * its shutdown sequence.
     */
    private final void run() {

        StopWatch sw;

        trace("run() entered");

        sw = new StopWatch();

        setState(SERVER_OPENING);

        serverError = null;

        print("Initiating startup sequence...");
        traceProperties();

        try {

            // Faster init first:
            // It is huge waste to fully open a database, only 
            // to find that the socket address is already in use
            openServerSocket();
        } catch (Exception e) {
            traceError(this + ".run/createServerSocket(): ");
            e.printStackTrace();

            serverError = e;

            // releases all resources
            // ensure server thread exits
            shutdown();

            return;
        }

        try {

            // Mount the database this server is supposed to host.
            // This may take some time if the database is not already
            // open in the HsqlRuntime repository.
            openDB();
        } catch (Exception e) {
            traceError(this + ".run/openDB(): ");
            e.printStackTrace();

            serverError = e;

            // releases all resources
            // ensure the server thread exits
            shutdown();

            return;
        }

        // At this point, we have a valid server socket and 
        // an open database, so its OK to start listenting 
        // for connections.
        setState(SERVER_ONLINE);
        notifyStatus();
        print(sw.elapsedTimeToMessage("Startup sequence completed"));
        printServerOnlineMessage();

        try {
            listen();
        } catch (IOException ioe) {
            if (getState() == SERVER_ONLINE) {
                traceError(this + ".run/listen(): ");
                ioe.printStackTrace();

                serverError = ioe;
            }
        } catch (Throwable t) {
            trace(t.toString());
        } finally {
            shutdown();
        }
    }

    /** Shuts down this server. */
    private final void shutdown() {

        StopWatch sw;

        // Paranoia.  Should never be the case, but can result
        // in deadlock if it is.
        if (Thread.currentThread() != serverThread) {
            throw new IllegalStateException("" + Thread.currentThread());
        }

        sw = new StopWatch();

        print(dashes);
        print("Initiating shutdown sequence...");
        releaseServerSocket();
        closeAllServerConnections();
        releaseDB();

        serverThread = null;

        setState(SERVER_SHUTDOWN);
        print(sw.elapsedTimeToMessage("Shutdown sequence completed"));
        notifyStatus();

        if (isNoSystemExit()) {
            printWithTimestamp("SHUTDOWN : System.exit() was not called");
            print(dashes);
        } else {
            printWithTimestamp("SHUTDOWN : System.exit() is called next");
            print(dashes);
            System.exit(0);
        }
    }

    /** Causes the calling thread to wait until the server thread notifies it */
    private final void waitForStatus() {

        trace("waitForStatus() entered");

        synchronized (status_monitor) {
            try {
                status_monitor.wait();
            } catch (InterruptedException e) {}
        }

        trace("waitForStatus() exited");
    }

// --------------------------- static utility methods --------------------------    

    /**
     * Prints message for the specified key, without any special
     * formatting. The message content comes from the server
     * resource bundle and thus may localized according to the default
     * JVM locale.
     *
     * @param key for message
     */
    protected static void printHelp(String key) {
        Trace.printSystemOut(BundleHandler.getString(bhnd, key));
    }

    /**
     * Retrieves a list of Strings naming the distinct, known to be valid local
     * InetAddress names for this machine.  The process is to collect and
     * return the union of the following sets:
     *
     * <ol>
     * <li> InetAddress.getAllByName(InetAddress.getLocalHost().getHostAddress())
     * <li> InetAddress.getAllByName(InetAddress.getLocalHost().getHostName())
     * <li> InetAddress.getAllByName(InetAddress.getByName(null).getHostAddress())
     * <li> InetAddress.getAllByName(InetAddress.getByName(null).getHostName())
     * <li> InetAddress.getByName("loopback").getHostAddress()
     * <li> InetAddress.getByName("loopback").getHostname()
     * </ol>
     *
     * @return the distinct, known to be valid local
     *        InetAddress names for this machine
     */
    public static Vector listLocalInetAddressNames() {

        InetAddress   addr;
        InetAddress[] addrs;
        HsqlHashSet   set;
        Vector        out;
        StringBuffer  sb;

        set = new HsqlHashSet();
        out = new Vector();

        try {
            addr  = InetAddress.getLocalHost();
            addrs = InetAddress.getAllByName(addr.getHostAddress());

            for (int i = 0; i < addrs.length; i++) {
                set.add(addrs[i].getHostAddress());
                set.add(addrs[i].getHostName());
            }

            addrs = InetAddress.getAllByName(addr.getHostName());

            for (int i = 0; i < addrs.length; i++) {
                set.add(addrs[i].getHostAddress());
                set.add(addrs[i].getHostName());
            }
        } catch (Exception e) {}

        try {
            addr  = InetAddress.getByName(null);
            addrs = InetAddress.getAllByName(addr.getHostAddress());

            for (int i = 0; i < addrs.length; i++) {
                set.add(addrs[i].getHostAddress());
                set.add(addrs[i].getHostName());
            }

            addrs = InetAddress.getAllByName(addr.getHostName());

            for (int i = 0; i < addrs.length; i++) {
                set.add(addrs[i].getHostAddress());
                set.add(addrs[i].getHostName());
            }
        } catch (Exception e) {}

        try {
            set.add(InetAddress.getByName("loopback").getHostAddress());
            set.add(InetAddress.getByName("loopback").getHostName());
        } catch (Exception e) {}

        for (Enumeration e = out.elements(); e.hasMoreElements(); ) {
            out.addElement(e.nextElement());
        }

        return out;
    }

    /**
     * Translates null or zero length value for address key to the
     * special value "any" which causes ServerSockets to be constructed
     * without specifying an InetAddress.
     *
     * @param p The properties object upon which to perform the translation
     */
    private void translateAddressProperty(HsqlProperties p) {

        String address;

        if (p == null) {
            return;
        }

        address = p.getProperty(SC_KEY_ADDRESS);

        if (address == null || address.trim().length() == 0) {
            p.setProperty(SC_KEY_ADDRESS, SC_DEFAULT_ADDRESS);
        }
    }
}
