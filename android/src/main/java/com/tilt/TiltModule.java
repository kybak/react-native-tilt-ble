package com.tilt;

import androidx.annotation.NonNull;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanSettings;
import android.util.SparseArray;
import android.content.Context;
import android.os.Build;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;


@ReactModule(name = TiltModule.NAME)
public class TiltModule extends ReactContextBaseJavaModule {
    public static final String NAME = "Tilt";
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private Map < String, String > uuidToColorMap;

    private static final int EXPECTED_MANUFACTURER_DATA_LENGTH = 23;
    private static final int APPLE_COMPANY_IDENTIFIER = 0x004C;
    private static final byte IBEACON_TYPE = 0x02;
    private static final byte EXPECTED_IBEACON_DATA_LENGTH = 0x15;

    public TiltModule(ReactApplicationContext reactContext) {
        super(reactContext);
        initializeBluetooth();
        initializeUuidToColorMap();
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    private void initializeBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) reactContext.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    private void initializeUuidToColorMap() {
        uuidToColorMap = new HashMap < > ();
        uuidToColorMap.put("A495BB10C5B14B44B5121370F02D74DE", "Red");
        uuidToColorMap.put("A495BB20C5B14B44B5121370F02D74DE", "Green");
        uuidToColorMap.put("A495BB30C5B14B44B5121370F02D74DE", "Black");
        uuidToColorMap.put("A495BB40C5B14B44B5121370F02D74DE", "Purple");
        uuidToColorMap.put("A495BB50C5B14B44B5121370F02D74DE", "Orange");
        uuidToColorMap.put("A495BB60C5B14B44B5121370F02D74DE", "Blue");
        uuidToColorMap.put("A495BB70C5B14B44B5121370F02D74DE", "Yellow");
        uuidToColorMap.put("A495BB80C5B14B44B5121370F02D74DE", "Pink");
    }

    public String getColorByUuid(String uuid) {
        return uuidToColorMap.getOrDefault(uuid.toUpperCase(), "Unknown Color");
    }


    public void sendEvent(WritableMap payload) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("onScanResults", payload);
    }

    @ReactMethod
    public void startScanning(Callback successCallback, Callback errorCallback) {
        if (bluetoothLeScanner == null) {
            errorCallback.invoke("Bluetooth LE Scanner not available");
            return;
        }

        ScanCallback leScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);

                WritableMap payload = Arguments.createMap();

                payload.putString("deviceAddress", result.getDevice().getAddress());
                payload.putInt("rssi", result.getRssi());

                ScanRecord scanRecord = result.getScanRecord();
                if (scanRecord != null) {
                    SparseArray < byte[] > manufacturerData = scanRecord.getManufacturerSpecificData();
                    if (manufacturerData != null && manufacturerData.size() > 0) {
                        parseBeaconData(scanRecord, payload);

                        if (uuidToColorMap.containsKey(payload.getString("uuid").toUpperCase())) {
                            sendEvent(payload);
                        }
                    }
                }

            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                errorCallback.invoke("Scan failed with error code: " + errorCode);
            }
        };

        // ScanSettings settings = new ScanSettings.Builder()
        // .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        // .build();
        bluetoothLeScanner.startScan(null, new ScanSettings.Builder().build(), leScanCallback);
    }

    @ReactMethod
    public void stopScanning() {
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(new ScanCallback() {});
        }
    }

    private void parseBeaconData(ScanRecord scanRecord, WritableMap payload) {

        byte[] manufacturerData = scanRecord.getManufacturerSpecificData(APPLE_COMPANY_IDENTIFIER);

        if (manufacturerData == null) {
            return;
        }

        if (manufacturerData.length < EXPECTED_MANUFACTURER_DATA_LENGTH) {
            return;
        }

        ByteBuffer manufacturerDataBuffer = ByteBuffer.wrap(manufacturerData)
        manufacturerDataBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byte[] uuidBytes = new byte[16];
        manufacturerDataBuffer.position(2);
        manufacturerDataBuffer.get(uuidBytes, 0, 16);
        StringBuilder uuidString = new StringBuilder();
        for (byte b: uuidBytes) {
            uuidString.append(String.format("%02x", b));
        }

        manufacturerDataBuffer.order(ByteOrder.BIG_ENDIAN);
        final short major = manufacturerDataBuffer.getShort(18);
        final short minor = manufacturerDataBuffer.getShort(20);
        double minorAsDouble = minor / 1000.0;

        payload.putString("uuid", uuidString.toString().toUpperCase());
        payload.putString("device", getColorByUuid(uuidString.toString().toUpperCase()));
        payload.putInt("temperature", major);
        payload.putDouble("gravity", minorAsDouble);
        payload.putString("rawData", bytesToHex(manufacturerData));

    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b: bytes) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }
}