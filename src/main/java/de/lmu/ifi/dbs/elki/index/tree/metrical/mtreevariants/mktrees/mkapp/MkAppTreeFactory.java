package de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.mktrees.mkapp;

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

import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancevalue.NumberDistance;
import de.lmu.ifi.dbs.elki.index.tree.metrical.mtreevariants.AbstractMTreeFactory;
import de.lmu.ifi.dbs.elki.persistent.PageFile;
import de.lmu.ifi.dbs.elki.persistent.PageFileFactory;
import de.lmu.ifi.dbs.elki.utilities.ClassGenericsUtil;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.CommonConstraints;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.Flag;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;

/**
 * Factory for a MkApp-Tree
 * 
 * @author Erich Schubert
 * 
 * @apiviz.stereotype factory
 * @apiviz.uses MkAppTreeIndex oneway - - «create»
 * 
 * @param <O> Object type
 * @param <D> Distance type
 */
public class MkAppTreeFactory<O, D extends NumberDistance<D, ?>> extends AbstractMTreeFactory<O, D, MkAppTreeNode<O, D>, MkAppEntry, MkAppTreeIndex<O, D>, MkAppTreeSettings<O, D>> {
  /**
   * Parameter for nolog
   */
  public static final OptionID NOLOG_ID = new OptionID("mkapp.nolog", "Flag to indicate that the approximation is done in the ''normal'' space instead of the log-log space (which is default).");

  /**
   * Parameter for k
   */
  public static final OptionID K_ID = new OptionID("mkapp.k", "positive integer specifying the maximum number k of reverse k nearest neighbors to be supported.");

  /**
   * Parameter for p
   */
  public static final OptionID P_ID = new OptionID("mkapp.p", "positive integer specifying the order of the polynomial approximation.");

  /**
   * Constructor.
   * 
   * @param pageFileFactory Data storage
   * @param settings Tree settings
   */
  public MkAppTreeFactory(PageFileFactory<?> pageFileFactory, MkAppTreeSettings<O, D> settings) {
    super(pageFileFactory, settings);
  }

  @Override
  public MkAppTreeIndex<O, D> instantiate(Relation<O> relation) {
    PageFile<MkAppTreeNode<O, D>> pagefile = makePageFile(getNodeClass());
    return new MkAppTreeIndex<>(relation, pagefile, settings);
  }

  protected Class<MkAppTreeNode<O, D>> getNodeClass() {
    return ClassGenericsUtil.uglyCastIntoSubclass(MkAppTreeNode.class);
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer<O, D extends NumberDistance<D, ?>> extends AbstractMTreeFactory.Parameterizer<O, D, MkAppTreeNode<O, D>, MkAppEntry, MkAppTreeSettings<O, D>> {
    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      IntParameter kP = new IntParameter(K_ID);
      kP.addConstraint(CommonConstraints.GREATER_EQUAL_ONE_INT);
      if(config.grab(kP)) {
        settings.k_max = kP.getValue();
      }

      IntParameter pP = new IntParameter(P_ID);
      pP.addConstraint(CommonConstraints.GREATER_EQUAL_ONE_INT);
      if(config.grab(pP)) {
        settings.p = pP.getValue();
      }

      Flag nologF = new Flag(NOLOG_ID);
      if(config.grab(nologF)) {
        settings.log = !nologF.getValue();
      }
    }

    @Override
    protected MkAppTreeFactory<O, D> makeInstance() {
      return new MkAppTreeFactory<>(pageFileFactory, settings);
    }

    @Override
    protected MkAppTreeSettings<O, D> makeSettings() {
      return new MkAppTreeSettings<>();
    }
  }
}
