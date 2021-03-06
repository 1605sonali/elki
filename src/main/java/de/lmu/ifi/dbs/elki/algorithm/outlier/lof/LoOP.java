package de.lmu.ifi.dbs.elki.algorithm.outlier.lof;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2013
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.outlier.OutlierAlgorithm;
import de.lmu.ifi.dbs.elki.data.type.CombinedTypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.QueryUtil;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDoubleDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.distance.DistanceDBIDListIter;
import de.lmu.ifi.dbs.elki.database.ids.distance.DoubleDistanceDBIDListIter;
import de.lmu.ifi.dbs.elki.database.ids.distance.DoubleDistanceKNNList;
import de.lmu.ifi.dbs.elki.database.ids.distance.KNNList;
import de.lmu.ifi.dbs.elki.database.query.DatabaseQuery;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery;
import de.lmu.ifi.dbs.elki.database.relation.MaterializedRelation;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.minkowski.EuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.index.preprocessed.knn.MaterializeKNNPreprocessor;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.logging.progress.StepProgress;
import de.lmu.ifi.dbs.elki.math.Mean;
import de.lmu.ifi.dbs.elki.math.MeanVariance;
import de.lmu.ifi.dbs.elki.math.statistics.distribution.NormalDistribution;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.ProbabilisticOutlierScore;
import de.lmu.ifi.dbs.elki.utilities.Alias;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.CommonConstraints;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.ObjectParameter;
import de.lmu.ifi.dbs.elki.utilities.pairs.Pair;

/**
 * LoOP: Local Outlier Probabilities
 * 
 * Distance/density based algorithm similar to LOF to detect outliers, but with
 * statistical methods to achieve better result stability.
 * 
 * @author Erich Schubert
 * 
 * @apiviz.has KNNQuery
 * 
 * @param <O> type of objects handled by this algorithm
 * @param <D> type of distances used
 */
@Title("LoOP: Local Outlier Probabilities")
@Description("Variant of the LOF algorithm normalized using statistical values.")
@Reference(authors = "H.-P. Kriegel, P. Kröger, E. Schubert, A. Zimek", title = "LoOP: Local Outlier Probabilities", booktitle = "Proceedings of the 18th International Conference on Information and Knowledge Management (CIKM), Hong Kong, China, 2009", url = "http://dx.doi.org/10.1145/1645953.1646195")
@Alias({ "de.lmu.ifi.dbs.elki.algorithm.outlier.LoOP", "LoOP", "outlier.LoOP" })
public class LoOP<O, D extends NumberDistance<D, ?>> extends AbstractAlgorithm<OutlierResult> implements OutlierAlgorithm {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(LoOP.class);

  /**
   * The distance function to determine the reachability distance between
   * database objects.
   */
  public static final OptionID REACHABILITY_DISTANCE_FUNCTION_ID = new OptionID("loop.referencedistfunction", "Distance function to determine the density of an object.");

  /**
   * The distance function to determine the reachability distance between
   * database objects.
   */
  public static final OptionID COMPARISON_DISTANCE_FUNCTION_ID = new OptionID("loop.comparedistfunction", "Distance function to determine the reference set of an object.");

  /**
   * Parameter to specify the number of nearest neighbors of an object to be
   * considered for computing its LOOP_SCORE, must be an integer greater than 1.
   */
  public static final OptionID KREACH_ID = new OptionID("loop.kref", "The number of nearest neighbors of an object to be used for the PRD value.");

  /**
   * Parameter to specify the number of nearest neighbors of an object to be
   * considered for computing its LOOP_SCORE, must be an integer greater than 1.
   */
  public static final OptionID KCOMP_ID = new OptionID("loop.kcomp", "The number of nearest neighbors of an object to be considered for computing its LOOP_SCORE.");

  /**
   * Parameter to specify the number of nearest neighbors of an object to be
   * considered for computing its LOOP_SCORE, must be an integer greater than 1.
   */
  public static final OptionID LAMBDA_ID = new OptionID("loop.lambda", "The number of standard deviations to consider for density computation.");

  /**
   * Holds the value of {@link #KREACH_ID}.
   */
  int kreach;

