/*

   Derby - Class org.apache.derby.impl.sql.compile.OptimizerImpl

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

package org.apache.derby.impl.sql.compile;

import org.apache.derby.iapi.services.sanity.SanityManager;

import org.apache.derby.iapi.error.StandardException;

import org.apache.derby.iapi.sql.compile.JoinStrategy;
import org.apache.derby.iapi.sql.compile.Optimizable;
import org.apache.derby.iapi.sql.compile.OptimizableList;
import org.apache.derby.iapi.sql.compile.OptimizablePredicate;
import org.apache.derby.iapi.sql.compile.OptimizablePredicateList;
import org.apache.derby.iapi.sql.compile.Optimizer;
import org.apache.derby.iapi.sql.compile.CostEstimate;
import org.apache.derby.iapi.sql.compile.RequiredRowOrdering;
import org.apache.derby.iapi.sql.compile.RowOrdering;
import org.apache.derby.iapi.sql.compile.AccessPath;

import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;

import org.apache.derby.iapi.sql.dictionary.ConglomerateDescriptor;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;

import org.apache.derby.catalog.IndexDescriptor;
import org.apache.derby.iapi.reference.SQLState;

import org.apache.derby.iapi.util.JBitSet;
import org.apache.derby.iapi.util.StringUtil;

import java.util.Properties;
import java.util.HashMap;

/**
 * This will be the Level 1 Optimizer.
 * RESOLVE - it's a level 0 optimizer right now.
 * Current State:
 *	o  No costing services
 *	o  We can only cost a derived table with a join once.
 *  
 * Optimizer uses OptimizableList to keep track of the best join order as it
 * builds it.  For each available slot in the join order, we cost all of the
 * Optimizables from that slot til the end of the OptimizableList.  Later,
 * we will choose the best Optimizable for that slot and reorder the list
 * accordingly.
 * In order to do this, we probably need to move the temporary pushing and
 * pulling of join clauses into Optimizer, since the logic will be different
 * for other implementations.  (Of course, we're not pushing and pulling join
 * clauses between permutations yet.)
 */

public class OptimizerImpl implements Optimizer 
{

	DataDictionary			 dDictionary;
	/* The number of tables in the query as a whole.  (Size of bit maps.) */
	int						 numTablesInQuery;
	/* The number of optimizables in the list to optimize */
	int						 numOptimizables;

	/* Bit map of tables that have already been assigned to slots.
	 * Useful for pushing join clauses as slots are assigned.
	 */
	protected JBitSet		 assignedTableMap;
	protected OptimizableList optimizableList;
	OptimizablePredicateList predicateList;
	JBitSet					 nonCorrelatedTableMap;

	protected int[]			 proposedJoinOrder;
	protected int[]					 bestJoinOrder;
	protected int			 joinPosition;
	boolean					 desiredJoinOrderFound;

	/* This implements a state machine to jump start to a appearingly good join
	 * order, when the number of tables is high, and the optimization could take
	 * a long time.  A good start can prune better, and timeout sooner.  Otherwise,
	 * it may take forever to exhaust or timeout (see beetle 5870).  Basically after
	 * we jump, we walk the high part, then fall when we reach the peak, finally we
	 * walk the low part til where we jumped to.
	 */
	private static final int NO_JUMP = 0;
	private static final int READY_TO_JUMP = 1;
	private static final int JUMPING = 2;
	private static final int WALK_HIGH = 3;
	private static final int WALK_LOW = 4;
	private int				 permuteState;
	private int[]			 firstLookOrder;

	private boolean			 ruleBasedOptimization;

	private CostEstimateImpl outermostCostEstimate;
	protected CostEstimateImpl currentCost;
	protected CostEstimateImpl currentSortAvoidanceCost;
	protected CostEstimateImpl bestCost;

	protected long			 timeOptimizationStarted;
	protected long			 currentTime;
	protected boolean		 timeExceeded;
	private boolean			 noTimeout;
	private boolean 		 useStatistics;
	private int				 tableLockThreshold;

	private JoinStrategy[]	joinStrategies;

	protected RequiredRowOrdering	requiredRowOrdering;

	private boolean			 foundABestPlan;

	protected CostEstimate sortCost;

	private RowOrdering currentRowOrdering = new RowOrderingImpl();
	private RowOrdering bestRowOrdering = new RowOrderingImpl();

	private boolean	conglomerate_OneRowResultSet;

	// optimizer trace
	protected boolean optimizerTrace;
	protected boolean optimizerTraceHtml;

	// max memory use per table
	protected int maxMemoryPerTable;

	// Whether or not we need to reload the best plan for an Optimizable
	// when we "pull" it.  If the latest complete join order was the
	// best one so far, then the Optimizable will already have the correct
	// best plan loaded so we don't need to do the extra work.  But if
	// the most recent join order was _not_ the best, then this flag tells
	// us that we need to reload the best plan when pulling.
	private boolean reloadBestPlan;

	// Set of optimizer->bestJoinOrder mappings used to keep track of which
	// of this OptimizerImpl's "bestJoinOrder"s was the best with respect to a
	// a specific outer query; the outer query is represented by an instance
	// of Optimizer.  Each outer query could potentially have a different
	// idea of what this OptimizerImpl's "best join order" is, so we have
	// to keep track of them all.
	private HashMap savedJoinOrders;

	// Value used to figure out when/if we've timed out for this
	// Optimizable.
	protected double timeLimit;

	// Cost estimate for the final "best join order" that we chose--i.e.
	// the one that's actually going to be generated.
	CostEstimate finalCostEstimate;

	/* Status variables used for "jumping" to previous best join
	 * order when possible.  In particular, this helps when this
	 * optimizer corresponds to a subquery and we are trying to
	 * find out what the best join order is if we do a hash join
	 * with the subquery instead of a nested loop join.  In that
	 * case the previous best join order will have the best join
	 * order for a nested loop, so we want to start there when
	 * considering hash join because odds are that same join order
	 * will give us the best cost for hash join, as well.  We
	 * only try this, though, if neither the previous round of
	 * optimization nor this round relies on predicates that have
	 * been pushed down from above--because that's the scenario
	 * for which the best join order is likely to be same for
	 * consecutive rounds.
	 */
	private boolean usingPredsPushedFromAbove;
	private boolean bestJoinOrderUsedPredsFromAbove;

	protected  OptimizerImpl(OptimizableList optimizableList, 
				  OptimizablePredicateList predicateList,
				  DataDictionary dDictionary,
				  boolean ruleBasedOptimization,
				  boolean noTimeout,
				  boolean useStatistics,
				  int maxMemoryPerTable,
				  JoinStrategy[] joinStrategies,
				  int tableLockThreshold,
				  RequiredRowOrdering requiredRowOrdering,
				  int numTablesInQuery)
		throws StandardException
	{
		if (SanityManager.DEBUG) {
			SanityManager.ASSERT(optimizableList != null,
							 "optimizableList is not expected to be null");
		}

		outermostCostEstimate =  getNewCostEstimate(0.0d, 1.0d, 1.0d);

		currentCost = getNewCostEstimate(0.0d, 0.0d, 0.0d);

		currentSortAvoidanceCost = getNewCostEstimate(0.0d, 0.0d, 0.0d);

		bestCost = getNewCostEstimate(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);

		// Verify that any Properties lists for user overrides are valid
		optimizableList.verifyProperties(dDictionary);

		this.numTablesInQuery = numTablesInQuery;
		numOptimizables = optimizableList.size();
		proposedJoinOrder = new int[numOptimizables];
		if (numTablesInQuery > 6)
		{
			permuteState = READY_TO_JUMP;
			firstLookOrder = new int[numOptimizables];
		}
		else
			permuteState = NO_JUMP;

		/* Mark all join positions as unused */
		for (int i = 0; i < numOptimizables; i++)
			proposedJoinOrder[i] = -1;

		bestJoinOrder = new int[numOptimizables];
		joinPosition = -1;
		this.optimizableList = optimizableList;
		this.predicateList = predicateList;
		this.dDictionary = dDictionary;
		this.ruleBasedOptimization = ruleBasedOptimization;
		this.noTimeout = noTimeout;
		this.maxMemoryPerTable = maxMemoryPerTable;
		this.joinStrategies = joinStrategies;
		this.tableLockThreshold = tableLockThreshold;
		this.requiredRowOrdering = requiredRowOrdering;
		this.useStatistics = useStatistics;

		/* initialize variables for tracking permutations */
		assignedTableMap = new JBitSet(numTablesInQuery);

		/*
		** Make a map of the non-correlated tables, which are the tables
		** in the list of Optimizables we're optimizing.  An reference
		** to a table that is not defined in the list of Optimizables
		** is presumed to be correlated.
		*/
		nonCorrelatedTableMap = new JBitSet(numTablesInQuery);
		for (int tabCtr = 0; tabCtr < numOptimizables; tabCtr++)
		{
			Optimizable	curTable = optimizableList.getOptimizable(tabCtr);
			nonCorrelatedTableMap.or(curTable.getReferencedTableMap());
		}

		/* Get the time that optimization starts */
		timeOptimizationStarted = System.currentTimeMillis();
		reloadBestPlan = false;
		savedJoinOrders = null;
		timeLimit = Double.MAX_VALUE;

		usingPredsPushedFromAbove = false;
		bestJoinOrderUsedPredsFromAbove = false;
	}

