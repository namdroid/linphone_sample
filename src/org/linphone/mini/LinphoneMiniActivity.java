package org.linphone.mini;
/*
LinphoneMiniActivity.java
Copyright (C) 2014  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/


import java.io.IOException;
import java.net.URL;

import org.asteriskjava.manager.AuthenticationFailedException;
import org.asteriskjava.manager.ManagerConnection;
import org.asteriskjava.manager.ManagerConnectionFactory;
import org.asteriskjava.manager.TimeoutException;
import org.asteriskjava.manager.action.ManagerAction;
import org.asteriskjava.manager.action.OriginateAction;
import org.asteriskjava.manager.response.ManagerResponse;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.mediastream.Log;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;

/**
 * @author Sylvain Berfini
 */
public class LinphoneMiniActivity extends Activity {
	
	private boolean running;
	private LinphoneMiniManager mManager;
	private LinphoneCoreListenerBase mListener;
	private ManagerConnection asteriskConnection;
	
    // The following are used for the shake detection
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    private static LinphoneMiniActivity instance;
    private Button callButton;
    private Button sendAlarmButton;
    private Button cancelAlarmButton;
    ManagerConnectionFactory factory;
//    private String confRoomNr = "789";
//    private String agentClient = "01109";
//    private String aliceClient = "6207";
    private String callId = "Bob is calling";
    private String serverIP = "10.0.1.200";
    private String password = "commend12";
   // private String serverIP = "192.168.178.41";
   // private String password = "4567";
    
    EditText etSipUser;
    EditText etAgent;
    EditText etAlice;
    EditText etRoomnr;
    TextView tvRegStat;
    
    Button btnRegister;
    Button btnUnregister;
    
	final Handler handler = new Handler();
	
	static final boolean isInstanciated() {
		return instance != null;
	}

