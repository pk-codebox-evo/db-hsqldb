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

import org.hsqldb.lib.HsqlArrayList;
import org.hsqldb.lib.HashSet;
import org.hsqldb.HsqlNameManager.HsqlName;

// fredt@users 20020520 - patch 1.7.0 - ALTER TABLE support
// tony_lai@users 20020820 - patch 595172 - drop constraint fix

/**
 * The methods in this class perform alterations to the structure of an
 * existing table which may result in a new Table object
 *
 * @version 1.7.2
 * @since 1.7.0
 */
class TableWorks {

    private Table table;

    TableWorks(Table table) {
        this.table = table;
    }

    Table getTable() {
        return table;
    }

// boucherb@users 20030402 - patch 1.7.2 added for reuse of TableWorks object
// under command interpreter support
    void setTable(Table table) {
        this.table = table;
    }

// fredt@users 20020225 - patch 1.7.0 - require existing index for foreign key
// fredt@users 20030309 - patch 1.7.2 - more rigorous rules

    /**
     *  Creates a foreign key according to current sql.strict_fk or
     *  sql.strong_fk settings. Foreign keys are enforced via indexes on both
     *  the referencing (child) and referenced (parent) tables.
     *  <p>
     *  In versions 1.7.0 and 1.7.1 some non-standard features were supported
     *  for compatibility with older databases as follows:
     *  <p>
     *  If sql.strict_fk is set (default for new databases) a pre-existing
     *  primary key or unique index is required on the referenced columns
     *  of the referenced table (1.7.1).
     *  <p>
     *  When there is no primary key or unique index, a new index is created
     *  automatically. If sql.strong_fk is set in the abasence of
     *  sql.strict_fk, this automatic index will be a unique index. Otherwise
     *  (for compatibility with existing data created with HSQLDB 1.61 or
     *  earlier) it will be an ordinary index (1.7.1).
     *  <p>
     *  In version 1.7.2, the semantics of sql.strict_fk are enforced
     *  regardless of the database properties settings (which are now obsolete).
     *
     *  The non-unique index on the referencing table is created unless
     *  PK or unique constraint index on the columns exist. This is becuase the
     *  index must always be created before the foreign key DDL is processed.
     *
     *  Foriegn keys on temp tables can reference other temp tables with the
     *  same rules above. Foreign keys on permanent tables cannot reference
     *  temp tables.
     *
     *  Duplicate foreign keys are now disallowed.
     *
     *  The introduction of ALTER TABLE for adding foreign keys opened some
     *  loopholes that were closed in version 1.7.2 :
     *
     *  -- The unique index on the referenced table must also belong to a
     *  constraint (PK or UNIQUE) when the foreign key is referencing the
     *  same table. Otherwise after a SHUTDOWN and restart the index will not
     *  exist at the time of creation of the foreign key.
     *
     *  -- The non-unique index on the referencing table is always created
     *  unless there is an index belonging to a PK or UNIQUE constraint that
     *  can be used instead.
     *
     *
     *  (fred@users)
     *
     * @param  fkcol
     * @param  expcol
     * @param  fkname         foreign key name
     * @param  expTable
     * @param  deleteAction
     * @param  updateAction
     * @throws HsqlException
     */
    void createForeignKey(int fkcol[], int expcol[], HsqlName fkname,
                          Table expTable, int deleteAction,
                          int updateAction) throws HsqlException {

        // name check
        if (table.getConstraint(fkname.name) != null) {
            throw Trace.error(Trace.CONSTRAINT_ALREADY_EXISTS);
        }

        // existing FK check
        if (table.getConstraintForColumns(expTable, expcol, fkcol) != null) {
            throw Trace.error(Trace.CONSTRAINT_ALREADY_EXISTS);
        }

        if (expTable.isTemp() != table.isTemp()) {
            throw Trace.error(Trace.FOREIGN_KEY_NOT_ALLOWED,
                              "both tables must be permanent or temporary");
        }

        int interval = table.database.getTableIndex(table)
                       - table.database.getTableIndex(expTable);
        Index exportindex = (interval == 0)
                            ? expTable.getConstraintIndexForColumns(expcol,
                                true)
                            : expTable.getIndexForColumns(expcol, true);

        if (exportindex == null) {
            throw Trace.error(Trace.INDEX_NOT_FOUND,
                              "needs unique index on referenced columns of "
                              + expTable.getName().statementName);
        }

        Index fkindex = table.getConstraintIndexForColumns(fkcol, false);

// fredt - this changes the index creation logic in ALPHA_Q - uncomment for old behaviour
//        if (fkindex == null)
        {
            HsqlName iname = table.database.nameManager.newAutoName("IDX");

            fkindex = createIndex(fkcol, iname, false);
        }

        HsqlName pkname = table.database.nameManager.newAutoName("REF",
            fkname.name);
        Constraint c = new Constraint(pkname, fkname, expTable, table,
                                      expcol, fkcol, exportindex, fkindex,
                                      deleteAction, updateAction);

        table.addConstraint(c);
        expTable.addConstraint(new Constraint(pkname, c));
    }

// fredt@users 20020315 - patch 1.7.0 - create index bug
// this method would break existing foreign keys as the table order in the DB
// was changed. Instead, we now link in place of the old table