  /**
   * Holds the value of {@link #KCOMP_ID}.
   */
  int kcomp;

  /**
   * Hold the value of {@link #LAMBDA_ID}.
   */
  double lambda;

  /**
   * Preprocessor Step 1.
   */
  protected DistanceFunction<? super O, D> reachabilityDistanceFunction;

  /**
   * Preprocessor Step 2.
   */
  protected DistanceFunction<? super O, D> comparisonDistanceFunction;

  /**
   * Include object itself in kNN neighborhood.
   */
  static boolean objectIsInKNN = false;

  /**
   * Constructor with parameters.
   * 
   * @param kreach k for reachability
   * @param kcomp k for comparison
   * @param reachabilityDistanceFunction distance function for reachability
   * @param comparisonDistanceFunction distance function for comparison
   * @param lambda Lambda parameter
   */
  public LoOP(int kreach, int kcomp, DistanceFunction<? super O, D> reachabilityDistanceFunction, DistanceFunction<? super O, D> comparisonDistanceFunction, double lambda) {
    super();
    this.kreach = kreach;
    this.kcomp = kcomp;
    this.reachabilityDistanceFunction = reachabilityDistanceFunction;
    this.comparisonDistanceFunction = comparisonDistanceFunction;
    this.lambda = lambda;
  }

  /**
   * Get the kNN queries for the algorithm.
   * 
   * @param database Database to analyze
   * @param relation Relation to analyze
   * @param stepprog Progress logger, may be {@code null}
   * @return result
   */
  protected Pair<KNNQuery<O, D>, KNNQuery<O, D>> getKNNQueries(Database database, Relation<O> relation, StepProgress stepprog) {
    KNNQuery<O, D> knnComp;
    KNNQuery<O, D> knnReach;
    if(comparisonDistanceFunction == reachabilityDistanceFunction || comparisonDistanceFunction.equals(reachabilityDistanceFunction)) {
      // We need each neighborhood twice - use "HEAVY" flag.
      knnComp = QueryUtil.getKNNQuery(relation, comparisonDistanceFunction, Math.max(kreach, kcomp), DatabaseQuery.HINT_HEAVY_USE, DatabaseQuery.HINT_OPTIMIZED_ONLY, DatabaseQuery.HINT_NO_CACHE);
      // No optimized kNN query - use a preprocessor!
      if(knnComp == null) {
        if(stepprog != null) {
          stepprog.beginStep(1, "Materializing neighborhoods with respect to reference neighborhood distance function.", LOG);
        }
        MaterializeKNNPreprocessor<O, D> preproc = new MaterializeKNNPreprocessor<>(relation, comparisonDistanceFunction, kcomp);
        database.addIndex(preproc);
        DistanceQuery<O, D> cdq = database.getDistanceQuery(relation, comparisonDistanceFunction);
        knnComp = preproc.getKNNQuery(cdq, kreach, DatabaseQuery.HINT_HEAVY_USE);
      }
      else {
        if(stepprog != null) {
          stepprog.beginStep(1, "Optimized neighborhoods provided by database.", LOG);
        }
      }
      knnReach = knnComp;
    }
    else {
      if(stepprog != null) {
        stepprog.beginStep(1, "Not materializing distance functions, since we request each DBID once only.", LOG);
      }
      knnComp = QueryUtil.getKNNQuery(relation, comparisonDistanceFunction, kreach);
      knnReach = QueryUtil.getKNNQuery(relation, reachabilityDistanceFunction, kcomp);
    }
    return new Pair<>(knnComp, knnReach);
  }

