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
import java.lang.reflect.Constructor;
import java.sql.SQLException;
import org.hsqldb.lib.HsqlArrayList;
import org.hsqldb.lib.FileUtil;

// fredt@users 20011220 - patch 437174 by hjb@users - cache update
// most changes and comments by HSB are kept unchanged
// fredt@users 20020221 - patch 513005 by sqlbob@users (RMP) - cache update
// fredt@users 20020320 - doc 1.7.0 by boucherb@users - doc update
// fredt@users 20021105 - patch 1.7.2 - refactoring and enhancements

/**
 *
 * Handles cached table persistence through a .data file and memory cache.<p>
 *
 * All CACHED tables are stored in a .data file. The Cache object provides
 * buffered access to the rows. The buffer is a linear
 * hash index implementation. Chains of elements in the hash table buckets
 * form a circular double-linked list of all the
 * cached elements. This list is used for selecting modified rows that need
 * saving to disk or to drop infrequently accessed rows to make way for new
 * rows. Saving modified rows to disk is performed in the sequential order of
 * the file offsets of the rows.<p>
 *
 * A separate linked list of free slots in the .data file is also kept. This
 * list is formed when rows are deleted from the database, and is used for
 * allocating space to newly created rows.<p>
 *
 * The algorithm for dropping rows from the cache has changed in version
 * 1.7.2 to better reflect row usage. (fred@users)
 *
 * @version    1.7.2
 * @see        CachedRow
 * @see        CacheFree
 */
class Cache {

    // cached row access counter
    static int iCurrentAccess = 0;

    // pre openning fields
    private Database                 dDatabase;
    protected HsqlDatabaseProperties dbProps;
    protected String                 sName;

    // cache operation mode
    protected boolean storeOnInsert;

    // post openning constant fields
    boolean           cacheReadonly;
    private int       cacheScale;
    private int       cachedRowType = DatabaseRowOutput.CACHE_ROW_160;
    private int       cacheLength;
    private int       writerLength;
    private int       maxCacheSize;
    private int       multiplierMask;
    private CachedRow rData[];
    private CachedRow rWriter[];

    // file format fields
    static final int FREE_POS_POS     = 16;    // where iFreePos is saved
    static final int INITIAL_FREE_POS = 32;

    // variable fields
    protected DatabaseFile rFile;
    protected int          iFreePos;

    //
    private CachedRow        rFirst;           // must point to one of rData[]
    private CachedRow        rLastChecked;     // can be any row
    private static final int MAX_FREE_COUNT = 1024;

    //
    private CacheFree fRoot;
    private int       iFreeCount;
    private int       iCacheSize;

    // reusable input / output streams
    DatabaseRowInputInterface  rowIn;
    DatabaseRowOutputInterface rowOut;

    /**
     *  Construct a new Cache object with the given database path name and
     *  the properties object to get the initial settings from.
     */
    private void init(int scale) {

        cacheReadonly = dDatabase.bReadOnly;
        cacheScale    = scale;
        cacheLength   = 1 << cacheScale;

        // HJB-2001-06-21: use different smaller size for the writer
        writerLength = cacheLength - 3;

        // HJB-2001-06-21: let the cache be larger than the array
        maxCacheSize   = cacheLength * 3;
        multiplierMask = cacheLength - 1;
        rData          = new CachedRow[cacheLength];
        rWriter        = new CachedRow[cacheReadonly ? 0
                                                     : writerLength];    // HJB-2001-06-21
        rFirst       = null;
        rLastChecked = null;
        iFreePos     = 0;
        fRoot        = null;
        iFreeCount   = 0;
        iCacheSize   = 0;
    }

    private void initBuffers() throws SQLException {
        rowIn  = DatabaseRowInput.newDatabaseRowInput(cachedRowType);
        rowOut = DatabaseRowOutput.newDatabaseRowOutput(cachedRowType);
    }

