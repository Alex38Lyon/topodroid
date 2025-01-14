/* @file SwapHotBitTask.java
 *
 * @author marco corvi
 * @date apr 2016
 *
 * @brief TopoDroid DistoX A3 swap hot bit in a given range
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;

// import android.util.Log;

import java.lang.ref.WeakReference;

// import java.io.FileWriter;
// import java.io.PrintWriter;
// import java.io.IOException;

// import java.util.List;
// import java.util.ArrayList;

// import android.app.Activity;
import android.os.AsyncTask;
// import android.content.Context;

class SwapHotBitTask extends AsyncTask<Void, Integer, Integer>
{
  private final WeakReference<TopoDroidApp> mApp;
  private int mType; // DistoX type
  private String mAddress;
  private int mFrom;
  private int mTo;

  // @param ht head_tail
  SwapHotBitTask( TopoDroidApp app, int type, String address, int[] ht )
  {
    mApp     = new WeakReference<TopoDroidApp>( app );
    mAddress = address;
    mType    = type;
    mFrom    = ht[0];
    mTo      = ht[1];
    // // Log.v(TopoDroidApp.TAG, "do reset from " + from + " to " + to );
  }

  @Override
  protected Integer doInBackground(Void... v)
  {
    int res = 0;
    if ( mType == Device.DISTO_X310 ) {
      res = -1;
    } else if ( mType == Device.DISTO_A3 && mApp.get() != null ) {
      res = mApp.get().swapA3HotBit( mAddress, mFrom, mTo );
    }
    return res;
  }

  // @Override
  // protected void onProgressUpdate(Integer... progress)
  // {
  // }

  @Override
  protected void onPostExecute( Integer result )
  {
    if ( result >= 0 ) {
      TDToast.make( R.string.swap_hotbit_ok );
    } else {
      TDToast.makeBad( R.string.read_failed );
    }
  }

}
