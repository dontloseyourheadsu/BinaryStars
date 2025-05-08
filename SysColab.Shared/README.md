# Shared Library for SysColab

This library is designed to be used by all SysColab projects. It contains shared code, utilities, and resources that are common across different projects within the SysColab ecosystem.

## Features

* **Shared Utilities**: Common functions and classes that can be reused across different projects.

## Classes

### `DeviceInfo`

Represents information about a device, including:

* `Id` (GUID): The unique identifier of the device.
* `Name` (string): The human-readable name of the device.
* `Address` (string): The network or hardware address of the device.
* `IsPaired` (bool): Indicates whether the device is currently paired.

This class is used to represent and serialize device metadata in JSON format.

---

### `FileOffer`

A lightweight record that encapsulates metadata about a file being offered for transfer:

* `FileId` (GUID): Unique identifier for the file.
* `Name` (string): Name of the file.
* `Size` (long): Size of the file in bytes.
* `SenderId` (GUID): Identifier of the device offering the file.

Useful for initiating and managing file transfers across devices.

---

### `RelayMessage`

Represents a message sent via a relay mechanism, which can carry arbitrary payloads in JSON format:

* `TargetId` (string): Identifier for the intended recipient.
* `SerializedJson` (string): The payload of the message as a JSON string.
* `MessageType` (string?): An optional type descriptor for routing or handling logic.

This record is intended for decoupled communication between devices or services.
