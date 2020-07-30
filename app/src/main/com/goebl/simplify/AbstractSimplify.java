/*
The MIT License (MIT)

Copyright (c) 2013 Heinrich Göbl

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/

/*
 * Source retrieved from: https://github.com/hgoebl/simplify-java
 * No changes made.
 */

package com.goebl.simplify;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Abstract base class for simplification of a polyline.
 *
 * @author hgoebl
 * @since 06.07.13
 */
abstract class AbstractSimplify<T> {

    private T[] sampleArray;

    protected AbstractSimplify(T[] sampleArray) {
        this.sampleArray = sampleArray;
    }

    /**
     * Simplifies a list of points to a shorter list of points.
     * @param points original list of points
     * @param tolerance tolerance in the same measurement as the point coordinates
     * @param highestQuality <tt>true</tt> for using Douglas-Peucker only,
     *                       <tt>false</tt> for using Radial-Distance algorithm before
     *                       applying Douglas-Peucker (should be a bit faster)
     * @return simplified list of points
     */
    public T[] simplify(T[] points,
                        double tolerance,
                        boolean highestQuality) {

        if (points == null || points.length <= 2) {
            return points;
        }

        double sqTolerance = tolerance * tolerance;

        if (!highestQuality) {
            points = simplifyRadialDistance(points, sqTolerance);
        }

        points = simplifyDouglasPeucker(points, sqTolerance);

        return points;
    }

    T[] simplifyRadialDistance(T[] points, double sqTolerance) {
        T point = null;
        T prevPoint = points[0];

        List<T> newPoints = new ArrayList<>();
        newPoints.add(prevPoint);

        for (int i = 1; i < points.length; ++i) {
            point = points[i];

            if (getSquareDistance(point, prevPoint) > sqTolerance) {
                newPoints.add(point);
                prevPoint = point;
            }
        }

        if (prevPoint != point) {
            newPoints.add(point);
        }

        return newPoints.toArray(sampleArray);
    }

    private static class Range {
        private Range(int first, int last) {
            this.first = first;
            this.last = last;
        }

        int first;
        int last;
    }

    T[] simplifyDouglasPeucker(T[] points, double sqTolerance) {

        BitSet bitSet = new BitSet(points.length);
        bitSet.set(0);
        bitSet.set(points.length - 1);

        List<Range> stack = new ArrayList<>();
        stack.add(new Range(0, points.length - 1));

        while (!stack.isEmpty()) {
            Range range = stack.remove(stack.size() - 1);

            int index = -1;
            double maxSqDist = 0f;

            // find index of point with maximum square distance from first and last point
            for (int i = range.first + 1; i < range.last; ++i) {
                double sqDist = getSquareSegmentDistance(points[i], points[range.first], points[range.last]);

                if (sqDist > maxSqDist) {
                    index = i;
                    maxSqDist = sqDist;
                }
            }

            if (maxSqDist > sqTolerance) {
                bitSet.set(index);

                stack.add(new Range(range.first, index));
                stack.add(new Range(index, range.last));
            }
        }

        List<T> newPoints = new ArrayList<>(bitSet.cardinality());
        for (int index = bitSet.nextSetBit(0); index >= 0; index = bitSet.nextSetBit(index + 1)) {
            newPoints.add(points[index]);
        }

        return newPoints.toArray(sampleArray);
    }


    public abstract double getSquareDistance(T p1, T p2);

    public abstract double getSquareSegmentDistance(T p0, T p1, T p2);
}
