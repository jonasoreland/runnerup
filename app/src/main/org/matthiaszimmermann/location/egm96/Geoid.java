package org.matthiaszimmermann.location.egm96;

import org.matthiaszimmermann.location.Location;

import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * offline <a href="https://en.wikipedia.org/wiki/Geoid">geoid</a> implementation based on the data provided 
 * by the <a href="http://earth-info.nga.mil/GandG/wgs84/gravitymod/egm96/intpt.html">online caluclator</a>.
 * 
 * @author matthiaszimmermann
 *
 */
public class Geoid {

	private static final short OFFSET_INVALID = -0x8000;

	private static final int ROWS = 719;  // (89.75 + 89.75)/0.25 + 1 = 719
	private static final int COLS = 1440; // 359.75/0.25 + 1 = 1440

	private static final double LATITUDE_MAX = 90.0;
	private static final double LATITUDE_MAX_GRID = 89.74;
	private static final double LATITUDE_ROW_FIRST = 89.50;
	private static final double LATITUDE_ROW_LAST = -89.50;
	private static final double LATITUDE_MIN_GRID = -89.74;
	private static final double LATITUDE_MIN = -90.0;
	public static final double LATITUDE_STEP = 0.25;

	private static final double LONGITIDE_MIN = 0.0;
	private static final double LONGITIDE_MIN_GRID = 0.0;
	private static final double LONGITIDE_MAX_GRID = 359.75;
	private static final double LONGITIDE_MAX = 360.0;
	private static final double LONGITIDE_STEP = 0.25;

	//Store in 'fixed point format' 16-bit short (in 1/100m (cm)) instead of 64-bit double
	private static final short [][] offset = new short[ROWS][COLS];
	private static short offset_north_pole = 0;
	private static short offset_south_pole = 0;
	private static boolean s_model_ok = false;

	@SuppressWarnings("UnusedReturnValue")
	public static boolean init(InputStream is) {
		if(s_model_ok) {
			return true;
		}

		try {

			s_model_ok = readGeoidOffsetsD(new BufferedInputStream(is));
		}
		catch (Exception e) {
			s_model_ok = false;
			System.err.println("failed to read stream "+e);
		}

		return s_model_ok;
	}

	public static double getOffset(double lat, double lon) {
        Location location = new Location(lat, lon);
        return getOffset(location);
    }

    private static double getOffset(Location location) {
		double lat = location.getLatitude();
		double lng = location.getLongitude();
		
		// special case for exact grid positions
		if(latIsGridPoint(lat) && lngIsGridPoint(lng)) {
			return getGridOffset(lat, lng);
		}
		
		Location [][] q = new Location[4][4];
		
		// get four grid locations surrounding the target location
		// used for bilinear interpolation
		q[1][1] = getGridFloorLocation(lat, lng);
		q[1][2] = getUpperLocation(q[1][1]);
		q[2][1] = getRightLocation(q[1][1]);
		q[2][2] = getUpperLocation(q[2][1]);
		
		// check if we can get points for bicubic interpolation
		if(q[1][1].getLatitude() >= LATITUDE_MIN_GRID && q[1][2].getLatitude() <= LATITUDE_MAX_GRID) {
			// left column
			q[0][1] = getLeftLocation(q[1][1]);
			q[0][2] = getUpperLocation(q[0][1]);
			q[0][3] = getUpperLocation(q[0][2]);
			
			// top row
			q[1][3] = getRightLocation(q[0][3]);
			q[2][3] = getRightLocation(q[1][3]);
			q[2][3] = getRightLocation(q[1][3]);
			q[3][3] = getRightLocation(q[2][3]);
			
			// bottom row
			q[0][0] = getLowerLocation(q[0][1]);
			q[1][0] = getRightLocation(q[0][0]);
			q[1][0] = getRightLocation(q[0][0]);
			q[2][0] = getRightLocation(q[1][0]);

			// right column
			q[3][0] = getRightLocation(q[2][0]);
			q[3][1] = getUpperLocation(q[3][0]);
			q[3][2] = getUpperLocation(q[3][1]);
						
//			return bilinearInterpolation(location, q[1][1], q[1][2], q[2][1], q[2][2]);
			return bicubicSplineInterpolation(location, q);
			
		}
		else {
			return bilinearInterpolation(location, q[1][1], q[1][2], q[2][1], q[2][2]);
		}
	}
	