	/**
	 * This method is called before every "round" of optimization, where
	 * we define a "round" to be the period between the last time a call to
	 * getOptimizer() (on either a ResultSetNode or an OptimizerFactory)
	 * returned _this_ OptimizerImpl and the time a call to this OptimizerImpl's
	 * getNextPermutation() method returns FALSE.  Any re-initialization
	 * of state that is required before each round should be done in this
	 * method.
	 */
	public void prepForNextRound()
	{
		// We initialize reloadBestPlan to false so that if we end up
		// pulling an Optimizable before we find a best join order
		// (which can happen if there is no valid join order for this
		// round) we won't inadvertently reload the best plans based
		// on some previous round.
		reloadBestPlan = false;

		/* Since we're preparing for a new round, we have to clear
		 * out the "bestCost" from the previous round to ensure that,
		 * when this round of optimizing is done, bestCost will hold
		 * the best cost estimate found _this_ round, if there was
		 * one.  If there was no best cost found (which can happen if
		 * there is no feasible join order) then bestCost will remain
		 * at Double.MAX_VALUE.  Then when outer queries check the
		 * cost and see that it is so high, they will reject whatever
		 * outer join order they're trying in favor of something that's
		 * actually valid (and therefore cheaper).
		 *
		 * Note that we do _not_ reset the "foundABestPlan" variable nor
		 * the "bestJoinOrder" array.  This is because it's possible that
		 * a "best join order" may not exist for the current round, in
		 * which case this OptimizerImpl must know whether or not it found
		 * a best join order in a previous round (foundABestPlan) and if
		 * so what the corresponding join order was (bestJoinOrder).  This
		 * information is required so that the correct query plan can be
		 * generated after optimization is complete, even if that best
		 * plan was not found in the most recent round.
		 */
		bestCost = getNewCostEstimate(
			Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);

		/* If we have predicates that were pushed down to this OptimizerImpl
		 * from an outer query, then we reset the timeout state to prepare for
		 * the next round of optimization.  Otherwise if we timed out during
		 * a previous round and then we get here for another round, we'll
		 * immediately "timeout" again before optimizing any of the Optimizables
		 * in our list.  This is okay if we don't have any predicates from
		 * outer queries because in that case the plan we find this round
		 * will be the same one we found in the previous round, in which
		 * case there's no point in resetting the timeout state and doing
		 * the work a second time.  But if we have predicates from an outer
		 * query, those predicates could help us find a much better plan
		 * this round than we did in previous rounds--so we reset the timeout
		 * state to ensure that we have a chance to consider plans that
		 * can take advantage of the pushed predicates.
		 */
		usingPredsPushedFromAbove = false;
		if ((predicateList != null) && (predicateList.size() > 0))
		{
			for (int i = predicateList.size() - 1; i >= 0; i--)
			{
				// If the predicate is "scoped", then we know it was pushed
				// here from an outer query.
				if (((Predicate)predicateList.
					getOptPredicate(i)).isScopedForPush())
				{
					usingPredsPushedFromAbove = true;
					break;
				}
			}
		}

		if (usingPredsPushedFromAbove)
		{
			timeOptimizationStarted = System.currentTimeMillis();
			timeExceeded = false;
		}
	}

    public int getMaxMemoryPerTable()
    {
        return maxMemoryPerTable;
    }
    