	public static final LinphoneMiniActivity instance() {
		if (instance != null)
			return instance;
		throw new RuntimeException("LinphoneMiniActivity not instantiated yet");
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mManager = new LinphoneMiniManager(this);
		

	    // ShakeDetector initialization
	    mSensorManager = (SensorManager) getSystemService(this.SENSOR_SERVICE);
	    mAccelerometer = mSensorManager
	            .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
	    mShakeDetector = new ShakeDetector();
	    mShakeDetector.setOnShakeListener(new ShakeDetector.OnShakeListener() {

	        @Override
	        public void onShake(int count) {
	        	Log.w("on Shake !!!!!!!!!");
				SendAlarmToAsteriskTask runner = new SendAlarmToAsteriskTask();
			    runner.execute();
	        }
	    });
	    

		tvRegStat  = (TextView) findViewById(R.id.tvRegStat);
		
		handler.postDelayed(new Runnable() {
		    public void run() {
		    	tvRegStat.setText(mManager.getRegState());
		        handler.postDelayed(this, 5000);
		    }
		 }, 5000); //Every 5000 ms 
		
		
	    btnRegister = (Button) findViewById(R.id.regButton);
	    btnRegister.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
	    	    tvRegStat.setText("Registering...");
				(new RegisterSipUser()).execute();
			}
		});
	    
	    btnUnregister = (Button) findViewById(R.id.unregButton);
	    btnUnregister.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
	    	    tvRegStat.setText("Unregistering...");
				(new UnRegisterSipUser()).execute();
			}
		});
	    
	    
	    callButton = (Button) findViewById(R.id.alarmButton);
	    callButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				etAgent = (EditText) findViewById(R.id.etAgent);
				String agentnr = etAgent.getText().toString();
				mManager.getInstance().newOutgoingCall(agentnr, callId);
				
				
			}
		});
	    
	    sendAlarmButton = (Button) findViewById(R.id.alarmBtnAst);
	    sendAlarmButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				SendAlarmToAsteriskTask runner = new SendAlarmToAsteriskTask();
			    runner.execute();
			}
		});
	    
	    cancelAlarmButton = (Button) findViewById(R.id.cancelAlarmBtn);
	    cancelAlarmButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				SendCancelAlarmToAsteriskTask runner = new SendCancelAlarmToAsteriskTask();
			    runner.execute();
			}
		});
	    
    	factory = new ManagerConnectionFactory(
    	        serverIP, "admin", "amp111");
	}
	
	private void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch(InterruptedException ie) {
			Log.e("LinphoneMiniActivity", "Interrupted!\nAborting");
			return;
		}
	}
	


	
	public void displayCustomToast(final String message, final int duration) {
	/* TODO
		LayoutInflater inflater = getLayoutInflater();
		View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

		TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
		toastText.setText(message);

		final Toast toast = new Toast(getApplicationContext());
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.setDuration(duration);
		toast.setView(layout);
		toast.show(); */
		
		Toast.makeText(this, message, duration);
	}
	
    public String astSendAlarm() throws IOException, AuthenticationFailedException,
    TimeoutException
    {
    	OriginateAction originateAction;
    	ManagerResponse originateResponse;
    	
	    etAlice = (EditText) findViewById(R.id.etAlice);
	    etAgent = (EditText) findViewById(R.id.etAgent);
	    etRoomnr = (EditText) findViewById(R.id.etRoomnr);
	    
    	String alicenr = etAlice.getText().toString();
    	String agentclient = etAgent.getText().toString();
    	String confRoom = etRoomnr.getText().toString();
    	
    	this.asteriskConnection = factory.createManagerConnection();

    	originateAction = new OriginateAction();
    	if (agentclient.startsWith("01") )
    		originateAction.setChannel("IAX2/" + agentclient +"@DSP-IAX01");   // agent extension
    	else if (agentclient.startsWith("02") )
      		originateAction.setChannel("IAX2/" + agentclient +"@DSP-IAX02");     
    	else
    		originateAction.setChannel("SIP/" + agentclient);  
    	originateAction.setContext("from-internal");
    	originateAction.setExten(confRoom); // conference room
    	originateAction.setPriority(new Integer(1));
    	originateAction.setAsync(true);
    	originateAction.setCallerId("Bob ist in Gefahr !");

    	// connect to Asterisk and log in
    	asteriskConnection.login();

    	// send the originate action
    	originateResponse = asteriskConnection.sendAction(originateAction, 30000);

    	// print out whether the originate succeeded or not
    	Log.e(originateResponse.getResponse());
    	
    	originateAction = new OriginateAction();
    	originateAction.setChannel("SIP/" + alicenr); // client ( ALICE)
    	originateAction.setContext("from-internal");
    	originateAction.setExten(confRoom); // conference room
    	originateAction.setPriority(new Integer(1));
    	originateAction.setAsync(true);
    	originateAction.setCallerId("Your Bob is in danger !");

    	// send the originate action
    	originateResponse = asteriskConnection.sendAction(originateAction, 30000);
    	
    	// and finally log off and disconnect
    	asteriskConnection.logoff();
    	
    	return originateResponse.getResponse();
}
 
    public String astPlaybackSoundOnConfRoom() throws IOException, AuthenticationFailedException,
    TimeoutException
    {
    	OriginateAction originateAction;
    	ManagerResponse originateResponse;

	    etRoomnr = (EditText) findViewById(R.id.etRoomnr);
    	String confRoom = etRoomnr.getText().toString();
    	this.asteriskConnection = factory.createManagerConnection();

    	originateAction = new OriginateAction();
    	originateAction.setChannel("Local/do_playback@playback-custom");
    	originateAction.setContext("from-internal");
    	originateAction.setExten(confRoom);
    	originateAction.setVariable("SMF_SoundFileToPlay", "custom/noalarm");
    	originateAction.setPriority(new Integer(1));
    	originateAction.setAsync(true);

    	// connect to Asterisk and log in
    	asteriskConnection.login();

    	// send the originate action and wait for a maximum of 30 seconds for Asterisk
    	// to send a reply
    	originateResponse = asteriskConnection.sendAction(originateAction, 30000);

    	// and finally log off and disconnect
    	asteriskConnection.logoff();
    	
    	return originateResponse.getResponse();
}
    

    
    private class SendAlarmToAsteriskTask extends AsyncTask<String, String, String> {
        protected String doInBackground(String... confRoomExtension) {

            String response = "";
			try {
				response = astSendAlarm();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (AuthenticationFailedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TimeoutException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

            return response;
        }

        protected void onProgressUpdate(String... progress) {

        }

        protected void onPostExecute(String response) {
        	// print out whether the originate succeeded or not
        	Log.e(response);
        	// TODO show popub with cancel alarm button 
        }
    }
    
    protected void register(String sipAddress, String password)  {
    	final LinphoneCoreFactory lcFactory = LinphoneCoreFactory.instance();
		LinphoneCore lc  = mManager.getLc();
		
		
		try {
			
			// Parse identity
			LinphoneAddress address = lcFactory.createLinphoneAddress(sipAddress);
			String username = address.getUserName();
			String domain = address.getDomain();


			if (password != null) {
				// create authentication structure from identity and add to linphone
				lc.addAuthInfo(lcFactory.createAuthInfo(username, password, null, domain));
			}

			// create proxy config
			LinphoneProxyConfig proxyCfg = lc.createProxyConfig(sipAddress, domain, null, true);
			proxyCfg.setExpires(3600);
			lc.addProxyConfig(proxyCfg); // add it to linphone
			lc.setDefaultProxyConfig(proxyCfg);

			
			// main loop for receiving notifications and doing background linphonecore work
			running = true;
			while (running) {
				lc.iterate(); // first iterate initiates registration 
				sleep(50);
			}


			// Unregister
			lc.getDefaultProxyConfig().edit();
			lc.getDefaultProxyConfig().enableRegister(false);
			lc.getDefaultProxyConfig().done();
			while(lc.getDefaultProxyConfig().getState() != RegistrationState.RegistrationCleared) {
				lc.iterate();
				sleep(50);
			}

			// Then register again
			lc.getDefaultProxyConfig().edit();
			lc.getDefaultProxyConfig().enableRegister(true);
			lc.getDefaultProxyConfig().done();

			while(lc.getDefaultProxyConfig().getState() != RegistrationState.RegistrationOk
					&& lc.getDefaultProxyConfig().getState() != RegistrationState.RegistrationFailed) {
				lc.iterate();
				sleep(50);
			}

			// Automatic unregistration on exit
		}	 catch (LinphoneCoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		finally {
			displayCustomToast("Registration done!", 20);
		}
    }
    
    
    protected void unregister(String sipAddress) throws LinphoneCoreException  {
    	final LinphoneCoreFactory lcFactory = LinphoneCoreFactory.instance();
		LinphoneCore lc  = mManager.getLc();

			
			LinphoneAddress address = lcFactory.createLinphoneAddress(sipAddress);
			String username = address.getUserName();
			String domain = address.getDomain();
			
			if (password != null) {
				// create authentication structure from identity and add to linphone
				lc.addAuthInfo(lcFactory.createAuthInfo(username, password, null, domain));
			}
			
			LinphoneProxyConfig proxyCfg = lc.createProxyConfig(sipAddress, domain, null, true);
			proxyCfg.setExpires(0);
			lc.addProxyConfig(proxyCfg); // add it to linphone
			lc.setDefaultProxyConfig(proxyCfg);
			
			// Unregister
			lc.getDefaultProxyConfig().edit();
			lc.getDefaultProxyConfig().enableRegister(false);
			lc.getDefaultProxyConfig().done();
			while(lc.getDefaultProxyConfig().getState() != RegistrationState.RegistrationCleared) {
				lc.iterate();
				sleep(50);
			}

			// Automatic unregistration on exit
	

    }
    
    private class UnRegisterSipUser extends AsyncTask<String, String, String> {
        protected String doInBackground(String... confRoomExtension) {

            String response = "";
 
    	    etSipUser = (EditText) findViewById(R.id.etSipUser);
    	    String userid = etSipUser.getText().toString();
    	    try {
				unregister("SIP:" + userid + "@" + serverIP);
			} catch (LinphoneCoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
            return response;
        }

        protected void onProgressUpdate(String... progress) {

        }

        protected void onPostExecute(String response) {
        	// print out whether the originate succeeded or not
        	Log.e(response); // "Success" ?
        }
    }
    
    private class RegisterSipUser extends AsyncTask<String, String, String> {
        protected String doInBackground(String... confRoomExtension) {

            String response = "";
		
    	    etSipUser = (EditText) findViewById(R.id.etSipUser);
    	    String userid = etSipUser.getText().toString();
    	    
			register("SIP:" + userid + "@" + serverIP, password);
			
            return response;
        }

        protected void onProgressUpdate(String... progress) {

        }

        protected void onPostExecute(String response) {
        	// print out whether the originate succeeded or not
        	Log.e(response); // "Success" ?
        }
    }
    
    private class SendCancelAlarmToAsteriskTask extends AsyncTask<String, String, String> {
        protected String doInBackground(String... confRoomExtension) {

            String response = "";
			try {
				response = astPlaybackSoundOnConfRoom();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (AuthenticationFailedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TimeoutException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

            return response;
        }

        protected void onProgressUpdate(String... progress) {

        }

        protected void onPostExecute(String response) {
        	// print out whether the originate succeeded or not
        	Log.e(response); // "Success" ?
        }
    }
    
    
	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		mManager.destroy();
		
		super.onDestroy();
	}
}