  /**
   * Performs the LoOP algorithm on the given database.
   * 
   * @param database Database to process
   * @param relation Relation to process
   * @return Outlier result
   */
  public OutlierResult run(Database database, Relation<O> relation) {
    final double sqrt2 = Math.sqrt(2.0);

    StepProgress stepprog = LOG.isVerbose() ? new StepProgress(5) : null;

    Pair<KNNQuery<O, D>, KNNQuery<O, D>> pair = getKNNQueries(database, relation, stepprog);
    KNNQuery<O, D> knnComp = pair.getFirst();
    KNNQuery<O, D> knnReach = pair.getSecond();

    // Assert we got something
    if(knnComp == null) {
      throw new AbortException("No kNN queries supported by database for comparison distance function.");
    }
    if(knnReach == null) {
      throw new AbortException("No kNN queries supported by database for density estimation distance function.");
    }

    // Probabilistic distances
    WritableDoubleDataStore pdists = DataStoreUtil.makeDoubleStorage(relation.getDBIDs(), DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_TEMP);
    Mean mean = new Mean();
    {// computing PRDs
      if(stepprog != null) {
        stepprog.beginStep(3, "Computing pdists", LOG);
      }
      FiniteProgress prdsProgress = LOG.isVerbose() ? new FiniteProgress("pdists", relation.size(), LOG) : null;
      for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
        final KNNList<D> neighbors = knnReach.getKNNForDBID(iditer, kreach);
        mean.reset();
        // use first kref neighbors as reference set
        int ks = 0;
        // TODO: optimize for double distances
        if(neighbors instanceof DoubleDistanceKNNList) {
          for(DoubleDistanceDBIDListIter neighbor = ((DoubleDistanceKNNList) neighbors).iter(); neighbor.valid(); neighbor.advance()) {
            if(objectIsInKNN || !DBIDUtil.equal(neighbor, iditer)) {
              final double d = neighbor.doubleDistance();
              mean.put(d * d);
              ks++;
              if(ks >= kreach) {
                break;
              }
            }
          }
        }
        else {
          for(DistanceDBIDListIter<D> neighbor = neighbors.iter(); neighbor.valid(); neighbor.advance()) {
            if(objectIsInKNN || !DBIDUtil.equal(neighbor, iditer)) {
              double d = neighbor.getDistance().doubleValue();
              mean.put(d * d);
              ks++;
              if(ks >= kreach) {
                break;
              }
            }
          }
        }
        double pdist = lambda * Math.sqrt(mean.getMean());
        pdists.putDouble(iditer, pdist);
        if(prdsProgress != null) {
          prdsProgress.incrementProcessed(LOG);
        }
      }
    }
    // Compute PLOF values.
    WritableDoubleDataStore plofs = DataStoreUtil.makeDoubleStorage(relation.getDBIDs(), DataStoreFactory.HINT_HOT | DataStoreFactory.HINT_TEMP);
    MeanVariance mvplof = new MeanVariance();
    {// compute LOOP_SCORE of each db object
      if(stepprog != null) {
        stepprog.beginStep(4, "Computing PLOF", LOG);
      }

      FiniteProgress progressPLOFs = LOG.isVerbose() ? new FiniteProgress("PLOFs for objects", relation.size(), LOG) : null;
      MeanVariance mv = new MeanVariance();
      for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
        final KNNList<D> neighbors = knnComp.getKNNForDBID(iditer, kcomp);
        mv.reset();
        // use first kref neighbors as comparison set.
        int ks = 0;
        for(DBIDIter neighbor = neighbors.iter(); neighbor.valid(); neighbor.advance()) {
          if(objectIsInKNN || !DBIDUtil.equal(neighbor, iditer)) {
            mv.put(pdists.doubleValue(neighbor));
            ks++;
            if(ks >= kcomp) {
              break;
            }
          }
        }
        double plof = Math.max(pdists.doubleValue(iditer) / mv.getMean(), 1.0);
        if(Double.isNaN(plof) || Double.isInfinite(plof)) {
          plof = 1.0;
        }
        plofs.putDouble(iditer, plof);
        mvplof.put((plof - 1.0) * (plof - 1.0));

        if(progressPLOFs != null) {
          progressPLOFs.incrementProcessed(LOG);
        }
      }
    }

    double nplof = lambda * Math.sqrt(mvplof.getMean());
    if(LOG.isDebugging()) {
      LOG.verbose("nplof normalization factor is " + nplof + " " + mvplof.getMean() + " " + mvplof.getSampleStddev());
    }

    // Compute final LoOP values.
    WritableDoubleDataStore loops = DataStoreUtil.makeDoubleStorage(relation.getDBIDs(), DataStoreFactory.HINT_STATIC);
    {// compute LOOP_SCORE of each db object
      if(stepprog != null) {
        stepprog.beginStep(5, "Computing LoOP scores", LOG);
      }

      FiniteProgress progressLOOPs = LOG.isVerbose() ? new FiniteProgress("LoOP for objects", relation.size(), LOG) : null;
      for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
        loops.putDouble(iditer, NormalDistribution.erf((plofs.doubleValue(iditer) - 1) / (nplof * sqrt2)));

        if(progressLOOPs != null) {
          progressLOOPs.incrementProcessed(LOG);
        }
      }
    }

    if(stepprog != null) {
      stepprog.setCompleted(LOG);
    }

    // Build result representation.
    Relation<Double> scoreResult = new MaterializedRelation<>("Local Outlier Probabilities", "loop-outlier", TypeUtil.DOUBLE, loops, relation.getDBIDs());
    OutlierScoreMeta scoreMeta = new ProbabilisticOutlierScore();
    return new OutlierResult(scoreMeta, scoreResult);
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    final TypeInformation type;
    if(reachabilityDistanceFunction.equals(comparisonDistanceFunction)) {
      type = reachabilityDistanceFunction.getInputTypeRestriction();
    }
    else {
      type = new CombinedTypeInformation(reachabilityDistanceFunction.getInputTypeRestriction(), comparisonDistanceFunction.getInputTypeRestriction());
    }
    return TypeUtil.array(type);
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer<O, D extends NumberDistance<D, ?>> extends AbstractParameterizer {
    /**
     * Holds the value of {@link #KREACH_ID}.
     */
    int kreach = 0;

    /**
     * Holds the value of {@link #KCOMP_ID}.
     */
    int kcomp = 0;

    /**
     * Hold the value of {@link #LAMBDA_ID}.
     */
    double lambda = 2.0;

    /**
     * Preprocessor Step 1.
     */
    protected DistanceFunction<O, D> reachabilityDistanceFunction = null;

    /**
     * Preprocessor Step 2.
     */
    protected DistanceFunction<O, D> comparisonDistanceFunction = null;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      final IntParameter kcompP = new IntParameter(KCOMP_ID);
      kcompP.addConstraint(CommonConstraints.GREATER_THAN_ONE_INT);
      if(config.grab(kcompP)) {
        kcomp = kcompP.intValue();
      }

      final ObjectParameter<DistanceFunction<O, D>> compDistP = new ObjectParameter<>(COMPARISON_DISTANCE_FUNCTION_ID, DistanceFunction.class, EuclideanDistanceFunction.class);
      if(config.grab(compDistP)) {
        comparisonDistanceFunction = compDistP.instantiateClass(config);
      }

      final IntParameter kreachP = new IntParameter(KREACH_ID);
      kreachP.addConstraint(CommonConstraints.GREATER_THAN_ONE_INT);
      kreachP.setOptional(true);
      if(config.grab(kreachP)) {
        kreach = kreachP.intValue();
      }
      else {
        kreach = kcomp;
      }

      final ObjectParameter<DistanceFunction<O, D>> reachDistP = new ObjectParameter<>(REACHABILITY_DISTANCE_FUNCTION_ID, DistanceFunction.class, true);
      if(config.grab(reachDistP)) {
        reachabilityDistanceFunction = reachDistP.instantiateClass(config);
      }

      // TODO: make default 1.0?
      final DoubleParameter lambdaP = new DoubleParameter(LAMBDA_ID, 2.0);
      lambdaP.addConstraint(CommonConstraints.GREATER_THAN_ZERO_DOUBLE);
      if(config.grab(lambdaP)) {
        lambda = lambdaP.doubleValue();
      }
    }

    @Override
    protected LoOP<O, D> makeInstance() {
      DistanceFunction<O, D> realreach = (reachabilityDistanceFunction != null) ? reachabilityDistanceFunction : comparisonDistanceFunction;
      return new LoOP<>(kreach, kcomp, realreach, comparisonDistanceFunction, lambda);
    }
  }
}