	/**
	 * @see Optimizer#getNextPermutation
	 *
	 * @exception StandardException		Thrown on error
	 */
	public boolean getNextPermutation()
			throws StandardException
	{
		/* Don't get any permutations if there is nothing to optimize */
		if (numOptimizables < 1)
		{
			if (optimizerTrace)
			{
				trace(NO_TABLES, 0, 0, 0.0, null);
			}

			return false;
		}

		/* Make sure that all Optimizables init their access paths.
		 * (They wait until optimization since the access path
		 * references the optimizer.)
		 */
		optimizableList.initAccessPaths(this);

		/*
		** Experiments show that optimization time only starts to
		** become a problem with seven tables, so only check for
		** too much time if there are more than seven tables.
		** Also, don't check for too much time if user has specified
		** no timeout.
		*/
		if ( ( ! timeExceeded ) &&
			 (numTablesInQuery > 6)  &&
			 ( ! noTimeout) )
		{
			/*
			** Stop optimizing if the time spent optimizing is greater than
			** the current best cost.
			*/
			currentTime = System.currentTimeMillis();
			timeExceeded = (currentTime - timeOptimizationStarted) > timeLimit;

			if (optimizerTrace && timeExceeded)
			{
				trace(TIME_EXCEEDED, 0, 0, 0.0, null);
			}
		}

		if (bestCost.isUninitialized() && foundABestPlan &&
			((!usingPredsPushedFromAbove && !bestJoinOrderUsedPredsFromAbove)
				|| timeExceeded))
		{
			/* We can get here if this OptimizerImpl is for a subquery
			 * that timed out for a previous permutation of the outer
			 * query, but then the outer query itself did _not_ timeout.
			 * In that case we'll end up back here for another round of
			 * optimization, but our timeExceeded flag will be true.
			 * We don't want to reset all of the timeout state here
			 * because that could lead to redundant work (see comments
			 * in prepForNextRound()), but we also don't want to return
			 * without having a plan, because then we'd return an unfairly
			 * high "bestCost" value--i.e. Double.MAX_VALUE.  Note that
			 * we can't just revert back to whatever bestCost we had
			 * prior to this because that cost is for some previous
			 * permutation of the outer query--not the current permutation--
			 * and thus would be incorrect.  So instead we have to delay
			 * the timeout until we find a complete (and valid) join order,
			 * so that we can return a valid cost estimate.  Once we have
			 * a valid cost we'll then go through the timeout logic
			 * and stop optimizing.
			 * 
			 * All of that said, instead of just trying the first possible
			 * join order, we jump to the join order that gave us the best
			 * cost in previous rounds.  We know that such a join order exists
			 * because that's how our timeout value was set to begin with--so
			 * if there was no best join order, we never would have timed out
			 * and thus we wouldn't be here.
			 *
			 * We can also get here if we've already optimized the list
			 * of optimizables once (in a previous round of optimization)
			 * and now we're back to do it again.  If that's true AND
			 * we did *not* receive any predicates pushed from above AND
			 * the bestJoinOrder from the previous round did *not* depend
			 * on predicates pushed from above, then we'll jump to the
			 * previous join order and start there.  NOTE: if after jumping
			 * to the previous join order and calculating the cost we haven't
			 * timed out, we will continue looking at other join orders (as
			 * usual) until we exhaust them all or we time out.
			 */
			if (permuteState != JUMPING)
			{
				// By setting firstLookOrder to our target join order
				// and then setting our permuteState to JUMPING, we'll
				// jump to the target join order and get the cost.  That
				// cost will then be saved as bestCost, allowing us to
				// proceed with normal timeout logic.
				if (firstLookOrder == null)
					firstLookOrder = new int[numOptimizables];
				for (int i = 0; i < numOptimizables; i++)
					firstLookOrder[i] = bestJoinOrder[i];
				permuteState = JUMPING;

				// If we were in the middle of a join order when this
				// happened, then reset the join order before jumping.
				// The call to rewindJoinOrder() here will put joinPosition
				// back to 0.  But that said, we'll then end up incrementing 
				// joinPosition before we start looking for the next join
				// order (see below), which means we need to set it to -1
				// here so that it gets incremented to "0" and then
				// processing can continue as normal from there.  Note:
				// we don't need to set reloadBestPlan to true here
				// because we only get here if we have *not* found a
				// best plan yet.
				if (joinPosition > 0)
				{
					rewindJoinOrder();
					joinPosition = -1;
				}
			}

			// Reset the timeExceeded flag so that we'll keep going
			// until we find a complete join order.  NOTE: we intentionally
			// do _not_ reset the timeOptimizationStarted value because we
			// we want to go through this timeout logic for every
			// permutation, to make sure we timeout as soon as we have
			// our first complete join order.
			timeExceeded = false;
		}

		/*
		** Pick the next table in the join order, if there is an unused position
		** in the join order, and the current plan is less expensive than
		** the best plan so far, and the amount of time spent optimizing is
		** still less than the cost of the best plan so far, and a best
		** cost has been found in the current join position.  Otherwise,
		** just pick the next table in the current position.
		*/
		boolean joinPosAdvanced = false;

		/* Determine if the current plan is still less expensive than
		 * the best plan so far.  If bestCost is uninitialized then
		 * we want to return false here; if we didn't, then in the (rare)
		 * case where the current cost is greater than Double.MAX_VALUE
		 * (esp. if it's Double.POSITIVE_INFINITY, which can occur
		 * for very deeply nested queries with long FromLists) we would
		 * give up on the current plan even though we didn't have a
		 * best plan yet, which would be wrong.  Also note: if we have
		 * a required row ordering then we might end up using the
		 * sort avoidance plan--but we don't know at this point
		 * which plan (sort avoidance or "normal") we're going to
		 * use, so we error on the side of caution and only short-
		 * circuit if both currentCost and currentSortAvoidanceCost
		 * (if the latter is applicable) are greater than bestCost.
		 */
		boolean alreadyCostsMore =
			!bestCost.isUninitialized() &&
			(currentCost.compare(bestCost) > 0) &&
			((requiredRowOrdering == null) ||
				(currentSortAvoidanceCost.compare(bestCost) > 0));

		if ((joinPosition < (numOptimizables - 1)) &&
			!alreadyCostsMore &&
			( ! timeExceeded )
			)
		{
			/*
			** Are we either starting at the first join position (in which
			** case joinPosition will be -1), or has a best cost been found
			** in the current join position?  The latter case might not be
			** true if there is no feasible join order.
			*/
			if ((joinPosition < 0) ||
				optimizableList.getOptimizable(
											proposedJoinOrder[joinPosition]).
								getBestAccessPath().getCostEstimate() != null)
			{
				joinPosition++;
				joinPosAdvanced = true;

				/*
				** When adding a table to the join order, the best row
				** row ordering for the outer tables becomes the starting
				** point for the row ordering of the current table.
				*/
				bestRowOrdering.copy(currentRowOrdering);
			}
		}
		else
		{
			if (optimizerTrace)
 			{
				/*
				** Not considered short-circuiting if all slots in join
				** order are taken.
				*/
				if (joinPosition < (numOptimizables - 1))
				{
					trace(SHORT_CIRCUITING, 0, 0, 0.0, null);
				}
			}

			// If we short-circuited the current join order then we need
			// to make sure that, when we start pulling optimizables to find
			// a new join order, we reload the best plans for those
			// optimizables as we pull them.  Otherwise we could end up
			// generating a plan for an optimizable even though that plan
			// was part of a short-circuited (and thus rejected) join
			// order.
			if (joinPosition < (numOptimizables - 1))
				reloadBestPlan = true;
		}

		if (permuteState == JUMPING && !joinPosAdvanced && joinPosition >= 0)
		{
			//not feeling well in the middle of jump
			// Note: we have to make sure we reload the best plans
			// as we rewind since they may have been clobbered
			// (as part of the current join order) before we gave
			// up on jumping.
			reloadBestPlan = true;
			rewindJoinOrder();  //fall
			permuteState = NO_JUMP;  //give up
		}

		/*
		** The join position becomes < 0 when all the permutations have been
		** looked at.
		*/
		while (joinPosition >= 0)
		{
			int nextOptimizable = 0;

			if (desiredJoinOrderFound || timeExceeded)
			{
				/*
				** If the desired join order has been found (which will happen
				** if the user specifies a join order), pretend that there are
				** no more optimizables at this join position.  This will cause
				** us to back out of the current join order.
				**
				** Also, don't look at any more join orders if we have taken
				** too much time with this optimization.
				*/
				nextOptimizable = numOptimizables;
			}
			else if (permuteState == JUMPING)  //still jumping
			{
				/* We're "jumping" to a join order that puts the optimizables
				** with the lowest estimated costs first (insofar as it
				** is legal to do so).  The "firstLookOrder" array holds the
				** ideal join order for position <joinPosition> up thru
				** position <numOptimizables-1>.  So here, we look at the
				** ideal optimizable to place at <joinPosition> and see if
				** it's legal; if it is, then we're done.  Otherwise, we
				** swap it with <numOptimizables-1> and see if that gives us
				** a legal join order w.r.t <joinPosition>.  If not, then we
				** swap it with <numOptimizables-2> and check, and if that
				** fails, then we swap it with <numOptimizables-3>, and so
				** on.  For example, assume we have 6 optimizables whose
				** order from least expensive to most expensive is 2, 1, 4,
				** 5, 3, 0.  Assume also that we've already verified the
				** legality of the first two positions--i.e. that joinPosition
				** is now "2". That means that "firstLookOrder" currently
				** contains the following:
				**
				** [ pos ]    0  1  2  3  4  5
				** [ opt ]    2  1  4  5  3  0
				**
				** Then at this point, we do the following:
				**
				**  -- Check to see if the ideal optimizable "4" is valid
				**     at its current position (2)
				**  -- If opt "4" is valid, then we're done; else we
				**     swap it with the value at position _5_:
				**
				** [ pos ]    0  1  2  3  4  5
				** [ opt ]    2  1  0  5  3  4
				**
				**  -- Check to see if optimizable "0" is valid at its
				**     new position (2).
				**  -- If opt "0" is valid, then we're done; else we
				**     put "0" back in its original position and swap
				**     the ideal optimizer ("4") with the value at
				**     position _4_:
				**
				** [ pos ]    0  1  2  3  4  5
				** [ opt ]    2  1  3  5  4  0
				**
				**  -- Check to see if optimizable "3" is valid at its
				**     new position (2).
				**  -- If opt "3" is valid, then we're done; else we
				**     put "3" back in its original position and swap
				**     the ideal optimizer ("4") with the value at
				**     position _3_:
				**
				** [ pos ]    0  1  2  3  4  5
				** [ opt ]    2  1  5  4  3  0
				**
				**  -- Check to see if optimizable "5" is valid at its
				**     new position (2).
				**  -- If opt "5" is valid, then we're done; else we've
				**     tried all the available optimizables and none
				**     of them are legal at position 2.  In this case,
				**     we give up on "JUMPING" and fall back to normal
				**     join-order processing.
				*/

				int idealOptimizable = firstLookOrder[joinPosition];
				nextOptimizable = idealOptimizable;
				int lookPos = numOptimizables;
				int lastSwappedOpt = -1;

				Optimizable nextOpt;
				for (nextOpt = optimizableList.getOptimizable(nextOptimizable);
					!(nextOpt.legalJoinOrder(assignedTableMap));
					nextOpt = optimizableList.getOptimizable(nextOptimizable))
				{
					// Undo last swap, if we had one.
					if (lastSwappedOpt >= 0) {
						firstLookOrder[joinPosition] = idealOptimizable;
						firstLookOrder[lookPos] = lastSwappedOpt;
					}

					if (lookPos > joinPosition + 1) {
					// we still have other possibilities; get the next
					// one by "swapping" it into the current position.
						lastSwappedOpt = firstLookOrder[--lookPos];
						firstLookOrder[joinPosition] = lastSwappedOpt;
						firstLookOrder[lookPos] = idealOptimizable;
						nextOptimizable = lastSwappedOpt;
					}
					else {
					// we went through all of the available optimizables
					// and none of them were legal in the current position;
					// so we give up and fall back to normal processing.
					// Note: we have to make sure we reload the best plans
					// as we rewind since they may have been clobbered
					// (as part of the current join order) before we got
					// here.
						if (joinPosition > 0) {
							joinPosition--;
							reloadBestPlan = true;
							rewindJoinOrder();
						}
						permuteState = NO_JUMP;
						break;
					}
				}

				if (permuteState == NO_JUMP)
					continue;

				if (joinPosition == numOptimizables - 1) {
				// we just set the final position within our
				// "firstLookOrder" join order; now go ahead
				// and search for the best join order, starting from
				// the join order stored in "firstLookOrder".  This
				// is called walking "high" because we're searching
				// the join orders that are at or "above" (after) the
				// order found in firstLookOrder.  Ex. if we had three
				// optimizables and firstLookOrder was [1 2 0], then
				// the "high" would be [1 2 0], [2 0 1] and [2 1 0];
				// the "low" would be [0 1 2], [0 2 1], and [1 0 2].
				// We walk the "high" first, then fall back and
				// walk the "low".
					permuteState = WALK_HIGH;
				}
			}
			else
			{
				/* Find the next unused table at this join position */
				nextOptimizable = proposedJoinOrder[joinPosition] + 1;

				for ( ; nextOptimizable < numOptimizables; nextOptimizable++)
				{
					boolean found = false;
					for (int posn = 0; posn < joinPosition; posn++)
					{
						/*
						** Is this optimizable already somewhere
						** in the join order?
						*/
						if (proposedJoinOrder[posn] == nextOptimizable)
						{
							found = true;
							break;
						}
					}

					/* Check to make sure that all of the next optimizable's
					 * dependencies have been satisfied.
					 */
					if (nextOptimizable < numOptimizables)
					{
						Optimizable nextOpt =
								optimizableList.getOptimizable(nextOptimizable);
						if (! (nextOpt.legalJoinOrder(assignedTableMap)))
						{
							if (optimizerTrace)
							{
								trace(SKIPPING_JOIN_ORDER, nextOptimizable, 0, 0.0, null);
							}

							/*
							** If this is a user specified join order then it is illegal.
							*/
							if ( ! optimizableList.optimizeJoinOrder())
							{
								if (optimizerTrace)
								{
									trace(ILLEGAL_USER_JOIN_ORDER, 0, 0, 0.0, null);
								}

								throw StandardException.newException(SQLState.LANG_ILLEGAL_FORCED_JOIN_ORDER);
							}
							continue;
						}
					}

					if (! found)
					{
						break;
					}
				}

			}

			/*
			** We are going to try an optimizable at the current join order
			** position.  Is there one already at that position?
			*/
			if (proposedJoinOrder[joinPosition] >= 0)
			{
				/*
				** We are either going to try another table at the current
				** join order position, or we have exhausted all the tables
				** at the current join order position.  In either case, we
				** need to pull the table at the current join order position
				** and remove it from the join order.
				*/
				Optimizable pullMe =
					optimizableList.getOptimizable(
											proposedJoinOrder[joinPosition]);

				/*
				** Subtract the cost estimate of the optimizable being
				** removed from the total cost estimate.
				**
				** The total cost is the sum of all the costs, but the total
				** number of rows is the number of rows returned by the
				** innermost optimizable.
				*/
				double prevRowCount;
				double prevSingleScanRowCount;
				int prevPosition = 0;
				if (joinPosition == 0)
				{
					prevRowCount = outermostCostEstimate.rowCount();
					prevSingleScanRowCount = outermostCostEstimate.singleScanRowCount();
				}
				else
				{
					prevPosition = proposedJoinOrder[joinPosition - 1];
					CostEstimate localCE = 
						optimizableList.
							getOptimizable(prevPosition).
								getBestAccessPath().
									getCostEstimate();
					prevRowCount = localCE.rowCount();
					prevSingleScanRowCount = localCE.singleScanRowCount();
				}

				/*
				** If there is no feasible join order, the cost estimate
				** in the best access path may never have been set.
				** In this case, do not subtract anything from the
				** current cost, since nothing was added to the current
				** cost.
				*/
				double newCost = currentCost.getEstimatedCost();
				double pullCost = 0.0;
				CostEstimate pullCostEstimate =
								pullMe.getBestAccessPath().getCostEstimate();
				if (pullCostEstimate != null)
				{
					pullCost = pullCostEstimate.getEstimatedCost();

					newCost -= pullCost;

					/*
					** It's possible for newCost to go negative here due to
					** loss of precision.
					*/
					if (newCost < 0.0)
						newCost = 0.0;
				}

				/* If we are choosing a new outer table, then
				 * we rest the starting cost to the outermostCost.
				 * (Thus avoiding any problems with floating point
				 * accuracy and going negative.)
				 */
				if (joinPosition == 0)
				{
					if (outermostCostEstimate != null)
					{
						newCost = outermostCostEstimate.getEstimatedCost();
					}
					else
					{
						newCost = 0.0;
					}
				}

				currentCost.setCost(
					newCost,
					prevRowCount,
					prevSingleScanRowCount);
				
				/*
				** Subtract from the sort avoidance cost if there is a
				** required row ordering.
				**
				** NOTE: It is not necessary here to check whether the
				** best cost was ever set for the sort avoidance path,
				** because it considerSortAvoidancePath() would not be
				** set if there cost were not set.
				*/
				if (requiredRowOrdering != null)
				{
					if (pullMe.considerSortAvoidancePath())
					{
						AccessPath ap = pullMe.getBestSortAvoidancePath();
						double	   prevEstimatedCost = 0.0d;

						/*
						** Subtract the sort avoidance cost estimate of the
						** optimizable being removed from the total sort
						** avoidance cost estimate.
						**
						** The total cost is the sum of all the costs, but the
						** total number of rows is the number of rows returned
						** by the innermost optimizable.
						*/
						if (joinPosition == 0)
						{
							prevRowCount = outermostCostEstimate.rowCount();
							prevSingleScanRowCount = outermostCostEstimate.singleScanRowCount();
							/* If we are choosing a new outer table, then
							 * we rest the starting cost to the outermostCost.
							 * (Thus avoiding any problems with floating point
							 * accuracy and going negative.)
							 */
							prevEstimatedCost = outermostCostEstimate.getEstimatedCost();
						}
						else
						{
							CostEstimate localCE = 
								optimizableList.
									getOptimizable(prevPosition).
										getBestSortAvoidancePath().
											getCostEstimate();
							prevRowCount = localCE.rowCount();
							prevSingleScanRowCount = localCE.singleScanRowCount();
							prevEstimatedCost = currentSortAvoidanceCost.getEstimatedCost() -
													ap.getCostEstimate().getEstimatedCost();
						}

						currentSortAvoidanceCost.setCost(
							prevEstimatedCost,
							prevRowCount,
							prevSingleScanRowCount);

						/*
						** Remove the table from the best row ordering.
						** It should not be necessary to remove it from
						** the current row ordering, because it is
						** maintained as we step through the access paths
						** for the current Optimizable.
						*/
						bestRowOrdering.removeOptimizable(
													pullMe.getTableNumber());

						/*
						** When removing a table from the join order,
						** the best row ordering for the remaining outer tables
						** becomes the starting point for the row ordering of
						** the current table.
						*/
						bestRowOrdering.copy(currentRowOrdering);
					}
				}

				/*
				** Pull the predicates at from the optimizable and put
				** them back in the predicate list.
				**
				** NOTE: This is a little inefficient because it pulls the
				** single-table predicates, which are guaranteed to always
				** be pushed to the same optimizable.  We could make this
				** leave the single-table predicates where they are.
				*/
				pullMe.pullOptPredicates(predicateList);

				/*
				** When we pull an Optimizable we need to go through and
				** load whatever best path we found for that Optimizable
				** with respect to _this_ OptimizerImpl.  An Optimizable
				** can have different "best paths" for different Optimizer
				** Impls if there are subqueries beneath it; we need to make
				** sure that when we pull it, it's holding the best path as
				** as we determined it to be for _us_.
				**
				** NOTE: We we only reload the best plan if it's necessary
				** to do so--i.e. if the best plans aren't already loaded.
				** The plans will already be loaded if the last complete
				** join order we had was the best one so far, because that
				** means we called "rememberAsBest" on every Optimizable
				** in the list and, as part of that call, we will run through
				** and set trulyTheBestAccessPath for the entire subtree.
				** So if we haven't tried any other plans since then,
				** we know that every Optimizable (and its subtree) already
				** has the correct best plan loaded in its trulyTheBest
				** path field.  It's good to skip the load in this case
				** because 'reloading best plans' involves walking the
				** entire subtree of _every_ Optimizable in the list, which
				** can be expensive if there are deeply nested subqueries.
				*/
				if (reloadBestPlan)
					pullMe.addOrLoadBestPlanMapping(false, this);

				/* Mark current join position as unused */
				proposedJoinOrder[joinPosition] = -1;
			}

			/* Have we exhausted all the optimizables at this join position? */
			if (nextOptimizable >= numOptimizables)
			{
				/*
				** If we're not optimizing the join order, remember the first
				** join order.
				*/
				if ( ! optimizableList.optimizeJoinOrder())
				{
					// Verify that the user specified a legal join order
					if ( ! optimizableList.legalJoinOrder(numTablesInQuery))
					{
						if (optimizerTrace)
						{
							trace(ILLEGAL_USER_JOIN_ORDER, 0, 0, 0.0, null);
						}

						throw StandardException.newException(SQLState.LANG_ILLEGAL_FORCED_JOIN_ORDER);
					}

					if (optimizerTrace)
					{
						trace(USER_JOIN_ORDER_OPTIMIZED, 0, 0, 0.0, null);
					}

					desiredJoinOrderFound = true;
				}

				if (permuteState == READY_TO_JUMP && joinPosition > 0 && joinPosition == numOptimizables-1)
				{
					permuteState = JUMPING;

					/* A simple heuristics is that the row count we got indicates a potentially
					 * good join order.  We'd like row count to get big as late as possible, so
					 * that less load is carried over.
					 */
					double rc[] = new double[numOptimizables];
					for (int i = 0; i < numOptimizables; i++)
					{
						firstLookOrder[i] = i;
						CostEstimate ce = optimizableList.getOptimizable(i).
												getBestAccessPath().getCostEstimate();
						if (ce == null)
						{
							permuteState = READY_TO_JUMP;  //come again?
							break;
						}
						rc[i] = ce.singleScanRowCount();
					}
					if (permuteState == JUMPING)
					{
						boolean doIt = false;
						int temp;
						for (int i = 0; i < numOptimizables; i++)	//simple selection sort
						{
							int k = i;
							for (int j = i+1; j < numOptimizables; j++)
								if (rc[j] < rc[k])  k = j;
							if (k != i)
							{
								rc[k] = rc[i];	//destroy the bridge
								temp = firstLookOrder[i];
								firstLookOrder[i] = firstLookOrder[k];
								firstLookOrder[k] = temp;
								doIt = true;
							}
						}

						if (doIt)
						{
							joinPosition--;
							rewindJoinOrder();  //jump from ground
							continue;
						}
						else permuteState = NO_JUMP;	//never
					}
				}

				/*
				** We have exhausted all the optimizables at this level.
				** Go back up one level.
				*/

				/* Go back up one join position */
				joinPosition--;

				/* Clear the assigned table map for the previous position 
				 * NOTE: We need to do this here to for the dependency tracking
				 */
				if (joinPosition >= 0)
				{
					Optimizable pullMe =
						optimizableList.getOptimizable(
											proposedJoinOrder[joinPosition]);

					/*
					** Clear the bits from the table at this join position.
					** This depends on them having been set previously.
					** NOTE: We need to do this here to for the dependency tracking
					*/
					assignedTableMap.xor(pullMe.getReferencedTableMap());
				}

				if (joinPosition < 0 && permuteState == WALK_HIGH) //reached peak
				{
					joinPosition = 0;	//reset, fall down the hill
					permuteState = WALK_LOW;
				}
				continue;
			}

			/*
			** We have found another optimizable to try at this join position.
			*/
			proposedJoinOrder[joinPosition] = nextOptimizable;

			if (permuteState == WALK_LOW)
			{
				boolean finishedCycle = true;
				for (int i = 0; i < numOptimizables; i++)
				{
					if (proposedJoinOrder[i] < firstLookOrder[i])
					{
						finishedCycle = false;
						break;
					}
					else if (proposedJoinOrder[i] > firstLookOrder[i])  //done
						break;
				}
				if (finishedCycle)
				{
					// We just set proposedJoinOrder[joinPosition] above, so
					// if we're done we need to put it back to -1 to indicate
					// that it's an empty slot.  Then we rewind and pull any
					// other Optimizables at positions < joinPosition.
					// Note: we have to make sure we reload the best plans
					// as we rewind since they may have been clobbered
					// (as part of the current join order) before we got
					// here.
					proposedJoinOrder[joinPosition] = -1;
					joinPosition--;
					if (joinPosition >= 0)
					{
						reloadBestPlan = true;
						rewindJoinOrder();
						joinPosition = -1;
					}
					permuteState = READY_TO_JUMP;
					return false;
				}
			}

			/* Re-init (clear out) the cost for the best access path
			 * when placing a table.
			 */
			optimizableList.getOptimizable(nextOptimizable).
				getBestAccessPath().setCostEstimate((CostEstimate) null);

			/* Set the assigned table map to be exactly the tables
			 * in the current join order. 
			 */
			assignedTableMap.clearAll();
			for (int index = 0; index <= joinPosition; index++)
			{
				assignedTableMap.or(optimizableList.getOptimizable(proposedJoinOrder[index]).getReferencedTableMap());
			}

			if (optimizerTrace)
			{
				trace(CONSIDERING_JOIN_ORDER, 0, 0, 0.0, null);
			}

			Optimizable nextOpt =
							optimizableList.getOptimizable(nextOptimizable);

			nextOpt.startOptimizing(this, currentRowOrdering);

			pushPredicates(
				optimizableList.getOptimizable(nextOptimizable),
				assignedTableMap);

			return true;
		}

		return false;
	}

