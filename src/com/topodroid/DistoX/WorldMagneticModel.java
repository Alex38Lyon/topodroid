/* @file WorldMagneticModel.java
 *
 * @author marco corvi
 * @date nov 2011
 *
 * @brief TopoDroid World Magnetic Model 
 * --------------------------------------------------------
 *  Copyright This sowftare is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 * Implemented after GeomagneticLibrary.c by
 *  National Geophysical Data Center
 *  NOAA EGC/2
 *  325 Broadway
 *  Boulder, CO 80303 USA
 *  Attn: Susan McLean
 *  Phone:  (303) 497-6478
 *  Email:  Susan.McLean@noaa.gov
 */
package com.topodroid.DistoX;
/*--------------------------------------------------------------------------*/

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

import android.content.Context;

class WorldMagneticModel
{
  int nMax;
  int numTerms;
  MagModel mModel;
  static MagDate  mStartEpoch = null;
  static float    mGeoidHeightBuffer[] = null;
  static WMMcoeff mWmmCoeff[] = null;
  MagEllipsoid mEllip;
  MagGeoid     mGeoid;

  WorldMagneticModel( Context context )
  {
    nMax = 12;
    numTerms = MagUtil.CALCULATE_NUMTERMS( nMax );
    loadWMM( context, numTerms );
    loadEGM9615( context );

    mModel = new MagModel( numTerms, nMax, nMax );
    mModel.epoch = 2015.0;
    mModel.CoefficientFileEndDate = mModel.epoch + 5;
    mModel.setCoeffs( mWmmCoeff );
    mEllip = new MagEllipsoid(); // default values
    mGeoid = new MagGeoid( mGeoidHeightBuffer );
  }

  MagElement computeMagElement( double latitude, double longitude, double height, int year, int month, int day )
  {
    MagDate date = new MagDate( year, month, day );
    return doComputeMagElement( latitude, longitude, height, date );
  }

  MagElement computeMagElement( double latitude, double longitude, double height, double dec_year )
  {
    MagDate date = new MagDate( dec_year );
    return doComputeMagElement( latitude, longitude, height, date );
  }

  // height [M]
  double geoidToEllipsoid( double latitude, double longitude, double height )
  {
    MagGeodetic geodetic = new MagGeodetic();
    geodetic.phi    = latitude;  // dec degree
    geodetic.lambda = longitude; // dec degree
    geodetic.HeightAboveGeoid = height / 1000; // KM
    geodetic.HeightAboveEllipsoid = -9999;
    mGeoid.convertGeoidToEllipsoidHeight( geodetic );
    return geodetic.HeightAboveEllipsoid * 1000; // M
  }

  // height [M]
  double ellipsoidToGeoid( double latitude, double longitude, double height )
  {
    MagGeodetic geodetic = new MagGeodetic();
    geodetic.phi    = latitude;  // dec degree
    geodetic.lambda = longitude; // dec degree
    geodetic.HeightAboveGeoid = -9999;
    geodetic.HeightAboveEllipsoid = height / 1000; // KM
    mGeoid.convertEllipsoidToGeoidHeight( geodetic ); 
    return geodetic.HeightAboveGeoid * 1000; // M
  }

  // ============================================================================
  
  // height = ellipsoid height [M]
  private MagElement doComputeMagElement( double latitude, double longitude, double height, MagDate date )
  {
    MagGeodetic geodetic = new MagGeodetic();
    geodetic.phi    = latitude;  // dec degree
    geodetic.lambda = longitude; // dec degree
    geodetic.HeightAboveEllipsoid = height / 1000; // KM
    geodetic.HeightAboveGeoid = height / 1000;
    // geodetic.HeightAboveGeoid = -9999;
    // mGeoid.convertEllipsoidToGeoidHeight( geodetic ); // FIXME

    MagSpherical spherical = mEllip.geodeticToSpherical( geodetic ); // geodetic to Spherical Eqs. 17-18 
    MagModel timedModel    = mModel.getTimelyModifyModel( date );

    GeomagLib geomag = new GeomagLib();

    /* Computes the geoMagnetic field elements and their time change*/
    MagElement elems = geomag.MAG_Geomag( mEllip, spherical, geodetic, timedModel );
    geomag.calculateGridVariation( geodetic, elems );
    // MagElement errors = MagUtil.getWMMErrorCalc( elems.H );
    return elems;
  }

