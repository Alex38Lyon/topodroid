/* @file CutNPaste.java
 *
 * @author marco corvi
 * @date dec 2015
 *
 * @brief TopoDroid shot stations cut-n-paste
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;

import java.lang.ref.WeakReference;

import android.content.Context;
import android.content.res.Resources;

import android.widget.PopupWindow;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import android.view.View;
// import android.view.View.OnClickListener;
// import android.view.View.OnTouchListener;
import android.view.Gravity;
import android.view.MotionEvent;

import android.graphics.Paint.FontMetrics;

import android.util.TypedValue;

class CutNPaste
{
  final static private int BUTTON_HEIGHT = 22;

  static private String mClipboardText = null;
  static private PopupWindow mPopup = null;
  static private WeakReference<EditText> mEditText;

  static boolean dismissPopup()
  {
    if ( mPopup != null ) {
      mPopup.dismiss();
      mPopup = null;
      return true;
    }
    return false;
  }

  static void makePopup( final Context context, EditText et )
  {
    if ( mPopup != null ) {
      mPopup.dismiss();
      mPopup = null;
      return;
    }
    mEditText = new WeakReference<EditText>( et );

    LinearLayout layout = new LinearLayout( context );
    layout.setOrientation(LinearLayout.VERTICAL);
    int lHeight = LinearLayout.LayoutParams.WRAP_CONTENT;
    int lWidth = LinearLayout.LayoutParams.WRAP_CONTENT;

    Resources res = context.getResources();
    String cut   = res.getString( R.string.cut );
    String copy  = res.getString( R.string.copy );
    String paste = res.getString( R.string.paste );

    Button btn_cut = makePopupButton( context, cut, layout, lWidth, lHeight,
      new View.OnClickListener( ) {
        public void onClick(View v) {
          EditText etext = mEditText.get();
          if ( etext != null ) {
            mClipboardText = etext.getText().toString();
            etext.setText(TDString.EMPTY);
            String str = String.format( context.getResources().getString( R.string.copied ), mClipboardText );
            TDToast.makeGravity( str, Gravity.LEFT | Gravity.TOP );
          }
          dismissPopup();
        }
      } );
    float w = btn_cut.getPaint().measureText( cut );

    Button btn_copy = makePopupButton( context, copy, layout, lWidth, lHeight,
      new View.OnClickListener( ) {
        public void onClick(View v) {
          EditText etext = mEditText.get();
          if ( etext != null ) {
            mClipboardText = etext.getText().toString();
            String str = String.format( context.getResources().getString( R.string.copied ), mClipboardText );
            TDToast.makeGravity( str, Gravity.LEFT | Gravity.TOP );
          }
          dismissPopup();
        }
      } );
    float ww = btn_copy.getPaint().measureText( cut );
    if ( ww > w ) w = ww;

    Button btn_paste = makePopupButton( context, paste, layout, lWidth, lHeight,
      new View.OnClickListener( ) {
        public void onClick(View v) {
          if ( mClipboardText != null ) {
            EditText etext = mEditText.get();
            if ( etext != null ) {
              etext.setText( mClipboardText );
            }
          }
          dismissPopup();
        }
      } );
    ww = btn_paste.getPaint().measureText( cut );
    if ( ww > w ) w = ww;
    int iw = (int)(w + 10);
    btn_cut.setWidth( iw );
    btn_copy.setWidth( iw );
    btn_paste.setWidth( iw );

    FontMetrics fm = btn_cut.getPaint().getFontMetrics();
    int ih = (int)( (Math.abs(fm.top) + Math.abs(fm.bottom) + Math.abs(fm.leading) ) * 7 * 2.7); // 1.7
    mPopup = new PopupWindow( layout, iw, ih );
    mPopup.showAsDropDown( et );
  }

  static private Button makeButton( Context context, String text, int color, int size )
  {
    Button button = new Button( context );
    // button.set???( R.layout.popup_item );

    // THIS CRASHES THE APP
    // button.setBackgroundResource( R.drawable.popup_bgcolor );
    button.setTextColor( color );
    button.setBackgroundColor( TDColor.VERYDARK_GRAY );

    button.setHeight( 3*size );
    button.setText( text );
    button.setTextSize( TypedValue.COMPLEX_UNIT_DIP, size );
    button.setSingleLine( true );
    button.setGravity( 0x03 ); // left
    button.setPadding( 4, 4, 4, 4 );
    return button;
  }

  static Button makePopupButton( Context context, String text,
                                 LinearLayout layout, int w, int h, View.OnClickListener listener )
  {
    Button button = makeButton( context, text, TDColor.WHITE, BUTTON_HEIGHT );
    layout.addView( button, new LinearLayout.LayoutParams(h, w));
    button.setOnClickListener( listener );
    button.setOnTouchListener( new View.OnTouchListener( ) {
      @Override public boolean onTouch( View v, MotionEvent ev ) {
        v.setBackgroundColor( TDColor.DARK_ORANGE );
        // button.performClick(); // don't do performClick right-away, give user a short feedback
        return false;
      }
    } );
    return button;
  }

  static private PopupWindow mPopupBT = null;

  /** show BT mPopup under button b
   * @param b button
   */
  static PopupWindow showPopupBT( final Context context, final ILister ilister, final TopoDroidApp app, View b, boolean gm_data )
  {
    final ListerHandler lister = new ListerHandler( ilister );
    LinearLayout popup_layout  = new LinearLayout( context );
    popup_layout.setOrientation(LinearLayout.VERTICAL);
    int lHeight = LinearLayout.LayoutParams.WRAP_CONTENT;
    int lWidth = LinearLayout.LayoutParams.WRAP_CONTENT;

    Resources res = context.getResources();
    // ----- RESET BT
    //
    String text = res.getString(R.string.remote_reset);
    Button textview0 = makePopupButton( context, text, popup_layout, lWidth, lHeight,
      new View.OnClickListener( ) {
        public void onClick(View v) {
          app.resetComm();
          dismissPopupBT();
          TDToast.make( R.string.bt_reset );
        }
      } );
    float w = textview0.getPaint().measureText( text );

    Button textview1 = null;
    Button textview2 = null;
    Button textview3 = null;
    Button textview4 = null;
    if ( TDInstance.deviceType() == Device.DISTO_X310 ) {
      // ----- TURN LASER ON
      //
      text = res.getString(R.string.remote_on);
      textview1 = makePopupButton( context, text, popup_layout, lWidth, lHeight,
        new View.OnClickListener( ) {
          public void onClick(View v) {
            app.setX310Laser( 1, 0, null );
            dismissPopupBT();
          }
        } );
      float ww = textview1.getPaint().measureText( text );
      if ( ww > w ) w = ww;

      // ----- TURN LASER OFF
      //
      text = res.getString(R.string.remote_off);
      textview2 = makePopupButton( context, text, popup_layout, lWidth, lHeight,
        new View.OnClickListener( ) {
          public void onClick(View v) {
            app.setX310Laser( 0, 0, null );
            dismissPopupBT();
          }
        } );
      ww = textview2.getPaint().measureText( text );
      if ( ww > w ) w = ww;

      if ( gm_data ) {
        // ----- MEASURE ONE CALIB DATA AND DOWNLOAD IF MODE IS CONTINUOUS
        //
        text = res.getString( R.string.popup_do_gm_data );
        textview3 = makePopupButton( context, text, popup_layout, lWidth, lHeight,
          new View.OnClickListener( ) {
            public void onClick(View v) {
              // ilister.enableBluetoothButton(false);
              new DeviceX310TakeShot( ilister, (TDSetting.mCalibShotDownload ? lister : null), app, 1 ).execute();
              dismissPopupBT();
            }
          } );
        ww = textview3.getPaint().measureText( text );
        if ( ww > w ) w = ww;

      } else {
        // ----- MEASURE ONE SPLAY AND DOWNLOAD IT IF MODE IS CONTINUOUS
        //
        text = res.getString( R.string.popup_do_splay );
        textview3 = makePopupButton( context, text, popup_layout, lWidth, lHeight,
          new View.OnClickListener( ) {
            public void onClick(View v) {
              // ilister.enableBluetoothButton(false);
              new DeviceX310TakeShot( ilister, (TDSetting.isConnectionModeContinuous() ? lister : null), app, 1 ).execute();
              dismissPopupBT();
            }
          } );
        ww = textview3.getPaint().measureText( text );
        if ( ww > w ) w = ww;

        // ----- MEASURE ONE LEG AND DOWNLOAD IT IF MODE IS CONTINUOUS
        //
        text = res.getString(R.string.popup_do_leg);
        textview4 = makePopupButton( context, text, popup_layout, lWidth, lHeight,
          new View.OnClickListener( ) {
            public void onClick(View v) {
              // ilister.enableBluetoothButton(false);
              new DeviceX310TakeShot( ilister, (TDSetting.isConnectionModeContinuous()? lister : null), app, TDSetting.mMinNrLegShots ).execute();
              dismissPopupBT();
            }
          } );
        ww = textview4.getPaint().measureText( text );
        if ( ww > w ) w = ww;
      }
    }
    int iw = (int)(w + 10);
    textview0.setWidth( iw );
    if ( TDInstance.deviceType() == Device.DISTO_X310 ) {
      if ( textview1 != null) textview1.setWidth( iw );
      if ( textview2 != null) textview2.setWidth( iw );
      if ( textview3 != null) textview3.setWidth( iw );
      if ( ! gm_data ) if ( textview4 != null ) textview4.setWidth( iw );
    }

    FontMetrics fm = textview0.getPaint().getFontMetrics();
    int ih = (int)( (Math.abs(fm.top) + Math.abs(fm.bottom) + Math.abs(fm.leading) ) * 7 * 1.70);
    mPopupBT = new PopupWindow( popup_layout, iw, ih ); 
    mPopupBT.showAsDropDown(b); 
    return mPopupBT;
  }

  static boolean dismissPopupBT()
  {
    if ( mPopupBT != null ) {
      mPopupBT.dismiss();
      mPopupBT = null;
      return true;
    }
    return false;
  }

}