	private void rewindJoinOrder()
		throws StandardException
	{
		for (; ; joinPosition--)
		{
			Optimizable pullMe =
				optimizableList.getOptimizable(
									proposedJoinOrder[joinPosition]);
			pullMe.pullOptPredicates(predicateList);
			if (reloadBestPlan)
				pullMe.addOrLoadBestPlanMapping(false, this);
			proposedJoinOrder[joinPosition] = -1;
			if (joinPosition == 0) break;
		}
		currentCost.setCost(0.0d, 0.0d, 0.0d);
		currentSortAvoidanceCost.setCost(0.0d, 0.0d, 0.0d);
		assignedTableMap.clearAll();
	}

	/*
	** Push predicates from this optimizer's list to the given optimizable,
	** as appropriate given the outer tables.
	**
	** @param curTable	The Optimizable to push predicates down to
	** @param outerTables	A bit map of outer tables
	**
	** @exception StandardException		Thrown on error
	*/
	void pushPredicates(Optimizable curTable, JBitSet outerTables)
			throws StandardException
	{
		/*
		** Push optimizable clauses to current position in join order.
		**
		** RESOLVE - We do not push predicates with subqueries not materializable.
		*/

		int		numPreds = predicateList.size();
		JBitSet	predMap = new JBitSet(numTablesInQuery);
		OptimizablePredicate pred;

		/* Walk the OptimizablePredicateList.  For each OptimizablePredicate,
		 * see if it can be assigned to the Optimizable at the current join
		 * position.
		 *
		 * NOTE - We walk the OPL backwards since we will hopefully be deleted
		 * entries as we walk it.
		 */
		for (int predCtr = numPreds - 1; predCtr >= 0; predCtr--)
		{
			pred = predicateList.getOptPredicate(predCtr);

			/* Skip over non-pushable predicates */
			if (! isPushable(pred))
			{
				continue;
			}
				
			/* Make copy of referenced map so that we can do destructive
			 * manipulation on the copy.
			 */
			predMap.setTo(pred.getReferencedMap());

			/* Clear bits representing those tables that have already been
			 * assigned, except for the current table.  The outer table map
			 * includes the current table, so if the predicate is ready to
			 * be pushed, predMap will end up with no bits set.
			 */
			for (int index = 0; index < predMap.size(); index++)
			{
				if (outerTables.get(index))
				{
					predMap.clear(index);
				}
			}

			/*
			** Only consider non-correlated variables when deciding where
			** to push predicates down to.
			*/
			predMap.and(nonCorrelatedTableMap);

			/*
			** Finally, push the predicate down to the Optimizable at the
			** end of the current proposed join order, if it can be evaluated
			** there.
			*/
			if (predMap.getFirstSetBit() == -1)
			{
				/* Push the predicate and remove it from the list */
				if (curTable.pushOptPredicate(pred))
				{
					predicateList.removeOptPredicate(predCtr);
				}
			}
		}
	}

