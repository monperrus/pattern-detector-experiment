/*

   Derby - Class org.apache.derby.impl.store.access.btree.BTreeMaxScan

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.impl.store.access.btree;

import org.apache.derby.iapi.reference.SQLState;

import org.apache.derby.iapi.services.sanity.SanityManager;

import org.apache.derby.iapi.error.StandardException; 

import org.apache.derby.iapi.store.access.RowUtil;
import org.apache.derby.iapi.store.access.Qualifier;
import org.apache.derby.iapi.store.access.ScanController;

import org.apache.derby.iapi.store.raw.FetchDescriptor;
import org.apache.derby.iapi.store.raw.Page;
import org.apache.derby.iapi.store.raw.RecordHandle;

import org.apache.derby.iapi.types.DataValueDescriptor;

import org.apache.derby.iapi.types.RowLocation;

import org.apache.derby.iapi.store.access.BackingStoreHashtable;


/**

  A b-tree scan controller corresponds to an instance of an open b-tree scan.
  <P>
  <B>Concurrency Notes</B>
  <P>
  The concurrency rules are derived from OpenBTree.
  <P>
  @see OpenBTree

**/

/**

A BTreeScan implementation that provides the 90% solution to the max on
btree problem.  If the row is the last row in the btree it works very
efficiently.  This implementation will be removed once backward scan is
fully functional.

The current implementation only exports to the user the ability to call
fetchMax() and get back one row, none of the generic scan ablities are
exported.  

To return the maximum row this implementation does the following:
1) calls positionAtStartPosition() which returns with the a latch on the
   rightmost leaf page and a lock on the rightmost leaf row on that page.
   It will loop until it can get the lock without waiting while holding
   the latch.  At this point the slot position is just right of the
   locked row.
2) in fetchMax() it loops backward on the last leaf page, locking rows
   as it does so, until it either finds the first non-deleted and locks
   that row without having to wait and thus did not give up the latch on the 
   page.  If successful it returns that row.
3) If it is not successful in this last page search it faults over to 
   the original implementation of max scan, which is simply a search from 
   left to right at the leaf level for the last row in the table.


**/

public class BTreeMaxScan extends BTreeScan
{

    /**************************************************************************
     * Private methods of This class:
     **************************************************************************
     */

