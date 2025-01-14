/* @file DrawingCommandManager.java
 *
 * @author marco corvi
 * @date nov 2011
 *
 * @brief TopoDroid drawing: commands manager
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;

import android.util.Log;

import android.content.res.Configuration;
import android.app.Activity;
import android.os.Build;
// import android.os.Handler;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Bitmap;
import android.graphics.Point;
// import android.graphics.PorterDuff;
import android.graphics.PointF;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
// import android.graphics.Path.Direction;
import android.view.Display;
// import android.view.Surface;

// import java.util.Iterator;
import java.util.List;
// import java.util.Map;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.Locale;
import java.util.Collections;
import java.util.ArrayList;
import java.io.BufferedWriter;
// import java.io.StringWriter;
import java.io.PrintWriter;
// import java.io.FileReader;
// import java.io.FileOutputStream;
import java.io.DataOutputStream;
// import java.io.BufferedReader;
// import java.io.IOException;
// import java.io.EOFException;

// import java.util.Locale;

/**
 */
class DrawingCommandManager
{
  private static final int BORDER = 20;

  static private int mDisplayMode = DisplayMode.DISPLAY_PLOT; // this display mode is shared among command managers
  private RectF mBBox;
  boolean mIsExtended = false;

  private DrawingPath mNorthLine;
  private DrawingPath mFirstReference;
  private DrawingPath mSecondReference;

  final private List<DrawingPath>    mGridStack1;
  final private List<DrawingPath>    mGridStack10;
  final private List<DrawingPath>    mGridStack100;

  private DrawingScaleReference mScaleRef; /*[AR] this is the instance of scale reference line*/

  final private List<DrawingPath>        mLegsStack;
  final private List<DrawingPath>        mSplaysStack;
  final private List<ICanvasCommand>     mCurrentStack;
  final private List<DrawingStationPath> mUserStations;  // user-inserted stations
  final private List<ICanvasCommand>     mRedoStack;
  // private List<DrawingPath>     mHighlight;  // highlighted path
  final private List<DrawingStationName> mStations;  // survey stations
  final private List<DrawingLinePath>    mScrap;     // scrap outline
  private List<DrawingOutlinePath> mXSectionOutlines; // xsections outlines
  private int mMaxAreaIndex;                   // max index of areas in this plot

  private Selection mSelection;
  private SelectionSet mSelected;
  private boolean mDisplayPoints;

  // the current station is displayed green
  private DrawingStationName mCurrentStationName = null;

  private Matrix mMatrix;
  private float  mScale; // current zoom: value of 1 pl in scene space
  private boolean mLandscape = false;

  // PATH_MULTISELECT
  private int mMultiselectionType = -1;  // current multiselection type (DRAWING_PATH_POINT / LINE / AREA
  private List< DrawingPath > mMultiselected;
  boolean isMultiselection = false; 
  int getMultiselectionType() { return mMultiselectionType; }

  void resetMultiselection()
  {
    // Log.v("DistoX", "reset Multi Selection" );
    mMultiselectionType  = -1;
    isMultiselection = false;
    synchronized( TDPath.mSelectionLock ) { mMultiselected.clear(); }
  }

  void startMultiselection()
  {
    // resetMultiselection();
    if ( isMultiselection ) return; // false;
    SelectionPoint sp = mSelected.mHotItem;
    if ( sp == null ) return; // false;
    DrawingPath path = sp.mItem;
    if ( path == null ) return; // false;
    int type = path.mType;
    if ( type < DrawingPath.DRAWING_PATH_POINT || type > DrawingPath.DRAWING_PATH_AREA ) return; // false;
    mMultiselectionType   = type;
    isMultiselection = true;
    addMultiselection( path );
    // Log.v("DistoX", "start Multi Selection " + mMultiselectionType + " " + mMultiselected.size() );
    // return true;
  }

  private void addMultiselection( DrawingPath path )
  {
    if ( path.mType == mMultiselectionType ) {
      synchronized( TDPath.mSelectionLock ) { mMultiselected.add( path ); }
    }
    // Log.v("DistoX", "add Multi Selection " + mMultiselectionType + " " + mMultiselected.size() );
  }

  // MULTISELECTION ACTIONS
  void deleteMultiselection()
  {
    // if ( ! isMultiselection ) return;
    mMultiselectionType  = -1;
    isMultiselection = false;
    synchronized ( TDPath.mSelectionLock ) {
      for ( DrawingPath path : mMultiselected ) {
        mSelection.removePath( path );
      }
      synchronized( mCurrentStack ) {
        for ( DrawingPath path : mMultiselected ) {
          mCurrentStack.remove( path );
        }
      }
      mMultiselected.clear();
    }
  }

  void decimateMultiselection()
  {
    // if ( ! isMultiselection ) return;
    synchronized ( TDPath.mSelectionLock ) {
      for ( DrawingPath path : mMultiselected ) {
        mSelection.removePath( path );
      }
      synchronized( mCurrentStack ) {
        for ( DrawingPath path : mMultiselected ) {
          DrawingPointLinePath line = (DrawingPointLinePath)path;
          int min_size = (path.mType == DrawingPath.DRAWING_PATH_AREA)? 3 : 2;
          line.makeReduce( 1, min_size );
        }
      }
      for ( DrawingPath path : mMultiselected ) {
        mSelection.insertPath( path );
      }
    }
  }

  void joinMultiselection( float dmin )
  {
    // if ( ! isMultiselection ) return;
    synchronized ( TDPath.mSelectionLock ) {
      synchronized( mCurrentStack ) {
	int k0 = mMultiselected.size();
        for ( int k1=0; k1<k0; ++k1 ) {
          DrawingPointLinePath l1 = (DrawingPointLinePath)( mMultiselected.get(k1) );
	  LinePoint lp0 = null;
	  LinePoint lp9 = null;
	  float d0 = dmin;
          float d9 = dmin;
          for ( int k2=k1+1; k2<k0; ++k2 ) {
            DrawingPointLinePath l2 = (DrawingPointLinePath)( mMultiselected.get(k2) );
            float d1 = l1.mFirst.distance( l2.mFirst ); // distance from first
            float d2 = l1.mFirst.distance( l2.mLast );  // distance from last
	    if ( d1 < d2 ) {
              if ( d1 < d0 ) { d0 = d1; lp0 = l2.mFirst; }
	    } else {
              if ( d2 < d0 ) { d0 = d2; lp0 = l2.mLast; }
            }
            d1 = l1.mLast.distance( l2.mFirst );
            d2 = l1.mLast.distance( l2.mLast );
	    if ( d1 < d2 ) {
              if ( d1 < d9 ) { d9 = d1; lp9 = l2.mFirst; }
	    } else {
              if ( d2 < d9 ) { d9 = d2; lp9 = l2.mLast; }
            }
	  }
	  boolean retrace = false;
	  if ( lp0 != null ) {
            l1.mFirst.shiftBy( lp0.x - l1.mFirst.x, lp0.y - l1.mFirst.y );
	    retrace = true;
	  }
	  if ( lp9 != null ) {
            l1.mLast.shiftBy( lp9.x - l1.mLast.x, lp9.y - l1.mLast.y );
	    retrace = true;
	  }
	  if ( retrace ) {
            l1.retracePath();
	  }
        }
      }
    }
  }
  // end PATH_MULTISELECT

  void setCurrentStationName( DrawingStationName st ) { mCurrentStationName = st; }
  DrawingStationName getCurrentStationName( ) { return mCurrentStationName; }

  // DrawingPath              getNorth()        { return mNorthLine;    }

  // used by DrawingDxf and DrawingSvg
  // return a copy of the drawing objects
  List<ICanvasCommand> getCommands()
  { 
    List<ICanvasCommand> ret = new ArrayList<ICanvasCommand>();
    synchronized( mCurrentStack ) {
      for ( ICanvasCommand cmd : mCurrentStack ) {
        if ( cmd.commandType() == 0 ) {
          ret.add( (DrawingPath)cmd ); // FIXME copy path? ret.add( ((DrawingPath)cmd).clone() );
	}
      }
    }
    return ret;
  }

  // accessors used by DrawingDxf and DrawingSvg
  List<DrawingPath>        getLegs()         { return mLegsStack;    } 
  List<DrawingPath>        getSplays()       { return mSplaysStack;  }
  List<DrawingStationName> getStations()     { return mStations;     } 
  List<DrawingStationPath> getUserStations() { return mUserStations; }

  // accessor for DrawingSvg
  List<DrawingPath> getGrid1()   { return mGridStack1; }
  List<DrawingPath> getGrid10()  { return mGridStack10; }
  List<DrawingPath> getGrid100() { return mGridStack100; }

  private int mSelectMode = Drawing.FILTER_ALL;
  void setSelectMode( int mode ) { mSelectMode = mode; }

  private boolean hasEraser = false;
  private float mEraserX = 0; // eraser (x,y) canvas coords
  private float mEraserY = 0;
  private float mEraserR = 0; // eraser radius

  static void setDisplayMode( int mode ) { mDisplayMode = mode; }
  static int getDisplayMode( ) { return mDisplayMode; }

  /* FIXME_HIGHLIGHT
  void highlights( TopoDroidApp app ) 
  {
    synchronized( mSplaysStack ) { highlightsSplays( app ); }
    synchronized( mLegsStack )   { highlightsLegs( app ); }
  }

  private void highlightsSplays( TopoDroidApp app )
  {
    for ( DrawingPath path : mSplaysStack ) {
      if ( app.hasHighlightedId( path.mBlock.mId ) ) { 
        path.setPathPaint( BrushManager.errorPaint );
      }
    }
  }

  private void highlightsLegs( TopoDroidApp app )
  {
    for ( DrawingPath path : mLegsStack ) {
      if ( app.hasHighlightedId( path.mBlock.mId ) ) { 
        path.setPathPaint( BrushManager.errorPaint );
      }
    }
  }
  */

  void setSplayAlpha( boolean on ) 
  {
    for ( DrawingPath p : mSplaysStack ) {
      if ( p.getCosine() > TDSetting.mSectionSplay || p.getCosine() < -TDSetting.mSectionSplay ) p.setPaintAlpha( on );
    }
  }

  /* Check if any line overlaps another of the same type
   * In case of overlap the overlapped line is removed
   */
  void checkLines()
  {
    synchronized( mCurrentStack ) {
      int size = mCurrentStack.size();
      for ( int i1 = 0; i1 < size; ++i1 ) {
        ICanvasCommand cmd1 = mCurrentStack.get( i1 );
	if ( cmd1.commandType() != 0 ) continue;
        DrawingPath path1 = (DrawingPath)cmd1;
        if ( path1.mType != DrawingPath.DRAWING_PATH_LINE ) continue;
        DrawingLinePath line1 = (DrawingLinePath)path1;
        for ( int i2 = 0; i2 < size; ++i2 ) {
          if ( i2 == i1 ) continue;
          ICanvasCommand cmd2 = mCurrentStack.get( i2 );
	  if ( cmd2.commandType() != 0 ) continue;
          DrawingPath path2 = (DrawingPath)cmd2;
          if ( path2.mType != DrawingPath.DRAWING_PATH_LINE ) continue;
          DrawingLinePath line2 = (DrawingLinePath)path2;
          // if every point in line2 overlaps a point in line1 
          if ( line1.overlap( line1 ) == line2.size() ) {
            TDLog.Error("LINE OVERLAP " + i1 + "-" + i2 + " total nr. " + size );
            // for ( int i=0; i<size; ++i ) {
            //   ICanvasCommand cmd = mCurrentStack.get( i );
	    //   if ( cmd.commandType() != 0 ) continue;
            //   DrawingPath path = (DrawingPath)cmd;
            //   if ( path.mType != DrawingPath.DRAWING_PATH_LINE ) continue;
            //   DrawingLinePath line = (DrawingLinePath)path;
            //   line.dump();
            // }
            // Log.v("DistoX", "LINE1 ");
            // line1.dump();
            // Log.v("DistoX", "LINE2 ");
            // line2.dump();
            doDeletePath( line2 );
            -- size;
            -- i2;
            // throw new RuntimeException();
            if ( i2 < i1 ) --i1;
          }
        }
      }
    }
  }

  /* Flip the X-axis
   * flip the drawing about the vertical direction
   */
  private void flipXAxes( List<DrawingPath> paths )
  {
    final float z = 1/mScale;
    for ( DrawingPath path : paths ) {
      path.flipXAxis( z );
    }
  }

