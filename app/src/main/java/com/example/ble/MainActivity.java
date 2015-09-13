package com.example.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
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

import static com.example.ble.Constants.TAG;
import static com.example.ble.Constants.TARGET_PERIPHERAL_NAME;

/**
 * メイン画面。
 */
public class MainActivity extends ActionBarActivity implements CompoundButton.OnCheckedChangeListener {

    public static final UUID ALERT_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_POWER_STATE_UUID = UUID.fromString("00002a1b-0000-1000-8000-00805f9b34fb");
    public static final UUID ALERT_LEVEL_UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");

    // 10秒 (10000msec)
    private static final long SCAN_PERIOD = 10000;
    // 空を表すラベル文字列
    private static final String EMPTY_LABEL = "---";

    private BluetoothAdapter bluetoothAdapter;
    private Handler handler;
    private BluetoothGatt bluetoothGatt;

    // BLE デバイス側のボタン押下回数
    private PushCounter counter;

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

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
                        updateScanStatus("Lost...");
                        switchCharacteristicActionStatus(false);
                        initializePushCount();
                    }
                });
            }
        }

        /**
         * {@link BluetoothGatt#discoverServices()}の呼び出し完了後に非同期で呼び出される。
         *
         * @see BluetoothGatt#discoverServices()
         * @see BluetoothGattCallback#onServicesDiscovered(BluetoothGatt, int)
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onConnectionStateChange status is " + status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onServicesDiscovered failed.");
                return;
            }

            for (BluetoothGattService service : gatt.getServices()) {
                if ((service == null) || (service.getUuid() == null)) {
                    Log.d(TAG, "BluetoothGattService is Empty!!");
                    continue;
                }
                Log.d(TAG, "BluetoothGattService UUID is " + service.getUuid().toString());
            }

            //  ステータス変更
            handler.post(new Runnable() {
                @Override
                public void run() {
                    updateScanStatus("Found!!");
                    switchCharacteristicActionStatus(true);
                    updatePushCount("0");
                }
            });

            // デバイスのボタン押下の通知受け取り設定
            if (isConnected()) {
                BluetoothGattCharacteristic c = findCharacteristic(BATTERY_SERVICE_UUID, BATTERY_POWER_STATE_UUID);
                bluetoothGatt.setCharacteristicNotification(c, true);
            }
        }

        /**
         * {@link BluetoothGatt#readCharacteristic(BluetoothGattCharacteristic)}の呼び出し完了後に非同期で呼び出される。<br />
         * BLE デバイスのバッテリー情報を画面のラベルに設定する。
         *
         * @see BluetoothGatt#readCharacteristic(BluetoothGattCharacteristic)
         * @see BluetoothGattCallback#onCharacteristicRead(BluetoothGatt, BluetoothGattCharacteristic, int)
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {
            if (characteristic.getUuid().equals(BATTERY_UUID)) {
                Log.d(TAG, "onCharacteristicRead battery: " + characteristic.getValue()[0]);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateBatteryStatus(String.format("%d%%", characteristic.getValue()[0]));
                    }
                });
            }
        }

        /**
         * {@link BluetoothGatt#setCharacteristicNotification(BluetoothGattCharacteristic, boolean)}で通知受け取り設定を行ったキャラクタリスティックの値に変更があった場合に非同期で呼び出される。<br />
         * BLE デバイス側のボタン押下回数をインクリメント (+1) する。
         *
         * @see BluetoothGatt#setCharacteristicNotification(BluetoothGattCharacteristic, boolean)
         * @see BluetoothGattCallback#onCharacteristicChanged(BluetoothGatt, BluetoothGattCharacteristic)
         */
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
                    Log.d(TAG, "Scan device: " + toStringOfDevice(device));

                    if (StringUtils.equals(device.getName(), TARGET_PERIPHERAL_NAME)) {
                        Log.d(TAG, "DeviceName: " + TARGET_PERIPHERAL_NAME);

                        bluetoothGatt = device.connectGatt(getApplicationContext(), false, gattCallback);
                        Log.d(TAG, "Scan device connet gatt " + (bluetoothGatt == null));
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

        verifyBleSupport();

        // 各種コンポーネント初期化
        initializeBleComponents();
        initializeBluetoothSwitchStatus();

        // ラベル初期化
        updateScanStatus((bluetoothAdapter.isEnabled() ? "Ready?" : EMPTY_LABEL));
        updateBatteryStatus(EMPTY_LABEL);
        initializePushCount();
    }

    /**
     * デバイスが BLE をサポートしているかを検証する。<br />
     * サポートしていない場合、メッセージを表示しアプリケーションを終了する。
     */
    private void verifyBleSupport() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * BLE 接続時に必要なコンポーネントを初期化する。
     */
    private void initializeBleComponents() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        handler = new Handler(getApplicationContext().getMainLooper());
    }

    /**
     * 本体の Bluetooth の状態に応じてスイッチを初期化する。
     */
    private void initializeBluetoothSwitchStatus() {
        Switch bluetoothStatus = (Switch) findViewById(R.id.switchBluetooth);
        bluetoothStatus.setChecked(bluetoothAdapter.isEnabled());
        bluetoothStatus.setOnCheckedChangeListener(this);
    }

    /**
     * Bluetooth 有効/無効のスイッチ状態によるハンドリングを行う。
     *
     * @param buttonView {@link CompoundButton}
     * @param isChecked  有効なら true
     */
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            bluetoothAdapter.enable();
        } else {
            bluetoothAdapter.disable();
            if (isConnected()) {
                bluetoothGatt.close();
                bluetoothGatt = null;

                updateScanStatus("Lost...");
                switchCharacteristicActionStatus(false);
                initializePushCount();
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

    /**
     * BLE デバイスのスキャンを開始する。
     *
     * @param view {@link View}
     */
    public void startScan(View view) {
        Log.d(TAG, "BluetoothScan START!!");

        updateScanStatus("Scanning...");

        // 10秒後に接続が成功していればスキャンを停止する
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    bluetoothAdapter.stopLeScan(scanCallback);
                }
            }
        }, SCAN_PERIOD);

        // スキャン開始
        bluetoothAdapter.startLeScan(scanCallback);
    }

    /**
     * BLE デバイスのスキャンを停止する。
     *
     * @param view {@link View}
     */
    public void stopScan(View view) {
        Log.d("BluetoothScan", "STOP!!");

        updateScanStatus("Stop!");

        // スキャン停止
        bluetoothAdapter.stopLeScan(scanCallback);
    }

    /**
     * {@code Characteristic}関連の値を操作するボタン類の状態を切り替える。
     *
     * @param enabled 有効にする場合は true
     */
    private void switchCharacteristicActionStatus(boolean enabled) {
        Log.d(TAG, "Switch Buttons for Characteristic action to " + enabled);

        // 各種ボタンの状態変更
        switchButtonStatus(R.id.writeAlarm, enabled);
        switchButtonStatus(R.id.writeVibration, enabled);
        switchButtonStatus(R.id.writeStop, enabled);
        switchButtonStatus(R.id.readBattery, enabled);

        updateBatteryStatus((enabled ? "Ready?" : EMPTY_LABEL));
    }

    /**
     * 指定されたボタンの活性/非活性を切り替える。
     *
     * @param buttonId {@link Button}オブジェクトを識別する ID
     * @param enabled  活性化させる場合は true
     */
    private void switchButtonStatus(int buttonId, boolean enabled) {
        Button button = (Button) findViewById(buttonId);
        button.setEnabled(enabled);
    }

    /**
     * BLE の {@code Characteristic}へアラーム開始の書き込みを行う。<br />
     * ※書き込む値は BSHSBTPT01BK に準拠した値
     *
     * @param view {@link View}
     */
    public void doWriteAlarm(View view) {
        Log.d(TAG, "Do findCharacteristic write [Alarm].");

        boolean result = doWriteCharacteristic(2);
        Log.d(TAG, "Write [Alarm] findCharacteristic result " + result);
    }

    /**
     * BLE の {@code Characteristic}へバイブレーション開始の書き込みを行う。<br />
     * ※書き込む値は BSHSBTPT01BK に準拠した値
     *
     * @param view {@link View}
     */
    public void doWriteVibration(View view) {
        Log.d(TAG, "Do findCharacteristic write [Vibration].");

        boolean result = doWriteCharacteristic(1);
        Log.d(TAG, "Write [Vibration] findCharacteristic result " + result);
    }

    /**
     * BLE の {@code Characteristic}へアラーム等を停止させる書き込みを行う。<br />
     * ※書き込む値は BSHSBTPT01BK に準拠した値
     *
     * @param view {@link View}
     */
    public void doWriteStop(View view) {
        Log.d(TAG, "Do findCharacteristic write [Stop].");

        boolean result = doWriteCharacteristic(0);
        Log.d(TAG, "Write [Stop] findCharacteristic result " + result);
    }

    /**
     * BLE の {@code Characteristic}へ書き込みを行う。<br />
     * BLE デバイスとの接続が切れている場合、false を返す。
     *
     * @param level 書き込む値
     * @return 書き込み結果
     */
    private boolean doWriteCharacteristic(int level) {
        if (!isConnected()) {
            Log.d(TAG, "Lost connection...");
            updateBatteryStatus("Lost");
            return false;
        }

        BluetoothGattCharacteristic c = findCharacteristic(ALERT_SERVICE_UUID, ALERT_LEVEL_UUID);
        if (c == null) {
            return false;
        }
        c.setValue(new byte[]{(byte) level});

        return bluetoothGatt.writeCharacteristic(c);
    }

    /**
     * BLE の {@code Characteristic}から値の読み込みを行う。
     *
     * @param view {@link View}
     */
    public void doReadBattery(View view) {
        Log.d(TAG, "Do findCharacteristic read [Battery].");

        if (!isConnected()) {
            Log.d(TAG, "Lost connection...");
            updateBatteryStatus("Lost");
            return;
        }

        BluetoothGattCharacteristic c = findCharacteristic(BATTERY_SERVICE_UUID, BATTERY_UUID);
        if (c == null) {
            return;
        }

        boolean result = bluetoothGatt.readCharacteristic(c);
        Log.d(TAG, "Read [Battery] findCharacteristic result " + result);
    }

    private void updateScanStatus(String newStatus) {
        TextView text = (TextView) findViewById(R.id.labelConnectStatus);
        text.setText(newStatus);
    }

    private void updateBatteryStatus(String newStatus) {
        TextView text = (TextView) findViewById(R.id.labelBattery);
        text.setText(newStatus);
    }

    private void updatePushCount(String count) {
        TextView text = (TextView) findViewById(R.id.labelPushCount);
        text.setText(count);
    }

    private void initializePushCount() {
        counter = new PushCounter();
        updatePushCount(EMPTY_LABEL);
    }

    private void incrementPushCount() {
        counter.increment();
        updatePushCount(counter.toString());
    }

    private boolean isConnected() {
        return (bluetoothGatt != null);
    }

    /**
     * {@link BluetoothGatt}からサービス・キャラクタリスティックの UUID を指定して{@link BluetoothGattCharacteristic}を取得する。<br />
     * 該当するサービス、キャラクタリスティックが存在しない場合は null を返す。
     *
     * @param sid サービスの UUID
     * @param cid キャラクタリスティックの UUID
     * @return 見つかった{@link BluetoothGattCharacteristic}
     */
    private BluetoothGattCharacteristic findCharacteristic(UUID sid, UUID cid) {
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
