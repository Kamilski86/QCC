# Zebra RFID SDK references a hidden platform class that does not exist in the public SDK.
# R8 reports it as missing; it is only reached on devices that provide it.
-dontwarn android.os.ServiceManager

# Vendor SDKs ship no (Zebra: empty) consumer rules and Honeywell bundles JNI libraries (libhedc.so)
# that are bound to class names: keep them unobfuscated.
-keep class com.zebra.** { *; }
-keep class com.honeywell.** { *; }

# Optional Zebra SDK transports/features we do not use (SFTP firmware update, IoT connector,
# websocket, embedded HTTP server, XML/crypto helpers). Their libraries are not bundled.
-dontwarn com.jcraft.jsch.**
-dontwarn fi.iki.elonen.**
-dontwarn org.apache.xerces.**
-dontwarn org.bouncycastle.**
-dontwarn org.java_websocket.**