    /**
     * Fetch the maximum non-deleted row from the table.
     *
     * Scan from left to right at the leaf level searching for the 
     * rightmost non deleted row in the index.
     *
	 * @exception  StandardException  Standard exception policy.
     **/
    private boolean fetchMaxRowFromBeginning(
    DataValueDescriptor[]   fetch_row)
        throws StandardException
	{
        int                 ret_row_count     = 0;
        RecordHandle        max_rh            = null;

        // we need to scan until we hit the end of the table or until we
        // run into a null.  Use this template to probe the "next" row so
        // that if we need to finish, fetch_row will have the right value.
        DataValueDescriptor[] check_row_template = new DataValueDescriptor[1];
        check_row_template[0] = fetch_row[0].getClone();
        FetchDescriptor check_row_desc = RowUtil.getFetchDescriptorConstant(1);

        // reopen the scan for reading from the beginning of the table.
        reopenScan(
            (DataValueDescriptor[]) null,
            ScanController.NA,
            (Qualifier[][]) null,
            (DataValueDescriptor[]) null,
            ScanController.NA);

        BTreeRowPosition pos = scan_position;

        positionAtStartForForwardScan(pos);

        // At this point:
        // current_page is latched.  current_slot is the slot on current_page
        // just before the "next" record this routine should process.

        // loop through successive leaf pages and successive slots on those
        // leaf pages.  Stop when either the last leaf is reached. At any
        // time in the scan fetch_row will contain "last" non-deleted row
        // seen.

        boolean nulls_not_reached = true;
        leaf_loop:
		while ((pos.current_leaf != null) && nulls_not_reached)
		{
            slot_loop:
			while ((pos.current_slot + 1) < pos.current_leaf.page.recordCount())
			{
                // unlock the previous row if doing read.
                if (pos.current_rh != null)
                {
                    this.getLockingPolicy().unlockScanRecordAfterRead(
                        pos, init_forUpdate);

                    // current_rh is used to track which row we need to unlock,
                    // at this point no row needs to be unlocked.
                    pos.current_rh = null;
                }

                // move scan current position forward.
                pos.current_slot++;
                this.stat_numrows_visited++;

                // get current record handle for positioning but don't read
                // data until we verify it is not deleted.  rh is needed
                // for repositioning if we lose the latch.
                RecordHandle rh = 
                    pos.current_leaf.page.fetchFromSlot(
                        (RecordHandle) null,
                        pos.current_slot, 
                        check_row_template,
                        null,
                        true);

                // lock the row.
                boolean latch_released =
                    !this.getLockingPolicy().lockScanRow(
                        this, this.getConglomerate(), pos,
                        init_lock_fetch_desc,
                        pos.current_lock_template,
                        pos.current_lock_row_loc,
                        false, init_forUpdate, lock_operation);

                // special test to see if latch release code works
                if (SanityManager.DEBUG)
                {
                    latch_released = 
                        test_errors(
                            this,
                            "BTreeMaxScan_fetchNextGroup", pos,
                            this.getLockingPolicy(),
                            pos.current_leaf, latch_released);
                }

                // At this point we have successfully locked this record, so
                // remember the record handle so that it can be unlocked if
                // necessary.  If the above lock deadlocks, we will not try
                // to unlock a lock we never got in close(), because current_rh
                // is null until after the lock is granted.
                pos.current_rh = rh;

                if (latch_released)
                {
                    // lost latch on page in order to wait for row lock.
                    // Call reposition() which will use the saved record
                    // handle to reposition to the same spot on the page.
                    // If the row is no longer on the page, reposition()
                    // will take care of searching the tree and position
                    // on the correct page.
                    if (!reposition(pos, false))
                    {
                        // Could not position on the exact same row that was
                        // saved, which means that it has been purged.
                        // Reposition on the row immediately to the left of
                        // the purged row instead.
                        if (!reposition(pos, true))
                        {
                            if (SanityManager.DEBUG)
                            {
                                SanityManager.THROWASSERT(
                                        "Cannot fail with 2nd param true");
                            }
                            // reposition will set pos.current_leaf to null if
                            // it returns false, so if this ever does fail in
                            // delivered code, expect a NullPointerException at
                            // the top of this loop when we call recordCount().
                        }

                        // Now we're positioned to the left of our saved
                        // position. Go to the top of the loop so that we move
                        // the scan to the next row and release the lock on
                        // the purged row.
                        continue slot_loop;
                    }
                }


                if (pos.current_leaf.page.isDeletedAtSlot(pos.current_slot))
                {
                    this.stat_numdeleted_rows_visited++;

                    if (check_row_template[0].isNull())
                    {
                        // nulls sort at high end and are not to be returned
                        // by max scan, so search is over, return whatever is
                        // in fetch_row.
                        nulls_not_reached = false;
                        break;
                    }
                }
                else if (check_row_template[0].isNull())
                {
                    nulls_not_reached = false;
                    break;
                }
                else 
                {

                    pos.current_leaf.page.fetchFromSlot(
                        pos.current_rh,
                        pos.current_slot, fetch_row, init_fetchDesc,
                        true);

                    stat_numrows_qualified++;
                    max_rh = pos.current_rh;
                }
			}

            // Move position of the scan to slot 0 of the next page.  If there
            // is no next page current_page will be null.
            positionAtNextPage(pos);

            this.stat_numpages_visited++;
		}


        // Reached last leaf of tree.
        positionAtDoneScan(pos);

        // we need to decrement when we stop scan at the end of the table.
        this.stat_numpages_visited--;

        return(max_rh != null);
	}

    /**************************************************************************
     * Protected implementation of abstract methods of BTreeScan class:
     **************************************************************************
     */

