/*
 * The Unified Mapping Platform (JUMP) is an extensible, interactive GUI
 * for visualizing and manipulating spatial features with geometry and attributes.
 *
 * Copyright (C) 2003 Vivid Solutions
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * For more information, contact:
 *
 * Vivid Solutions
 * Suite #1A
 * 2328 Government Street
 * Victoria BC  V8T 5G5
 * Canada
 *
 * (250)385-6040
 * www.vividsolutions.com
 */

package com.vividsolutions.jump.workbench.ui.renderer.style;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;

import javax.swing.Icon;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import com.vividsolutions.jump.workbench.ui.Viewport;

/**
 * Base class for styles painting linestring vertices.
 *
 * @author Martin Davis
 * @version 1.0
 */
public abstract class LineStringVertexStyle extends LineStringStyle implements ChoosableStyle {

    protected String name;

    protected Icon icon;

    public LineStringVertexStyle(String name, Icon icon) {
        super(name, icon);
        this.name = name;
        this.icon = icon;
    }

    protected void paintLineString(LineString lineString, Viewport viewport,
        Graphics2D graphics) throws Exception
    {
      int numPtsToRender = lineString.getNumPoints();
      // don't paint end vertex twice
      if (lineString.isClosed())
        numPtsToRender--;
      for (int i = 0; i < numPtsToRender; i++) {
        Coordinate p = lineString.getCoordinateN(i);
        paint(viewport.toViewPoint(new Point2D.Double(p.x, p.y)),
              lineString, i,
              viewport, graphics);
      }
    }

    protected abstract void paint(Point2D p0, LineString line, int index,
        Viewport viewport, Graphics2D graphics) throws Exception;

    public String getName() {
        return name;
    }

    public Icon getIcon() {
        return icon;
    }
}
