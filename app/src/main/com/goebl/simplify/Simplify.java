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

/**
 * Simplification of a 2D-polyline.
 *
 * @author hgoebl
 * @since 06.07.13
 */
public class Simplify<T> extends AbstractSimplify<T> {

    private final PointExtractor<T> pointExtractor;

    /**
     * Simple constructor for 2D-Simplifier.
     * <br>
     * With this simple constructor your array elements must implement {@link Point}.<br>
     * If you have coordinate classes which cannot be changed to implement <tt>Point</tt>, use
     * {@link #Simplify(Object[], PointExtractor)} constructor!
     *
     * @param sampleArray pass just an empty array (<tt>new MyPoint[0]</tt>) - necessary for type consistency.
     */
    public Simplify(T[] sampleArray) {
        super(sampleArray);
        this.pointExtractor = new PointExtractor<T>() {
            @Override
            public double getX(T point) {
                return ((Point) point).getX();
            }

            @Override
            public double getY(T point) {
                return ((Point) point).getY();
            }
        };
    }

    /**
     * Alternative constructor for 2D-Simplifier.
     * <br>
     * With this constructor your array elements do not have to implement a special interface like {@link Point}.<br>
     * Implement a {@link PointExtractor} to give <tt>Simplify</tt> access to your coordinates.
     *
     * @param sampleArray pass just an empty array (<tt>new MyPoint[0]</tt>) - necessary for type consistency.
     * @param pointExtractor your implementation to extract X and Y coordinates from you array elements.
     */
    public Simplify(T[] sampleArray, PointExtractor<T> pointExtractor) {
        super(sampleArray);
        this.pointExtractor = pointExtractor;
    }

    @Override
    public double getSquareDistance(T p1, T p2) {

        double dx = pointExtractor.getX(p1) - pointExtractor.getX(p2);
        double dy = pointExtractor.getY(p1) - pointExtractor.getY(p2);

        return dx * dx + dy * dy;
    }

    @Override
    public double getSquareSegmentDistance(T p0, T p1, T p2) {
        double x0, y0, x1, y1, x2, y2, dx, dy, t;

        x1 = pointExtractor.getX(p1);
        y1 = pointExtractor.getY(p1);
        x2 = pointExtractor.getX(p2);
        y2 = pointExtractor.getY(p2);
        x0 = pointExtractor.getX(p0);
        y0 = pointExtractor.getY(p0);

        dx = x2 - x1;
        dy = y2 - y1;

        if (dx != 0.0d || dy != 0.0d) {
            t = ((x0 - x1) * dx + (y0 - y1) * dy)
                    / (dx * dx + dy * dy);

            if (t > 1.0d) {
                x1 = x2;
                y1 = y2;
            } else if (t > 0.0d) {
                x1 += dx * t;
                y1 += dy * t;
            }
        }

        dx = x0 - x1;
        dy = y0 - y1;

        return dx * dx + dy * dy;
    }
}
