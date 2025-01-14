/* @file DataDownloader.java
 *
 * @author marco corvi
 * @date sept 2014
 *
 * @brief TopoDroid survey shots data downloader (continuous mode)
 *
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;

// import android.util.Log;

// import java.util.ArrayList;

import android.content.Context;


class DataDownloader
{
  // int mStatus = 0; // 0 disconnected, 1 connected, 2 connecting
  final static int STATUS_OFF  = 0;
  final static int STATUS_ON   = 1;
  final static int STATUS_WAIT = 2;

  private int mConnected = STATUS_OFF;  // whetehr it is "connected": 0 unconnected, 1 connecting, 2 connected
  private boolean mDownload  = false;  // whether it is "downloading"

  boolean isConnected() { return mConnected == STATUS_ON; }
  boolean isDownloading() { return mDownload; }
  boolean needReconnect() { return mDownload && mConnected != STATUS_ON; }
  void setConnected( int connected ) { mConnected = connected; }
  void setDownload( boolean download ) { mDownload = download; }
  void updateConnected( boolean on_off )
  {
    if ( on_off ) {
      mConnected = STATUS_ON;
    } else {
      if ( mDownload ) {
        mConnected = ( TDSetting.mConnectionMode == TDSetting.CONN_MODE_CONTINUOUS && TDSetting.mAutoReconnect )? STATUS_WAIT : STATUS_OFF;
      } else {
        mConnected = STATUS_OFF;
      }
    }
  }

  int getStatus()
  {
    if ( ! mDownload ) return STATUS_OFF;
    return mConnected;
    // if ( mConnected == 0 ) {
    //   if ( TDSetting.mAutoReconnect ) return STATUS_WAIT;
    //   return STATUS_OFF;
    // } else if ( mConnected == 1 ) {
    //   return STATUS_WAIT;
    // } // else mConnected == 2
    // return STATUS_ON;
  }

  // private Context mContext; // UNUSED
  private final TopoDroidApp mApp;
  // private BroadcastReceiver mBTReceiver = null;

  DataDownloader( Context context, TopoDroidApp app )
  {
    // mContext = context;
    mApp     = app;
    // mBTReceiver = null;
  }

  boolean toggleDownload()
  {
    mDownload = ! mDownload;
    return mDownload;
    // Log.v("DistoX", "toggle download to " + mDownload );
  }

  void doDataDownload()
  {
    // Log.v("DistoXDOWN", "do data download " + mDownload + " connected " + mConnected );
    if ( mDownload ) {
      startDownloadData();
    } else {
      stopDownloadData();
    }
  }

  /** called with mDownload == true
   */
  private void startDownloadData( )
  {
    // TDLog.Log( TDLog.LOG_COMM, "**** download data. status: " + mStatus );
    if ( TDSetting.mConnectionMode == TDSetting.CONN_MODE_BATCH ) {
      tryDownloadData( );
    } else if ( TDSetting.mConnectionMode == TDSetting.CONN_MODE_CONTINUOUS ) {
      // Log.v("DistoXDOWN", "start download continuous" );
      if ( TDSetting.mAutoReconnect ) {
        TDInstance.secondLastShotId = TopoDroidApp.lastShotId( ); // FIXME-LATEST
        new ReconnectTask( this ).execute();
      } else {
        notifyConnectionStatus( STATUS_WAIT );
        // notifyUiThreadConnectionStatus( STATUS_WAIT );
        tryConnect( );
        // notifyUiThreadConnectionStatus( mConnected );
      }
    } else if ( TDSetting.mConnectionMode == TDSetting.CONN_MODE_MULTI ) {
      tryDownloadData( );
    }
  }

  void stopDownloadData()
  {
    // Log.v("DistoX", "stop Download Data() connected " + mConnected );
    // if ( ! mConnected ) return;
    // if ( TDSetting.isConnectionModeBatch() ) {
      mApp.disconnectComm();
      notifyConnectionStatus( STATUS_OFF );
      // notifyUiThreadConnectionStatus( STATUS_OFF );
    // }
  }

  // called also by ReconnectTask
  void tryConnect( )
  {
    // Log.v("DistoXDOWN", "try Connect() download " + mDownload + " connected " + mConnected );
    if ( TDInstance.device != null && DeviceUtil.isAdapterEnabled() ) {
      mApp.disconnectComm();
      if ( ! mDownload ) {
        mConnected = STATUS_OFF;
        return;
      }
      if ( mConnected == STATUS_ON ) {
        mConnected = STATUS_OFF;
        // Log.v( "DistoXDOWN", "**** toggle: connected " + mConnected );
      } else {
        // if this runs the RFcomm thread, it returns true
        int connected = STATUS_ON;
        if ( ! mApp.connectDevice( TDInstance.device.mAddress ) ) {
           connected = TDSetting.mAutoReconnect ? STATUS_WAIT : STATUS_OFF;
        }
        // Log.v( "DistoXDOWN", "**** connect device returns " + connected );
        notifyUiThreadConnectionStatus( connected );
      }
    }
  }

  private void notifyUiThreadConnectionStatus( int connected )
  {
    mConnected = connected;
    // Log.v("DistoXDOWN", "notify UI thread " + connected );
    TopoDroidApp.mActivity.runOnUiThread( new Runnable() { public void run () { mApp.notifyStatus( ); } } );
  }

  // this must be called on UI thread (onPostExecute)
  void notifyConnectionStatus( int connected )
  {
    mConnected = connected;
    // Log.v("DistoXDOWN", "notify thread " + connected );
    mApp.notifyStatus( );
  }

  // BATCH ON-DEMAND DOWNLOAD
  // non-private to allow the DistoX select dialog
  private void tryDownloadData( )
  {
    TDInstance.secondLastShotId = TopoDroidApp.lastShotId( ); // FIXME-LATEST
    if ( TDInstance.device != null && DeviceUtil.isAdapterEnabled() ) {
      notifyConnectionStatus( STATUS_WAIT );
      // notifyUiThreadConnectionStatus( STATUS_WAIT );
      // TDLog.Log( TDLog.LOG_COMM, "shot menu DOWNLOAD" );
      new DataDownloadTask( mApp, mApp.mListerSet, null ).execute();
    } else {
      mDownload = false;
      notifyConnectionStatus( STATUS_OFF );
      // notifyUiThreadConnectionStatus( STATUS_OFF );
      TDLog.Error( "download data: no device selected" );
      if ( TDInstance.sid < 0 ) {
        TDLog.Error( "download data: no survey selected" );
      // } else {
        // DBlock last_blk = mApp.mData.selectLastLegShot( TDInstance.sid );
        // (new ShotNewDialog( mContext, mApp, lister, last_blk, -1L )).show();
      }
    }
  }

  // static boolean mConnectedSave = false;

  void onStop()
  {
    // Log.v("DistoX", "DataDownloader onStop()");
    mDownload = false;
    if ( mConnected  > STATUS_OFF ) { // mConnected == STATUS_ON || mConnected == STATUS_WAIT
      stopDownloadData();
      mConnected = STATUS_OFF;
    }
  }

  void onResume()
  {
    // Log.v("DistoX", "Data Downloader onResume()");
  }

}
