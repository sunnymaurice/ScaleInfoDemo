/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.BluetoothChat;

import java.io.UnsupportedEncodingException;

import android.app.Activity;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.BluetoothChat.BluetoothConnectionService.LocalBinder;

/**
 * This is the main Activity that displays the current chat session.
 */
public class BluetoothChat extends Activity {
    // Debugging
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_SAVE_DEVICE = 6;

    // Pref keys
    private static final String PREFS_LAST_DEVICE = "LastDevice";

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Layout Views
    private TextView mTitle;
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;
    
    // Inforamtion Views
    private TextView mGrossWeight;
    private TextView mTare;
    private TextView mNetWeight;
    private TextView mBMI;
    //private Button mGetScaleDataBtn;

    private final int SIMPLE_NOTFICATION_ID = 1;
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;
    private NotificationManager mNotificationManager;

    private SharedPreferences prefs;
	private Editor prefsEditor;
	
	private String grossWeightStr;
	private String netWeightStr;
	private String tareStr;
	private String bodyMassIndexStr;
	
	/*
	 * 2014/04/01 by Maurice Sun.
	 * Construct a ServiceConnection object mBTServiceConn to use BluetoothConnectionService
	 */	
	private BluetoothConnectionService mBTService = null;
			
	private ServiceConnection mBTServiceConn = new ServiceConnection(){

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			LocalBinder binder = (LocalBinder) service;
			mBTService = binder.getService();			
			//mBTService =  ((BluetoothConnectionService.LocalBinder)service).getService();
		}
		@Override
		public void onServiceDisconnected(ComponentName name) {
			mBTService = null;
		}
		
	};
	
	private void startBTconnService()
	{
		boolean i;
		Intent it = new Intent(this, BluetoothConnectionService.class);
		
		try{
			i= bindService(it, mBTServiceConn, BIND_AUTO_CREATE);		
			Log.e("BT Main", "bindServece retrun: "+i);
		} catch(SecurityException e)
		{
			Log.e("BT Main", "find SecurityException...");
		}						
	}
	
	private void stopBTconnService()
	{
		Log.d("BT Main", "stopBTconnService");
		unbindService(mBTServiceConn);
		
		if(mBTService != null)
		{
			//TODO: stop existing bluetooth connection here.
			mBTService.stop();
		}
		mBTService = null;
	}
			
	static final String HEXES = "0123456789ABCDEF";
	
	public static String getHexString( byte [] raw ) 
	{
	    if ( raw == null ) {
	      return null;
	    }
	    final StringBuilder hex = new StringBuilder( 2 * raw.length );
	    for ( final byte b : raw ) {
	      hex.append(HEXES.charAt((b & 0xF0) >> 4))
	         .append(HEXES.charAt((b & 0x0F)));
	    }
	    return hex.toString();
	}
	
	public String convertHexToString(String hex)
	{
		 
		  StringBuilder sb = new StringBuilder();
		  StringBuilder temp = new StringBuilder();
	 
		  //49204c6f7665204a617661 split into two characters 49, 20, 4c...
		  for( int i=0; i<hex.length()-1; i+=2 ){
	 
		      //grab the hex in pairs
		      String output = hex.substring(i, (i + 2));
		      //convert hex to decimal
		      int decimal = Integer.parseInt(output, 16);
		      //convert the decimal to character
		      sb.append((char)decimal);
	 
		      temp.append(decimal);
		  }
		  //Log.e("Maurice Test", "Decimal : " + temp.toString());
	 
		  return sb.toString();
	}
	
	public String stringToHex(String input) throws UnsupportedEncodingException
	{
		if (input == null) throw new NullPointerException();
	        return getHexString(input.getBytes());
	}
	
	private void parseM430info(String message)
	{
	//TODO: Need consider all the possible situations. Reference: M430 V1.02 Specificiation.	
		 // Match any white space character including new line, tab, form feed e.g. \t, \n, \r and \f.
		 final String delim = "\\s+"; //equivalent to [ \\t\\n\\x0B\\f\\r]
	     
		 String [] items = message.split(delim);
		 
		 /*
		 for(String str : items)
		 {
			 Log.e("Parser Test", "item:"+str+"\n");
		 }
		 */
		 
		 /* Get gross weight */
		 if(items[2] != null)
		 {
			 grossWeightStr = items[2];
			 //Log.e("parseM430info", "gross weight:" + grossWeightStr);
			 mGrossWeight.setText(grossWeightStr);
		 }
		 /* Get tare */
		 if(items[5] != null)
		 {
			 tareStr = items[5];
			 //Log.e("parseM430info", "tare:" + tareStr);
			 mTare.setText(tareStr);
		 }
		 /* Get net weight */
		 if(items[8] != null)
		 {	
			 /* If there is a negative sign */
			 if(items[8].equals("-"))
			 {
				 netWeightStr = items[8]+items[9];
				 
				 /* Get BMI */
				 if(items[10].equals("PATIENT"))
				 {
					 bodyMassIndexStr = items[15];					 
				 }
				 else
				 {
					 bodyMassIndexStr = "NONE";					 
				 }
			 }
			 else
			 {
				 netWeightStr = items[8];
				 
				 if(items[9].equals("PATIENT"))
				 {
					 bodyMassIndexStr = items[14];					 
				 }
				 else
				 {
					 bodyMassIndexStr = "NONE";					 
				 }
			 }
			 //Log.e("parseM430info", "net weight:" + grossWeightStr);
			 //Log.e("parseM430info", "BMI:" + grossWeightStr);
			 mNetWeight.setText(netWeightStr);
			 mBMI.setText(bodyMassIndexStr);
		 }		 				 
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //Toast.makeText(this, "BLE is not supported... Use old one", Toast.LENGTH_LONG).show();
            //finish();
        }
        
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefsEditor = prefs.edit();
        if (D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);        
        setContentView(R.layout.main);        
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);
        
        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);
                
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Log.i("FNORD", "mNotificationManager: " + mNotificationManager);

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        /* 2014.03.21 by Maurice */
        initInfoViewComponents();
    }

    private void initInfoViewComponents()
    {
    	mGrossWeight = (TextView)findViewById(R.id.textGrossWeightDisplay);
    	mGrossWeight.setText("N/A");
    	mTare = (TextView)findViewById(R.id.textTareDisplay);
    	mTare.setText("N/A");
    	mNetWeight = (TextView)findViewById(R.id.textNetWeightDisplay);
    	mNetWeight.setText("N/A");
    	mBMI = (TextView)findViewById(R.id.textBMIDisplay);
    	mBMI.setText("N/A");
    }
    
    public void BtnGetScaleDataHandler(View v)
    {
    	//Send "P" or "p" command to the scale for a requst of sending weight information to us via bluetooth connection.
    	String command = "p";
    	
    	 // Check that we're actually connected before trying anything
    	/* Note: Rewriten for usage of service */
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED)
    	//if(mBTService.getState() != BluetoothConnectionService.STATE_CONNECTED)
        {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Get the message bytes and tell the BluetoothChatService to write
        byte[] send = command.getBytes();        
        mChatService.write(send);     
        //mBTService.write(send);
    }
    
    @Override
    public void onStart() {
        super.onStart();
        if (D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else {
            if (mChatService == null) setupChat();
        	//if(mBTService == null) setupChat(); 
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if (D) Log.e(TAG, "+ ON RESUME +");

        mNotificationManager.cancel(SIMPLE_NOTFICATION_ID);

        Log.i("FNORD", "" + getIntent());

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
        //if (mBTService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
        	//if(mBTService.getState() == BluetoothConnectionService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
        		//mBTService.start();

              String address = prefs.getString(PREFS_LAST_DEVICE, null);
              Log.e(TAG, " Address: " + address + " yeah!");
              if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED && address != null) {
              //if (mBTService.getState() != BluetoothConnectionService.STATE_CONNECTED && address != null) {

            	  if (mBluetoothAdapter.isDiscovering()) {
            		  mBluetoothAdapter.cancelDiscovery();
            	  }

            	  // Get the BluetoothDevice object
            	  BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

            	  Log.e(TAG, " DeviceAddress:" + device.getAddress() + " yeah!");
            	  Log.e(TAG, " DeviceName:" + device.getName() + " yeah!");

            	  // Attempt to connect to the device
            	  mChatService.connect(device);
            	  //mBTService.connect(device);
            	}
            }
        }
    }
