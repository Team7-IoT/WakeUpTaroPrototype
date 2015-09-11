package com.example.alarmiotprototypeapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
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
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.lang.StringUtils;


public class MainActivity extends ActionBarActivity implements CompoundButton.OnCheckedChangeListener {

    private static final long SCAN_PERIOD = 10000;
    private static final String TARGET_PERIPHERAL_NAME = "Team7PrototypeBLE";

    private BluetoothAdapter bluetoothAdapter;
    private Handler handler;
    private BluetoothGatt bluetoothGatt;

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d("onConnectionStateChange", status + "->" + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("GATT", newState + ": " + BluetoothProfile.STATE_CONNECTED);

//                gatt.discoverServices();
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("GATT", newState + ": " + BluetoothProfile.STATE_DISCONNECTED);

            }
        }

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
        }
    };

    private BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            ParcelUuid[] uuids = device.getUuids();
            String uuid = "";
            if (uuids != null) {
                Log.d("UUID", uuids.toString());
                uuid += StringUtils.join(uuids, " ");
            }
            Log.d("ScanRecord", toStringOfUUID(scanRecord));
            Log.d("BLEActivity", toStringOfDevice(device) + "[" + uuid + "]");

            if (StringUtils.equals(device.getName(), TARGET_PERIPHERAL_NAME)) {
                Log.d("DeviceName", TARGET_PERIPHERAL_NAME);

                // 意中のデバイスが見つかったらスキャンを停止
                bluetoothAdapter.stopLeScan(scanCallback);
                // changeScanStatus("Stop!");

                bluetoothGatt = device.connectGatt(getApplicationContext(), false, gattCallback);
            }
        }

        private String toStringOfDevice(BluetoothDevice device) {
            StringBuilder sb = new StringBuilder();
            sb = sb.append("name=").append(device.getName());
            sb = sb.append(", bondStatus=").append(device.getBondState());
            sb = sb.append(", address=").append(device.getAddress());
            sb = sb.append(", type=").append(device.getType());
            return sb.toString();
        }

        private String toStringOfUUID(byte[] scanRecords) {
            String uuid = "NONE";
            if (scanRecords.length > 30) {
                uuid = "Over 30 Length is " + scanRecords.length;

                StringBuilder sb = new StringBuilder();
                for (byte record : scanRecords) {
                    sb.append(intToHex2(record & 0xff));
                    sb.append(" ");
                }
                uuid += sb.toString();

                /*
                //iBeacon の場合 6 byte 目から、 9 byte 目はこの値に固定されている。
                if ((scanRecords[5] == (byte) 0x4c) && (scanRecords[6] == (byte) 0x00) &&
                        (scanRecords[7] == (byte) 0x02) && (scanRecords[8] == (byte) 0x15)) {
                    uuid = intToHex2(scanRecords[9] & 0xff)
                            + intToHex2(scanRecords[10] & 0xff)
                            + intToHex2(scanRecords[11] & 0xff)
                            + intToHex2(scanRecords[12] & 0xff)
                            + "-"
                            + intToHex2(scanRecords[13] & 0xff)
                            + intToHex2(scanRecords[14] & 0xff)
                            + "-"
                            + intToHex2(scanRecords[15] & 0xff)
                            + intToHex2(scanRecords[16] & 0xff)
                            + "-"
                            + intToHex2(scanRecords[17] & 0xff)
                            + intToHex2(scanRecords[18] & 0xff)
                            + "-"
                            + intToHex2(scanRecords[19] & 0xff)
                            + intToHex2(scanRecords[20] & 0xff)
                            + intToHex2(scanRecords[21] & 0xff)
                            + intToHex2(scanRecords[22] & 0xff)
                            + intToHex2(scanRecords[23] & 0xff)
                            + intToHex2(scanRecords[24] & 0xff);

                    String major = intToHex2(scanRecords[25] & 0xff) + intToHex2(scanRecords[26] & 0xff);
                    String minor = intToHex2(scanRecords[27] & 0xff) + intToHex2(scanRecords[28] & 0xff);
                }
                */
            }

            return uuid;
        }

        public String intToHex2(int i) {
            char hex_2[] = {Character.forDigit((i >> 4) & 0x0f, 16), Character.forDigit(i & 0x0f, 16)};
            String hex_2_str = new String(hex_2);
            return hex_2_str.toUpperCase();
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

//        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        handler = new Handler(getApplicationContext().getMainLooper());

        initializeBluetoothStatus();
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
                bluetoothAdapter.startLeScan(scanCallback);
//                UUID[] targets = {UUID.fromString("280611F4-7398-41F0-A209-091B226F51DD"), UUID.fromString("85F203DC-A35F-471A-B57A-2B2A772A4295")};
//                bluetoothAdapter.startLeScan(targets, scanCallback);
            }
        }, SCAN_PERIOD);
    }

    public void stopScan(View view) {
        Log.d("BluetoothScan", "STOP!!");

        changeScanStatus("Stop!");

        bluetoothAdapter.stopLeScan(scanCallback);
    }

    public void sendSignal(View view) {
        TextView text = (TextView) findViewById(R.id.labelReceiveData);
        text.setText("Send!!");
    }

    private void changeScanStatus(String changedStatus) {
        TextView text = (TextView) findViewById(R.id.labelConnectStatus);
        text.setText(changedStatus);
    }
}