    /**
     *  Because of the way indexes and column data are held in memory and
     *  on disk, it is necessary to recreate the table when an index is added
     *  to a non-empty table cached table.<p>
     *
     *  With empty tables, Index objects are simply added<p>
     *
     *  With MEOMRY and TEXT tables, a new index is built up and nodes for
     *  earch row are interlinked (fredt@users)
     *
     * @param  col
     * @param  name
     * @param  unique
     * @return  new index
     * @throws  HsqlException normally for lack of resources
     */
    Index createIndex(int col[], HsqlName name,
                      boolean unique) throws HsqlException {

        Index newindex;

        if (table.isEmpty() || table.isIndexingMutable()) {
            newindex = table.createIndex(col, name, unique);
        } else {
            Table tn = table.moveDefinition(null, null,
                                            table.getColumnCount(), 0);

            newindex = tn.createIndexStructure(col, name, unique, false);

            tn.moveData(table, table.getColumnCount(), 0);
            tn.updateConstraints(table, table.getColumnCount(), 0);

            int index = table.database.getTableIndex(table);

            table.database.getTables().set(index, tn);

            table = tn;
        }

        table.database.indexNameList.addName(newindex.getName().name,
                                             table.getName());

        return newindex;
    }

// fredt@users 20020225 - avoid duplicate constraints

    /**
     *  A unique constraint relies on a unique indexe on the table. It can
     *  cover a single column or multiple columns.
     *  <p>
     *  All unique constraint names are generated by Database.java as unique
     *  within the database. Duplicate constraints (more than one unique
     *  constriant on the same set of columns are still allowed but the
     *  names will be different. (fredt@users)
     *
     * @param  col
     * @param  name
     * @throws  HsqlException
     */
    void createUniqueConstraint(int[] col,
                                HsqlName name) throws HsqlException {

        HsqlArrayList constraints = table.getConstraints();

        for (int i = 0, size = constraints.size(); i < size; i++) {
            Constraint c = (Constraint) constraints.get(i);

            if (c.isEquivalent(col, Constraint.UNIQUE)
                    || c.getName().name.equals(name.name)) {
                throw Trace.error(Trace.CONSTRAINT_ALREADY_EXISTS);
            }
        }

        // create an autonamed index
        HsqlName   indexname = table.database.nameManager.newAutoName("IDX");
        Index      index         = createIndex(col, indexname, true);
        Constraint newconstraint = new Constraint(name, table, index);

        table.addConstraint(newconstraint);
        resetAutoIndex(index);
    }

// fredt@users 20030730 - patch 1.7.2 - obscure autoindex bug

    /**
     * When a unique constraint is added and its index can be used for an
     * existing foreign key autogenerated reference index, the autogenerated
     * one is replaced with the new index and removed from the table by this
     * method.
     *
     * This is essential to avoid errors with cached tables when the database
     * restarts. At this instance, the index for unique constraint will be
     * used for the foreign key, and no autogenerated index will be created.
     * As there will be a redundant autogen index in the *.data file, the
     * calculated number of indexed will be one less than those actually in
     * the .data file, resulting in read errors. (fredt@users)
     */
    void resetAutoIndex(Index index) throws HsqlException {

        Constraint c =
            table.getAutoIndexConstraintForColumns(index.getColumns());

        if (c == null) {
            return;
        }

        Index oldindex = c.getRefIndex();

        c.replaceIndex(oldindex, index);
        dropIndex(oldindex.getName().name);
    }

// fredt@users 20020315 - patch 1.7.0 - drop index bug

