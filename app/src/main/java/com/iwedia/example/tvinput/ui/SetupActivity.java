/*
 * Copyright (C) 2015 iWedia S.A. Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.iwedia.example.tvinput.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bytel.dtv.scan.SignalAllInfo;
import com.iwedia.dtv.scan.IScanCallback;
import com.iwedia.dtv.scan.ScanInstallStatus;
import com.iwedia.dtv.scan.ScanStatus;
import com.iwedia.dtv.types.InternalException;
import com.iwedia.example.tvinput.R;
import com.iwedia.example.tvinput.TvService;
import com.iwedia.example.tvinput.a4tvbal.AlDtvManager;
import com.iwedia.example.tvinput.a4tvbal.AlScanControl;
import com.iwedia.example.tvinput.engine.Manager;
import com.iwedia.example.tvinput.engine.RouteManager;
import com.iwedia.example.tvinput.utils.Logger;

/**
 * Setup activity for this TvInput.
 */
public class SetupActivity extends Activity implements IScanCallback {

    /** Object used to write to logcat output */
    private final Logger mLog = new Logger(
            TvService.APP_NAME + SetupActivity.class.getSimpleName(), Logger.ERROR);

    private static final int ON_INIT_TEXT = 0;

    private static final int ON_NEW_CHANNEL_FOUND = 1;

    private static final int ON_SCAN_START = 2;

    private static final int ON_SCAN_COMPLETED = 3;

    private static final int ON_PROGRESS_UPDATE = 4;

    private enum ScanState {
        IDLE, SCANNING
    }

    private static ScanState mScanState;

    private TextView mSubtitle;
    private ProgressBar mProgressBar;
    private Button mScanAction;

    private Manager mDtvManager = null;

    private RouteManager mRouteManager;
    private String mSubtitleText;
    private int mChannelCounter;
    private Handler mHandler;
    private Object mLocker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLog.d("[onCreate]");
        setContentView(R.layout.setup_activity);
        Intent intent = new Intent(getApplicationContext(), TvService.class);
        getApplicationContext().startService(intent);
        mSubtitle = (TextView) findViewById(R.id.subtitle);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mScanAction = (Button) findViewById(R.id.scan_action);

        mHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case ON_INIT_TEXT:
                        mSubtitleText = (String) msg.obj;
                        mSubtitle.setText(mSubtitleText);
                        break;
                    case ON_NEW_CHANNEL_FOUND:
                        synchronized (mLocker) {
                            mChannelCounter++;
                            String temp = mSubtitleText + "\n" + "DVB channels found: "
                                    + mChannelCounter;
                            mSubtitle.setText(temp);
                        }
                        break;
                    case ON_SCAN_START:
                        mScanAction.setText(R.string.tif_setup_stop);
                        mProgressBar.setMax(100);
                        mProgressBar.setVisibility(View.VISIBLE);
                        mSubtitleText += "\n" + "scan started";
                        mSubtitle.setText(mSubtitleText);
                        break;
                    case ON_SCAN_COMPLETED:
                        mScanAction.setText(R.string.tif_setup_start);
                        mProgressBar.setVisibility(View.INVISIBLE);
                        // scan completed
                        mSubtitleText = mSubtitle.getText().toString();
                        mSubtitleText += "\n" + "scan completed";
                        mSubtitle.setText(mSubtitleText);
                        break;
                    case ON_PROGRESS_UPDATE:
                        mProgressBar.setProgress(msg.arg1);
                        break;
                }
            }
        };

        // ! In same time TvService will be created by system
        // ! TvService will wait and bind to MW service

        mScanState = ScanState.IDLE;

        mLocker = new Object();
    }

    public void onDestroy() {
        super.onDestroy();
        if (mDtvManager != null && mDtvManager.getDtvManager() != null) {
            mDtvManager.getDtvManager().getScanControl().unregisterCallback(this);
        }
    }

    public void onClickScanAction(View view) {
        mLog.d("[onClickScanAction][" + mScanState + "][" + (view == null) + "]");
        switch (mScanState) {
            case IDLE:
                mDtvManager = Manager.getInstance();
                if (mDtvManager == null) {
                    mSubtitleText = mSubtitle.getText().toString();
                    mSubtitleText += "\n" + "MW not ready, try again";
                    mSubtitle.setText(mSubtitleText);
                    return;
                }

                mRouteManager = mDtvManager.getRouteManager();
                ((AlScanControl) mDtvManager.getDtvManager().getScanControl())
                        .registerCallback(this);

                String subtitleText = "Available install frontends:";
                if (mRouteManager.getInstallRouteCab() != RouteManager.EC_INVALID_ROUTE) {
                    subtitleText += "\n" + "cable";
                }
                if (mRouteManager.getInstallRouteTer() != RouteManager.EC_INVALID_ROUTE) {
                    subtitleText += "\n" + "terrestrial";
                }
                if (mRouteManager.getInstallRouteSat() != RouteManager.EC_INVALID_ROUTE) {
                    subtitleText += "\n" + "satellite";
                }
                if (mRouteManager.getInstallRouteIp() != RouteManager.EC_INVALID_ROUTE) {
                    subtitleText += "\n" + "IP";
                }
                mSubtitle.setText(mSubtitleText);

                Message msg = new Message();
                msg.what = ON_INIT_TEXT;
                msg.obj = subtitleText;
                mHandler.sendMessage(msg);

                mHandler.sendEmptyMessage(ON_SCAN_START);
                mChannelCounter = 0;

                try {
                    mDtvManager.getChannelManager().startScan();
                } catch (InternalException e) {
                    e.printStackTrace();
                }
                mScanState = ScanState.SCANNING;
                break;
            case SCANNING:
                if (view == null) {
                    // scan completed
                    mHandler.sendEmptyMessage(ON_SCAN_COMPLETED);
                } else {
                    // user click
                    try {
                        mDtvManager.getChannelManager().stopScan();
                    } catch (InternalException e) {
                        e.printStackTrace();
                    }
                    mSubtitleText = mSubtitle.getText().toString();
                    mSubtitleText += "\n" + "scan aborted";
                    mSubtitle.setText(mSubtitleText);
                }
                mScanState = ScanState.IDLE;

                // ! Close activity
                setResult(Activity.RESULT_OK);
                finish();
        }
    }

    @Override
    public void antennaConnected(int routeId, boolean state) {
        mLog.d("[antennaConnected][" + mRouteManager.getInstallRouteDescription(routeId)
                + "][connected: " + state + "]");
    }

    @Override
    public void installServiceDATAName(int routeId, String name) {
        mLog.d("[installServiceDATAName][" + mRouteManager.getInstallRouteDescription(routeId)
                + "][name: " + name + "]");
        mHandler.sendEmptyMessage(ON_NEW_CHANNEL_FOUND);
    }

    @Override
    public void installServiceDATANumber(int routeId, int name) {
        mLog.d("[installServiceDATANumber][" + mRouteManager.getInstallRouteDescription(routeId)
                + "][name: " + name + "]");
    }

    @Override
    public void installServiceRADIOName(int routeId, String name) {
        mLog.d("[installServiceRADIOName][" + mRouteManager.getInstallRouteDescription(routeId)
                + "][name: " + name + "]");
        mHandler.sendEmptyMessage(ON_NEW_CHANNEL_FOUND);
    }

    @Override
    public void installServiceRADIONumber(int routeId, int name) {
        mLog.d("[installServiceRADIONumber][" + mRouteManager.getInstallRouteDescription(routeId)
                + "][name: " + name + "]");
    }

    @Override
    public void installServiceTVName(int routeId, String name) {
        mLog.d("[installServiceTVName][" + mRouteManager.getInstallRouteDescription(routeId)
                + "][name: " + name + "]");
        mHandler.sendEmptyMessage(ON_NEW_CHANNEL_FOUND);
    }

    @Override
    public void installServiceTVNumber(int routeId, int name) {
        mLog.d("[installServiceTVNumber][" + mRouteManager.getInstallRouteDescription(routeId)
                + "][name: " + name + "]");
    }

    @Override
    public void installStatus(ScanInstallStatus scanStatus) {
        mLog.d("[installStatus][" + scanStatus + "]");
    }

    @Override
    public void networkChanged(int networkId) {
        mLog.d("[networkChanged][network Id: " + networkId + "]");
    }

    @Override
    public void sat2ipServerDropped(int routeId) {
        mLog.d("[sat2ipServerDropped][" + mRouteManager.getInstallRouteDescription(routeId) + "]");
    }

    @Override
    public void scanFinished(int routeId) {
        mLog.d("[scanFinished][" + mRouteManager.getInstallRouteDescription(routeId) + "]");
        mDtvManager.getChannelManager().refreshChannelList();
        onClickScanAction(null);
        // Send an intent to application that is safe to pull channels from TIF
        // database
        Intent intent = new Intent("com.iwedia.tifservice.TIF_CHANNEL_DB_UPDATED");
        sendBroadcast(intent);
    }

    @Override
    public void scanNoServiceSpace(int routeId) {
        mLog.d("[scanFinished][" + mRouteManager.getInstallRouteDescription(routeId) + "]");
    }

    @Override
    public void scanProgressChanged(int routeId, int value) {
        mLog.d("[scanProgressChanged][" + mRouteManager.getInstallRouteDescription(routeId)
                + "][progres: " + value + "]");
        Message msg = new Message();
        msg.what = ON_PROGRESS_UPDATE;
        msg.arg1 = value;
        mHandler.sendMessage(msg);
    }

    @Override
    public void scanTunFrequency(int routeId, int frequency) {
        mLog.d("[scanTunFrequency][" + mRouteManager.getInstallRouteDescription(routeId)
                + "][frequency: " + frequency + "]");
    }

    @Override
    public void signalBer(int routeId, int ber) {
        mLog.d("[signalBer][" + mRouteManager.getInstallRouteDescription(routeId) + "][ber: " + ber
                + "]");
    }

    @Override
    public void signalQuality(int routeId, int quality) {
        mLog.d("[signalQuality][" + mRouteManager.getInstallRouteDescription(routeId)
                + "][quality: " + quality + "]");
    }

    @Override
    public void signalStrength(int routeId, int strength) {
        mLog.d("[signalStrength][" + mRouteManager.getInstallRouteDescription(routeId)
                + "][strength: " + strength + "]");
    }

    @Override
    public void triggerStatus(int routeId) {
        mLog.d("[triggerStatus][" + mRouteManager.getInstallRouteDescription(routeId) + "]");
    }

    // @Override
    public void signalAllInfo(SignalAllInfo signalAllInfo) {
        mLog.d("[signalAllInfo][" + signalAllInfo.toString() + "]");
    }

    // @Override
    public void scanStatus(int routeID, ScanStatus status) {
        mLog.d("[scanStatus][" + routeID + "][" + status.getValue() + "]");
    }
}