	/**
	 * bilinearInterpolation according to description on wikipedia
	 * @see <a href="https://en.wikipedia.org/wiki/Bilinear_interpolation">wikipedia Bilinear_interpolation</a>
	 * @return  the lineary interpolated value
	 */
	private static double bilinearInterpolation(Location target, Location q11, Location q12, Location q21, Location q22) {
		double fq11 = getGridOffset(q11); // lower left
		double fq12 = getGridOffset(q12); // upper left
		double fq21 = getGridOffset(q21); // lower right
		double fq22 = getGridOffset(q22); // upper right
		
		double x1 = q11.getLongitude();
		double x2 = q22.getLongitude();
		double y1 = q22.getLatitude();
		double y2 = q11.getLatitude();
		
		// special case for latitude moving from 359.75 -> 0
		if(x1 == 359.75 && x2 == 0.0) {
			x2 = 360.0;
		}
		
		double x = target.getLongitude();
		double y = target.getLatitude();
		
		double f11 = fq11 * (x2 - x) * (y2 - y);
		double f12 = fq12 * (x2 - x) * (y - y1);
		double f21 = fq21 * (x - x1) * (y2 - y);
		double f22 = fq22 * (x - x1) * (y - y1);
		
		return (f11 + f12 + f21 + f22) / ((x2 - x1) * (y2 - y1));
	}
	
	/**
	 * Bicubic spline: If you provide a 4x4 grid of values for geometric quantities in u and v, 
	 * this class creates an object that will interpolate a Bicubic spline to give you the value 
	 * within any point of a unit tile in (u,v) space.
	 * If you want to create a spline surface, you can make a two dimensional array of such objects.
	 * 
	 * @see <a href="http://mrl.nyu.edu/~perlin/cubic/Cubic_java.html">Gubic</a>
	 * @return bicubic spline
	 */	
    private static double bicubicSplineInterpolation(Location target, Location[][] grid) {
		double[][] G = new double [4][4];

		for(int i = 0; i < 4; i++) {
			for(int j = 0; j < 4; j++) {
				G[i][j] = getGridOffset(grid[i][j]);
			}
		}
		
		double u1 = grid[1][1].getLatitude();
		double v1 = grid[1][1].getLongitude();
				
		double u = (target.getLatitude() - u1 + LATITUDE_STEP) / (4 * LATITUDE_STEP);
		double v = (target.getLongitude() - v1 + LONGITIDE_STEP) / (4 * LONGITIDE_STEP);
		Cubic c = new Cubic(G);
		
    	return c.eval(u, v);
    }	
    
	private static Location getUpperLocation(Location location) {
		double lat = location.getLatitude();
		double lng = location.getLongitude();
		
		if(lat == LATITUDE_MAX_GRID) {
			lat = LATITUDE_MAX;
		}
		else if(lat == LATITUDE_ROW_FIRST) {
			lat = LATITUDE_MAX_GRID;
		}
		else if(lat == LATITUDE_MIN) {
			lat = LATITUDE_MIN_GRID;
		}
		else if(lat == LATITUDE_MIN_GRID) {
			lat = LATITUDE_ROW_LAST;
		}
		else {
			lat += LATITUDE_STEP;
		}
		
		return new Location(lat, lng);
	}
	
	private static Location getLowerLocation(Location location) {
		double lat = location.getLatitude();
		double lng = location.getLongitude();
		
		if(lat == LATITUDE_MIN_GRID) {
			lat = LATITUDE_MIN;
		}
		else if(lat == LATITUDE_ROW_FIRST) {
			lat = LATITUDE_MIN_GRID;
		}
		else if(lat == LATITUDE_MAX) {
			lat = LATITUDE_MAX_GRID;
		}
		else if(lat == LATITUDE_MAX_GRID) {
			lat = LATITUDE_ROW_FIRST;
		}
		else {
			lat -= LATITUDE_STEP;
		}
		
		return new Location(lat, lng);
	}
	
	private static Location getLeftLocation(Location location) {
		double lat = location.getLatitude();
		double lng = location.getLongitude();
		
		return new Location(lat, lng - LATITUDE_STEP);
	}
	
	private static Location getRightLocation(Location location) {
		double lat = location.getLatitude();
		double lng = location.getLongitude();
		
		return new Location(lat, lng + LATITUDE_STEP);
	}
	
	private static Location getGridFloorLocation(double lat, double lng) {
		Location floor = (new Location(lat, lng)).floor();
		double latFloor = floor.getLatitude();
		
		if(lat >= LATITUDE_MAX_GRID && lat < LATITUDE_MAX) { 
			latFloor = LATITUDE_MAX_GRID; 
		}
		else if(lat < LATITUDE_MIN_GRID) {
			latFloor = LATITUDE_MIN; 
		}
		else if(lat < LATITUDE_ROW_LAST) {
			latFloor = LATITUDE_MIN_GRID; 
		}
		
		return new Location(latFloor, floor.getLongitude());
	}
	
	private static double getGridOffset(Location location) {
		return getGridOffset(location.getLatitude(), location.getLongitude());
	}

	private static double getGridOffset(double lat, double lng) {
		return getGridOffsetS(lat, lng)/100.0d;
	}
    private static short getGridOffsetS(double lat, double lng) {
		if(!s_model_ok) {
			return OFFSET_INVALID;
		}
		
		if(!latIsGridPoint(lat) || !lngIsGridPoint(lng)) {
			return OFFSET_INVALID;
		}
		
		if(latIsPole(lat)) {
			if(lat == LATITUDE_MAX) {
				return offset_north_pole;
			}
			else {
				return offset_south_pole;
			}
		}
		
		int i = latToI(lat);
		int j = lngToJ(lng);
		
		return offset[i][j];
	}