	/**
	 * @see Optimizer#getNextDecoratedPermutation
	 *
	 * @exception StandardException		Thrown on error
	 */
	public boolean getNextDecoratedPermutation()
				throws StandardException
	{
		boolean		retval;
		Optimizable curOpt =
			optimizableList.getOptimizable(proposedJoinOrder[joinPosition]);
		double		originalRowCount = 0.0;
		
		// RESOLVE: Should we step through the different join strategies here?

		/* Returns true until all access paths are exhausted */
		retval =  curOpt.nextAccessPath(this,
										(OptimizablePredicateList) null,
										currentRowOrdering);

		// If the previous path that we considered for curOpt was _not_ the best
		// path for this round, then we need to revert back to whatever the
		// best plan for curOpt was this round.  Note that the cost estimate
		// for bestAccessPath could be null here if the last path that we
		// checked was the only one possible for this round.
		if ((curOpt.getBestAccessPath().getCostEstimate() != null) &&
			(curOpt.getCurrentAccessPath().getCostEstimate() != null))
		{
			// Note: we can't just check to see if bestCost is cheaper
			// than currentCost because it's possible that currentCost
			// is actually cheaper--but it may have been 'rejected' because
			// it would have required too much memory.  So we just check
			// to see if bestCost and currentCost are different.  If so
			// then we know that the most recent access path (represented
			// by "current" access path) was not the best.
			if (curOpt.getBestAccessPath().getCostEstimate().compare(
				curOpt.getCurrentAccessPath().getCostEstimate()) != 0)
			{
				curOpt.addOrLoadBestPlanMapping(false, curOpt);
			}
			else if (curOpt.getBestAccessPath().getCostEstimate().rowCount() <
				curOpt.getCurrentAccessPath().getCostEstimate().rowCount())
			{
				// If currentCost and bestCost have the same cost estimate
				// but currentCost has been rejected because of memory, we
				// still need to revert the plans.  In this case the row
				// count for currentCost will be greater than the row count
				// for bestCost, so that's what we just checked.
				curOpt.addOrLoadBestPlanMapping(false, curOpt);
			}
		}

		/*
		** When all the access paths have been looked at, we know what the
		** cheapest one is, so remember it.  Only do this if a cost estimate
		** has been set for the best access path - one will not have been
		** set if no feasible plan has been found.
		*/
		CostEstimate ce = curOpt.getBestAccessPath().getCostEstimate();
		if ( ( ! retval ) && (ce != null))
		{
			/*
			** Add the cost of the current optimizable to the total cost.
			** The total cost is the sum of all the costs, but the total
			** number of rows is the number of rows returned by the innermost
			** optimizable.
			*/
			currentCost.setCost(
				currentCost.getEstimatedCost() + ce.getEstimatedCost(),
				ce.rowCount(),
				ce.singleScanRowCount());

			if (curOpt.considerSortAvoidancePath() &&
				requiredRowOrdering != null)
			{
				/* Add the cost for the sort avoidance path, if there is one */
				ce = curOpt.getBestSortAvoidancePath().getCostEstimate();

				currentSortAvoidanceCost.setCost(
					currentSortAvoidanceCost.getEstimatedCost() +
						ce.getEstimatedCost(),
					ce.rowCount(),
					ce.singleScanRowCount());
			}

			if (optimizerTrace)
			{
				trace(TOTAL_COST_NON_SA_PLAN, 0, 0, 0.0, null);
				if (curOpt.considerSortAvoidancePath())
				{
					trace(TOTAL_COST_SA_PLAN, 0, 0, 0.0, null);
				}
			}
				
			/* Do we have a complete join order? */
			if ( joinPosition == (numOptimizables - 1) )
			{
				if (optimizerTrace)
				{
					trace(COMPLETE_JOIN_ORDER, 0, 0, 0.0, null);
				}

				/* Add cost of sorting to non-sort-avoidance cost */
				if (requiredRowOrdering != null)
				{
					boolean gotSortCost = false;

					/* Only get the sort cost once */
					if (sortCost == null)
					{
						sortCost = newCostEstimate();
					}
					/* requiredRowOrdering records if the bestCost so far is
					 * sort-needed or not, as done in rememberBestCost.  If
					 * the bestCost so far is sort-needed, and assume
					 * currentCost is also sort-needed, we want this comparison
					 * to be as accurate as possible.  Different plans may
					 * produce different estimated row count (eg., heap scan
					 * vs. index scan during a join), sometimes the difference
					 * could be very big.  However the actual row count should
					 * be only one value.  So when comparing these two plans,
					 * we want them to have the same sort cost.  We want to
					 * take the smaller row count, because a better estimation
					 * (eg. through index) would yield a smaller number.  We
					 * adjust the bestCost here if it had a bigger rowCount
					 * estimate.  The performance improvement of doing this
					 * sometimes is quite dramatic, eg. from 17 sec to 0.5 sec,
					 * see beetle 4353.
					 */
					else if (requiredRowOrdering.getSortNeeded())
					{
						if (bestCost.rowCount() > currentCost.rowCount())
						{
							// adjust bestCost
							requiredRowOrdering.estimateCost(
													bestCost.rowCount(),
													bestRowOrdering,
													sortCost
													);
							double oldSortCost = sortCost.getEstimatedCost();
							requiredRowOrdering.estimateCost(
													currentCost.rowCount(),
													bestRowOrdering,
													sortCost
													);
							gotSortCost = true;
							bestCost.setCost(bestCost.getEstimatedCost() -
											oldSortCost + 
											sortCost.getEstimatedCost(),
											sortCost.rowCount(),
											currentCost.singleScanRowCount());
						}
						else if (bestCost.rowCount() < currentCost.rowCount())
						{
							// adjust currentCost's rowCount
							currentCost.setCost(currentCost.getEstimatedCost(),
												bestCost.rowCount(),
												currentCost.singleScanRowCount());
						}
					}

					/* This does not figure out if sorting is necessary, just
					 * an asumption that sort is needed; if the assumption is
					 * wrong, we'll look at sort-avoidance cost as well, later
					 */
					if (! gotSortCost)
					{
						requiredRowOrdering.estimateCost(
													currentCost.rowCount(),
													bestRowOrdering,
													sortCost
													);
					}

					originalRowCount = currentCost.rowCount();

					currentCost.setCost(currentCost.getEstimatedCost() +
										sortCost.getEstimatedCost(),
										sortCost.rowCount(),
										currentCost.singleScanRowCount()
										);
					
					if (optimizerTrace)
					{
						trace(COST_OF_SORTING, 0, 0, 0.0, null);
						trace(TOTAL_COST_WITH_SORTING, 0, 0, 0.0, null);
					}
				}

				/*
				** Is the cost of this join order lower than the best one we've
				** found so far?
				**
				** NOTE: If the user has specified a join order, it will be the
				** only join order the optimizer considers, so it is OK to use
				** costing to decide that it is the "best" join order.
				**
				** For very deeply nested queries, it's possible that the optimizer
				** will return an estimated cost of Double.INFINITY, which is
				** greater than our uninitialized cost of Double.MAX_VALUE and
				** thus the "compare" check below will return false.   So we have
				** to check to see if bestCost is uninitialized and, if so, we
				** save currentCost regardless of what value it is--because we
				** haven't found anything better yet.
				**
				** That said, it's also possible for bestCost to be infinity
				** AND for current cost to be infinity, as well.  In that case
				** we can't really tell much by comparing the two, so for lack
				** of better alternative we look at the row counts.  See
				** CostEstimateImpl.compare() for more.
				*/
				if ((! foundABestPlan) ||
					(currentCost.compare(bestCost) < 0) ||
					bestCost.isUninitialized())
				{
					rememberBestCost(currentCost, Optimizer.NORMAL_PLAN);

					// Since we just remembered all of the best plans,
					// no need to reload them when pulling Optimizables
					// from this join order.
					reloadBestPlan = false;
				}
				else
					reloadBestPlan = true;

				/* Subtract cost of sorting from non-sort-avoidance cost */
				if (requiredRowOrdering != null)
				{
					/*
					** The cost could go negative due to loss of precision.
					*/
					double newCost = currentCost.getEstimatedCost() -
										sortCost.getEstimatedCost();
					if (newCost < 0.0)
						newCost = 0.0;
					
					currentCost.setCost(newCost,
										originalRowCount,
										currentCost.singleScanRowCount()
										);
				}

				/*
				** This may be the best sort-avoidance plan if there is a
				** required row ordering, and we are to consider a sort
				** avoidance path on the last Optimizable in the join order.
				*/
				if (requiredRowOrdering != null &&
					curOpt.considerSortAvoidancePath())
				{
					if (requiredRowOrdering.sortRequired(bestRowOrdering) ==
									RequiredRowOrdering.NOTHING_REQUIRED)
					{
						if (optimizerTrace)
						{
							trace(CURRENT_PLAN_IS_SA_PLAN, 0, 0, 0.0, null);
						}

						if ((currentSortAvoidanceCost.compare(bestCost) <= 0)
							|| bestCost.isUninitialized())
						{
							rememberBestCost(currentSortAvoidanceCost,
											Optimizer.SORT_AVOIDANCE_PLAN);
						}
					}
				}
			}
		}

		return retval;
	}

