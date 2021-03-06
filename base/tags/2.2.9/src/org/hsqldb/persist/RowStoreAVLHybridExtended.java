/* Copyright (c) 2001-2011, The HSQL Development Group
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


package org.hsqldb.persist;

import org.hsqldb.Row;
import org.hsqldb.RowAVL;
import org.hsqldb.Session;
import org.hsqldb.Table;
import org.hsqldb.TableBase;
import org.hsqldb.index.Index;
import org.hsqldb.index.NodeAVL;
import org.hsqldb.navigator.RowIterator;

/*
 * Implementation of PersistentStore for information schema and temp tables.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.2.9
 * @since 2.0.1
 */
public class RowStoreAVLHybridExtended extends RowStoreAVLHybrid {

    public RowStoreAVLHybridExtended(Session session,
                                     PersistentStoreCollection manager,
                                     TableBase table, boolean diskBased) {
        super(session, manager, table, diskBased);
    }

    public CachedObject getNewCachedObject(Session session, Object object,
                                           boolean tx) {

        if (indexList != table.getIndexList()) {
            resetAccessorKeys(table.getIndexList());
        }

        return super.getNewCachedObject(session, object, tx);
    }

    public void indexRow(Session session, Row row) {

        NodeAVL node  = ((RowAVL) row).getNode(0);
        int     count = 0;

        while (node != null) {
            count++;

            node = node.nNext;
        }

        if (count != indexList.length) {
            resetAccessorKeys(table.getIndexList());
            ((RowAVL) row).setNewNodes(this);
        }

        super.indexRow(session, row);
    }

    /**
     * Row might have changed from memory to disk or indexes added
     */
    public void delete(Session session, Row row) {

        row = ((Table) table).getDeleteRowFromLog(session, row.getData());

        super.delete(session, row);
    }

    public CachedObject getAccessor(Index key) {

        int position = key.getPosition();

        if (position >= accessorList.length || indexList[position] != key) {
            resetAccessorKeys(table.getIndexList());

            return getAccessor(key);
        }

        return accessorList[position];
    }

    public synchronized void resetAccessorKeys(Index[] keys) {

        if (indexList.length == 0 || accessorList[0] == null) {
            indexList    = keys;
            accessorList = new CachedObject[indexList.length];

            return;
        }

        if (isCached) {
            resetAccessorKeysForCached();

            return;
        }

        super.resetAccessorKeys(keys);
    }

    private void resetAccessorKeysForCached() {

        RowStoreAVLHybrid tempStore = new RowStoreAVLHybridExtended(session,
            manager, table, true);

        tempStore.changeToDiskTable(session);

        RowIterator iterator = table.rowIterator(this);

        while (iterator.hasNext()) {
            Row row = iterator.getNextRow();
            Row newRow = (Row) tempStore.getNewCachedObject(session,
                row.getData(), false);

            tempStore.indexRow(session, newRow);
        }

        indexList    = tempStore.indexList;
        accessorList = tempStore.accessorList;
    }
}