    //Get offsets from a definition file where the data is stored in a compressed format
	//The file is created from the standard file with the following snippet:
	//   cat /cygdrive/f/temp/EGM96complete.dat | perl -ne 'BEGIN{open F,">egm96-delta.dat";$e0=0;} \
	//; if(/^\s*([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)/){$e1=sprintf "%.0f", $3*100;$e=$e1-$e0; $e0=$e1; \
	//; if(-0x40<=$e && $e <0x40){$e+=0x40; print F pack "C",$e; \
	//; }elsif (-0x4000<=$e && $e<0x4000){$e+=0xc000; print F pack "n",$e;}else{die "offset out of bounds";}} \
	//; END{print F pack "n",0xc000-$e1; close F}'

	//Only the offset is stored, assuming coordinates are OK
	//Data is stored in 'fixed point', resolution 1/100m (cm)
	//The data is stored as difference to the previous value (as the values are correlated and can be fit in one byte normally)
	//If the difference is more than fit in 7 bits (+/-0.64m), two bytes are used
	//One byte data is stored with an offset of 64, so the first bit is never set
	//For two bytes, the data is stored as 0xc000+offset, so first bit is always set
	//Last, the south pole offset is added negatively, to get last offset as 0 (used as a check)

	private static boolean readGeoidOffsetsD(BufferedInputStream is) throws Exception {
		//BufferedReader _may_ increase the performance
		final byte[] buf = new byte[1000];
		int bufRead = 0;
		byte prevByte=0;
		int off = 0;
		int offsetCount = -1; //NorthPole is first
		boolean allRead = false;
		boolean prevIsTwo=false;
		do {
			int i = 0;
			while (i < bufRead) {
				byte c = buf[i];
				i++;
				if (prevIsTwo) {
					off += ((((prevByte&0xff) <<8) |(c&0xff))-0xc000);
					prevIsTwo=false;
				} else if ((c & 0x80) == 0) {
					off += ((c&0xff) - 0x40);
				} else {
					prevIsTwo = true;
				}
				prevByte=c;
				if (!prevIsTwo) {
					if (offsetCount < 0) {
						offset_north_pole = (short) off;
					} else if (offsetCount == ROWS * COLS) {
						offset_south_pole = (short) off;
					} else if (offsetCount == 1 + ROWS * COLS) {
						if (off == 0) {
							allRead = true;
						} else {
							System.err.println("Offset is not 0 at southpole "+offsetCount / COLS + " "+offsetCount % COLS + " "+off+" "+c);
						}
					} else if (offsetCount > ROWS * COLS) {
						//Should not occur
						allRead = false;
						System.err.println("Unexpected data "+offsetCount / COLS + " "+offsetCount % COLS + " "+off+" "+c);
					} else {
						offset[offsetCount / COLS][offsetCount % COLS] = (short) off;
					}
					offsetCount++;
				}
			}
			bufRead = is.read(buf);
		} while (bufRead > 0);

		return allRead;
	}

	private static boolean latOk(double lat) {
		return lat >= LATITUDE_MIN && lat <= LATITUDE_MAX;
	}
	
	@SuppressWarnings("unused")
	private static boolean lngOk(double lng) {
		return lng >= LONGITIDE_MIN && lng <= LONGITIDE_MAX;
	} 
	
	private static boolean lngOkGrid(double lng) {
		return lng >= LONGITIDE_MIN_GRID && lng <= LONGITIDE_MAX_GRID;
	}
	
	private static boolean latIsGridPoint(double lat) {
		return latOk(lat) && (latIsPole(lat) || lat == LATITUDE_MAX_GRID || lat == LATITUDE_MIN_GRID || lat <= LATITUDE_ROW_FIRST && lat >= LATITUDE_ROW_LAST && lat / LATITUDE_STEP == Math.round(lat / LATITUDE_STEP));

	}
	
	private static boolean lngIsGridPoint(double lng) {
		return lngOkGrid(lng) && lng / LONGITIDE_STEP == Math.round(lng / LONGITIDE_STEP);

	}
	
	private static boolean latIsPole(double lat) {
		return lat == LATITUDE_MAX || lat == LATITUDE_MIN; 
	}
	
	private static int latToI(double lat) {
		if(lat == LATITUDE_MAX_GRID) { return 0; }
		if(lat == LATITUDE_MIN_GRID) { return ROWS - 1; }
		else                         { return (int)((LATITUDE_ROW_FIRST - lat) / LATITUDE_STEP) + 1; }
	}
	
	private static int lngToJ(double lng) {
		return (int)(lng / LONGITIDE_STEP);
	}
	
}
