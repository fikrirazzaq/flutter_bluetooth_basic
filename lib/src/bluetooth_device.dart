import 'package:json_annotation/json_annotation.dart';

part 'bluetooth_device.g.dart';

@JsonSerializable(includeIfNull: false)
class BluetoothDevice {
  BluetoothDevice(
      {this.name,
        required this.address,
        this.type: 0,
        this.connected: false});

  final String? name;
  final String address;
  final int type;
  final bool connected;

  factory BluetoothDevice.fromJson(Map<String, dynamic> json) =>
      _$BluetoothDeviceFromJson(json);
  Map<String, dynamic> toJson() => _$BluetoothDeviceToJson(this);
  factory BluetoothDevice.fromMap(Map map) {
    return BluetoothDevice(
      name: map["name"],
      address: map["address"]!,
      type: map["type"] ,
      connected: map["isConnected"] ?? false,
    );
  }
}
