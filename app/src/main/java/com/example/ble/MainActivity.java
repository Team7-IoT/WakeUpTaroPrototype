package com.example.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.lang.StringUtils;

import java.util.UUID;


public class MainActivity extends ActionBarActivity implements CompoundButton.OnCheckedChangeListener {

    public static final UUID ALERT_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_POWER_STATE_UUID = UUID.fromString("00002a1b-0000-1000-8000-00805f9b34fb");
    public static final UUID ALERT_LEVEL_UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");
    public static final UUID LINK_LOSS_UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");

    private static final long SCAN_PERIOD = 10000;
    private static final String TARGET_PERIPHERAL_NAME = "BSHSBTPT01BK";

    private static final String TAG = "WakeUpTaroPrototype";

    private BluetoothAdapter bluetoothAdapter;
    private Handler handler;
    private BluetoothGatt bluetoothGatt;

    private int pushCount;

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            Log.d(TAG, String.format("onConnectionStateChange Current status(%s) to New Status(%s).", status, newState));

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "GATT New status is STATE_CONNECTED");

                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "GATT New status is STATE_DISCONNECTED");

                //  ステータス変更
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        changeScanStatus("Lost...");
                        switchCharacteristicActionStatus(false);
                        initializePushCount();
                    }
                });
            }
        }

        /**
         * {@link BluetoothGatt#discoverServices()}の呼び出し完了後に呼び出される。
         *
         * @see BluetoothGatt#discoverServices()
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d("onConnectionStateChange", "status -> " + status);

            for (BluetoothGattService service : gatt.getServices()) {
                if ((service == null) || (service.getUuid() == null)) {
                    Log.d("BluetoothGattService", "Service is Empty!!");
                    continue;
                }
                Log.d("BluetoothGattService", "UUID is " + service.getUuid().toString());
            }

            //  ステータス変更
            handler.post(new Runnable() {
                @Override
                public void run() {
                    changeScanStatus("Found!!");
                    switchCharacteristicActionStatus(true);
                    changePushCount("0");
                }
            });

            // ボタン押下の通知受け取り設定
            if (isConnected()) {
                BluetoothGattCharacteristic c = characteristic(BATTERY_SERVICE_UUID, BATTERY_POWER_STATE_UUID);
                bluetoothGatt.setCharacteristicNotification(c, true);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
            if (characteristic.getUuid().equals(BATTERY_UUID)) {
                Log.d(TAG, "onCharacteristicRead battery: " + characteristic.getValue()[0]);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        changeBatteryStatus(String.format("%d%%", characteristic.getValue()[0]));
                    }
                });
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (BATTERY_POWER_STATE_UUID.equals(characteristic.getUuid())) {
                Log.d(TAG, "onCharacteristicChanged button: " + characteristic.getValue()[0]);

                if (characteristic.getValue()[0] == 1) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            incrementPushCount();
                        }
                    });
                }
            }
        }
    };

    private BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, final byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ParcelUuid[] uuids = device.getUuids();
                    String uuid = "";
                    if (uuids != null) {
                        Log.d("UUID", uuids.toString());
                        uuid += StringUtils.join(uuids, " ");
                    }
                    Log.d("BLEActivity", toStringOfDevice(device) + "[" + uuid + "]");

                    if (StringUtils.equals(device.getName(), TARGET_PERIPHERAL_NAME)) {
                        Log.d(TAG, "DeviceName: " + TARGET_PERIPHERAL_NAME);

                        bluetoothGatt = device.connectGatt(getApplicationContext(), false, gattCallback);

                        if (bluetoothGatt == null) {
                            Log.d(TAG, "BluetoothGATT is null");
                        } else {
                            Log.d(TAG, "BluetoothGATT is not null!!!!");
                        }
                    }
                }
            });
        }

        /**
         * {@link BluetoothDevice}の概要を表す文字列表現を返す。
         *
         * @param device スキャンして見つかった {@link BluetoothDevice}
         * @return {@link BluetoothDevice}の概要
         */
        private String toStringOfDevice(BluetoothDevice device) {
            StringBuilder sb = new StringBuilder();
            sb = sb.append("name=").append(device.getName());
            sb = sb.append(", bondStatus=").append(device.getBondState());
            sb = sb.append(", address=").append(device.getAddress());
            sb = sb.append(", type=").append(device.getType());
            return sb.toString();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        handler = new Handler(getApplicationContext().getMainLooper());

        initializeBluetoothStatus();

        // ラベル初期化
        if (bluetoothAdapter.isEnabled()) {
            changeScanStatus("Ready?");
        } else {
            changeScanStatus("---");
        }
        changeBatteryStatus("---");
        initializePushCount();
    }

    /**
     * 本体の Bluetooth の状態に応じてスイッチを初期化する。
     */
    private void initializeBluetoothStatus() {
        Switch bluetoothStatus = (Switch) findViewById(R.id.switchBluetooth);
        bluetoothStatus.setChecked(bluetoothAdapter.isEnabled());
        bluetoothStatus.setOnCheckedChangeListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            bluetoothAdapter.enable();
        } else {
            bluetoothAdapter.disable();
            if (isConnected()) {
                bluetoothGatt.close();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void startScan(View view) {
        Log.d("BluetoothScan", "START!!");

        changeScanStatus("Start!");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    bluetoothAdapter.stopLeScan(scanCallback);
                }
            }
        }, SCAN_PERIOD);

        bluetoothAdapter.startLeScan(scanCallback);
    }

    public void stopScan(View view) {
        Log.d("BluetoothScan", "STOP!!");

        changeScanStatus("Stop!");

        bluetoothAdapter.stopLeScan(scanCallback);
    }

    private void switchCharacteristicActionStatus(boolean enabled) {
        Log.d(TAG, "Switch Buttons for Characteristic action to " + enabled);

        // 各種ボタンの状態変更
        switchButtonStatus(R.id.writeAlarm, enabled);
        switchButtonStatus(R.id.writeVibration, enabled);
        switchButtonStatus(R.id.writeStop, enabled);
        switchButtonStatus(R.id.readBattery, enabled);

        changeBatteryStatus((enabled ? "Ready?" : "---"));
    }

    private void switchButtonStatus(int buttonId, boolean enabled) {
        Button button = (Button) findViewById(buttonId);
        button.setEnabled(enabled);
    }

    public void doWriteAlarm(View view) {
        Log.d(TAG, "Do characteristic write [Alarm].");

        boolean result = doWriteCharacteristic(view, 2);
        Log.d(TAG, "Write [Alarm] characteristic result " + result);
    }

    public void doWriteVibration(View view) {
        Log.d(TAG, "Do characteristic write [Vibration].");

        boolean result = doWriteCharacteristic(view, 1);
        Log.d(TAG, "Write [Vibration] characteristic result " + result);
    }

    public void doWriteStop(View view) {
        Log.d(TAG, "Do characteristic write [Stop].");

        boolean result = doWriteCharacteristic(view, 0);
        Log.d(TAG, "Write [Stop] characteristic result " + result);
    }

    private boolean doWriteCharacteristic(View view, int level) {
        if (!isConnected()) {
            Log.d(TAG, "Lost connection...");
            return false;
        }

        BluetoothGattCharacteristic c = characteristic(ALERT_SERVICE_UUID, ALERT_LEVEL_UUID);
        c.setValue(new byte[]{(byte) level});

        return bluetoothGatt.writeCharacteristic(c);
    }

    public void doReadBattery(View view) {
        Log.d(TAG, "Do characteristic read [Battery].");

        BluetoothGattCharacteristic c = characteristic(BATTERY_SERVICE_UUID, BATTERY_UUID);

        boolean result = bluetoothGatt.readCharacteristic(c);
        Log.d(TAG, "Read [Battery] characteristic result " + result);
    }

    private void changeScanStatus(String changedStatus) {
        TextView text = (TextView) findViewById(R.id.labelConnectStatus);
        text.setText(changedStatus);
    }

    private void changeBatteryStatus(String changedStatus) {
        TextView text = (TextView) findViewById(R.id.labelBattery);
        text.setText(changedStatus);
    }

    private void initializePushCount() {
        pushCount = 0;
        changePushCount("---");
    }

    private void incrementPushCount() {
        pushCount++;
        changePushCount(String.valueOf(pushCount));
    }

    private void changePushCount(String count) {
        TextView text = (TextView) findViewById(R.id.labelPushCount);
        text.setText(count);
    }

    private boolean isConnected() {
        return (bluetoothGatt != null);
    }

    private BluetoothGattCharacteristic characteristic(UUID sid, UUID cid) {
        if (!isConnected()) {
            return null;
        }
        BluetoothGattService s = bluetoothGatt.getService(sid);
        if (s == null) {
            Log.w(TAG, "Service NOT found :" + sid.toString());
            return null;
        }
        BluetoothGattCharacteristic c = s.getCharacteristic(cid);
        if (c == null) {
            Log.w(TAG, "Characteristic NOT found :" + cid.toString());
            return null;
        }
        return c;
    }
}
