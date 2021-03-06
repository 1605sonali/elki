package de.lmu.ifi.dbs.elki.visualization.visualizers.parallel.selection;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2011
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

import java.awt.geom.Line2D;
import java.util.Collection;

import org.apache.batik.dom.events.DOMMouseEvent;
import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;
import org.w3c.dom.events.Event;
import org.w3c.dom.svg.SVGPoint;

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.HashSetModifiableDBIDs;
import de.lmu.ifi.dbs.elki.result.DBIDSelection;
import de.lmu.ifi.dbs.elki.result.HierarchicalResult;
import de.lmu.ifi.dbs.elki.result.Result;
import de.lmu.ifi.dbs.elki.result.ResultUtil;
import de.lmu.ifi.dbs.elki.result.SelectionResult;
import de.lmu.ifi.dbs.elki.visualization.VisualizationTask;
import de.lmu.ifi.dbs.elki.visualization.batikutil.DragableArea;
import de.lmu.ifi.dbs.elki.visualization.css.CSSClass;
import de.lmu.ifi.dbs.elki.visualization.projector.ParallelPlotProjector;
import de.lmu.ifi.dbs.elki.visualization.style.StyleLibrary;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGPlot;
import de.lmu.ifi.dbs.elki.visualization.svg.SVGUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.AbstractVisFactory;
import de.lmu.ifi.dbs.elki.visualization.visualizers.Visualization;
import de.lmu.ifi.dbs.elki.visualization.visualizers.parallel.AbstractParallelVisualization;

/**
 * Tool-Visualization for the tool to select objects
 * 
 * @author Robert Rödler
 * 
 * @apiviz.stereotype factory
 * @apiviz.uses Instance - - «create»
 */
public class SelectionToolLineVisualization extends AbstractVisFactory {
  /**
   * A short name characterizing this Visualizer.
   */
  private static final String NAME = "Object Selection";

  /**
   * Input modes
   * 
   * @apiviz.exclude
   */
  private enum Mode {
    REPLACE, ADD, INVERT
  }

  /**
   * Constructor, adhering to
   * {@link de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizable}
   */
  public SelectionToolLineVisualization() {
    super();
  }

  @Override
  public Visualization makeVisualization(VisualizationTask task) {
    return new Instance(task);
  }

  @Override
  public void processNewResult(HierarchicalResult baseResult, Result result) {
    Collection<SelectionResult> selectionResults = ResultUtil.filterResults(result, SelectionResult.class);
    for(SelectionResult selres : selectionResults) {
      Collection<ParallelPlotProjector<?>> ps = ResultUtil.filterResults(baseResult, ParallelPlotProjector.class);
      for(ParallelPlotProjector<?> p : ps) {
        final VisualizationTask task = new VisualizationTask(NAME, selres, p.getRelation(), this);
        task.level = VisualizationTask.LEVEL_INTERACTIVE;
        task.tool = true;
        task.thumbnail = false;
        task.noexport = true;
        task.initDefaultVisibility(false);
        baseResult.getHierarchy().add(selres, task);
        baseResult.getHierarchy().add(p, task);
      }
    }
  }

  /**
   * Instance.
   * 
   * @author Robert Rödler
   * 
   * @apiviz.has SelectionResult oneway - - updates
   * @apiviz.has DBIDSelection oneway - - updates
   */
  public class Instance extends AbstractParallelVisualization<NumberVector<?>> implements DragableArea.DragListener {
    /**
     * CSS class of the selection rectangle while selecting.
     */
    private static final String CSS_RANGEMARKER = "selectionRangeMarker";

    /**
     * Element for selection rectangle
     */
    Element rtag;

    /**
     * Element for the rectangle to add listeners
     */
    Element etag;

    /**
     * Constructor.
     * 
     * @param task Task
     */
    public Instance(VisualizationTask task) {
      super(task);
      incrementalRedraw();
    }

    @Override
    protected void redraw() {
      addCSSClasses(svgp);

      rtag = svgp.svgElement(SVGConstants.SVG_G_TAG);
      SVGUtil.addCSSClass(rtag, CSS_RANGEMARKER);
      layer.appendChild(rtag);

      // etag: sensitive area
      DragableArea drag = new DragableArea(svgp, -.1 * getMarginLeft(), -.5 * getMarginTop(), getSizeX() + .2 * getMarginLeft(), getMarginTop() * 1.5 + getSizeY(), this);
      etag = drag.getElement();
      layer.appendChild(etag);
    }

    /**
     * Delete the children of the element
     * 
     * @param container SVG-Element
     */
    private void deleteChildren(Element container) {
      while(container.hasChildNodes()) {
        container.removeChild(container.getLastChild());
      }
    }

    @Override
    public boolean startDrag(SVGPoint startPoint, Event evt) {
      return true;
    }

    @Override
    public boolean duringDrag(SVGPoint startPoint, SVGPoint dragPoint, Event evt, boolean inside) {
      deleteChildren(rtag);
      double x = Math.min(startPoint.getX(), dragPoint.getX());
      double y = Math.min(startPoint.getY(), dragPoint.getY());
      double width = Math.abs(startPoint.getX() - dragPoint.getX());
      double height = Math.abs(startPoint.getY() - dragPoint.getY());
      rtag.appendChild(svgp.svgRect(x, y, width, height));
      return true;
    }

    @Override
    public boolean endDrag(SVGPoint startPoint, SVGPoint dragPoint, Event evt, boolean inside) {
      Mode mode = getInputMode(evt);
      deleteChildren(rtag);
      if(startPoint.getX() != dragPoint.getX() || startPoint.getY() != dragPoint.getY()) {
        updateSelection(mode, startPoint, dragPoint);
      }
      return true;
    }