    /**
     * disallow fetchRows on this scan type, caller should only be able
     * to call fetchMax().
     * <p>
	 * @exception  StandardException  Standard exception policy.
     **/
    protected int fetchRows(
    BTreeRowPosition        pos,
    DataValueDescriptor[][] row_array,
    RowLocation[]           rowloc_array,
    BackingStoreHashtable   hash_table,
    long                    max_rowcnt,
    int[]                   key_column_numbers)
        throws StandardException
    {
        throw StandardException.newException(
                SQLState.BTREE_UNIMPLEMENTED_FEATURE);
    }


    /**
     * Position scan at "start" position of the MAX scan.
     * <p>
     * Positions the scan to the slot just after the last record on the
     * rightmost leaf of the index.  Returns the rightmost leaf page latched,  
     * the rightmost row on the page locked and 
     * sets "current_slot" to the slot number just right of the last row
     * on the page.
     * <p>
     *
	 * @exception  StandardException  Standard exception policy.
     **/
    protected void positionAtStartPosition(
    BTreeRowPosition    pos)
        throws StandardException
	{
		boolean         exact;

        // This routine should only be called from first next() call //
        if (SanityManager.DEBUG)
        {
            SanityManager.ASSERT(this.scan_state         == SCAN_INIT);
            SanityManager.ASSERT(pos.current_rh          == null);
            SanityManager.ASSERT(pos.current_positionKey == null);
        }

        // Loop until you can lock the last row, on the rightmost leaf page
        // of the tree, while holding the page latched, without waiting.
        //
        // If you have to wait, drop the latch, and wait for the lock.
        // This makes it likely that the next search you will loop just
        // once, find the same lock satisfies the search and since you already
        // have the lock it will be granted.
        while (true)
        {
            // Find the starting page and row slot, must start at root and
            // search for rightmost leaf.
            ControlRow root = ControlRow.get(this, BTree.ROOTPAGEID); 

            // include search of tree in page visited stats.
            stat_numpages_visited += root.getLevel() + 1;

            if (init_startKeyValue == null)
            {
                // No start given, position at last slot + 1 of rightmost leaf 
                pos.current_leaf = (LeafControlRow) root.searchRight(this);

                pos.current_slot = pos.current_leaf.page.recordCount();
                exact     = false;
            }
            else
            {
                // only max needed, no start position supported.
                throw StandardException.newException(
                        SQLState.BTREE_UNIMPLEMENTED_FEATURE);
            }

            // lock the last row on the rightmost leaf of the table, as this
            // is a max scan no previous key locking necessary.  Previous key
            // locking is used to protect a range of keys, but for max there
            // is only a single row returned.

            pos.current_slot--;
            boolean latch_released = 
                !this.getLockingPolicy().lockScanRow(
                    this, this.getConglomerate(), pos,
                    init_lock_fetch_desc,
                    pos.current_lock_template,
                    pos.current_lock_row_loc,
                    false, init_forUpdate, lock_operation);
            pos.current_slot++;

            // special test to see if latch release code works
            if (SanityManager.DEBUG)
            {
                latch_released = 
                    test_errors(
                        this,
                        "BTreeMaxScan_positionAtStartPosition", pos,
                        this.getLockingPolicy(), pos.current_leaf, latch_released);
            }

            if (latch_released)
            {
                // lost latch on pos.current_leaf, search the tree again.
                pos.current_leaf = null;
                continue;
            }
            else
            {
                // success! got all the locks, while holding the latch.
                break;
            }
        }

        this.scan_state          = SCAN_INPROGRESS;

        if (SanityManager.DEBUG)
            SanityManager.ASSERT(pos.current_leaf != null);
	}

    /**************************************************************************
     * Public Methods of This class:
     **************************************************************************
     */