    /**
     *  Because of the way indexes and column data are held in memory and
     *  on disk, it is necessary to recreate the table when an index is added
     *  to a non-empty table.<p>
     *  Originally, this method would break existing foreign keys as the
     *  table order in the DB was changed. The new table is now linked
     *  in place of the old table (fredt@users)
     *
     * @param  indexname
     * @throws  HsqlException
     */
    void dropIndex(String indexname) throws HsqlException {

        if (table.isIndexingMutable()) {
            table.dropIndex(indexname);
        } else {
            Table tn = table.moveDefinition(indexname, null,
                                            table.getColumnCount(), 0);

            tn.moveData(table, table.getColumnCount(), 0);
            tn.updateConstraints(table, table.getColumnCount(), 0);

            int i = table.database.getTableIndex(table);

            table.database.getTables().set(i, tn);

            table = tn;
        }

        table.database.indexNameList.removeName(indexname);
    }

    /**
     *
     * @param  column
     * @param  colindex
     * @param  adjust +1 or -1
     * @throws  HsqlException
     */
    void addOrDropColumn(Column column, int colindex,
                         int adjust) throws HsqlException {

        if (table.isText()) {
            throw Trace.error(Trace.OPERATION_NOT_SUPPORTED);
        }

        Table tn = table.moveDefinition(null, column, colindex, adjust);

        tn.moveData(table, colindex, adjust);
        tn.updateConstraints(table, colindex, adjust);

        int i = table.database.getTableIndex(table);

        table.database.getTables().set(i, tn);

        table = tn;
    }

    /**
     *  Method declaration
     *
     */
    void dropConstraint(String name) throws HsqlException {

        int        j    = table.getConstraintIndex(name);
        Constraint c    = table.getConstraint(name);
        HashSet    cset = new HashSet();

        if (c == null) {
            throw Trace.error(Trace.CONSTRAINT_NOT_FOUND,
                              name + " in table: " + table.getName().name);
        }

        cset.add(c);

        if (c.getType() == Constraint.MAIN) {
            throw Trace.error(Trace.DROP_SYSTEM_CONSTRAINT);
        }

        if (c.getType() == Constraint.FOREIGN_KEY) {
            Table mainTable = c.getMain();
            Constraint mainConstraint =
                mainTable.getConstraint(c.getPkName());

            cset.add(mainConstraint);

            int   k         = mainTable.getConstraintIndex(c.getPkName());
            Index mainIndex = mainConstraint.getMainIndex();

            // never drop user defined indexes
            // fredt - todo - use of auto main indexes for FK's will be
            // deprecated so that there is no need to drop an index on the
            // main (pk) table
            if (mainIndex.getName().isReservedIndexName()) {
                boolean candrop = false;

                try {

                    // drop unless the index is used by other constraints
                    mainTable.checkDropIndex(mainIndex.getName().name, cset);

                    candrop = true;

                    TableWorks tw = new TableWorks(mainTable);

                    tw.dropIndex(mainIndex.getName().name);

                    // update this.table if self referencing FK
                    if (mainTable == table) {
                        table = tw.getTable();
                    }
                } catch (HsqlException e) {
                    if (candrop) {
                        throw e;
                    }
                }
            }

            // drop the reference index if automatic and unused elsewhere
            Index refIndex = c.getRefIndex();

            if (refIndex.getName().isReservedIndexName()) {
                try {

                    // drop unless the index is used by other constraints
                    table.checkDropIndex(refIndex.getName().name, cset);
                    dropIndex(refIndex.getName().name);
                } catch (HsqlException e) {}
            }

            mainTable.vConstraint.remove(k);
            table.vConstraint.remove(j);
        } else if (c.getType() == Constraint.UNIQUE) {

            // throw if the index for unique constraint is shared
            table.checkDropIndex(c.getMainIndex().getName().name, cset);

            // all is well if dropIndex throws for lack of resources
            dropIndex(c.getMainIndex().getName().name);
            table.vConstraint.remove(j);
        }
    }
}