  // public static void main( String[] argv )
  // {
  //   WorldMagneticModel WMM = new WorldMagneticModel();
  //   // System.out.println("Ready");
  //   try {
  //     FileReader fr = new FileReader( "sample_coords.txt" );
  //     BufferedReader br = new BufferedReader( fr );
  //     String line;
  //     while( true ) {
  //       line = br.readLine();
  //       if ( line == null ) break;
  //       // line = line.trim();
  //       // System.out.println("Line " + line );
  //       String[] vals = line.split(" ");
  //       double date = Double.parseDouble( vals[0] );
  //       // System.out.println("Date " + date );
  //       // vals[1] coord system
  //       // System.out.println("Coords " + vals[1] );
  //       // System.out.println("Alt. " + vals[2] );
  //       char unit = vals[2].charAt(0);
  //       double f = 1.0;
  //       if ( unit == 'M' ) { f= 0.0001; }
  //       if ( unit == 'F' ) { f= 0.0003048; }
  //       double alt = f * Double.parseDouble( vals[2].substring(1) );
  //       double lat = Double.parseDouble( vals[3] );
  //       double lng = Double.parseDouble( vals[4] );
  //       // System.out.println("Compute " + date + " " + lat + " " + lng + " " + alt );
  //       MagElement elems = WMM.computeMagElement( lat, lng, alt, date );
  //       elems.dump();
  //     }
  //     fr.close();
  //   } catch ( IOException e ) { }
  // }

  // --------------------------------------------------

  private static int byteToInt( byte[] bval )
  {
    int i0 = (int)(bval[0]); if ( i0 < 0 ) i0 = 256 + i0;
    int i1 = (int)(bval[1]); if ( i1 < 0 ) i1 = 256 + i1;
    int i2 = (int)(bval[2]); if ( i2 < 0 ) i2 = 256 + i2;
    int i3 = (int)(bval[3]); if ( i3 < 0 ) i3 = 256 + i3;
    // System.out.println( "Bytes " + bval[0] + " " + bval[1] + " " + bval[2] + " " + bval[3] );
    // System.out.println( "Ints " + i0 + " " + i1 + " " + i2 + " " + i3 );
    // return (i0 | (i1<<8) | (i2<<16) | (i3<<24));
    return (((i3*256 + i2)*256 + i1)*256 + i0);
  }


  private static void loadEGM9615( Context context )
  {
    if ( mGeoidHeightBuffer != null ) return;
    try {
      byte[] bval = new byte[4];
      DataInputStream fis = new DataInputStream( context.getAssets().open( "wmm/egm9615" ) );
      int N = 1038961;
      mGeoidHeightBuffer = new float[ (int)N ];
      for ( int k=0; k < N; ++k ) {
        fis.read( bval );
        int ival = byteToInt( bval );
	float val = ival / 1000.0f;
	mGeoidHeightBuffer[k] = val;
      }
      fis.close();
    } catch ( IOException e ) {
      // TODO 
    }
    // System.out.println("loaded EGM9615");
  }

  private static void loadWMM( Context context, int num_terms )
  {
    if ( mWmmCoeff != null ) return;
    mWmmCoeff = new WMMcoeff[ num_terms ];
    for ( int k=0; k<num_terms; ++k ) mWmmCoeff[k] = null;
    
    try {
      InputStreamReader fr = new InputStreamReader( context.getAssets().open( "wmm/wmm.cof" ) );
      BufferedReader br = new BufferedReader( fr );
      String line = br.readLine().trim();
      String[] vals = line.split(" ");
      float start = Float.parseFloat( vals[0] );
      // System.out.println("Start Epoch " + start );
      mStartEpoch = new MagDate( start );
      for ( ; ; ) {
        line = br.readLine().trim();
        if ( line.startsWith("99999") ) break;
	vals = line.split(" ");
	int j = 0; while ( vals[j].length() == 0 ) ++j;
	int n = Integer.parseInt( vals[j] );
	++j; while ( vals[j].length() == 0 ) ++j;
	int m = Integer.parseInt( vals[j] );
	++j; while ( vals[j].length() == 0 ) ++j;
	float v0 = Float.parseFloat( vals[j] );
	++j; while ( vals[j].length() == 0 ) ++j;
	float v1 = Float.parseFloat( vals[j] );
        ++j; while ( vals[j].length() == 0 ) ++j;
	float v2 = Float.parseFloat( vals[j] );
        ++j; while ( vals[j].length() == 0 ) ++j;
	float v3 = Float.parseFloat( vals[j] );
        int index = WMMcoeff.index( n, m );
        // System.out.println(" N,M " + n + " " + m + " " + v0 + " " + v1 );
	mWmmCoeff[index] = new WMMcoeff( n, m, v0, v1, v2, v3 );
      }
      fr.close();
    } catch( IOException e ) {
      // TODO 
    }
    // System.out.println("loaded WMM");
  }
}