    Cache(String name, Database db) throws SQLException {

        sName     = name;
        dDatabase = db;
        dbProps   = db.getProperties();

        int scale = getCacheScale(db.getProperties());

        init(scale);
    }

    private static int getCacheScale(HsqlDatabaseProperties props) {

        int scale = 0;

        try {
            scale = props.getIntegerProperty("hsqldb.cache_scale", 14);

            if (scale < 8) {
                scale = 8;

                throw new NumberFormatException();
            } else if (scale > 16) {
                scale = 16;

                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            Trace.printSystemOut(
                "bad value for hsqldb.cache_scale in properties file");
        }

        return scale;
    }

    /**
     * Opens this object's database file.
     */
    void open(boolean readonly) throws SQLException {

        try {
            boolean exists = false;
            File    f      = new File(sName);

            if (f.exists() && f.length() > FREE_POS_POS) {
                exists = true;
            }

            rFile = new DatabaseFile(sName, readonly ? "r"
                                                     : "rw", 2048);

            if (exists) {
                rFile.readSeek(FREE_POS_POS);

                iFreePos = rFile.readInteger();
            } else {
                iFreePos = INITIAL_FREE_POS;

                dbProps.setProperty("hsqldb.cache_version", "1.7.0");
            }

            String cacheVersion = dbProps.getProperty("hsqldb.cache_version",
                "1.6.0");

            if (cacheVersion.equals("1.7.0")) {
                cachedRowType = DatabaseRowOutput.CACHE_ROW_170;
            }

            initBuffers();
        } catch (Exception e) {
            Trace.throwerror(Trace.FILE_IO_ERROR,
                             "error " + e + " opening file " + sName);
        }
    }

    /**
     *  Writes out all cached data and the free position to this
     *  object's database file and then closes the file.
     */
    void flush() throws SQLException {

        if (rFile == null || rFile.readOnly) {
            return;
        }

        try {
            rFile.seek(FREE_POS_POS);
            rFile.writeInteger(iFreePos);
            saveAll();
            rFile.close();

            rFile = null;

            boolean empty = new File(sName).length() < INITIAL_FREE_POS;

            if (empty) {
                new File(sName).delete();
            }
        } catch (Exception e) {
            Trace.throwerror(Trace.FILE_IO_ERROR,
                             "error " + e + " closing file " + sName);
        }
    }

    /**
     *  Writes out all the rows to a new file without fragmentation and
     *  returns an ArrayList containing new positions for index roots.
     */
    HsqlArrayList defrag() throws SQLException {

        HsqlArrayList indexRoots = null;

        try {
            flush();

            if (!FileUtil.exists(sName)) {
                return null;
            }

            open(true);

            DataFileDefrag dfd = new DataFileDefrag();

            indexRoots = dfd.defrag(dDatabase, rFile, sName);

            closeFile();
            Trace.printSystemOut("closed source");
            new File(sName).delete();
            new File(sName + ".new").renameTo(new File(sName));
            init(cacheScale);
            open(cacheReadonly);
            Trace.printSystemOut("opened new file");
        } catch (Exception e) {
            e.printStackTrace();
            Trace.throwerror(Trace.FILE_IO_ERROR,
                             "error " + e + " defrag file " + sName);
        }

        return indexRoots;
    }

    /**
     *  Closes this object's database file without flushing pending writes.
     */
    void closeFile() throws SQLException {

        if (rFile == null) {
            return;
        }

        try {
            rFile.close();

            rFile = null;
        } catch (Exception e) {
            Trace.throwerror(Trace.FILE_IO_ERROR,
                             "error " + e + " in shutdown file " + sName);
        }
    }

    /**
     * Marks space in this object's cache file as free. <p>
     *
     * <B>Note:</B> If there exists more than MAX_FREE_COUNT free positions,
     * then they are probably all too small, so we start a new list. <p>
     *
     *  todo: This is wrong when deleting lots of records
     */
    void free(CachedRow r) throws SQLException {

        iFreeCount++;

        CacheFree n = new CacheFree();

        n.iPos    = r.iPos;
        n.iLength = r.storageSize;

        if (iFreeCount > MAX_FREE_COUNT) {
            iFreeCount = 0;
        } else {
            n.fNext = fRoot;
        }

        fRoot = n;

        // it's possible to remove roots too
        remove(r);
    }

    /**
     * Calculates the number of bytes required to store a Row in this object's
     * database file.
     */
    protected void setStorageSize(CachedRow r) throws SQLException {

        // 32 bytes overhead for each index + iSize, iPos
        Table t    = r.getTable();
        int   size = 8 + 16 * t.getIndexCount();

        size          += rowOut.getSize(r);
        size          = ((size + 7) / 8) * 8;    // align to 8 byte blocks
        r.storageSize = size;
    }

    /**
     * Adds a Row to the Cache. <p>
     *
     */
    void add(CachedRow r) throws SQLException {

        if (iCacheSize >= maxCacheSize) {
            cleanUp();
        }

        setStorageSize(r);

        r.iLastAccess = iCurrentAccess++;

        int i = setFilePos(r);

        // HJB-2001-06-21
        int       k      = (i >> 3) & multiplierMask;
        CachedRow before = rData[k];

        if (before == null) {
            before = rFirst;
        }

        r.insert(before);

        try {

            // for text tables
            if (storeOnInsert) {
                saveRow(r);
            }
        } catch (IOException e) {
            throw Trace.error(Trace.FILE_IO_ERROR);
        }

        iCacheSize++;

        rData[k] = r;
        rFirst   = r;
    }

    /**
     * Allocates file space for the row. <p>
     *
     * A Row is added by walking the list of CacheFree
     * objects to see if there is available space to store it,
     * reusing space if it exists.  Otherwise the
     * file is grown to accommodate it.
     */
    int setFilePos(CachedRow r) throws SQLException {

        int       rowSize = r.storageSize;
        int       size    = rowSize;
        CacheFree f       = fRoot;
        CacheFree last    = null;
        int       i       = iFreePos;

        while (f != null) {
            if (Trace.TRACE) {
                Trace.stop();
            }

            // first that is long enough
            if (f.iLength >= size) {
                i    = f.iPos;
                size = f.iLength - size;

                if (size < 32) {

                    // remove almost empty blocks
                    if (last == null) {
                        fRoot = f.fNext;
                    } else {
                        last.fNext = f.fNext;
                    }

                    iFreeCount--;
                } else {
                    f.iLength = size;
                    f.iPos    += rowSize;
                }

                break;
            }

            last = f;
            f    = f.fNext;
        }

        if (i == iFreePos) {
            iFreePos += size;
        }

        r.setPos(i);

        return i;
    }

    /**
     * Constructs a new Row for the specified table, using row data read
     * at the specified position (pos) in this object's database file.
     */
    protected CachedRow makeRow(int pos, Table t) throws SQLException {

        CachedRow r = null;

        try {
            rFile.readSeek(pos);

            int size = rFile.readInteger();

            rowIn.resetRow(pos, size);
            rFile.read(rowIn.getBuffer(), 4, size - 4);

            r = new CachedRow(t, rowIn);
        } catch (IOException e) {
            e.printStackTrace();
            Trace.throwerror(Trace.FILE_IO_ERROR, "reading file : " + e);
        }

        return r;
    }

    /**
     * Reads a Row object from this Cache that corresponds to the
     * (pos) file offset in .data file.
     */
    CachedRow getRow(int pos, Table t) throws SQLException {

        CachedRow r = getRow(pos);

        if (r != null) {
            r.iLastAccess = iCurrentAccess++;

            return r;
        }

        if (iCacheSize >= maxCacheSize) {
            cleanUp();
        }

        //-- makeRow in text tables may change iPos because of blank lines
        //-- row can be null at the end of csv file
        r = makeRow(pos, t);

        if (r == null) {
            return r;
        }

        int       k      = (r.iPos >> 3) & multiplierMask;
        CachedRow before = rData[k];

        if (before == null) {
            before = rFirst;
        }

        if (r != null) {
            r.insert(before);

            iCacheSize++;

            rData[k]      = r;
            rFirst        = r;
            r.iLastAccess = iCurrentAccess++;
        }

        return r;
    }

    private CachedRow getRow(int pos) {

        // HJB-2001-06-21
        int       k     = (pos >> 3) & multiplierMask;
        CachedRow r     = rData[k];
        CachedRow start = r;
        int       p     = 0;

        while (r != null) {
            p = r.iPos;

            if (p == pos) {
                return r;
            } else if (((p >> 3) & multiplierMask) != k) {

                // HJB-2001-06-21 - check the row belongs to this bucket
                break;
            }

            r = r.rNext;

            if (r == start) {
                break;
            }
        }

        return null;
    }

    /**
     * Reduces the number of rows held in this Cache object. <p>
     *
     * Cleanup is done by checking the accessCount of the Rows and removing
     * some of those that have been accessed less frequently.
     *
     */
    private void cleanUp() throws SQLException {

        int count = 0;
        int j     = 0;

        resetAccessCount();

        // HJB-2001-06-21
        while ((j++ < cacheLength) && (iCacheSize > maxCacheSize / 2)
                && (count < writerLength)) {
            CachedRow r = getWorst();

            if (r == null) {
                return;
            }

            if (r.hasChanged()) {

                // HJB-2001-06-21
                // make sure that the row will not be added twice
                // getWorst() in some cases returns the same row many times

                /**
                 *  for (int i=0;i<count;i++) { if (rWriter[i]==r) { r=null;
                 *  break; } } if (r!=null) { rWriter[count++] = r; }
                 */
                rWriter[count++] = r;
            } else {

                // here we can't remove roots
                if (!r.isRoot() &&!r.isLocked()) {
                    remove(r);
                }
            }
        }

        if (count != 0) {
            saveSorted(count);
        }

        for (int i = 0; i < count; i++) {

            // here we can't remove roots
            CachedRow r = rWriter[i];

            if (!r.isRoot()) {
                remove(r);
            }

            rWriter[i] = null;
        }
    }

    protected void remove(Table t) throws SQLException {

        CachedRow row = rFirst;

        for (int i = 0; i < iCacheSize; i++) {
            if (row.tTable == t) {
                row = remove(row);
            } else {
                row = row.rNext;
            }
        }
    }

    /**
     * Removes a Row from this Cache object.
     *
     */
    protected CachedRow remove(CachedRow r) throws SQLException {

        // r.hasChanged() == false unless called from Cache.remove(Table)
        // make sure rLastChecked does not point to r
        if (r == rLastChecked) {
            rLastChecked = rLastChecked.rNext;

            if (rLastChecked == r) {
                rLastChecked = null;
            }
        }

        // make sure rData[k] does not point here
        // HJB-2001-06-21
        int k = (r.iPos >> 3) & multiplierMask;

        if (rData[k] == r) {
            CachedRow n = r.rNext;

            rFirst = n;

            if (n == r || ((n.iPos >> 3) & multiplierMask) != k) {    // HJB-2001-06-21
                n = null;
            }

            rData[k] = n;
        }

        // make sure rFirst does not point here
        if (r == rFirst) {
            rFirst = rFirst.rNext;

            if (r == rFirst) {
                rFirst = null;
            }
        }

        iCacheSize--;

        return r.free();
    }

    /**
     * Finds the Row with the smallest (oldest) iLastAccess member (LRU). <p>
     *
     * <b>Note:</b> This method is called by the cleanup method.
     */
    private CachedRow getWorst() throws SQLException {

        if (rLastChecked == null) {
            rLastChecked = rFirst;

            if (rLastChecked == null) {
                return null;
            }
        }

        CachedRow candidate = rLastChecked;
        int       worst     = rLastChecked.iLastAccess;

        rLastChecked = rLastChecked.rNext;

        // algorithm: check the next 5 rows and take the worst
        for (int i = 0; i < 5; i++) {
            int w = rLastChecked.iLastAccess;

            if (w < worst) {
                candidate = rLastChecked;
                worst     = w;
            }

            rLastChecked = rLastChecked.rNext;
        }

        return candidate;
    }

    /**
     * Scales down all iLastAccess values to avoid negative numbers.
     *
     */
    private void resetAccessCount() throws SQLException {

        if (iCurrentAccess < Integer.MAX_VALUE / 2) {
            return;
        }

        iCurrentAccess >>= 8;

        int i = iCacheSize;

        while (i-- > 0) {
            rFirst.iLastAccess >>= 8;
            rFirst             = rFirst.rNext;
        }
    }

    /**
     * Writes out all modified cached Rows.
     *
     */
    protected void saveAll() throws SQLException {

        if (rFirst == null) {
            return;
        }

        CachedRow r = rFirst;

        while (true) {
            int       count = 0;
            CachedRow begin = r;

            do {
                if (Trace.STOP) {
                    Trace.stop();
                }

                if (r.hasChanged()) {
                    rWriter[count++] = r;
                }

                r = r.rNext;
            } while (r != begin && count < writerLength);    // HJB-2001-06-21

            if (count == 0) {
                return;
            }

            saveSorted(count);

            for (int i = 0; i < count; i++) {
                rWriter[i] = null;
            }
        }
    }

    /**
     * Writes out the specified Row.
     */
    protected void saveRow(CachedRow r) throws IOException, SQLException {

        rFile.seek(r.iPos);
        r.write(rowOut);
        rFile.write(rowOut.getOutputStream().getBuffer(), 0,
                    rowOut.getOutputStream().size());
        rowOut.reset();
    }

    /**
     * Writes out the first <code>count</code> rWriter Rows in iPos
     * sorted order.
     */
    private void saveSorted(int count) throws SQLException {

        sort(rWriter, 0, count - 1);

        try {
            for (int i = 0; i < count; i++) {
                saveRow(rWriter[i]);
            }
        } catch (Exception e) {
            Trace.throwerror(Trace.FILE_IO_ERROR, "saveSorted " + e);
        }
    }

    /**
     * FastQSorts the [l,r] partition of the specfied array of Rows, based on
     * the contained Row objects' iPos (file offset) values.
     *
     */
    private static final void sort(CachedRow w[], int l,
                                   int r) throws SQLException {

        int i;
        int j;
        int p;

        while (r - l > 10) {
            i = (r + l) >> 1;

            if (w[l].iPos > w[r].iPos) {
                swap(w, l, r);
            }

            if (w[i].iPos < w[l].iPos) {
                swap(w, l, i);
            } else if (w[i].iPos > w[r].iPos) {
                swap(w, i, r);
            }

            j = r - 1;

            swap(w, i, j);

            p = w[j].iPos;
            i = l;

            while (true) {
                if (Trace.STOP) {
                    Trace.stop();
                }

                while (w[++i].iPos < p) {
                    ;
                }

                while (w[--j].iPos > p) {
                    ;
                }

                if (i >= j) {
                    break;
                }

                swap(w, i, j);
            }

            swap(w, i, r - 1);
            sort(w, l, i - 1);

            l = i + 1;
        }

        for (i = l + 1; i <= r; i++) {
            if (Trace.STOP) {
                Trace.stop();
            }

            CachedRow t = w[i];

            for (j = i - 1; j >= l && w[j].iPos > t.iPos; j--) {
                w[j + 1] = w[j];
            }

            w[j + 1] = t;
        }
    }

    /**
     * Swaps the a'th and b'th elements of the specified Row array.
     */
    private static void swap(CachedRow w[], int a, int b) {

        CachedRow t = w[a];

        w[a] = w[b];
        w[b] = t;
    }

    /**
     * Getter for iFreePos member
     */
    int getFreePos() {
        return iFreePos;
    }
}