	/**
	 * Is the cost of this join order lower than the best one we've
	 * found so far?  If so, remember it.
	 *
	 * NOTE: If the user has specified a join order, it will be the
	 * only join order the optimizer considers, so it is OK to use
	 * costing to decide that it is the "best" join order.
	 *	@exception StandardException	Thrown on error
	 */
	private void rememberBestCost(CostEstimate currentCost, int planType)
		throws StandardException
	{
		foundABestPlan = true;

		if (optimizerTrace)
		{
			trace(CHEAPEST_PLAN_SO_FAR, 0, 0, 0.0, null);
			trace(PLAN_TYPE, planType, 0, 0.0, null);
			trace(COST_OF_CHEAPEST_PLAN_SO_FAR, 0, 0, 0.0, null);
		}

		/* Remember the current cost as best */
		bestCost.setCost(currentCost);

		// Our time limit for optimizing this round is the time we think
		// it will take us to execute the best join order that we've 
		// found so far (across all rounds of optimizing).  In other words,
		// don't spend more time optimizing this OptimizerImpl than we think
		// it's going to take to execute the best plan.  So if we've just
		// found a new "best" join order, use that to update our time limit.
		if (bestCost.getEstimatedCost() < timeLimit)
			timeLimit = bestCost.getEstimatedCost();

		/*
		** Remember the current join order and access path
		** selections as best.
		** NOTE: We want the optimizer trace to print out in
		** join order instead of in table number order, so
		** we use 2 loops.
		*/
		bestJoinOrderUsedPredsFromAbove = usingPredsPushedFromAbove;
		for (int i = 0; i < numOptimizables; i++)
		{
			bestJoinOrder[i] = proposedJoinOrder[i];
		}
		for (int i = 0; i < numOptimizables; i++)
		{
			optimizableList.getOptimizable(bestJoinOrder[i]).
				rememberAsBest(planType, this);
		}

		/* Remember if a sort is not needed for this plan */
		if (requiredRowOrdering != null)
		{
			if (planType == Optimizer.SORT_AVOIDANCE_PLAN)
				requiredRowOrdering.sortNotNeeded();
			else
				requiredRowOrdering.sortNeeded();
		}

		if (optimizerTrace)
		{
			if (requiredRowOrdering != null)
			{
				trace(SORT_NEEDED_FOR_ORDERING, planType, 0, 0.0, null);
			}
			trace(REMEMBERING_BEST_JOIN_ORDER, 0, 0, 0.0, null);
		}
	}

	/**
	 * @see org.apache.derby.iapi.sql.compile.Optimizer#costPermutation
	 *
	 * @exception StandardException		Thrown on error
	 */
	public void costPermutation() throws StandardException
	{
		/*
		** Get the cost of the outer plan so far.  This gives us the current
		** estimated rows, ordering, etc.
		*/
		CostEstimate outerCost;
		if (joinPosition == 0)
		{
			outerCost = outermostCostEstimate;
		}
		else
		{
			/*
			** NOTE: This is somewhat problematic.  We assume here that the
			** outer cost from the best access path for the outer table
			** is OK to use even when costing the sort avoidance path for
			** the inner table.  This is probably OK, since all we use
			** from the outer cost is the row count.
			*/
			outerCost =
				optimizableList.getOptimizable(
					proposedJoinOrder[joinPosition - 1]).
						getBestAccessPath().getCostEstimate();
		}

		Optimizable optimizable = optimizableList.getOptimizable(proposedJoinOrder[joinPosition]);

		/*
		** Don't consider non-feasible join strategies.
		*/
		if ( ! optimizable.feasibleJoinStrategy(predicateList, this))
		{
			return;
		}

		/* Cost the optimizable at the current join position */
		optimizable.optimizeIt(this,
							   predicateList,
							   outerCost,
							   currentRowOrdering);
	}

	/**
	 * @see org.apache.derby.iapi.sql.compile.Optimizer#costOptimizable
	 *
	 * @exception StandardException		Thrown on error
	 */
	public void	costOptimizable(Optimizable optimizable,
								TableDescriptor td, 
								ConglomerateDescriptor cd,
								OptimizablePredicateList predList,
								CostEstimate outerCost)
			throws StandardException
	{
		/*
		** Don't consider non-feasible join strategies.
		*/
		if ( ! optimizable.feasibleJoinStrategy(predList, this))
		{
			return;
		}

		/*
		** Classify the predicates according to the given conglomerate.
		** The predicates are classified as start keys, stop keys,
		** qualifiers, and none-of-the-above.  They are also ordered
		** to match the ordering of columns in keyed conglomerates (no
		** ordering is done for heaps).
		*/
		// if (predList != null)
		// 	predList.classify(optimizable, cd);

		if (ruleBasedOptimization)
		{
			ruleBasedCostOptimizable(optimizable,
										td,
										cd,
										predList,
										outerCost);
		}
		else
		{
			costBasedCostOptimizable(optimizable,
										td,
										cd,
										predList,
										outerCost);
		}
	}

	/**
	 * This method decides whether the given conglomerate descriptor is
	 * cheapest based on rules, rather than based on cost estimates.
	 * The rules are:
	 *
	 *		Covering matching indexes are preferred above all
	 *		Non-covering matching indexes are next in order of preference
	 *		Covering non-matching indexes are next in order of preference
	 *		Heap scans are next in order of preference
	 *		Non-covering, non-matching indexes are last in order of
	 *		preference.
	 *
	 * In the current language architecture, there will always be a
	 * heap, so a non-covering, non-matching index scan will never be
	 * chosen.  However, the optimizer may see a non-covering, non-matching
	 * index first, in which case it will choose it temporarily as the
	 * best conglomerate seen so far.
	 *
	 * NOTE: This method sets the cost in the optimizable, even though it
	 * doesn't use the cost to determine which access path to choose.  There
	 * are two reasons for this: the cost might be needed to determine join
	 * order, and the cost information is copied to the query plan.
	 */
	private void ruleBasedCostOptimizable(Optimizable optimizable,
											TableDescriptor td,
											ConglomerateDescriptor cd,
											OptimizablePredicateList predList,
											CostEstimate outerCost)
				throws StandardException
	{
		/* CHOOSE BEST CONGLOMERATE HERE */
		ConglomerateDescriptor	conglomerateDescriptor = null;
		ConglomerateDescriptor	bestConglomerateDescriptor = null;
		AccessPath bestAp = optimizable.getBestAccessPath();
		int lockMode = optimizable.getCurrentAccessPath().getLockMode();


		/*
		** If the current conglomerate better than the best so far?
		** The pecking order is:
		**		o  covering index useful for predicates
		**			(if there are predicates)
		**		o  index useful for predicates (if there are predicates)
		**		o  covering index
		**		o  table scan
		*/

		/*
		** If there is more than one conglomerate descriptor
		** choose any index that is potentially useful.
		*/
		if (predList != null &&
			predList.useful(optimizable, cd))
		{
			/*
			** Do not let a non-covering matching index scan supplant a
			** covering matching index scan.
			*/
			boolean newCoveringIndex = optimizable.isCoveringIndex(cd);
			if ( ( ! bestAp.getCoveringIndexScan()) ||
			    bestAp.getNonMatchingIndexScan() ||
				newCoveringIndex )
			{
				bestAp.setCostEstimate(
					estimateTotalCost(
									predList,
									cd,
									outerCost,
									optimizable
									)
								);
				bestAp.setConglomerateDescriptor(cd);
				bestAp.setNonMatchingIndexScan(false);
				bestAp.setCoveringIndexScan(newCoveringIndex);

				bestAp.setLockMode(optimizable.getCurrentAccessPath().getLockMode());

				optimizable.rememberJoinStrategyAsBest(bestAp);
			}

			return;
		}

		/* Remember the "last" covering index.
		 * NOTE - Since we don't have costing, we just go for the
		 * last one since that's as good as any
		 */
		if (optimizable.isCoveringIndex(cd))
		{
			bestAp.setCostEstimate(
								estimateTotalCost(predList,
													cd,
													outerCost,
													optimizable)
								);
			bestAp.setConglomerateDescriptor(cd);
			bestAp.setNonMatchingIndexScan(true);
			bestAp.setCoveringIndexScan(true);

			bestAp.setLockMode(optimizable.getCurrentAccessPath().getLockMode());

			optimizable.rememberJoinStrategyAsBest(bestAp);
			return;
		}

		/*
		** If this is the heap, and the best conglomerate so far is a
		** non-covering, non-matching index scan, pick the heap.
		*/
		if ( ( ! bestAp.getCoveringIndexScan()) &&
			 bestAp.getNonMatchingIndexScan() &&
			 ( ! cd.isIndex() )
		   )
		{
			bestAp.setCostEstimate(
									estimateTotalCost(predList,
														cd,
														outerCost,
														optimizable)
									);

			bestAp.setConglomerateDescriptor(cd);

			bestAp.setLockMode(optimizable.getCurrentAccessPath().getLockMode());

			optimizable.rememberJoinStrategyAsBest(bestAp);

			/*
			** No need to set non-matching index scan and covering
			** index scan, as these are already correct.
			*/
			return;
		}


		/*
		** If all else fails, and no conglomerate has been picked yet,
		** pick this one.
		*/
		bestConglomerateDescriptor = bestAp.getConglomerateDescriptor();
		if (bestConglomerateDescriptor == null)
		{
			bestAp.setCostEstimate(
									estimateTotalCost(predList,
									 					cd,
														outerCost,
														optimizable)
									);

			bestAp.setConglomerateDescriptor(cd);

			/*
			** We have determined above that this index is neither covering
			** nor matching.
			*/
			bestAp.setCoveringIndexScan(false);
			bestAp.setNonMatchingIndexScan(cd.isIndex());

			bestAp.setLockMode(optimizable.getCurrentAccessPath().getLockMode());

			optimizable.rememberJoinStrategyAsBest(bestAp);
		}

		return;
	}