  // from ICanvasCommand
  public void flipXAxis( float z )
  {
    synchronized( mGridStack1 ) {
      flipXAxes( mGridStack1 );
      if ( mNorthLine != null ) mNorthLine.flipXAxis(z);
      flipXAxes( mGridStack10 );
      flipXAxes( mGridStack100 );
    }
    synchronized( mLegsStack )   { flipXAxes( mLegsStack ); }
    synchronized( mSplaysStack ) { flipXAxes( mSplaysStack ); }
    // FIXME 
    synchronized( mScrap ) { mScrap.clear(); }
    synchronized( TDPath.mXSectionsLock ) { mXSectionOutlines.clear(); }
 
    synchronized( mStations ) {
      for ( DrawingStationName st : mStations ) {
        st.flipXAxis(z);
      }
    }
    if ( mCurrentStack != null ) {
      Selection selection = new Selection();
      synchronized( mCurrentStack ) {
        for ( ICanvasCommand cmd : mCurrentStack ) {
          if ( cmd.commandType() == 0 ) {
            cmd.flipXAxis(z);
            DrawingPath path = (DrawingPath)cmd;
            if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
              DrawingLinePath line = (DrawingLinePath)path;
              line.flipReversed();
            }
	    synchronized ( TDPath.mSelectionLock ) {
              selection.insertPath( path );
	    }
          }
        }
      }
      mSelection = selection;
    }
    synchronized( mUserStations ) {
      for ( DrawingStationPath p : mUserStations ) {
        p.flipXAxis(z);
      }
    }
  }

  /* Shift the drawing
   * translate the drawing by (x,y)
   */
  void shiftDrawing( float x, float y )
  {
    // if ( mStations != null ) {
    //   synchronized( mStations ) {
    //     for ( DrawingStationName st : mStations ) {
    //       st.shiftBy( x, y );
    //     }
    //   }
    // }
    if ( mCurrentStack != null ){
      synchronized( mCurrentStack ) {
        for ( ICanvasCommand cmd : mCurrentStack ) {
          cmd.shiftPathBy( x, y );
        }
      }
    }
    if ( mSelection != null ) {
      synchronized( TDPath.mSelectionLock ) {
        mSelection.shiftSelectionBy( x, y );
      }
    }
  }

  /* Scale the drawing
   * scale the drawing by z
   */
  void scaleDrawing( float z )
  {
    // if ( mStations != null ) {
    //   synchronized( mStations ) {
    //     for ( DrawingStationName st : mStations ) {
    //       st.shiftBy( x, y );
    //     }
    //   }
    // }
    Matrix m = new Matrix();
    m.postScale(z,z);

    if ( mCurrentStack != null ){
      synchronized( mCurrentStack ) {
        for ( ICanvasCommand cmd : mCurrentStack ) {
          cmd.scalePathBy( z, m );
        }
      }
    }
    if ( mSelection != null ) {
      synchronized( TDPath.mSelectionLock ) {
        mSelection.scaleSelectionBy( z, m );
      }
    }
  }

  DrawingCommandManager()
  {
    mIsExtended  = false;
    mBBox = new RectF();
    mNorthLine       = null;
    mFirstReference  = null;
    mSecondReference = null;

    mGridStack1   = Collections.synchronizedList(new ArrayList<DrawingPath>());
    mGridStack10  = Collections.synchronizedList(new ArrayList<DrawingPath>());
    mGridStack100 = Collections.synchronizedList(new ArrayList<DrawingPath>());
    mLegsStack    = Collections.synchronizedList(new ArrayList<DrawingPath>());
    mSplaysStack  = Collections.synchronizedList(new ArrayList<DrawingPath>());
    mScrap        = Collections.synchronizedList(new ArrayList<DrawingLinePath>());
    mXSectionOutlines = Collections.synchronizedList(new ArrayList<DrawingOutlinePath>());
    mCurrentStack = Collections.synchronizedList(new ArrayList<ICanvasCommand>());
    mUserStations = Collections.synchronizedList(new ArrayList<DrawingStationPath>());
    mRedoStack    = Collections.synchronizedList(new ArrayList<ICanvasCommand>());
    // mHighlight = Collections.synchronizedList(new ArrayList<DrawingPath>());
    mStations     = Collections.synchronizedList(new ArrayList<DrawingStationName>());
    // PATH_MULTISELECT
    mMultiselected = Collections.synchronizedList( new ArrayList< DrawingPath >());

    // mGridStack1   = new ArrayList<DrawingPath>();
    // mGridStack10  = new ArrayList<DrawingPath>();
    // mGridStack100 = new ArrayList<DrawingPath>();
    // mLegsStack    = new ArrayList<DrawingPath>();
    // mSplaysStack  = new ArrayList<DrawingPath>();
    // mScrap        = new ArrayList<DrawingLinePath>();
    // mXSectionOutlines = new ArrayList<DrawingOutlinePath>();
    // mCurrentStack = new ArrayList<ICanvasCommand>();
    // mUserStations = new ArrayList<DrawingStationPath>();
    // mRedoStack    = new ArrayList<ICanvasCommand>();
    // // mHighlight = new ArrayList<DrawingPath>();
    // mStations     = new ArrayList<DrawingStationName>();

    mMatrix       = new Matrix(); // identity
    mSelection    = new Selection();
    mSelected     = new SelectionSet();
    mMaxAreaIndex = 0;
  }

  /**
   * this is the only place DrawuingScaleReference is instantiated
   */
  void addScaleRef( ) // boolean with_azimuth
  {
    mScaleRef = new DrawingScaleReference( BrushManager.referencePaint, new Point(20,-20), 0.33f ); // with_azimuth
  }

  // void debug()
  // {
  //   Log.v("DistoX-CMD", "Manager grid " + mGridStack1.toArray().length + " " 
  //                                   + mGridStack10.toArray().length + " " 
  //                                   + mGridStack100.toArray().length + " legs "
  //                                   + mLegsStack.toArray().length + " "
  //                                   + mSplaysStack.toArray().length + " items "
  //                                   + mCurrentStack.toArray().length );
  // }

  void syncClearSelected()
  { 
    synchronized( TDPath.mSelectionLock ) { clearSelected(); }
  }

  private void clearSelected()
  {
    mSelected.clear();
    // PATH_MULTISELECT
    mMultiselected.clear();
    mMultiselectionType  = -1;
    isMultiselection = false;
  }

  void clearReferences()
  {
    // Log.v("DistoX", "clear references");
    synchronized( mGridStack1 ) {
      mNorthLine       = null;
      mFirstReference  = null;
      mSecondReference = null;
      mGridStack1.clear();
      mGridStack10.clear();
      mGridStack100.clear();
      mScaleRef = null;
    }

    synchronized( mLegsStack )   { mLegsStack.clear(); }
    synchronized( mSplaysStack ) { mSplaysStack.clear(); }
    synchronized( mScrap       ) { mScrap.clear(); }
    synchronized( TDPath.mXSectionsLock   ) { mXSectionOutlines.clear(); }
    synchronized( mStations )    { mStations.clear(); }
    synchronized( TDPath.mSelectionLock ) {
      clearSelected();
      mSelection.clearReferencePoints();
    }
  }

  private void clearSketchItems()
  {
    // Log.v("DistoX", "clear sketch items");
    synchronized( TDPath.mSelectionLock ) { mSelection.clearSelectionPoints(); }
    synchronized( mCurrentStack ) { mCurrentStack.clear(); }
    synchronized( mUserStations ) { mUserStations.clear(); }
    mRedoStack.clear();
    syncClearSelected();
    mDisplayPoints = false;
  }

  void clearDrawing()
  {
    clearReferences();
    clearSketchItems();
    // mMatrix = new Matrix(); // identity
  }

  // first and second references are used only by the OverviewWindow
  void setFirstReference( DrawingPath path ) { synchronized( mGridStack1 ) { mFirstReference = path; } }

  void setSecondReference( DrawingPath path ) { synchronized( mGridStack1 ) { mSecondReference = path; } }

  void addSecondReference( float x, float y ) 
  {
    synchronized( mGridStack1 ) { 
      if ( mSecondReference != null ) mSecondReference.pathAddLineTo(x,y); 
    }
  }

  /* the next index for the ID of the area border
   */
  int getNextAreaIndex()
  {
    ++mMaxAreaIndex;
    return mMaxAreaIndex;
  }

  /* return the list of shots that intesect the segment (p1--p2)
   */
  List< DrawingPathIntersection > getIntersectionShot( LinePoint p1, LinePoint p2 )
  {
    List< DrawingPathIntersection > ret = new ArrayList<>();
    Float pt = Float.valueOf( 0 );
    for ( DrawingPath p : mLegsStack ) {
      if ( p.mType == DrawingPath.DRAWING_PATH_FIXED ) {
        float t = p.intersectSegment( p1.x, p1.y, p2.x, p2.y );
        if ( t >= 0 && t <= 1 ) {
          ret.add( new DrawingPathIntersection( p, t ) );
        }
      }
    }
    return ret;
  }

  /* Get the station at (x,y)
   * Return the station inside the square centered at (x,y) of side 2*size
   */
  DrawingStationName getStationAt( float x, float y, float size ) // x,y canvas coords
  {
    // Log.v("DistoX", "get station at " + x + " " + y );
    for ( DrawingStationName st : mStations ) {
      // Log.v("DistoX", "station at " + st.cx + " " + st.cy );
      if ( Math.abs( x - st.cx ) < size && Math.abs( y - st.cy ) < size ) return st;
    }
    return null;
  }

  DrawingStationName getStation( String name ) 
  {
    for ( DrawingStationName st : mStations ) {
      if ( name.equals( st.getName() ) ) return st;
    }
    return null;
  }

  void setDisplayPoints( boolean display ) { mDisplayPoints = display; }

  boolean isSelectable() { return mSelection != null; }

  // public void clearHighlight()
  // {
  //   for ( DrawingPath p : mHighlight ) {
  //     if ( p.mType == DrawingPath.DRAWING_PATH_FIXED ) {
  //       p.mPaint = BrushManager.fixedShotPaint;
  //     } else {
  //       p.mPaint = BrushManager.paintSplayXB;
  //     }
  //   }
  //   mHighlight.clear();
  // }

  // public DBlock setHighlight( int plot_type, float x, float y )
  // {
  //   clearHighlight();
  //   if ( ! PlotInfo.isSketch2d( plot_type ) ) return null;
  //   boolean legs   = (mDisplayMode & DisplayMode.DISPLAY_LEG) != 0;
  //   boolean splays = (mDisplayMode & DisplayMode.DISPLAY_SPLAY) != 0;
  //   boolean latest = (mDisplayMode & DisplayMode.DISPLAY_LATEST) != 0;
  //   if ( mHighlight.size() == 1 ) {
  //     return mHighlight.get(0).mBlock;
  //   }
  //   return null;
  // }

  /* Set the transform matrix for the canvas rendering of the drawing
   * The matrix is diag(s*dx, s*dy)
   */
  void setTransform( Activity act, float dx, float dy, float s, boolean landscape )
  {
    // int orientation = TDInstance.context.getResources().getConfiguration().orientation;
    // float hh = TopoDroidApp.mDisplayHeight;
    // float ww = TopoDroidApp.mDisplayWidth;
    // if ( orientation == Configuration.ORIENTATION_LANDSCAPE ) {
    //   ww = TopoDroidApp.mDisplayHeight;
    //   hh = TopoDroidApp.mDisplayWidth;
    // }
    // if ( ww < hh ) { ww = hh; } else { hh = ww; }

    Display d = act.getWindowManager().getDefaultDisplay();
    int r = d.getRotation();
    float ww, hh;
    if ( Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR2 ) {
      hh = d.getHeight();
      ww = d.getWidth();
    } else {
      Point pt = new Point();
      d.getSize( pt );
      hh = pt.y;
      ww = pt.x;
    }
    // Log.v( "DistoX-RR", "R " + r + " W " + ww + " H " + hh );

    mLandscape = landscape;
    mScale  = 1 / s;
    mMatrix = new Matrix();
    if ( landscape ) {
      mBBox.left   = - mScale * hh + dy;      // scene coords
      mBBox.right  =   dy; 
      mBBox.top    = - dx;
      mBBox.bottom =   mScale * ww - dx;
      mMatrix.postRotate(-90,0,0);
      mMatrix.postTranslate( dx, dy );
    } else {
      mBBox.left   = - dx;      // scene coords
      mBBox.right  = mScale * ww - dx; 
      mBBox.top    = - dy;
      mBBox.bottom = mScale * hh - dy;
      mMatrix.postTranslate( dx, dy );
    }
    mMatrix.postScale( s, s );

    synchronized ( mCurrentStack ) {
      for ( ICanvasCommand c : mCurrentStack ) {
        if ( c.commandType() == 0 ) {
          DrawingPath path = (DrawingPath)c;
          path.mLandscape = landscape;
          if ( path.mType == DrawingPath.DRAWING_PATH_AREA ) {
            DrawingAreaPath area = (DrawingAreaPath)path;
            area.shiftShaderBy( dx, dy, s );
          }
        }
      }
    }

    // FIXME 
    // TUNING this is to see how many buckets are on the canvas and how many points they contain
    //
    // if ( mSelection != null ) {
    //   int cnt = 0;
    //   float pts = 0;
    //   for ( SelectionBucket bucket : mSelection.mBuckets ) {
    //     if ( bucket.intersects( mBBox ) ) { ++ cnt; pts += bucket.size(); }
    //   }
    //   pts /= cnt;
    //   Log.v("DistoX-CMD", "visible buckets " + cnt + " avg pts/bucket " + pts );
    // }
  }

  // oooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo

  void addEraseCommand( EraseCommand cmd )
  {
    mCurrentStack.add( cmd );
  }

  // set the eraser circle
  // x, y canvas coords
  void setEraser( float x, float y, float r )
  {
    // Log.v("DistoX-ERASE", "set eraser " + x + " " + y + " " + r );
    mEraserX = x;
    mEraserY = y;
    mEraserR = r;
    hasEraser = true;
  }

  void endEraser() { hasEraser = false; }

  // called only if hasEraser is true
  private void drawEraser( Canvas canvas )
  {
    Path path = new Path();
    path.addCircle( mEraserX, mEraserY, mEraserR, Path.Direction.CCW );
    // path.transform( mMatrix );
    canvas.drawPath( path, BrushManager.highlightPaint2 );
  }

  final static String remove_line = "remove line completely";
  final static String remove_line_first = "remove line first point";
  final static String remove_line_second = "remove line second point";
  final static String remove_line_middle = "remove line middle point";
  final static String remove_line_last = "remove line last points";
  final static String remove_area_point = "remove area point";
  final static String remove_area = "remove area completely";

  /** 
   * return result code:
   *    0  no erasing
   *    1  point erased
   *    2  line complete erase
   *    3  line start erase
   *    4  line end erase 
   *    5  line split
   *    6  area complete erase
   *    7  area point erase
   *
   * x    X scene
   * y    Y scene
   * zoom canvas display zoom
   *
   * N.B. mSelection cannot be null here
   */
  void eraseAt( float x, float y, float zoom, EraseCommand eraseCmd, int erase_mode, float erase_size ) 
  {
    SelectionSet sel = new SelectionSet();
    float erase_radius = TDSetting.mCloseCutoff + erase_size / zoom;
    synchronized ( TDPath.mSelectionLock ) {
      mSelection.selectAt( sel, x, y, erase_radius, Drawing.FILTER_ALL, false, false, false, null );
    }
    // int ret = 0;
    if ( sel.size() > 0 ) {
      synchronized( mCurrentStack ) {
        for ( SelectionPoint pt : sel.mPoints ) {
          DrawingPath path = pt.mItem;
          if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
            if ( erase_mode == Drawing.FILTER_ALL || erase_mode == Drawing.FILTER_LINE ) {
              DrawingLinePath line = (DrawingLinePath)path;
	      if ( line.mLineType == BrushManager.mLineLib.mLineSectionIndex ) {
		// do not erase section lines 2018-06-22
		// deleting a section line should call DrawingWindow.deleteLine()
		// deleteSectionLine( line );
                continue;
              }
              LinePoint first = line.mFirst;
              LinePoint last  = line.mLast;
              int size = line.size();
              if ( size <= 2 || ( size == 3 && pt.mPoint == first.mNext ) ) // 2-point line OR erase midpoint of a 3-point line 
              {
                // TDLog.Log( TDLog.LOG_PLOT, remove_line );
                // ret = 2; 
                eraseCmd.addAction( EraseAction.ERASE_REMOVE, path );
                mCurrentStack.remove( path );
                synchronized ( TDPath.mSelectionLock ) {
                  mSelection.removePath( path );
                }
              } 
              else if ( pt.mPoint == first ) // erase first point of the multi-point line (2016-05-14)
              {
                // TDLog.Log( TDLog.LOG_PLOT, remove_line_first );
                // ret = 3;
                eraseCmd.addAction( EraseAction.ERASE_MODIFY, path );
                // LinePoint lp = points.get(0);
                // LinePoint lp = first;
                doRemoveLinePoint( line, pt.mPoint, pt );
                synchronized ( TDPath.mSelectionLock ) {
                  mSelection.removeLinePoint( line, first ); // index = 0
                  // mSelection.mPoints.remove( pt );        // index = 1
                }
                line.retracePath();
              }
              else if ( pt.mPoint == first.mNext ) // erase second point of the multi-point line
              {
                // TDLog.Log( TDLog.LOG_PLOT, remove_line_second );
                // ret = 3;
                eraseCmd.addAction( EraseAction.ERASE_MODIFY, path );
                // LinePoint lp = points.get(0);
                // LinePoint lp = first;
                doRemoveLinePoint( line, first, null );
                doRemoveLinePoint( line, pt.mPoint, pt );
                synchronized ( TDPath.mSelectionLock ) {
                  mSelection.removeLinePoint( line, first ); // index = 0
                  mSelection.mPoints.remove( pt );        // index = 1
                }
                line.retracePath();
              } 
              else if ( pt.mPoint == last.mPrev ) // erase second-to-last of multi-point line
              {
                // TDLog.Log( TDLog.LOG_PLOT, remove_line_last );
                // ret = 4;
                eraseCmd.addAction( EraseAction.ERASE_MODIFY, path );
                // LinePoint lp = points.get(size-1);
                // LinePoint lp = last;
                doRemoveLinePoint( line, last, null );
                doRemoveLinePoint( line, pt.mPoint, pt );
                synchronized ( TDPath.mSelectionLock ) {
                  mSelection.removeLinePoint( line, last ); // size -1
                  mSelection.mPoints.remove( pt );        // size -2
                }
                line.retracePath();
              }
              else if ( pt.mPoint == last ) // erase last of multi-point line
              {
                // TDLog.Log( TDLog.LOG_PLOT, remove_line_last );
                // ret = 4;
                eraseCmd.addAction( EraseAction.ERASE_MODIFY, path );
                // LinePoint lp = points.get(size-1);
                // LinePoint lp = last;
                doRemoveLinePoint( line, pt.mPoint, pt );
                synchronized ( TDPath.mSelectionLock ) {
                  mSelection.removeLinePoint( line, last ); // size -1
                  // mSelection.mPoints.remove( pt );        // size -2
                }
                line.retracePath();
              } else { // erase a point in the middle of multi-point line
                // TDLog.Log( TDLog.LOG_PLOT, remove_line_middle );
                // ret = 5;
                doSplitLine( line, pt.mPoint, eraseCmd );
                break; // IMPORTANT break the for-loop
              }
            }
          } else if ( path.mType == DrawingPath.DRAWING_PATH_AREA ) {
            if ( erase_mode == Drawing.FILTER_ALL || erase_mode == Drawing.FILTER_AREA ) {
              DrawingAreaPath area = (DrawingAreaPath)path;
              if ( area.size() <= 3 ) {
                // TDLog.Log( TDLog.LOG_PLOT, remove_area );
                // ret = 6;
                eraseCmd.addAction( EraseAction.ERASE_REMOVE, path );
                mCurrentStack.remove( path );
                synchronized ( TDPath.mSelectionLock ) {
                  mSelection.removePath( path );
                }
              } else {
                // TDLog.Log( TDLog.LOG_PLOT, remove_area_point );
                // ret = 7;
                eraseCmd.addAction( EraseAction.ERASE_MODIFY, path );
                doRemoveLinePoint( area, pt.mPoint, pt );
                area.retracePath();
              }
            }
          } else if ( path.mType == DrawingPath.DRAWING_PATH_POINT ) {
            if ( erase_mode == Drawing.FILTER_ALL || erase_mode == Drawing.FILTER_POINT ) {
              // ret = 1;
              eraseCmd.addAction( EraseAction.ERASE_REMOVE, path );
              mCurrentStack.remove( path );
              synchronized ( TDPath.mSelectionLock ) {
                mSelection.removePath( path );
              }
            }
          }
        }
      }
    }
    // checkLines();
    // return ret;
  }

  /* Split the line at the point lp
   * The erase command is updated with the removal of the original line and the insert
   * of the two new pieces
   // called from synchronized( CurrentStack ) context
   // called only by eraseAt
   */
  private void doSplitLine( DrawingLinePath line, LinePoint lp, EraseCommand eraseCmd )
  {
    DrawingLinePath line1 = new DrawingLinePath( line.mLineType );
    DrawingLinePath line2 = new DrawingLinePath( line.mLineType );
    if ( line.splitAt( lp, line1, line2, true ) ) {
      // Log.v("DistoX", "split " + line.size() + " ==> " + line1.size() + " " + line2.size() );
      // synchronized( mCurrentStack ) // not neceessary: called in synchronized context
      {
        eraseCmd.addAction( EraseAction.ERASE_REMOVE, line );
        mCurrentStack.remove( line );
        if ( line1.size() > 1 ) {
          eraseCmd.addAction( EraseAction.ERASE_INSERT, line1 );
          mCurrentStack.add( line1 );
        }
        if ( line2.size() > 1 ) {
          eraseCmd.addAction( EraseAction.ERASE_INSERT, line2 );
          mCurrentStack.add( line2 );
        }
      }
      synchronized( TDPath.mSelectionLock ) {
        mSelection.removePath( line ); 
        if ( line1.size() > 1 ) mSelection.insertLinePath( line1 );
        if ( line2.size() > 1 ) mSelection.insertLinePath( line2 );
      }
    // } else {
      // FIXME 
      // TDLog.Error( "FAILED splitAt " + lp.x + " " + lp.y );
      // line.dump();
    }
    // checkLines();
  }

  void splitLine( DrawingLinePath line, LinePoint lp )
  {
    if ( lp == null ) return;
    if ( lp == line.mFirst || lp == line.mLast ) return; // cannot split at first and last point
    int size = line.size();
    if ( size == 2 ) return;
    syncClearSelected();

    DrawingLinePath line1 = new DrawingLinePath( line.mLineType );
    DrawingLinePath line2 = new DrawingLinePath( line.mLineType );
    if ( line.splitAt( lp, line1, line2, false ) ) {
      synchronized( mCurrentStack ) {
        mCurrentStack.remove( line );
        mCurrentStack.add( line1 );
        mCurrentStack.add( line2 );
      }
      synchronized( TDPath.mSelectionLock ) {
        mSelection.removePath( line ); 
        mSelection.insertLinePath( line1 );
        mSelection.insertLinePath( line2 );
      }
    }
    // checkLines();
  }

  // called from synchronized( mCurrentStack )
  private void doRemoveLinePoint( DrawingPointLinePath line, LinePoint point, SelectionPoint sp )
  {
    line.remove( point );
    if ( sp != null ) { // sp can be null 
      synchronized( TDPath.mSelectionLock ) {
        mSelection.removePoint( sp );
      }
    }
    // checkLines();
  }

  boolean removeLinePoint( DrawingPointLinePath line, LinePoint point, SelectionPoint sp )
  {
    if ( point == null ) return false;
    int size = line.size();
    if ( size <= 2 ) return false;
    syncClearSelected();
    for ( LinePoint lp = line.mFirst; lp != null; lp = lp.mNext ) 
    {
      if ( lp == point ) {
        synchronized( mCurrentStack ) {
          line.remove( point );
	}
        synchronized( TDPath.mSelectionLock ) {
          mSelection.removePoint( sp );
        }
        // checkLines();
        return true;
      }
    }
    // checkLines();
    return false;
  }

  private void doDeletePath( DrawingPath path )
  {
    synchronized( mCurrentStack ) {
      mCurrentStack.remove( path );
    }
    synchronized( TDPath.mSelectionLock ) {
      mSelection.removePath( path );
      clearSelected();
    }
  }
  
  private boolean isInside( float x, float y, ArrayList<PointF> b )
  {
    int n = b.size();
    PointF p = b.get( n-1 );
    float x1 = x - p.x;
    float y1 = y - p.y;
    float z1 = x1*x1 + y1*y1;
    if ( z1 > 0 ) { z1 = (float)Math.sqrt(z1); x1 /= z1; y1 /= z1; }
    double angle = 0;
    for ( PointF q : b ) {
      float x2 = x - q.x;
      float y2 = y - q.y;
      float z2 = x2*x2 + y2*y2;
      if ( z2 > 0 ) { z2 = (float)Math.sqrt(z2); x2 /= z2; y2 /= z2; }
      angle += Math.asin( x2*y1 - y2*x1 );
      x1 = x2;
      y1 = y2;
    }
    return Math.abs( angle ) > 3.28; 
  }

  List<DrawingPath> splitPlot( ArrayList< PointF > border, boolean remove ) 
  {
    ArrayList<DrawingPath> paths = new ArrayList<>();
    synchronized( mCurrentStack ) {
      for ( ICanvasCommand c : mCurrentStack ) {
        if ( c.commandType() == 0 ) {
          DrawingPath p = (DrawingPath)c;
          if ( DrawingLevel.isLevelVisible( p ) && isInside( p.getX(), p.getY(), border ) ) {
            paths.add(p);
          }
        }
      }
      if ( remove ) {
        for ( DrawingPath pp : paths ) {
          mCurrentStack.remove( pp );
	  synchronized ( TDPath.mSelectionLock ) { 
            mSelection.removePath( pp );
	  }
        }
      }
    }
    return paths;
  }
    

  // p is the path of sp
  void deleteSplay( DrawingPath p, SelectionPoint sp )
  {
    synchronized( mSplaysStack ) {
      mSplaysStack.remove( p );
    }
    synchronized( TDPath.mSelectionLock ) {
      mSelection.removePoint( sp );
      clearSelected();
    }
  }

  void deletePath( DrawingPath path, EraseCommand eraseCmd ) // called by DrawingSurface
  {
    doDeletePath( path );
    // checkLines();
    if ( eraseCmd != null ) eraseCmd.addAction( EraseAction.ERASE_REMOVE, path );
  }

  // deleting a section line automatically deletes the associated section point(s)
  void deleteSectionLine( DrawingPath line, String scrap, EraseCommand cmd )
  {
    synchronized( mCurrentStack ) {
      int index = BrushManager.getPointSectionIndex();
      if ( index >= 0 ) {
        ArrayList<DrawingPath> todo = new ArrayList<>();
        for ( ICanvasCommand c : mCurrentStack ) {
          if ( c.commandType() == 0 ) {
            DrawingPath p = (DrawingPath)c;
            if ( p.mType == DrawingPath.DRAWING_PATH_POINT ) {
              DrawingPointPath pt = (DrawingPointPath)p;
              if ( pt.mPointType == index && scrap.equals( pt.getOption( "-scrap" ) ) ) {
                todo.add(p);
              }
            }
          }
        }
        for ( DrawingPath pp : todo ) deletePath( pp, cmd );
      }
      deletePath( line, cmd );
    }
  }

  void sharpenPointLine( DrawingPointLinePath line ) 
  {
    synchronized( mCurrentStack ) {
      line.makeSharp( );
    }
    // checkLines();
  }

  // @param decimation   log-decimation 
  void reducePointLine( DrawingPointLinePath line, int decimation ) 
  {
    if ( decimation <= 0 ) return;
    synchronized( TDPath.mSelectionLock ) {
      mSelection.removePath( line );
      clearSelected();
    }
    synchronized( mCurrentStack ) {
      int min_size = (line.mType == DrawingPath.DRAWING_PATH_AREA)? 3 : 2;
      line.makeReduce( decimation, min_size );
    }
    synchronized( TDPath.mSelectionLock ) {
      mSelection.insertPath( line );
    }
    // checkLines();
  }


  void rockPointLine( DrawingPointLinePath line ) 
  {
    synchronized( TDPath.mSelectionLock ) {
      mSelection.removePath( line );
      clearSelected();
    }
    synchronized( mCurrentStack ) {
      line.makeRock( );
    }
    synchronized( TDPath.mSelectionLock ) {
      mSelection.insertPath( line );
    }
    // checkLines();
  }

  void closePointLine( DrawingPointLinePath line )
  {
    synchronized( mCurrentStack ) {
      SelectionPoint sp = mSelection.getSelectionPoint( line.mLast );
      line.makeClose( );
      // re-bucket last line point
      synchronized ( TDPath.mSelectionLock ) {
        mSelection.rebucket( sp );
      }
    }
  }

  // ooooooooooooooooooooooooooooooooooooooooooooooooooooo

  // void setBounds( float x1, float x2, float y1, float y2 )
  // {
  //   mSelection = new Selection();
  // }
  //   try {
  //     mSelection = new Selection( x1, x2, y1, y2, 5.0f );
  //     mSelected  = new SelectionSet();
  //   } catch ( SelectionException e ) {
  //     TDLog.Error( "oversize: unable to select " );
  //     mSelection = null;
  //   }
  // } 

  // FIXME LEGS_SPLAYS
  void resetFixedPaint( TopoDroidApp app, boolean profile, Paint paint )
  {
    if( mLegsStack != null ) { 
      synchronized( mLegsStack ) {
        for ( DrawingPath path : mLegsStack ) {
          if ( path.mBlock == null || ( ! path.mBlock.mMultiBad ) ) {
            path.setPathPaint( paint );
          }
        }
	// highlightsLegs( app ); // FIXME_HIGHLIGHT
      }
    }
    if( mSplaysStack != null ) { 
      synchronized( mSplaysStack ) {
        for ( DrawingPath path : mSplaysStack ) {
          if ( path.mBlock == null || ( ! path.mBlock.mMultiBad ) ) {
            // path.setPathPaint( paint );
            if ( profile ) {
              if ( TDSetting.mDashSplay == TDSetting.DASHING_AZIMUTH ) {
                path.setSplayPaintPlan( path.mBlock, path.getCosine(), BrushManager.darkBluePaint, BrushManager.deepBluePaint );
              } else {
                path.setSplayPaintProfile( path.mBlock, BrushManager.darkBluePaint, BrushManager.deepBluePaint );
              }
            } else {
              if ( TDSetting.mDashSplay == TDSetting.DASHING_CLINO ) {
                path.setSplayPaintProfile( path.mBlock, BrushManager.darkBluePaint, BrushManager.deepBluePaint );
              } else {
                path.setSplayPaintPlan( path.mBlock, path.getCosine(), BrushManager.deepBluePaint, BrushManager.darkBluePaint );
              }
            }
          }
        }
	// highlightsSplays( app ); // FIXME_HIGHLIGHT
      }
    }
  }

  void addLegPath( DrawingPath path, boolean selectable )
  { 
    if ( mLegsStack == null ) return;
    synchronized( mLegsStack ) {
      mLegsStack.add( path );
      if ( selectable ) {
        synchronized( TDPath.mSelectionLock ) {
          // if ( path.mBlock != null ) {
          //   // Log.v( "DistoX", "selection add fixed path " + path.mBlock.mFrom + " " + path.mBlock.mTo );
          // }
          mSelection.insertPath( path );
        }
      }
    }
  }  

  void addSplayPath( DrawingPath path, boolean selectable )
  {
    if ( mSplaysStack == null ) return;
    synchronized( mSplaysStack ) {
      mSplaysStack.add( path );
      if ( selectable ) {
        synchronized( TDPath.mSelectionLock ) {
          // if ( path.mBlock != null ) {
          //   // Log.v( "DistoX", "selection add fixed path " + path.mBlock.mFrom + " " + path.mBlock.mTo );
          // }
          mSelection.insertPath( path );
        }
      }
    }
  }  
  
  // used by H-Sections
  void setNorthLine( DrawingPath path ) { mNorthLine = path; }

  void addGrid( DrawingPath path, int k )
  { 
    if ( mGridStack1 == null ) return;
    synchronized( mGridStack1 ) {
      switch (k) {
        case 1:   mGridStack1.add( path );   break;
        case 10:  mGridStack10.add( path );  break;
        case 100: mGridStack100.add( path ); break;
      }
    }
  }

  // void setScaleBar( float x0, float y0 ) 
  // {
  //   if ( mCurrentStack.size() > 0 ) return;
  //   DrawingLinePath scale_bar = new DrawingLinePath( BrushManager.mLineLib.mLineSectionIndex );
  //   scale_bar.addStartPoint( x0 - 50, y0 );
  //   scale_bar.addPoint( x0 + 50, y0 );  // 5 meters
  //   synchronized( mCurrentStack ) {
  //     mCurrentStack.add( scale_bar );
  //   }
  // }

  DrawingStationPath getUserStation( String name )
  {
    if ( name != null ) {
      for ( DrawingStationPath sp : mUserStations ) if ( name.equals( sp.name() ) ) return sp;
    }
    return null;
  }

  void removeUserStation( DrawingStationPath path )
  {
    // Log.v("DistoX", "remove user station " + path.mName );
    synchronized( mUserStations ) {
      mUserStations.remove( path );
    }
  }

  // boolean hasUserStation( String name )
  // {
  //   for ( DrawingStationPath p : mUserStations ) if ( p.mName.equals( name ) ) return true;
  //   return false;
  // }
  

  void addUserStation( DrawingStationPath path )
  {
    // Log.v("DistoX", "add user station " + path.mName );
    synchronized( mUserStations ) {
      mUserStations.add( path );
    }
  }

  void addCommand( DrawingPath path )
  {
    // TDLog.Log( TDLog.LOG_PLOT, "addCommand stack size  " + mCurrentStack.size() );
    // Log.v("DistoX", "add command type " + path.mType + " " + path.left + " " + path.top + " " 
    //        + mBBox.left + " " + mBBox.top + " " + mBBox.right + " " + mBBox.bottom );

    mRedoStack.clear();

    if ( path.mType == DrawingPath.DRAWING_PATH_AREA ) {
      DrawingAreaPath area = (DrawingAreaPath)path;
      if ( area.mAreaCnt > mMaxAreaIndex ) {
        mMaxAreaIndex = area.mAreaCnt;
      }
    }

    // if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
    //   DrawingLinePath line = (DrawingLinePath)path;
    //   LinePoint lp = line.mFirst;
    //   Log.v("DistoX-CMD", "add path. size " + line.size() + " start " + lp.x + " " + lp.y );
    // }
    
    synchronized( mCurrentStack ) {
      mCurrentStack.add( path );
    }
    if ( path.mType != DrawingPath.DRAWING_PATH_NORTH ) {
      synchronized( TDPath.mSelectionLock ) {
        mSelection.insertPath( path );
      }
    }
    
    // checkLines();
  }

  void deleteSectionPoint( String scrap_name, EraseCommand cmd )
  {
    int index = BrushManager.getPointSectionIndex();
    synchronized( mCurrentStack ) {
      for ( ICanvasCommand icc : mCurrentStack ) { // FIXME reverse_iterator
        if ( icc.commandType() == 0 ) { // DrawingPath
          DrawingPath path = (DrawingPath)icc;
          if ( path.mType == DrawingPath.DRAWING_PATH_POINT ) {
            DrawingPointPath dpp = (DrawingPointPath) path;
            if ( dpp.mPointType == index ) {
              // FIXME GET_OPTION
              if ( scrap_name.equals( dpp.getOption( "-scrap" ) ) ) {
                deletePath( path, cmd );
                return; // true;
              }
              // String vals[] = dpp.mOptions.split(" ");
              // int len = vals.length;
              // for ( int k = 0; k < len; ++k ) {
              //   if ( scrap_name.equals( vals[k] ) ) {
              //     deletePath( path, cmd );
              //     return;
              //   }
              // }
            }
          }
        }
      }
    }
    // return false;
  }

  private void union( RectF b0, RectF b1 )
  {
    if ( b0.left   > b1.left   ) b0.left   = b1.left;
    if ( b0.right  < b1.right  ) b0.right  = b1.right;
    if ( b0.top    > b1.top    ) b0.top    = b1.top;
    if ( b0.bottom < b1.bottom ) b0.bottom = b1.bottom;
  }

  // called by DrawingSurface.getBitmap()
  RectF getBitmapBounds()
  {
    // Log.v("DistoX", "get bitmap bounds. splays " + mSplaysStack.size() 
    //               + " legs " + mLegsStack.size() 
    //               + " cmds " + mCurrentStack.size() );
    RectF bounds = new RectF(-1,-1,1,1);
    RectF b = new RectF();
    if( mSplaysStack != null ) { 
      synchronized( mSplaysStack ) {
        for ( DrawingPath path : mSplaysStack ) {
          path.computeBounds( b, true );
          // bounds.union( b );
          union( bounds, b );
        }
      }
    }
    if( mLegsStack != null ) { 
      synchronized( mLegsStack ) {
        for ( DrawingPath path : mLegsStack ) {
          path.computeBounds( b, true );
          // bounds.union( b );
          union( bounds, b );
        }
      }
    }

    if( mCurrentStack != null ){
      synchronized( mCurrentStack ) {
        for ( ICanvasCommand cmd : mCurrentStack ) {
          cmd.computeBounds( b, true );
          // bounds.union( b );
          union( bounds, b );
        }
      }
    }
    // Log.v("DistoX", "bounds " + bounds.left + " " + bounds.top + " " + bounds.right + " " + bounds.bottom );
    return bounds;
  }

  private float mBitmapScale = 1;

  // returns the last used bitmap scale
  float getBitmapScale() { return mBitmapScale; }

  public Bitmap getBitmap()
  {
    RectF bounds = getBitmapBounds();
    // TDLog.Log( TDLog.LOG_PLOT, "getBitmap Bounds " + bounds.left + " " + bounds.top + " " + bounds.right + " " + bounds.bottom );
    mBitmapScale = TDSetting.mBitmapScale;

    int width  = (int)((bounds.right - bounds.left + 2 * BORDER) );
    int height = (int)((bounds.bottom - bounds.top + 2 * BORDER) );
    int max = (int)( 8 * 1024 * 1024 / (mBitmapScale * mBitmapScale) );  // 16 MB 2 B/pixel
    while ( width*height > max ) {
      mBitmapScale /= 2;
      max *= 4;
    }
    width  = (int)((bounds.right - bounds.left + 2 * BORDER) * mBitmapScale );
    height = (int)((bounds.bottom - bounds.top + 2 * BORDER) * mBitmapScale );
   
    Bitmap bitmap = null;
    while ( bitmap == null && mBitmapScale > 0.05 ) {
      if ( width <= 0 || height <= 0 ) return null; 
      try {
        // bitmap =  Bitmap.createBitmap (width, height, Bitmap.Config.ARGB_8888);
        bitmap =  Bitmap.createBitmap (width, height, Bitmap.Config.RGB_565);
      } catch ( OutOfMemoryError e ) {
        mBitmapScale /= 2;
        width  = (int)((bounds.right - bounds.left + 2 * BORDER) * mBitmapScale );
        height = (int)((bounds.bottom - bounds.top + 2 * BORDER) * mBitmapScale );
      } catch ( IllegalArgumentException e ) {
        TDLog.Error("create bitmap illegal arg " + e.getMessage() );
        return null;
      }
    }
    if ( bitmap == null ) return null;
    if ( mBitmapScale <= 0.05 ) {
      bitmap.recycle();
      return null;
    }
    // Log.v( "DistoX", "PNG mBitmapScale " + mBitmapScale + "/" + TDSetting.mBitmapScale + " " + width + "x" + height );
    Canvas c = new Canvas (bitmap);
    // c.drawColor(TDSetting.mBitmapBgcolor, PorterDuff.Mode.CLEAR);
    c.drawColor( TDSetting.mBitmapBgcolor );

    // commandManager.execute All(c,previewDoneHandler);
    c.drawBitmap (bitmap, 0, 0, null);

    Matrix mat = new Matrix();
    float sca = 1 / mBitmapScale;
    mat.postTranslate( BORDER - bounds.left, BORDER - bounds.top );
    mat.postScale( mBitmapScale, mBitmapScale );
    if ( TDSetting.mSvgGrid ) {
      if ( mGridStack1 != null ) {
        synchronized( mGridStack1 ) {
          for ( DrawingPath p1 : mGridStack1 ) {
            p1.draw( c, mat, sca, null );
          }
          for ( DrawingPath p10 : mGridStack10 ) {
            p10.draw( c, mat, sca, null );
          }
          for ( DrawingPath p100 : mGridStack100 ) {
            p100.draw( c, mat, sca, null );
          }
          if ( mNorthLine != null ) mNorthLine.draw( c, mat, sca, null );
          // no extend line for bitmap
        }
      }
    }

    if ( TDSetting.mTherionSplays ) {
      if ( mSplaysStack != null ) {
        synchronized( mSplaysStack ) {
          for ( DrawingPath path : mSplaysStack ) {
            path.draw( c, mat, sca, null );
          }
        }
      }
    }

    if ( mLegsStack != null ) {
      synchronized( mLegsStack ) {
        for ( DrawingPath path : mLegsStack ) {
          path.draw( c, mat, sca, null );
        }
      }
    }
 
    if ( TDSetting.mAutoStations ) {
      if ( mStations != null ) {  
        synchronized( mStations ) {
          for ( DrawingStationName st : mStations ) {
            st.draw( c, mat, sca, null );
          }
        }
      }
    }

    if( mCurrentStack != null ){
      synchronized( mCurrentStack ) {
        if ( TDSetting.mWithLevels == 0 ) { // treat no-levels case by itself
          for ( ICanvasCommand cmd : mCurrentStack ) {
            if ( cmd.commandType() == 0 ) {
              cmd.draw( c, mat, sca, null );
            }
          }
        } else {
          for ( ICanvasCommand cmd : mCurrentStack ) {
            if ( cmd.commandType() == 0 ) {
              if ( DrawingLevel.isLevelVisible( (DrawingPath)cmd ) ) {
                cmd.draw( c, mat, sca, null );
              }
            }
          }
        }
      }
    }
    // checkLines();
    return bitmap;
  }

  // static final String actionName[] = { "remove", "insert", "modify" }; // DEBUG LOG

  public void undo ()
  {
    final int length = currentStackLength();
    if ( length > 0) {
      final ICanvasCommand cmd = mCurrentStack.get(  length - 1  );

      synchronized( mCurrentStack ) {
        mCurrentStack.remove( length - 1 );
        // cmd.undoCommand();
      }
      mRedoStack.add( cmd );

      if ( cmd.commandType() == 0 ) {
        synchronized( TDPath.mSelectionLock ) {
          mSelection.removePath( (DrawingPath)cmd );
        }
      } else { // EraseCommand
        EraseCommand eraseCmd = (EraseCommand)cmd;
        int na = eraseCmd.mActions.size(); 
        while ( na > 0 ) {
          --na;
          EraseAction action = eraseCmd.mActions.get( na );
          DrawingPath path = action.mPath;
          // Log.v("DistoX", "UNDO " + actionName[action.mType] + " path " + path.toString() );
          if ( action.mInitialType == EraseAction.ERASE_INSERT ) {
            synchronized( mCurrentStack ) {
              mCurrentStack.remove( path );
            }
            synchronized( TDPath.mSelectionLock ) {
              mSelection.removePath( path );
            }
          } else if ( action.mType == EraseAction.ERASE_REMOVE ) {
            synchronized( mCurrentStack ) {
              action.restorePoints( true ); // true: use old points
              mCurrentStack.add( path );
            }
            synchronized( TDPath.mSelectionLock ) {
              mSelection.insertPath( path );
            }
          } else if ( action.mType == EraseAction.ERASE_MODIFY ) { // undo modify
            synchronized( TDPath.mSelectionLock ) {
              mSelection.removePath( path );
            }
            synchronized( mCurrentStack ) {
              action.restorePoints( true );
            }
            synchronized( TDPath.mSelectionLock ) {
              mSelection.insertPath( path );
            }
          }
        }
      }
    }
    // checkLines();
  }

  private int currentStackLength()
  {
    // return mCurrentStack.toArray().length;
    return mCurrentStack.size();
  }

  // line points are scene-coords
  // continuation is checked in canvas-coords: canvas = offset + scene * zoom
  DrawingLinePath getLineToContinue( LinePoint lp, int type, float zoom, float size )
  {
    String group = BrushManager.getLineGroup( type );
    if ( group == null ) return null;

    float delta = 2 * size / zoom;

    DrawingLinePath ret = null;
    synchronized( mCurrentStack ) {
      for ( ICanvasCommand cmd : mCurrentStack ) {
        if ( cmd.commandType() != 0 ) continue; // FIXME EraseCommand

        final DrawingPath drawingPath = (DrawingPath)cmd;
        if ( drawingPath.mType == DrawingPath.DRAWING_PATH_LINE ) {
          DrawingLinePath linePath = (DrawingLinePath)drawingPath;
          // if ( linePath.mLineType == type ) 
          if ( group.equals( BrushManager.getLineGroup( linePath.mLineType ) ) )
          {
            if ( linePath.mFirst.distance( lp ) < delta || linePath.mLast.distance( lp ) < delta ) {
              if ( ret != null ) return null; // ambiguity
              ret = linePath;
            }
          }
        }
      }
    }
    // if ( ret != null ) mSelection.removePath( ret ); // FIXME do not remove continuation line
    // checkLines();
    return ret;
  }
        
  // @return true if the line has been modified
  // @param line  line to modify
  // @param line2 modification
  // @param zoom  current zoom
  // @param size  selection size
  boolean modifyLine( DrawingLinePath line, DrawingLinePath line2, float zoom, float size )
  {
    LinePoint lp1 = line.mFirst; 
    if ( lp1 == null ) return false; // sanity check
    if ( line2 == null || line2.size() < 3 ) return false;
    float delta = size / zoom;
    LinePoint first = line2.mFirst;
    LinePoint last  = line2.mLast;
    for ( ; lp1 != null; lp1 = lp1.mNext ) {
      if ( lp1.distance( first ) < delta ) {
        LinePoint lp2 = null;
        LinePoint lp1n = lp1.mNext;
        if ( lp1n != null ) {
          lp2 = line.mLast;
          // int toDrop = 0; // number of points to drop
          for ( ; lp2 != lp1 && lp2 != null; lp2 = lp2.mPrev ) { // FIXME 20190512 check lp2 != null
            if ( lp2.distance( last ) < delta ) {
              lp2 = lp2.mNext; // backup one point
              break;
            }
            // ++ toDrop;
          }
          if ( lp2 == lp1 ) { // if loop ended because arrived to the initial point lp1
            lp2 = null;
          }
        } 
        // int old_size = line.size();
        // line.mSize += line2.mSize - toDrop; // better recount points
        synchronized( TDPath.mSelectionLock ) {
          mSelection.removePath( line );
        }
        synchronized( mCurrentStack ) {
          // line.replacePortion( lp1, lp2, line2 );
          lp1.mNext = first.mNext;
          first.mPrev = lp1;
          last.mNext = lp2;
          if ( lp2 != null ) lp2.mPrev = last;
          line.recomputeSize();
          line.retracePath();
          // Log.v("DistoX-C", "size old " + old_size + " drop " + toDrop + " line2 " + line2.size() + " new " + line.size() );
        }
        synchronized( TDPath.mSelectionLock ) {
          mSelection.insertPath( line );
        }
        return true;
      }
    }
    return false;
  }

  /** add the points of the first line to the second line
   */
  void addLineToLine( DrawingLinePath line, DrawingLinePath line0 )
  {
    synchronized( TDPath.mSelectionLock ) {
      mSelection.removePath( line0 );
    }
    synchronized( mCurrentStack ) {
      boolean reverse = line0.mFirst.distance( line.mFirst ) < line0.mLast.distance( line.mFirst );
      if ( reverse ) line0.reversePath();
      line0.append( line );
      if ( reverse ) {
        line0.reversePath();
        line0.computeUnitNormal();
      }
    }
    synchronized( TDPath.mSelectionLock ) {
      mSelection.insertPath( line0 );
    }
    // checkLines();
  }


  private void drawGreenDot( Canvas canvas, SelectionPoint pt, float dot_radius )
  {
    Path path = new Path();
    if ( pt.mPoint != null ) { // line-point
      path.addCircle( pt.mPoint.x, pt.mPoint.y, dot_radius, Path.Direction.CCW );
    } else {  
      path.addCircle( pt.mItem.cx, pt.mItem.cy, dot_radius, Path.Direction.CCW );
    }
    path.transform( mMatrix );
    canvas.drawPath( path, BrushManager.highlightPaint2 );
  }

  // N.B. doneHandler is not used
  void executeAll( Canvas canvas, float zoom, DrawingStationSplay station_splay )
  {
    if ( canvas == null ) {
      TDLog.Error( "drawing execute all: null canvas");
      return;
    }

    boolean legs     = (mDisplayMode & DisplayMode.DISPLAY_LEG     ) != 0;
    boolean splays   = (mDisplayMode & DisplayMode.DISPLAY_SPLAY   ) != 0;
    boolean latest   = ( (mDisplayMode & DisplayMode.DISPLAY_LATEST  ) != 0 ) && TDSetting.mShotRecent;
    boolean stations = (mDisplayMode & DisplayMode.DISPLAY_STATION ) != 0;
    boolean grids    = (mDisplayMode & DisplayMode.DISPLAY_GRID    ) != 0;
    boolean outline  = (mDisplayMode & DisplayMode.DISPLAY_OUTLINE ) != 0;
    boolean scaleRef = (mDisplayMode & DisplayMode.DISPLAY_SCALEBAR ) != 0;

    boolean spoints   = false;
    boolean slines    = false;
    boolean sareas    = false;
    boolean sshots    = false;
    boolean sstations = false;

    switch (mSelectMode) {
      case Drawing.FILTER_ALL:
        sshots = true;
        sstations = stations;
        spoints = slines = sareas = true;
        break;
      case Drawing.FILTER_POINT:
        spoints = true;
        break;
      case Drawing.FILTER_LINE:
        slines = true;
        break;
      case Drawing.FILTER_AREA:
        sareas = true;
        break;
      case Drawing.FILTER_SHOT:
        sshots = true;
        break;
      case Drawing.FILTER_STATION:
        sstations = true;
        break;
    }

    if( grids && mGridStack1 != null ) {
      synchronized( mGridStack1 ) {
        if ( mScale < 1 ) {
          for ( DrawingPath p1 : mGridStack1 ) {
            p1.draw( canvas, mMatrix, mScale, mBBox );
          }
        }
        if ( mScale < 10 ) {
          for ( DrawingPath p10 : mGridStack10 ) {
            p10.draw( canvas, mMatrix, mScale, mBBox );
          }
        }
        for ( DrawingPath p100 : mGridStack100 ) {
          p100.draw( canvas, mMatrix, mScale, mBBox );
        }
        if ( mNorthLine != null ) {
          mNorthLine.draw( canvas, mMatrix, mScale, mBBox );
        }
        if(scaleRef && (mScaleRef != null)) {
          mScaleRef.draw(canvas, zoom, mLandscape);
        }
      }
    }

    if ( legs && mLegsStack != null ) {
      synchronized( mLegsStack ) {
        for ( DrawingPath leg: mLegsStack ) {
          leg.draw( canvas, mMatrix, mScale, mBBox );
        }
      }
    }

    if ( mSplaysStack != null ) {
      synchronized( mSplaysStack ) {
        if ( splays ) { // draw all splays except the splays-off
          for ( DrawingPath path : mSplaysStack ) {
	        if ( ! station_splay.isStationOFF( path ) ) path.draw( canvas, mMatrix, mScale, mBBox );
	      }
        } else if ( latest || station_splay.hasSplaysON() ) { // draw the splays-on and/or the lastest
          for ( DrawingPath path : mSplaysStack ) {
            if ( station_splay.isStationON( path ) || path.isBlockRecent() ) path.draw( canvas, mMatrix, mScale, mBBox );
	      }
	    }
      }
    }
    if ( mScrap != null && mScrap.size() > 0 ) {
      synchronized( mScrap )  {
        for (DrawingLinePath path : mScrap ) {
          path.draw( canvas, mMatrix, mScale, null /* mBBox */ );
        }
      }
    }
    if ( mXSectionOutlines != null && mXSectionOutlines.size() > 0 ) {
      synchronized( TDPath.mXSectionsLock )  {
        for ( DrawingOutlinePath path : mXSectionOutlines ) {
          path.mPath.draw( canvas, mMatrix, mScale, null /* mBBox */ );
        }
      }
    }
 
    if ( stations && mStations != null ) {  
      synchronized( mStations ) {
        for ( DrawingStationName st : mStations ) {
          st.draw( canvas, mMatrix, mScale, mBBox );
        }
      }
    }

    if ( mCurrentStack != null ){
      synchronized( mCurrentStack ) {
        if ( outline ) {
          for ( ICanvasCommand cmd : mCurrentStack  ) {
            if ( cmd.commandType() == 0 ) {
              DrawingPath path = (DrawingPath)cmd;
              if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
                DrawingLinePath line = (DrawingLinePath)path;
                if ( line.hasOutline() ) {
                  cmd.draw( canvas, mMatrix, mScale, mBBox );
                }
              }
            }
          }
        } else {
          if ( TDSetting.mWithLevels == 0 ) { // treat no-levels case by itself
            for ( ICanvasCommand cmd : mCurrentStack  ) {
              if ( cmd.commandType() == 0 ) {
                DrawingPath path = (DrawingPath)cmd;
                cmd.draw( canvas, mMatrix, mScale, mBBox );
                if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
                  DrawingLinePath line = (DrawingLinePath)path;
                  if ( line.mLineType == BrushManager.mLineLib.mLineSectionIndex ) { // add direction-tick to section-lines
                    LinePoint lp = line.mFirst;
                    Path path1 = new Path();
                    path1.moveTo( lp.x, lp.y );
                    path1.lineTo( lp.x+line.mDx*TDSetting.mArrowLength, lp.y+line.mDy*TDSetting.mArrowLength );
                    path1.transform( mMatrix );
                    canvas.drawPath( path1, BrushManager.mSectionPaint );
                  }
                }
              }
            }
          } else {
            for ( ICanvasCommand cmd : mCurrentStack  ) {
              if ( cmd.commandType() == 0 ) {
                DrawingPath path = (DrawingPath)cmd;
                if ( DrawingLevel.isLevelVisible( (DrawingPath)cmd ) ) {
                  cmd.draw( canvas, mMatrix, mScale, mBBox );
                  if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
                    DrawingLinePath line = (DrawingLinePath)path;
                    if ( line.mLineType == BrushManager.mLineLib.mLineSectionIndex ) { // add direction-tick to section-lines
                      LinePoint lp = line.mFirst;
                      Path path1 = new Path();
                      path1.moveTo( lp.x, lp.y );
                      path1.lineTo( lp.x+line.mDx*TDSetting.mArrowLength, lp.y+line.mDy*TDSetting.mArrowLength );
                      path1.transform( mMatrix );
                      canvas.drawPath( path1, BrushManager.mSectionPaint );
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    if ( ! TDSetting.mAutoStations ) {
      synchronized( mUserStations ) {
        for ( DrawingStationPath p : mUserStations ) {
          p.draw( canvas, mMatrix, mScale, mBBox );
        }
      }
    }

    if ( mDisplayPoints ) {
      float dot_radius = TDSetting.mDotRadius/zoom;
      synchronized( TDPath.mSelectionLock ) {
        if ( TDSetting.mWithLevels == 0 ) { // treat no-levels case by itself
          for ( SelectionBucket bucket: mSelection.mBuckets ) {
            if ( bucket.intersects( mBBox ) ) {
              for ( SelectionPoint pt : bucket.mPoints ) { 
                int type = pt.type();
                if ( type == DrawingPath.DRAWING_PATH_POINT ) {
                  if ( ! spoints ) continue;
                } else if ( type == DrawingPath.DRAWING_PATH_LINE ) {
                  if ( ! slines ) continue;
                } else if ( type == DrawingPath.DRAWING_PATH_AREA ) {
                  if ( ! sareas ) continue;
                } else if ( ( type == DrawingPath.DRAWING_PATH_FIXED && ! (legs && sshots) )
                            // || ( type == DrawingPath.DRAWING_PATH_SPLAY && ! (splays && sshots) )
                            || ( type == DrawingPath.DRAWING_PATH_NAME  && ! (sstations) ) ) {
                  continue;
                }
	        if ( type == DrawingPath.DRAWING_PATH_SPLAY ) {
                  // FIXME_LATEST latest splays
                  if ( splays ) {
                    if ( station_splay.isStationOFF( pt.mItem ) ) continue;
                  } else {
                    if ( ! station_splay.isStationON( pt.mItem ) ) continue;
                  }
	        } 
                drawGreenDot( canvas, pt, dot_radius );
              }
            }
          }
        } else {
          for ( SelectionBucket bucket: mSelection.mBuckets ) {
            if ( bucket.intersects( mBBox ) ) {
              for ( SelectionPoint pt : bucket.mPoints ) { 
                int type = pt.type();
                if ( type == DrawingPath.DRAWING_PATH_POINT ) {
                  if ( ! spoints || ! DrawingLevel.isLevelVisible( pt.mItem ) ) continue;
                } else if ( type == DrawingPath.DRAWING_PATH_LINE ) {
                  if ( ! slines || ! DrawingLevel.isLevelVisible( pt.mItem ) ) continue;
                } else if ( type == DrawingPath.DRAWING_PATH_AREA ) {
                  if ( ! sareas || ! DrawingLevel.isLevelVisible( pt.mItem ) ) continue;
                } else if ( ( type == DrawingPath.DRAWING_PATH_FIXED && ! (legs && sshots) )
                            // || ( type == DrawingPath.DRAWING_PATH_SPLAY && ! (splays && sshots) )
                            || ( type == DrawingPath.DRAWING_PATH_NAME  && ! (sstations) ) ) {
                  continue;
                }
	        if ( type == DrawingPath.DRAWING_PATH_SPLAY ) {
                  // FIXME_LATEST latest splays
                  if ( splays ) {
                    if ( station_splay.isStationOFF( pt.mItem ) ) continue;
                  } else {
                    if ( ! station_splay.isStationON( pt.mItem ) ) continue;
                  }
	        } 
                drawGreenDot( canvas, pt, dot_radius );
              }
            }
          }
        }
        // for ( SelectionPoint pt : mSelection.mPoints ) { // FIXME SELECTION
        //   float x, y;
        //   if ( pt.mPoint != null ) { // line-point
        //     x = pt.mPoint.x;
        //     y = pt.mPoint.y;
        //   } else {  
        //     x = pt.mItem.cx;
        //     y = pt.mItem.cy;
        //   }
        //   Path path = new Path();
        //   path.addCircle( x, y, dot_radius, Path.Direction.CCW );
        //   path.transform( mMatrix );
        //   canvas.drawPath( path, BrushManager.highlightPaint2 );
        // }

      }
      // synchronized( TDPath.mSelectedLock ) {
      synchronized( TDPath.mSelectionLock ) {
	// PATH_SELECTION
	if ( isMultiselection ) {
          Path path = new Path();
	  if ( mMultiselectionType == DrawingPath.DRAWING_PATH_POINT ) {
            float radius = 4*TDSetting.mDotRadius/zoom;
	    for ( DrawingPath item : mMultiselected ) {
              float x = item.cx;
              float y = item.cy;
              path.addCircle( x, y, radius, Path.Direction.CCW );
            }
	  } else { // if ( mMultiselectionType == DrawingPath.DRAWING_PATH_LINE || mMultiselectionType == DrawingPath.DRAWING_PATH_LINE ) 
	    for ( DrawingPath item : mMultiselected ) {
	      DrawingPointLinePath line = (DrawingPointLinePath) item;
              LinePoint lp = line.mFirst;
              path.moveTo( lp.x, lp.y );
              for ( lp = lp.mNext; lp != null; lp = lp.mNext ) {
                if ( lp.has_cp ) {
                  path.cubicTo( lp.x1, lp.y1, lp.x2, lp.y2, lp.x, lp.y );
                } else {
                  path.lineTo( lp.x, lp.y );
                }
              }
	    }
	  }
          path.transform( mMatrix );
          canvas.drawPath( path, BrushManager.fixedYellowPaint );
	  // end PATH_SELECTION
	} else if ( mSelected.mPoints.size() > 0 ) { // FIXME SELECTION
          float radius = 4*TDSetting.mDotRadius/zoom;
          Path path;
          SelectionPoint sp = mSelected.mHotItem;
          if ( sp != null ) {
            float x, y;
            LinePoint lp = sp.mPoint;
            DrawingPath item = sp.mItem;
            LinePoint lp1 = (sp.mRange == null)? null : sp.mRange.start();
            LinePoint lp2 = (sp.mRange == null)? null : sp.mRange.end();

            if ( lp != null ) { // line-point
              x = lp.x;
              y = lp.y;
            } else {
              x = item.cx;
              y = item.cy;
            }
            path = new Path();
            path.addCircle( x, y, radius, Path.Direction.CCW );
            path.transform( mMatrix );
            canvas.drawPath( path, BrushManager.highlightPaint2 );
            if ( lp != null && lp.has_cp ) {
              path = new Path();
              path.moveTo( lp.x1, lp.y1 );
              path.lineTo( lp.x2, lp.y2 );
              path.lineTo( x, y );
              path.addCircle( lp.x1, lp.y1, radius/2, Path.Direction.CCW );
              path.addCircle( lp.x2, lp.y2, radius/2, Path.Direction.CCW );
              path.transform( mMatrix );
              canvas.drawPath( path, BrushManager.highlightPaint3 );
            }
	    if ( item.mType == DrawingPath.DRAWING_PATH_LINE ) {
              Paint paint = BrushManager.fixedYellowPaint;
              DrawingLinePath line = (DrawingLinePath) item;
              lp = line.mFirst;
              LinePoint lpn = lp1;
              if ( lp == lp1 ) {
                paint = BrushManager.fixedOrangePaint;
                lpn = lp2;
              }
              path = new Path();
              path.moveTo( lp.x+line.mDx*10, lp.y+line.mDy*10 );
              path.lineTo( lp.x, lp.y );
              for ( lp = lp.mNext; lp != lpn && lp != null; lp = lp.mNext ) {
                if ( lp.has_cp ) {
                  path.cubicTo( lp.x1, lp.y1, lp.x2, lp.y2, lp.x, lp.y );
                } else {
                  path.lineTo( lp.x, lp.y );
                }
              }
              path.transform( mMatrix );
              canvas.drawPath( path, paint );
              if ( lp != null && lp != lp2 ) {
                path = new Path();
                path.moveTo( lp.x, lp.y );
                for ( lp = lp.mNext; lp != lp2 && lp != null; lp = lp.mNext ) {
                  if ( lp.has_cp ) {
                    path.cubicTo( lp.x1, lp.y1, lp.x2, lp.y2, lp.x, lp.y );
                  } else {
                    path.lineTo( lp.x, lp.y );
                  }
                }
                path.transform( mMatrix );
                canvas.drawPath( path, BrushManager.fixedOrangePaint );
              }
              if ( lp != null && lp.mNext != null ) {
                path = new Path();
                path.moveTo( lp.x, lp.y );
                for ( lp = lp.mNext; lp != null; lp = lp.mNext ) {
                  if ( lp.has_cp ) {
                    path.cubicTo( lp.x1, lp.y1, lp.x2, lp.y2, lp.x, lp.y );
                  } else {
                    path.lineTo( lp.x, lp.y );
                  }
                }
                path.transform( mMatrix );
                canvas.drawPath( path, BrushManager.fixedYellowPaint );
              }
	    } else if ( TDLevel.overExpert && mIsExtended && item.mType == DrawingPath.DRAWING_PATH_FIXED ) {
              path = new Path();
	      float w = mScale * TopoDroidApp.mDisplayWidth / 8; // TDSetting.mMinShift
	      switch ( item.getBlockExtend() ) {
                case -1:
	          path.moveTo( x, y ); 
	          path.lineTo( x+w, y );
                  break;
                case 1:
	          path.moveTo( x-w, y ); 
	          path.lineTo( x, y );
                  break;
		default:
	          path.moveTo( x-w, y ); 
	          path.lineTo( x+w, y );
              }
              path.transform( mMatrix );
              canvas.drawPath( path, BrushManager.fixedYellowPaint );
            }
          }
          radius = radius/3; // 2/zoom;
          for ( SelectionPoint pt : mSelected.mPoints ) {
            // float x, y;
            path = new Path();
            if ( pt.mPoint != null ) { // line-point
              path.addCircle( pt.mPoint.x, pt.mPoint.y, radius, Path.Direction.CCW );
            } else {
              path.addCircle( pt.mItem.cx, pt.mItem.cy, radius, Path.Direction.CCW );
            }
            path.transform( mMatrix );
            canvas.drawPath( path, BrushManager.highlightPaint );
          }
        }
      }  // synch( mSelectedLock ) mSelectionLock
    }
    synchronized( mGridStack1 ) {
      if ( mFirstReference != null )  mFirstReference.draw( canvas, mMatrix, mScale, null );
      if ( mSecondReference != null ) mSecondReference.draw( canvas, mMatrix, mScale, null );
    }

    if ( hasEraser ) {
      drawEraser( canvas );
    }
  }

  // boolean hasStationName( String name )
  // {
  //   if ( name == null ) return false;
  //   synchronized( mCurrentStack ) {
  //     final Iterator i = mCurrentStack.iterator();
  //     while ( i.hasNext() ){
  //       final ICanvasCommand cmd = (ICanvasCommand) i.next();
  //       if ( cmd.commandType() == 0 ) {
  //         DrawingPath p = (DrawingPath) cmd;
  //         if ( p.mType == DrawingPath.DRAWING_PATH_STATION ) {
  //           DrawingStationPath sp = (DrawingStationPath)p;
  //           if ( name.equals( sp.mName ) ) return true;
  //         }
  //       }
  //     }
  //   }
  //   return false;
  // }

  boolean hasMoreRedo()
  {
    return  mRedoStack.toArray().length > 0;
  }

  boolean hasMoreUndo()
  {
    // return  mCurrentStack.toArray().length > 0;
    return  mCurrentStack.size() > 0;
  }

  public void redo()
  {
    final int length = mRedoStack.toArray().length;
    if ( length > 0) {
      final ICanvasCommand cmd = mRedoStack.get(  length - 1  );
      mRedoStack.remove( length - 1 );

      if ( cmd.commandType() == 0 ) {
        DrawingPath redoCommand = (DrawingPath)cmd;
        synchronized( mCurrentStack ) {
          mCurrentStack.add( redoCommand );
        }
        synchronized( TDPath.mSelectionLock ) {
          mSelection.insertPath( redoCommand );
        }
      } else {
        EraseCommand eraseCmd = (EraseCommand) cmd;
        for ( EraseAction action : eraseCmd.mActions ) {
          DrawingPath path = action.mPath;
          // Log.v("DistoX", "REDO " + actionName[action.mType] + " path " + path.mType );
          if ( action.mInitialType == EraseAction.ERASE_INSERT ) {
            synchronized( mCurrentStack ) {
              mCurrentStack.add( path );
            }
            synchronized( TDPath.mSelectionLock ) {
              mSelection.insertPath( path );
            }
          } else if ( action.mType == EraseAction.ERASE_REMOVE ) {
            synchronized( mCurrentStack ) {
              mCurrentStack.remove( path );
            }
            synchronized( TDPath.mSelectionLock ) {
              mSelection.removePath( path );
            }
          } else if ( action.mType == EraseAction.ERASE_MODIFY ) {
            synchronized( TDPath.mSelectionLock ) {
              mSelection.removePath( path );
            }
            synchronized( mCurrentStack ) {
              action.restorePoints( false ); // false: use new points
            }
            synchronized( TDPath.mSelectionLock ) {
              mSelection.insertPath( path );
            }
          }
        }
        synchronized( mCurrentStack ) {
          mCurrentStack.add( cmd );
        }
      }
    }
    // checkLines();
  }

  boolean setRangeAt( float x, float y, float zoom, int type, float size )
  {
    SelectionPoint sp1 = mSelected.mHotItem;
    if ( sp1 == null ) {
      // Log.v("DistoX", "set range at: hotItem is null" );
      return false;
    }
    DrawingPath item = sp1.mItem;
    if ( item.mType != DrawingPath.DRAWING_PATH_LINE && item.mType != DrawingPath.DRAWING_PATH_AREA ) {
      // Log.v("DistoX", "set range at: item not line/area" );
      // mSelected.clear();
      return false;
    }

    if ( SelectionRange.isItem( type ) ) {
      DrawingPointLinePath path = (DrawingPointLinePath)item;
      sp1.setRangeTypeAndPoints( type, path.first(), path.last(), 0, 0 );
      return true;
    }

    float radius = TDSetting.mCloseCutoff + size / zoom;
    SelectionPoint sp2 = null;
    // synchronized ( TDPath.mSelectedLock ) {
    synchronized ( TDPath.mSelectionLock ) {
      sp2 = mSelection.selectOnItemAt( item, x, y, 4*radius );
    }
    if ( sp2 == null ) {
      // Log.v("DistoX", "set range at: select on Item return null");
      mSelected.clear();
      return false;
    }
    
    // range is sp1 -- sp2
    LinePoint lp1 = sp1.mPoint;
    LinePoint lp2 = sp2.mPoint;
    int cnt = 0;
    LinePoint lp = lp1;
    for ( ; lp != null; lp=lp.mNext ) { ++cnt; if ( lp == lp2 ) break; }
    if ( lp == null ) {
      cnt = 0;
      for ( lp=lp1; lp != null; lp=lp.mPrev ) { ++cnt; if ( lp == lp2 ) break; }
      if ( lp == null ) { // error
        // Log.v("DistoX", "set range at: error lp==null");
        return false;
      }
      lp = lp1; lp1 = lp2; lp2 = lp; // swap lp1 <--> lp2
    } 
    LinePoint lp0 = lp1;
    float d1 = 0;
    float d2 = 0;
    int c1 = 0;
    int c2 = 0;
    for ( int c = cnt/2; c > 0; --c ) {
      ++ c1;
      lp = lp0.mNext; 
      d1 += lp0.distance( lp );
      lp0 = lp;
    }
    LinePoint lp4 = lp0;
    for ( LinePoint lp3 = lp0.mNext; lp3 != null; lp3=lp3.mNext) {
      ++ c2;
      d2 += lp4.distance( lp3 );
      if ( lp3 == lp2 ) break;
      lp4 = lp3;
    }
    // Log.v("DistoX", "set range d1 " + d1 + " d2 " + d2 + " C " + cnt + " " + c1 + " " + c2 );
     
    // now make the range sp1 -- sp2 and the hotItem the midpoint
    SelectionPoint sp = mSelection.getSelectionPoint( lp0 ); 
    sp.setRangeTypeAndPoints( type, lp1, lp2, d1, d2 );

    mSelected.clear();
    mSelected.addPoint( sp );
    mSelected.mHotItem = sp;

    return true;
  }

    
  SelectionSet getItemsAt( float x, float y, float zoom, int mode, float size, DrawingStationSplay station_splay )
  {
    float radius = TDSetting.mCloseCutoff + size/zoom; // TDSetting.mSelectness / zoom;
    // Log.v( "DistoX", "getItemAt " + x + " " + y + " zoom " + zoom + " mode " + mode + " size " + size + " " + radius );
    boolean legs   = (mDisplayMode & DisplayMode.DISPLAY_LEG) != 0;
    boolean splays = (mDisplayMode & DisplayMode.DISPLAY_SPLAY ) != 0;
    // boolean latest = (mDisplayMode & DisplayMode.DISPLAY_LATEST ) != 0;
    boolean stations = (mDisplayMode & DisplayMode.DISPLAY_STATION ) != 0;
    // synchronized ( TDPath.mSelectedLock ) {
    synchronized ( TDPath.mSelectionLock ) {
      mSelected.clear();
      // FIXME_LATEST latests splays are not considered in the selection
      mSelection.selectAt( mSelected, x, y, radius, mode, legs, splays, stations, station_splay ); 
      if ( mSelected.mPoints.size() > 0 ) {
        // Log.v("DistoX", "seleceted " + mSelected.mPoints.size() + " points " );
        mSelected.nextHotItem();
      }
    }
    return mSelected;
  }
    
  void addItemAt( float x, float y, float zoom, float size )
  {
    float radius = TDSetting.mCloseCutoff + size/zoom; // TDSetting.mSelectness / zoom;
    // Log.v( "DistoX", "getItemAt " + x + " " + y + " zoom " + zoom + " mode " + mode + " size " + size + " " + radius );
    synchronized ( TDPath.mSelectionLock ) {
      mSelected.clear();
      mSelection.selectAt( mSelected, x, y, radius, mMultiselectionType );
      for ( SelectionPoint sp : mSelected.mPoints ) {
        addMultiselection( sp.mItem );
      }
    }
  }

  void splitPointHotItem()
  { 
    SelectionPoint sp = mSelected.mHotItem;
    if ( sp == null ) return;
    if ( sp.type() != DrawingPath.DRAWING_PATH_LINE && sp.type() != DrawingPath.DRAWING_PATH_AREA ) return;
    LinePoint lp = sp.mPoint;
    if ( lp == null ) return;
    float x = lp.x;
    float y = lp.y;
    DrawingPointLinePath line = (DrawingPointLinePath)sp.mItem;
    LinePoint p1 = line.insertPointAfter( x, y, lp );
    SelectionPoint sp1 = null;
    synchronized( TDPath.mSelectionLock ) {
      sp1 = mSelection.insertPathPoint( line, p1 );
    }
    if ( sp1 != null ) {
      // synchronized( TDPath.mSelectedLock ) {
      synchronized( TDPath.mSelectionLock ) {
        mSelected.mPoints.add( sp1 );
      }
    }
  }

  /** insert points in the range of the selected point
   */
  void insertPointsHotItem()
  {
    SelectionPoint sp = mSelected.mHotItem;
    if ( sp == null ) return;
    if ( sp.type() != DrawingPath.DRAWING_PATH_LINE && sp.type() != DrawingPath.DRAWING_PATH_AREA ) return;
    if ( sp.mRange == null ) return;
    LinePoint lp1 = sp.mRange.start();
    if ( lp1 == null ) return;
    LinePoint lp2 = sp.mRange.end();
    DrawingPointLinePath line = (DrawingPointLinePath)sp.mItem;

    // lp0 if the point after lp1 - lp is inserted as midpoint between lp1 and lp0
    for ( LinePoint lp0 = lp1.mNext; lp1 != lp2 && lp0 != null; lp0 = lp0.mNext ) {
      float x = (lp1.x + lp0.x)/2;
      float y = (lp1.y + lp0.y)/2;
      LinePoint lp = line.insertPointAfter( x, y, lp1 ); 
      SelectionPoint sp1 = mSelection.insertPathPoint( line, lp );
      lp1 = lp0;
    } 
    syncClearSelected();
  }

  // moved to methods of LinePoint
  // private float orthoProject( LinePoint q, LinePoint p0, LinePoint p1 )
  // {
  //   float x01 = p1.x - p0.x;
  //   float y01 = p1.y - p0.y;
  //   return ((q.x-p0.x)*x01 + (q.y-p0.y)*y01) / ( x01*x01 + y01*y01 );
  // }
  // private float orthoDistance( LinePoint q, LinePoint p0, LinePoint p1 )
  // {
  //   float x01 = p1.x - p0.x;
  //   float y01 = p1.y - p0.y;
  //   return TDMath.abs( (q.x-p0.x)*y01 - (q.y-p0.y)*x01 ) / TDMath.sqrt( x01*x01 + y01*y01 );
  // }
      
  boolean moveHotItemToNearestPoint( float dmin )
  {
    SelectionPoint sp = mSelected.mHotItem;
    if ( sp == null ) return false;
    float x = 0.0f;
    float y = 0.0f;
    if ( sp.type() == DrawingPath.DRAWING_PATH_POINT ) {
      x = sp.mItem.cx;
      y = sp.mItem.cy;
    } else if ( sp.type() == DrawingPath.DRAWING_PATH_LINE || sp.type() == DrawingPath.DRAWING_PATH_AREA ) {
      x = sp.mPoint.x;
      y = sp.mPoint.y;
    } else {
      return false;
    }
    SelectionPoint spmin = mSelection.getNearestPoint( sp, x, y, dmin );

    if ( spmin != null ) {
      if ( spmin.type() == DrawingPath.DRAWING_PATH_LINE || spmin.type() == DrawingPath.DRAWING_PATH_AREA ) {
        x = spmin.mPoint.x - x;
        y = spmin.mPoint.y - y;
      } else {
        x = spmin.mItem.cx - x;
        y = spmin.mItem.cy - y;
      }
      // sp.shiftBy( x, y, 0f );
      sp.shiftBy( x, y );
    }
    return true;
  }

  boolean appendHotItemToNearestLine()
  {
    SelectionPoint sp = mSelected.mHotItem;
    if ( sp == null ) return false;
    if ( sp.type() != DrawingPath.DRAWING_PATH_LINE ) return false;
    if ( BrushManager.isLineSection( sp.type() ) ) return false; // NOT for "section" lines
    LinePoint pt1 = sp.mPoint;
    DrawingLinePath line1 = (DrawingLinePath)sp.mItem;
    if ( pt1 != line1.mFirst && pt1 != line1.mLast ) return false;

    int linetype = line1.mLineType;

    float x = 0.0f;
    float y = 0.0f;
    x = sp.mPoint.x;
    y = sp.mPoint.y;
    
    SelectionPoint spmin = mSelection.getNearestLineEndPoint( sp, x, y, 10f, linetype );
    if ( spmin == null ) return false;

    LinePoint pt2 = spmin.mPoint; // MERGE this line with "linemin"
    DrawingLinePath line2 = (DrawingLinePath)spmin.mItem;
    synchronized ( TDPath.mSelectionLock ) {
      mSelection.removePath( line2 );
      mSelection.removePath( line1 );
    }

    boolean reverse1 = ( pt1 == line1.mLast );
    boolean reverse2 = ( pt2 == line2.mFirst );
    synchronized( mCurrentStack ) {
      if ( reverse2 ) line2.reversePath();
      if ( reverse1 ) line1.reversePath();
      LinePoint pt = line1.mFirst; // append to end
      while ( pt != null ) {
        if ( pt.has_cp ) {
          line2.addPoint3( pt.x1, pt.y1, pt.x2, pt.y2, pt.x, pt.y );
        } else {
          line2.addPoint( pt.x, pt.y );
        }
        pt = pt.mNext;
      }
      if ( reverse1 ) line1.reversePath();
      mCurrentStack.remove( line1 );
      if ( reverse2 ) {
        line2.reversePath();
        line2.retracePath();
      }
    }
    synchronized ( TDPath.mSelectionLock ) {
      mSelection.insertPath( line2 );
      mSelected.clear();
    }
    return true;
  }

  class NearbySplay
  {
    final float dx, dy;
    final float d; // distance from point
    final LinePoint pt; // point
    float llen, rlen;

    NearbySplay( float xx, float yy, float dd, LinePoint lp )
    {
      dx = xx;
      dy = yy;
      d  = dd;
      pt = lp;
    }
  }
  
  // return 0 ok
  //       -1 no hot item
  //       -2 not line
  //       -3 no splay
  int snapHotItemToNearestSplays( float dthr, DrawingStationSplay station_splay )
  {
    SelectionPoint sp = mSelected.mHotItem;
    if ( sp == null ) return -1;
    if ( sp.type() != DrawingPath.DRAWING_PATH_LINE ) return -2;

    DrawingPath item = sp.mItem;
    DrawingLinePath line = (DrawingLinePath)item;

    boolean splays = (mDisplayMode & DisplayMode.DISPLAY_SPLAY  ) != 0;
    boolean latest = (mDisplayMode & DisplayMode.DISPLAY_LATEST ) != 0;
    // nearby splays are the splays that get close enough (dthr) to the line
    ArrayList< NearbySplay > nearby_splays = new ArrayList<>();
    for ( DrawingPath fxd : mSplaysStack ) {
      if ( splays ) {
	if ( station_splay.isStationOFF( fxd ) ) continue;
      } else {
	if ( ! ( station_splay.isStationON( fxd ) || ( latest && fxd.isBlockRecent() ) ) ) continue;
      }
      float x = fxd.x2;
      float y = fxd.y2;
      float dmin = dthr;
      LinePoint lpmin = null;
      for ( LinePoint lp2 = line.mFirst; lp2 != null; lp2=lp2.mNext ) {
        float d = lp2.distance( x, y );
        if ( d < dmin ) {
          dmin = d;
          lpmin = lp2;
        } else if ( lpmin != null ) { // if distances increase after a good min, break
          nearby_splays.add( new NearbySplay( fxd.x2 - lpmin.x, fxd.y2 - lpmin.y, dmin, lpmin ) );
          break;
        }
      }
    }
    // Log.v("DistoX", "Nearby splays " + nearby_splays.size() + " line size " + line.size() );
    int ks = nearby_splays.size();
    if ( ks == 0 ) return -3;
    // check that two nearby splays do not have the same linepoint
    for ( int k1 = 0; k1 < ks; ) {
      NearbySplay nbs1 = nearby_splays.get( k1 );
      int dk1 = 1; // increment of k1
      int k2 = k1+1;
      while ( k2<ks ) {
        NearbySplay nbs2 = nearby_splays.get( k2 );
        if ( nbs1.pt == nbs2.pt ) {
          ks --;
          if ( nbs1.d <= nbs2.d ) {
            nearby_splays.remove( k2 );
          } else {
            nearby_splays.remove( k1 );
            dk1 = 0;
            break;
          }
        } else {
          k2 ++;
        }
      }
      k1 += dk1;
    }
    // Log.v("DistoX", "Nearby splays " + nearby_splays.size() + " / " + ks );

    // compute distances between consecutive line points
    // and order nearby_splays following the line path
    int k = 0; // partition of unity
    float len = 0.001f;
    LinePoint lp1 = line.mFirst;
    int size = line.size();
    float[] dist = new float[ size ];
    int k0 = 0;
    for ( LinePoint lp2 = line.mFirst; lp2 != null; lp2 = lp2.mNext ) {
      dist[k0] = lp1.distance( lp2 );
      len += dist[k0];
      ++k0;

      int kk = k;
      for ( ; kk<ks; ++kk ) {
        if ( lp2 == nearby_splays.get(kk).pt ) {
          if ( kk != k ) { // swap nearby_splays k <--> kk
            NearbySplay nbs = nearby_splays.remove( kk );
            nearby_splays.add( k, nbs );
          }
          nearby_splays.get(k).llen = len;
          if ( k > 0 ) nearby_splays.get( k-1 ).rlen = len;
          len = 0;
          ++ k;
          break;
        }
      }
      lp1 = lp2; // lp1 = previous point
    }
    len += 0.001f;
    nearby_splays.get( k-1 ).rlen = len;

    //   |----------*--------*-----
    //      llen   sp1 rlen
    //                 llen sp2 rlen

    k0 = 0;
    int kl = -1;
    int kr = 0;
    len = 0;
    LinePoint lp2 = line.mFirst;
    NearbySplay spr = null; // right splay
    for ( NearbySplay spl : nearby_splays ) { // left splay
      while ( lp2 != spl.pt /* && lp2 != null && k0 < size */ ) { // N.B. lp2 must be non-null and k0 must be < size
        len += dist[k0];
        float dx = len/spl.llen * spl.dx;
        float dy = len/spl.llen * spl.dy;
        if ( spr != null ) {
          dx += (1 - len/spr.rlen) * spr.dx;
          dy += (1 - len/spr.rlen) * spr.dy;
        }
        lp2.shiftBy( dx, dy );
        lp2 = lp2.mNext;
        ++ k0;
      }
      // if ( lp2 == spl.pt ) { // this must be true
        lp2.shiftBy( spl.dx, spl.dy );
        lp2 = lp2.mNext;
      // }
      spr = spl;
      // if ( k0 >= size ) break;
      ++ k0;
      len = 0;
    }
    if ( spr != null ) { // always true
      while ( lp2 != null /* && k0 < size */ ) { // N.B. k0 must be < size
        len += dist[k0];
        float dx = (1 - len/spr.rlen) * spr.dx;
        float dy = (1 - len/spr.rlen) * spr.dy;
        lp2.shiftBy( dx, dy );
        lp2 = lp2.mNext;
        ++ k0;
      }
    }
    line.retracePath();

    return 0;
  }

  // return error codes
  //  -1   no selected point
  //  -2   selected point not on area border
  //  -3   no close line
  //  +1   only a point: nothing to follow
  //
  int snapHotItemToNearestLine()
  {
    SelectionPoint sp = mSelected.mHotItem;

    // no selected point or selected point not on area border:
    if ( sp == null ) return -1;
    if ( sp.type() != DrawingPath.DRAWING_PATH_AREA ) return -2;

    DrawingPath item = sp.mItem;
    DrawingAreaPath area = (DrawingAreaPath)item;
    LinePoint q0 = sp.mPoint;
    LinePoint q1 = area.next( q0 ); // next point on the area border
    LinePoint q2 = area.prev( q0 ); // previous point on the border
    // area border: ... --> q2 --> q0 --> q1 --> ...

    float x = q0.x;
    float y = q0.y;
    float thr = 10f;
    float dmin = thr; // require a minimum distance
    DrawingPointLinePath lmin = null;
    boolean min_is_area = false;
    // int kk0 = -1;

    // find drawing path with minimal distance from (x,y)
    LinePoint pp0 = null;

    for ( ICanvasCommand cmd : mCurrentStack ) {
      if ( cmd.commandType() != 0 ) continue;
      DrawingPath p = (DrawingPath)cmd;
      if ( p == item ) continue;
      if ( p.mType != DrawingPath.DRAWING_PATH_LINE &&
           p.mType != DrawingPath.DRAWING_PATH_AREA ) continue;
      DrawingPointLinePath lp = (DrawingPointLinePath)p;
      int ks = lp.size();
      for ( LinePoint pt = lp.mFirst; pt != null && ks > 0; pt = pt.mNext )
      {
        -- ks;
        // float d = pts.get(k).distance( x, y );
        float d = pt.distance( x, y );
        if ( d < dmin ) {
          dmin = d;
          // kk0 = k;
          pp0  = pt;
          lmin = lp;
          min_is_area = ( p.mType == DrawingPath.DRAWING_PATH_AREA );
        }
      }
    }
    if ( lmin == null ) return -3;
    int cmax = area.size() + 1;
    
    // if ( TDLog.LOG_DEBUG ) { // ===== FIRST SET OF LOGS
    //   TDLog.Debug( "snap to line");
    //   for ( LinePoint pt = lmin.mFirst; pt!=null; pt=pt.mNext ) TDLog.Debug( pt.x + " " + pt.y );
    //   TDLog.Debug( "snap area");
    //   for ( LinePoint pt = area.mFirst; pt!=null; pt=pt.mNext ) TDLog.Debug( pt.x + " " + pt.y );
    //   TDLog.Debug( "snap qq0= " + q0.x + " " + q0.y + " to pp0= " + pp0.x + " " + pp0.y );
    // }

    int ret = 0; // return code

    LinePoint pp1 = lmin.next( pp0 );
    LinePoint pp2 = lmin.prev( pp0 );
    //
    // lmin: ... ---> pp2 ---> pp0 ---> pp1 --->
    // area: ...      q2 ----> q0 ----> q1 ...
    //                qq2 ------------> qq1          FORWARD
    //
    // area: ...      q1 <---- q0 <---- q2 ...
    //                qq2 ------------> qq1          REVERSE

    LinePoint pp10 = null; // current point forward
    LinePoint pp20 = null; // current point backward
    // LinePoint pp1  = null; // next point forward
    // LinePoint pp2  = null; // prev point backwrad
    LinePoint qq10 = null;
    LinePoint qq20 = null;
    LinePoint qq1 = null;
    LinePoint qq2 = null;
    boolean reverse = false;
    int step = 1;
    // if ( kk1 >= 0 ) 
    if ( pp1 != null ) { 
      // TDLog.Debug( "snap pp1 " + pp1.x + " " + pp1.y + " FOLLOW LINE FORWARD" );
      // pp1  = pts1.get( kk1 );
      // pp10 = pts1.get( kk0 );
      pp10 = pp0;
      // if ( kk2 >= 0 ) 
      if ( pp2 != null ) {
        // TDLog.Debug( "snap pp2 " + pp2.x + " " + pp2.y );
        // pp2  = pts1.get( kk2 ); 
        // pp20 = pts1.get( kk0 ); 
        pp20 = pp0;
      }
      if ( pp1.distance( q1 ) < pp1.distance( q2 ) ) {
        qq1  = q1; // follow border forward
        qq10 = q0;
        // TDLog.Debug( "snap qq1 " + qq1.x + " " + qq1.y + " follow border forward" );
        if ( pp2 != null ) {
          qq2  = q2;
          qq20 = q0;
          // TDLog.Debug( "snap qq2 " + qq2.x + " " + qq2.y );
        }
      } else {
        reverse = true;
        qq1  = q2; // follow border backward
        qq10 = q0;
        // TDLog.Debug( "snap reverse qq1 " + qq1.x + " " + qq1.y + " follow border backward" );
        if ( pp2 != null ) {
          qq2 = q1;
          qq20 = q0;
          // TDLog.Debug( "snap qq2 " + qq2.x + " " + qq2.y + " follow forward");
        }
      }
    } else if ( pp2 != null ) { // pp10 is null
      // pp2  = pts1.get( kk2 ); 
      // pp20 = pts1.get( kk0 ); 
      pp20 = pp0;
      // TDLog.Debug( "snap pp1 null pp2 " + pp2.x + " " + pp2.y + " FOLLOW LINE BACKWARD" );
      if ( pp2.distance( q2 ) < pp2.distance( q1 ) ) {
        qq2 = q2;
        qq20 = q0;
        // TDLog.Debug( "snap qq2 " + qq2.x + " " + qq2.y + " follow border backward" );
      } else {
        reverse = true;
        qq2 = q1;
        qq20 = q0;
        // TDLog.Debug( "snap reverse qq2 " + qq2.x + " " + qq2.y + " follow border forward" );
      }
    } else {  // pp10 and pp20 are null: nothing to follow
      // copy pp0 to q0
      q0.x = pp0.x;
      q0.y = pp0.y;
      ret = 1;
    }

    if ( qq1 != null ) {
      // TDLog.Debug( "qq1 not null " + qq1.x + " " + qq1.y + " reverse " + reverse );
      // follow line pp10 --> pp1 --> ... using step 1
      // with border qq10 --> qq1 --> ... using step delta1
      //
      // lmin: ... ---> pp2 ---> pp0 ---> pp1 --->
      //                         pp10 --> pp1 --->
      // area: ...      q2 ----> q0 ----> q1 ...
      //                qq2 ------------> qq1          FORWARD

      for (int c=0; c<cmax; ++c) { // try to move qq1 forward
        // TDLog.Debug( "snap at qq1 " + qq1.x + " " + qq1.y );
        float s = qq1.orthoProject( pp10, pp1 );
        while ( s > 1.0 ) {
          pp10 = pp1;
          // TDLog.Debug( "snap follow pp10 " + pp10.x + " " + pp10.y );
          pp1  = lmin.next( pp1 );
          if ( pp1 == null ) {
            // TDLog.Debug( "snap end of line pp1 null, pp10 " + pp10.x + " " + pp10.y );
            break;
          }
          if ( pp1 == pp0 ) {
            // TDLog.Debug( "snap pp1 == pp0, pp10 " + pp10.x + " " + pp10.y );
            break;
          }
          s = qq1.orthoProject( pp10, pp1 );
        }
        if ( pp1 == null ) break;
        float d1 = qq1.orthoDistance( pp10, pp1 );
        // TDLog.Debug( "distance d1 " + d1 + " s " + s );

        if ( s < 0.0f ) break;
        if ( d1 > thr || d1 < 0.001f ) break; 
        qq10 = qq1;
        qq1 = (reverse)? area.prev(qq1) : area.next( qq1 );
        if ( qq1 == q0 ) break;
      }
    } else {
      // TDLog.Debug( "snap qq1 null" );
      qq10 = q0; // FIXME
    }
    // if ( qq10 != null && pp10 != null ) {
    //   TDLog.Debug( "QQ10 " + qq10.x + " " + qq10.y + " PP10 " + pp10.x + " " + pp10.y );
    // }

    if ( qq2 != null ) {
      // TDLog.Debug( "qq2 not null: " + qq2.x + " " + qq2.y + " reverse " + reverse );
      // follow line pp20 --> pp2 --> ... using step size1-1
      // with border qq20 --> qq2 --> ... using step delta2
      for (int c=0; c < cmax; ++c) { // try to move qq2 backward
        // TDLog.Debug( "snap at qq2 " + qq2.x + " " + qq2.y );
        float s = qq2.orthoProject( pp20, pp2 );
        while ( s > 1.0 ) {
          pp20 = pp2;
          // TDLog.Debug( "snap s>1, follow pp20 " + pp20.x + " " + pp20.y );
          pp2 = lmin.prev( pp2 );
          if ( pp2 == null ) {
            // TDLog.Debug( "snap end of line pp2 null, pp20 " + pp20.x + " " + pp20.y );
            break;
          }
          if ( pp2 == pp0 ) {
            // TDLog.Debug( "snap pp2 == pp0, pp20 " + pp20.x + " " + pp20.y );
            break;
          }
          s = qq2.orthoProject( pp20, pp2 );
        }
        if ( pp2 == null ) break;
        float d2 = qq2.orthoDistance( pp20, pp2 );
        // TDLog.Debug( "distance qq2-P_line " + d2 + " s " + s );

        if ( s < 0.0f ) break;
        if ( d2 > thr || d2 < 0.001f ) break; 
        qq20 = qq2;
        qq2 = (reverse)? area.next(qq2) : area.prev( qq2 );
        if ( qq2 == q0 ) break;
      }
    } else {
      // TDLog.Debug( "snap qq2 null");
      qq20 = q0; // FIXME
    }
    // if ( qq20 != null && pp20 != null ) {
    //   TDLog.Debug( "QQ20 " + qq20.x + " " + qq20.y + " PP20 " + pp20.x + " " + pp20.y );
    // }

    if ( qq20 == qq10 || (reverse && pp10 == null) || (!reverse && pp20 == null) ) {
      // should not happen, anyways copy pp0 to q0
      q0.x = pp0.x;
      q0.y = pp0.y;
      ret = 2;
    }

    synchronized( mCurrentStack ) {
      if ( ret == 0 ) { 
        synchronized( TDPath.mSelectionLock ) {
          mSelection.removePath( area );
        }
        // next-prev refer to the point list along the area path.
        LinePoint next = qq10.mNext; // unlink qq20 -> ... -> qq10
        LinePoint prev = qq20.mPrev;
        if ( reverse ) {             // unlink qq10 -> ... -> qq20
          next = qq20.mNext;
          prev = qq10.mPrev;
        } 

        if ( prev == null ) {
          area.mFirst = null; // ( reverse )? qq10 : qq20;
          // TDLog.Debug( "snap setting area FIRST null ");
        } else {
          // TDLog.Debug( "snap start prev " + prev.x + " " + prev.y );
          LinePoint q = prev;
          while ( prev != null && prev != next ) {
            q = prev;
            prev = q.mPrev;
          }
          area.mFirst = q;
          if ( q.mPrev != null ) { // make sure first has no prev
            q.mPrev.mNext = null;
          }
          q.mPrev = null;
          // TDLog.Debug( "snap setting area FIRST " + area.mFirst.x + " " + area.mFirst.y );
        }

        if ( next == null ) {
          area.mLast = null; // ( reverse )? qq20 : qq10;
          // TDLog.Debug( "snap setting area LAST null ");
        } else {
          // TDLog.Debug( "snap start next " + next.x + " " + next.y );
          LinePoint q = next;
          while ( next != null && next != prev ) {
            q = next;
            next = q.mNext;
          }
          area.mLast = q;
          if ( q.mNext != null ) {
            q.mNext.mPrev = null;
          }
          q.mNext = null;
          // TDLog.Debug( "snap setting area LAST " + area.mLast.x + " " + area.mLast.y );
        }

        next = (reverse)? qq20 : qq10; // where to close the snapped portion
        prev = (reverse)? qq10 : qq20; // where to start the snapped portion
        // it can be qq10.next == qq20 (forward)
        if ( next.mNext == prev ) {
          for ( LinePoint qc = prev; qc != null && qc != next; ) {
            LinePoint qn = qc.mNext;
            if ( qn != null ) qn.mPrev = null;
            qc.mNext = null;
            qc = qn;
          }
          area.mFirst = next;
          area.mLast  = prev;
          if ( area.mFirst != null ) { // always true: area.mFirst == next != null
            area.mFirst.mNext = area.mLast;
            area.mFirst.mPrev = null;
            if ( area.mLast != null ) // always true [?]
            {
              area.mLast.mPrev = area.mFirst;
              area.mLast.mNext = null;
            } else { 
              area.mLast = area.mFirst;
            }
          } else { 
            area.mFirst = area.mLast;
            if ( area.mFirst != null ) area.mFirst.mNext = area.mFirst.mPrev = null;
          }
        }

        // insert points pp20 - ... - pp10 (included)
        if ( reverse ) {
          LinePoint q = qq10.mPrev;
          LinePoint p = pp10;
          // if ( q != null ) {
          //   // TDLog.Debug( "snap attach at " + q.x + " " + q.y );
          // } else {
          //   // TDLog.Debug( "snap restart area ");
          // }
          q = new LinePoint( p.x, p.y, q );
          // TDLog.Debug( "snap first new point " + q.x + " " + q.y );
          if ( p != pp20 ) {
            p = p.mPrev;
            if ( area.mFirst == null ) area.mFirst = q;
            for ( ; p != null && p != pp20; p = p.mPrev ) {
              if ( p.has_cp && p != pp10 ) {
                LinePoint pp = p.mNext;
                q = new LinePoint( pp.x2, pp.y2, pp.x1, pp.y1, p.x, p.y, q );
              } else {
                q = new LinePoint( p.x, p.y, q );
              }
              // TDLog.Debug( "snap new point " + q.x + " " + q.y );
            }
            if ( p != null ) { // FIXME add last point
              if ( p.has_cp ) {
                LinePoint pp = p.mNext;
                q = new LinePoint( pp.x2, pp.y2, pp.x1, pp.y1, p.x, p.y, q );
              } else {
                q = new LinePoint( p.x, p.y, q );
              }
              // TDLog.Debug( "snap last new point " + q.x + " " + q.y );
            }
          }
          q.mNext = next;
          if ( next != null ) { // always true [?]
            next.mPrev  = q;
            next.has_cp = false; // enforce straight segment
          }
          if ( area.mLast == null ) area.mLast = q;

        } else { // not reverse

          LinePoint q = qq20.mPrev;
          LinePoint p = pp20;
          // if ( q != null ) {
          //   // TDLog.Debug( "snap attach at " + q.x + " " + q.y );
          // } else {
          //   // TDLog.Debug( "snap restart area ");
          // }
          q = new LinePoint( p.x, p.y, q );
          // TDLog.Debug( "snap first new point " + q.x + " " + q.y );
          if ( p != pp10 ) {
            p = p.mNext;
            if ( area.mFirst == null ) area.mFirst = q;
            for ( ; p != null && p != pp10; p = p.mNext ) {
              q = new LinePoint( p, q );
              // TDLog.Debug( "snap new point " + q.x + " " + q.y );
            }
            // if ( p != null ) { // FIXME not add "last" point
            //   q = new LinePoint( p, q );
            //   TDLog.Debug( "snap last new point " + q.x + " " + q.y );
            // }
          }
          q.mNext = next;
          if ( next != null ) { // always true [?]
            next.mPrev  = q;
            next.has_cp = false;
          }
          if ( area.mLast == null ) area.mLast = q;
        }


        // if ( area.mLast == area.mFirst ) { // avoid circular closed border
        //   area.mLast = area.mLast.mPrev;
        //   area.mLast.mNext = null;
        //   area.mFirst.mPrev = null;
        // }

        area.recount(); 
        // TDLog.Debug( "snap new size " + area.size() );
      }

      // area.mPoints = pts2;
      area.retracePath();
      
      if ( ret == 0 ) {
        synchronized( TDPath.mSelectionLock ) {
          mSelection.insertPath( area );
        }
      }
      syncClearSelected();
    }
    // checkLines();
    return ret;
  }

  SelectionPoint hotItem() { return mSelected.mHotItem; }

  boolean hasSelected() { return mSelected.mPoints.size() > 0; }

  void rotateHotItem( float dy )
  { 
    synchronized( TDPath.mSelectionLock ) {
      mSelected.rotateHotItem( dy );
    }
  }

  // void shiftHotItem( float dx, float dy, float range ) 
  void shiftHotItem( float dx, float dy )
  { 
    synchronized( TDPath.mSelectionLock ) {
      // SelectionPoint sp = mSelected.shiftHotItem( dx, dy, range );
      SelectionPoint sp = mSelected.shiftHotItem( dx, dy );
      if ( sp != null ) {
        DrawingPath path = sp.mItem;
        if ( path.mType == DrawingPath.DRAWING_PATH_POINT ) {
          DrawingPointPath pt = (DrawingPointPath)path;
          if ( BrushManager.isPointSection( pt.mPointType )  ) {
            String scrap_name = pt.getOption( "-scrap" );
            if ( scrap_name != null ) {
              shiftXSectionOutline( scrap_name, dx, dy );
            }
          }
        }
        mSelection.checkBucket( sp );
      }
    }
  }

  SelectionPoint nextHotItem() { return mSelected.nextHotItem(); }
  SelectionPoint prevHotItem() { return mSelected.prevHotItem(); }

  // used by flipProfile
  // void rebuildSelection()
  // {
  //   Selection selection = new Selection();
  //   synchronized ( mCurrentStack ) {
  //     final Iterator i = mCurrentStack.iterator();
  //     while ( i.hasNext() ) {
  //       final ICanvasCommand cmd = (ICanvasCommand) i.next();
  //       if ( cmd.commandType() != 0 ) continue;
  //       DrawingPath path = (DrawingPath) cmd;
  //       selection.insertPath( path );
  //       // switch ( path.mType ) {
  //       //   case DrawingPath.DRAWING_PATH_POINT:
  //       //   case DrawingPath.DRAWING_PATH_LINE;
  //       //   case DrawingPath.DRAWING_PATH_AREA:
  //       //     selection.insertPath( path );
  //       //     break;
  //       // }
  //     }
  //   }
  //   mSelection = selection;
  // }

  private RectF computeBBox() 
  {
    float xmin=1000000f, xmax=-1000000f, 
          ymin=1000000f, ymax=-1000000f;
    synchronized( mCurrentStack ) {
      for ( ICanvasCommand cmd : mCurrentStack ) {
        if ( cmd.commandType() != 0 ) continue;
        DrawingPath p = (DrawingPath) cmd;
        // RectF bbox = p.mBBox;
        if ( p.left   < xmin ) xmin = p.left;
        if ( p.right  > xmax ) xmax = p.right;
        if ( p.top    < ymin ) ymin = p.top;
        if ( p.bottom > ymax ) ymax = p.bottom;
      }
    }
    return new RectF( xmin, ymin, xmax, ymax ); // left top right bottom
  }

  // FIXME DataHelper and SID are necessary to export splays by the station
  // @param full_name   file name without extension, which is also scrap_name for single scrap 
  void exportTherion( int type, BufferedWriter out, String full_name, String proj_name, int proj_dir, boolean multiscrap )
  {
    if ( multiscrap ) {
      // Log.v("DistoXX", "multi scrap export stack size " + mCurrentStack.size() );
      // BBox computed by export multiscrap
      DrawingIO.exportTherionMultiScrap( type, out, full_name, proj_name, proj_dir,
		      /* bbox, mNorthLine, */ mCurrentStack, mUserStations, mStations, mSplaysStack );
    } else {
      RectF bbox = computeBBox();
      DrawingIO.exportTherion( type, out, full_name, proj_name, proj_dir,
		      bbox, mNorthLine, mCurrentStack, mUserStations, mStations, mSplaysStack );
    }
  }
   
  void exportDataStream( int type, DataOutputStream dos, String scrap_name, int proj_dir )
  {
    RectF bbox = computeBBox();
    DrawingIO.exportDataStream( type, dos, scrap_name, proj_dir, bbox, mNorthLine, mCurrentStack, mUserStations, mStations );
  }

  void exportAsCsx( PrintWriter pw, String survey, String cave, String branch, /* String session, */
                    List<PlotInfo> all_sections, List<PlotInfo> sections /* , DrawingUtil drawingUtil */ )
  {
    ArrayList< DrawingPath > paths = new ArrayList<>();
    synchronized( mCurrentStack ) {
      for ( ICanvasCommand cmd : mCurrentStack ) {
        if ( cmd.commandType() == 0 ) paths.add( (DrawingPath) cmd );
      }
      DrawingIO.doExportAsCsx( pw, survey, cave, branch, /* session, */ null, paths, all_sections, sections /* , drawingUtil */ ); // bind=null
    }
  }

  DrawingAudioPath getAudioPoint( long bid )
  {
    synchronized( mCurrentStack ) {
      for ( ICanvasCommand cmd : mCurrentStack ) {
        if ( cmd.commandType() == 0 ) {
          DrawingPath path = (DrawingPath)cmd;
          if ( path.mType == DrawingPath.DRAWING_PATH_POINT ) {
            DrawingPointPath pt = (DrawingPointPath)path;
            if ( BrushManager.isPointAudio( pt.mPointType ) ) {
              DrawingAudioPath audio = (DrawingAudioPath)pt;
              if ( audio.mId == bid ) return audio;
            }
          }
        }
      }
    }
    return null;
  }
 
  // called by DrawingSurface.addDrawingStationName
  void addStation( DrawingStationName st, boolean selectable )
  {
    // Log.v("DistoX", "add station " + st.getName() + " scene " + st.cx + " " + st.cy + " XSection " + st.mXSectionType );
    synchronized( mStations ) {
      mStations.add( st );
      if ( selectable ) {
        synchronized( TDPath.mSelectionLock ) {
          // Log.v( "DistoX", "selection add station " + st.getName() );
          mSelection.insertStationName( st );
        }
      }
    }
  }

  // this is not efficient: the station names should be stored in a tree (key = name) for log-time search
  // type = type of the plot
  void setStationXSections( List<PlotInfo> xsections, long type )
  {
    for ( DrawingStationName st : mStations ) {
      String name = st.getName();
      // Log.v( "DistoX", "Station <" + name + ">" );
      for ( PlotInfo plot : xsections ) {
        if ( name.equals( plot.start ) ) {
          st.setXSection( plot.azimuth, plot.clino, type );
          break;
        }
      }
    }
  }

  float computeSectionArea()
  {
    float ret = 0;
    for ( ICanvasCommand icc : mCurrentStack ) {
      if ( icc.commandType() != 0 ) continue;
      DrawingPath p = (DrawingPath)icc;
      if ( p.mType != DrawingPath.DRAWING_PATH_LINE ) continue;
      DrawingLinePath lp = (DrawingLinePath)p;
      if ( lp.mLineType != BrushManager.mLineLib.mLineWallIndex ) continue;
      LinePoint pt = lp.mFirst;
      while ( pt != lp.mLast ) {
        LinePoint pn = pt.mNext;
        ret += pt.y * pn.x - pt.x * pn.y;
        pt = pn;
      }
    }
    return ret / 2;
  }

  void linkSections()
  {
    synchronized( mCurrentStack ) {
      for ( ICanvasCommand cmd : mCurrentStack ) {
        if ( cmd.commandType() != 0 ) continue; 
        DrawingPath p = (DrawingPath)cmd;
        if ( p.mType != DrawingPath.DRAWING_PATH_POINT ) continue;
        DrawingPointPath pt = (DrawingPointPath)p;
        if ( ! BrushManager.isPointSection( pt.mPointType ) ) continue;
	// get the line/station
	String scrap = p.getOption("-scrap");
        if ( scrap != null ) {
	  // Log.v("DistoXX", "section point scrap " + scrap );
	  int pos = scrap.lastIndexOf( "-xx" );
	  if ( pos > 0 ) {
            String id = scrap.substring(pos+1); // line id
	    if ( /* id != null && */ id.length() > 0 ) { // id always not null [?]
              for ( ICanvasCommand cmd2 : mCurrentStack ) {
                if ( cmd2.commandType() != 0 ) continue; 
                DrawingPath p2 = (DrawingPath)cmd2;
                if ( p2.mType != DrawingPath.DRAWING_PATH_LINE ) continue;
                DrawingLinePath ln = (DrawingLinePath)p2;
                if ( ln.mLineType != BrushManager.mLineLib.mLineSectionIndex ) continue;
	        if ( id.equals( ln.getOption("-id") ) ) {
                  pt.setLink( ln );
	          break;
	        }
	      }
	    }
	  } else {
            pos = scrap.lastIndexOf( "-xs-" );
	    if ( pos < 0 ) pos = scrap.lastIndexOf( "-xh-" );
	    if ( pos > 0 ) {
              String name = scrap.substring(pos+4);
	      if ( /* name != null && */ name.length() > 0 ) { // name always not null [?]
	        // Log.v("DistoXX", "section station " + name );
	        for ( DrawingStationName st : mStations ) {
                  if ( name.equals( st.getName() ) ) {
                    pt.setLink( st );
	            break;
                  }
	        }
	      }
	    }
	  }
        }
      }
    }
  }

  void clearScrapOutline() { synchronized( mScrap ) { mScrap.clear(); } }

  void addScrapOutlinePath( DrawingLinePath path )
  {
    synchronized( mScrap ) {
      mScrap.add( path );
    }
  }

  // void addScrapDataStream( String tdr, float xdelta, float ydelta )
  // {
  //   synchronized( mScrap ) {
  //     mScrap.clear();
  //   }
  // }

  void clearXSectionsOutline() { synchronized( TDPath.mXSectionsLock ) { mXSectionOutlines.clear(); } }

  boolean hasXSectionOutline( String name ) 
  { 
    if ( mXSectionOutlines == null || mXSectionOutlines.size() == 0 ) return false;
    synchronized( TDPath.mXSectionsLock )  {
      for ( DrawingOutlinePath path : mXSectionOutlines ) {
        if ( path.isScrap( name ) ) return true;
      }
    }
    return false;
  }

  void addXSectionOutlinePath( DrawingOutlinePath path )
  {
    synchronized( TDPath.mXSectionsLock ) {
      mXSectionOutlines.add( path );
    }
  }

  void clearXSectionOutline( String name )
  {
    List<DrawingOutlinePath> xsection_outlines = Collections.synchronizedList(new ArrayList<DrawingOutlinePath>());
    synchronized( TDPath.mXSectionsLock ) {
      for ( DrawingOutlinePath path : mXSectionOutlines  ) {
        if ( ! path.isScrap( name ) ) xsection_outlines.add( path );
      }
      mXSectionOutlines.clear(); // not necessary
    }
    mXSectionOutlines = xsection_outlines;
  }

  private void shiftXSectionOutline( String name, float dx, float dy )
  {
    synchronized( TDPath.mXSectionsLock ) {
      for ( DrawingOutlinePath path : mXSectionOutlines ) {
        if ( path.isScrap( name ) ) path.mPath.shiftBy( dx, dy );
      }
    }
  }

  RectF getBoundingBox( )
  {
    RectF bbox = new RectF( 0, 0, 0, 0 );
    for ( ICanvasCommand cmd : getCommands() ) {
      if ( cmd.commandType() != 0 ) continue;
      DrawingPath p = (DrawingPath)cmd;
      bbox.union( p );
    }
    return bbox;
  }

}