    /**
     * Get the current input mode, on each mouse event.
     * 
     * @param evt Mouse event.
     * @return current input mode
     */
    private Mode getInputMode(Event evt) {
      if(evt instanceof DOMMouseEvent) {
        DOMMouseEvent domme = (DOMMouseEvent) evt;
        // TODO: visual indication of mode possible?
        if(domme.getShiftKey()) {
          return Mode.ADD;
        }
        else if(domme.getCtrlKey()) {
          return Mode.INVERT;
        }
        else {
          return Mode.REPLACE;
        }
      }
      // Default mode is replace.
      return Mode.REPLACE;
    }

    /**
     * Updates the selection in the context.<br>
     * 
     * @param mode Input mode
     * @param p1 first point of the selected rectangle
     * @param p2 second point of the selected rectangle
     */
    private void updateSelection(Mode mode, SVGPoint p1, SVGPoint p2) {
      DBIDSelection selContext = context.getSelection();
      // Note: we rely on SET semantics below!
      final HashSetModifiableDBIDs selection;
      if(selContext == null || mode == Mode.REPLACE) {
        selection = DBIDUtil.newHashSet();
      }
      else {
        selection = DBIDUtil.newHashSet(selContext.getSelectedIds());
      }
      int[] axisrange = getAxisRange(Math.min(p1.getX(), p2.getX()), Math.max(p1.getX(), p2.getX()));
      DBIDs ids = ResultUtil.getSamplingResult(relation).getSample();
      for(DBIDIter iter = ids.iter(); iter.valid(); iter.advance()) {
        double[] yPos = proj.fastProjectDataToRenderSpace(relation.get(iter));
        if(checkSelected(axisrange, yPos, Math.max(p1.getX(), p2.getX()), Math.min(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.min(p1.getY(), p2.getY()))) {
          if(mode == Mode.INVERT) {
            if(!selection.contains(iter)) {
              selection.add(iter);
            }
            else {
              selection.remove(iter);
            }
          }
          else {
            // In REPLACE and ADD, add objects.
            // The difference was done before by not re-using the selection.
            // Since we are using a set, we can just add in any case.
            selection.add(iter);
          }
        }
      }
      context.setSelection(new DBIDSelection(selection));
    }

    private int[] getAxisRange(double x1, double x2) {
      final int dim = proj.getVisibleDimensions();
      int minaxis = 0;
      int maxaxis = 0;
      boolean minx = true;
      boolean maxx = false;
      int count = -1;
      for(int i = 0; i < dim; i++) {
        if(minx && getVisibleAxisX(i) > x1) {
          minaxis = count;
          minx = false;
          maxx = true;
        }
        if(maxx && (getVisibleAxisX(i) > x2 || i == dim - 1)) {
          maxaxis = count + 1;
          if(i == dim - 1 && getVisibleAxisX(i) <= x2) {
            maxaxis++;
          }
          break;
        }
        count = i;
      }
      return new int[] { minaxis, maxaxis };
    }

    private boolean checkSelected(int[] ar, double[] yPos, double x1, double x2, double y1, double y2) {
      final int dim = proj.getVisibleDimensions();
      if(ar[0] < 0) {
        ar[0] = 0;
      }
      if(ar[1] >= dim) {
        ar[1] = dim - 1;
      }
      for(int i = ar[0] + 1; i <= ar[1] - 1; i++) {
        if(yPos[i] <= y1 && yPos[i] >= y2) {
          return true;
        }
      }
      Line2D.Double idline1 = new Line2D.Double(getVisibleAxisX(ar[0]), yPos[ar[0]], getVisibleAxisX(ar[0] + 1), yPos[ar[0] + 1]);
      Line2D.Double idline2 = new Line2D.Double(getVisibleAxisX(ar[1] - 1), yPos[ar[1] - 1], getVisibleAxisX(ar[1]), yPos[ar[1]]);
      Line2D.Double rectline1 = new Line2D.Double(x2, y1, x1, y1);
      Line2D.Double rectline2 = new Line2D.Double(x2, y1, x2, y2);
      Line2D.Double rectline3 = new Line2D.Double(x2, y2, x1, y2);
      if(idline1.intersectsLine(rectline1) || idline1.intersectsLine(rectline2) || idline1.intersectsLine(rectline3)) {
        return true;
      }
      Line2D.Double rectline4 = new Line2D.Double(x1, y1, x1, y2);
      if(idline2.intersectsLine(rectline1) || idline2.intersectsLine(rectline4) || idline2.intersectsLine(rectline3)) {
        return true;
      }
      return false;
    }

    /**
     * Adds the required CSS-Classes
     * 
     * @param svgp SVG-Plot
     */
    protected void addCSSClasses(SVGPlot svgp) {
      // Class for the range marking
      if(!svgp.getCSSClassManager().contains(CSS_RANGEMARKER)) {
        final CSSClass rcls = new CSSClass(this, CSS_RANGEMARKER);
        final StyleLibrary style = context.getStyleResult().getStyleLibrary();
        rcls.setStatement(SVGConstants.CSS_FILL_PROPERTY, style.getColor(StyleLibrary.SELECTION_ACTIVE));
        rcls.setStatement(SVGConstants.CSS_OPACITY_PROPERTY, style.getOpacity(StyleLibrary.SELECTION_ACTIVE));
        svgp.addCSSClassOrLogError(rcls);
      }
    }
  }
}