	/**
	 * This method decides whether the given conglomerate descriptor is
	 * cheapest based on cost, rather than based on rules.  It compares
	 * the cost of using the given ConglomerateDescriptor with the cost
	 * of using the best ConglomerateDescriptor so far.
	 */
	private void costBasedCostOptimizable(Optimizable optimizable,
											TableDescriptor td,
											ConglomerateDescriptor cd,
											OptimizablePredicateList predList,
											CostEstimate outerCost)
				throws StandardException
	{
		CostEstimate estimatedCost = estimateTotalCost(predList,
														cd,
														outerCost,
														optimizable);

		// Before considering the cost, make sure we set the optimizable's
		// "current" cost to be the one that we found.  Doing this allows
		// us to compare "current" with "best" later on to find out if
		// the "current" plan is also the "best" one this round--if it's
		// not then we'll have to revert back to whatever the best plan is.
		// That check is performed in getNextDecoratedPermutation() of
		// this class.
		optimizable.getCurrentAccessPath().setCostEstimate(estimatedCost);

		/*
		** Skip this access path if it takes too much memory.
		**
		** NOTE: The default assumption here is that the number of rows in
		** a single scan is the total number of rows divided by the number
		** of outer rows.  The optimizable may over-ride this assumption.
		*/
		// RESOLVE: The following call to memoryUsageOK does not behave
		// correctly if outerCost.rowCount() is POSITIVE_INFINITY; see
		// DERBY-1259.
		if( ! optimizable.memoryUsageOK( estimatedCost.rowCount() / outerCost.rowCount(), maxMemoryPerTable))
		{
			if (optimizerTrace)
			{
				trace(SKIPPING_DUE_TO_EXCESS_MEMORY, 0, 0, 0.0, null);
			}
			return;
		}

		/* Pick the cheapest cost for this particular optimizable. */
		AccessPath ap = optimizable.getBestAccessPath();
		CostEstimate bestCostEstimate = ap.getCostEstimate();

		if ((bestCostEstimate == null) ||
			bestCostEstimate.isUninitialized() ||
			(estimatedCost.compare(bestCostEstimate) < 0))
		{
			ap.setConglomerateDescriptor(cd);
			ap.setCostEstimate(estimatedCost);
			ap.setCoveringIndexScan(optimizable.isCoveringIndex(cd));

			/*
			** It's a non-matching index scan either if there is no
			** predicate list, or nothing in the predicate list is useful
			** for limiting the scan.
			*/
			ap.setNonMatchingIndexScan(
									(predList == null) ||
									( ! ( predList.useful(optimizable, cd) ) )
									);
			ap.setLockMode(optimizable.getCurrentAccessPath().getLockMode());
			optimizable.rememberJoinStrategyAsBest(ap);
		}

		/*
		** Keep track of the best sort-avoidance path if there is a
		** required row ordering.
		*/
		if (requiredRowOrdering != null)
		{
			/*
			** The current optimizable can avoid a sort only if the
			** outer one does, also (if there is an outer one).
			*/
			if (joinPosition == 0 ||
				optimizableList.getOptimizable(
										proposedJoinOrder[joinPosition - 1]).
												considerSortAvoidancePath())
			{
				/*
				** There is a required row ordering - does the proposed access
				** path avoid a sort?
				*/
				if (requiredRowOrdering.sortRequired(currentRowOrdering,
														assignedTableMap)
										== RequiredRowOrdering.NOTHING_REQUIRED)
				{
					ap = optimizable.getBestSortAvoidancePath();
					bestCostEstimate = ap.getCostEstimate();

					/* Is this the cheapest sort-avoidance path? */
					if ((bestCostEstimate == null) ||
						bestCostEstimate.isUninitialized() ||
						(estimatedCost.compare(bestCostEstimate) < 0))
					{
						ap.setConglomerateDescriptor(cd);
						ap.setCostEstimate(estimatedCost);
						ap.setCoveringIndexScan(
											optimizable.isCoveringIndex(cd));

						/*
						** It's a non-matching index scan either if there is no
						** predicate list, or nothing in the predicate list is
						** useful for limiting the scan.
						*/
						ap.setNonMatchingIndexScan(
										(predList == null) ||
										( ! (predList.useful(optimizable, cd)) )
										);
						ap.setLockMode(
							optimizable.getCurrentAccessPath().getLockMode());
						optimizable.rememberJoinStrategyAsBest(ap);
						optimizable.rememberSortAvoidancePath();

						/*
						** Remember the current row ordering as best
						*/
						currentRowOrdering.copy(bestRowOrdering);
					}
				}
			}
		}
	}

	/**
	 * This is the version of costOptimizable for non-base-tables.
	 *
	 * @see Optimizer#considerCost
	 *
	 * @exception StandardException		Thrown on error
	 */
	public void	considerCost(Optimizable optimizable,
								OptimizablePredicateList predList,
								CostEstimate estimatedCost,
								CostEstimate outerCost)
			throws StandardException
	{
		/*
		** Don't consider non-feasible join strategies.
		*/
		if ( ! optimizable.feasibleJoinStrategy(predList, this))
		{
			return;
		}

		// Before considering the cost, make sure we set the optimizable's
		// "current" cost to be the one that we received.  Doing this allows
		// us to compare "current" with "best" later on to find out if
		// the "current" plan is also the "best" one this round--if it's
		// not then we'll have to revert back to whatever the best plan is.
		// That check is performed in getNextDecoratedPermutation() of
		// this class.
		optimizable.getCurrentAccessPath().setCostEstimate(estimatedCost);

		/*
		** Skip this access path if it takes too much memory.
		**
		** NOTE: The default assumption here is that the number of rows in
		** a single scan is the total number of rows divided by the number
		** of outer rows.  The optimizable may over-ride this assumption.
		*/

        // RESOLVE: The following call to memoryUsageOK does not behave
        // correctly if outerCost.rowCount() is POSITIVE_INFINITY; see
        // DERBY-1259.
        if( ! optimizable.memoryUsageOK( estimatedCost.rowCount() / outerCost.rowCount(),
                                         maxMemoryPerTable))
		{
			if (optimizerTrace)
			{
				trace(SKIPPING_DUE_TO_EXCESS_MEMORY, 0, 0, 0.0, null);
			}
			return;
		}

		/* Pick the cheapest cost for this particular optimizable. 
		 * NOTE: Originally, the code only chose the new access path if 
		 * it was cheaper than the old access path.  However, I (Jerry)
		 * found that the new and old costs were the same for a derived
		 * table and the old access path did not have a join strategy
		 * associated with it in that case.  So, we now choose the new
		 * access path if it is the same cost or cheaper than the current
		 * access path.
		 */
		AccessPath ap = optimizable.getBestAccessPath();
		CostEstimate bestCostEstimate = ap.getCostEstimate();

		if ((bestCostEstimate == null) ||
			bestCostEstimate.isUninitialized() ||
			(estimatedCost.compare(bestCostEstimate) <= 0))
		{
			ap.setCostEstimate(estimatedCost);
			optimizable.rememberJoinStrategyAsBest(ap);
		}

		/*
		** Keep track of the best sort-avoidance path if there is a
		** required row ordering.
		*/
		if (requiredRowOrdering != null)
		{
			/*
			** The current optimizable can avoid a sort only if the
			** outer one does, also (if there is an outer one).
			*/
			if (joinPosition == 0 ||
				optimizableList.getOptimizable(
										proposedJoinOrder[joinPosition - 1]).
												considerSortAvoidancePath())
			{
				/*
				** There is a required row ordering - does the proposed access
				** path avoid a sort?
				*/
				if (requiredRowOrdering.sortRequired(currentRowOrdering,
														assignedTableMap)
										== RequiredRowOrdering.NOTHING_REQUIRED)
				{
					ap = optimizable.getBestSortAvoidancePath();
					bestCostEstimate = ap.getCostEstimate();

					/* Is this the cheapest sort-avoidance path? */
					if ((bestCostEstimate == null) ||
						bestCostEstimate.isUninitialized() ||
						(estimatedCost.compare(bestCostEstimate) < 0))
					{
						ap.setCostEstimate(estimatedCost);
						optimizable.rememberJoinStrategyAsBest(ap);
						optimizable.rememberSortAvoidancePath();

						/*
						** Remember the current row ordering as best
						*/
						currentRowOrdering.copy(bestRowOrdering);
					}
				}
			}
		}
	}

	/**
	 * @see org.apache.derby.iapi.sql.compile.Optimizer#getDataDictionary
	 */

	public DataDictionary getDataDictionary()
	{
		return dDictionary;
	}

	/**
	 * @see Optimizer#modifyAccessPaths
	 *
	 * @exception StandardException		Thrown on error
	 */
	public void modifyAccessPaths() throws StandardException
	{
		if (optimizerTrace)
		{
			trace(MODIFYING_ACCESS_PATHS, 0, 0, 0.0, null);
		}

		if ( ! foundABestPlan)
		{
			if (optimizerTrace)
			{
				trace(NO_BEST_PLAN, 0, 0, 0.0, null);
			}

			throw StandardException.newException(SQLState.LANG_NO_BEST_PLAN_FOUND);
		}

		/* Change the join order of the list of optimizables */
		optimizableList.reOrder(bestJoinOrder);

		/* Form a bit map of the tables as they are put into the join order */
		JBitSet outerTables = new JBitSet(numOptimizables);

		/* Modify the access path of each table, as necessary */
		for (int ictr = 0; ictr < numOptimizables; ictr++)
		{
			Optimizable optimizable = optimizableList.getOptimizable(ictr);

			/* Current table is treated as an outer table */
			outerTables.or(optimizable.getReferencedTableMap());

			/*
			** Push any appropriate predicates from this optimizer's list
			** to the optimizable, as appropriate.
			*/
			pushPredicates(optimizable, outerTables);

			optimizableList.setOptimizable(
				ictr,
				optimizable.modifyAccessPath(outerTables));
		}
	}

