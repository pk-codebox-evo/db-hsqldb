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


package org.hsqldb.util;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.applet.*;
import java.util.*;
import java.sql.*;

/**
 *  Utility program (or applet) for transferring tables between different
 *  databases via JDBC. Understands HSQLDB database particularly well.
 *
 * @version 1.7.0
 */

// fredt@users 20011220 - patch 481239 by xponsard@users - enhancements
// enhancements to support saving and loading of transfer settings,
// transfer of blobs, and catalog and schema names in source db
// changes by fredt to allow saving and loading of transfer settings
// fredt@users 20020215 - patch 516309 by Nicolas Bazin - enhancements
// sqlbob@users 20020401 - patch 1.7.0 - reengineering
// nicolas BAZIN 20020430 - add Catalog selection, correct a bug preventing table
//    edition, change double quotes to simple quotes for default values of CHAR type
public class Transfer extends Applet
implements WindowListener, ActionListener, ItemListener, Traceable {

    Frame            fMain;
    Image            imgEmpty;
    DataAccessPoint  sourceDb;
    DataAccessPoint  targetDb;
    TransferTable    tCurrent;
    int              iMaxRows;
    int              iSelectionStep;
    Vector           tTable;
    java.awt.List    lTable;
    String           sSourceSchemas[];
    String           sSourceCatalog, sDestSchema, sDestCatalog;
    TextField tSourceTable, tDestTable, tDestDropIndex, tDestCreateIndex;
    TextField        tDestDrop, tDestCreate, tDestDelete, tDestAlter;
    TextField        tSourceSelect, tDestInsert;
    Checkbox         cTransfer, cDrop, cCreate, cDelete, cInsert, cAlter;
    Checkbox         cCreateIndex, cDropIndex;
    Checkbox         cFKForced, cIdxForced;
    Button           bStart, bContinue;
    TextField        tMessage;
    int              iTransferMode;
    static boolean   bMustExit;
    int              CurrentTransfer, CurrentAlter;
    final static int SELECT_SOURCE_CATALOG = 1;
    final static int SELECT_SOURCE_SCHEMA  = 2;
    final static int SELECT_DEST_CATALOG   = 3;
    final static int SELECT_DEST_SCHEMA    = 4;
    final static int SELECT_SOURCE_TABLES  = 5;
    final static int TRFM_TRANSFER         = 1;
    final static int TRFM_DUMP             = 2;
    final static int TRFM_RESTORE          = 3;

    /**
     * Method declaration
     *
     *
     * @param s
     */
    public void trace(String s) {

        if ((s != null) &&!s.equals("")) {
            tMessage.setText(s);

            if (TRACE) {
                System.out.println(s);
            }
        }
    }

    /**
     * Method declaration
     *
     */
    public void init() {

        Transfer m = new Transfer();

        m._main(null);
    }

    /**
     * Method declaration
     *
     */
    public static void work(String arg[]) {

        Transfer m = new Transfer();

        m._main(arg);
    }

    /**
     * Method declaration
     *
     *
     * @param arg
     */
    public static void main(String arg[]) {

        System.getProperties().put("sun.java2d.noddraw", "true");

        bMustExit = true;

        work(arg);
    }

    private boolean CatalogToSelect() {

        Vector result = null;

        try {
            lTable.removeAll();

            if (iSelectionStep == this.SELECT_SOURCE_CATALOG) {
                result = sourceDb.getCatalog();
            } else if (iSelectionStep == this.SELECT_DEST_CATALOG) {
                result = targetDb.getCatalog();
            } else {
                Exit();
            }

            if (result.size() > 1) {
                lTable.setMultipleMode(true);

                if (iSelectionStep == this.SELECT_SOURCE_CATALOG) {
                    bStart.setLabel("Select Catalog: Source");
                } else {
                    bStart.setLabel("Select Catalog: Destination");
                }

                bStart.invalidate();
                bStart.setEnabled(true);

                for (Enumeration e =
                        result.elements(); e.hasMoreElements(); ) {
                    lTable.add(e.nextElement().toString());
                }

                lTable.repaint();
                trace("Select correct Catalog");
            } else {
                if (result.size() == 1) {
                    if (iSelectionStep == this.SELECT_SOURCE_CATALOG) {
                        sSourceCatalog = (String) result.firstElement();
                        sSourceSchemas = null;
                    } else {
                        sDestCatalog = (String) result.firstElement();
                        sDestSchema  = null;
                    }
                } else {
                    if (iSelectionStep == this.SELECT_SOURCE_CATALOG) {
                        sSourceCatalog = null;
                        sSourceSchemas = null;
                    } else {
                        sDestCatalog = null;
                        sDestSchema  = null;
                    }
                }

                if ((iSelectionStep == this.SELECT_DEST_CATALOG)
                        && (sDestCatalog != null)) {
                    try {
                        targetDb.setCatalog(sDestCatalog);
                    } catch (Exception ex) {
                        trace("Catalog " + sSourceCatalog
                              + " could not be selected in the target database");

                        sSourceCatalog = null;
                    }
                }

                iSelectionStep++;

                ProcessNextStep();

/*
                if (SchemaToSelect()) {
                    fMain.show();

                    return false;
                }
*/
                return false;
            }
        } catch (Exception exp) {
            lTable.removeAll();
            trace("Exception reading catalog: " + exp);
            exp.printStackTrace();
        }

        return (lTable.getItemCount() > 0);
    }

    private boolean SchemaToSelect() {

        Vector result = null;

        try {
            lTable.removeAll();

            if (iSelectionStep == this.SELECT_SOURCE_SCHEMA) {
                result = sourceDb.getSchemas();
            } else if (iSelectionStep == this.SELECT_DEST_SCHEMA) {
                result = targetDb.getSchemas();
            } else {
                Exit();
            }

            if (result.size() > 1) {
                lTable.setMultipleMode(true);

                if (iSelectionStep == this.SELECT_SOURCE_SCHEMA) {
                    bStart.setLabel("Select Schema: Source");
                } else {
                    bStart.setLabel("Select Schema: Destination");
                }

                bStart.invalidate();
                bStart.setEnabled(true);

                for (Enumeration e =
                        result.elements(); e.hasMoreElements(); ) {
                    lTable.add(e.nextElement().toString());
                }

                lTable.repaint();
                trace("Select correct Schema or load Settings file");
            } else {
                if (result.size() == 1) {
                    if (iSelectionStep == this.SELECT_SOURCE_SCHEMA) {
                        sSourceSchemas    = new String[1];
                        sSourceSchemas[0] = (String) result.firstElement();
                    } else {
                        sDestSchema = (String) result.firstElement();
                    }
                } else {
                    if (iSelectionStep == this.SELECT_SOURCE_SCHEMA) {
                        sSourceSchemas = null;
                    } else {
                        sDestSchema = null;
                    }
                }

                if (iTransferMode == TRFM_DUMP) {
                    iSelectionStep = this.SELECT_SOURCE_TABLES;
                } else {
                    iSelectionStep++;
                }

                ProcessNextStep();

/*
                if (iSelectionStep == this.SELECT_SOURCE_TABLES) {
                    if (iTransferMode == TRFM_TRANSFER) {
                        bStart.setLabel("Start Transfer");
                    } else {
                        bStart.setLabel("Start Dump");
                    }

                    bStart.invalidate();
                    bStart.setEnabled(false);
                    lTable.setMultipleMode(false);
                    RefreshMainDisplay();
                } else {
                    if (CatalogToSelect()) {
                        fMain.show();

                        return false;
                    }
                }
*/
                return false;
            }
        } catch (Exception exp) {
            lTable.removeAll();
            trace("Exception reading schemas: " + exp);
            exp.printStackTrace();
        }

        return (lTable.getItemCount() > 0);
    }

    /**
     * Method declaration
     *
     */
    void _main(String arg[]) {

        /*
        ** What function is asked from the transfer tool?
        */
        iTransferMode = TRFM_TRANSFER;

        if ((arg != null) && (arg.length > 0)) {
            if ((arg[0].toLowerCase().equals("-r"))
                    || (arg[0].toLowerCase().equals("--restore"))) {
                iTransferMode = TRFM_RESTORE;
            } else if ((arg[0].toLowerCase().equals("-d"))
                       || (arg[0].toLowerCase().equals("--dump"))) {
                iTransferMode = TRFM_DUMP;
            }
        }

        fMain = new Frame("HSQL Transfer Tool");
        imgEmpty = createImage(new MemoryImageSource(2, 2, new int[4 * 4], 2,
                2));

        fMain.setIconImage(imgEmpty);
        fMain.addWindowListener(this);
        fMain.setSize(640, 480);
        fMain.add("Center", this);

        MenuBar bar      = new MenuBar();
        String  extras[] = {
            "Insert 10 rows only", "Insert 1000 rows only", "Insert all rows",
            "-", "Load Settings...", "Save Settings...", "-", "Exit"
        };
        Menu menu = new Menu("Options");

        addMenuItems(menu, extras);
        bar.add(menu);
        fMain.setMenuBar(bar);
        initGUI();

        Dimension d    = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension size = fMain.getSize();

        // (ulrivo): full size on screen with less than 640 width
        if (d.width >= 640) {
            fMain.setLocation((d.width - size.width) / 2,
                              (d.height - size.height) / 2);
        } else {
            fMain.setLocation(0, 0);
            fMain.setSize(d);
        }

        fMain.setVisible(true);

        CurrentTransfer = CurrentAlter = 0;

        try {
            if ((iTransferMode == TRFM_DUMP)
                    || (iTransferMode == TRFM_TRANSFER)) {
                sourceDb = new TransferDb(
                    ConnectionDialog.createConnection(
                        fMain, "Source Database"), this);

                if (!sourceDb.isConnected()) {
                    Exit();

                    return;
                }
            }

            if ((iTransferMode == TRFM_RESTORE)
                    || (iTransferMode == TRFM_TRANSFER)) {
                targetDb = new TransferDb(
                    ConnectionDialog.createConnection(
                        fMain, "Target Database"), this);

                if (!targetDb.isConnected()) {
                    Exit();

                    return;
                }
            } else {
                FileDialog f = new FileDialog(fMain, "Dump FileName",
                                              FileDialog.SAVE);

                f.show();

                String sFileName = f.getFile();
                String Path      = f.getDirectory();

                if ((sFileName == null) || (sFileName.equals(""))) {
                    Exit();

                    return;
                } else {
                    targetDb = new TransferSQLText(Path + sFileName, this);
                }
            }
        } catch (Exception e) {
            Exit();
            e.printStackTrace();

            return;
        }

        if ((iTransferMode == TRFM_DUMP)
                || (iTransferMode == TRFM_TRANSFER)) {
            iSelectionStep = SELECT_SOURCE_CATALOG;
            sSourceCatalog = null;

            ProcessNextStep();
/*
            CatalogToSelect();
*/
        }

/*
        if (lTable.getItemCount()>0) {
            lTable.select(0);
            displayTable((TransferTable) tTable.elementAt(0));
        }
*/
        fMain.show();

        return;
    }

    private void RefreshMainDisplay() {

        lTable.removeAll();
        lTable.repaint();

        try {
            tTable = sourceDb.getTables(sSourceCatalog, sSourceSchemas);

            for (int i = 0; i < tTable.size(); i++) {
                TransferTable t = (TransferTable) tTable.elementAt(i);

                t.setDest(sDestSchema, targetDb);
                t.getTableStructure();
                lTable.add(t.sSourceTable);
                lTable.select(i);
                displayTable(t);
            }

            bStart.setEnabled(true);

            if (iTransferMode == TRFM_TRANSFER) {
                trace("Edit definitions and press [Start Transfer]");
            } else if (iTransferMode == TRFM_DUMP) {
                trace("Edit definitions and press [Start Dump]");
            }
        } catch (Exception e) {
            trace("Exception reading source tables: " + e);
            e.printStackTrace();
        }

        fMain.show();
    }

    /**
     * Method declaration
     *
     *
     * @param f
     * @param m
     */
    private void addMenuItems(Menu f, String m[]) {

        for (int i = 0; i < m.length; i++) {
            if (m[i].equals("-")) {
                f.addSeparator();
            } else {
                MenuItem item = new MenuItem(m[i]);

                item.addActionListener(this);
                f.add(item);
            }
        }
    }

    /**
     * Method declaration
     *
     *
     * @param e
     */
    public void itemStateChanged(ItemEvent e) {

        ItemSelectable item = e.getItemSelectable();

        if (item == lTable) {
            if (iSelectionStep == SELECT_SOURCE_TABLES) {
                String table    = lTable.getSelectedItem();
                int    selected = ((Integer) e.getItem()).intValue();

                for (int i = 0; i < tTable.size(); i++) {
                    TransferTable t = (TransferTable) tTable.elementAt(i);

                    if (t == null) {
                        continue;
                    }

                    if (i == selected) {
                        saveTable();
                        displayTable(t);
                        updateEnabled(true);
                    }
                }
            }
        } else {

            // it must be a checkbox
            saveTable();
            updateEnabled(true);
        }
    }

    /**
     * Method declaration
     *
     */
    private void saveTable() {

        if (tCurrent == null) {
            return;
        }

        TransferTable t = tCurrent;

        t.sSourceTable     = tSourceTable.getText();
        t.sDestTable       = tDestTable.getText();
        t.sDestDrop        = tDestDrop.getText();
        t.sDestCreateIndex = tDestCreateIndex.getText();
        t.sDestDropIndex   = tDestDropIndex.getText();
        t.sDestCreate      = tDestCreate.getText();
        t.sDestDelete      = tDestDelete.getText();
        t.sSourceSelect    = tSourceSelect.getText();
        t.sDestInsert      = tDestInsert.getText();
        t.bTransfer        = cTransfer.getState();
        t.bDrop            = cDrop.getState();
        t.bCreate          = cCreate.getState();
        t.bDelete          = cDelete.getState();
        t.bInsert          = cInsert.getState();
        t.bAlter           = cAlter.getState();
        t.bCreateIndex     = cCreateIndex.getState();
        t.bDropIndex       = cDropIndex.getState();

        boolean reparsetable = ((t.bFKForced != cFKForced.getState())
                                || (t.bIdxForced != cIdxForced.getState()));

        t.bFKForced  = cFKForced.getState();
        t.bIdxForced = cIdxForced.getState();

        if (reparsetable) {
            try {
                t.getTableStructure();
            } catch (Exception e) {
                trace("Exception reading source tables: " + e);
                e.printStackTrace();
            }
        }
    }

    /**
     * Method declaration
     *
     *
     * @param t
     */
    private void displayTable(TransferTable t) {

        tCurrent = t;

        if (t == null) {
            return;
        }

        tSourceTable.setText(t.sSourceTable);
        tDestTable.setText(t.sDestTable);
        tDestDrop.setText(t.sDestDrop);
        tDestCreateIndex.setText(t.sDestCreateIndex);
        tDestDropIndex.setText(t.sDestDropIndex);
        tDestCreate.setText(t.sDestCreate);
        tDestDelete.setText(t.sDestDelete);
        tSourceSelect.setText(t.sSourceSelect);
        tDestInsert.setText(t.sDestInsert);
        tDestAlter.setText(t.sDestAlter);
        cTransfer.setState(t.bTransfer);
        cDrop.setState(t.bDrop);
        cCreate.setState(t.bCreate);
        cDropIndex.setState(t.bDropIndex);
        cCreateIndex.setState(t.bCreateIndex);
        cDelete.setState(t.bDelete);
        cInsert.setState(t.bInsert);
        cAlter.setState(t.bAlter);
        cFKForced.setState(t.bFKForced);
        cIdxForced.setState(t.bIdxForced);
    }

    /**
     * Method declaration
     *
     *
     * @param and
     */
    private void updateEnabled(boolean and) {

        boolean b = cTransfer.getState();

        tDestTable.setEnabled(and && b);
        tDestDrop.setEnabled(and && b && cDrop.getState());
        tDestCreate.setEnabled(and && b && cCreate.getState());
        tDestDelete.setEnabled(and && b && cDelete.getState());
        tDestCreateIndex.setEnabled(and && b && cCreateIndex.getState());
        tDestDropIndex.setEnabled(and && b && cDropIndex.getState());
        tSourceSelect.setEnabled(and && b);
        tDestInsert.setEnabled(and && b && cInsert.getState());
        tDestAlter.setEnabled(and && b && cAlter.getState());
        cDrop.setEnabled(and && b);
        cCreate.setEnabled(and && b);
        cDelete.setEnabled(and && b);
        cCreateIndex.setEnabled(and && b);
        cDropIndex.setEnabled(and && b);
        cInsert.setEnabled(and && b);
        cAlter.setEnabled(and && b);
        cFKForced.setEnabled(cAlter.getState());
        cIdxForced.setEnabled(cCreateIndex.getState());
        bStart.setEnabled(and);

        if (iTransferMode == TRFM_TRANSFER) {
            bContinue.setEnabled(and);
        }
    }

    /**
     * Method ProcessNextStep
     */
    private void ProcessNextStep() {

        switch (iSelectionStep) {

            case SELECT_SOURCE_CATALOG :
            case SELECT_DEST_CATALOG :
                if (CatalogToSelect()) {
                    fMain.show();

                    return;
                }
                break;

            case SELECT_DEST_SCHEMA :
            case SELECT_SOURCE_SCHEMA :
                if (SchemaToSelect()) {
                    fMain.show();

                    return;
                }
                break;

            case SELECT_SOURCE_TABLES :
                if (iTransferMode == TRFM_TRANSFER) {
                    bStart.setLabel("Start Transfer");
                } else if (iTransferMode == TRFM_DUMP) {
                    bStart.setLabel("Start Dump");
                }

                bStart.invalidate();
                bStart.setEnabled(false);
                lTable.setMultipleMode(false);
                RefreshMainDisplay();
                break;

            default :
                break;
        }
    }

    /**
     * Method declaration
     *
     *
     * @param ev
     */
    public void actionPerformed(ActionEvent ev) {

        if (ev.getSource() instanceof TextField) {
            saveTable();

            return;
        }

        String   s = ev.getActionCommand();
        MenuItem i = new MenuItem();

        if (s == null) {
            if (ev.getSource() instanceof MenuItem) {
                i = (MenuItem) ev.getSource();
                s = i.getLabel();
            }
        }

        if (s.equals("Start Transfer") || s.equals("ReStart Transfer")) {
            bStart.setLabel("ReStart Transfer");
            bStart.invalidate();

            CurrentTransfer = 0;
            CurrentAlter    = 0;

            transfer();
        } else if (s.equals("Continue Transfer")) {
            transfer();
        } else if (s.equals("Start Dump")) {
            CurrentTransfer = 0;
            CurrentAlter    = 0;

            transfer();
        } else if (s.equals("Quit")) {
            Exit();
        } else if (s.indexOf("Select Schema") >= 0) {
            String[] selection = lTable.getSelectedItems();

            if ((selection == null) || (selection.length == 0)) {
                return;
            }

            if (iSelectionStep == this.SELECT_SOURCE_SCHEMA) {
                sSourceSchemas = selection;
            } else {
                sDestSchema = selection[0];
            }

            if (iTransferMode == TRFM_DUMP) {
                iSelectionStep = this.SELECT_SOURCE_TABLES;
            } else {
                iSelectionStep++;
            }

            ProcessNextStep();
        } else if (s.indexOf("Select Catalog") >= 0) {
            String selection = lTable.getSelectedItem();

            if ((selection == null) || (selection.equals(""))) {
                return;
            }

            if (iSelectionStep == this.SELECT_SOURCE_CATALOG) {
                sSourceCatalog = selection;
                sSourceSchemas = null;
            } else {
                sDestCatalog = selection;
                sDestSchema  = null;

                try {
                    targetDb.setCatalog(sDestCatalog);
                } catch (Exception ex) {
                    trace("Catalog " + sDestCatalog
                          + " could not be selected in the target database");

                    sDestCatalog = null;
                }
            }

            iSelectionStep++;

            ProcessNextStep();
/*
            if (SchemaToSelect()) {
                fMain.show();

                return;
            }

            if (iSelectionStep == this.SELECT_SOURCE_TABLES) {
                lTable.removeAll();
                lTable.repaint();
                RefreshMainDisplay();
            } else if (iSelectionStep == this.SELECT_DEST_CATALOG) {
                if (CatalogToSelect()) {
                    fMain.show();

                    return;
                }
            }
*/
        } else if (s.equals("Insert 10 rows only")) {
            iMaxRows = 10;
        } else if (s.equals("Insert 1000 rows only")) {
            iMaxRows = 1000;
        } else if (s.equals("Insert all rows")) {
            iMaxRows = 0;
        } else if (s.equals("Load Settings...")) {
            FileDialog f = new FileDialog(fMain, "Load Settings",
                                          FileDialog.LOAD);

            f.show();

            String file = f.getFile();

            if (file != null) {
                LoadPrefs(file);
                displayTable(tCurrent);
            }
        } else if (s.equals("Save Settings...")) {
            FileDialog f = new FileDialog(fMain, "Save Settings",
                                          FileDialog.SAVE);

            f.show();

            String file = f.getFile();

            if (file != null) {
                SavePrefs(file);
            }
        } else if (s.equals("Exit")) {
            windowClosing(null);
        }
    }

    /**
     * Method declaration
     *
     *
     * @param e
     */
    public void windowActivated(WindowEvent e) {}

    /**
     * Method declaration
     *
     *
     * @param e
     */
    public void windowDeactivated(WindowEvent e) {}

    /**
     * Method declaration
     *
     *
     * @param e
     */
    public void windowClosed(WindowEvent e) {}

    private void cleanup() {

        try {
            if (sourceDb != null) {
                sourceDb.close();
            }

            if (targetDb != null) {
                targetDb.close();
            }
        } catch (Exception e) {}
    }

    /**
     * Method declaration
     *
     *
     * @param ev
     */
    public void windowClosing(WindowEvent ev) {

        fMain.dispose();

        if (bMustExit) {
            System.exit(0);
        }
    }

    /**
     * Method declaration
     *
     *
     * @param e
     */
    public void windowDeiconified(WindowEvent e) {}

    /**
     * Method declaration
     *
     *
     * @param e
     */
    public void windowIconified(WindowEvent e) {}

    /**
     * Method declaration
     *
     *
     * @param e
     */
    public void windowOpened(WindowEvent e) {}

    /**
     * Method declaration
     *
     */
    private void initGUI() {

        Font fFont = new Font("Dialog", Font.PLAIN, 12);

        setLayout(new BorderLayout());

        Panel p = new Panel();

        p.setBackground(SystemColor.control);
        p.setLayout(new GridLayout(16, 1));

        tSourceTable = new TextField();

        tSourceTable.setEnabled(false);

        tDestTable = new TextField();

        tDestTable.addActionListener(this);

        tDestDrop = new TextField();

        tDestDrop.addActionListener(this);

        tDestCreate = new TextField();

        tDestCreate.addActionListener(this);

        tDestDelete = new TextField();

        tDestDelete.addActionListener(this);

        tDestCreateIndex = new TextField();

        tDestCreateIndex.addActionListener(this);

        tDestDropIndex = new TextField();

        tDestDropIndex.addActionListener(this);

        tSourceSelect = new TextField();

        tSourceSelect.addActionListener(this);

        tDestInsert = new TextField();

        tDestInsert.addActionListener(this);

        tDestAlter = new TextField();

        tDestAlter.addActionListener(this);

        cTransfer = new Checkbox("Transfer to destination table", true);

        cTransfer.addItemListener(this);

        cDrop = new Checkbox("Drop destination table (ignore error)", true);

        cDrop.addItemListener(this);

        cCreate = new Checkbox("Create destination table", true);

        cCreate.addItemListener(this);

        cDropIndex = new Checkbox("Drop destination index (ignore error)",
                                  true);

        cDropIndex.addItemListener(this);

        cIdxForced = new Checkbox("force Idx_ prefix for indexes names",
                                  false);

        cIdxForced.addItemListener(this);

        cCreateIndex = new Checkbox("Create destination index", true);

        cCreateIndex.addItemListener(this);

        cDelete = new Checkbox("Delete rows in destination table", true);

        cDelete.addItemListener(this);

        cInsert = new Checkbox("Insert into destination", true);

        cInsert.addItemListener(this);

        cFKForced = new Checkbox("force FK_ prefix for foreign key names",
                                 false);

        cFKForced.addItemListener(this);

        cAlter = new Checkbox("Alter destination table", true);

        cAlter.addItemListener(this);
        p.add(createLabel("Source table"));
        p.add(tSourceTable);
        p.add(cTransfer);
        p.add(tDestTable);
        p.add(cDrop);
        p.add(tDestDrop);
        p.add(cCreate);
        p.add(tDestCreate);
        p.add(cDropIndex);
        p.add(tDestDropIndex);
        p.add(cCreateIndex);
        p.add(tDestCreateIndex);
        p.add(cDelete);
        p.add(tDestDelete);
        p.add(cAlter);
        p.add(tDestAlter);
        p.add(createLabel("Select source records"));
        p.add(tSourceSelect);
        p.add(cInsert);
        p.add(tDestInsert);
        p.add(createLabel(""));
        p.add(createLabel(""));
        p.add(cIdxForced);
        p.add(cFKForced);
        p.add(createLabel(""));
        p.add(createLabel(""));

        if (iTransferMode == TRFM_TRANSFER) {
            bStart    = new Button("Start Transfer");
            bContinue = new Button("Continue Transfer");

            bContinue.setEnabled(false);
        } else {
            bStart = new Button("Start Dump");
        }

        bStart.addActionListener(this);
        p.add(bStart);

        if (iTransferMode == TRFM_TRANSFER) {
            bContinue.addActionListener(this);
            p.add(bContinue);
        }

        bStart.setEnabled(false);
        fMain.add("Center", createBorderPanel(p));

        lTable = new java.awt.List(10);

        lTable.addItemListener(this);
        fMain.add("West", createBorderPanel(lTable));

        tMessage = new TextField();

        Panel pMessage = createBorderPanel(tMessage);

        fMain.add("South", pMessage);
    }

    /**
     * Method declaration
     *
     *
     * @param center
     *
     * @return
     */
    private Panel createBorderPanel(Component center) {

        Panel p = new Panel();

        p.setBackground(SystemColor.control);
        p.setLayout(new BorderLayout());
        p.add("Center", center);
        p.add("South", createLabel(""));
        p.add("East", createLabel(""));
        p.add("West", createLabel(""));
        p.setBackground(SystemColor.control);

        return p;
    }

    /**
     * Method declaration
     *
     *
     * @param s
     *
     * @return
     */
    private Label createLabel(String s) {

        Label l = new Label(s);

        l.setBackground(SystemColor.control);

        return l;
    }

    private void SavePrefs(String f) {
        saveTable();
        TransferCommon.savePrefs(f, sourceDb, targetDb, this, tTable);
    }

    private void LoadPrefs(String f) {

        TransferTable t;

        trace("Parsing Settings file");
        bStart.setEnabled(false);

        if (iTransferMode == TRFM_TRANSFER) {
            bContinue.setEnabled(false);
        }

        tTable = TransferCommon.loadPrefs(f, sourceDb, targetDb, this);
        iSelectionStep = SELECT_SOURCE_TABLES;

        lTable.removeAll();

        for (int i = 0; i < tTable.size(); i++) {
            t = (TransferTable) tTable.elementAt(i);

            lTable.add(t.sSourceTable);
        }

        t = (TransferTable) tTable.elementAt(0);

        displayTable(t);
        lTable.select(0);
        updateEnabled(true);
        lTable.invalidate();

        if (iTransferMode == TRFM_TRANSFER) {
            bStart.setLabel("Start Transfer");
            trace("Edit definitions and press [Start Transfer]");
        } else if (iTransferMode == TRFM_DUMP) {
            bStart.setLabel("Start Dump");
            trace("Edit definitions and press [Start Dump]");
        }

        bStart.invalidate();

        if (iTransferMode == TRFM_TRANSFER) {
            bContinue.setEnabled(false);
        }
    }

    /**
     * Method declaration
     *
     */
    private void transfer() {

        saveTable();
        updateEnabled(false);
        trace("Start Transfer");

        int           TransferIndex = CurrentTransfer;
        int           AlterIndex    = CurrentAlter;
        TransferTable t             = null;
        long          startTime, stopTime;

        startTime = System.currentTimeMillis();

        try {
            for (int i = TransferIndex; i < tTable.size(); i++) {
                CurrentTransfer = i;
                t               = (TransferTable) tTable.elementAt(i);

                lTable.select(i);
                displayTable(t);
                t.transferStructure();
                t.transferData(iMaxRows);
            }

            for (int i = AlterIndex; i < tTable.size(); i++) {
                CurrentAlter = i;
                t            = (TransferTable) tTable.elementAt(i);

                lTable.select(i);
                displayTable(t);
                t.transferAlter();
            }

            stopTime = System.currentTimeMillis();

            trace("Transfer finished successfully in: "
                  + (stopTime - startTime) / 1000.00 + " sec");

            if (iTransferMode == TRFM_TRANSFER) {
                bContinue.setLabel("Quit");
                bContinue.setEnabled(true);
                bContinue.invalidate();
            } else {
                bStart.setLabel("Quit");
                bStart.setEnabled(true);
                bStart.invalidate();
            }
        } catch (Exception e) {
            String last = tMessage.getText();

            trace("Transfer stopped - " + last + " /  / Error: "
                  + e.getMessage());
            e.printStackTrace();
        }

        if (iTransferMode == TRFM_TRANSFER) {
            bContinue.setEnabled((CurrentAlter < tTable.size()));
        }

        updateEnabled(true);
        System.gc();
    }

    protected void Exit() {

        cleanup();
        fMain.dispose();

        if (bMustExit) {
            System.exit(0);
        }
    }
}