/*
    @Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {    			
		super.onRestoreInstanceState(savedInstanceState);
		
		mGrossWeight.setText(savedInstanceState.getString("GrossWeight"));
		mNetWeight.setText(savedInstanceState.getString("NetWeight"));
		mTare.setText(savedInstanceState.getString("Tare"));
		mBMI.setText(savedInstanceState.getString("BMI"));
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {		
		super.onSaveInstanceState(outState);
		
		outState.putString("GrossWeight", mGrossWeight.getText().toString());
		outState.putString("NetWeight", mNetWeight.getText().toString());
		outState.putString("Tare", mTare.getText().toString());
		outState.putString("BMI", mBMI.getText().toString());		
	}
*/
	private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        /* To rewrite as a background service */
        mChatService = new BluetoothChatService(this, mHandler);        
        //startBTconnService();

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        
        /*
         * Fix the below problem.
         * When application start, mChatService.start() in the onResume() will be call "once" and create the AcceptThread. 
         * If you do any other things that make the main activity onPause() pause (In Google samples case, if you click 
         * menu to start device_list activity the main activity will pause), when the application back to main activity it 
         * will call create AcceptThread method "one more time", this cause the problem because one thread already running 
         * but you try to interrupt it. And at the end happen accept() fail error and throw java.io.IOException: Operation 
         * Canceled error.         
         */
        if (mChatService != null) mChatService.stop();
        //if(mBTService != null) mBTService.stop();
        
        mGrossWeight.setText("N/A");
        mNetWeight.setText("N/A");
        mTare.setText("N/A");
        mBMI.setText("N/A");
        
        if (D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        
        
        if (D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        //if(mBTService != null) stopBTconnService();      
        
        if (D) Log.e(TAG, "--- ON DESTROY ---");
    }

    private void ensureDiscoverable() {
        if (D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
    	//if (mBTService.getState() != BluetoothConnectionService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);
            //mBTService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    // The action listener for the EditText widget, to listen for the return key
    private TextView.OnEditorActionListener mWriteListener =
            new TextView.OnEditorActionListener() {
                public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                    // If the action is a key-up event on the return key, send the message
                    if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                        String message = view.getText().toString();
                        sendMessage(message);
                    }
                    if (D) Log.i(TAG, "END onEditorAction");
                    return true;
                }
            };                       
                     
    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            Log.i("FNORD", "handleMessage(): msg: " + msg);

            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if (D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            mTitle.setText(R.string.title_connected_to);
                            mTitle.append(mConnectedDeviceName);
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            mTitle.setText(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            mTitle.setText(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    
                    if(!writeMessage.contains("\n"))                    	
                    	mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;


                case MESSAGE_READ:
                    //byte[] readBuf = (byte[]) msg.obj;                    
                    
                    // construct a string from the valid bytes in the buffer
                    String readMessage = (String) msg.obj;
                    String hexMessage;
                    
                    try {
                    	hexMessage = stringToHex(readMessage);
                    	mConversationArrayAdapter.add("Hex: " + hexMessage+"\n");
                    } catch (UnsupportedEncodingException e) {
                    	// TODO Auto-generated catch block
                    	e.printStackTrace();
                    }
                    
                  /* Parse the data to fetch the scale infomation we want to display in the text views. */
                    parseM430info(readMessage);
                    mConversationArrayAdapter.add(mConnectedDeviceName + ":\n" + readMessage);                    
                  /*
                    Notification notification = new Notification(R.drawable.app_icon, readMessage,
                            System.currentTimeMillis());
                    Intent notificationIntent = new Intent(BluetoothChat.this, BluetoothChat.class);
                    notificationIntent.setFlags(254);
                    PendingIntent contentIntent = PendingIntent.getActivity(BluetoothChat.this, 666, notificationIntent, 0);
                    notification.setLatestEventInfo(BluetoothChat.this, mConnectedDeviceName, readMessage, contentIntent);
                    long[] vibrate = {0, 100, 200, 300};
                    notification.vibrate = vibrate;
                    mNotificationManager.notify(SIMPLE_NOTFICATION_ID, notification);
                  */				 
                    break;


                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            case MESSAGE_SAVE_DEVICE:
            	String address = msg.obj.toString();
            	prefsEditor.putString(PREFS_LAST_DEVICE, address);
            	prefsEditor.commit();
            	break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    // Attempt to connect to the device
                    mChatService.connect(device);
                    //mBTService.connect(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occured
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.scan:
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                return true;
            case R.id.discoverable:
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
        }
        return false;
    }

    /*
	@Override	
	public void onConfigurationChanged(Configuration newConfig) {
		// TODO : TO Adjust Views according to screen orientation.
		super.onConfigurationChanged(newConfig);
		
		if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
	        //align_landscape();
	    } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
	        //align_portrait();
	    }
	}
	*/

}