package com.goebl.simplify;

/**
 * Helper to get X and Y coordinates from a foreign class T.
 *
 * @author hgoebl
 * @since 06.07.13
 */
public interface PointExtractor<T> {
    double getX(T point);
    double getY(T point);
}
