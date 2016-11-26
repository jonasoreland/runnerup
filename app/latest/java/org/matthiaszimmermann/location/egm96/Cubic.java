package org.matthiaszimmermann.location.egm96;

/**
 * @see <a href="http://mrl.nyu.edu/~perlin/cubic/Cubic_java.html">Java Cubic</a>
 * @author Ken Perlin
 *
 */
class Cubic
{
   private static final double[][] BEZIER = {      // Bezier basis matrix
      {-1  ,  3  , -3  , 1  },
      { 3  , -6  ,  3  , 0  },
      {-3  ,  3  ,  0  , 0  },
      { 1  ,  0  ,  0  , 0  } 
   };

   private final double[][] C = new double[4][4];    // bicubic coefficients matrix

   Cubic(double[][] G) {
      double[][] t = new double[4][4];
      for (int i = 0; i < 4 ; i++)    // T = G MT
      for (int j = 0 ; j < 4 ; j++)
      for (int k = 0 ; k < 4 ; k++)
	 t[i][j] += G[i][k] * Cubic.BEZIER[j][k];
      
      for (int i = 0 ; i < 4 ; i++)    // C = M T
      for (int j = 0 ; j < 4 ; j++)
      for (int k = 0 ; k < 4 ; k++)
	 C[i][j] += Cubic.BEZIER[i][k] * t[k][j];
   }

   private final double[] C3 = C[0];
   private final double[] C2 = C[1];
   private final double[] C1 = C[2];
   private final double[] C0 = C[3];

   double eval(double u, double v) {
      return u * (u * (u * (v * (v * (v * C3[0] + C3[1]) + C3[2]) + C3[3])
                         + (v * (v * (v * C2[0] + C2[1]) + C2[2]) + C2[3]))
                         + (v * (v * (v * C1[0] + C1[1]) + C1[2]) + C1[3]))
                         + (v * (v * (v * C0[0] + C0[1]) + C0[2]) + C0[3]);
   }
}
