
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

package com.vividsolutions.jump.util;

import java.util.*;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.util.Assert;


/**
 * Some utility functions for handling Coordinate arrays.
 */
public class CoordinateArrays {

    //<<TODO:REFACTORING>> JTS already has a class named CoordinateArrays.
    //I wonder if we should collapse this class into that one. [Jon Aquino]
    // MD - yep, at some point.
    // [mmichaud - 2016] some methods have not yet been integrated into
    // JTS CoordinateArrays
    private static final Coordinate[] coordArrayType = new Coordinate[0];

    public static Coordinate[] toCoordinateArray(List<Coordinate[]> coordList) {
        return coordList.toArray(coordArrayType);
    }

    /**
     * Converts an array of coordinates to a line or point, as appropriate.
     * @param coords the coordinates of a line or point
     * @param fact a factory used to create the Geometry
     * @return a line if there is more than one coordinate; a point if there is
     * just one coordinate; an empty point otherwise
     */
    public static Geometry toLineOrPoint(Coordinate[] coords,
        GeometryFactory fact) {
        if (coords.length > 1) {
            return fact.createLineString(coords);
        }

        if (coords.length == 1) {
            return fact.createPoint(coords[0]);
        }

        return fact.createPoint((Coordinate)null);
    }

    public static boolean equals(Coordinate[] coord1, Coordinate[] coord2) {
        return org.locationtech.jts.geom.CoordinateArrays.equals(coord1, coord2);
    }

    /**
     * Converts a collection of coordinate arrays to a collection of geometries.
     * @param coordArrays a collection of Coordinate[]
     * @param fact a factory used to create the Geometries
     * @return a collection of LineStrings and Points
     */
    public static List<Geometry> fromCoordinateArrays(
            List<Coordinate[]> coordArrays, GeometryFactory fact) {
        List<Geometry> geomList = new ArrayList<>();

        for (Coordinate[] coordArray : coordArrays) {
            Geometry geom = toLineOrPoint(coordArray, fact);
            geomList.add(geom);
        }

        return geomList;
    }

    /**
     * Extract the coordinate arrays for a geometry into a List.
     * @param g the Geometry to extract from
     * @param coordArrayList the List to add the coordinate arrays to
     * @param orientPolygons whether or not the arrays in the List should be
     * oriented (clockwise for the shell, counterclockwise for the holes)
     */
    public static void addCoordinateArrays(Geometry g, boolean orientPolygons,
        List<Coordinate[]> coordArrayList) {
        if (g.getDimension() <= 0) {
            return;
        } else if (g instanceof LineString) {
            LineString l = (LineString) g;
            coordArrayList.add(l.getCoordinates());
        } else if (g instanceof Polygon) {
            Polygon poly = (Polygon) g;
            Coordinate[] shell = poly.getExteriorRing().getCoordinates();

            if (orientPolygons) {
                shell = ensureOrientation(shell, Orientation.CLOCKWISE);
            }

            coordArrayList.add(shell);

            for (int i = 0; i < poly.getNumInteriorRing(); i++) {
                Coordinate[] hole = poly.getInteriorRingN(i).getCoordinates();

                if (orientPolygons) {
                    hole = ensureOrientation(hole, Orientation.COUNTERCLOCKWISE);
                }

                coordArrayList.add(hole);
            }
        } else if (g instanceof GeometryCollection) {
            GeometryCollection gc = (GeometryCollection) g;

            for (int i = 0; i < gc.getNumGeometries(); i++) {
                addCoordinateArrays(gc.getGeometryN(i), orientPolygons,
                    coordArrayList);
            }
        } else {
            Assert.shouldNeverReachHere("Geometry of type " +
                g.getClass().getName() + " not handled");
        }
    }

    /**
     * Sets the orientation of an array of coordinates.
     * @param coord the coordinates to inspect
     * @param desiredOrientation CGAlgorithms.CLOCKWISE or CGAlgorithms.COUNTERCLOCKWISE
     * @return a new array with entries in reverse order, if the orientation is
     * incorrect; otherwise, the original array
     */
    public static Coordinate[] ensureOrientation(Coordinate[] coord, int desiredOrientation) {
        if (coord.length == 0) {
            return coord;
        }

        int orientation = Orientation.isCCW(coord) ?
                Orientation.COUNTERCLOCKWISE : Orientation.CLOCKWISE;

        if (orientation != desiredOrientation) {
            Coordinate[] reverse = coord.clone();
            org.locationtech.jts.geom.CoordinateArrays.reverse(reverse);

            return reverse;
        }

        return coord;
    }

    /**
     * Extract the coordinate arrays for a geometry.
     * Polygons will be checked to ensure their rings are oriented correctly.
     * Note: coordinates from Points or MultiPoints will not be extracted.
     * @param g the Geometry to extract from
     * @param orientPolygons ensure that Polygons are correctly oriented
     * @return a list of Coordinate[]
     */
    public static List<Coordinate[]> toCoordinateArrays(Geometry g, boolean orientPolygons) {
        List<Coordinate[]> coordArrayList = new ArrayList<>();
        addCoordinateArrays(g, orientPolygons, coordArrayList);

        return coordArrayList;
    }
}