	/** @see Optimizer#newCostEstimate */
	public CostEstimate newCostEstimate()
	{
		return new CostEstimateImpl();
	}

	/** @see Optimizer#getOptimizedCost */
	public CostEstimate getOptimizedCost()
	{
		return bestCost;
	}

	/**
	 * @see Optimizer#getFinalCost
	 *
	 * Sum up the cost of all of the trulyTheBestAccessPaths
	 * for the Optimizables in our list.  Assumption is that
	 * we only get here after optimization has completed--i.e.
	 * while modifying access paths.
	 */
	public CostEstimate getFinalCost()
	{
		// If we already did this once, just return the result.
		if (finalCostEstimate != null)
			return finalCostEstimate;

		// The total cost is the sum of all the costs, but the total
		// number of rows is the number of rows returned by the innermost
		// optimizable.
		finalCostEstimate = getNewCostEstimate(0.0d, 0.0d, 0.0d);
		CostEstimate ce = null;
		for (int i = 0; i < bestJoinOrder.length; i++)
		{
			ce = optimizableList.getOptimizable(bestJoinOrder[i])
					.getTrulyTheBestAccessPath().getCostEstimate();

			finalCostEstimate.setCost(
				finalCostEstimate.getEstimatedCost() + ce.getEstimatedCost(),
				ce.rowCount(),
				ce.singleScanRowCount());
		}

		return finalCostEstimate;
	}

	/** @see Optimizer#setOuterRows */
	public void setOuterRows(double outerRows)
	{
		outermostCostEstimate.setCost(
				outermostCostEstimate.getEstimatedCost(),
				outerRows,
				outermostCostEstimate.singleScanRowCount());
	}

	/** @see Optimizer#tableLockThreshold */
	public int tableLockThreshold()
	{
		return tableLockThreshold;
	}

	/**
	 * Get the number of join strategies supported by this optimizer.
	 */
	public int getNumberOfJoinStrategies()
	{
		return joinStrategies.length;
	}

	/** @see Optimizer#getJoinStrategy */
	public JoinStrategy getJoinStrategy(int whichStrategy) {
		if (SanityManager.DEBUG) {
			if (whichStrategy < 0 || whichStrategy >= joinStrategies.length) {
				SanityManager.THROWASSERT("whichStrategy value " +
									whichStrategy +
									" out of range - should be between 0 and " +
									(joinStrategies.length - 1));
			}

			if (joinStrategies[whichStrategy] == null) {
				SanityManager.THROWASSERT("Strategy " + whichStrategy +
											" not filled in.");
			}
		}

		return joinStrategies[whichStrategy];
	}

	/** @see Optimizer#getJoinStrategy */
	public JoinStrategy getJoinStrategy(String whichStrategy) {
		JoinStrategy retval = null;
		String upperValue = StringUtil.SQLToUpperCase(whichStrategy);

		for (int i = 0; i < joinStrategies.length; i++) {
			if (upperValue.equals(joinStrategies[i].getName())) {
				retval = joinStrategies[i];
			}
		}

		return retval;
	}

	/**
		@see Optimizer#uniqueJoinWithOuterTable

		@exception StandardException	Thrown on error
	 */
	public double uniqueJoinWithOuterTable(OptimizablePredicateList predList)
											throws StandardException
	{
		double retval = -1.0;
		double numUniqueKeys = 1.0;
		double currentRows = currentCost.rowCount();

		if (predList != null)
		{

			for (int i = joinPosition - 1; i >= 0; i--)
			{
				Optimizable opt = optimizableList.getOptimizable(
														proposedJoinOrder[i]);
				double uniqueKeysThisOptimizable = opt.uniqueJoin(predList);
				if (uniqueKeysThisOptimizable > 0.0)
					numUniqueKeys *= opt.uniqueJoin(predList);
			}
		}

		if (numUniqueKeys != 1.0)
		{
			retval = numUniqueKeys / currentRows;
		}

		return retval;
	}

	private boolean isPushable(OptimizablePredicate pred)
	{
		/* Predicates which contain subqueries that are not materializable are
		 * not currently pushable.
		 */
		if (pred.hasSubquery())
		{
			return false;
		}
		else
		{
			return true;
		}
	}

	/**
	 * Estimate the total cost of doing a join with the given optimizable.
	 *
	 * @exception StandardException		Thrown on error
	 */
	private CostEstimate estimateTotalCost(OptimizablePredicateList predList,
											ConglomerateDescriptor cd,
											CostEstimate outerCost,
											Optimizable optimizable)
		throws StandardException
	{
		/* Get the cost of a single scan */
		CostEstimate resultCost =
			optimizable.estimateCost(predList,
									cd,
									outerCost,
									this,
									currentRowOrdering);

		return resultCost;
	}

	/** @see Optimizer#getLevel */
	public int getLevel()
	{
		return 1;
	}

	public CostEstimateImpl getNewCostEstimate(double theCost,
							double theRowCount,
							double theSingleScanRowCount)
	{
		return new CostEstimateImpl(theCost, theRowCount, theSingleScanRowCount);
	}

	// Optimzer trace
	public void trace(int traceFlag, int intParam1, int intParam2,
					  double doubleParam, Object objectParam1)
	{
	}
	
	/** @see Optimizer#useStatistics */
	public boolean useStatistics() { return useStatistics && optimizableList.useStatistics(); }

	/**
	 * Remember the current best join order as the best one for
	 * some outer query, represented by another OptimizerImpl. Then
	 * iterate through our optimizableList and tell each Optimizable
	 * to remember its best plan with respect to the outer query.
	 * See Optimizable.addOrLoadBestPlan() for more on why this is
	 * necessary.
	 *
	 * @param doAdd True if we're adding a mapping, false if we're loading.
	 * @param planKey Object to use as the map key when adding/looking up
	 *  a plan.  If this is an instance of OptimizerImpl then it corresponds
	 *  to an outer query; otherwise it's some Optimizable above this
	 *  OptimizerImpl that could potentially reject plans chosen by this
	 *  OptimizerImpl.
	 */
	protected void addOrLoadBestPlanMappings(boolean doAdd,
		Object planKey) throws StandardException
	{
		// First we save this OptimizerImpl's best join order.  If there's
		// only one optimizable in the list, then there's only one possible
		// join order, so don't bother.
		if (numOptimizables > 1)
		{
			int [] joinOrder = null;
			if (doAdd)
			{
				// If the savedJoinOrder map already exists, search for the
				// join order for the target optimizer and reuse that.
				if (savedJoinOrders == null)
					savedJoinOrders = new HashMap();
				else
					joinOrder = (int[])savedJoinOrders.get(planKey);

				// If we don't already have a join order array for the optimizer,
				// create a new one.
				if (joinOrder == null)
					joinOrder = new int[numOptimizables];

				// Now copy current bestJoinOrder and save it.
				for (int i = 0; i < bestJoinOrder.length; i++)
					joinOrder[i] = bestJoinOrder[i];

				savedJoinOrders.put(planKey, joinOrder);
			}
			else
			{
				// If we get here, we want to load the best join order from our
				// map into this OptimizerImpl's bestJoinOrder array.

				// If we don't have any join orders saved, then there's nothing to
				// load.  This can happen if the optimizer tried some join order
				// for which there was no valid plan.
				if (savedJoinOrders != null)
				{
					joinOrder = (int[])savedJoinOrders.get(planKey);
					if (joinOrder != null)
					{
						// Load the join order we found into our
						// bestJoinOrder array.
						for (int i = 0; i < joinOrder.length; i++)
							bestJoinOrder[i] = joinOrder[i];
					}
				}
			}
		}

		// Now iterate through all Optimizables in this OptimizerImpl's list
	 	// and add/load the best plan "mapping" for each one, as described in
	 	// in Optimizable.addOrLoadBestPlanMapping().
		for (int i = optimizableList.size() - 1; i >= 0; i--)
		{
			optimizableList.getOptimizable(i).
				addOrLoadBestPlanMapping(doAdd, planKey);
		}
	}

	/**
	 * Add predicates to this optimizer's predicateList. This method
	 * is intended for use during the modifyAccessPath() phase of
	 * compilation, as it allows nodes (esp. SelectNodes) to add to the
	 * list of predicates available for the final "push" before code
	 * generation.  Just as the constructor for this class allows a
	 * caller to specify a predicate list to use during the optimization
	 * phase, this method allows a caller to specify a predicate list to
	 * use during the modify-access-paths phase.
	 *
	 * Before adding the received predicates, this method also
	 * clears out any scoped predicates that might be sitting in
	 * OptimizerImpl's list from the last round of optimizing.
	 *
	 * @param pList List of predicates to add to this OptimizerImpl's
	 *  own list for pushing.
	 */
	protected void addPredicatesToList(PredicateList pList)
		throws StandardException
	{
		if ((pList == null) || (pList == predicateList))
		// nothing to do.
			return;

		if (predicateList == null)
		// in this case, there is no 'original' predicateList, so we
		// can just create one.
			predicateList = new PredicateList();

		// First, we need to go through and remove any predicates in this
		// optimizer's list that may have been pushed here from outer queries
		// during the previous round(s) of optimization.  We know if the
		// predicate was pushed from an outer query because it will have
		// been scoped to the node for which this OptimizerImpl was
		// created.
		Predicate pred = null;
		for (int i = predicateList.size() - 1; i >= 0; i--) {
			pred = (Predicate)predicateList.getOptPredicate(i);
			if (pred.isScopedForPush())
				predicateList.removeOptPredicate(i);
		}

		// Now transfer all of the received predicates into this
		// OptimizerImpl's list.
		pList.transferAllPredicates(predicateList);
		return;
	}

}