    /**
     * Fetch the maximum row in the table.
     *
     * Call positionAtStartPosition() to quickly position on rightmost row
     * of rightmost leaf of tree.
     *
     * Search last page for last non deleted row, and if one is found return
     * it as max.
     *
     * If no row found on last page, or could not find row withou losing latch
     * then call fetchMaxRowFromBeginning() to search from left to right
     * for maximum value in index.
     *
	 * @exception  StandardException  Standard exception policy.
     **/
    public boolean fetchMax(
    DataValueDescriptor[]   fetch_row)
        throws StandardException
    {
        BTreeRowPosition    pos           = scan_position;
        int                 ret_row_count = 0;

        if (SanityManager.DEBUG)
        {
            SanityManager.ASSERT(this.container != null,
                "BTreeMaxScan.fetchMax() called on a closed scan.");
        }


        if (this.scan_state == BTreeScan.SCAN_INPROGRESS)
        {
            // Get current page of scan, with latch

            // RESOLVE (mikem) - I don't think this code can be called.
            
            // reposition the scan at the row just before the next one to 
            // return.
            // This routine handles the mess of repositioning if the row or 
            // the page has disappeared. This can happen if a lock was not 
            // held on the row while not holding the latch (can happen if
            // this scan is read uncommitted).
            if (!reposition(scan_position, true))
            {
                if (SanityManager.DEBUG)
                {
                    SanityManager.THROWASSERT(
                        "can not fail with 2nd param true.");
                }
            }

        }
        else if (this.scan_state == SCAN_INIT)
        {
            // 1st positioning of scan (delayed from openScan).
            positionAtStartPosition(scan_position);
        }
        else
        {
            if (SanityManager.DEBUG)
                SanityManager.ASSERT(this.scan_state == SCAN_DONE);

            return(false);
        }


        // At this point:
        // current_page is latched.  current_slot is the slot on current_page
        // just "right" of the "next" record this routine should process.
        // In this case teh "next" record is the last row on the rightmost
        // leaf page.


        boolean max_found = false;

        // Code is positioned on the rightmost leaf of the index, the rightmost
        // non-deleted row on this page is the maximum row to return.

        if ((pos.current_slot - 1) > 0)
        {
            // move scan backward in search of last non-deleted row on page.
            pos.current_slot--;

            while (pos.current_slot > 0)
            {
                this.stat_numrows_visited++;

                // get current record handle for positioning but don't read
                // data until we verify it is not deleted.  rh is needed
                // for repositioning if we lose the latch.
                RecordHandle rh = 
                    pos.current_leaf.page.fetchFromSlot(
                        (RecordHandle) null,
                        pos.current_slot, fetch_row, init_fetchDesc,
                        true);

                // lock current row in max scan, no previous key lock necessary.
                boolean latch_released =
                    !this.getLockingPolicy().lockScanRow(
                        this, this.getConglomerate(), pos, 
                        init_lock_fetch_desc,
                        pos.current_lock_template,
                        pos.current_lock_row_loc,
                        false, init_forUpdate, lock_operation);

                // At this point we have successfully locked this record, so
                // remember the record handle so that it can be unlocked if
                // necessary.  If the above lock deadlocks, we will not try
                // to unlock a lock we never got in close(), because current_rh
                // is null until after the lock is granted.
                pos.current_rh = rh;


                if (latch_released)
                {
                    // had to wait on lock while lost latch, now last page of
                    // index may have changed, give up on "easy/fast" max scan.
                    pos.current_leaf = null;
                    break;
                }

                if (pos.current_leaf.page.isDeletedAtSlot(pos.current_slot))
                {
                    this.stat_numdeleted_rows_visited++;
                    pos.current_rh_qualified = false;
                }
                else if (fetch_row[0].isNull())
                {
                    pos.current_rh_qualified = false;
                }
                else
                {
                    pos.current_rh_qualified = true;
                }

                if (pos.current_rh_qualified)
                {
                    // return the qualifying max row.
                    ret_row_count++;
                    stat_numrows_qualified++;

                    // current_slot is invalid after releasing latch
                    pos.current_slot = Page.INVALID_SLOT_NUMBER;

                    max_found = true;
                    break;
                }
                else
                {
                    pos.current_slot--;
                }
            }
		}

        if (pos.current_leaf != null)
        {
            // done with "last" page in table.
            pos.current_leaf.release();
            pos.current_leaf = null;
        }

        // Clean up the scan based on searching through rightmost leaf of btree
        positionAtDoneScan(scan_position);

        if (!max_found)
        {
            // max row in table was not last row in table
            max_found = fetchMaxRowFromBeginning(fetch_row);
        }

        return(max_found);
	}
}
