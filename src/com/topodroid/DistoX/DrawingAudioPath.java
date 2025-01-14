/* @file DrawingAudioPath.java
 *
 * @author marco corvi
 * @date june 2017
 *
 * @brief TopoDroid drawing: audio point
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;

// import android.graphics.Canvas;
// import android.graphics.Paint;
// import android.graphics.Path;
// import android.graphics.RectF;
// import android.graphics.Matrix;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

// import android.util.Log;
import android.util.Base64;

/**
 */
class DrawingAudioPath extends DrawingPointPath
{
  long mId;
  // private Paint paint;
  
  // FIXME-COPYPATH
  // @Override
  // DrawingPath copyPath()
  // {
  //   DrawingAudioPath ret = new DrawingAudioPath( cx, cy, mScale, mOptions, mId );
  //   copyTo( ret );
  //   return ret;
  // }

  DrawingAudioPath( float off_x, float off_y, int scale, String options, long id )
  {
    super( BrushManager.getPointAudioIndex(), off_x, off_y, scale, null, options );
    mId = id;

    // mPointText = text;
    // setPaint( BrushManager.pointPaint[ BrushManager.POINT_LABEL ] );
    // mPaint = BrushManager.pointPaint[ BrushManager.POINT_LABEL ];
    // paint = new Paint();
    // paint.setDither(true);
    // paint.setColor( 0xffffffff );
    // paint.setStyle(Paint.Style.STROKE);
    // paint.setStrokeJoin(Paint.Join.ROUND);
    // paint.setStrokeCap(Paint.Cap.ROUND);
    // paint.setStrokeWidth( WIDTH_CURRENT );
  }

  static DrawingAudioPath loadDataStream( int version, DataInputStream dis, float x, float y )
  {
    // int type;
    try {
      float orientation = 0;
      float ccx = x + dis.readFloat( );
      float ccy = y + dis.readFloat( );
      // String th_name = dis.readUTF( );
      // type = BrushManager.getPointLabelIndex();
      // String group = ( version >= 401147 )? dis.readUTF() : ""; // audio has no group
      if ( version > 207043 ) orientation = dis.readFloat( ); // audio-point have no orientation
      int scale = dis.readInt( );
      int lvl = ( version >= 401090 )? dis.readInt() : DrawingLevel.LEVEL_DEFAULT;
      String text = dis.readUTF(); // audio-point does not have text (not used)
      String options = dis.readUTF();
      int id = dis.readInt();

      // TDLog.Log( TDLog.LOG_PLOT, "Label <" + text + " " + ccx + " " + ccy + " scale " + scale + " (" + options + ")" );
      DrawingAudioPath ret = new DrawingAudioPath( ccx, ccy, scale, options, id );
      ret.mLevel = lvl;
      ret.setOrientation( orientation );
      return ret;
    } catch ( IOException e ) {
      TDLog.Error( "LABEL in error " + e.getMessage() );
      // Log.v("DistoX", "LABEL in error " + e.getMessage() );
    }
    return null;
  }

  @Override
  String toTherion( )
  {
    StringWriter sw = new StringWriter();
    PrintWriter pw  = new PrintWriter(sw);
    pw.format(Locale.US, "point %.2f %.2f audio -text \"%s\" -audio \"%d.wav\" ",
         cx*TDSetting.mToTherion, -cy*TDSetting.mToTherion, ((mPointText==null)?"":mPointText), (int)mId );
    toTherionOrientation( pw );
    toTherionOptions( pw );
    pw.format("\n");
    return sw.getBuffer().toString();
  }

  // FIXME_SYNC might be a problem with a long audio recording, but the method is called on export, 
  // which runs on asynch task 
  @Override
  void toCsurvey( PrintWriter pw, String survey, String cave, String branch, String bind /* , DrawingUtil mDrawingUtil */ )
  { 
    // Log.v("DistoX", "audio point " + mId + " survey " + survey );
    File audiofile = new File( TDPath.getSurveyAudioFile( survey, Long.toString( mId ) ) );
    if ( audiofile.exists() ) {
      byte[] buf = TDExporter.readFileBytes( audiofile );
      if ( buf != null ) {
        pw.format("<item layer=\"6\" cave=\"%s\" branch=\"%s\" type=\"12\" category=\"80\" transparency=\"0.00\"",
          cave, branch );
        pw.format(" text=\"%s\" textrotatemode=\"1\" >\n", ((mPointText==null)?"":mPointText) );
        if ( bind != null ) pw.format(" bind=\"%s\"", bind );
        // pw.format("  <pen type=\"10\" />\n");
        // pw.format("  <brush type=\"7\" />\n");
        pw.format(" <attachment dataformat\"0\" data=\"%s\" name=\"\" note=\"\" type=\"audio/x-wav\" />\n", 
          Base64.encodeToString( buf, Base64.NO_WRAP ) );
        float x = DrawingUtil.sceneToWorldX( cx, cy ); // convert to world coords.
        float y = DrawingUtil.sceneToWorldY( cx, cy );
        pw.format(Locale.US, " <points data=\"%.2f %.2f \" />\n", x, y );
        // pw.format("  <font type=\"0\" />\n");
        pw.format("</item>\n");
      }
    }
  }

  @Override
  void toDataStream( DataOutputStream dos )
  {
    try {
      dos.write( 'Z' );
      dos.writeFloat( cx );
      dos.writeFloat( cy );
      // dos.writeUTF( BrushManager.mPointLib.getSymbolThName(mPointType) );
      // if ( version >= 401147 ) dos.writeUTF( (group != null)? group : "" ); // audio has no group
      dos.writeFloat( (float)mOrientation ); // from version 2.7.4e
      dos.writeInt( mScale );
      // if ( version >= 401090 )
        dos.writeInt( mLevel );
      dos.writeUTF( ( mPointText != null )? mPointText : "" ); // audio-point does not have text
      dos.writeUTF( ( mOptions != null )? mOptions : "" );
      dos.writeInt( ((int)mId) );
      // TDLog.Log( TDLog.LOG_PLOT, "T " + " " + cx + " " + cy );
    } catch ( IOException e ) {
      TDLog.Error( "AUDIO out error " + e.toString() );
    }
  }
}


