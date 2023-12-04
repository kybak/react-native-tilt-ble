import { useEffect, useState } from 'react';
import {
  NativeModules,
  Platform,
  NativeEventEmitter,
  PermissionsAndroid,
} from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-tilt-ble' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const Tilt = NativeModules.Tilt
  ? NativeModules.Tilt
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

interface Tilt {
  startScan(): void;
  temperature?: number | null;
  gravity?: number | null;
  device?: string | null;
  scanning?: boolean | null;
}

export function useTilt(): Tilt {
  const [temperature, setTemperature] = useState<number | null>(null);
  const [gravity, setGravity] = useState<number | null>(null);
  const [device, setDevice] = useState<string | null>(null);
  const [scanning, setScanning] = useState<boolean | null>(false);

  const startScan = async () => {
    const allowed = await requestPermissions();

    if (allowed) {
      console.log('Scanning...');
      setScanning(true);

      Tilt.startScanning(
        () => {},
        (error: string) => {
          setScanning(false);
          console.error('Scan error:', error);
        }
      );
    }
  };

  useEffect(() => {
    const eventEmitter = new NativeEventEmitter(NativeModules.IBeacon);
    let eventListener = eventEmitter.addListener('onScanResults', (event) => {
      if (event.device) {
        console.log(`iBeacon data:`, event);
        setDevice(event.device);
        setTemperature(event.temperature);
        setGravity(event.gravity);
      }
    });

    return () => {
      Tilt.stopScanning();
      eventListener.remove();
    };
  }, []);

  const requestAndroidPermissions = async () => {
    const bluetoothScanPermission = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN!,
      {
        title: 'Scan Permission',
        message: 'Bluetooth Low Energy requires Scan',
        buttonPositive: 'OK',
      }
    );
    const bluetoothConnectPermission = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT!,
      {
        title: 'Connect Permission',
        message: 'Bluetooth Low Energy requires Connect',
        buttonPositive: 'OK',
      }
    );
    const fineLocationPermission = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION!,
      {
        title: 'Location Permission',
        message: 'Bluetooth Low Energy requires Location',
        buttonPositive: 'OK',
      }
    );

    const coarseLocationPermission = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION!,
      {
        title: 'Location Permission',
        message: 'Bluetooth Low Energy requires Coarse Location',
        buttonPositive: 'OK',
      }
    );

    return (
      bluetoothScanPermission === 'granted' &&
      bluetoothConnectPermission === 'granted' &&
      fineLocationPermission === 'granted' &&
      coarseLocationPermission === 'granted'
    );
  };

  const requestPermissions = async (): Promise<boolean> => {
    return new Promise(async function (resolve, reject) {
      if (Platform.OS === 'android') {
        const allowed = await requestAndroidPermissions();
        if (allowed) {
          return reject('Not allowed');
        }
        resolve(allowed);
      } else {
        return reject('Android only');
      }
    });
  };

  return { startScan, scanning, temperature, gravity, device };
}
