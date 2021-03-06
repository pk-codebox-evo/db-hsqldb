/* Copyright (c) 1995-2000, The Hypersonic SQL Group.
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
 * Neither the name of the Hypersonic SQL Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE HYPERSONIC SQL GROUP,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software consists of voluntary contributions made by many individuals
 * on behalf of the Hypersonic SQL Group.
 *
 *
 * For work added by the HSQL Development Group:
 *
 * Copyright (c) 2001-2009, The HSQL Development Group
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

import java.io.IOException;

import org.hsqldb.index.DiskNode;
import org.hsqldb.index.Node;
import org.hsqldb.lib.IntLookup;
import org.hsqldb.rowio.RowInputInterface;
import org.hsqldb.rowio.RowOutputInterface;

// fredt@users 20020221 - patch 513005 by sqlbob@users (RMP)
// fredt@users 20020920 - patch 1.7.1 - refactoring to cut memory footprint
// fredt@users 20021205 - patch 1.7.2 - enhancements
// fredt@users 20021215 - doc 1.7.2 - javadoc comments
// boucherb@users - 20040411 - doc 1.7.2 - javadoc comments

/**
 *  In-memory representation of a disk-based database row object with  methods
 *  for serialization and de-serialization. <p>
 *
 *  A CachedRow is normally part of a circular double linked list which
 *  contains all of the Rows currently in the Cache for the database. It is
 *  unlinked from this list when it is freed from the Cache to make way for
 *  other rows.
 *
 *  New class from the Hypersonic Original
 *
 * @author Thomas Mueller (Hypersonic SQL Group)
 * @version 1.8.0
 * @since Hypersonic SQL
 */
public class CachedRow extends Row {

    public static final int NO_POS = -1;

    //
    protected TableBase tTable;
    int                 storageSize;

    /**
     *  Flag indicating unwritten data.
     */
    protected boolean hasDataChanged;

    /**
     *  Flag indicating Node data has changed.
     */
    boolean hasNodesChanged;

    /**
     *  Default constructor used only in subclasses.
     */
    CachedRow() {}

    /**
     *  Constructor for new Rows.  Variable hasDataChanged is set to true in
     *  order to indicate the data needs saving.
     *
     * @param t table
     * @param o row data
     * @throws HsqlException if a database access error occurs
     */
    public CachedRow(TableBase t, Object[] o) throws HsqlException {

        tTable  = t;
        tableId = tTable.getId();

        setNewNodes();

        oData          = o;
        hasDataChanged = hasNodesChanged = true;
    }

    /**
     *  Constructor when read from the disk into the Cache.
     *
     * @param t table
     * @param in data source
     * @throws IOException
     * @throws HsqlException
     */
    public CachedRow(TableBase t,
                     RowInputInterface in) throws IOException, HsqlException {

        tTable      = t;
        iPos        = in.getPos();
        storageSize = in.getSize();

        int indexcount = t.getIndexCount();

        nPrimaryNode = new DiskNode(this, in, 0);

        Node n = nPrimaryNode;

        for (int i = 1; i < indexcount; i++) {
            n.nNext = new DiskNode(this, in, i);
            n       = n.nNext;
        }

        oData = in.readData(tTable.getColumnTypes());
    }

    Node insertNode(int index) {
        return null;
    }

    private void readRowInfo(RowInputInterface in)
    throws IOException, HsqlException {

        // for use when additional transaction info is attached to rows
    }

    /**
     *  This method is called only when the Row is deleted from the database
     *  table. The links with all the other objects apart from the data
     *  are removed.
     *
     * @throws HsqlException
     */
    public void delete() throws HsqlException {
        super.delete();
    }

    public int getStorageSize() {
        return storageSize;
    }

    /**
     * Sets the file position for the row
     *
     * @param pos position in data file
     */
    public void setPos(int pos) {

        iPos = pos;

        Node n = nPrimaryNode;

        while (n != null) {
            ((DiskNode) n).iData = iPos;
            n                    = n.nNext;
        }
    }

    /**
     * Sets flag for Node data change.
     */
    public void setChanged() {
        hasNodesChanged = true;
    }

    /**
     * Returns true if Node data has changed.
     *
     * @return boolean
     */
    public boolean hasChanged() {
        return hasNodesChanged;
    }

    /**
     * Returns the Table to which this Row belongs.
     *
     * @return Table
     */
    public TableBase getTable() {
        return tTable;
    }

    public void setStorageSize(int size) {
        storageSize = size;
    }

    /**
     * Returns true if any of the Nodes for this row is a root node.
     * Used only in Cache.java to avoid removing the row from the cache.
     *
     * @return boolean
     * @throws HsqlException
     */
    public boolean isKeepInMemory() {

        Node n = nPrimaryNode;

/*
        while (n != null) {
            if (n.isRoot()) {
                return true;
            }

            n = n.nNext;
        }
*/
        return false;
    }

    public void destroy() {

        super.destroy();

        tTable = null;
    }

    /**
     * used in CachedDataRow
     */
    void setNewNodes() {
        int indexcount = tTable.getIndexCount();

        nPrimaryNode = new DiskNode(this, 0);

        Node n = nPrimaryNode;

        for (int i = 1; i < indexcount; i++) {
            n.nNext = new DiskNode(this, i);
            n       = n.nNext;
        }
    }

    /**
     * Used exclusively by Cache to save the row to disk. New implementation in
     * 1.7.2 writes out only the Node data if the table row data has not
     * changed. This situation accounts for the majority of invocations as for
     * each row deleted or inserted, the Nodes for several other rows will
     * change.
     */
    public void write(RowOutputInterface out) {

        try {
            writeNodes(out);

            if (hasDataChanged) {
                out.writeData(oData, tTable.colTypes);
                out.writeEnd();

                hasDataChanged = false;
            }
        } catch (IOException e) {}
    }

    private void writeRowInfo(RowOutputInterface out) {

        // for use when additional transaction info is attached to rows
    }

    public void write(RowOutputInterface out, IntLookup lookup) {

        out.writeSize(storageSize);

        Node rownode = nPrimaryNode;

        while (rownode != null) {
            ((DiskNode) rownode).writeTranslate(out, lookup);

            rownode = rownode.nNext;
        }

        out.writeData(getData(), tTable.colTypes);
        out.writeEnd();
    }

    /**
     *  Writes the Nodes, immediately after the row size.
     *
     * @param out
     *
     * @throws IOException
     * @throws HsqlException
     */
    private void writeNodes(RowOutputInterface out) throws IOException {

        out.writeSize(storageSize);

        Node n = nPrimaryNode;

        while (n != null) {
            n.write(out);

            n = n.nNext;
        }

        hasNodesChanged = false;
    }

    /**
     * Lifetime scope of this method depends on the operations performed on
     * any cached tables since this row or the parameter were constructed.
     * If only deletes or only inserts have been performed, this method
     * remains valid. Otherwise it can return invalid results.
     *
     * @param obj row to compare
     * @return boolean
     */
    public boolean equals(Object obj) {

        if (obj == this) {
            return true;
        }

        if (obj instanceof CachedRow) {
            return ((CachedRow) obj).iPos == iPos;
        }

        return false;
    }

    /**
     * Hash code is valid only until a modification to the cache
     *
     * @return file position of row
     */
    public int hashCode() {
        return iPos;
    }
}
