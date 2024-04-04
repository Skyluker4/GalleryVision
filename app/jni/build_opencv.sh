#!/bin/sh

set -e

export PATH="/opt/Android/cmake/3.22.1/bin:$PATH"
export ANDROID_COMPILE_SDK="android-33"
export YOUR_OPENCV_SRC_FOLDER=./opencv
export YOUR_OPENCV_BUILD_FOLDER=./opencv/build
export ANDROID_SDK=/opt/Android
export NDK_ROOT=/opt/Android/ndk/25.2.9519653

PATH="/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH" JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" python3 $YOUR_OPENCV_SRC_FOLDER/platforms/android/build_sdk.py $YOUR_OPENCV_BUILD_FOLDER $YOUR_OPENCV_SRC_FOLDER --ndk_path $ANDROID_NDK_HOME --sdk_path $ANDROID_SDK --config ./opencv-ndk.config.py --no_samples_build
PATH="/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH" JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" ANDROID_HOME="/opt/Android" python3 $YOUR_OPENCV_SRC_FOLDER/platforms/android/build_java_shared_aar.py $YOUR_OPENCV_BUILD_FOLDER/OpenCV-android-sdk --java_version 17 --android_target_sdk 34 --android_compile_sdk 34 --android_min_sdk 21
